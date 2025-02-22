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

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.OperatingSystemMXBean;
import com.swirlds.common.NodeId;
import com.swirlds.common.PlatformStatNames;
import com.swirlds.common.SwirldState;
import com.swirlds.common.ThresholdLimitingHandler;
import com.swirlds.common.Transaction;
import com.swirlds.common.Units;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.common.statistics.StatsSpeedometer;
import com.swirlds.common.statistics.internal.AbstractStatistics;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.SwirldStateSingleTransactionPool;
import com.swirlds.platform.network.NetworkStats;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.ConsensusHandlingStats;
import com.swirlds.platform.stats.ConsensusStats;
import com.swirlds.platform.stats.CycleTimingStat;
import com.swirlds.platform.stats.HashgraphStats;
import com.swirlds.platform.stats.IssStats;
import com.swirlds.platform.stats.MaxStat;
import com.swirlds.platform.stats.PlatformStatistics;
import com.swirlds.platform.stats.SignedStateStats;
import com.swirlds.platform.stats.SwirldStateStats;
import com.swirlds.platform.stats.SyncStats;
import com.swirlds.platform.stats.TimeStat;
import com.swirlds.platform.stats.TransactionStatistics;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import com.swirlds.platform.sync.SyncTiming;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.swirlds.common.PlatformStatNames.SIGNED_STATE_HASHING_TIME;
import static com.swirlds.common.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This class collects and reports various statistics about network operation. A statistic such as
 * Transactions Per Second can be retrieved by using its name "trans/sec". The current list of statistic
 * names can be found by calling {@link #getAvailableStats}, and includes the following:
 *
 * <ul>
 * <li><b>badEv/sec</b> - number of corrupted events received per second *
 * <li><b>bytes/sec_sync</b> - average number of bytes per second transferred during a sync *
 * <li><b>bytes/sec_trans</b> - number of bytes in the transactions received per second (from unique events created
 * by self and others) *
 * <li><b>bytes/trans</b> - number of bytes in each transactions *
 * <li><b>cEvents/sec</b> - number of events per second created by this node *
 * <li><b>conns</b> - number of times a TLS connections was created *
 * <li><b>cpuLoadSys</b> - the CPU load of the whole system *
 * <li><b>directMemInMB</b> - total megabytes of off-heap (direct) memory in the JVM *
 * <li><b>directMemPercent</b> - off-heap (direct) memory used as a percentage of -XX:MaxDirectMemorySize param *
 * <li><b>dupEv%</b> - percentage of events received that are already known *
 * <li><b>ev/syncS</b> - number of events sent per successful sync *
 * <li><b>ev/syncR</b> - number of events received per successful sync *
 * <li><b>events/sec</b> - number of unique events received per second (created by self and others) *
 * <li><b>eventStreamQueueSize</b> - size of the queue from which we take events and write to EventStream file *
 * <li><b>icSync/sec</b> - (interrupted call syncs) syncs interrupted per second initiated by this member *
 * <li><b>irSync/sec</b> - (interrupted receive syncs) syncs interrupted per second initiated by other
 * member *
 * <li><b>lastSeq</b> - last event number generated by me *
 * <li><b>local</b> - number of members running on this local machine *
 * <li><b>memberID</b> - ID number of this member *
 * <li><b>members</b> - total number of members participating *
 * <li><b>memFree</b> - bytes of free memory (which can increase after a garbage collection) *
 * <li><b>memMax</b> - maximum bytes that the JVM might use *
 * <li><b>memTot</b> - total bytes in the Java Virtual Machine *
 * <li><b>name</b> - name of this member *
 * <li><b>ping</b> - average time for a round trip message between 2 computers (in milliseconds) *
 * <li><b>proc</b> - number of processors (cores) available to the JVM *
 * <li><b>roundSup</b> - latest round with state signed by a supermajority *
 * <li><b>rounds/sec</b> - average number of rounds per second *
 * <li><b>sec/sync</b> - duration of average successful sync (in seconds) *
 * <li><b>secC2C</b> - time from creating an event to knowing its consensus (in seconds) *
 * <li><b>SecC2H</b> - time from knowing consensus for a transaction to handling it (in seconds) *
 * <li><b>secC2R</b> - time from another member creating an event to receiving it and veryfing the signature
 * (in seconds) *
 * <li><b>secC2RC</b> - time from another member creating an event to it being received and and knowing
 * consensus for it (in seconds) *
 * <li><b>secR2C</b> - time from receiving an event to knowing its consensus (in seconds) *
 * <li><b>secR2F</b> - time from a round's first received event to all the famous witnesses being known (in
 * seconds) *
 * <li><b>secR2nR</b> - time from fist event received in one round, to first event received in the next
 * round (in seconds) *
 * <li><b>simListenSyncs</b> - avg number of simultaneous listening syncs happening at any given time *
 * <li><b>simSyncs</b> - avg number of simultaneous syncs happening at any given time *
 * <li><b>sync/secC</b> - (call syncs) syncs completed per second initiated by this member *
 * <li><b>sync/secR</b> - (receive syncs) syncs completed per second initiated by other member *
 * <li><b>threads</b> - the current number of live threads *
 * <li><b>time</b> - the current time *
 * <li><b>TLS</b> - 1 if using TLS, 0 if not *
 * <li><b>transCons</b> - transCons queue size *
 * <li><b>transEvent</b> - transEvent queue size *
 * <li><b>trans/event</b> - number of app transactions in each event *
 * <li><b>trans/sec</b> - number of app transactions received per second (from unique events created by self
 * and others) *
 * <li><b>write</b> - the app claimed to log statistics every this many milliseconds *
 * </ul>
 */
