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

package com.swirlds.platform.network;

public final class ByteConstants {
	/** periodically sent to the SyncListener to keep connections alive */
	public static final byte HEARTBEAT = 0x40 /* 64 */;
	/** a reply sent back when a heartbeat is received by the SyncListener */
	public static final byte HEARTBEAT_ACK = 0x41 /* 65 */;
	/** sent to request a sync */
	public static final byte COMM_SYNC_REQUEST = 0x42 /* 66 */;
	/** sent as a reply to COMM_SYNC_REQUEST when accepting an incoming sync request */
	public static final byte COMM_SYNC_ACK = 0x43 /* 67 */;
	/** sent as a reply to COMM_SYNC_REQUEST when rejecting an incoming sync request (because too busy) */
	public static final byte COMM_SYNC_NACK = 0x44 /* 68 */;
	/** sent at the end of a sync, to show it's done */
	public static final byte COMM_SYNC_DONE = 0x45 /* 69 */;
	/** optionally sent during event transfer phase to assure the peer the connection is still open */
	public static final byte COMM_SYNC_ONGOING = 0x46 /* 70 */;
	/** sent after a new socket connection is made */
	public static final byte COMM_CONNECT = 0x47 /* 71 */;
	/** sent before sending each event */
	public static final byte COMM_EVENT_NEXT = 0x48 /* 72 */;
	/** sent after all events have been sent for this sync */
	public static final byte COMM_EVENT_DONE = 0x4a /* 74 */;
	/** sent if a node wants to get the latest signed state */
	public static final byte COMM_STATE_REQUEST = 0x4c /* 76 */;
	/** sent as a reply to COMM_STATE_REQUEST when accepting to transfer the latest state */
	public static final byte COMM_STATE_ACK = 0x4d /* 77 */;
	/** sent as a reply to COMM_STATE_REQUEST when NOT accepting to transfer the latest state */
	public static final byte COMM_STATE_NACK = 0x4e /* 78 */;
	/**
	 * Private constructor to never instantiate this class
	 */
	private ByteConstants() {
	}
}
