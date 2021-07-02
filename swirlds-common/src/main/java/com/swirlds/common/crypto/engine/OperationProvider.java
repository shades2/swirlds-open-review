/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.crypto.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.NoSuchAlgorithmException;

/**
 * The abstract base class of all non-caching cryptographic transformation providers. Provides an abstraction that
 * allows a single implementation to support multiple algorithms of the same type and a plugin model for switching
 * provider implementations with ease. Generic enough to support multiple types of cryptographic transformations.
 *
 * This class does not guarantee that any caching of algorithm instances will be performed and it is recommended that
 * implementors of this base class should return a new instance of the algorithm implementation every time {@link
 * #loadAlgorithm(Enum)} is called.
 *
 * Not all implementations need to use the OptionalData field. For these implementations, the object of type
 * Element contains all data required to perform the operation. For classes that do not utilize OptionalData,
 * it is recommended to set it to type Void.
 *
 * @param <Element>
 * 		the type of the input to be transformed
 * @param <OptionalData>
 * 		the type of optional data to be used when performing the operation, may be Void type
 * @param <Result>
 * 		the type of the output resulting from the transformation
 * @param <Alg>
 * 		the type of the algorithm implementation
 * @param <AlgType>
 * 		the type of the enumeration providing the list of available algorithms
 */
public abstract class OperationProvider<Element, OptionalData, Result, Alg, AlgType extends Enum> {

	/**
	 * the logger all implementations should use for reporting issues or diagnostic messages
	 */
	private final Logger log;

	/**
	 * Default Constructor. Initializes the {@link Logger} to be used by the implementation.
	 */
	public OperationProvider() {
		this.log = LogManager.getLogger(this.getClass());
	}

	/**
	 * Provides the implementor with access to the {@link Logger}.
	 *
	 * @return an initialized logger
	 */
	protected Logger log() {
		return log;
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm.
	 * This method calls the {@link #loadAlgorithm(Enum)} method and then delegates to the {@link #handleItem(Object,
	 * Enum, Object, Object)} method to perform the actual cryptographic transformation.
	 *
	 * @param item
	 * 		the input to be transformed by the given algorithm
	 * @param optionalData
	 * 		Additional data to be transformed by the given algorithm.
	 * @param algorithmType
	 * 		the algorithm to be used when performing the cryptographic transformation
	 * @return the result of the cryptographic transformation
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	public Result compute(final Element item, final OptionalData optionalData, final AlgType algorithmType)
			throws NoSuchAlgorithmException {
		return handleItem(loadAlgorithm(algorithmType), algorithmType, item, optionalData);
	}

	/**
	 * Same as {@link OperationProvider#compute(Object, Object, Enum)}, but the optional data is set to Null.
	 *
	 * @param item
	 * 		the input to be transformed by the given algorithm
	 * @param algorithmType
	 * 		the algorithm to be used when performing the cryptographic transformation
	 * @return the result of the cryptographic transformation
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	public Result compute(final Element item, final AlgType algorithmType)
			throws NoSuchAlgorithmException {
		return compute(item, null, algorithmType);
	}

	/**
	 * Loads a concrete implementation of the required algorithm. This method may choose to return the same instance
	 * or a new instance of the algorithm on each invocation.
	 *
	 * @param algorithmType
	 * 		the type of algorithm to be loaded
	 * @return a concrete implementation of the required algorithm
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 * @see CachingOperationProvider#loadAlgorithm(Enum)
	 * @see CachingOperationProvider#handleAlgorithmRequired(Enum)
	 */
	protected abstract Alg loadAlgorithm(final AlgType algorithmType) throws NoSuchAlgorithmException;

	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm. This method must
	 * use the provided instance of the required algorithm as given by the {@code algorithm} parameter.
	 *
	 * @param algorithm
	 * 		the concrete instance of the required algorithm
	 * @param algorithmType
	 * 		the type of algorithm to be used when performing the transformation
	 * @param item
	 * 		the input to be transformed
	 * @return the result of the cryptographic transformation
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	protected abstract Result handleItem(final Alg algorithm, final AlgType algorithmType,
			final Element item, final OptionalData optionalData) throws NoSuchAlgorithmException;

}
