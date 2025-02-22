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

package com.swirlds.platform;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.ConsensusEvent;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.logging.payloads.StreamParseErrorPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.swirlds.common.stream.EventStreamType.EVENT;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getTimeStampFromFileName;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.parseStreamFile;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readFirstIntFromFile;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This class is used for state recovery.
 * Parse event stream files and playback event on given SwirldState object
 *
 * Running a different thread, parsing event files from given directory.
 * Searching event whose consensus timestamp following in the range of
 * start timestamp (exclusive) and end timestamp (inclusive), i.e., (startTimestamp, endTimestamp]
 */
public class StreamEventParser extends Thread {
	private static final Logger LOGGER = LogManager.getLogger();

	private final LinkedBlockingQueue<EventImpl> events = new LinkedBlockingQueue<>();

	private boolean isParsingDone = false;

	private static final int POLL_WAIT = 5000;

	private final String fileDir;
	private final Instant startTimestamp;
	private final Instant endTimestamp;
	private long eventsCounter;
	private EventImpl prevParsedEvent;

	/**
	 * current event stream version
	 */
	public static final int EVENT_STREAM_FILE_VERSION = 5;

	StreamEventParser(String fileDir, Instant startTimestamp, Instant endTimestamp) {
		this.fileDir = fileDir;
		this.startTimestamp = startTimestamp;
		this.endTimestamp = endTimestamp;
	}

