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

package com.swirlds.p2p.portforwarding;

import com.swirlds.p2p.portforwarding.PortForwarder.Protocol;

public class PortMapping {
	private final String ip;
	private final int internalPort;
	private final int externalPort;
	private final Protocol protocol;

	public PortMapping(final String ip, final int internalPort, final int externalPort, Protocol protocol) {
		this.ip = ip;
		this.internalPort = internalPort;
		this.externalPort = externalPort;
		this.protocol = protocol;
	}

	public String getIp() {
		return ip;
	}

	public int getInternalPort() {
		return internalPort;
	}

	public int getExternalPort() {
		return externalPort;
	}

	public Protocol getProtocol() {
		return protocol;
	}
}
