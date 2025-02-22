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

package com.swirlds.common.merkle.synchronization.internal;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

/**
 * When the teacher submits a query of "do you have this node", the learner replies with a QueryResponse.
 */
public class QueryResponse implements SelfSerializable {

	private static final long CLASS_ID = 0x7CBF61E166C6E5F7L;

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	protected boolean learnerHasTheNode;

	public QueryResponse() {

	}

	/**
	 * Construct a query response.
	 *
	 * @param learnerHasTheNode
	 * 		true if this node (the learner) has the given response
	 */
	public QueryResponse(boolean learnerHasTheNode) {
		this.learnerHasTheNode = learnerHasTheNode;
	}

	/**
	 * Does the learner have the node in question?
	 *
	 * @return true if the learner has the node
	 */
	public boolean doesLearnerHaveTheNode() {
		return learnerHasTheNode;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeBoolean(learnerHasTheNode);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		learnerHasTheNode = in.readBoolean();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}
