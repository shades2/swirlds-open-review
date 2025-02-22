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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.internal.NodeToSend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A teaching tree view for a standard in memory merkle tree.
 */
public class StandardTeacherTreeView implements TeacherTreeView<NodeToSend> {

	private final Queue<NodeToSend> nodesToHandle;
	private final BlockingQueue<NodeToSend> expectedResponses;

	private final NodeToSend root;

	/**
	 * Create a view for a standard merkle tree.
	 *
	 * @param root
	 * 		the root of the tree
	 */
	public StandardTeacherTreeView(final MerkleNode root) {
		this.root = new NodeToSend(root);

		nodesToHandle = new LinkedList<>();
		expectedResponses = new LinkedBlockingDeque<>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeToSend getRoot() {
		return root;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addToHandleQueue(final NodeToSend node) {
		nodesToHandle.add(node);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeToSend getNextNodeToHandle() {
		return nodesToHandle.remove();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean areThereNodesToHandle() {
		return !nodesToHandle.isEmpty();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeToSend getChildAndPrepareForQueryResponse(final NodeToSend parent, final int childIndex) {
		final NodeToSend child = new NodeToSend(parent.getNode().asInternal().getChild(childIndex));
		parent.registerChild(child);

		if (!expectedResponses.add(child)) {
			throw new MerkleSynchronizationException("unable to add expected response to queue");
		}

		return child;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeToSend getNodeForNextResponse() {
		return expectedResponses.remove();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isResponseExpected() {
		return !expectedResponses.isEmpty();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void registerResponseForNode(final NodeToSend node, final boolean learnerHasNode) {
		node.registerResponse(learnerHasNode);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasLearnerConfirmedFor(final NodeToSend node) {
		node.waitForResponse();
		return node.getResponseStatus();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isInternal(final NodeToSend node, final boolean isOriginal) {
		// This implementation can safely ignore "isOriginal"
		final MerkleNode merkleNode = node.getNode();
		return merkleNode != null && !merkleNode.isLeaf();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId(final NodeToSend node) {
		final MerkleNode merkleNode = node.getNode();

		if (merkleNode == null) {
			throw new MerkleSynchronizationException("null has no class ID");
		}

		return merkleNode.getClassId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleNode getMerkleRoot(final NodeToSend node) {
		return node.getNode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberOfChildren(final NodeToSend node) {
		final MerkleNode merkleNode = node.getNode();

		if (merkleNode == null || merkleNode.isLeaf()) {
			throw new MerkleSynchronizationException("can not get number of children from node that is not internal");
		}

		return merkleNode.asInternal().getNumberOfChildren();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isCustomReconnectRoot(final NodeToSend node) {
		final MerkleNode merkleNode = node.getNode();
		return merkleNode != null && merkleNode.hasCustomReconnectView();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Hash> getChildHashes(final NodeToSend parent) {

		final MerkleNode node = parent.getNode();

		if (node == null || node.isLeaf()) {
			throw new MerkleSynchronizationException("can not get child hashes of null value");
		}
		if (node.isLeaf()) {
			throw new MerkleSynchronizationException("can not get child hashes of leaf");
		}

		final MerkleInternal internal = node.asInternal();

		final int childCount = internal.getNumberOfChildren();
		final List<Hash> hashes = new ArrayList<>(childCount);

		for (int childIndex = 0; childIndex < childCount; childIndex++) {
			final MerkleNode child = internal.getChild(childIndex);

			if (child == null) {
				hashes.add(CryptoFactory.getInstance().getNullHash());
			} else {
				final Hash hash = child.getHash();
				if (hash == null) {
					throw new MerkleSynchronizationException(node.getClass().getName() +
							" at position " + node.getRoute() + " is unhashed");
				}

				hashes.add(child.getHash());
			}
		}

		return hashes;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serializeLeaf(final SerializableDataOutputStream out, final NodeToSend leaf) throws IOException {
		final MerkleNode merkleNode = leaf.getNode();

		if (merkleNode == null) {
			out.writeSerializable(null, true);
			return;
		}

		if (!merkleNode.isLeaf()) {
			throw new MerkleSynchronizationException("this method can not serialize an internal node");
		}

		out.writeSerializable(merkleNode.asLeaf(), true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serializeInternal(final SerializableDataOutputStream out, final NodeToSend internal) throws IOException {
		final MerkleNode merkleNode = internal.getNode();

		if (merkleNode == null || merkleNode.isLeaf()) {
			throw new MerkleSynchronizationException("this method can not serialize a leaf node");
		}

		out.writeLong(merkleNode.getClassId());
	}
}
