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

package com.swirlds.common.threading;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An object used to configure and build {@link QueueThread}s.
 *
 * @param <T>
 * 		the type held by the queue
 */
public class QueueThreadConfiguration<T> {

	/**
	 * The maximum capacity of the queue. If -1 then there is no maximum capacity.
	 */
	private int capacity;

	/**
	 * The maximum size of the buffer used to drain the queue.
	 */
	private int maxBufferSize;

	/**
	 * The method used to handle items from the queue.
	 */
	private QueueThreadHandler<T> handler;

	/**
	 * A collection of thresholds that can be triggered based on the size of the queue.
	 */
	private final List<QueueThreadThreshold> thresholds;

	/** A runnable to execute when waiting for an item to become available in the queue. */
	private InterruptableRunnable waitForItemRunnable;

	/** An initialized queue to use. */
	private BlockingQueue<T> queue;

	/** The interruptable status of the thread. Queue threads are not interruptable by default */
	private boolean interruptable = false;
	
	private final StoppableThreadConfiguration<InterruptableRunnable> stoppableThreadConfiguration;

	public static final int DEFAULT_CAPACITY = 100;
	public static final int DEFAULT_MAX_BUFFER_SIZE = 10_000;
	public static final int UNLIMITED_CAPACITY = -1;

	/**
	 * Build a new queue thread configuration with default values.
	 */
	public QueueThreadConfiguration() {
		capacity = DEFAULT_CAPACITY;
		maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;
		thresholds = new LinkedList<>();
		stoppableThreadConfiguration = new StoppableThreadConfiguration<>();

		// Queue threads are always pausable
		stoppableThreadConfiguration.setPausable(true);
	}

	/**
	 * Build a new queue thread. Does not start the thread.
	 *
	 * @return a queue thread built using this configuration
	 */
	public QueueThread<T> build() {
		return build(false);
	}

	/**
	 * Build a new queue thread.
	 *
	 * @param start
	 * 		if true then start the thread
	 * @return a queue thread built using this configuration
	 */
	public QueueThread<T> build(final boolean start) {
		if (handler == null) {
			throw new NullPointerException("handler must not be null");
		}

		final QueueThread<T> thread = new QueueThread<>(this);

		if (start) {
			thread.start();
		}

		return thread;
	}

	/**
	 * Get the capacity for created threads. If -1 then the queue has no maximum capacity.
	 */
	public int getCapacity() {
		return capacity;
	}

	/**
	 * Set the capacity for created threads.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setCapacity(final int capacity) {
		this.capacity = capacity;
		return this;
	}

	/**
	 * Set the capacity to unlimited.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setUnlimitedCapacity() {
		this.capacity = UNLIMITED_CAPACITY;
		return this;
	}

	/**
	 * Get the maximum buffer size for created threads. Buffer size is not the same as queue capacity, it has to do
	 * with the buffer that is used when draining the queue.
	 */
	public int getMaxBufferSize() {
		return maxBufferSize;
	}

	/**
	 * Set the maximum buffer size for created threads. Buffer size is not the same as queue capacity, it has to do
	 * with the buffer that is used when draining the queue.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setMaxBufferSize(final int maxBufferSize) {
		this.maxBufferSize = maxBufferSize;
		return this;
	}

	/**
	 * Get the handler method that will be called against every item in the queue.
	 */
	public QueueThreadHandler<T> getHandler() {
		return handler;
	}

	/**
	 * Set the handler method that will be called against every item in the queue.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setHandler(final QueueThreadHandler<T> handler) {
		this.handler = handler;
		return this;
	}

	/**
	 * Get the the thread group that new threads will be created in.
	 */
	public ThreadGroup getThreadGroup() {
		return stoppableThreadConfiguration.getThreadGroup();
	}

	/**
	 * Set the the thread group that new threads will be created in.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setThreadGroup(final ThreadGroup threadGroup) {
		stoppableThreadConfiguration.setThreadGroup(threadGroup);
		return this;
	}

	/**
	 * Get the daemon behavior of new threads.
	 */
	public boolean isDaemon() {
		return stoppableThreadConfiguration.isDaemon();
	}

	/**
	 * Set the daemon behavior of new threads.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setDaemon(final boolean daemon) {
		stoppableThreadConfiguration.setDaemon(daemon);
		return this;
	}

	/**
	 * Get the priority of new threads.
	 */
	public int getPriority() {
		return stoppableThreadConfiguration.getPriority();
	}

	/**
	 * Set the priority of new threads.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setPriority(final int priority) {
		stoppableThreadConfiguration.setPriority(priority);
		return this;
	}

	/**
	 * Get the class loader for new threads.
	 */
	public ClassLoader getContextClassLoader() {
		return stoppableThreadConfiguration.getContextClassLoader();
	}

	/**
	 * Set the class loader for new threads.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setContextClassLoader(final ClassLoader contextClassLoader) {
		stoppableThreadConfiguration.setContextClassLoader(contextClassLoader);
		return this;
	}

	/**
	 * Get the exception handler for new threads.
	 */
	public Thread.UncaughtExceptionHandler getExceptionHandler() {
		return stoppableThreadConfiguration.getExceptionHandler();
	}

	/**
	 * Set the exception handler for new threads.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setExceptionHandler(final Thread.UncaughtExceptionHandler exceptionHandler) {
		stoppableThreadConfiguration.setExceptionHandler(exceptionHandler);
		return this;
	}

	/**
	 * Get the node ID that will run threads created by this object.
	 */
	public long getNodeId() {
		return stoppableThreadConfiguration.getNodeId();
	}

