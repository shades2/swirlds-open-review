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

package com.swirlds.common.events;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;

/**
 * A class used to store base event data that does not affect the hash of that event.
 * <p>
 * A base event is a set of data describing an event at the point when it is created, before it is added to the
 * hashgraph and before its consensus can be determined. Some of this data is used to create a hash of an event
 * that is signed, and some data is additional and does not affect that hash. This data is split into 2 classes:
 * {@link BaseEventHashedData} and {@link BaseEventUnhashedData}.
 */
public class BaseEventUnhashedData implements SelfSerializable {
	private static final long CLASS_ID = 0x33cb9d4ae38c9e91L;
	private static final int CLASS_VERSION = 1;
	private static final int MAX_SIG_LENGTH = 384;
	private static final long SEQUENCE_UNUSED = -1;

	///////////////////////////////////////
	// immutable, sent during normal syncs, does NOT affect the hash that is signed:
	///////////////////////////////////////

	//--------------- NOTE -------------------------------------------------------------------------------------------
	// Sequence number fields are no longer in use, so when an object is constructed, they are set to SEQUENCE_UNUSED
	// These fields are still kept because there are a lot of downstream consequences of changing the event stream
	// format, so they will be removed along with other fields that should not be part of the stream.
	// otherId is also probably not needed anymore, so at some point this class can be replaced with just a Signature
	//----------------------------------------------------------------------------------------------------------------

	/** sequence number for this by its creator (0 is first) */
	private long creatorSeq;
	/** ID of otherParent (translate before sending) */
	private long otherId;
	/** sequence number for otherParent event (by its creator) */
	private long otherSeq;
	/** creator's sig for this */
	private byte[] signature;

	public BaseEventUnhashedData() {
	}

	public BaseEventUnhashedData(final long otherId, final byte[] signature) {
		this.creatorSeq = SEQUENCE_UNUSED;
		this.otherId = otherId;
		this.signature = signature;
		this.otherSeq = SEQUENCE_UNUSED;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(creatorSeq);
		out.writeLong(otherId);
		out.writeLong(otherSeq);
		out.writeByteArray(signature);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		creatorSeq = in.readLong();
		otherId = in.readLong();
		otherSeq = in.readLong();
		signature = in.readByteArray(MAX_SIG_LENGTH);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final BaseEventUnhashedData that = (BaseEventUnhashedData) o;

		return new EqualsBuilder()
				.append(creatorSeq, that.creatorSeq)
				.append(otherId, that.otherId)
				.append(otherSeq, that.otherSeq)
				.append(signature, that.signature)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(creatorSeq)
				.append(otherId)
				.append(otherSeq)
				.append(signature)
				.toHashCode();
	}

	@Override
	public String toString() {
		final int signatureLength = signature == null ? 0 : signature.length;
		return "BaseEventUnhashedData{" +
				"creatorSeq=" + creatorSeq +
				", otherId=" + otherId +
				", otherSeq=" + otherSeq +
				", signature=" + CommonUtils.hex(signature, signatureLength) +
				'}';
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	public long getOtherId() {
		return otherId;
	}

	public byte[] getSignature() {
		return signature;
	}
}
