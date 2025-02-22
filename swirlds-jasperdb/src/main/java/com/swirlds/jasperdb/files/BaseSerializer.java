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

import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Base interface for common 80% of DataItem and Key serializers.
 *
 * @param <T>
 * 		The type of data we are serializing and deserializing
 */
public interface BaseSerializer<T> {

	/**
	 * Get if the number of bytes a data item takes when serialized is variable or fixed
	 *
	 * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
	 */
	default boolean isVariableSize() {
		return getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE;
	}

	/**
	 * Get the number of bytes a data item takes when serialized
	 *
	 * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
	 */
	int getSerializedSize();

	/**
	 * For variable sized data get the typical  number of bytes a data item takes when serialized
	 *
	 * @return Either for fixed size same as getSerializedSize() or an estimated typical size for data items
	 */
	default int getTypicalSerializedSize() {
		if (isVariableSize()) {
			throw new IllegalStateException("Variable sized data implementations have to override this method");
		}
		return getSerializedSize();
	}

	/**
	 * Get the current data item serialization version
	 */
	long getCurrentDataVersion();

	/**
	 * Deserialize a data item from a byte buffer, that was written with given data version
	 *
	 * @param buffer
	 * 		The buffer to read from containing the data item including its header
	 * @param dataVersion
	 * 		The serialization version the data item was written with
	 * @return Deserialized data item
	 */
	T deserialize(ByteBuffer buffer, long dataVersion) throws IOException;

	/**
	 * Serialize a data item including header to the output stream returning the size of the data written
	 *
	 * @param data
	 * 		The data item to serialize
	 * @param outputStream
	 * 		Output stream to write to
	 */
	int serialize(T data, SerializableDataOutputStream outputStream) throws IOException;

	/**
	 * Copy the serialized data item in dataItemData into the writingStream. Important if serializedVersion is not the
	 * same as current serializedVersion then update the data to the latest serialization.
	 *
	 * @param serializedVersion
	 * 		The serialized version of the data item in dataItemData
	 * @param dataItemSize
	 * 		The size in bytes of the data item dataItemData
	 * @param dataItemData
	 * 		Buffer containing complete data item including the data item header
	 * @param writingStream
	 * 		The stream to write data item out to
	 * @return the number of bytes written, this could be the same as dataItemSize or bigger or smaller if
	 * 		serialization version has changed.
	 * @throws IOException
	 * 		if there was a problem writing data item to stream or converting it
	 */
	default int copyItem(
			final long serializedVersion,
			final int dataItemSize,
			final ByteBuffer dataItemData,
			final SerializableDataOutputStream writingStream
	) throws IOException {
		if (serializedVersion == getCurrentDataVersion()) {
			writingStream.write(dataItemData.array(), 0, dataItemSize);
		} else {
			// deserialize and reserialize to convert versions
			return serialize(deserialize(dataItemData, serializedVersion), writingStream);
		}
		return dataItemSize;
	}
}
