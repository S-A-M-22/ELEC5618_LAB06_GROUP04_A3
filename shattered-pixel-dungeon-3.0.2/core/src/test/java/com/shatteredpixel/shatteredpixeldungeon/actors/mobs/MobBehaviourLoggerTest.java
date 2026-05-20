package com.shatteredpixel.shatteredpixeldungeon.actors.mobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MobBehaviourLogger.
 *
 * Tests are deliberately isolated from the rest of the codebase:
 * - We construct a fresh logger per test via the package-private constructor
 *   so the singleton can't leak state between tests.
 * - We inject in-memory sinks instead of the libGDX file sink, so no
 *   libGDX runtime is required.
 *
 * Organisation by concern, mirroring the test plan's structure.
 */
class MobBehaviourLoggerTest {

	private static final Pattern LINE_PATTERN = Pattern.compile(
			"^\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}] \\[[A-Z_]+] mob#-?\\d+\\(.*\\)@-?\\d+ : .+$");

	private MobBehaviourLogger logger;
	private List<String> terminal;
	private List<String> file;

	@BeforeEach
	void setUp() {
		logger = new MobBehaviourLogger();
		terminal = Collections.synchronizedList(new ArrayList<>());
		file = Collections.synchronizedList(new ArrayList<>());
		logger.setTerminalSink(terminal::add);
		logger.setFileSink(file::add);
	}

	@Nested
	@DisplayName("Singleton")
	class SingletonTests {
		@Test
		@DisplayName("getInstance returns the same object across calls")
		void getInstance_returnsSameInstance() {
			assertSame(MobBehaviourLogger.getInstance(), MobBehaviourLogger.getInstance());
		}

		@Test
		@DisplayName("getInstance never returns null")
		void getInstance_isNonNull() {
			assertNotNull(MobBehaviourLogger.getInstance());
		}
	}

	@Nested
	@DisplayName("Enable / disable")
	class EnableDisableTests {

		@Test
		@DisplayName("logger is enabled by default")
		void enabledByDefault() {
			assertTrue(logger.isEnabled());
		}

		@Test
		@DisplayName("isEnabled reflects setEnabled in both directions")
		void isEnabled_reflectsSetter() {
			logger.setEnabled(false);
			assertFalse(logger.isEnabled());
			logger.setEnabled(true);
			assertTrue(logger.isEnabled());
		}

		@Test
		@DisplayName("setEnabled(false) stops events from being buffered")
		void disabled_doesNotBuffer() {
			logger.setEnabled(false);
			logger.logSpawn(1, "Rat", 10);
			assertEquals(0, logger.size());
		}

		@Test
		@DisplayName("setEnabled(false) stops events from reaching sinks")
		void disabled_doesNotEmit() {
			logger.setEnabled(false);
			logger.logSpawn(1, "Rat", 10);
			assertTrue(terminal.isEmpty());
			assertTrue(file.isEmpty());
		}

		@Test
		@DisplayName("re-enabling resumes recording without losing prior buffered events")
		void reenable_keepsExistingEntries() {
			logger.logSpawn(1, "Rat", 10);
			logger.setEnabled(false);
			logger.logSpawn(2, "Rat", 11); // suppressed
			logger.setEnabled(true);
			logger.logSpawn(3, "Rat", 12);
			assertEquals(2, logger.size());
		}
	}

	@Nested
	@DisplayName("Event recording")
	class EventRecordingTests {

		@Test
		@DisplayName("logSpawn produces a MOB_SPAWN entry with correct fields")
		void logSpawn_recordsCorrectEntry() {
			logger.logSpawn(7, "Rat", 42);
			MobBehaviourLogger.Entry e = onlyEntry();
			assertEquals(MobBehaviourLogger.EVENT_SPAWN, e.eventType);
			assertEquals(7, e.mobId);
			assertEquals("Rat", e.mobName);
			assertEquals(42, e.pos);
			assertTrue(e.details.contains("added"));
		}

