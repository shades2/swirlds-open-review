/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.jasperdb.files;

import com.swirlds.common.Units;
import com.swirlds.jasperdb.KeyRange;
import com.swirlds.jasperdb.Snapshotable;
import com.swirlds.jasperdb.collections.LongList;
import com.swirlds.jasperdb.files.DataFileCollection.LoadedDataCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.swirlds.jasperdb.files.DataFileCommon.dataLocationToString;
import static com.swirlds.jasperdb.files.DataFileCommon.fileIndexFromDataLocation;
import static com.swirlds.jasperdb.files.DataFileCommon.formatSizeBytes;
import static com.swirlds.jasperdb.files.DataFileCommon.getSizeOfFiles;
import static com.swirlds.jasperdb.files.DataFileCommon.getSizeOfFilesByPath;
import static com.swirlds.jasperdb.files.DataFileCommon.logMergeStats;
import static com.swirlds.jasperdb.files.DataFileCommon.printDataLinkValidation;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.JASPER_DB;

/**
 * A specialized map like disk based data store with long keys. It is assumed the keys are a single sequential block of
 * numbers that does not need to start at zero. The index from long key to disk location for value is in RAM and the
 * value data is stored in a set of files on disk.
 *
 * There is an assumption that keys are a contiguous range of incrementing numbers. This allows easy deletion during
 * merging by accepting any key/value with a key outside this range is not needed any more. This design comes from being
 * used where keys are leaf paths in a binary tree.
 *
 * @param <D>
 * 		type for data items
 */
@SuppressWarnings({ "DuplicatedCode" })
public class MemoryIndexDiskKeyValueStore<D> implements AutoCloseable, Snapshotable {
	private static final Logger LOG = LogManager.getLogger(MemoryIndexDiskKeyValueStore.class);

	/**
	 * This is useful for debugging and validating but is too expensive to enable in production.
	 */
	protected static boolean enableDeepValidation = LOG.isTraceEnabled();
	/**
	 * Index mapping, it uses our key as the index within the list and the value is the dataLocation in fileCollection
	 * where the key/value pair is stored.
	 */
	private final LongList index;
	/** On disk set of DataFiles that contain our key/value pairs */
	private final DataFileCollection<D> fileCollection;
	/**
	 * The name for the data store, this allows more than one data store in a single directory. Also, useful for
	 * identifying what files are used by what part of the code.
	 */
	private final String storeName;

	/**
	 * Construct a new MemoryIndexDiskKeyValueStore
	 *
	 * @param storeDir
	 * 		The directory to store data files in
	 * @param storeName
	 * 		The name for the data store, this allows more than one data store in a single directory.
	 * @param dataItemSerializer
	 * 		Serializer for converting raw data to/from data items
	 * @param loadedDataCallback
	 * 		call back for handing loaded data from existing files on startup. Can be null if not needed.
	 * @param keyToDiskLocationIndex
	 * 		The index to use for keys to disk locations. Having this passed in allows multiple
	 * 		MemoryIndexDiskKeyValueStore stores to share the same index if there key ranges do not overlap. For example
	 * 		with internal node and leaf paths in a virtual map tree. It also lets the caller decide the LongList
	 * 		implementation to use. This does mean the caller is responsible for snapshot of the index.
	 * @throws IOException
	 * 		If there was a problem opening data files
	 */
	public MemoryIndexDiskKeyValueStore(
			final Path storeDir,
			final String storeName,
			final DataItemSerializer<D> dataItemSerializer,
			final LoadedDataCallback loadedDataCallback,
			final LongList keyToDiskLocationIndex) throws IOException {
		this.storeName = storeName;
		this.index = keyToDiskLocationIndex;
		final boolean indexIsEmpty = keyToDiskLocationIndex.size() == 0;
		// create store dir
		Files.createDirectories(storeDir);
		// rebuild index as well as calling user's loadedDataCallback if needed
		final LoadedDataCallback combinedLoadedDataCallback;
		if (!indexIsEmpty && loadedDataCallback == null) {
			combinedLoadedDataCallback = null;
		} else {
			combinedLoadedDataCallback = (key, dataLocation, dataValue) -> {
				if (indexIsEmpty) {
					index.put(key, dataLocation);
				}
				if (loadedDataCallback != null) {
					loadedDataCallback.newIndexEntry(key, dataLocation, dataValue);
				}
			};
		}
		// create file collection
		fileCollection = new DataFileCollection<>(storeDir, storeName, dataItemSerializer, combinedLoadedDataCallback);
	}

