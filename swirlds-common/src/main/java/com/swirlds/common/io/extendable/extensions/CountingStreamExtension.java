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

package com.swirlds.common.io.extendable.extensions;

import com.swirlds.common.Units;
import com.swirlds.common.io.extendable.extensions.internal.Counter;
import com.swirlds.common.io.extendable.extensions.internal.StandardCounter;
import com.swirlds.common.io.extendable.extensions.internal.ThreadSafeCounter;

/**
 * A stream extension that counts the number of bytes that pass through it
 */
public class CountingStreamExtension extends AbstractStreamExtension {

	private final Counter counter;

	/**
	 * Create a new thread safe counting stream extension.
	 */
	public CountingStreamExtension() {
		this(true);
	}

	/**
	 * Create a counting stream extension.
	 *
	 * @param threadSafe
	 * 		if true then the extension will be thread safe, if false then it will not be thread safe
	 * 		(and perhaps it will be slightly more performant)
	 */
	public CountingStreamExtension(final boolean threadSafe) {
		if (threadSafe) {
			counter = new ThreadSafeCounter();
		} else {
			counter = new StandardCounter();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void newByte(final int aByte) {
		counter.addToCount(1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void newBytes(final byte[] bytes, final int offset, final int length) {
		counter.addToCount(length);
	}

	/**
	 * Resets the number of bytes read
	 */
	public void resetCount() {
		counter.resetCount();
	}

	/**
	 * @return the number of bytes that have passed by this stream since the last reset
	 */
	public long getCount() {
		return counter.getCount();
	}

	/**
	 * Get the number of bytes, in kibibytes.
	 *
	 * @return the number of bytes that have passed by this stream since the last reset
	 */
	public double getKibiBytes() {
		return counter.getCount() * Units.BYTES_TO_KIBIBYTES;
	}

	/**
	 * Get the number of bytes, in mebibytes.
	 *
	 * @return the number of bytes that have passed by this stream since the last reset
	 */
	public double getMebiBytes() {
		return counter.getCount() * Units.BYTES_TO_MEBIBYTES;
	}

	/**
	 * Get the number of bytes, in gibibytes.
	 *
	 * @return the number of bytes that have passed by this stream since the last reset
	 */
	public double getGibiBytes() {
		return counter.getCount() * Units.BYTES_TO_GIBIBYTES;
	}

	/**
	 * Returns the number bytes read and resets the count to 0
	 *
	 * @return the number of bytes read since the last reset
	 */
	public long getAndResetCount() {
		return counter.getAndResetCount();
	}
}