		@Test
		@DisplayName("logStateChange records from->to in details")
		void logStateChange_recordsTransition() {
			logger.logStateChange(7, "Rat", 42, "Sleeping", "Hunting");
			MobBehaviourLogger.Entry e = onlyEntry();
			assertEquals(MobBehaviourLogger.EVENT_STATE_CHANGE, e.eventType);
			assertTrue(e.details.contains("Sleeping -> Hunting"));
		}

		@Test
		@DisplayName("logAlert records the trigger reason")
		void logAlert_recordsTrigger() {
			logger.logAlert(7, "Rat", 42, "damage taken");
			MobBehaviourLogger.Entry e = onlyEntry();
			assertEquals(MobBehaviourLogger.EVENT_ALERT, e.eventType);
			assertTrue(e.details.contains("trigger=damage taken"));
		}

		@Test
		@DisplayName("logTarget records target name and id")
		void logTarget_recordsTargetDetails() {
			logger.logTarget(7, "Rat", 42, "Hero", 99);
			MobBehaviourLogger.Entry e = onlyEntry();
			assertEquals(MobBehaviourLogger.EVENT_TARGET, e.eventType);
			assertTrue(e.details.contains("Hero(99)"));
		}

		@Test
		@DisplayName("timestampMs is set to roughly now")
		void timestamp_isCurrent() {
			long before = System.currentTimeMillis();
			logger.logSpawn(1, "Rat", 10);
			long after = System.currentTimeMillis();
			MobBehaviourLogger.Entry e = onlyEntry();
			assertTrue(e.timestampMs >= before && e.timestampMs <= after,
					"timestamp " + e.timestampMs + " not in [" + before + ", " + after + "]");
		}

		@Test
		@DisplayName("Entry.toString contains every field")
		void entryToString_isInformative() {
			logger.logSpawn(7, "Rat", 42);
			String s = onlyEntry().toString();
			assertTrue(s.contains("MOB_SPAWN"));
			assertTrue(s.contains("7"));
			assertTrue(s.contains("Rat"));
			assertTrue(s.contains("42"));
		}
	}

	@Nested
	@DisplayName("Line format")
	class FormatTests {

		@Test
		@DisplayName("formatted line matches the documented pattern")
		void formattedLine_matchesPattern() {
			logger.logSpawn(7, "Rat", 42);
			assertEquals(1, terminal.size());
			String line = terminal.get(0);
			assertTrue(LINE_PATTERN.matcher(line).matches(),
					"line did not match: " + line);
		}

		@Test
		@DisplayName("state change line includes 'from -> to'")
		void stateChangeLine_includesArrow() {
			logger.logStateChange(7, "Rat", 42, "Sleeping", "Hunting");
			String line = terminal.get(0);
			assertTrue(line.contains("Sleeping -> Hunting"));
		}

		@Test
		@DisplayName("terminal and file sinks receive identical lines")
		void sinksReceiveIdenticalLines() {
			logger.logSpawn(7, "Rat", 42);
			assertEquals(terminal, file);
		}
	}

	@Nested
	@DisplayName("Buffer bounds")
	class BufferBoundsTests {

		@Test
		@DisplayName("buffer starts empty")
		void newLogger_isEmpty() {
			assertEquals(0, logger.size());
			assertTrue(logger.getEntries().isEmpty());
		}

		@Test
		@DisplayName("appending below cap grows linearly")
		void belowCap_growsLinearly() {
			logger.setMaxEntries(5);
			for (int i = 0; i < 3; i++) logger.logSpawn(i, "Rat", i);
			assertEquals(3, logger.size());
		}

		@Test
		@DisplayName("appending above cap evicts oldest in FIFO order")
		void overCap_evictsOldest() {
			logger.setMaxEntries(3);
			for (int i = 1; i <= 5; i++) logger.logSpawn(i, "Rat", i);
			List<MobBehaviourLogger.Entry> es = logger.getEntries();
			assertEquals(3, es.size());
			assertEquals(3, es.get(0).mobId);
			assertEquals(4, es.get(1).mobId);
			assertEquals(5, es.get(2).mobId);
		}

