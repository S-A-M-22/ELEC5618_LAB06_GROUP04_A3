package com.shatteredpixel.shatteredpixeldungeon.actors.mobs;

import com.badlogic.gdx.files.FileHandle;
import com.watabou.utils.FileUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Records mob behaviour events for the Mob Behaviour Logging quality improvement
 * (ELEC5618 Assignment 3, predefined item 5).
 *
 * Captures four event types required by the assignment:
 *  - MOB_SPAWN       when a mob is first added to a level
 *  - STATE_CHANGE    every time the mob's AI state changes
 *  - ALERT           when the mob becomes alerted
 *  - TARGET_ASSIGNED when the mob sets or changes its target
 *
 * Each event is recorded with a millisecond timestamp and the mob's unique id.
 * Events are pushed to both a configurable terminal sink (System.out by default)
 * and a configurable file sink (libGDX FileHandle by default), and also held in
 * a bounded in-memory ring buffer for inspection and tests.
 *
 * The logger is intentionally observational: it never mutates game state, and
 * all sink failures are swallowed so that a broken file system cannot disrupt
 * gameplay.
 */
public final class MobBehaviourLogger {

	public static final int DEFAULT_MAX_ENTRIES = 1000;
	public static final String DEFAULT_LOG_FILE = "logs/mob_behaviour.log";

	public static final String EVENT_SPAWN        = "MOB_SPAWN";
	public static final String EVENT_STATE_CHANGE = "STATE_CHANGE";
	public static final String EVENT_ALERT        = "ALERT";
	public static final String EVENT_TARGET       = "TARGET_ASSIGNED";

	private static final MobBehaviourLogger INSTANCE = new MobBehaviourLogger();

	public static MobBehaviourLogger getInstance() {
		return INSTANCE;
	}

	private boolean enabled = true;
	private int maxEntries = DEFAULT_MAX_ENTRIES;
	private final LinkedList<Entry> buffer = new LinkedList<>();

	private final SimpleDateFormat timestampFormat =
			new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

	private Consumer<String> terminalSink = System.out::println;
	private Consumer<String> fileSink = MobBehaviourLogger::defaultFileSink;

	MobBehaviourLogger() {
		// package-private for tests; production code uses getInstance()
	}

	public synchronized void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public synchronized boolean isEnabled() {
		return enabled;
	}

	public synchronized void setMaxEntries(int n) {
		this.maxEntries = Math.max(1, n);
		while (buffer.size() > maxEntries) {
			buffer.removeFirst();
		}
	}

	public synchronized int getMaxEntries() {
		return maxEntries;
	}

	public synchronized void clear() {
		buffer.clear();
	}

	public synchronized List<Entry> getEntries() {
		return new ArrayList<>(buffer);
	}

	public synchronized int size() {
		return buffer.size();
	}

	public synchronized void setTerminalSink(Consumer<String> sink) {
		this.terminalSink = sink;
	}

	public synchronized void setFileSink(Consumer<String> sink) {
		this.fileSink = sink;
	}

	// ----- public event API -----

	public void logSpawn(int mobId, String mobName, int pos) {
		if (!enabled) return;
		record(EVENT_SPAWN, mobId, mobName, pos, "mob added to level");
	}

	public void logStateChange(int mobId, String mobName, int pos, String from, String to) {
		if (!enabled) return;
		record(EVENT_STATE_CHANGE, mobId, mobName, pos, safe(from) + " -> " + safe(to));
	}

	public void logAlert(int mobId, String mobName, int pos, String trigger) {
		if (!enabled) return;
		record(EVENT_ALERT, mobId, mobName, pos, "trigger=" + safe(trigger));
	}

	public void logTarget(int mobId, String mobName, int pos, String targetName, int targetId) {
		if (!enabled) return;
		record(EVENT_TARGET, mobId, mobName, pos,
				"target=" + safe(targetName) + "(" + targetId + ")");
	}

	// ----- internals -----

	private void record(String eventType, int mobId, String mobName, int pos, String details) {
		Entry entry = new Entry(
				System.currentTimeMillis(), eventType, mobId, safe(mobName), pos, details);

		Consumer<String> term;
		Consumer<String> file;
		String line;
		synchronized (this) {
			buffer.addLast(entry);
			while (buffer.size() > maxEntries) {
				buffer.removeFirst();
			}
			line = format(entry);
			term = terminalSink;
			file = fileSink;
		}

		// Sinks called outside the monitor so a slow/throwing sink can't stall others.
		dispatch(term, line);
		dispatch(file, line);
	}

	private static void dispatch(Consumer<String> sink, String line) {
		if (sink == null) return;
		try {
			sink.accept(line);
		} catch (Throwable ignored) {
			// A broken sink must never break gameplay.
		}
	}

	private String format(Entry e) {
		String ts;
		synchronized (timestampFormat) {
			ts = timestampFormat.format(new Date(e.timestampMs));
		}
		return String.format(Locale.ROOT,
				"[%s] [%s] mob#%d(%s)@%d : %s",
				ts, e.eventType, e.mobId, e.mobName, e.pos, e.details);
	}

	private static String safe(String s) {
		return s == null ? "<null>" : s;
	}

	private static void defaultFileSink(String line) {
		FileHandle handle = FileUtils.getFileHandle(DEFAULT_LOG_FILE);
		handle.writeString(line + System.lineSeparator(), true);
	}

	// ----- data class -----

	/**
	 * Immutable record of a single logged event. Public fields are final;
	 * a single defensive accessor (toString) renders the entry without
	 * exposing internal state.
	 */
	public static final class Entry {
		public final long timestampMs;
		public final String eventType;
		public final int mobId;
		public final String mobName;
		public final int pos;
		public final String details;

		public Entry(long timestampMs, String eventType, int mobId,
		             String mobName, int pos, String details) {
			this.timestampMs = timestampMs;
			this.eventType = eventType;
			this.mobId = mobId;
			this.mobName = mobName;
			this.pos = pos;
			this.details = details;
		}

		@Override
		public String toString() {
			return "Entry{" +
					"ts=" + timestampMs +
					", event=" + eventType +
					", mobId=" + mobId +
					", mob=" + mobName +
					", pos=" + pos +
					", details=" + details +
					'}';
		}
	}
}
