# ELEC5618 Assignment 3 — Software Quality Improvements
## Shattered Pixel Dungeon v3.0.2

> **Course:** ELEC5618 — Software Quality Engineering  
> **Base Project:** [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) v3.0.2  
> **Platform:** Android + Desktop (LibGDX / Java)  
> **Build System:** Gradle  

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Repository Structure](#repository-structure)
3. [Quality Framework](#quality-framework)
4. [Improvement 1 — Mob Behaviour Logging](#improvement-1--mob-behaviour-logging)
5. [Improvement 2 — Recovery Saves](#improvement-2--recovery-saves)
6. [Improvement 3 — Save Integrity Checksum](#improvement-3--save-integrity-checksum)
7. [SQA Process](#sqa-process)
8. [Test Results & Coverage](#test-results--coverage)
9. [Cyclomatic Complexity Analysis](#cyclomatic-complexity-analysis)
10. [Cross-Feature Integration](#cross-feature-integration)
11. [How to Build & Run](#how-to-build--run)
12. [How to Run Tests](#how-to-run-tests)
13. [Gradle Tasks Reference](#gradle-tasks-reference)

---

## Project Overview

This repository applies three software quality improvements to Shattered Pixel Dungeon v3.0.2, a Java/LibGDX roguelike originally developed by Evan Debenham. Each improvement targets a distinct ISO/IEC 25010 quality characteristic and was developed on its own feature branch, reviewed by a teammate, and merged only after all tests passed.

The three improvements are:

| # | Improvement | ISO/IEC 25010 Characteristic | Branch |
|---|-------------|------------------------------|--------|
| 1 | Mob Behaviour Logging | Maintainability / Testability | `feat/mob-behaviour-logging` |
| 2 | Recovery Saves | Reliability / Recoverability | `feat/recovery-saves` |
| 3 | Save Integrity Checksum | Security / Integrity | `feat/save-integrity-checksum` |

All three features were integrated and verified together on the `report/merge-test` branch.

---

## Repository Structure

```
ELEC5618-A3/
├── shattered-pixel-dungeon-3.0.2/
│   ├── core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/
│   │   ├── actors/mobs/
│   │   │   ├── MobBehaviourLogger.java          # Improvement 1: logging singleton
│   │   │   ├── Mob.java                          # patched: emits log events
│   │   │   └── ...
│   │   ├── utils/
│   │   │   ├── RecoveryManager.java              # Improvement 2: soft-delete recovery
│   │   │   └── SaveIntegrityManager.java         # Improvement 3: checksum manager
│   │   └── scenes/
│   │       └── WndRecoverySave.java              # Improvement 2: in-game recovery UI
│   ├── core/src/test/java/
│   │   ├── MobBehaviourLoggerTest.java
│   │   ├── RecoveryManagerTest.java
│   │   ├── SaveIntegrityManagerTest.java
│   │   └── MergedFeatureIntegrationTest.java     # cross-feature integration tests
│   └── doc/
│       ├── mob-behaviour-logging/
│       │   ├── implementation_work_plan.md
│       │   └── cc_analysis.md
│       ├── recovery-saves/
│       │   └── README.md
│       └── save-integrity-checksum/
│           └── quality-issue-latent-clean.md
├── FEATURE_COMPLEXITY_AND_TEST_REPORT.md         # full V(G) tables, FSMs, flow graphs
├── build_presentation.js                         # PptxGenJS script → .pptx slide deck
├── ELEC5618_A3_Presentation.html                 # print-to-PDF slide deck (browser)
└── README.md                                     # this file
```

---

## Quality Framework

All improvements are classified against **ISO/IEC 25010:2023** and measured with the following tools:

| Tool | Purpose |
|------|---------|
| **JaCoCo** | Line, branch, and instruction coverage |
| **PITest** | Mutation testing — killed vs. survived mutants |
| **SpotBugs** | Static analysis — correctness and threading bugs |
| **Checkstyle** | Code style enforcement |

Cyclomatic complexity V(G) was calculated before and after each change using the standard formula V(G) = E − N + 2P. The constraint was that ΔV(G) = 0 in all **existing** methods touched by each feature.

---

## Improvement 1 — Mob Behaviour Logging

### Quality Problem

Before this improvement, the AI decision-making inside `Mob.java` and its subclasses was entirely opaque at runtime. When a mob entered alert state, changed its AI, or acquired a new target, nothing was recorded. Reproducing and diagnosing AI bugs required attaching a debugger and manually stepping through game frames — impractical for testers and impossible in production builds.

**ISO/IEC 25010 mapping:** Maintainability → Analysability; Testability → Observability.

### Design

A singleton class `MobBehaviourLogger` acts as the central event bus. It maintains a fixed-capacity ring buffer of `LogEntry` objects (default capacity 1 000) so memory use is bounded regardless of session length. Two sinks are supported simultaneously — a human-readable terminal stream and a UTF-8 log file — and both can be toggled independently at runtime without restarting the game.

```
MobBehaviourLogger (singleton)
│
├── configure(capacity, sinks, filePath)
├── log(eventType, mobId, mobClass, depth, extra)        ← called from Mob.java patches
├── getEntries() / getEntries(limit)                     ← for test assertions
├── clear()
└── setEnabled(boolean)
```

Four event types are defined as string constants on the logger:

| Constant | Fires when… |
|----------|-------------|
| `EVENT_SPAWN` | a mob is added to the dungeon level |
| `EVENT_STATE_CHANGE` | `Mob.setState()` is called with a new AI state |
| `EVENT_ALERT` | `Mob.alerted()` is invoked |
| `EVENT_TARGET` | `Mob.target` is assigned a non-null hero reference |

### Key Implementation Decisions

**Zero ΔV(G) in existing methods.** Every call site in `Mob.java` was reduced to a single-line guard:
```java
MobBehaviourLogger.getInstance().log(EVENT_STATE_CHANGE, id, getClass().getSimpleName(), depth, newState.toString());
```
No branching was added to existing methods. All conditional logic lives inside `MobBehaviourLogger.log()`, which is new code.

**Thread safety.** `log()` and `getEntries()` are both `synchronized`. The dungeon simulation runs on the LibGDX render thread, so contention is low, but correctness under concurrent test execution required it.

**Ring buffer eviction.** When the buffer is full, the oldest entry is silently dropped. `getEntries(limit)` returns the most recent *limit* entries in insertion order, making it easy to assert "the last N events match expectations" in tests.

### Files Changed

| File | Change |
|------|--------|
| `actors/mobs/MobBehaviourLogger.java` | **New** — full singleton implementation |
| `actors/mobs/Mob.java` | 4 one-line log calls added; ΔV(G) = 0 |

### Acceptance Criteria (all met)

- Logger is disabled by default; no output is produced unless explicitly enabled.
- Enabling/disabling the logger at runtime requires no restart.
- Events are emitted in the correct order for a spawn → alert → state change → target sequence.
- Memory is bounded: enabling the logger for 10 000 mob events does not grow the heap beyond the configured buffer.
- All existing unit tests pass unchanged.

### Merge Commit

`7a72358` — feat/mob-behaviour-logging → main

---

## Improvement 2 — Recovery Saves

### Quality Problem

Shattered Pixel Dungeon's original save system used a permanent hard-delete: calling `Dungeon.deleteGame(slot)` immediately removed all files for that slot with no possibility of recovery. A single stray call — or a crash mid-delete — left the player with an unrecoverable game state.

**ISO/IEC 25010 mapping:** Reliability → Recoverability; Fault Tolerance.

### Design

`RecoveryManager` intercepts every delete request and instead performs a **soft delete**: it copies the entire save bundle to a `recovery/` subdirectory before removing it from the active slot directory. A timestamp-prefixed folder name ensures no two recoveries collide. At most **5 recovery entries per slot** are retained; when the sixth arrives the oldest is automatically pruned.

```
RecoveryManager (static utility class)
│
├── backupBeforeDelete(int slot)        ← called from Dungeon.deleteGame() patch
├── listRecoveries(int slot)            ← returns List<RecoveryEntry>, newest first
├── restore(RecoveryEntry entry, int targetSlot)
├── delete(RecoveryEntry entry)
└── pruneOldest(int slot, int maxEntries)
```

The in-game recovery UI is surfaced through `WndRecoverySave`, a `Window` subclass that appears in the title-screen save slot menu. It lists available recovery entries for the selected slot, shows the timestamp and hero class for each, and calls `RecoveryManager.restore()` when the player confirms.

### Save Directory Layout

```
<save-dir>/
├── game1/          ← active slot
│   ├── game.dat
│   └── depth1.dat
└── recovery/
    └── game1/
        ├── 20240315_143022_game1/    ← most recent backup
        │   ├── game.dat
        │   └── depth1.dat
        └── 20240301_092110_game1/    ← older backup (pruned after 5)
```

On Windows the save directory is:
```
%USERPROFILE%\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon\
```

### Files Changed

| File | Change |
|------|--------|
| `utils/RecoveryManager.java` | **New** — full backup / restore / prune logic |
| `scenes/WndRecoverySave.java` | **New** — in-game recovery window |
| `Dungeon.java` | `deleteGame()` patched to call `RecoveryManager.backupBeforeDelete()` |
| `scenes/WndGameInProgress.java` | recovery button wired into slot menu |

### Gradle CLI Tasks

Three Gradle tasks allow recovery operations without launching the game, useful for CI and manual testing:

```bash
# List all recovery entries for slot 1
./gradlew :desktop:listRecoverySaves -Pslot=1

# Restore a specific entry back to slot 1
./gradlew :desktop:restoreRecoverySave -Pslot=1 -Ptimestamp=20240315_143022

# Delete a specific recovery entry
./gradlew :desktop:deleteRecoverySave -Pslot=1 -Ptimestamp=20240315_143022
```

### Acceptance Criteria (all met)

- Deleting an active save creates a timestamped recovery folder before removing the slot.
- No more than 5 recovery entries exist per slot at any time; oldest is pruned automatically.
- Restoring a recovery entry to an empty slot produces a byte-identical copy of the original save.
- Restoring over an occupied slot backs up the occupant first (recursive safety).
- Gradle tasks complete without launching the game UI.

### Merge Commit

`7811326` — feat/recovery-saves → main

---

## Improvement 3 — Save Integrity Checksum

### Quality Problem

The game's save files are gzipped JSON bundles stored on the local filesystem. Nothing prevented silent corruption — whether from truncated writes, filesystem errors, or external modification. A corrupted save would either crash the game on load or, worse, load silently with subtly wrong state (wrong depth, missing items, broken transitions).

**ISO/IEC 25010 mapping:** Security → Integrity; Reliability → Fault Tolerance.

### Design

A two-layer checksum scheme is applied on every save and verified on every load:

**Layer 1 — per-bundle checksum.** `SaveIntegrityManager.sign(jsonObject)` computes a SHA-256 hash of the canonical JSON string (keys sorted, no extra whitespace) and injects it as `"__checksum": "<hex>"` before the bundle is written. On load, `verify(jsonObject)` extracts and removes the field, recomputes the hash, and compares.

**Layer 2 — file-level manifest.** `save_checksums.dat` in the save directory maps each save file path to its SHA-256 hash. After writing any save file, `SaveIntegrityManager.updateManifest(path, hash)` upserts the entry. On load, `verifyManifest(path)` re-hashes the file and compares against the manifest entry.

```
SaveIntegrityManager (static utility class)
│
├── sign(JSONObject bundle)          → mutates bundle, adds __checksum
├── verify(JSONObject bundle)        → returns VerifyResult {OK, MISSING, MISMATCH}
├── hashFile(Path path)              → returns hex SHA-256 of file bytes
├── updateManifest(Path, String)     → upserts entry in save_checksums.dat
└── verifyManifest(Path)             → returns VerifyResult
```

**Legacy save compatibility.** Saves that pre-date this feature have no `__checksum` field and no manifest entry. Both `verify()` and `verifyManifest()` return `VerifyResult.MISSING` (not `MISMATCH`) for these, allowing the game to load them normally while logging a warning. Only an explicit hash mismatch blocks loading.

### Test Fixtures

Six save fixtures in `doc/save-integrity-checksum/` exercise latent quality issues independent of the checksum feature, demonstrating the kinds of corruption the feature is designed to catch:

| Slot | Seed label | Fault type |
|------|-----------|------------|
| game1 | `UP-FLOOR-0-DOWN-MISSING-D2` | `depth2.dat` absent despite being marked generated |
| game2 | `DOWN-RELOADS-FLOOR-1` | Down-ladder loops back to current floor |
| game3 | `DOWN-TO-DEPTH-999` | Down-ladder points to non-existent depth 999 |
| game4 | `VISIBLE-DOWN-NO-TRANSITION` | Visible exit tile has no transition metadata |
| game5 | `HIDDEN-FLOOR-TILE-DESCENDS` | Hidden floor tile descends; visible ladder does nothing |
| game6 | `LOCKED-LEVEL-STAIRS-BLOCKED` | Floor flagged locked — visible transitions refused |

All six pass the integrity check (checksums are valid; the faults are semantic) and all six fail only during gameplay interaction, demonstrating that checksum verification catches byte-level corruption while remaining orthogonal to game-logic bugs.

### Files Changed

| File | Change |
|------|--------|
| `utils/SaveIntegrityManager.java` | **New** — sign/verify/manifest logic |
| `Dungeon.java` | `saveGame()` calls `sign()`, `loadGame()` calls `verify()` |
| `FileUtils.java` | `writeFile()` calls `updateManifest()` after write |

### Merge Commit

`43c6f42` — feat/save-integrity-checksum → main

---

## SQA Process

### Branching Strategy

Each improvement was developed in isolation on its own feature branch. No feature branch was merged until:

1. All unit tests for that feature passed (`./gradlew :core:test`).
2. A teammate performed a code review and approved the PR.
3. SpotBugs reported zero new bugs in the `CORRECTNESS` and `THREAD_SAFETY` categories.
4. JaCoCo line coverage for new code was ≥ 90%.

```
main
 ├── feat/mob-behaviour-logging    → merged at 7a72358
 ├── feat/recovery-saves           → merged at 7811326
 ├── feat/save-integrity-checksum  → merged at 43c6f42
 └── report/merge-test             (integration verification)
```

### Pair Programming & Mob Reviews

Each feature was developed with a partner using informal pair programming (driver / navigator rotation in 25-minute blocks). Before merge, all three team members reviewed each PR together in a mob review session, checking for correctness, thread safety, edge cases, and adherence to the zero-ΔV(G) constraint on existing methods.

### Test-Driven Development

Tests were written before implementation for all three features following the red → green → refactor cycle:

1. **Red** — write a failing test that specifies the desired behaviour (e.g., "logging a spawn event stores it in the buffer").
2. **Green** — write the minimum implementation to make the test pass.
3. **Refactor** — clean up the implementation while keeping tests green.

This was particularly effective for `SaveIntegrityManager`, where the `VerifyResult` enum and the `MISSING` vs. `MISMATCH` distinction were driven entirely by failing tests before a single line of the manager was written.

### Static Analysis

SpotBugs was run as part of the Gradle build:

```bash
./gradlew :core:spotbugsMain
```

All findings were either fixed or documented as accepted risks before merge. No `CORRECTNESS` or `THREAD_SAFETY` category bugs were accepted.

---

## Test Results & Coverage

### Summary

| Feature | Unit Tests | Integration Tests | Line Coverage | Branch Coverage |
|---------|-----------|-------------------|---------------|-----------------|
| Mob Behaviour Logging | 18 | 4 | 96.0% | 100.0% |
| Save Integrity Checksum | 21 | 5 | 94.0% | 87.2% |
| Recovery Saves | 16 | 6 | 97.2% | 100.0% |
| Cross-feature (all three) | — | 8 | — | — |
| **Total** | **55** | **23** | — | — |

### Mob Behaviour Logging — Selected Test Cases

| Test | Path exercised | Expected outcome |
|------|---------------|-----------------|
| `testSpawnEventRecorded` | logger enabled → `log(EVENT_SPAWN, ...)` | entry in buffer with correct type |
| `testRingBufferEviction` | fill buffer to capacity + 1 | oldest entry dropped, size = capacity |
| `testDisabledLoggerDropsAll` | logger disabled → `log(...)` × N | buffer remains empty |
| `testGetEntriesLimit` | 10 entries logged, `getEntries(3)` | returns 3 most recent in order |
| `testFileSinkWritesUTF8` | file sink configured, 5 events logged | file contains 5 lines, valid UTF-8 |
| `testConcurrentLogThreadSafety` | 4 threads × 250 events simultaneously | no lost updates, no exceptions |
| `testStateChangeEventOnSetState` | `mob.setState(SLEEPING)` → `setState(HUNTING)` | two STATE_CHANGE events in buffer |
| `testAlertEventOnAlerted` | `mob.alerted()` called | one ALERT event in buffer |

### Recovery Saves — Selected Test Cases

| Test | Path exercised | Expected outcome |
|------|---------------|-----------------|
| `testBackupCreatedOnDelete` | `backupBeforeDelete(1)` | timestamped folder created in `recovery/game1/` |
| `testRestoreProducesIdenticalFiles` | backup then restore to empty slot | byte-identical save files |
| `testMaxFiveEntriesEnforced` | 6 sequential deletes of slot 1 | only 5 entries in `recovery/game1/` |
| `testOldestPrunedFirst` | 6 deletes, inspect remaining entries | oldest timestamp absent |
| `testRestoreOverOccupiedSlotBacksUpFirst` | restore over live slot | original occupant backed up before overwrite |
| `testListRecoveriesNewestFirst` | 3 backups at different timestamps | `listRecoveries()` returns newest first |

### Save Integrity Checksum — Selected Test Cases

| Test | Path exercised | Expected outcome |
|------|---------------|-----------------|
| `testSignInjectsChecksum` | `sign(bundle)` | `__checksum` key present in JSON |
| `testVerifyOkOnUnmodifiedBundle` | sign then verify same object | `VerifyResult.OK` |
| `testVerifyMismatchOnTamperedBundle` | sign, modify one field, verify | `VerifyResult.MISMATCH` |
| `testVerifyMissingOnLegacySave` | verify bundle with no checksum field | `VerifyResult.MISSING` |
| `testManifestUpdatedAfterWrite` | write file, check manifest | SHA-256 in manifest matches file hash |
| `testManifestMismatchOnCorruptedFile` | write file, corrupt bytes, verify manifest | `VerifyResult.MISMATCH` |
| `testChecksumFieldExcludedFromHash` | sign twice, compare checksums | identical (field excluded from hash input) |

---

## Cyclomatic Complexity Analysis

Full flow graphs, FSM diagrams, and per-method V(G) tables are in [`FEATURE_COMPLEXITY_AND_TEST_REPORT.md`](FEATURE_COMPLEXITY_AND_TEST_REPORT.md). Key findings are summarised here.

### Constraint: ΔV(G) = 0 in All Patched Existing Methods

Every method in the pre-existing codebase that was modified received only straight-line additions — no new `if`, `while`, `for`, `switch`, or `catch` blocks. This kept V(G) identical in those methods before and after each patch.

### New Code Complexity

| Class / Method | V(G) | Rationale |
|---------------|------|-----------|
| `MobBehaviourLogger.log()` | 4 | enabled check + sink dispatch + buffer-full eviction |
| `MobBehaviourLogger.getEntries(limit)` | 2 | limit clamp |
| `RecoveryManager.backupBeforeDelete()` | 3 | slot valid + destination exists |
| `RecoveryManager.pruneOldest()` | 3 | list size check + loop |
| `RecoveryManager.restore()` | 4 | source exists + target occupied + copy loop |
| `SaveIntegrityManager.verify()` | 3 | missing vs. mismatch vs. ok |
| `SaveIntegrityManager.verifyManifest()` | 3 | missing vs. mismatch vs. ok |
| `SaveIntegrityManager.sign()` | 1 | straight-line |

All new V(G) values sit in the 1–5 range, indicating well-scoped, easily testable methods. No method exceeds the threshold of 10 that would trigger mandatory refactoring.

### Finite State Machine Summary

Each feature's runtime behaviour was modelled as an FSM to drive test-path selection:

**Mob Behaviour Logger** — states: `DISABLED`, `ENABLED_BUFFER_SPACE`, `ENABLED_BUFFER_FULL`. Transitions: `setEnabled(true/false)`, `log()` when buffer not full, `log()` when buffer full (evicts oldest).

**Recovery Manager** — states: `IDLE`, `BACKING_UP`, `PRUNING`, `RESTORING`. Transitions driven by `backupBeforeDelete()`, `restore()`, `delete()`.

**Save Integrity** — states: `UNSIGNED`, `SIGNED`, `VERIFIED_OK`, `VERIFIED_MISSING`, `VERIFIED_MISMATCH`. Transitions: `sign()`, `verify()`.

### Markov Chain Analysis

A first-order Markov chain was constructed for each feature to model the probability of transitioning between states under normal gameplay. This informed which state transitions to stress-test and which error paths (low-probability transitions) required targeted negative tests rather than relying on random coverage.

For the Mob Behaviour Logger, the highest-probability transition chain is `ENABLED_BUFFER_SPACE → ENABLED_BUFFER_SPACE` (repeated logging), with buffer-full eviction as a low-probability but high-impact transition — tested explicitly in `testRingBufferEviction`.

For the Save Integrity Manager, the chain `UNSIGNED → SIGNED → VERIFIED_OK` covers the normal write-read cycle (probability ≈ 0.98 under clean conditions). The `VERIFIED_MISMATCH` state has a near-zero probability under normal conditions but a severity of "block load", making it the highest-priority path for dedicated negative tests.

---

## Cross-Feature Integration

`MergedFeatureIntegrationTest.java` on branch `report/merge-test` verifies that all three features coexist without interference. Eight integration scenarios are covered:

| Scenario | Features exercised | Assertion |
|----------|--------------------|-----------|
| Log events during save + checksum | Mob Logging + Checksum | log buffer unaffected by sign/verify calls |
| Recovery backup preserves checksum | Recovery + Checksum | restored save passes integrity check |
| Mob events during recovery restore | Mob Logging + Recovery | spawn events emitted for mobs in restored save |
| All three during a simulated level load | All three | no exceptions; correct event counts; verified save |
| Checksum on recovery entry files | Recovery + Checksum | recovery folder files pass manifest verification |
| Log buffer survives recovery restore | Mob Logging + Recovery | buffer contents unchanged after restore |
| Concurrent log + save | Mob Logging + Checksum | no data races under simultaneous logging and writing |
| Full game cycle | All three | spawn → alert → save (signed) → delete (backed up) → restore (verified) |

All 8 scenarios pass on `report/merge-test`.

---

## How to Build & Run

### Prerequisites

- JDK 11 or higher
- Android SDK (for Android build only)
- Gradle wrapper included (`./gradlew`)

### Desktop Build & Run

```bash
cd shattered-pixel-dungeon-3.0.2
./gradlew :desktop:run
```

### Android Build

```bash
./gradlew :android:assembleDebug
# APK at android/build/outputs/apk/debug/android-debug.apk
```

### Enabling Mob Behaviour Logging at Runtime

Logging is **disabled by default**. To enable it programmatically:

```java
MobBehaviourLogger logger = MobBehaviourLogger.getInstance();
logger.configure(1000, MobBehaviourLogger.SINK_TERMINAL | MobBehaviourLogger.SINK_FILE, "mob_log.txt");
logger.setEnabled(true);
```

Or pass the system property at launch:

```bash
./gradlew :desktop:run -Dmob.logging=true -Dmob.log.file=mob_log.txt
```

---

## How to Run Tests

### All Tests

```bash
cd shattered-pixel-dungeon-3.0.2
./gradlew :core:test
```

### Tests for a Specific Feature

```bash
# Mob Behaviour Logging
./gradlew :core:test --tests "MobBehaviourLoggerTest"

# Recovery Saves
./gradlew :core:test --tests "RecoveryManagerTest"

# Save Integrity Checksum
./gradlew :core:test --tests "SaveIntegrityManagerTest"

# Cross-feature integration
./gradlew :core:test --tests "MergedFeatureIntegrationTest"
```

### Coverage Report (JaCoCo)

```bash
./gradlew :core:jacocoTestReport
# HTML report: core/build/reports/jacoco/test/html/index.html
```

### Mutation Testing (PITest)

```bash
./gradlew :core:pitest
# Report: core/build/reports/pitest/index.html
```

### Static Analysis (SpotBugs)

```bash
./gradlew :core:spotbugsMain
# XML report: core/build/reports/spotbugs/main.xml
```

---

## Gradle Tasks Reference

| Task | Module | Description |
|------|--------|-------------|
| `test` | `:core` | Run all unit and integration tests |
| `jacocoTestReport` | `:core` | Generate JaCoCo HTML coverage report |
| `pitest` | `:core` | Run PITest mutation analysis |
| `spotbugsMain` | `:core` | Run SpotBugs static analysis on main sources |
| `listRecoverySaves` | `:desktop` | List recovery entries for a slot (`-Pslot=N`) |
| `restoreRecoverySave` | `:desktop` | Restore an entry (`-Pslot=N -Ptimestamp=...`) |
| `deleteRecoverySave` | `:desktop` | Delete an entry (`-Pslot=N -Ptimestamp=...`) |
| `run` | `:desktop` | Build and launch the desktop game |
| `assembleDebug` | `:android` | Build a debug APK |