	/**
	 * Set the node ID. Node IDs less than 0 are interpreted as "no node ID".
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setNodeId(final long nodeId) {
		stoppableThreadConfiguration.setNodeId(nodeId);
		return this;
	}

	/**
	 * Get the name of the component that new threads will be associated with.
	 */
	public String getComponent() {
		return stoppableThreadConfiguration.getComponent();
	}

	/**
	 * Set the name of the component that new threads will be associated with.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setComponent(final String component) {
		stoppableThreadConfiguration.setComponent(component);
		return this;
	}

	/**
	 * Get the name for created threads.
	 */
	public String getThreadName() {
		return stoppableThreadConfiguration.getThreadName();
	}

	/**
	 * Set the name for created threads.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setThreadName(final String threadName) {
		stoppableThreadConfiguration.setThreadName(threadName);
		return this;
	}

	/**
	 * Set the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 */
	public long getOtherNodeId() {
		return stoppableThreadConfiguration.getOtherNodeId();
	}

	/**
	 * Get the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setOtherNodeId(final long otherNodeId) {
		stoppableThreadConfiguration.setOtherNodeId(otherNodeId);
		return this;
	}

	/**
	 * Get the period of time  that must elapse after a stop request before this
	 * thread is considered to be a hanging thread. A value of 0 means that the thread will never be considered
	 * to be a hanging thread.
	 */
	public Duration getHangingThreadPeriod() {
		return stoppableThreadConfiguration.getHangingThreadPeriod();
	}

	/**
	 * Set the period of time that must elapse after a stop request before this
	 * thread is considered to be a hanging thread. A value of 0 means that the thread will never be considered
	 * to be a hanging thread.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setHangingThreadPeriod(final Duration hangingThreadPeriod) {
		stoppableThreadConfiguration.setHangingThreadPeriod(hangingThreadPeriod);
		return this;
	}

	/**
	 * Set the runnable to execute when there is nothing in the queue to handle. The default is to poll the queue,
	 * waiting a certain amount of time for it to return an item.
	 *
	 * @param waitForItemRunnable
	 * 		the runnable to execute
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setWaitForItemRunnable(final InterruptableRunnable waitForItemRunnable) {
		this.waitForItemRunnable = waitForItemRunnable;
		return this;
	}

	/**
	 * Sets the initialized queue. The default is a {@link java.util.concurrent.LinkedBlockingQueue}.
	 *
	 * @param queue
	 * 		the initialized queue
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setQueue(final BlockingQueue<T> queue) {
		this.queue = queue;
		return this;
	}

	/**
	 * Set a threshold on the size of the queue. When the threshold is exceeded, an action is performed.
	 * <p>
	 * Threshold detection is a best effort operation. Due to the multithreading nature of a QueueThread,
	 * it is possible for the number of elements in the queue to briefly exceed a threshold without
	 * being detected. Although possible to provide stronger guarantees, doing so may have performance implications.
	 *
	 * @param threshold
	 * 		a method that defines the threshold. Should return true when the threshold is exceeded.
	 * @param action
	 * 		the action to take when the threshold is exceeded
	 * @param minimumPeriod
	 * 		the minimum amount of time that must pass before triggering the action another time.
	 * 		Prevents the action from being spammed if the threshold is exceeded very frequently.
	 * @return this object
	 */
	public QueueThreadConfiguration<T> addThreshold(
			final Predicate<Integer> threshold,
			final Consumer<Integer> action,
			final Duration minimumPeriod) {
		thresholds.add(new QueueThreadThreshold(threshold, action, minimumPeriod));
		return this;
	}

	public List<QueueThreadThreshold> getThresholds() {
		return thresholds;
	}

	/**
	 * Intentionally package private. Get the underlying stoppable thread configuration.
	 */
	StoppableThreadConfiguration<InterruptableRunnable> getStoppableThreadConfiguration() {
		return stoppableThreadConfiguration;
	}

	/**
	 * {@inheritDoc}
	 */
	public Duration getMinimumPeriod() {
		return stoppableThreadConfiguration.getMinimumPeriod();
	}

	/**
	 * {@inheritDoc}
	 */
	public QueueThreadConfiguration<T> setMinimumPeriod(final Duration minimumPeriod) {
		stoppableThreadConfiguration.setMinimumPeriod(minimumPeriod);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public double getMaximumRate() {
		return stoppableThreadConfiguration.getMaximumRate();
	}

	/**
	 * {@inheritDoc}
	 */
	public QueueThreadConfiguration<T> setMaximumRate(final double hz) {
		stoppableThreadConfiguration.setMaximumRate(hz);
		return this;
	}

	/**
	 * Get the runnable to execute when waiting for an item to become available in the queue.
	 */
	public InterruptableRunnable getWaitForItemRunnable() {
		return waitForItemRunnable;
	}

	/**
	 * Get the initialized queue. Returns null if none was provided.
	 */
	public BlockingQueue<T> getQueue() {
		return queue;
	}

	/**
	 * Defines the thread to be interruptable not. In general, queue threads should not be interruptable.
	 *
	 * @return this object
	 */
	public QueueThreadConfiguration<T> setInterruptable(final boolean interruptable) {
		this.interruptable = interruptable;
		return this;
	}

	/**
	 * Get whether this thread can be interrupted.
	 */
	public boolean isInterruptable() {
		return interruptable;
	}
}
