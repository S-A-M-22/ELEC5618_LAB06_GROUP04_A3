package com.shatteredpixel.shatteredpixeldungeon.actors.mobs;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests that drive the instrumentation in Mob.java through the
 * package-private helpers and verify the four required events are recorded
 * by MobBehaviourLogger.
 *
 * Tests construct lightweight TestMob subclasses that override the abstract
 * combat methods but bypass sprite/Dungeon/Level dependencies. We hit the
 * helpers directly rather than the full AI act loop because the act loop
 * needs a populated Dungeon.level — and that needs libGDX bootstrap, which
 * is out of scope for unit tests.
 */
class MobBehaviourLoggerIntegrationTest {

	private MobBehaviourLogger logger;

	@BeforeEach
	void resetLoggerToSilentDefaults() {
		logger = MobBehaviourLogger.getInstance();
		logger.clear();
		logger.setEnabled(true);
		// Suppress the default System.out / FileUtils sinks so tests don't
		// spam stdout or touch the file system.
		logger.setTerminalSink(s -> {});
		logger.setFileSink(s -> {});
	}

	@AfterEach
	void clearLogger() {
		logger.clear();
		logger.setEnabled(true);
		logger.setTerminalSink(s -> {});
		logger.setFileSink(s -> {});
	}

	// ---------- spawn ----------

	@Test
	@DisplayName("onAdd fires MOB_SPAWN exactly once on first add")
	void onAdd_firesSpawnOnce() {
		TestMob mob = new TestMob();
		mob.onAdd();
		List<MobBehaviourLogger.Entry> entries = byType(MobBehaviourLogger.EVENT_SPAWN);
		assertEquals(1, entries.size());
		assertEquals("TestMob", entries.get(0).mobName);
	}

	@Test
	@DisplayName("onAdd called twice still only fires MOB_SPAWN once")
	void onAdd_calledTwice_firesOnceOnly() {
		TestMob mob = new TestMob();
		mob.onAdd();
		mob.onAdd();
		assertEquals(1, byType(MobBehaviourLogger.EVENT_SPAWN).size());
	}

	@Test
	@DisplayName("onAdd after firstAdded=false (simulated save-restore) does NOT fire spawn")
	void onAdd_afterRestore_doesNotFireSpawn() {
		TestMob mob = new TestMob();
		// Mimics what restoreFromBundle does at the end:
		mob.firstAdded = false;
		mob.onAdd();
		assertTrue(byType(MobBehaviourLogger.EVENT_SPAWN).isEmpty());
	}

	// ---------- state change ----------

	@Test
	@DisplayName("setState to a different state fires STATE_CHANGE")
	void setState_differentState_firesStateChange() {
		TestMob mob = new TestMob();
		mob.setState(mob.HUNTING, "test trigger");
		List<MobBehaviourLogger.Entry> entries = byType(MobBehaviourLogger.EVENT_STATE_CHANGE);
		assertEquals(1, entries.size());
		assertTrue(entries.get(0).details.contains("Sleeping -> Hunting"));
	}

	@Test
	@DisplayName("setState to the same state is a no-op (does not fire STATE_CHANGE)")
	void setState_sameState_isNoOp() {
		TestMob mob = new TestMob();
		mob.setState(mob.SLEEPING, "test trigger"); // already SLEEPING
		assertTrue(byType(MobBehaviourLogger.EVENT_STATE_CHANGE).isEmpty());
	}

	@Test
	@DisplayName("aggro from non-PASSIVE state fires both TARGET_ASSIGNED and STATE_CHANGE")
	void aggro_firesBothTargetAndState() {
		TestMob mob = new TestMob();
		TestMob target = new TestMob();
		mob.aggro(target);
		assertEquals(1, byType(MobBehaviourLogger.EVENT_TARGET).size());
		assertEquals(1, byType(MobBehaviourLogger.EVENT_STATE_CHANGE).size());
	}

	// ---------- target ----------