		@Test
		@DisplayName("setMaxEntries with a smaller value trims existing entries")
		void shrinkingCap_trimsExisting() {
			logger.setMaxEntries(10);
			for (int i = 1; i <= 8; i++) logger.logSpawn(i, "Rat", i);
			logger.setMaxEntries(3);
			assertEquals(3, logger.size());
			assertEquals(6, logger.getEntries().get(0).mobId);
		}

		@Test
		@DisplayName("setMaxEntries with 0 clamps to 1")
		void zeroCap_clampsToOne() {
			logger.setMaxEntries(0);
			assertEquals(1, logger.getMaxEntries());
		}

		@Test
		@DisplayName("setMaxEntries with negative clamps to 1")
		void negativeCap_clampsToOne() {
			logger.setMaxEntries(-5);
			assertEquals(1, logger.getMaxEntries());
		}

		@Test
		@DisplayName("clear() empties the buffer")
		void clear_emptiesBuffer() {
			logger.logSpawn(1, "Rat", 10);
			logger.logSpawn(2, "Rat", 11);
			logger.clear();
			assertEquals(0, logger.size());
		}

		@Test
		@DisplayName("getEntries returns a defensive copy")
		void getEntries_isDefensiveCopy() {
			logger.logSpawn(1, "Rat", 10);
			List<MobBehaviourLogger.Entry> copy = logger.getEntries();
			copy.clear();
			assertEquals(1, logger.size(), "internal buffer was mutated via getEntries()");
		}
	}

	@Nested
	@DisplayName("Sinks")
	class SinkTests {

		@Test
		@DisplayName("each event calls the terminal sink exactly once")
		void terminalSink_calledOncePerEvent() {
			logger.logSpawn(1, "Rat", 10);
			logger.logSpawn(2, "Rat", 11);
			assertEquals(2, terminal.size());
		}

		@Test
		@DisplayName("each event calls the file sink exactly once")
		void fileSink_calledOncePerEvent() {
			logger.logSpawn(1, "Rat", 10);
			logger.logSpawn(2, "Rat", 11);
			assertEquals(2, file.size());
		}

		@Test
		@DisplayName("a throwing file sink does not break logging or buffer append")
		void fileSinkThrows_doesNotBreakLogger() {
			logger.setFileSink(s -> { throw new RuntimeException("disk full"); });
			assertDoesNotThrow(() -> logger.logSpawn(1, "Rat", 10));
			assertEquals(1, logger.size());
			assertEquals(1, terminal.size(), "terminal sink should still receive the event");
		}

		@Test
		@DisplayName("a throwing terminal sink does not break logging")
		void terminalSinkThrows_doesNotBreakLogger() {
			logger.setTerminalSink(s -> { throw new RuntimeException("stdout closed"); });
			assertDoesNotThrow(() -> logger.logSpawn(1, "Rat", 10));
			assertEquals(1, logger.size());
			assertEquals(1, file.size(), "file sink should still receive the event");
		}

		@Test
		@DisplayName("a null sink does not throw")
		void nullSink_doesNotThrow() {
			logger.setTerminalSink(null);
			logger.setFileSink(null);
			assertDoesNotThrow(() -> logger.logSpawn(1, "Rat", 10));
			assertEquals(1, logger.size(), "events still buffered when sinks are null");
		}

		@Test
		@DisplayName("swapping the sink redirects subsequent output")
		void swappingSink_redirects() {
			List<String> alt = new ArrayList<>();
			logger.logSpawn(1, "Rat", 10);
			logger.setTerminalSink(alt::add);
			logger.logSpawn(2, "Rat", 11);
			assertEquals(1, terminal.size());
			assertEquals(1, alt.size());
		}
	}

	@Nested
	@DisplayName("Null safety")
	class NullSafetyTests {

		@Test
		@DisplayName("null mob name is rendered as <null>, no NPE")
		void nullMobName_isHandled() {
			assertDoesNotThrow(() -> logger.logSpawn(1, null, 10));
			assertTrue(terminal.get(0).contains("<null>"));
		}