public class Statistics extends AbstractStatistics implements
		ConsensusStats,
		EventAddedObserver,
		HashgraphStats,
		IssStats,
		PlatformStatistics,
		SignedStateStats,
		SyncStats,
		SwirldStateStats,
		ConsensusHandlingStats,
		TransactionStatistics,
		NetworkStats {

	private static final Logger LOG = LogManager.getLogger(Statistics.class);

	/** The maximum number of times an exception should be logged before being suppressed. */
	private static final long EXCEPTION_RATE_THRESHOLD = 10;

	/** The number of time intervals measured on the thread-cons cycle */
	private static final int CONS_ROUND_INTERVALS = 5;

	/** The number of time intervals measured on the new signed state creation cycle */
	private static final int NEW_SIGNED_STATE_INTERVALS = 5;

	/** which Platform to watch */
	protected AbstractPlatform platform;
	/** an object used to get OS stats */
	private final OperatingSystemMXBean osBean;

	private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter = new ThresholdLimitingHandler<>(
			EXCEPTION_RATE_THRESHOLD);

	private final SavedFileStatistics savedFileStatistics;

	private final ReconnectStatistics reconnectStatistics;


	/** an object to get thread stats */
	ThreadMXBean thbean;

	/** number of app transactions (from self and others) per second */
	StatsSpeedometer transactionsPerSecond;
	/** number of events (from self and others) per second */
	StatsSpeedometer eventsPerSecond;
	/** number of rounds reaching consensus per second */
	StatsSpeedometer roundsPerSecond;
	/** number of events discarded because already known */
	StatsSpeedometer duplicateEventsPerSecond;
	/** number of events discarded for bad sequence number / signature */
	StatsSpeedometer badEventsPerSecond;
	/** number of syncs per second that complete, where self called someone else */
	StatsSpeedometer callSyncsPerSecond;
	/** number of syncs per second that complete, where someone else called self */
	StatsSpeedometer recSyncsPerSecond;
	/** number of syncs initiated by member interrupted in the middle, per second */
	StatsSpeedometer interruptedCallSyncsPerSecond;
	/** number of syncs initiated by others interrupted in the middle, per second */
	StatsSpeedometer interruptedRecSyncsPerSecond;
	/** number of events created by this node per second */
	StatsSpeedometer eventsCreatedPerSecond;
	/** all connections of this platform */
	final Queue<SyncConnection> connections = new ConcurrentLinkedQueue<>();
	/** total number of connections created so far (both caller and listener) */
	final AtomicInteger connsCreated = new AtomicInteger(0);
	/**
	 * number of bytes in the transactions received per second (from unique events created by self and
	 * others)
	 */
	StatsSpeedometer bytesPerSecondTrans;
	/** number of bytes sent per second over the network (total for this member) */
	StatsSpeedometer bytesPerSecondSent;
	/** number of extra bytes sent per second to help other members who fall behind to catch up */
	StatsSpeedometer bytesPerSecondCatchupSent;
	/** time for event, from when the event is received, to when all the famous witnesses are known */
	StatsRunningAverage avgReceivedFamousTime;
	/** time for member, from creating to knowing consensus */
	StatsRunningAverage avgCreatedConsensusTime;
	/** time for a member, from receiving to knowing consensus */
	StatsRunningAverage avgReceivedConsensusTime;
	/** time for a member, from knowing consensus to handling that consensus transaction */
	private StatsRunningAverage avgConsHandleTime;

	private final AverageStat knownSetSize = new AverageStat(
			CATEGORY,
			"knownSetSize",
			"the average size of the known set during a sync",
			FLOAT_FORMAT_10_3,
			AverageStat.WEIGHT_VOLATILE
	);
	/** average wall clock time from start of a successful sync until it's done */
	private final TimeStat avgSyncDuration = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync",
			"duration of average successful sync (in seconds)"
	);

	/** average wall clock time for step 1 of a successful sync */
	private final TimeStat avgSyncDuration1 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync1",
			"duration of step 1 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 2 of a successful sync */
	private final TimeStat avgSyncDuration2 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync2",
			"duration of step 2 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 3 of a successful sync */
	private final TimeStat avgSyncDuration3 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync3",
			"duration of step 3 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 4 of a successful sync */
	private final TimeStat avgSyncDuration4 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync4",
			"duration of step 4 of average successful sync (in seconds)"
	);
	/** average wall clock time for step 5 of a successful sync */
	private final TimeStat avgSyncDuration5 = new TimeStat(
			ChronoUnit.SECONDS,
			INTERNAL_CATEGORY,
			"sec/sync5",
			"duration of step 5 of average successful sync (in seconds)"
	);
	private final AverageStat syncGenerationDiff = new AverageStat(
			INTERNAL_CATEGORY,
			"syncGenDiff",
			"number of generation ahead (positive) or behind (negative) when syncing",
			FLOAT_FORMAT_8_1,
			AverageStat.WEIGHT_VOLATILE
	);
	private final AverageStat eventRecRate = new AverageStat(
			INTERNAL_CATEGORY,
			"eventRecRate",
			"the rate at which we receive and enqueue events in ev/sec",
			FLOAT_FORMAT_8_1,
			AverageStat.WEIGHT_VOLATILE
	);

	private final CycleTimingStat consensusCycleTiming = new CycleTimingStat(
			ChronoUnit.MILLIS,
			INTERNAL_CATEGORY,
			"consRound",
			CONS_ROUND_INTERVALS,
			List.of("handleMillis/round",
					"storeMillis/round",
					"hashMillis/round",
					"buildStateMillis",
					"forSigCleanMillis"
			),
			List.of("average time to handle a consensus round",
					"average time to add consensus round events to signed state storage",
					"average time spent hashing the consensus round events",
					"average time spent building a signed state",
					"average time spent expiring signed state storage events"
			)
	);

	private final CycleTimingStat newSignedStateCycleTiming = new CycleTimingStat(
			ChronoUnit.MICROS,
			INTERNAL_CATEGORY,
			"newSS",
			NEW_SIGNED_STATE_INTERVALS,
			List.of(
					"getStateMicros",
					"getStateDataMicros",
					"runningHashMicros",
					"newSSInstanceMicros",
					"queueAdmitMicros"
			),
			List.of("average time to get the state to sign",
					"average time to get events and min gen info",
					"average time spent waiting on the running hash future",
					"average time spent creating the new signed state instance",
					"average time spent admitting the signed state to the signing queue"
			)
	);

	private final TimeStat noMoreTransDuration = new TimeStat(
			ChronoUnit.MICROS,
			INTERNAL_CATEGORY,
			"noMoreTransMicros",
			"the average duration of noMoreTransactions in microseconds"
	);

	/** average time (in seconds) to send a byte and get a reply, for each member (holds 0 for self) */
	public StatsRunningAverage[] avgPingMilliseconds;
	/** average bytes per second received during a sync with each member (holds 0 for self) */
	public StatsSpeedometer[] avgBytePerSecSent;
	/** time for event, from being created to being received by another member */
	StatsRunningAverage avgCreatedReceivedTime;
	/** time for event, from being created by one, to knowing consensus by another */
	StatsRunningAverage avgCreatedReceivedConsensusTime;
	/** time for event, from receiving the first event in a round to the first event in the next round */
	StatsRunningAverage avgFirstEventInRoundReceivedTime;
	/** average number of app transactions per event, counting both system and non-system transactions */
	StatsRunningAverage avgTransactionsPerEvent;
	/** average number of bytes per app transaction, counting both system and non-system transactions */
	StatsRunningAverage avgBytesPerTransaction;
	/** average percentage of received events that are already known */
	StatsRunningAverage avgDuplicatePercent;
	/** self event consensus timestamp minus time created */
	private StatsRunningAverage avgSelfCreatedTimestamp;
	/** other event consensus timestamp minus time received */
	private StatsRunningAverage avgOtherReceivedTimestamp;
	/** INTERNAL: number of app transactions (from self and others) per second */
	StatsSpeedometer transactionsPerSecondSys;
	/**
	 * INTERNAL: number of bytes in system transactions received per second, both created by self and
	 * others, only counting unique events
	 */
	StatsSpeedometer bytesPerSecondSys;
	/**
	 * INTERNAL: average number of system transactions per event, counting both system and non-system
	 * transactions
	 */
	StatsRunningAverage avgTransactionsPerEventSys;
	/**
	 * INTERNAL: average number of bytes per system transaction, counting both system and non-system
	 * transactions
	 */
	StatsRunningAverage avgBytesPerTransactionSys;
	/** sleeps per second because caller thread had too many failed connects */
	StatsSpeedometer sleep1perSecond;
	/** fraction of syncs that are slowed to let others catch up */
	StatsRunningAverage fracSyncSlowed;
	/** fraction of each second spent in dot products */
	StatsSpeedometer timeFracDot;
	/** fraction of each second spent adding an event to the hashgraph, and calculating consensus */
	StatsSpeedometer timeFracAdd;
	/** average number of bytes per second transfered during a sync */
	StatsRunningAverage avgBytesPerSecSync;

	/** number of consensus transactions per second handled by SwirldState.handleTransaction() */
	private StatsSpeedometer transHandledPerSecond;
	/** avg time to handle a consensus transaction in SwirldState.handleTransaction (in seconds) */
	private StatsRunningAverage avgSecTransHandled;
	/** average time it takes the copy() method in SwirldState to finish (in microseconds) */
	private StatsRunningAverage avgStateCopyMicros;

	/** average time it takes to acquire the lock for a state fast copy */
	private final TimeStat avgStateCopyAdmit = new TimeStat(
			ChronoUnit.MICROS,
			INTERNAL_CATEGORY,
			"avgStateCopyAdmit",
			"average time it takes to acquire the lock for fast copying the state (in microseconds)"
	);

	/** average time it takes to create a new SignedState (in seconds) */
	private StatsRunningAverage avgSecNewSignedState;

	/** boolean result of function {@link SyncManager#shouldCreateEvent(SyncResult)} */
	StatsRunningAverage shouldCreateEvent;

	private final AtomicInteger issCount = new AtomicInteger();

	/** number of coin rounds that have occurred so far */
	StatsRunningAverage numCoinRounds;


	//////////////////// these are updated in Statistics.updateOthers() /////////////

	/** number of app transactions received so far */
	AtomicLong numTrans = new AtomicLong(0);
	/** average time for a round trip message between 2 computers (in milliseconds) */
	StatsRunningAverage avgPing;
	/** number of connections created (both calling and listening) */
	StatsRunningAverage avgConnsCreated;
	/** bytes of free memory (which can increase after a garbage collection) */
	StatsRunningAverage memFree;
	/** total bytes in the Java Virtual Machine */
	StatsRunningAverage memTot;
	/** maximum bytes that the JVM might use */
	StatsRunningAverage memMax;
	/** maximum amount of off-heap (direct) memory being used by the JVM, in megabytes */
	private StatsRunningAverage directMemInMB;
	/** off-heap (direct) memory being used by the JVM, as a percentage of -XX:MaxDirectMemorySize param */
	private StatsRunningAverage directMemPercent;
	/**
	 * helper variables (set once, when the directMemory StatsRunningAverage variables are created) used to
	 * refresh those values whenever the stats are created
	 */
	private BufferPoolMXBean directMemMXBean;
	private double maximumDirectMemSizeInMB = -1;
	/** number of processors (cores) available to the JVM */
	StatsRunningAverage avgNumProc;
	/** the CPU load of the whole system */
	StatsRunningAverage cpuLoadSys;
	/** Total number of thread running */
	StatsRunningAverage threads;
	/** ID number of this member */
	StatsRunningAverage avgSelfId;
	/** total number of members participating */
	StatsRunningAverage avgNumMembers;
	/** number of members running on this local machine */
	StatsRunningAverage avgNumLocal;
	/** statistics are logged every this many milliseconds */
	StatsRunningAverage avgWrite;
	/** max number of syncs this can initiate simultaneously */
	StatsRunningAverage avgSimCallSyncsMax;
	/** avg number of syncs this member is doing simultaneously */
	StatsRunningAverage avgSimSyncs;
	/** avg number of listening syncs this member is doing simultaneously */
	StatsRunningAverage avgSimListenSyncs;
	/** number of non-consensus events waiting to be handled [forCurr.size()] */
	StatsRunningAverage avgQ1forCurr;

	/**
	 * The number events in the consensus rounds waiting to be handled by
	 * {@link ConsensusRoundHandler}
	 */
	private final AverageAndMax avgQ2ConsEvents = new AverageAndMax(
			INTERNAL_CATEGORY,
			PlatformStatNames.CONSENSUS_QUEUE_SIZE,
			"average number of events in the consensus queue (q2) waiting to be handled",
			FLOAT_FORMAT_10_3,
			AverageStat.WEIGHT_VOLATILE
	);

	/** The average number of events per round. */
	private final AverageAndMax avgEventsPerRound = new AverageAndMax(
			INTERNAL_CATEGORY,
			"events/round",
			"average number of events in a consensus round",
			FLOAT_FORMAT_8_1,
			AverageStat.WEIGHT_VOLATILE
	);

	/** number of handled consensus events that will be part of the next signed state */
	StatsRunningAverage avgQSignedStateEvents;
	/** number of events received waiting to be processed, or events waiting to be created [forSigs.size()] */
	StatsRunningAverage avgQ4forHash;
	/** number of SignedStates waiting to be hashed and signed in the queue [stateToHashSign.size()] */
	StatsRunningAverage avgStateToHashSignDepth;
	/** size of the queue from which we take events and write to EventStream file */
	private StatsRunningAverage eventStreamQueueSize;
	/** size of the queue from which we take events, calculate Hash and RunningHash */
	private StatsRunningAverage hashQueueSize;
	/** latest round with signed state by a supermajority */
	StatsRunningAverage avgRoundSupermajority;
	/** number of events sent per successful sync */
	private final AverageAndMax avgEventsPerSyncSent = new AverageAndMax(
			CATEGORY,
			"ev/syncS",
			"number of events sent per successful sync",
			FLOAT_FORMAT_8_1
	);
	/** number of events received per successful sync */
	private final AverageAndMax avgEventsPerSyncRec = new AverageAndMax(
			CATEGORY,
			"ev/syncR",
			"number of events received per successful sync",
			FLOAT_FORMAT_8_1
	);
	private final AverageStat averageOtherParentAgeDiff = new AverageStat(
			CATEGORY,
			"opAgeDiff",
			"average age difference (in generations) between an event created by this node and its other parent",
			FLOAT_FORMAT_5_3,
			AverageStat.WEIGHT_VOLATILE
	);
	/** INTERNAL: total number of events in memory, for all members on the local machine together */
	StatsRunningAverage avgEventsInMem;
	/** running average of the number of signatures collected on each signed state */
	StatsRunningAverage avgStateSigs;

	/** number of stale events per second */
	StatsSpeedometer staleEventsPerSecond;
	/** number of stale events ever */
	AtomicLong staleEventsTotal = new AtomicLong(0);
	/** number of events generated per second to rescue childless events so they don't become stale */
	StatsSpeedometer rescuedEventsPerSecond;

	/** The number of creators that have more than one tip at the start of each sync. */
	private final MaxStat multiTipsPerSync = new MaxStat(
			CATEGORY,
			PlatformStatNames.MULTI_TIPS_PER_SYNC,
			"the number of creators that have more than one tip at the start of each sync",
			INTEGER_FORMAT_5
	);
	/** The average number of tips per sync at the start of each sync. */
	private StatsRunningAverage tipsPerSync;

	/** The average number of generations that should be expired but cannot because of reservations. */
	private final AverageStat gensWaitingForExpiry = new AverageStat(
			CATEGORY,
			PlatformStatNames.GENS_WAITING_FOR_EXPIRY,
			"the average number of generations waiting to be expired",
			FLOAT_FORMAT_5_3,
			AverageStat.WEIGHT_VOLATILE
	);

	/**
	 * The ratio of rejected syncs to accepted syncs over time.
	 */
	private final AverageStat rejectedSyncRatio = new AverageStat(
			INTERNAL_CATEGORY,
			PlatformStatNames.REJECTED_SYNC_RATIO,
			"the averaged ratio of rejected syncs to accepted syncs over time",
			FLOAT_FORMAT_1_3,
			AverageStat.WEIGHT_VOLATILE
	);
	/**
	 * The ratio of rejected syncs to accepted syncs over time.
	 */
	private final AverageStat avgTransSubmitMicros = new AverageStat(
			INTERNAL_CATEGORY,
			PlatformStatNames.TRANS_SUBMIT_MICROS,
			"average time spent submitting a user transaction (in microseconds)",
			FLOAT_FORMAT_1_3,
			AverageStat.WEIGHT_VOLATILE
	);
	/**
	 * average time spent in
	 * {@code SwirldStateManager#preConsensusEvent} by the {@code thread-curr} thread (in microseconds)
	 */
	private final TimeStat preConsHandleTime = new TimeStat(
			ChronoUnit.MICROS,
			INTERNAL_CATEGORY,
			"preConsHandleMicros",
			"average time it takes to handle a pre-consensus event from q4 (in microseconds)"
	);

	/**
	 * average time spent in {@link SwirldStateManagerSingle} when performing a shuffle (in microseconds)
	 */
	private StatsRunningAverage avgShuffleMicros;

	/**
	 * avg length of the state archival queue
	 */
	StatsRunningAverage stateArchivalQueueAvg;

	/**
	 * avg time taken to archive a signed state (in microseconds)
	 */
	StatsRunningAverage stateArchivalTimeAvg;

	/**
	 * avg length of the state deletion queue
	 */
	StatsRunningAverage stateDeletionQueueAvg;

	/**
	 * avg time taken to delete a signed state (in microseconds)
	 */
	StatsRunningAverage stateDeletionTimeAvg;

	File rootDirectory = new File("/");
	long freeDiskspace = rootDirectory.getFreeSpace();
	long totalDiskspace = rootDirectory.getTotalSpace();

	private static final String INTEGER_FORMAT_5 = "%5d";
	private static final String FLOAT_FORMAT_10_3 = "%,10.3f";
	private static final String FLOAT_FORMAT_13_0 = "%,13.0f";
	private static final String FLOAT_FORMAT_15_3 = "%,15.3f";
	private static final String FLOAT_FORMAT_16_0 = "%,16.0f";
	private static final String FLOAT_FORMAT_16_2 = "%,16.2f";
	private static final String FLOAT_FORMAT_8_1 = "%,8.1f";
	private static final String FLOAT_FORMAT_1_3 = "%,1.3f";
	private static final String FLOAT_FORMAT_5_3 = "%,5.3f";
	private static final double WHOLE_PERCENT = 100.0;    // all of something is to be reported as 100.0%

	/** once a second, update all the statistics that aren't updated by any other class */
	@Override
	public void updateOthers() {
		try {
			// don't update anything until the platform creates the hashgraph
			if (platform.getEventTaskCreator() != null) {

				// calculate the value for otherStatPing (the average of all, not including self)
				double sum = 0;
				final double[] times = getPingMilliseconds(); // times are in seconds
				for (final double time : times) {
					sum += time;
				}
				// don't average in the times[selfId]==0, so subtract 1 from the length
				final double pingValue = sum / (times.length - 1);  // pingValue is in milliseconds

				avgPing.recordValue(pingValue);
				interruptedCallSyncsPerSecond.update(0);
				interruptedRecSyncsPerSecond.update(0);
				badEventsPerSecond.update(0);
				platform.getStats().sleep1perSecond.update(0);
				memFree.recordValue(Runtime.getRuntime().freeMemory());
				memTot.recordValue(Runtime.getRuntime().totalMemory());
				memMax.recordValue(Runtime.getRuntime().maxMemory());
				updateDirectMemoryStatistics();
				avgNumProc.recordValue(
						Runtime.getRuntime().availableProcessors());
				cpuLoadSys.recordValue(osBean.getSystemCpuLoad());
				threads.recordValue(thbean.getThreadCount());
				avgSelfId.recordValue(platform.getSelfId().getId());
				avgNumMembers.recordValue(
						platform.getAddressBook().getSize());
				avgNumLocal.recordValue(
						platform.getAddressBook().getOwnHostCount());
				avgWrite.recordValue(statsWritePeriod);
				avgSimCallSyncsMax.recordValue(Settings.maxOutgoingSyncs);
				avgQ1forCurr.recordValue(platform.getPreConsensusHandler().getQueueSize());
				avgQ2ConsEvents.update(platform.getConsensusHandler().getNumEventsInQueue());
				avgQSignedStateEvents.recordValue(platform.getConsensusHandler().getSignedStateEventsSize());
				avgQ4forHash.recordValue(platform.getIntakeQueue().size());
				avgSimSyncs.recordValue(platform.getSimultaneousSyncThrottle().getNumSyncs());
				avgSimListenSyncs.recordValue(platform.getSimultaneousSyncThrottle().getNumListenerSyncs());
				eventStreamQueueSize.recordValue(platform.getEventStreamManager() != null ?
						platform.getEventStreamManager().getEventStreamingQueueSize() : 0);
				hashQueueSize.recordValue(platform.getEventStreamManager() != null ?
						platform.getEventStreamManager().getHashQueueSize() : 0);
				avgStateToHashSignDepth.recordValue(platform.getConsensusHandler().getStateToHashSignSize());
				avgRoundSupermajority.recordValue(
						platform.getSignedStateManager().getLastCompleteRound());
				avgEventsInMem.recordValue(EventCounter.getNumEventsInMemory());
				updateConnectionStats();
				freeDiskspace = rootDirectory.getFreeSpace();
			}
		} catch (final Exception e) {
			exceptionRateLimiter.handle(e,
					(error) -> LOG.error(EXCEPTION.getMarker(), "Exception while updating statistics.", error));
		}
	}

	private void updateConnectionStats() {
		long totalBytesSent = 0;
		for (final Iterator<SyncConnection> iterator = connections.iterator(); iterator.hasNext(); ) {
			final SyncConnection conn = iterator.next();
			if (conn != null) {
				final long bytesSent = conn.getDos().getConnectionByteCounter().getAndResetCount();
				totalBytesSent += bytesSent;
				final int otherId = conn.getOtherId().getIdAsInt();
				if (otherId < avgBytePerSecSent.length && avgBytePerSecSent[otherId] != null) {
					avgBytePerSecSent[otherId].update(bytesSent);
				}
				if (!conn.connected()) {
					iterator.remove();
				}
			}
		}
		bytesPerSecondSent.update(totalBytesSent);
		avgConnsCreated.recordValue(connsCreated.get());
	}

	/**
	 * Update additional statistics that aren't updated by any other class. Split out of {link #updateOthers()}
	 * to stop SonarCloud from complaining that it's Cognitive Complexity was 1 too high.
	 */
	private void updateDirectMemoryStatistics() {
		if (directMemMXBean == null) {
			return;
		}

		final long bytesUsed = directMemMXBean.getMemoryUsed();
		// recording the value of -1 as (-1) / (1024 * 1024) makes it too close to 0; treat it as -1 megabytes
		// for visibility
		if (bytesUsed == -1) {
			directMemInMB.recordValue(bytesUsed);
			if (maximumDirectMemSizeInMB > 0) {
				directMemPercent.recordValue(bytesUsed);
			}
			return;
		}
		final double megabytesUsed = bytesUsed * Units.BYTES_TO_MEBIBYTES;
		directMemInMB.recordValue(megabytesUsed);
		if (maximumDirectMemSizeInMB > 0) {
			directMemPercent.recordValue(megabytesUsed * WHOLE_PERCENT / maximumDirectMemSizeInMB);
		}
	}

	/**
	 * Do not allow instantiation without knowing which Platform to monitor. Do not call this.
	 */
	@SuppressWarnings("unused")
	Statistics() {
		super();
		throw new UnsupportedOperationException(
				"called constructor new Statistics() instead of new Statistics(platform)");
	}

	/**
	 * Same as {@link #Statistics(AbstractPlatform, List)} with additionalEntries set to be empty
	 */
	public Statistics(final AbstractPlatform platform) {
		this(platform, Collections.emptyList());
	}

	/**
	 * Constructor for a Statistics object that will monitor the statistics for the network for the given
	 * Platform.
	 *
	 * @param platform
	 * 		the Platform whose statistics should be monitored
	 * @param additionalEntries
	 * 		additional stat entries to be added
	 */
	public Statistics(final AbstractPlatform platform, final List<StatEntry> additionalEntries) {
		super();
		this.platform = platform;
		this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		this.thbean = ManagementFactory.getThreadMXBean();
		this.savedFileStatistics = new SavedFileStatistics();

		this.reconnectStatistics = new ReconnectStatistics();
		final int abSize = platform.getAddressBook() == null ? 0 : platform.getAddressBook().getSize(); //0 during unit
		// tests

		avgPingMilliseconds = new StatsRunningAverage[abSize];
		avgBytePerSecSent = new StatsSpeedometer[abSize];
		for (int i = 0; i < avgPingMilliseconds.length; i++) {
			avgPingMilliseconds[i] = new StatsRunningAverage(Settings.halfLife);
		}
		for (int i = 0; i < avgBytePerSecSent.length; i++) {
			avgBytePerSecSent[i] = new StatsSpeedometer(Settings.halfLife);
		}
		createStatEntriesArray(platform, additionalEntries);
		setUpStatEntries();
	}

	/**
	 * reset all the Speedometer and RunningAverage objects with a half life of Platform.halfLife
	 */
	@Override
	public void resetAllSpeedometers() {
		super.resetAllSpeedometers();

		for (final StatsRunningAverage avgPingSecond : avgPingMilliseconds) {
			avgPingSecond.reset(Settings.halfLife);
		}
		for (final StatsSpeedometer abpss : avgBytePerSecSent) {
			abpss.reset(Settings.halfLife);
		}
	}

	/**
	 * Returns the time for a round-trip message to each member (in milliseconds).
	 * <p>
	 * This is an exponentially-weighted average of recent ping times.
	 *
	 * @return the average times, for each member, in milliseconds
	 */
	public double[] getPingMilliseconds() {
		final double[] times = new double[avgPingMilliseconds.length];
		for (int i = 0; i < times.length; i++) {
			times[i] = avgPingMilliseconds[i].getWeightedMean();
		}
		times[platform.getSelfId().getIdAsInt()] = 0;
		return times;
	}

	/**
	 * return an array of info about all the statistics
	 *
	 * @return the array of StatEntry elements for every statistic managed by this class
	 */
	@Override
	public StatEntry[] getStatEntriesArray() {
		return statEntries;
	}

	/**
	 * Create all the data for the statEntry array. This must be called before getStatEntriesArray
	 *
	 * @param platform
	 * 		link to the platform instance
	 * @param additionalEntries
	 * 		additional stat entries to be added
	 */
	private void createStatEntriesArray(final AbstractPlatform platform, final List<StatEntry> additionalEntries) {
		statEntries = new StatEntry[] {
				new StatEntry(
						INFO_CATEGORY,
						"time",
						"the current time",
						"%25s",
						null,
						null,
						null,
						() -> DateTimeFormatter
								.ofPattern("yyyy-MM-dd HH:mm:ss z")
								.format(Instant.now()
										.atZone(ZoneId.of("UTC")))),
				new StatEntry(
						INTERNAL_CATEGORY,
						"trans",
						"number of transactions received so far",
						"%d",
						null,
						null,
						null,
						() -> numTrans.get()),
				new StatEntry(
						CATEGORY,
						"secR2C",
						"time from receiving an event to knowing its consensus (in seconds)",
						FLOAT_FORMAT_10_3,
						avgReceivedConsensusTime,
						h -> {
							avgReceivedConsensusTime = new StatsRunningAverage(h);
							return avgReceivedConsensusTime;
						},
						null,
						() -> avgReceivedConsensusTime.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"secC2C",
						"time from creating an event to knowing its consensus (in seconds)",
						FLOAT_FORMAT_10_3,
						avgCreatedConsensusTime,
						h -> {
							avgCreatedConsensusTime = new StatsRunningAverage(h);
							return avgCreatedConsensusTime;
						},
						null,
						() -> avgCreatedConsensusTime.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"SecC2H",
						"time from knowing consensus for a transaction to handling it (in seconds)",
						FLOAT_FORMAT_10_3,
						avgConsHandleTime,
						h -> {
							avgConsHandleTime = new StatsRunningAverage(h);
							return avgConsHandleTime;
						},
						null,
						() -> avgConsHandleTime.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"rounds/sec",
						"average number of rounds per second",
						"%,11.3f",
						roundsPerSecond,
						(h) -> {
							roundsPerSecond = new StatsSpeedometer(h);
							return roundsPerSecond;
						},
						null,
						() -> roundsPerSecond.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"ping",
						"average time for a round trip message between 2 computers (in milliseconds)",
						"%,7.0f",
						avgPing,
						h -> {
							avgPing = new StatsRunningAverage(h);
							return avgPing;
						},
						null,
						() -> avgPing.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"bytes/sec_trans",
						"number of bytes in the transactions received per second (from unique events created " +
								"by self and others)",
						FLOAT_FORMAT_16_2,
						bytesPerSecondTrans,
						h -> {
							bytesPerSecondTrans = new StatsSpeedometer(h);
							return bytesPerSecondTrans;
						},
						null,
						() -> bytesPerSecondTrans.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"bytes/sec_sent",
						"number of bytes sent per second over the network (total for this member)",
						FLOAT_FORMAT_16_2,
						bytesPerSecondSent,
						h -> {
							bytesPerSecondSent = new StatsSpeedometer(6 * h);
							return bytesPerSecondSent;
						},
						h -> bytesPerSecondSent.reset(6 * h),
						() -> bytesPerSecondSent.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"bytes/sec_catchup",
						"number of bytes sent per second to help others catch up",
						FLOAT_FORMAT_16_2,
						bytesPerSecondCatchupSent,
						h -> {
							bytesPerSecondCatchupSent = new StatsSpeedometer(h);
							return bytesPerSecondCatchupSent;
						},
						null,
						() -> bytesPerSecondCatchupSent.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"bytes/sec_sys",
						"number of bytes in the system transactions received per second (from unique events " +
								"created by self and others)",

						FLOAT_FORMAT_16_2,
						bytesPerSecondSys,
						h -> {
							bytesPerSecondSys = new StatsSpeedometer(h);
							return bytesPerSecondSys;
						},
						null,
						() -> bytesPerSecondSys.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"trans/sec",
						"number of app transactions received per second (from unique events created by self and " +
								"others)",

						"%,13.2f",
						transactionsPerSecond,
						h -> {
							transactionsPerSecond = new StatsSpeedometer(h);
							return transactionsPerSecond;
						},
						null,
						() -> transactionsPerSecond.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"events/sec",
						"number of unique events received per second (created by self and others)",
						FLOAT_FORMAT_16_2,
						eventsPerSecond,
						h -> {
							eventsPerSecond = new StatsSpeedometer(h);
							return eventsPerSecond;
						},
						null,
						() -> eventsPerSecond.getCyclesPerSecond()),// },
				new StatEntry(
						INTERNAL_CATEGORY,
						"dupEv/sec",
						"number of events received per second that are already known",
						"%,14.2f",
						duplicateEventsPerSecond,
						h -> {
							duplicateEventsPerSecond = new StatsSpeedometer(h);
							return duplicateEventsPerSecond;
						},
						null,
						() -> duplicateEventsPerSecond.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"dupEv%",
						"percentage of events received that are already known",
						"%,10.2f",
						avgDuplicatePercent,
						h -> {
							avgDuplicatePercent = new StatsRunningAverage(h);
							return avgDuplicatePercent;
						},
						null,
						() -> avgDuplicatePercent.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"badEv/sec",
						"number of corrupted events received per second",
						"%,14.7f",
						badEventsPerSecond,
						h -> {
							badEventsPerSecond = new StatsSpeedometer(h);
							return badEventsPerSecond;
						},
						null,
						() -> badEventsPerSecond.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"sync/secC",
						"(call syncs) syncs completed per second initiated by this member",
						"%,14.7f",
						callSyncsPerSecond,
						h -> {
							callSyncsPerSecond = new StatsSpeedometer(h);
							return callSyncsPerSecond;
						},
						null,
						() -> callSyncsPerSecond.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"sync/secR",
						"(receive syncs) syncs completed per second initiated by other member",
						"%,14.7f",
						recSyncsPerSecond,
						h -> {
							recSyncsPerSecond = new StatsSpeedometer(h);
							return recSyncsPerSecond;
						},
						null,
						() -> recSyncsPerSecond.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"icSync/sec",
						"(interrupted call syncs) syncs interrupted per second initiated by this member",
						"%,14.7f",
						interruptedCallSyncsPerSecond,
						h -> {
							interruptedCallSyncsPerSecond = new StatsSpeedometer(h);
							return interruptedCallSyncsPerSecond;
						},
						null,
						() -> interruptedCallSyncsPerSecond
								.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"irSync/sec",
						"(interrupted receive syncs) syncs interrupted per second initiated by other member",
						"%,14.7f",
						interruptedRecSyncsPerSecond,
						h -> {
							interruptedRecSyncsPerSecond = new StatsSpeedometer(h);
							return interruptedRecSyncsPerSecond;
						},
						null,
						() -> interruptedRecSyncsPerSecond
								.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"memFree",
						"bytes of free memory (which can increase after a garbage collection)",
						FLOAT_FORMAT_16_0,
						memFree,
						h -> {
							memFree = new StatsRunningAverage(0);
							return memFree;
						},// zero lambda for no smoothing
						h -> memFree.reset(0),// zero lambda for no smoothing
						() -> memFree.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"memTot",
						"total bytes in the Java Virtual Machine",
						FLOAT_FORMAT_16_0,
						memTot,
						h -> {
							memTot = new StatsRunningAverage(0);
							return memTot;
						},// zero lambda for no smoothing
						h -> memTot.reset(0),// zero lambda for no smoothing
						() -> memTot.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"memMax",
						"maximum bytes that the JVM might use",
						FLOAT_FORMAT_16_0,
						memMax,
						h -> {
							memMax = new StatsRunningAverage(0);
							return memMax;
						},// zero lambda for no smoothing
						h -> memMax.reset(0),// zero lambda for no smoothing
						() -> memMax.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"directMemInMB",
						"megabytes of off-heap (direct) memory being used by the JVM",
						FLOAT_FORMAT_16_2,
						directMemInMB,
						h -> {
							directMemInMB = new StatsRunningAverage(0);
							return directMemInMB;
						},// zero lambda for no smoothing
						h -> directMemInMB.reset(0),// zero lambda for no smoothing
						() -> directMemInMB.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"directMemPercent",
						"off-heap (direct) memory used, as a percent of MaxDirectMemorySize",
						FLOAT_FORMAT_16_2,
						directMemPercent,
						h -> {
							directMemPercent = new StatsRunningAverage(0);
							return directMemPercent;
						},// zero lambda for no smoothing
						h -> directMemPercent.reset(0),// zero lambda for no smoothing
						() -> directMemPercent.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"proc",
						"number of processors (cores) available to the JVM",
						"%,8.0f",
						avgNumProc,
						h -> {
							avgNumProc = new StatsRunningAverage(h);
							return avgNumProc;
						},
						null,
						() -> avgNumProc.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"cpuLoadSys",
						"the CPU load of the whole system",
						"%,1.4f",
						cpuLoadSys,
						h -> {
							cpuLoadSys = new StatsRunningAverage(h);
							return cpuLoadSys;
						},
						null,
						() -> cpuLoadSys.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"threads",
						"the current number of live threads",
						"%,6.0f",
						threads,
						h -> {
							threads = new StatsRunningAverage(h);
							return threads;
						},
						null,
						() -> threads.getWeightedMean()),
				new StatEntry(
						INFO_CATEGORY,
						"name",
						"name of this member",
						"%8s",
						null,
						null,
						null,
						() -> {
							if (platform.isMirrorNode()) {
								return "Mirror-" + platform.getSelfId().getId();
							}
							return platform.getAddressBook()
									.getAddress(platform.getSelfId().getId())
									.getSelfName();
						}),
				new StatEntry(
						INFO_CATEGORY,
						"memberID",
						"ID number of this member",
						"%3.0f",
						avgSelfId,
						h -> {
							avgSelfId = new StatsRunningAverage(h);
							return avgSelfId;
						},
						null,
						() -> avgSelfId.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"members",
						"total number of members participating",
						"%,10.0f",
						avgNumMembers,
						h -> {
							avgNumMembers = new StatsRunningAverage(h);
							return avgNumMembers;
						},
						null,
						() -> avgNumMembers.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"local",
						"number of members running on this local machine",
						"%,8.0f",
						avgNumLocal,
						h -> {
							avgNumLocal = new StatsRunningAverage(h);
							return avgNumLocal;
						},
						null,
						() -> avgNumLocal.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"write",
						"the app claimed to log statistics every this many milliseconds",
						"%,8.0f",
						avgWrite,
						h -> {
							avgWrite = new StatsRunningAverage(h);
							return avgWrite;
						},
						null,
						() -> avgWrite.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"bytes/trans",
						"number of bytes in each transactions",
						FLOAT_FORMAT_16_0,
						avgBytesPerTransaction,
						h -> {
							avgBytesPerTransaction = new StatsRunningAverage(h);
							return avgBytesPerTransaction;
						},
						null,
						() -> avgBytesPerTransaction.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"trans/event",
						"number of app transactions in each event",
						"%,17.1f",
						avgTransactionsPerEvent,
						h -> {
							avgTransactionsPerEvent = new StatsRunningAverage(h);
							return avgTransactionsPerEvent;
						},
						null,
						() -> avgTransactionsPerEvent.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"simCallSyncsMax",
						"max number of syncs this can initiate simultaneously",
						"%,2.0f",
						avgSimCallSyncsMax,
						h -> {
							avgSimCallSyncsMax = new StatsRunningAverage(h);
							return avgSimCallSyncsMax;
						},
						null,
						() -> avgSimCallSyncsMax.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"simSyncs",
						"avg number of simultaneous syncs happening at any given time",
						"%,9.6f",
						avgSimSyncs,
						h -> {
							avgSimSyncs = new StatsRunningAverage(h);
							return avgSimSyncs;
						},
						null,
						() -> avgSimSyncs.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"simListenSyncs",
						"avg number of simultaneous listening syncs happening at any given time",
						"%,9.6f",
						avgSimListenSyncs,
						h -> {
							avgSimListenSyncs = new StatsRunningAverage(h);
							return avgSimListenSyncs;
						},
						null,
						() -> avgSimListenSyncs.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"cEvents/sec",
						"number of events per second created by this node",
						FLOAT_FORMAT_16_2,
						eventsCreatedPerSecond,
						h -> {
							eventsCreatedPerSecond = new StatsSpeedometer(h);
							return eventsCreatedPerSecond;
						},
						null,
						() -> eventsCreatedPerSecond.getCyclesPerSecond()),
				new StatEntry(
						CATEGORY,
						"secC2R",
						"time from another member creating an event to receiving it and veryfing the " +
								"signature (in seconds)",
						FLOAT_FORMAT_10_3,
						avgCreatedReceivedTime,
						h -> {
							avgCreatedReceivedTime = new StatsRunningAverage(h);
							return avgCreatedReceivedTime;
						},
						null,
						() -> avgCreatedReceivedTime.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"secC2RC",
						"time from another member creating an event to it being received and and knowing  " +
								"consensus for it (in seconds)",
						FLOAT_FORMAT_10_3,
						avgCreatedReceivedConsensusTime,
						h -> {
							avgCreatedReceivedConsensusTime = new StatsRunningAverage(h);
							return avgCreatedReceivedConsensusTime;
						},
						null,
						() -> avgCreatedReceivedConsensusTime
								.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"secR2nR",
						"time from first event received in one round, to first event received in the " +
								"next round (in seconds)",
						FLOAT_FORMAT_10_3,
						avgFirstEventInRoundReceivedTime,
						h -> {
							avgFirstEventInRoundReceivedTime = new StatsRunningAverage(h);
							return avgFirstEventInRoundReceivedTime;
						},
						null,
						() -> avgFirstEventInRoundReceivedTime
								.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"secR2F",
						"time from a round's first received event to all the famous witnesses being known (in seconds)",
						FLOAT_FORMAT_10_3,
						avgReceivedFamousTime,
						h -> {
							avgReceivedFamousTime = new StatsRunningAverage(h);
							return avgReceivedFamousTime;
						},
						null,
						() -> avgReceivedFamousTime.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"q1",
						"number of non-consensus events in queue waiting to be handled",
						FLOAT_FORMAT_10_3,
						avgQ1forCurr,
						h -> {
							avgQ1forCurr = new StatsRunningAverage(h);
							return avgQ1forCurr;
						},
						null,
						() -> avgQ1forCurr.getWeightedMean()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"queueSignedStateEvents",
						"number of handled consensus events that will be part of the next signed state",
						"%,10.1f",
						avgQSignedStateEvents,
						(h) -> {
							avgQSignedStateEvents = new StatsRunningAverage(h);
							return avgQSignedStateEvents;
						},
						null,
						() -> avgQSignedStateEvents.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"q4",
						"number events in receiving queue waiting to be processed or created",
						"%,10.1f",
						avgQ4forHash,
						h -> {
							avgQ4forHash = new StatsRunningAverage(h);
							return avgQ4forHash;
						},
						null,
						() -> avgQ4forHash.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"roundSup",
						"latest round with state signed by a supermajority",
						"%,10.0f",
						avgRoundSupermajority,
						h -> {
							avgRoundSupermajority = new StatsRunningAverage(h);
							return avgRoundSupermajority;
						},
						null,
						() -> avgRoundSupermajority.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"secSC2T",
						"self event consensus timestamp minus time created (in seconds)",
						FLOAT_FORMAT_10_3,
						avgSelfCreatedTimestamp,
						(h) -> {
							avgSelfCreatedTimestamp = new StatsRunningAverage(h);
							return avgSelfCreatedTimestamp;
						},
						null,
						() -> avgSelfCreatedTimestamp.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"secOR2T",
						"other event consensus timestamp minus time received (in seconds)",
						FLOAT_FORMAT_10_3,
						avgOtherReceivedTimestamp,
						h -> {
							avgOtherReceivedTimestamp = new StatsRunningAverage(h);
							return avgOtherReceivedTimestamp;
						},
						null,
						() -> avgOtherReceivedTimestamp.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"eventsInMem",
						"total number of events in memory, for all members on the local machine together",
						FLOAT_FORMAT_16_2,
						avgEventsInMem,
						h -> {
							avgEventsInMem = new StatsRunningAverage(h);
							return avgEventsInMem;
						},
						null,
						() -> avgEventsInMem.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"trans/sec_sys",
						"number of system transactions received per second (from unique events created by self and " +
								"others)",

						"%,13.2f",
						transactionsPerSecondSys,
						h -> {
							transactionsPerSecondSys = new StatsSpeedometer(h);
							return transactionsPerSecondSys;
						},
						null,
						() -> transactionsPerSecondSys.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"bytes/trans_sys",
						"number of bytes in each system transaction",
						FLOAT_FORMAT_16_0,
						avgBytesPerTransactionSys,
						h -> {
							avgBytesPerTransactionSys = new StatsRunningAverage(h);
							return avgBytesPerTransactionSys;
						},
						null,
						() -> avgBytesPerTransactionSys.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"trans/event_sys",
						"number of system transactions in each event",
						"%,17.1f",
						avgTransactionsPerEventSys,
						h -> {
							avgTransactionsPerEventSys = new StatsRunningAverage(h);
							return avgTransactionsPerEventSys;
						},
						null,
						() -> avgTransactionsPerEventSys.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"conns",
						"number of times a TLS connections was created",
						"%,10.0f",
						avgConnsCreated,
						h -> {
							avgConnsCreated = new StatsRunningAverage(0);
							return avgConnsCreated;
						},// no smoothing
						h -> avgConnsCreated.reset(0),// zero lambda for no smoothing
						() -> avgConnsCreated.getWeightedMean()),
				new StatEntry(
						INFO_CATEGORY,
						"TLS",
						"1 if using TLS, 0 if not",
						"%6d",
						null,
						null,
						null,
						() -> Settings.useTLS ? 1 : 0),
				new StatEntry(
						INTERNAL_CATEGORY,
						"fracSyncSlowed",
						"fraction of syncs that are slowed to let others catch up",
						"%,9.6f",
						fracSyncSlowed,
						h -> {
							fracSyncSlowed = new StatsRunningAverage(h);
							return fracSyncSlowed;
						},
						null,
						() -> fracSyncSlowed.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"timeFracDot",
						"fraction of each second spent on dot products",
						"%,9.6f",
						timeFracDot,
						h -> {
							timeFracDot = new StatsSpeedometer(h);
							return timeFracDot;
						},
						null,
						() -> timeFracDot.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"timeFracAdd",
						"fraction of each second spent adding an event to the hashgraph and finding consensus",
						"%,9.6f",
						timeFracAdd,
						h -> {
							timeFracAdd = new StatsSpeedometer(h);
							return timeFracAdd;
						},
						null,
						() -> timeFracAdd.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"sleep1/sec",
						"sleeps per second because caller thread had too many failed connects",
						"%,9.6f",
						sleep1perSecond,
						h -> {
							sleep1perSecond = new StatsSpeedometer(h);
							return sleep1perSecond;
						},
						null,
						() -> sleep1perSecond.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						PlatformStatNames.TRANSACTIONS_HANDLED_PER_SECOND,
						"number of consensus transactions per second handled by SwirldState.handleTransaction()",
						"%,9.6f",
						transHandledPerSecond,
						h -> {
							transHandledPerSecond = new StatsSpeedometer(h);
							return transHandledPerSecond;
						},
						null,
						() -> transHandledPerSecond.getCyclesPerSecond()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"secTransH",
						"avg time to handle a consensus transaction in SwirldState.handleTransaction (in seconds)",
						"%,10.6f",
						avgSecTransHandled,
						h -> {
							avgSecTransHandled = new StatsRunningAverage(h);
							return avgSecTransHandled;
						},
						null,
						() -> avgSecTransHandled.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"stateCopyMicros",
						"average time it takes the SwirldState.copy() method in SwirldState to finish (in " +
								"microseconds)",
						FLOAT_FORMAT_16_2,
						avgStateCopyMicros,
						h -> {
							avgStateCopyMicros = new StatsRunningAverage(h);
							return avgStateCopyMicros;
						},
						null,
						() -> avgStateCopyMicros.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						SIGNED_STATE_HASHING_TIME,
						"average time it takes to create a new SignedState (in seconds)",
						FLOAT_FORMAT_10_3,
						avgSecNewSignedState,
						h -> {
							avgSecNewSignedState = new StatsRunningAverage(h);
							return avgSecNewSignedState;
						},
						null,
						() -> avgSecNewSignedState.getWeightedMean()),
				new StatEntry(
						CATEGORY,
						"bytes/sec_sync",
						"average number of bytes per second transfered during a sync",
						FLOAT_FORMAT_16_2,
						avgBytesPerSecSync,
						h -> {
							avgBytesPerSecSync = new StatsRunningAverage(h);
							return avgBytesPerSecSync;
						},
						null,
						() -> avgBytesPerSecSync.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"stateSigs",
						"number of signatures collected on each signed state",
						"%,10.2f",
						avgStateSigs,
						h -> {
							avgStateSigs = new StatsRunningAverage(h);
							return avgStateSigs;
						},
						null,
						() -> avgStateSigs.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"coinR",
						"number of coin rounds that have occurred so far",
						"%,10.0f",
						numCoinRounds,
						h -> {
							numCoinRounds = new StatsRunningAverage(h);
							return numCoinRounds;
						},
						null,
						() -> numCoinRounds.getWeightedMean()),

				new StatEntry(
						INFO_CATEGORY,
						"lastGen",
						"last event generation number by me",
						"%d",
						null,
						null,
						null,

						() -> {
							if (platform.isMirrorNode()) {
								return -1;
							}
							return platform.getLastGen(platform.getSelfId().getId());
						}),


				new StatEntry(
						INFO_CATEGORY,
						"transEvent",
						"transEvent queue size",
						"%d",
						null,
						null,
						null,
						this::getTransEventSize),

				new StatEntry(
						INFO_CATEGORY,
						"transCons",
						"transCons queue size",
						"%d",
						null,
						null,
						null,
						this::getTransConsSize),

				// Statistics for monitoring transaction and event creation logic

				new StatEntry(
						INTERNAL_CATEGORY,
						"isEvFrozen",
						"isEventCreationFrozen",
						"%b",
						null,
						null,
						null,
						() -> platform.getFreezeManager().isEventCreationFrozen()
								|| platform.getStartUpEventFrozenManager().isEventCreationPausedAfterStartUp()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"isStrongMinorityInMaxRound",
						"isStrongMinorityInMaxRound",
						"%b",
						null,
						null,
						null,
						() -> {
							if (platform.isMirrorNode()) {
								return false;
							}
							return platform.getCriticalQuorum().isInCriticalQuorum(platform.getSelfId().getId());
						}),

				new StatEntry(
						INTERNAL_CATEGORY,
						"transThrottleCallAndCreate",
						"transThrottleCallAndCreate",
						"%b",
						null,
						null,
						null,
						() -> platform.getSyncManager() == null ? 0 :
								platform.getSyncManager().transThrottleCallAndCreate()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"getNumUserTransEvents",
						"getNumUserTransEvents",
						"%d",
						null,
						null,
						null,
						() -> platform.getTransactionTracker().getNumUserTransEvents()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"hasFallenBehind",
						"hasFallenBehind",
						"%b",
						null,
						null,
						null,
						() -> platform.getSyncManager() == null ? 0 :
								platform.getSyncManager().hasFallenBehind()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"shouldCreateEvent",
						"shouldCreateEvent",
						"%,10.1f",
						shouldCreateEvent,
						(h) -> {
							shouldCreateEvent = new StatsRunningAverage(h);
							return shouldCreateEvent;
						},
						null,
						() -> shouldCreateEvent.getWeightedMean()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"numReportFallenBehind",
						"numReportFallenBehind",
						"%d",
						null,
						null,
						null,
						() -> platform.getSyncManager() == null ? 0 :
								platform.getSyncManager().numReportFallenBehind),

				new StatEntry(
						INTERNAL_CATEGORY,
						"staleEv/sec",
						"number of stale events per second",
						FLOAT_FORMAT_16_2,
						staleEventsPerSecond,
						h -> {
							staleEventsPerSecond = new StatsSpeedometer(h);
							return staleEventsPerSecond;
						},
						null,
						() -> staleEventsPerSecond.getCyclesPerSecond()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"staleEvTot",
						"total number of stale events ever",
						"%d",
						null,
						null,
						null,
						() -> staleEventsTotal.get()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"rescuedEv/sec",
						"number of events per second generated to prevent stale events",
						FLOAT_FORMAT_16_2,
						rescuedEventsPerSecond,
						h -> {
							rescuedEventsPerSecond = new StatsSpeedometer(h);
							return rescuedEventsPerSecond;
						},
						null,
						() -> rescuedEventsPerSecond.getCyclesPerSecond()),

				new StatEntry(
						INTERNAL_CATEGORY,
						"DiskspaceFree",
						"disk space being used right now",
						"%d",
						null,
						null,
						null,
						() -> freeDiskspace),
				new StatEntry(
						INTERNAL_CATEGORY,
						"DiskspaceWhole",
						"total disk space available on node",
						"%d",
						null,
						null,
						null,
						() -> totalDiskspace),
				new StatEntry(
						INTERNAL_CATEGORY,
						"DiskspaceUsed",
						"disk space free for use by the node",
						"%d",
						null,
						null,
						null,
						() -> totalDiskspace - freeDiskspace),
				new StatEntry(
						INFO_CATEGORY,
						"eventStreamQueueSize",
						"size of the queue from which we take events and write to EventStream file",
						FLOAT_FORMAT_13_0,
						eventStreamQueueSize,
						h -> eventStreamQueueSize = new StatsRunningAverage(h),
						null,
						() -> eventStreamQueueSize.getWeightedMean()),
				new StatEntry(
						INFO_CATEGORY,
						"hashQueueSize",
						"size of the queue from which we take events, calculate Hash and RunningHash",
						FLOAT_FORMAT_13_0,
						hashQueueSize,
						h -> hashQueueSize = new StatsRunningAverage(h),
						null,
						() -> hashQueueSize.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"shuffleMicros",
						"average time spent in SwirldStateManagerSingle.Shuffler#shuffle() method (in " +
								"microseconds)",
						FLOAT_FORMAT_16_2,
						avgShuffleMicros,
						h -> {
							avgShuffleMicros = new StatsRunningAverage(h);
							return avgShuffleMicros;
						},
						null,
						() -> avgShuffleMicros.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"stateToHashSignDepth",
						"average depth of the stateToHashSign queue (number of SignedStates)",
						FLOAT_FORMAT_16_2,
						avgStateToHashSignDepth,
						h -> {
							avgStateToHashSignDepth = new StatsRunningAverage(h);
							return avgStateToHashSignDepth;
						},
						null,
						() -> avgStateToHashSignDepth.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"stateArchivalQueueAvg",
						"avg length of the state archival queue",
						FLOAT_FORMAT_15_3,
						stateArchivalQueueAvg,
						h -> {
							stateArchivalQueueAvg = new StatsRunningAverage(h);
							return stateArchivalQueueAvg;
						},
						null,
						() -> stateArchivalQueueAvg.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"stateArchivalTimeAvg",
						"avg time to archive a signed state (in microseconds)",
						FLOAT_FORMAT_15_3,
						stateArchivalTimeAvg,
						h -> {
							stateArchivalTimeAvg = new StatsRunningAverage(h);
							return stateArchivalTimeAvg;
						},
						null,
						() -> stateArchivalTimeAvg.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"stateDeletionQueueAvg",
						"avg length of the state deletion queue",
						FLOAT_FORMAT_15_3,
						stateDeletionQueueAvg,
						h -> {
							stateDeletionQueueAvg = new StatsRunningAverage(h);
							return stateDeletionQueueAvg;
						},
						null,
						() -> stateDeletionQueueAvg.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						"stateDeletionTimeAvg",
						"avg time it takes to delete a signed state (in microseconds)",
						FLOAT_FORMAT_15_3,
						stateDeletionTimeAvg,
						h -> {
							stateDeletionTimeAvg = new StatsRunningAverage(h);
							return stateDeletionTimeAvg;
						},
						null,
						() -> stateDeletionTimeAvg.getWeightedMean()),
				new StatEntry(
						INTERNAL_CATEGORY,
						PlatformStatNames.TIPS_PER_SYNC,
						"the average number of tips per sync at the start of each sync",
						FLOAT_FORMAT_15_3,
						tipsPerSync,
						h -> {
							tipsPerSync = new StatsRunningAverage(h);
							return tipsPerSync;
						},
						null,
						() -> tipsPerSync.getWeightedMean()),
				new StatEntry(INTERNAL_CATEGORY,
						"issCount",
						"the number nodes that currently disagree with the hash of this node's state",
						"%d",
						null,
						null,
						null,
						issCount::get)
		};
		final List<StatEntry> entryList = new ArrayList<>(Arrays.asList(statEntries));

		entryList.addAll(additionalEntries);

		entryList.add(this.savedFileStatistics.createStatForTrackingFileSize("SignedState.swh",
				"Size of SignedState.swh, bytes"));
		entryList.add(this.savedFileStatistics.createStatForTrackingFileSize("PostgresBackup.tar.gz",
				"Size of PostgresDB, bytes"));

		// atomic stats
		entryList.addAll(avgEventsPerSyncSent.getAllEntries());
		entryList.addAll(avgEventsPerSyncRec.getAllEntries());
		entryList.addAll(avgSyncDuration.getAllEntries());
		entryList.addAll(consensusCycleTiming.getAllEntries());
		entryList.addAll(newSignedStateCycleTiming.getAllEntries());
		entryList.add(avgSyncDuration1.getAverageStat());
		entryList.add(avgSyncDuration2.getAverageStat());
		entryList.add(avgSyncDuration3.getAverageStat());
		entryList.add(avgSyncDuration4.getAverageStat());
		entryList.add(avgSyncDuration5.getAverageStat());
		entryList.add(syncGenerationDiff.getStatEntry());
		entryList.add(eventRecRate.getStatEntry());
		entryList.add(rejectedSyncRatio.getStatEntry());
		entryList.add(avgTransSubmitMicros.getStatEntry());
		entryList.add(averageOtherParentAgeDiff.getStatEntry());
		entryList.add(gensWaitingForExpiry.getStatEntry());
		entryList.add(knownSetSize.getStatEntry());
		entryList.add(multiTipsPerSync.getStatEntry());
		entryList.add(noMoreTransDuration.getAverageStat());
		entryList.add(avgStateCopyAdmit.getAverageStat());
		entryList.add(preConsHandleTime.getAverageStat());
		entryList.add(avgQ2ConsEvents.getAverageStat());
		entryList.add(avgQ2ConsEvents.getMaxStat());
		entryList.add(avgEventsPerRound.getAverageStat());
		entryList.add(avgEventsPerRound.getMaxStat());

		for (int i = 0; i < avgPingMilliseconds.length; i++) {
			final int ii = i; //make the current value of i into a constant inside each lambda generated here
			entryList.add(new StatEntry(
					PING_CATEGORY,
					String.format("ping_ms_%02d", i),
					String.format("milliseconds to send node %02d a byte and receive a reply", ii),
					"%,4.2f",
					avgPingMilliseconds[i] = new StatsRunningAverage(Settings.halfLife),
					h -> (avgPingMilliseconds[ii] = new StatsRunningAverage(h)),
					null,
					() -> avgPingMilliseconds[ii].getWeightedMean()));
			avgPingMilliseconds[i] = new StatsRunningAverage(Settings.halfLife);
		}
		for (int i = 0; i < avgBytePerSecSent.length; i++) {
			final int ii = i; //make the current value of i into a constant inside each lambda generated here
			entryList.add(new StatEntry(
					BPSS_CATEGORY,
					String.format("bytes/sec_sent_%02d", i),
					String.format("bytes per second sent to node %02d", ii),
					FLOAT_FORMAT_16_2,
					avgBytePerSecSent[i] = new StatsSpeedometer(Settings.halfLife),
					h -> (avgBytePerSecSent[ii] = new StatsSpeedometer(h)),
					null,
					() -> avgBytePerSecSent[ii].getCyclesPerSecond()));
		}

		reconnectStatistics.registerStats(entryList);
		setDirectMemMXBean();
		setMaximumDirectMemSizeInMB();

		statEntries = entryList.toArray(statEntries);
	}

	private int getTransEventSize() {
		if (platform.getSwirldStateManager() == null ||
				platform.getSwirldStateManager().getTransactionPool() == null) {
			return 0;
		}
		return platform.getSwirldStateManager().getTransactionPool().getEventSize();
	}

	private long getTransConsSize() {
		if (platform.getSwirldStateManager() == null
				|| platform.getSwirldStateManager() instanceof SwirldStateManagerDouble) {
			return 0;
		}
		return ((SwirldStateSingleTransactionPool) platform.getSwirldStateManager().getTransactionPool()).getConsSize();
	}

	//
	// direct memory stats methods below
	//

	private void setDirectMemMXBean() {
		// scan through PlatformMXBeans to find the one responsible for direct memory used
		final List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
		for (final BufferPoolMXBean pool : pools) {
			if (pool.getName().equals("direct")) {
				directMemMXBean = pool;
				return;
			}
		}
	}

	private void setMaximumDirectMemSizeInMB() {
		final HotSpotDiagnosticMXBean hsdiag = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
		long maxDirectMemoryInBytes = Runtime.getRuntime().maxMemory();
		if (hsdiag != null) {
			try {
				final long value = Long.parseLong(hsdiag.getVMOption("MaxDirectMemorySize").getValue());
				if (value > 0) {
					maxDirectMemoryInBytes = value;
				}
			} catch (final NumberFormatException ex) {
				// just use the present value, namely Runtime.getRuntime().maxMemory().
			}
		}
		maximumDirectMemSizeInMB = maxDirectMemoryInBytes * Units.BYTES_TO_MEBIBYTES;
	}

	//
	// ConsensusStats below
	//

	/**
	 * Time when this platform received the first event created by someone else in the most recent round.
	 * This is used to calculate Statistics.avgFirstEventInRoundReceivedTime which is "time for event, from
	 * receiving the first event in a round to the first event in the next round".
	 */
	private volatile Instant firstEventInLastRoundTime = null;
	/** the max round number for which at least one event is known that was created by someone else */
	private volatile long lastRoundNumber = -1;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addedEvent(final EventImpl event) {
		// this method is only ever called by 1 thread, so no need for locks
		if (!platform.getSelfId().equalsMain(event.getCreatorId())
				&& event.getRoundCreated() > lastRoundNumber) {// if first event in a round
			final Instant now = Instant.now();
			if (firstEventInLastRoundTime != null) {
				avgFirstEventInRoundReceivedTime.recordValue(
						firstEventInLastRoundTime.until(now,
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			}
			firstEventInLastRoundTime = now;
			lastRoundNumber = event.getRoundCreated();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void coinRounds(final long numCoinRounds) {
		this.numCoinRounds.recordValue(numCoinRounds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void lastFamousInRound(final EventImpl event) {
		if (!platform.getSelfId().equalsMain(event.getCreatorId())) {// record this for events received
			avgReceivedFamousTime.recordValue(
					event.getTimeReceived().until(Instant.now(),
							ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusReached(final EventImpl event) {
		// Keep a running average of how many seconds from when I first know of an event
		// until it achieves consensus. Actually, keep two such averages: one for events I
		// create, and one for events I receive.
		// Because of transThrottle, these statistics can end up being misleading, so we are only tracking events that
		// have user transactions in them.
		if (event.hasUserTransactions()) {
			if (platform.getSelfId().equalsMain(event.getCreatorId())) { // set either created or received time to now
				avgCreatedConsensusTime
						.recordValue(event.getTimeReceived().until(Instant.now(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			} else {
				avgReceivedConsensusTime
						.recordValue(event.getTimeReceived().until(Instant.now(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
				avgCreatedReceivedConsensusTime
						.recordValue(event.getTimeCreated().until(Instant.now(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			}
		}

		// Because of transThrottle, these statistics can end up being misleading, so we are only tracking events that
		// have user transactions in them.
		if (event.hasUserTransactions()) {
			if (platform.getSelfId().equalsMain(event.getCreatorId())) {
				avgSelfCreatedTimestamp.recordValue(
						event.getTimeCreated().until(event.getConsensusTimestamp(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			} else {
				avgOtherReceivedTimestamp.recordValue(
						event.getTimeReceived().until(event.getConsensusTimestamp(),
								ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusReachedOnRound() {
		roundsPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dotProductTime(final long nanoTime) {
		timeFracDot.update(((double) nanoTime) * NANOSECONDS_TO_SECONDS);
	}

	//
	// HashgraphStats below
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void staleEvent(final EventImpl event) {
		staleEventsTotal.incrementAndGet();
		staleEventsPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rescuedEvent() {
		rescuedEventsPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void duplicateEvent() {
		duplicateEventsPerSecond.cycle();
		avgDuplicatePercent.recordValue(100); // move toward 100%
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void nonDuplicateEvent() {
		avgDuplicatePercent.recordValue(0); // move toward 0%
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void receivedEvent(final EventImpl event) {
		avgCreatedReceivedTime.recordValue(
				event.getTimeCreated().until(event.getTimeReceived(),
						ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invalidEventSignature() {
		badEventsPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processedEventTask(final long startTime) {
		// nanoseconds spent adding to hashgraph
		timeFracAdd.update(((double) time() - startTime) * NANOSECONDS_TO_SECONDS);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void eventAdded(final EventImpl event) {
		if (event.isCreatedBy(platform.getSelfId())) {
			eventsCreatedPerSecond.cycle();
			if (event.getBaseEventHashedData().hasOtherParent()) {
				averageOtherParentAgeDiff.update(event.getGeneration() - event.getOtherParentGen());
			}
		}

		// count the unique events in the hashgraph
		eventsPerSecond.cycle();

		// record stats for all transactions in this event
		final Transaction[] trans = event.getTransactions();
		final int numTransactions = (trans == null ? 0 : trans.length);

		// we have already ensured this isn't a duplicate event, so record all the stats on it:

		// count the bytes in the transactions, and bytes per second, and transactions per event
		// for both app transactions and system transactions.
		// Handle system transactions
		int appSize = 0;
		int sysSize = 0;
		int numAppTrans = 0;
		int numSysTrans = 0;
		for (int i = 0; i < numTransactions; i++) {
			if (trans[i].isSystem()) {
				numSysTrans++;
				sysSize += trans[i].getSerializedLength();
				avgBytesPerTransactionSys.recordValue(trans[i].getSerializedLength());
			} else {
				numAppTrans++;
				appSize += trans[i].getSerializedLength();
				avgBytesPerTransaction.recordValue(trans[i].getSerializedLength());
			}
		}
		avgTransactionsPerEvent.recordValue(numAppTrans);
		avgTransactionsPerEventSys.recordValue(numSysTrans);
		bytesPerSecondTrans.update(appSize);
		bytesPerSecondSys.update(sysSize);
		// count each transaction within that event (this is like calling cycle() numTrans times)
		transactionsPerSecond.update(numAppTrans);
		transactionsPerSecondSys.update(numSysTrans);

		// count all transactions ever in the hashgraph
		this.numTrans.addAndGet(event.getTransactions().length);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateArchivalQueue(final int len) {
		stateArchivalQueueAvg.recordValue(len);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateArchivalTime(final double time) {
		stateArchivalTimeAvg.recordValue(time);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateDeletionQueue(final int len) {
		stateDeletionQueueAvg.recordValue(len);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateDeletionTime(final double time) {
		stateDeletionTimeAvg.recordValue(time);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateMultiTipsPerSync(final int multiTipCount) {
		multiTipsPerSync.update(multiTipCount);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateTipsPerSync(final int tipCount) {
		tipsPerSync.recordValue(tipCount);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateGensWaitingForExpiry(final long numGenerations) {
		gensWaitingForExpiry.update(numGenerations);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateRejectedSyncRatio(final boolean syncRejected) {
		rejectedSyncRatio.update(syncRejected);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateTransSubmitMicros(final long microseconds) {
		avgTransSubmitMicros.update(microseconds);
	}

	public ReconnectStatistics getReconnectStats() {
		return reconnectStatistics;
	}

	@Override
	public void generations(final GraphGenerations self, final GraphGenerations other) {
		syncGenerationDiff.update(self.getMaxRoundGeneration() - other.getMaxRoundGeneration());
	}

	@Override
	public void eventsReceived(final long nanosStart, final int numberReceived) {
		if (numberReceived == 0) {
			return;
		}
		final double nanos = ((double) System.nanoTime()) - nanosStart;
		final double seconds = nanos / ChronoUnit.SECONDS.getDuration().toNanos();
		eventRecRate.update(Math.round(numberReceived / seconds));
	}

	@Override
	public void syncThrottleBytesWritten(final int bytesWritten) {
		bytesPerSecondCatchupSent.update(bytesWritten);
		fracSyncSlowed.recordValue(bytesWritten > 0 ? 1 : 0);
	}

	@Override
	public void recordSyncTiming(final SyncTiming timing, final SyncConnection conn) {
		avgSyncDuration1.update(timing.getTimePoint(0), timing.getTimePoint(1));
		avgSyncDuration2.update(timing.getTimePoint(1), timing.getTimePoint(2));
		avgSyncDuration3.update(timing.getTimePoint(2), timing.getTimePoint(3));
		avgSyncDuration4.update(timing.getTimePoint(3), timing.getTimePoint(4));
		avgSyncDuration5.update(timing.getTimePoint(4), timing.getTimePoint(5));

		avgSyncDuration.update(timing.getTimePoint(0), timing.getTimePoint(5));
		final double syncDurationSec = timing.getPointDiff(5, 0) * Units.NANOSECONDS_TO_SECONDS;
		final double speed =
				Math.max(conn.getDis().getSyncByteCounter().getCount(), conn.getDos().getSyncByteCounter().getCount())
						/ syncDurationSec;

		// set the bytes/sec speed of the sync currently measured
		avgBytesPerSecSync.recordValue(speed);
	}

	@Override
	public CycleTimingStat getConsCycleStat() {
		return consensusCycleTiming;
	}

	@Override
	public CycleTimingStat getNewSignedStateCycleStat() {
		return newSignedStateCycleTiming;
	}

	/**
	 * Records the number of events in a round.
	 *
	 * @param numEvents
	 * 		the number of events in the round
	 */
	@Override
	public void recordEventsPerRound(final int numEvents) {
		avgEventsPerRound.update(numEvents);
	}

	@Override
	public void knownSetSize(final int knownSetSize) {
		this.knownSetSize.update(knownSetSize);
	}

	@Override
	public void syncDone(final SyncResult info) {
		if (info.isCaller()) {
			callSyncsPerSecond.cycle();
		} else {
			recSyncsPerSecond.cycle();
		}
		avgEventsPerSyncSent.update(info.getEventsWritten());
		avgEventsPerSyncRec.update(info.getEventsRead());
	}

	@Override
	public void eventCreation(final boolean shouldCreateEvent) {
		this.shouldCreateEvent.recordValue(shouldCreateEvent ? 1 : 0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void recordNewSignedStateTime(final double seconds) {
		avgSecNewSignedState.recordValue(seconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusTransHandleTime(final double seconds) {
		avgSecTransHandled.recordValue(seconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusToHandleTime(final double seconds) {
		avgConsHandleTime.recordValue(seconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusTransHandled() {
		transHandledPerSecond.cycle();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shuffleMicros(final double micros) {
		avgShuffleMicros.recordValue(micros);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void stateCopyMicros(final double micros) {
		avgStateCopyMicros.recordValue(micros);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getAvgSelfCreatedTimestamp() {
		return avgSelfCreatedTimestamp.getWeightedMean();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getAvgOtherReceivedTimestamp() {
		return avgOtherReceivedTimestamp.getWeightedMean();
	}

	/**
	 * The time it takes {@link SwirldState#noMoreTransactions()} to finish
	 */
	@Override
	public void noMoreTransactionsMicros(final long start, final long end) {
		noMoreTransDuration.update(start, end);
	}

	@Override
	public void stateCopyAdmit(final long start, final long end) {
		avgStateCopyAdmit.update(start, end);
	}

	@Override
	public void setIssCount(final int issCount) {
		this.issCount.set(issCount);
	}

	@Override
	public void preConsensusHandleTime(final long start, final long end) {
		this.preConsHandleTime.update(start, end);
	}

	@Override
	public void connectionEstablished(final SyncConnection connection) {
		if (connection == null) {
			return;
		}
		connections.add(connection);
		connsCreated.incrementAndGet(); // count new connections
	}

	@Override
	public void recordPingTime(final NodeId node, final long pingNanos) {
		avgPingMilliseconds[node.getIdAsInt()].recordValue((pingNanos) / 1_000_000.0);
	}
}
