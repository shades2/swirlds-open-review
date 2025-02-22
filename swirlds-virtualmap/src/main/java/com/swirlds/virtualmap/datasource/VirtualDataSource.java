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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Defines a data source, used with {@code VirtualMap}, to implement a virtual tree. Both in-memory and
 * on-disk data sources may be written. When constructing a {@code VirtualMap}, create a concrete data source
 * implementation.
 * <p>
 * The {@link VirtualDataSource} defines methods for getting the root node, and for looking up a leaf node
 * by key.
 * <p>
 * The nodes returned by the methods on this interface represent the *LATEST* state on disk. Once retrieved,
 * the nodes can be fast-copied for later versions, and persisted to disk via *archive*.
 * <p>
 * Each datasource instance works for a single type of K and V
 * <p>
 * <strong>YOU MUST NEVER ASK FOR SOMETHING YOU HAVE NOT PREVIOUSLY WRITTEN.</strong> If you do, you will get
 * very strange exceptions. This is deemed acceptable because guarding against it would require obnoxious
 * performance degradation.
 *
 * @param <K>
 * 		The key for a leaf node.
 * @param <V>
 * 		The type of leaf node.
 */
@SuppressWarnings("unused")
public interface VirtualDataSource<K extends VirtualKey<? super K>, V extends VirtualValue> {

	/** nominal value for a invalid path */
	int INVALID_PATH = -1;

	/**
	 * Close the data source
	 *
	 * @throws IOException
	 * 		If there was a problem closing the data source
	 */
	void close() throws IOException;

	/**
	 * Save a bulk set of changes to internal nodes and leaves.
	 * <p><strong>YOU MUST NEVER ASK FOR SOMETHING YOU HAVE NOT PREVIOUSLY WRITTEN.</strong></p>
	 *
	 * @param firstLeafPath
	 * 		the new path of first leaf node
	 * @param lastLeafPath
	 * 		the new path of last leaf node
	 * @param internalRecords
	 * 		stream of new internal nodes and updated internal nodes
	 * @param leafRecordsToAddOrUpdate
	 * 		stream of new leaf nodes and updated leaf nodes
	 * @param leafRecordsToDelete
	 * 		stream of new leaf nodes to delete, The leaf record's key and path have to be populated, all other data can
	 * 		be null.
	 * @throws IOException
	 * 		If there was a problem saving changes to data source
	 */
	void saveRecords(
			final long firstLeafPath,
			final long lastLeafPath,
			final Stream<VirtualInternalRecord> internalRecords,
			final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
			final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete) throws IOException;

	/**
	 * Load the record for a leaf node by key
	 *
	 * @param key
	 * 		the key for a leaf
	 * @return the leaf's record if one was stored for the given key or null if not stored
	 * @throws IOException
	 * 		If there was a problem reading the leaf record
	 */
	VirtualLeafRecord<K, V> loadLeafRecord(final K key) throws IOException;

	/**
	 * Load the record for a leaf node by path
	 *
	 * @param path
	 * 		the path for a leaf
	 * @return the leaf's record if one was stored for the given path or null if not stored
	 * @throws IOException
	 * 		If there was a problem reading the leaf record
	 */
	VirtualLeafRecord<K, V> loadLeafRecord(final long path) throws IOException;

	/**
	 * Load the record for an internal node by path
	 *
	 * @param path
	 * 		the path for a internal
	 * @return the internal node's record if one was stored for the given path or null if not stored
	 * @throws IOException
	 * 		If there was a problem reading the internal record
	 */
	VirtualInternalRecord loadInternalRecord(final long path) throws IOException;

	/**
	 * Load the hash for a leaf
	 *
	 * NOTE: Called during the hashing phase ONLY. Never called on non-existent nodes.
	 *
	 * @param path
	 * 		the path to the leaf
	 * @return leaf's hash or null if no leaf hash is stored for the given path
	 * @throws IOException
	 * 		If there was a problem loading the leaf's hash from data source
	 */
	Hash loadLeafHash(final long path) throws IOException;

	/**
	 * Write a snapshot of the current state of the database at this moment in time. This will need to be called between
	 * calls to saveRecords to have a reliable state. This will block till the snapshot is completely created.
	 * <p><b>
	 * IMPORTANT, after this is completed the caller owns the directory. It is responsible for deleting it when it
	 * is no longer needed.
	 * </b></p>
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem writing the current database out to the given directory
	 */
	void snapshot(final Path snapshotDirectory) throws IOException;

	/**
	 * Switch this database instance to use the statistics registered in another database. Required due
	 * to statistics restriction that prevents stats from being registered after initial boot up process.
	 *
	 * @param that
	 * 		the database with statistics to copy
	 */
	void copyStatisticsFrom(VirtualDataSource<K, V> that);

	/**
	 * Register all statistics with an object that manages statistics.
	 *
	 * @param registry
	 * 		the object that will manage statistics
	 */
	void registerStatistics(Consumer<StatEntry> registry);

	/**
	 * Start background compaction process, if it is not already running.
	 */
	default void startBackgroundCompaction(){}

	/**
	 * Stop background compaction process, if it is running.
	 */
	default void stopBackgroundCompaction(){}
}