	public EventImpl getNextEvent() {
		try {
			return events.poll(POLL_WAIT, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			LOGGER.info(EXCEPTION.getMarker(), "Unexpected", e);
			Thread.currentThread().interrupt();
			return null;
		}
	}

	public long getEventsCounter() {
		return eventsCounter;
	}

	/**
	 * whether we got all events
	 *
	 * @return whether the parser has processed all events
	 */
	public boolean noMoreEvents() {
		return isParsingDone && events.isEmpty();
	}

	/**
	 * Parsing event stream files from a specific folder with a search timestamp,
	 * then playback transactions inside those events
	 */
	private void eventPlayback() {
		parseEventFolder(this.fileDir, this::handleEvent);
		handleEvent(null); //push the last prevParsedEvent to queue
		isParsingDone = true;
		LOGGER.info(EVENT_PARSER.getMarker(), "Recovered {} event from stream file",
				() -> eventsCounter);
	}

	/**
	 * Parsing event stream files from a specific folder with a search timestamp
	 *
	 * @param fileDir
	 * 		directory where event files are stored
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 */
	private void parseEventFolder(String fileDir,
			EventConsumer eventHandler) {
		if (fileDir != null) {
			LOGGER.info(EVENT_PARSER.getMarker(), "Loading event file from path {} ",
					() -> fileDir);

			// Only get .evts files from the directory
			File folder = new File(fileDir);
			File[] files = folder.listFiles((dir, name) -> EVENT.isStreamFile(name));
			LOGGER.info(EVENT_PARSER.getMarker(), "Files before sorting {}",
					() -> Arrays.toString(files));
			//sort file by its name and timestamp order
			Arrays.sort(files);

			for (int i = 0; i < files.length; i++) {
				String fullPathName = files[i].getAbsolutePath();
				Instant currTimestamp = getTimeStampFromFileName(files[i].getName());

				if (currTimestamp.compareTo(endTimestamp) > 0) {
					LOGGER.info(EVENT_PARSER.getMarker(),
							"Search event file ended because file timestamp {} is after endTimestamp {}",
							() -> currTimestamp, () -> endTimestamp);
					return;
				}

				if (!processEventFile(files, i, eventHandler)) {
					LOGGER.error(EXCEPTION.getMarker(),
							() -> new StreamParseErrorPayload(
									"Experienced error during parsing file " + fullPathName));
					return;
				}
			}
		}
	}

	/**
	 * Processes a file in the file array:
	 * for a file which is not the last file, we check whether we should skip it or not, and parse the file when needed;
	 * for the last file, we always parse it.
	 *
	 * @param files
	 * 		a file array
	 * @param index
	 * 		index of the file to be parsed
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 * @return whether there is error when parsing the file
	 */
	private boolean processEventFile(final File[] files, final int index,
			final EventConsumer eventHandler) {
		boolean result = true;
		if (index < files.length - 1) {
			//if this is not the last file, we can compare timestamp from the next file with startTimestamp
			Instant nextTimestamp = getTimeStampFromFileName(files[index + 1].getName());

			// if  startTimestamp < nextTimestamp, we should parse this file
			if (startTimestamp.compareTo(nextTimestamp) < 0) {
				result = parseEventFile(files[index], eventHandler);
			} else {
				// else we can skip this file
				LOGGER.info(EVENT_PARSER.getMarker(), " Skip file {}: startTimestamp {} nextTimestamp {}",
						files[index]::getName,
						() -> startTimestamp,
						() -> nextTimestamp);
			}
		} else {
			// last file will always be opened and parsed since we could not know
			// what is the timestamp of the last event within the file
			result = parseEventFile(files[index], eventHandler);
		}
		return result;
	}

	/**
	 * Parse event stream file (.evts) version 5
	 * and put parsed event objects into eventHandler
	 *
	 * If startTimestamp is null then return all parsed events
	 *
	 * @param file
	 * 		event stream file
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 * @return return false if experienced any error otherwise return true
	 */
	private static boolean parseEventFile(final File file, EventConsumer eventHandler) {
		if (!file.exists()) {
			LOGGER.error(EXCEPTION.getMarker(), "File {} does not exist: ", file::getName);
			return false;
		}
		LOGGER.info(EVENT_PARSER.getMarker(), "Processing file {}", file::getName);

		if (!EVENT.isStreamFile(file)) {
			LOGGER.error(EXCEPTION.getMarker(), "parseEventFile fails :: {} is not an event stream file",
					file::getName);
			return false;
		}

		return parseEventStreamFile(file, eventHandler, false);
	}

	/**
	 * Parse event stream file (.evts) version 5
	 * and put parsed event objects into eventHandler
	 *
	 * @param file
	 * 		event stream file
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 * @param populateSettingsCommon
	 * 		should be true when this method is called from a utility program which may not read the settings.txt file
	 * 		and
	 * 		follow the normal initialization routines in the Browser class
	 * @return return false if experienced any error otherwise return true
	 */
	public static boolean parseEventStreamFile(final File file, final EventConsumer eventHandler,
			final boolean populateSettingsCommon) {
		if (populateSettingsCommon) {
			// Populate the SettingsCommon object with the defaults or configured values from the Settings class.
			// This is necessary because this method may be called from a utility program which may or may not
			// read the settings.txt file and follow the normal initialization routines in the Browser class.
			Browser.populateSettingsCommon();
		}
		try {
			final int fileVersion = readFirstIntFromFile(file);
			if (fileVersion == EVENT_STREAM_FILE_VERSION) {
				//should return false if any parsing error happened
				//so the whole parsing process can stop
				return parseEventStreamV5(file, eventHandler);
			} else {
				LOGGER.info(EVENT_PARSER.getMarker(), "failed to parse file {} whose version is {}",
						file::getName,
						() -> fileVersion);

				return false;
			}
		} catch (IOException e) {
			LOGGER.info(EXCEPTION.getMarker(), "Unexpected", e);
			return false;
		}
	}

	/**
	 * Parse event stream file (.evts) version 5
	 * and put parsed event objects into eventHandler
	 *
	 * @param file
	 * 		event stream file
	 * @param eventHandler
	 * 		call back function for handling parsed event object
	 */
	private static boolean parseEventStreamV5(final File file, final EventConsumer eventHandler) {
		Iterator<SelfSerializable> iterator = parseStreamFile(file, EVENT);
		boolean isStartRunningHash = true;
		while (iterator.hasNext()) {
			SelfSerializable object = iterator.next();
			if (object == null) { // iterator.next() returns null if any error occurred
				return false;
			}
			if (isStartRunningHash) {
				LOGGER.info(EVENT_PARSER.getMarker(), "From file {} read startRunningHash = {}",
						file::getName,
						() -> object);
				isStartRunningHash = false;
			} else if (object instanceof Hash) {
				LOGGER.info(EVENT_PARSER.getMarker(), "From file {} read endRunningHash = {}",
						file::getName,
						() -> object);
			} else {
				EventImpl event = new EventImpl((ConsensusEvent) object);
				// set event's baseHash
				CryptoFactory.getInstance().digestSync(event.getBaseEventHashedData());
				eventHandler.consume(event);
			}
		}
		return true;
	}

	private void addToQueue(EventImpl event) {
		if (event != null) {
			events.offer(event);
			eventsCounter++;
		}
	}

	/**
	 * @param event
	 * 		Event to be handled
	 * @return indicate whether should continue parse event from input stream
	 */
	private boolean handleEvent(EventImpl event) {
		if (event == null) {
			LOGGER.info(EVENT_PARSER.getMarker(), "Finished parsing events");
			if (prevParsedEvent != null) {
				LOGGER.info(EVENT_PARSER.getMarker(), "Last recovered event consensus timestamp {}, round {}",
						prevParsedEvent.getConsensusTimestamp(), prevParsedEvent.getRoundReceived());
				addToQueue(prevParsedEvent);
			}
			return false;
		}
		// events saved in stream file are consensus events
		// we need to setConsensus to be true, otherwise we will got `ConsensusEventHandler queue has non consensus
		// event` error in ConsensusEventHandler.applyConsensusEventToState() when handling this event during state
		// recovery
		event.setConsensus(true);

		Instant consensusTimestamp = event.getConsensusTimestamp();

		boolean shouldContinue;
		// Search criteria :
		// 		startTimestamp < consensusTimestamp <= endTimestamp
		//
		// startTimestamp < consensusTimestamp ->  startTimestamp isBefore consensusTimestamp
		// consensusTimestamp <= endTimestamp ->   consensusTimestamp is NOT after endTimestamp
		//
		// for event whose consensusTimestamp is before or equal to startTimestamp, we ignore such event,
		// because this event should not be played back in swirdsState
		// we cannot write such events to event stream files, because we only have eventsRunningHash loaded from signed
		// state. we must start to update eventsRunningHash for events whose consensus timestamp is after the
		// loaded signed state, and then start to write event stream file at the first complete window
		if (startTimestamp.isBefore(consensusTimestamp) && !consensusTimestamp.isAfter(endTimestamp)) {
			if (prevParsedEvent != null) {
				//this is not the first parsed event, push prevParsedEvent to queue
				addToQueue(prevParsedEvent);
			}
			prevParsedEvent = event;
			shouldContinue = true;
		} else if (consensusTimestamp.isAfter(endTimestamp)) {
			LOGGER.info(EVENT_PARSER.getMarker(),
					"Search finished due to consensusTimestamp is after endTimestamp");
			shouldContinue = false;

		} else {
			shouldContinue = true;
		}
		return shouldContinue;
	}

	@Override
	public void run() {
		eventPlayback();
	}
}
