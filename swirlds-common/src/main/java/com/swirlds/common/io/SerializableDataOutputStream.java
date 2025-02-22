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
package com.swirlds.common.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.swirlds.common.io.SerializableStreamConstants.BOOLEAN_BYTES;
import static com.swirlds.common.io.SerializableStreamConstants.CLASS_ID_BYTES;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_LIST_ARRAY_LENGTH;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_VERSION;
import static com.swirlds.common.io.SerializableStreamConstants.SERIALIZATION_PROTOCOL_VERSION;
import static com.swirlds.common.io.SerializableStreamConstants.VERSION_BYTES;

/**
 * A drop-in replacement for {@link DataOutputStream}, which handles SerializableDet classes specially.
 * It is designed for use with the SerializableDet interface, and its use is described there.
 */
public class SerializableDataOutputStream extends ExtendedDataOutputStream {

	/**
	 * Creates a new data output stream to write data to the specified
	 * underlying output stream. The counter <code>written</code> is
	 * set to zero.
	 *
	 * @param out
	 * 		the underlying output stream, to be saved for later
	 * 		use.
	 * @see java.io.FilterOutputStream#out
	 */
	public SerializableDataOutputStream(OutputStream out) {
		super(out);
	}

	/**
	 * Write the serialization protocol version number to the stream. Should be used when serializing to a file that
	 * can be read by future versions.
	 *
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeProtocolVersion() throws IOException {
		this.writeInt(SERIALIZATION_PROTOCOL_VERSION);
	}

	private void writeSerializable(SelfSerializable serializable, boolean writeClassId,
			FunctionalSerialize serializeMethod) throws IOException {
		if (serializable == null) {
			if (writeClassId) {
				writeLong(NULL_CLASS_ID);
			} else {
				writeInt(NULL_VERSION);
			}
			return;
		}
		writeClassIdVersion(serializable, writeClassId);
		serializeMethod.serialize(this);
	}

	/**
	 * Writes a {@link SelfSerializable} object to a stream. If the class is known at the time of deserialization, the
	 * the {@code writeClassId} param can be set to false. If the class might be unknown when deserializing, then the
	 * {@code writeClassId} must be written.
	 *
	 * @param serializable
	 * 		the object to serialize
	 * @param writeClassId
	 * 		whether to write the class ID or not
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeSerializable(SelfSerializable serializable, boolean writeClassId) throws IOException {
		writeSerializable(serializable, writeClassId, serializable);
	}

	public <E extends Enum<E>> void writeOptionalSerializable(
			OptionalSelfSerializable<E> serializable,
			boolean writeClassId,
			E option) throws IOException {
		writeSerializable(serializable, writeClassId, (out) -> {
			serializable.serialize(out, option);
		});
	}

	/**
	 * Writes a list of objects returned by an {@link Iterator} when the size in known ahead of time. If the class is
	 * known at the time of deserialization, the {@code writeClassId} param can be set to false. If the class might be
	 * unknown when deserializing, then the {@code writeClassId} must be written.
	 *
	 * @param iterator
	 * 		the iterator that returns the data
	 * @param size
	 * 		the size of the dataset
	 * @param writeClassId
	 * 		whether to write the class ID or not
	 * @param allSameClass
	 * 		should be set to true if all the objects in the list are the same class
	 * @param <T>
	 * 		the type returned by the iterator
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public <T extends SelfSerializable> void writeSerializableIterableWithSize(
			Iterator<T> iterator,
			int size,
			boolean writeClassId,
			boolean allSameClass) throws IOException {
		this.writeInt(size);
		if (size == 0) {
			return;
		}
		writeBoolean(allSameClass);
		// if the class ID and version is written only once, we need to write it when we come across
		// the first non-null member, this variable will keep track of whether its written or not
		boolean classIdVersionWritten = false;
		while (iterator.hasNext()) {
			SelfSerializable serializable = iterator.next();
			if (!allSameClass) {
				// if classes are different, we just write every class one by one
				writeSerializable(serializable, writeClassId);
				continue;
			}
			if (serializable == null) {
				writeBoolean(true);
				continue;
			}
			writeBoolean(false);
			if (!classIdVersionWritten) {
				// this is the first non-null member, so we write the ID and version
				writeClassIdVersion(serializable, writeClassId);
				classIdVersionWritten = true;
			}
			serializable.serialize(this);
		}
	}

	/**
	 * Writes a list of {@link SelfSerializable} objects to the stream
	 *
	 * @param list
	 * 		the list to write, can be null
	 * @param writeClassId
	 * 		set to true if the classID should be written. This can be false if the class is known when
	 * 		de-serializing
	 * @param allSameClass
	 * 		should be set to true if all the objects in the list are the same class
	 * @param <T>
	 * 		the class stored in the list
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public <T extends SelfSerializable> void writeSerializableList(
			List<T> list,
			boolean writeClassId,
			boolean allSameClass) throws IOException {

		if (list == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
			return;
		}
		writeSerializableIterableWithSize(list.iterator(), list.size(), writeClassId, allSameClass);
	}

	/**
	 * Writes an array of {@link SelfSerializable} objects to the stream
	 *
	 * @param array
	 * 		the array to write, can be null
	 * @param writeClassId
	 * 		set to true if the classID should be written. This can be false if the class is known when
	 * 		de-serializing
	 * @param allSameClass
	 * 		should be set to true if all the objects in the list are the same class
	 * @param <T>
	 * 		the class stored in the list
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public <T extends SelfSerializable> void writeSerializableArray(
			T[] array,
			boolean writeClassId,
			boolean allSameClass) throws IOException {

		if (array == null) {
			writeSerializableList(null, writeClassId, allSameClass);
		} else {
			writeSerializableList(Arrays.asList(array), writeClassId, allSameClass);
		}
	}

	/**
	 * Get the serialized byte length an array of {@link SerializableWithKnownLength} objects
	 *
	 * @param array
	 * 		the array to write, can be null
	 * @param writeClassId
	 * 		set to true if the classID should be written. This can be false if the class is known when
	 * 		de-serializing
	 * @param allSameClass
	 * 		should be set to true if all the objects in the array are the same class
	 * @param <T>
	 * 		the class stored in the array
	 */
	public static <T extends SerializableWithKnownLength> int getSerializedLength(
			final T[] array,
			final boolean writeClassId,
			final boolean allSameClass) {
		int totalByteLength = Integer.BYTES; // length of array size
		if (array == null || array.length == 0) {
			return totalByteLength;
		}

		totalByteLength += BOOLEAN_BYTES;
		boolean classIdVersionWritten = false;
		for (final T t : array) {
			if (!allSameClass) {
				totalByteLength += getInstanceSerializedLength(t, true, writeClassId);
				continue;
			}
			if (t == null) {
				totalByteLength += BOOLEAN_BYTES;
				continue;
			}
			totalByteLength += BOOLEAN_BYTES;
			if (!classIdVersionWritten) {
				// this is the first non-null member, so we write the ID and version
				totalByteLength += VERSION_BYTES;
				if (writeClassId) {
					totalByteLength += CLASS_ID_BYTES;
				}
				classIdVersionWritten = true;
			}
			// version and class info already written
			totalByteLength += getInstanceSerializedLength(t, false, false);

		}

		return totalByteLength;
	}

	/**
	 * Get the serialized byte length of {@link SerializableWithKnownLength} object
	 *
	 * @param data
	 * 		array to write, should not be null
	 * @param writeClassId
	 * 		set to true if the classID should be written. This can be false if the class is known when
	 * 		de-serializing
	 */
	public static <T extends SerializableWithKnownLength> int getInstanceSerializedLength(
			final T data,
			final boolean writeVersion,
			final boolean writeClassId) {
		int totalByteLength = 0;
		if (writeClassId) {
			totalByteLength += CLASS_ID_BYTES;
		}
		if (data == null) {
			return totalByteLength;
		}
		if (writeVersion) {
			totalByteLength += VERSION_BYTES;                //version integer
		}
		totalByteLength += data.getSerializedLength();        //data its own content serialized length
		return totalByteLength;
	}

	/** This method assumes serializable is not null */
	protected void writeClassIdVersion(
			final SerializableDet serializable,
			final boolean writeClassId) throws IOException {

		if (writeClassId) {
			this.writeLong(serializable.getClassId());
		}
		this.writeInt(serializable.getVersion());
	}
}
