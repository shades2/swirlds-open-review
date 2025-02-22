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

package com.swirlds.common.list;

public class ListDigestException extends ListException {

	/**
	 * Constructs a new runtime exception with {@code null} as its
	 * detail message.  The cause is not initialized, and may subsequently be
	 * initialized by a call to {@link #initCause}.
	 */
	public ListDigestException() {
	}

	/**
	 * Constructs a new runtime exception with the specified detail message.
	 * The cause is not initialized, and may subsequently be initialized by a
	 * call to {@link #initCause}.
	 *
	 * @param message
	 * 		the detail message. The detail message is saved for
	 * 		later retrieval by the {@link #getMessage()} method.
	 */
	public ListDigestException(final String message) {
		super(message);
	}

	/**
	 * Constructs a new runtime exception with the specified detail message and
	 * cause.  <p>Note that the detail message associated with
	 * {@code cause} is <i>not</i> automatically incorporated in
	 * this runtime exception's detail message.
	 *
	 * @param message
	 * 		the detail message (which is saved for later retrieval
	 * 		by the {@link #getMessage()} method).
	 * @param cause
	 * 		the cause (which is saved for later retrieval by the
	 *        {@link #getCause()} method).  (A {@code null} value is
	 * 		permitted, and indicates that the cause is nonexistent or
	 * 		unknown.)
	 * @since 1.4
	 */
	public ListDigestException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new runtime exception with the specified cause and a
	 * detail message of {@code (cause==null ? null : cause.toString())}
	 * (which typically contains the class and detail message of
	 * {@code cause}).  This constructor is useful for runtime exceptions
	 * that are little more than wrappers for other throwables.
	 *
	 * @param cause
	 * 		the cause (which is saved for later retrieval by the
	 *        {@link #getCause()} method).  (A {@code null} value is
	 * 		permitted, and indicates that the cause is nonexistent or
	 * 		unknown.)
	 * @since 1.4
	 */
	public ListDigestException(final Throwable cause) {
		super(cause);
	}
}