	@Test
	@DisplayName("setEnemy with a new char fires TARGET_ASSIGNED")
	void setEnemy_newTarget_fires() {
		TestMob mob = new TestMob();
		TestMob target = new TestMob();
		mob.setEnemy(target, "test");
		List<MobBehaviourLogger.Entry> entries = byType(MobBehaviourLogger.EVENT_TARGET);
		assertEquals(1, entries.size());
		assertTrue(entries.get(0).details.contains("TestMob"));
	}

	@Test
	@DisplayName("setEnemy with the same char twice fires TARGET_ASSIGNED only once")
	void setEnemy_sameTarget_firesOnce() {
		TestMob mob = new TestMob();
		TestMob target = new TestMob();
		mob.setEnemy(target, "first");
		mob.setEnemy(target, "second");
		assertEquals(1, byType(MobBehaviourLogger.EVENT_TARGET).size());
	}

	@Test
	@DisplayName("setEnemy(null) from non-null fires TARGET_ASSIGNED with <none>")
	void setEnemy_null_firesWithNonePlaceholder() {
		TestMob mob = new TestMob();
		TestMob target = new TestMob();
		mob.setEnemy(target, "first");
		logger.clear();
		mob.setEnemy(null, "clear");
		List<MobBehaviourLogger.Entry> entries = byType(MobBehaviourLogger.EVENT_TARGET);
		assertEquals(1, entries.size());
		assertTrue(entries.get(0).details.contains("<none>"));
		assertTrue(entries.get(0).details.contains("(-1)"));
	}

	@Test
	@DisplayName("clearEnemy after aggro fires TARGET (null) and STATE (WANDERING)")
	void clearEnemy_afterAggro_firesBothEvents() {
		TestMob mob = new TestMob();
		TestMob target = new TestMob();
		mob.aggro(target);
		logger.clear();
		mob.clearEnemy();
		assertEquals(1, byType(MobBehaviourLogger.EVENT_TARGET).size());
		assertEquals(1, byType(MobBehaviourLogger.EVENT_STATE_CHANGE).size());
	}

	// ---------- alert ----------

	@Test
	@DisplayName("markAlerted on a non-alerted mob fires ALERT")
	void markAlerted_firesAlert() {
		TestMob mob = new TestMob();
		mob.markAlerted("test trigger");
		List<MobBehaviourLogger.Entry> entries = byType(MobBehaviourLogger.EVENT_ALERT);
		assertEquals(1, entries.size());
		assertTrue(entries.get(0).details.contains("test trigger"));
	}

	@Test
	@DisplayName("markAlerted on an already-alerted mob is a no-op")
	void markAlerted_alreadyAlerted_isNoOp() {
		TestMob mob = new TestMob();
		mob.markAlerted("first");
		mob.markAlerted("second");
		assertEquals(1, byType(MobBehaviourLogger.EVENT_ALERT).size());
	}

	// ---------- disabled ----------

	@Test
	@DisplayName("when logger is disabled, instrumentation produces no events")
	void disabledLogger_silencesAllEvents() {
		logger.setEnabled(false);
		TestMob mob = new TestMob();
		TestMob other = new TestMob();
		mob.onAdd();
		mob.aggro(other);
		mob.markAlerted("test");
		mob.clearEnemy();
		assertEquals(0, logger.size());
	}

	// ---------- helpers ----------

	private List<MobBehaviourLogger.Entry> byType(String type) {
		return logger.getEntries().stream()
				.filter(e -> e.eventType.equals(type))
				.collect(java.util.stream.Collectors.toList());
	}

	/**
	 * Minimal concrete Mob that avoids sprite/Dungeon/Level dependencies.
	 * State is initialised by Mob's field initializer to SLEEPING.
	 */
	static class TestMob extends Mob {
		TestMob() {
			HP = HT = 10;
			EXP = 1;
			defenseSkill = 5;
		}
		@Override public int damageRoll() { return 1; }
		@Override public int attackSkill(Char target) { return 1; }
		@Override public String name() { return "TestMob"; }
	}
}
