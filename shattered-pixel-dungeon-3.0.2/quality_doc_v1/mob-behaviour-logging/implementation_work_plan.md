# Mob Behaviour Logging - Detailed Implementation Workplan


## 1. Overview

The **Mob Behaviour Logging** improvement introduces a structured logging subsystem into the `Mob` class hierarchy of Shattered Pixel Dungeon. The objective is to record and trace AI state transitions (SLEEPING, WANDERING, HUNTING, FLEEING, PASSIVE), combat actions (attack, damage, death), movement decisions, and enemy-selection logic for every mob instance. This data supports debugging mob AI, enables analytics on game balance, and provides a foundation for automated testing of mob behaviour correctness.

## 2. Scope of Changes

### 2.1 Primary Files to Modify

| File | Purpose |
|------|---------|
| `Mob.java` | Core mob class; houses AI state machine, combat methods, movement logic |
| `GLog.java` | Game logging utility; extend with structured log levels for behaviour events |

### 2.2 New Files to Create

| File | Purpose |
|------|---------|
| `MobBehaviourLogger.java` | Dedicated logger class encapsulating all mob behaviour event recording |
| `MobBehaviourLogEntry.java` | Data class representing a single log entry (timestamp, mob ID, event type, details) |
| `MobBehaviourLoggerTest.java` | JUnit test suite for the logging subsystem |

---

## 3. Step-by-Step Implementation Plan

### Step 1: Define the Log Entry Data Model

**File:** `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MobBehaviourLogEntry.java`

Create a plain data class that captures:
- `long timestamp` - game turn or system time
- `int mobId` - unique mob instance identifier (from `Actor.id()`)
- `String mobClass` - simple class name (e.g., "Rat", "Skeleton")
- `String eventType` - enumerated string: `STATE_CHANGE`, `ATTACK`, `DAMAGE_RECEIVED`, `DEATH`, `MOVEMENT`, `ENEMY_SELECTED`, `TARGET_LOST`, `ALERTED`, `BECKON`
- `String fromState` - previous AI state (for state transitions)
- `String toState` - new AI state (for state transitions)
- `String details` - free-form context (e.g., "enemy=Hero, distance=3")
- `int position` - cell position on the dungeon map

This class should implement `toString()` for human-readable output and optionally `Bundlable` for save/restore.

### Step 2: Create the MobBehaviourLogger Singleton

**File:** `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MobBehaviourLogger.java`

Design a singleton logger:

```java
public class MobBehaviourLogger {
    private static MobBehaviourLogger instance;
    private boolean enabled = false;
    private ArrayList<MobBehaviourLogEntry> logBuffer;
    private int maxBufferSize = 1000;

    public static MobBehaviourLogger getInstance();
    public void setEnabled(boolean enabled);
    public boolean isEnabled();

    // Core logging methods
    public void logStateChange(Mob mob, String fromState, String toState, String reason);
    public void logAttack(Mob mob, Char target, int damage, boolean hit);
    public void logDamageReceived(Mob mob, int damage, Object source);
    public void logDeath(Mob mob, Object cause);
    public void logMovement(Mob mob, int fromPos, int toPos, String state);
    public void logEnemySelected(Mob mob, Char enemy, String reason);
    public void logAlert(Mob mob, String trigger);
    public void logBeckon(Mob mob, int cell);

    // Retrieval and management
    public List<MobBehaviourLogEntry> getLog();
    public List<MobBehaviourLogEntry> getLogForMob(int mobId);
    public List<MobBehaviourLogEntry> getLogByEventType(String eventType);
    public void clearLog();
    public String exportLog();  // formatted text dump
}
```

Key design decisions:
- Circular buffer with configurable `maxBufferSize` to prevent memory leaks during long game sessions.
- Toggle via `setEnabled()` so logging can be turned on/off at runtime (e.g., from a debug settings menu).
- Thread-safe via synchronized access to the buffer (the game is single-threaded but future-proofing is good practice).

### Step 3: Instrument AI State Transitions in Mob.java

Insert logging calls at every point where `state` is reassigned. The existing state machine in `Mob.java` has these transition points:

| Location | Transition | 
|----------|-----------|
| `Sleeping.awaken()` | SLEEPING -> HUNTING or WANDERING |
| `Wandering.noticeEnemy()` | WANDERING -> HUNTING |
| `Hunting.act()` (enemy null) | HUNTING -> WANDERING |
| `Hunting.act()` (can't reach) | HUNTING -> WANDERING |
| `Fleeing.nowhereToRun()` | FLEEING -> HUNTING or WANDERING |
| `Fleeing.escaped()` | FLEEING -> (subclass specific) |
| `add(Buff)` | Various -> HUNTING/FLEEING/SLEEPING |
| `remove(Buff)` | FLEEING -> HUNTING/WANDERING |
| `damage()` | SLEEPING -> WANDERING |
| `aggro()` | Any (non-PASSIVE) -> HUNTING |
| `clearEnemy()` | HUNTING -> WANDERING |

**Implementation pattern** at each transition site:

```java
// Before state change:
String oldState = state.getClass().getSimpleName();
state = HUNTING;
MobBehaviourLogger.getInstance().logStateChange(
    this, oldState, "HUNTING", "enemy spotted at distance " + distance(enemy));
```

### Step 4: Instrument Combat Methods

**`doAttack(Char enemy)`:**
- Log when a mob initiates an attack, including the target and whether the sprite is visible.

**`onAttackComplete()`:**
- Log the result of `attack(enemy)` (hit/miss, damage dealt). This requires capturing the return value.

**`damage(int dmg, Object src)`:**
- Log incoming damage amount, source type, and resulting state change (if any).

**`die(Object cause)`:**
- Log death event including cause, final position, and loot drop status.

**`defenseProc(Char enemy, int damage)`:**
- Log surprise attacks, target switches during combat.

### Step 5: Instrument Enemy Selection Logic

**`chooseEnemy()`:**
- Log the final selected enemy and the reason for selection (dread, terror, amok, aggression, closest, etc.).
- Log when no enemy is found (returns null).

**`aggro(Char ch)`:**
- Log forced target changes.

### Step 6: Instrument Movement and Pathfinding

**`getCloser(int target)`:**
- Log movement from old position to new position.
- Log path recalculations.

**`getFurther(int target)`:**
- Log flee movements.

**`beckon(int cell)`:**
- Log when a mob is beckoned by the swarm intelligence challenge or other mechanics.

### Step 7: Add Runtime Toggle to Settings

Add a boolean preference `mobBehaviourLogging` to `SPDSettings.java` or the debug options. Wire it to `MobBehaviourLogger.getInstance().setEnabled(...)` during game initialization in `ShatteredPixelDungeon.java`.

### Step 8: Add Log Viewer (Optional UI Enhancement)

Create a simple scrollable text window accessible from the developer/debug menu that displays the last N log entries, with filters for mob type and event type.

### Step 9: Integration with Existing GLog

Optionally integrate high-priority behaviour events (state changes during combat visible to the player) into `GLog` using the `GLog.i()` method, so they appear in the in-game message log when debug mode is active.

### Step 10: Testing

Write comprehensive JUnit tests (see separate test file) covering:
- Logger enable/disable toggling
- All log entry creation paths
- Buffer overflow and circular buffer behaviour
- State transition correctness via FSM analysis
- Export and retrieval filtering

---

## 4. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Performance overhead from logging | Medium | Disable by default; use circular buffer; avoid string concatenation when disabled |
| Memory leak from unbounded log | High | Fixed-size circular buffer with configurable max |
| Breaking existing mob behaviour | High | All logging is observational (read-only); no game state mutation |
| Thread safety issues | Low | Game is single-threaded; add synchronized as precaution |

---

## 5. Acceptance Criteria

1. Every AI state transition in every mob type is logged with from/to state, reason, and timestamp.
2. All combat events (attack initiated, damage received, death) are captured with full context.
3. The logger can be toggled on/off at runtime without affecting gameplay.
4. The circular buffer correctly evicts oldest entries when full.
5. Log entries can be filtered by mob ID and event type.
6. All unit tests pass with greater than 90% branch coverage on the logging subsystem.
7. Zero regression in existing mob behaviour (logging is purely observational).