	/**
	 * Merge all files that match the given filter
	 *
	 * @param filterForFilesToMerge filter to choose which subset of files to merge
	 * @param mergingPaused AtomicBoolean to monitor if we should pause merging
	 * @param minNumberOfFilesToMerge The minimum number of files to consider for a merge
	 * @throws IOException if there was a problem merging
	 * @throws InterruptedException if the merge thread was interupted
	 */
	public void merge(final Function<List<DataFileReader<D>>, List<DataFileReader<D>>> filterForFilesToMerge,
			final AtomicBoolean mergingPaused, final int minNumberOfFilesToMerge)
			throws IOException, InterruptedException {
		final long START = System.currentTimeMillis();
		final List<DataFileReader<D>> allMergeableFiles = fileCollection.getAllFilesAvailableForMerge();
		final List<DataFileReader<D>> filesToMerge = filterForFilesToMerge.apply(allMergeableFiles);
		if (filesToMerge == null) {
			// nothing to do
			return;
		}
		final int size = filesToMerge.size();
		if (size < minNumberOfFilesToMerge) {
			LOG.info(JASPER_DB.getMarker(),
					"[{}] No meed to merge as {} is less than the minimum {} files to merge.",
					storeName, size, minNumberOfFilesToMerge);
			return;
		}
		final long filesToMergeSize = getSizeOfFiles(filesToMerge);
		LOG.info(JASPER_DB.getMarker(), "[{}] Starting merging {} files, total {}...\n",
				storeName, size, formatSizeBytes(filesToMergeSize));
		if (enableDeepValidation) {
			startChecking();
		}
		final List<Path> newFilesCreated = fileCollection.mergeFiles(
				// update index with all moved data
				moves -> moves.forEach((key, oldValue, newValue) -> {
					boolean casSuccessful = index.putIfEqual(key, oldValue, newValue);
					if (enableDeepValidation) {
						checkItem(casSuccessful, key, oldValue, newValue);
					}
				}),
				filesToMerge, mergingPaused);
		if (enableDeepValidation) {
			endChecking(filesToMerge);
		}

		logMergeStats(
				storeName, (System.currentTimeMillis() - START) * Units.MILLISECONDS_TO_SECONDS,
				filesToMergeSize, getSizeOfFilesByPath(newFilesCreated),
				fileCollection, filesToMerge, allMergeableFiles, LOG);
	}

	/**
	 * Start a writing session ready for calls to put()
	 *
	 * @throws IOException
	 * 		If there was a problem opening a writing session
	 */
	public void startWriting() throws IOException {
		fileCollection.startWriting();
	}

	/**
	 * Put a value into this store, you must be in a writing session started with startWriting()
	 *
	 * @param key
	 * 		The key to store value for
	 * @param dataItem
	 * 		Buffer containing the data's value, it should have its position and limit set correctly
	 * @throws IOException
	 * 		If there was a problem write key/value to the store
	 */
	public void put(final long key, final D dataItem) throws IOException {
		long dataLocation = fileCollection.storeDataItem(dataItem);
		// store data location in index
		index.put(key, dataLocation);
	}

	/**
	 * End a session of writing
	 *
	 * @param minimumValidKey
	 * 		The minimum valid key at this point in time.
	 * @param maximumValidKey
	 * 		The maximum valid key at this point in time.
	 * @throws IOException
	 * 		If there was a problem closing the writing session
	 */
	public void endWriting(final long minimumValidKey, final long maximumValidKey) throws IOException {
		final DataFileReader<D> dataFileReader = fileCollection.endWriting(minimumValidKey, maximumValidKey);
		// we have updated all indexes so the data file can now be included in merges
		dataFileReader.setFileAvailableForMerging(true);
		LOG.info(JASPER_DB.getMarker(), "{} ended writing, numOfFiles={}, minimumValidKey={}, maximumValidKey={}",
				storeName, fileCollection.getNumOfFiles(), minimumValidKey, maximumValidKey);
	}

	/**
	 * Get a value by reading it from disk.
	 *
	 * @param key
	 * 		The key to find and read value for
	 * @return Array of serialization version for data if the value was read or null if not found
	 * @throws IOException
	 * 		If there was a problem reading the value from file
	 */
	public D get(final long key) throws IOException {
		// Check if out of range
		final KeyRange keyRange = fileCollection.getValidKeyRange();
		if (!keyRange.withinRange(key)) {
			// Key 0 is the root node and always supported, but if it doesn't exist, just return null,
			// even when no data has yet been written.
			if (key != 0) {
				LOG.trace(JASPER_DB.getMarker(), "Key [{}] is outside valid key range of {}", key, keyRange);
			}
			return null;
		}
		// read from files via index lookup
		return fileCollection.readDataItemUsingIndex(index, key);
	}

	/**
	 * Close all files being used
	 *
	 * @throws IOException
	 * 		If there was a problem closing files
	 */
	public void close() throws IOException {
		fileCollection.close();
	}