		@Test
		@DisplayName("null target name is rendered as <null>, no NPE")
		void nullTargetName_isHandled() {
			assertDoesNotThrow(() -> logger.logTarget(1, "Rat", 10, null, -1));
			assertTrue(terminal.get(0).contains("<null>(-1)"));
		}

		@Test
		@DisplayName("null state names render gracefully")
		void nullStateNames_areHandled() {
			assertDoesNotThrow(() -> logger.logStateChange(1, "Rat", 10, null, null));
			assertTrue(terminal.get(0).contains("<null> -> <null>"));
		}

		@Test
		@DisplayName("negative ids and positions are accepted")
		void negativeIdsAccepted() {
			logger.logSpawn(-1, "Ghost", -7);
			MobBehaviourLogger.Entry e = onlyEntry();
			assertEquals(-1, e.mobId);
			assertEquals(-7, e.pos);
		}
	}

	@Nested
	@DisplayName("Concurrency")
	class ConcurrencyTests {

		@Test
		@DisplayName("concurrent writers produce a consistent buffer state")
		void concurrentWrites_areThreadSafe() throws Exception {
			int threads = 4;
			int perThread = 250;
			logger.setMaxEntries(threads * perThread);
			ExecutorService exec = Executors.newFixedThreadPool(threads);
			CountDownLatch start = new CountDownLatch(1);
			AtomicInteger id = new AtomicInteger(0);
			try {
				for (int t = 0; t < threads; t++) {
					exec.submit(() -> {
						start.await();
						for (int i = 0; i < perThread; i++) {
							logger.logSpawn(id.incrementAndGet(), "Rat", 0);
						}
						return null;
					});
				}
				start.countDown();
				exec.shutdown();
				assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
			} finally {
				exec.shutdownNow();
			}
			assertEquals(threads * perThread, logger.size());
			assertEquals(threads * perThread, terminal.size());
			assertEquals(threads * perThread, file.size());
		}
	}

	@Nested
	@DisplayName("Smoke (end-to-end)")
	class SmokeTests {

		@Test
		@DisplayName("a scripted gameplay sequence is fully captured in order")
		void scriptedSequence_isCapturedInOrder() {
			StringBuilder captured = new StringBuilder();
			Consumer<String> sink = s -> captured.append(s).append('\n');
			logger.setTerminalSink(sink);
			logger.setFileSink(sink);

			logger.logSpawn(1, "Rat", 100);
			logger.logStateChange(1, "Rat", 100, "Sleeping", "Wandering");
			logger.logStateChange(1, "Rat", 101, "Wandering", "Hunting");
			logger.logAlert(1, "Rat", 101, "noticeEnemy");
			logger.logTarget(1, "Rat", 101, "Hero", 99);
			logger.logStateChange(1, "Rat", 101, "Hunting", "Fleeing");

			String out = captured.toString();
			assertTrue(out.contains("MOB_SPAWN"));
			assertTrue(out.contains("Sleeping -> Wandering"));
			assertTrue(out.contains("Wandering -> Hunting"));
			assertTrue(out.contains("ALERT"));
			assertTrue(out.contains("TARGET_ASSIGNED"));
			assertTrue(out.contains("Hunting -> Fleeing"));

			int posSpawn = out.indexOf("MOB_SPAWN");
			int posTarget = out.indexOf("TARGET_ASSIGNED");
			int posFlee = out.indexOf("Hunting -> Fleeing");
			assertTrue(posSpawn < posTarget && posTarget < posFlee,
					"events not in expected order");

			List<MobBehaviourLogger.Entry> es = logger.getEntries();
			for (int i = 1; i < es.size(); i++) {
				assertTrue(es.get(i - 1).timestampMs <= es.get(i).timestampMs,
						"timestamps not monotonic at index " + i);
			}
		}
	}

	private MobBehaviourLogger.Entry onlyEntry() {
		List<MobBehaviourLogger.Entry> es = logger.getEntries();
		assertFalse(es.isEmpty(), "logger captured no entries");
		assertEquals(1, es.size(), "logger captured more than one entry");
		return es.get(0);
	}
}
