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

package com.swirlds.common.crypto;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.logging.LogMarker;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class SerializablePublicKey implements SelfSerializable {
	private static final long CLASS_ID = 0x2554c14f4f61cd9L;
	private static final int CLASS_VERSION = 2;
	private static final int MAX_KEY_LENGTH = 6_144;
	private static final int MAX_ALG_LENGTH = 10;

	private PublicKey publicKey;
	private KeyType keyType;

	/**
	 * {@inheritDoc}
	 */
	public SerializablePublicKey() {
	}

	/**
	 * {@inheritDoc}
	 */
	public SerializablePublicKey(PublicKey publicKey) {
		if (publicKey == null) {
			// null will not be supported for the time being, or maybe never
			throw new IllegalArgumentException("publicKey must not be null!");
		}
		this.publicKey = publicKey;
		this.keyType = KeyType.getKeyType(publicKey);
	}

	/**
	 * Getter that the returns the underlying JCE {@link PublicKey} implementation.
	 *
	 * @return the public key
	 */
	public PublicKey getPublicKey() {
		return publicKey;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(keyType.getAlgorithmIdentifier());
		out.writeByteArray(publicKey.getEncoded());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		if (version == 1) {
			String algorithm = in.readNormalisedString(MAX_ALG_LENGTH);
			keyType = KeyType.valueOf(algorithm);
		} else {
			keyType = KeyType.getKeyType(in.readInt());
		}
		byte[] keyBytes = in.readByteArray(MAX_KEY_LENGTH);
		publicKey = bytesToPublicKey(keyBytes, keyType.getAlgorithmName());
	}

	/**
	 * Converts an encoded public key representation from the given {@code bytes} argument to a {@link PublicKey}
	 * instance.
	 *
	 * @param bytes
	 * 		the encoded public key
	 * @param keyType
	 * 		the JCE key algorithm identifier
	 * @return the public key
	 * @throws CryptographyException
	 * 		if the algorithm is not available or the encoded key is invalid
	 */
	public static PublicKey bytesToPublicKey(byte[] bytes, String keyType) {
		try {
			EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
			KeyFactory keyFactory = KeyFactory.getInstance(keyType);
			return keyFactory.generatePublic(publicKeySpec);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
			throw new CryptographyException(ex, LogMarker.EXCEPTION);
		}
	}

	/**
	 * A method used to deserialize a public key before if had a version number
	 *
	 * @param in
	 * 		the stream to read from
	 * @param algorithm
	 * 		the algorithm of the key, this was not stored in the stream before
	 * @throws IOException
	 * 		thrown if an IO error happens
	 */
	public void deserializeVersion0(SerializableDataInputStream in, String algorithm) throws IOException {
		keyType = KeyType.valueOf(algorithm);
		byte[] keyBytes = in.readByteArray(MAX_KEY_LENGTH);
		publicKey = bytesToPublicKey(keyBytes, algorithm);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		SerializablePublicKey that = (SerializablePublicKey) o;
		return publicKey.equals(that.publicKey) &&
				keyType == that.keyType;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "SerializablePublicKey{" +
				"publicKey=" + (publicKey == null ? null : Arrays.toString(publicKey.getEncoded())) +
				", keyType=" + keyType +
				'}';
	}
}