	/**
	 * Start snapshot, this is called while saving is blocked. It is expected to complete as fast as possible and only
	 * do the minimum needed to capture/write state that could be changed by saving.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	@Override
	public void startSnapshot(Path snapshotDirectory) throws IOException {
		fileCollection.startSnapshot(snapshotDirectory);
	}

	/**
	 * Do the bulk of snapshot work, as much as possible. Saving is not blocked while this method is running, and it is
	 * expected that saving can happen concurrently without problems. This will block till the snapshot is completely
	 * created.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	@Override
	public void middleSnapshot(Path snapshotDirectory) throws IOException {
		fileCollection.middleSnapshot(snapshotDirectory);
	}

	/**
	 * End snapshot, this is called while saving is blocked. It is expected to complete as fast as possible and only do
	 * the minimum needed to finish any work and return state after snapshotting.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	@Override
	public void endSnapshot(Path snapshotDirectory) throws IOException {
		fileCollection.endSnapshot(snapshotDirectory);
	}

	/**
	 * Get statistics for sizes of all files
	 *
	 * @return statistics for sizes of all fully written files, in bytes
	 */
	public LongSummaryStatistics getFilesSizeStatistics() {
		return fileCollection.getAllFullyWrittenFilesSizeStatistics();
	}

	// =================================================================================================================
	// Debugging Tools, these can be enabled with the ENABLE_DEEP_VALIDATION flag above

	/** Debugging store of compare and swap operations. Enabled in trace level logging only. */
	private LongObjectHashMap<CasRecord> casRecords;
	/** Debugging store of how many keys were checked */
	private LongLongHashMap keyCount;

	/** Start collecting data for debugging integrity checking */
	private void startChecking() {
		casRecords = new LongObjectHashMap<>();
		keyCount = new LongLongHashMap();
	}

	/** Check an item for debugging integrity checking */
	private void checkItem(
			final boolean casSuccessful,
			final long key,
			final long oldValue,
			final long newValue
	) {
		casRecords.put(key, new CasRecord(casSuccessful, oldValue, newValue, index.get(key, 0)));
	}

	/** End debugging integrity checking and print results */
	private void endChecking(final List<DataFileReader<D>> filesToMerge) {
		// set of merged files
		final SortedSet<Integer> mergedFileIds = new TreeSet<>();
		for (final DataFileReader<D> file : filesToMerge) {
			mergedFileIds.add(file.getMetadata().getIndex());
		}

		final KeyRange validKeyRange = fileCollection.getValidKeyRange();
		final long minValidKey = validKeyRange.getMinValidKey();
		final long maxValidKey = validKeyRange.getMaxValidKey();

		for (long key = minValidKey; key <= maxValidKey; key++) {
			final long location = index.get(key, -1);
			if (mergedFileIds.contains(fileIndexFromDataLocation(location))) { // only entries for deleted files
				// If we enter this "if", it means we have a corrupt index. Either we should have updated the
				// index but didn't, or shouldn't have updated it but did, or we didn't even try to update it
				// when we should have updated it.
				final CasRecord miss = casRecords.get(key);
				if (miss == null) {
					// We found a value in the index that refers to a file we've merged and deleted, but was never
					// in the moves list. We never attempted to update the index, when we should have!
					LOG.trace(JASPER_DB.getMarker(),
							"MISSING CAS RECORD for current = {}", dataLocationToString(location));
				} else {
					LOG.trace(JASPER_DB.getMarker(), "CAS {} " +
									"key = {}, value = {}, from = {}, to = {}, current = {}",
							(miss.missed ? "miss" : "hit"),
							key,
							dataLocationToString(location),
							dataLocationToString(miss.fileMovingFrom),
							dataLocationToString(miss.fileMovingTo),
							dataLocationToString(miss.currentLocation));
				}
			}
		}
		keyCount.forEachKeyValue((key, count) -> {
			if (count > 1) {
				LOG.trace(EXCEPTION.getMarker(), "Key [{}] has invalid count {}", key, count);
			}
		});
		printDataLinkValidation(storeName, index, fileCollection.getSetOfNewFileIndexes(),
				fileCollection.getAllFullyWrittenFiles(), validKeyRange);
	}

	/**
	 * POJO for storing a compare and swap operations
	 */
	private static class CasRecord {
		private final boolean missed;
		private final long fileMovingFrom;
		private final long fileMovingTo;
		private final long currentLocation;

		public CasRecord(
				final boolean missed, final long fileMovingFrom, final long fileMovingTo, final long currentLocation) {
			this.missed = missed;
			this.fileMovingFrom = fileMovingFrom;
			this.fileMovingTo = fileMovingTo;
			this.currentLocation = currentLocation;
		}
	}
}
