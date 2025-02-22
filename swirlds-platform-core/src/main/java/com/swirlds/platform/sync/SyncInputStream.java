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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.extendable.extensions.HashingStreamExtension;
import com.swirlds.platform.network.ByteConstants;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.swirlds.common.io.extendable.ExtendableInputStream.extendInputStream;

public class SyncInputStream extends SerializableDataInputStream {

	/** The maximum number of tips allowed per node. */
	private static final int MAX_TIPS_PER_NODE = 1000;

	private final CountingStreamExtension syncByteCounter;
	private final HashingStreamExtension hasher;

	private SyncInputStream(InputStream in, CountingStreamExtension syncByteCounter, HashingStreamExtension hasher) {
		super(in);
		this.syncByteCounter = syncByteCounter;
		this.hasher = hasher;
	}

	public static SyncInputStream createSyncInputStream(InputStream in, int bufferSize) {
		CountingStreamExtension syncCounter = new CountingStreamExtension();
		HashingStreamExtension hasher = new HashingStreamExtension(DigestType.SHA_384);

		// the buffered reader reads data first, for efficiency
		return new SyncInputStream(
				extendInputStream(new BufferedInputStream(in, bufferSize), syncCounter, hasher),
				syncCounter,
				hasher
		);
	}

	public CountingStreamExtension getSyncByteCounter() {
		return syncByteCounter;
	}

	public HashingStreamExtension getHasher() {
		return hasher;
	}

	/**
	 * Reads a sync request response from the stream
	 *
	 * @return true if the sync has been accepted, false if it was rejected
	 * @throws IOException
	 * 		if a stream exception occurs
	 * @throws SyncException
	 * 		if something unexpected has been read from the stream
	 */
	public boolean readSyncRequestResponse() throws IOException, SyncException {
		final byte b = readByte();
		if (b == ByteConstants.COMM_SYNC_NACK) {
			// sync rejected
			return false;
		}
		if (b != ByteConstants.COMM_SYNC_ACK) {
			throw new SyncException(String.format(
					"COMM_SYNC_REQUEST was sent but reply was %02x instead of COMM_SYNC_ACK or COMM_SYNC_NACK", b));
		}
		return true;
	}

	/**
	 * Read the other node's generation numbers from an input stream
	 *
	 * @throws IOException
	 * 		if a stream exception occurs
	 */
	public SyncGenerations readGenerations() throws IOException {
		return readSerializable(false, SyncGenerations::new);
	}

	/**
	 * Read the other node's tip hashes
	 *
	 * @throws IOException
	 * 		is a stream exception occurs
	 */
	public List<Hash> readTipHashes(final int numberOfNodes) throws IOException {
		return readSerializableList(
				numberOfNodes * MAX_TIPS_PER_NODE,
				false,
				Hash::new);
	}

	public GossipEvent readEventData() throws IOException {
		final BaseEventHashedData hashedData = readSerializable(false, BaseEventHashedData::new);
		final BaseEventUnhashedData unhashedData = readSerializable(false, BaseEventUnhashedData::new);

		return new GossipEvent(hashedData, unhashedData);
	}
}
