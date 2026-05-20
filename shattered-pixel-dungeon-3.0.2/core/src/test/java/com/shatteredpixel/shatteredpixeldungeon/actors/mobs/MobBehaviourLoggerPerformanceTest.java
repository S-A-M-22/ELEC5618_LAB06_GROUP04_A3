package com.shatteredpixel.shatteredpixeldungeon.actors.mobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance microbenchmarks for MobBehaviourLogger.
 *
 * Backs the assignment's "must not disrupt game performance" requirement with
 * concrete per-event numbers. Each test reports ns/event and events/sec to
 * stdout (visible via testLogging.showStandardStreams=true). The assertions
 * use generous upper bounds so the tests stay green on slow CI hosts —
 * the *reported number*, not the assertion, is the headline.
 */
class MobBehaviourLoggerPerformanceTest {

	private static final int WARMUP = 50_000;

	private MobBehaviourLogger logger;

	@BeforeEach
	void setUp() {
		logger = new MobBehaviourLogger();
		logger.setTerminalSink(s -> {});
		logger.setFileSink(s -> {});
		logger.setMaxEntries(Integer.MAX_VALUE);
	}

	@Test
	@DisplayName("disabled logging is essentially free (<10 µs/event guard)")
	void disabledLogger_addsLessThanTenMicrosecondsPerEvent() {
		logger.setEnabled(false);
		warmup();

		int n = 1_000_000;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			logger.logSpawn(i, "Rat", 0);
		}
		double nsPerEvent = (System.nanoTime() - start) / (double) n;
		report("disabled", nsPerEvent, n);
		assertTrue(nsPerEvent < 10_000,
				"disabled overhead too high: " + nsPerEvent + " ns/event");
	}

	@Test
	@DisplayName("enabled logSpawn with stub sinks (<100 µs/event guard)")
	void enabledSpawnLogging_addsLessThanHundredMicrosecondsPerEvent() {
		logger.setEnabled(true);
		warmup();

		int n = 200_000;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			logger.logSpawn(i, "Rat", 0);
		}
		double nsPerEvent = (System.nanoTime() - start) / (double) n;
		report("enabled logSpawn (stub sinks)", nsPerEvent, n);
		assertTrue(nsPerEvent < 100_000,
				"enabled overhead too high: " + nsPerEvent + " ns/event");
	}

	@Test
	@DisplayName("enabled logStateChange with stub sinks (<100 µs/event guard)")
	void enabledStateChangeLogging_addsLessThanHundredMicrosecondsPerEvent() {
		logger.setEnabled(true);
		for (int i = 0; i < WARMUP; i++) {
			logger.logStateChange(i, "Rat", 0, "Sleeping", "Hunting");
		}
		logger.clear();

		int n = 200_000;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			logger.logStateChange(i, "Rat", 0, "Sleeping", "Hunting");
		}
		double nsPerEvent = (System.nanoTime() - start) / (double) n;
		report("enabled logStateChange (stub sinks)", nsPerEvent, n);
		assertTrue(nsPerEvent < 100_000,
				"enabled overhead too high: " + nsPerEvent + " ns/event");
	}

	@Test
	@DisplayName("ring-buffer eviction at the 1000-entry cap stays cheap")
	void ringBufferAtCapacity_evictsOldestEntryWithinBudget() {
		logger.setEnabled(true);
		logger.setMaxEntries(1000);
		// Pre-fill the buffer so every iteration triggers eviction.
		for (int i = 0; i < 1000; i++) {
			logger.logSpawn(i, "Rat", 0);
		}

		int n = 200_000;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			logger.logSpawn(i, "Rat", 0);
		}
		double nsPerEvent = (System.nanoTime() - start) / (double) n;
		report("eviction at cap=1000", nsPerEvent, n);
		assertTrue(nsPerEvent < 100_000,
				"eviction overhead too high: " + nsPerEvent + " ns/event");
	}

	private void warmup() {
		for (int i = 0; i < WARMUP; i++) {
			logger.logSpawn(i, "Rat", 0);
		}
		logger.clear();
	}

	private void report(String label, double nsPerEvent, int n) {
		double eventsPerSec = 1.0e9 / nsPerEvent;
		System.out.printf("[PERF] %-40s : %8.1f ns/event  (%,.0f events/sec, N=%,d)%n",
				label, nsPerEvent, eventsPerSec, n);
	}
}
