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

package com.swirlds.common.io;

import com.swirlds.common.constructable.RuntimeConstructable;

/**
 * An object implementing this interface will have a way of serializing and deserializing itself. This
 * serialization must deterministically generate the same output bytes every time. If the bytes generated
 * by the serialization algorithm change due to code changes then then this must be captured via a protocol
 * version increase. SerializableDet objects are required to maintain the capability of deserializing objects
 * serialized using old protocols.
 */
public interface SerializableDet extends RuntimeConstructable, Versioned {

	/**
	 * Any version lower than this is not supported and will cause
	 * an exception to be thrown if it is attempted to be used.
	 *
	 * @return minimum supported version number
	 */
	default int getMinimumSupportedVersion() {
		return 1;
	}

}
