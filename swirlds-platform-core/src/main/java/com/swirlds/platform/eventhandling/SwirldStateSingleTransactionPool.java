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
package com.swirlds.platform.eventhandling;

import com.swirlds.common.Transaction;
import com.swirlds.platform.SettingsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * Store additional lists of transactions used to keep the three states used by {@link
 * com.swirlds.platform.state.SwirldStateManagerSingle} up to date with self transactions.
 */
public class SwirldStateSingleTransactionPool extends EventTransactionPool {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/** list of transactions by self waiting to be handled by doCurr */
	private volatile LinkedList<Transaction> transCurr = new LinkedList<>();
	/** list of transactions by self waiting to be handled by doWork */
	private volatile LinkedList<Transaction> transWork = new LinkedList<>();
	/** list of transactions by self waiting to be handled by doCons (which just passes them on) */
	private final LinkedList<Transaction> transCons = new LinkedList<>();

	public SwirldStateSingleTransactionPool(final SettingsProvider settings) {
		super(settings);
	}

	@Override
	public synchronized boolean submitTransaction(final Transaction trans) {
		final int t = settings.getThrottleTransactionQueueSize();

		// In order to be submitted, the transaction must be a system transaction, or all the queues must have room.
		if (!trans.isSystem() &&
				(transEvent.size() > t
						|| transCurr.size() > t
						|| transCons.size() > t
						|| transWork.size() > t)) {
			return false;
		}

		// this should stay true. The linked lists will never be full or return false.
		final boolean ans = offerToEventQueue(trans)
				&& transCurr.offer(trans)
				&& transCons.offer(trans)
				&& transWork.offer(trans);

		if (!ans) {
			LOG.error(EXCEPTION.getMarker(),
					"SwirldStateSingleTransactionPool queues were all shorter than Settings" +
							".throttleTransactionQueueSize, yet offer returned false");
		}
		return ans; // this will be true, unless a bad error has occurred
	}

	/** remove and return the earliest-added event in transCurr, or null if none */
	public synchronized Transaction pollCurr() {
		return transCurr.poll();
	}

	/** remove and return the earliest-added event in transWork, or null if none */
	public synchronized Transaction pollWork() {
		return transWork.poll();
	}

	/** remove and return the earliest-added event in transCons, or null if none */
	public synchronized Transaction pollCons() {
		return transCons.poll();
	}

	/**
	 * get the number of transactions in the transCurr queue (to be handled by stateCurr)
	 *
	 * @return the number of transactions
	 */
	public synchronized int getCurrSize() {
		return transCurr.size();
	}

	/**
	 * get the number of transactions in the transWork queue (to be handled by stateWork)
	 *
	 * @return the number of transactions
	 */
	public synchronized int getWorkSize() {
		return transWork.size();
	}

	/**
	 * get the number of transactions in the transCons queue
	 *
	 * @return the number of transactions
	 */
	public synchronized int getConsSize() {
		return transCons.size();
	}

	/** Do a shuffle: discard transCurr, move transWork to transCurr, clone transCons to transWork */
	@SuppressWarnings("unchecked") // needed because stupid Java type erasure gives no alternative
	public synchronized void shuffle() {
		transCurr = transWork;
		transWork = (LinkedList<Transaction>) transCons.clone();
	}

	/**
	 * return a single string giving the number of transactions in each list
	 */
	@Override
	public synchronized String status() {
		return "SwirldStateSingleTransactionPool sizes:"
				+ "transEvent=" + transEvent.size()
				+ " transCurr=" + transCurr.size()
				+ " transWork=" + transWork.size()
				+ " transCons=" + transCons.size();
	}

	/**
	 * Clear all the transactions from SwirldStateSingleTransactionPool
	 */
	@Override
	public synchronized void clear() {
		super.clear();
		transCurr.clear();
		transWork.clear();
		transCons.clear();
	}
}
