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

package com.swirlds.platform.chatter;

import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventAddedObserver;

/**
 * Transfers information from consensus to the chatter module
 */
public class ChatterNotifier implements EventAddedObserver, ConsensusRoundObserver {
	private final ChatterCore<GossipEvent> chatterCore;

	public ChatterNotifier(final ChatterCore<GossipEvent> chatterCore) {
		this.chatterCore = chatterCore;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusRound(final ConsensusRound consensusRound) {
		chatterCore.purge(
				consensusRound.getGenerations().getMinRoundGeneration()
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void eventAdded(final EventImpl event) {
		chatterCore.handleMessage(event.getBaseEvent());
	}
}
