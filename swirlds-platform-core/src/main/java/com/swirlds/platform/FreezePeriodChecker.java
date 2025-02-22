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

package com.swirlds.platform;

import java.time.Instant;

/**
 * Checks whether a timestamp is in freeze period
 */
public interface FreezePeriodChecker {
	/**
	 * Checks whether the given instant is in the freeze period
	 * Only when the timestamp is not before freezeTime, and freezeTime is after lastFrozenTime,
	 * the timestamp is in the freeze period.
	 *
	 * @param timestamp
	 * 		an Instant to check
	 * @return true if it is in the freeze period, false otherwise
	 */
	boolean isInFreezePeriod(Instant timestamp);
}
