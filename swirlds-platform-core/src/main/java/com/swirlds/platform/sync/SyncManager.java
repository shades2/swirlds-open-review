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

import com.swirlds.common.NodeId;

import java.util.List;

public interface SyncManager {
	boolean shouldAcceptSync();

	boolean shouldInitiateSync();

	List<Long> getNeighborsToCall();

	boolean transThrottle();

	boolean transThrottleCallAndCreate();

	boolean shouldCreateEvent(NodeId otherId, boolean oneNodeFallenBehind, int eventsRead, int eventsWritten);

	boolean shouldCreateEvent(final SyncResult info);

	void successfulSync();
}
