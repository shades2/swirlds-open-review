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

package com.swirlds.common.locks;

/**
 * Return an instance of this when a {@link ResourceLock} has not been acquired
 */
public class NotAcquiredResource<T> implements MaybeLockedResource<T> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		// do nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getResource() {
		throw new IllegalStateException("Cannot get resource if the lock is not obtained");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setResource(T resource) {
		throw new IllegalStateException("Cannot set resource if the lock is not obtained");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLockAcquired() {
		return false;
	}
}
