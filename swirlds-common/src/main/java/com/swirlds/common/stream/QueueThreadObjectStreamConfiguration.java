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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.threading.QueueThreadConfiguration;

/**
 * Configures and builds {@link QueueThreadObjectStream} instances.
 *
 * @param <T>
 * 		the type of the object in the stream
 */
public class QueueThreadObjectStreamConfiguration<T extends RunningHashable> {

	private LinkedObjectStream<T> forwardTo;

	private final QueueThreadConfiguration<T> queueThreadConfiguration;

	/**
	 * Intentionally private, use static factory method.
	 */
	public QueueThreadObjectStreamConfiguration() {
		queueThreadConfiguration = new QueueThreadConfiguration<>();
	}

	/**
	 * Build a new thread.
	 */
	public QueueThreadObjectStream<T> build() {
		if (forwardTo == null) {
			throw new NullPointerException("forwardTo is null");
		}

		return new QueueThreadObjectStream<>(this);
	}

	/**
	 * Set the object stream to forward values to.
	 */
	public LinkedObjectStream<T> getForwardTo() {
		return forwardTo;
	}

	/**
	 * Get the object stream to forward values to.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setForwardTo(final LinkedObjectStream<T> forwardTo) {
		this.forwardTo = forwardTo;
		return this;
	}

	/**
	 * Get the capacity for created threads.
	 */
	public int getCapacity() {
		return queueThreadConfiguration.getCapacity();
	}

	/**
	 * Set the capacity for created threads.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setCapacity(final int capacity) {
		queueThreadConfiguration.setCapacity(capacity);
		return this;
	}

	/**
	 * Get the maximum buffer size for created threads. Buffer size is not the same as queue capacity, it has to do
	 * with the buffer that is used when draining the queue.
	 */
	public int getMaxBufferSize() {
		return queueThreadConfiguration.getMaxBufferSize();
	}

	/**
	 * Set the maximum buffer size for created threads. Buffer size is not the same as queue capacity, it has to do
	 * with the buffer that is used when draining the queue.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setMaxBufferSize(final int maxBufferSize) {
		queueThreadConfiguration.setMaxBufferSize(maxBufferSize);
		return this;
	}

	/**
	 * Get the the thread group that new threads will be created in.
	 */
	public ThreadGroup getThreadGroup() {
		return queueThreadConfiguration.getThreadGroup();
	}

	/**
	 * Set the the thread group that new threads will be created in.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setThreadGroup(final ThreadGroup threadGroup) {
		queueThreadConfiguration.setThreadGroup(threadGroup);
		return this;
	}

	/**
	 * Get the daemon behavior of new threads.
	 */
	public boolean isDaemon() {
		return queueThreadConfiguration.isDaemon();
	}

	/**
	 * Set the daemon behavior of new threads.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setDaemon(final boolean daemon) {
		queueThreadConfiguration.setDaemon(daemon);
		return this;
	}

	/**
	 * Get the priority of new threads.
	 */
	public int getPriority() {
		return queueThreadConfiguration.getPriority();
	}

	/**
	 * Set the priority of new threads.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setPriority(final int priority) {
		queueThreadConfiguration.setPriority(priority);
		return this;
	}

	/**
	 * Get the class loader for new threads.
	 */
	public ClassLoader getContextClassLoader() {
		return queueThreadConfiguration.getContextClassLoader();
	}

	/**
	 * Set the class loader for new threads.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setContextClassLoader(final ClassLoader contextClassLoader) {
		queueThreadConfiguration.setContextClassLoader(contextClassLoader);
		return this;
	}

	/**
	 * Get the exception handler for new threads.
	 */
	public Thread.UncaughtExceptionHandler getExceptionHandler() {
		return queueThreadConfiguration.getExceptionHandler();
	}

	/**
	 * Set the exception handler for new threads.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setExceptionHandler(
			final Thread.UncaughtExceptionHandler exceptionHandler) {
		queueThreadConfiguration.setExceptionHandler(exceptionHandler);
		return this;
	}

	/**
	 * Get the node ID that will run threads created by this object.
	 */
	public long getNodeId() {
		return queueThreadConfiguration.getNodeId();
	}

	/**
	 * Set the node ID. Node IDs less than 0 are interpreted as "no node ID".
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setNodeId(final long nodeId) {
		queueThreadConfiguration.setNodeId(nodeId);
		return this;
	}

	/**
	 * Get the name of the component that new threads will be associated with.
	 */
	public String getComponent() {
		return queueThreadConfiguration.getComponent();
	}

	/**
	 * Set the name of the component that new threads will be associated with.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setComponent(final String component) {
		queueThreadConfiguration.setComponent(component);
		return this;
	}

	/**
	 * Get the name for created threads.
	 */
	public String getThreadName() {
		return queueThreadConfiguration.getThreadName();
	}

	/**
	 * Set the name for created threads.
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setThreadName(final String threadName) {
		queueThreadConfiguration.setThreadName(threadName);
		return this;
	}

	/**
	 * Set the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 */
	public long getOtherNodeId() {
		return queueThreadConfiguration.getOtherNodeId();
	}

	/**
	 * Get the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 *
	 * @return this object
	 */
	public QueueThreadObjectStreamConfiguration<T> setOtherNodeId(final long otherNodeId) {
		queueThreadConfiguration.setOtherNodeId(otherNodeId);
		return this;
	}

	/**
	 * Intentionally package private. Get the underlying queue thread configuration.
	 */
	QueueThreadConfiguration<T> getQueueThreadConfiguration() {
		return queueThreadConfiguration;
	}
}
