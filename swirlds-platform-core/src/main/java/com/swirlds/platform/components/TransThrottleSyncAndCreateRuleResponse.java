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

package com.swirlds.platform.components;

/**
 * Types of response of {@link TransThrottleSyncAndCreateRule}
 */
public enum TransThrottleSyncAndCreateRuleResponse {
	/**
	 * should not initiate a sync and create an event, and don't check subsequent rules
	 */
	DONT_SYNC_OR_CREATE,
	/**
	 * should initiate a sync and create an event, and don't check subsequent rules
	 */
	SYNC_AND_CREATE,
	/**
	 * continue with checking subsequent rules
	 */
	PASS
}
