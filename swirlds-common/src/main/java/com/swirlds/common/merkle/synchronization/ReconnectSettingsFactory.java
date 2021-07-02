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

package com.swirlds.common.merkle.synchronization;

import java.time.Duration;

/**
 * Utility class for fetching reconnect settings.
 */
public abstract class ReconnectSettingsFactory {

	private static ReconnectSettings reconnectSettings;

	public static void configure(ReconnectSettings reconnectSettings) {
		ReconnectSettingsFactory.reconnectSettings = reconnectSettings;
	}

	public static ReconnectSettings get() {
		if (reconnectSettings == null) {
			reconnectSettings = getDefaultSettings();
		}
		return reconnectSettings;
	}

	private static ReconnectSettings getDefaultSettings() {
		return new ReconnectSettings() {
			@Override
			public boolean isActive() {
				return true;
			}

			@Override
			public int getReconnectWindowSeconds() {
				return -1;
			}

			@Override
			public double getFallenBehindThreshold() {
				return 0.5;
			}

			@Override
			public int getAsyncInputStreamTimeoutMilliseconds() {
				return 10_000;
			}

			@Override
			public double getAsyncOutputStreamFlushFraction() {
				return 0.01;
			}

			@Override
			public boolean isAsyncStreams() {
				return true;
			}

			@Override
			public int getSendingSynchronizerResponseThreadPoolSize() {
				return 4;
			}

			@Override
			public int getHashValidationThreadPoolSize() {
				return 40;
			}

			@Override
			public int getMaxAckDelayMilliseconds() {
				return 10;
			}

			@Override
			public int getMaximumReconnectFailuresBeforeShutdown() {
				return 10;
			}

			@Override
			public Duration getMinimumTimeBetweenReconnects() {
				return Duration.ofMinutes(10);
			}
		};
	}
}
