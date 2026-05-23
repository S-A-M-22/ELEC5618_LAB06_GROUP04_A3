# ELEC5618 Assignment 3 — Software Quality Improvements

## Shattered Pixel Dungeon v3.0.2

> **Course:** ELEC5618 — Software Quality Engineering
> **Base Project:** [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) v3.0.2
> **Platform:** Android + Desktop, LibGDX / Java
> **Build System:** Gradle
> **Detailed Feature Report:** `FEATURE_COMPLEXITY_AND_TEST_REPORT.md`

This repository is an Assignment 3 quality-improvement fork of **Shattered Pixel Dungeon v3.0.2**. The work focuses on three reliability, observability, and recovery features:

1. **Mob Behaviour Logging** — records important mob AI events for debugging and quality assurance.
2. **Recovery Saves** — keeps recoverable copies of deleted save slots before destructive deletion.
3. **Save Integrity Checksum** — detects save-file corruption or tampering through bundle and manifest checksums.

The goal is not to redesign the game. The goal is to improve software quality by making game behaviour more observable, making saves safer, and making deletion less destructive.

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
13. [Fixture Scripts](#fixture-scripts)
14. [Gradle Tasks Reference](#gradle-tasks-reference)

---

## Project Overview

This project extends Shattered Pixel Dungeon with three targeted software-quality improvements.

| Feature                 | Quality Goal                    | User / Developer Benefit                                                                                       |
| ----------------------- | ------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Mob Behaviour Logging   | Observability                   | Makes mob AI state changes, target changes, alerts, and spawns easier to inspect during debugging and testing. |
| Recovery Saves          | Reliability and fault tolerance | Gives players a chance to recover save slots after deletion.                                                   |
| Save Integrity Checksum | Data integrity                  | Detects stale, corrupted, or externally modified protected save files before they are loaded.                  |

The feature report compares each feature against the first parent of the merge commit that introduced it.

| Feature                 | Feature Merge | Benchmark Parent | Main Changed Production Files                                                     |
| ----------------------- | ------------: | ---------------: | --------------------------------------------------------------------------------- |
| Mob behaviour logging   | [`7a72358`](https://github.com/S-A-M-22/ELEC5618-A3/commit/7a7235814be6a7369181bc805edb7eba18775cbd) | `7a72358^1` | `Mob.java`, `MobBehaviourLogger.java`                                             |
| Save integrity checksum | [`43c6f42`](https://github.com/S-A-M-22/ELEC5618-A3/commit/43c6f424fc87434b7da000375abf022d26717259) | `43c6f42^1` | `Bundle.java`, `FileUtils.java`, `GameSettings.java`, `CrashReportWriter.java`    |
| Recovery saves          | [`7811326`](https://github.com/S-A-M-22/ELEC5618-A3/commit/7811326dfb46291c9dc8688acb6ecc0b1f198520) | `7811326^1` | `Dungeon.java`, `RecoveryManager.java`, `StartScene.java`, `WndRecoverySave.java` |

---

## Repository Structure

The source checkout keeps the upstream game inside `shattered-pixel-dungeon-3.0.2/`. The files below are the assignment-specific documentation, scripts, production code, and tests referenced by the feature report.

```text
.
├── README.md
├── FEATURE_COMPLEXITY_AND_TEST_REPORT.md
├── scripts/
│   ├── README.md
│   ├── create_quality_save_fixtures.py
│   ├── install_empty_save_and_run.sh
│   └── install_modified_save_and_run.sh
└── shattered-pixel-dungeon-3.0.2/
    ├── build.gradle
    ├── core/
    │   ├── src/main/java/com/shatteredpixel/shatteredpixeldungeon/
    │   │   ├── Dungeon.java
    │   │   ├── RecoveryManager.java
    │   │   ├── actors/mobs/Mob.java
    │   │   ├── actors/mobs/MobBehaviourLogger.java
    │   │   ├── scenes/StartScene.java
    │   │   └── windows/WndRecoverySave.java
    │   ├── src/test/java/com/shatteredpixel/shatteredpixeldungeon/
    │   │   ├── RecoveryManagerTest.java
    │   │   └── actors/mobs/MobBehaviourLoggerTest.java
    │   └── src/integrationTest/java/com/shatteredpixel/shatteredpixeldungeon/
    │       ├── RecoverySavesIntegrationTest.java
    │       └── actors/mobs/
    │           ├── MobBehaviourLoggerIntegrationTest.java
    │           └── MergedFeatureIntegrationTest.java
    └── SPD-classes/
        ├── src/main/java/com/watabou/utils/
        │   ├── Bundle.java
        │   ├── CrashReportWriter.java
        │   ├── FileUtils.java
        │   └── GameSettings.java
        ├── src/test/java/com/watabou/utils/
        │   ├── BundleTest.java
        │   ├── CrashReportWriterTest.java
        │   ├── FileUtilsTest.java
        │   └── GameSettingsTest.java
        └── src/integrationTest/java/com/watabou/utils/
            └── FileUtilsSaveManifestIntegrationTest.java
```

---

## Quality Framework

The three improvements were assessed using a software-quality framework based on:

* **User impact:** whether the change improves gameplay reliability, save safety, or debugging clarity.
* **Complexity placement:** whether new complexity is isolated in new helper components instead of making legacy logic harder to reason about.
* **Unit testing:** whether individual methods and edge cases are covered.
* **Integration testing:** whether features work across file systems, save slots, game state, and UI-facing flows.
* **Regression awareness:** whether existing behaviour is preserved where the feature should only observe or guard behaviour.
* **Coverage evidence:** whether the feature test suite provides measurable line, branch, and instruction coverage.

Cyclomatic complexity is reported as:

```text
V(G) = decisions + 1
```

Coverage evidence in the feature report comes from the Gradle project directory:

```bash
cd shattered-pixel-dungeon-3.0.2
./gradlew featureTest --no-daemon
```

---

## Improvement 1 — Mob Behaviour Logging

### Purpose

Mob Behaviour Logging improves observability of enemy AI behaviour. Instead of directly mutating mob state everywhere, selected mutation points now pass through helper methods that can log important events.

The report states that the original AI decision structure is preserved: existing gameplay branches remain the same, while new complexity is contained in observational helpers and logger code.

### What It Logs

The logger records events such as:

| Event Type        | Meaning                                                                   |
| ----------------- | ------------------------------------------------------------------------- |
| `MOB_SPAWN`       | A mob was added for the first time.                                       |
| `STATE_CHANGE`    | A mob changed AI state, such as wandering, hunting, fleeing, or sleeping. |
| `TARGET_ASSIGNED` | A mob target was assigned, changed, or cleared.                           |
| `ALERT`           | A mob became alerted by a trigger.                                        |

### Implementation Summary

The feature adds `MobBehaviourLogger` and helper methods in `Mob`:

```java
setState(AiState newState, String reason)
setEnemy(Char newEnemy, String reason)
markAlerted(String trigger)
```

Instead of raw assignments such as:

```java
state = HUNTING;
enemy = target;
alerted = true;
```

the updated code routes behaviour changes through helpers that can:

* ignore no-op changes,
* record meaningful event entries,
* keep a bounded log buffer,
* dispatch events to configured sinks,
* safely handle null values,
* avoid breaking gameplay if a sink throws.

### User-Facing Benefit

For developers, testers, and reviewers, this makes mob behaviour easier to inspect. Bugs involving enemy state transitions, repeated alerts, target switching, or unexpected AI behaviour can be investigated from recorded event sequences.

### Quality Evidence

The feature report identifies the following test files:

```text
core/src/test/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MobBehaviourLoggerTest.java
core/src/test/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MobBehaviourLoggerPerformanceTest.java
core/src/integrationTest/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MobBehaviourLoggerIntegrationTest.java
```

Covered behaviours include:

* singleton logger access,
* enabled and disabled logger paths,
* spawn, state-change, alert, and target logging,
* defensive copy behaviour,
* ring-buffer eviction,
* null-safe formatting,
* sink failure isolation,
* concurrent writes,
* mob lifecycle integration,
* `aggro` and `clearEnemy` integration paths,
* performance overhead checks.

Documented log line format:

```text
[12:34:56.789] [STATE_CHANGE] mob#7(TestMob)@42 : WANDERING -> HUNTING
```

---

## Improvement 2 — Recovery Saves

### Purpose

Recovery Saves improves player-facing reliability by creating a recoverable copy of a save slot before destructive deletion. This reduces the risk of irreversible save loss.

Before this change, the deletion flow permanently overwrote or deleted save files. After this change, deletion first attempts to move the save data into a recovery area.

### Behaviour

When a save is deleted:

1. `Dungeon.deleteGame` calls `RecoveryManager.moveToRecovery(save)`.
2. `RecoveryManager` copies flat save-slot files into a recovery entry.
3. Recovery entries are stored using the format:

```text
recovery/<timestamp>_<slot>/
```

4. Only the five newest recovery entries are retained.
5. `StartScene` shows a Recover button when recovery entries exist.
6. `WndRecoverySave` lists recovery entries.
7. Restoring into an occupied target slot asks for overwrite confirmation.

### Retention Policy

```text
Maximum retained recovery entries: 5
```

When more than five recovery entries exist, the oldest entry is pruned.

### User-Facing Benefit

Players get a recovery path after deleting a save slot. This is especially useful when a slot is deleted accidentally or when a user later decides they need the old save state.

### Quality Evidence

The feature report identifies the following test files:

```text
core/src/test/java/com/shatteredpixel/shatteredpixeldungeon/RecoveryManagerTest.java
core/src/integrationTest/java/com/shatteredpixel/shatteredpixeldungeon/RecoverySavesIntegrationTest.java
```

Covered behaviours include:

* parsing recovery entry names,
* ignoring malformed recovery entries,
* returning an empty list when no recovery directory exists,
* sorting recovery entries,
* skipping nested directories during copy,
* preserving file content during delete-and-restore,
* restoring to a different target slot,
* overwriting occupied slots,
* keeping only five newest recovery entries,
* keeping the recovery entry available after restore.

The UI hook is in `StartScene`: a Recover button is shown only when `RecoveryManager.listRecoveryEntries()` returns at least one valid recovery entry. `WndRecoverySave` lists entries by deleted time and original slot, and asks for confirmation before overwriting an occupied target slot.

---

## Improvement 3 — Save Integrity Checksum

### Purpose

Save Integrity Checksum improves save reliability by detecting protected save files that have been corrupted, edited externally, or made inconsistent with the expected save manifest.

The feature adds two layers of integrity checking:

| Layer                  | Description                                                                                                            |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Bundle checksum        | A SHA-256 checksum is stored inside each JSON bundle as `__checksum`.                                                  |
| Save manifest checksum | `save_checksums.dat` tracks protected save files, normalized game slot paths, file checksums, and an overall checksum. |

### Behaviour

When a protected save is written:

1. The bundle is written with a checksum.
2. Protected save files are scanned.
3. Per-file checksums are calculated.
4. An overall manifest checksum is calculated.
5. `save_checksums.dat` is written.

When a protected save is loaded:

1. The manifest is checked when available.
2. The target save file is validated.
3. The bundle checksum is verified.
4. Invalid checksums, malformed checksum fields, and manifest mismatches are rejected.

The report also identifies support for legacy saves without checksums and compatibility logic for normalized game slot paths.

### Protected Save Behaviour

The feature report describes manifest validation paths for:

* missing manifests,
* invalid manifest shape,
* valid exact matches,
* invalid stored overall checksum,
* normalized rename compatibility,
* legacy bundle checksum compatibility,
* checksum mismatch rejection.

### Additional Reliability Support

The feature also adds or modifies reliability-related code in:

```text
Bundle.java
FileUtils.java
GameSettings.java
CrashReportWriter.java
```

The report identifies `CrashReportWriter` as a new reliability component with failure containment, filename collision handling, truncation, platform/thread fallback, and GDX store failure handling.

### User-Facing Benefit

The game is less likely to silently accept corrupted or externally modified protected saves. Instead, checksum and manifest mismatches are detected during load.

This feature should be described as an integrity check, not as a complete security boundary.

### Quality Evidence

The feature report identifies the following test files:

```text
SPD-classes/src/test/java/com/watabou/utils/BundleTest.java
SPD-classes/src/test/java/com/watabou/utils/CrashReportWriterTest.java
SPD-classes/src/test/java/com/watabou/utils/FileUtilsTest.java
SPD-classes/src/test/java/com/watabou/utils/GameSettingsTest.java
SPD-classes/src/integrationTest/java/com/watabou/utils/FileUtilsSaveManifestIntegrationTest.java
```

Covered behaviours include:

* signed bundle write and read,
* checksum removal after read,
* legacy save loading without checksum,
* compressed save round trip,
* checksum mismatch rejection,
* canonical key ordering,
* nested objects, arrays, booleans, and null values,
* malformed checksum field rejection,
* save manifest creation,
* external tampering detection,
* random byte-flip rejection,
* protected file rename rejection,
* manifest shape validation,
* delete and overwrite manifest refresh,
* settings writes refreshing the manifest,
* manifest update failure containment,
* crash report write failure containment.

The low-level `IOException` messages used by the integrity layer include `bundle checksum is invalid`, `bundle checksum mismatch`, `save manifest is invalid`, and `save manifest checksum mismatch`.

---

## SQA Process

The software quality process used for this repository is based on feature-level comparison, path analysis, automated tests, and coverage reporting.

### 1. Feature Baseline Comparison

Each feature is compared against the first parent of the merge commit that introduced it.

| Feature                 | Merge Commit | Benchmark Parent |
| ----------------------- | -----------: | ---------------: |
| Mob behaviour logging   |    `7a72358` |      `7a72358^1` |
| Save integrity checksum |    `43c6f42` |      `43c6f42^1` |
| Recovery saves          |    `7811326` |      `7811326^1` |

### 2. Complexity Analysis

Cyclomatic complexity is reviewed to check whether each feature adds complexity in a controlled way.

The main findings are:

| Feature                 | Complexity Finding                                                                                           |
| ----------------------- | ------------------------------------------------------------------------------------------------------------ |
| Mob behaviour logging   | Existing AI decision branches are preserved. New complexity is isolated in helper and logger methods.        |
| Save integrity checksum | Highest complexity increase because of canonical JSON signing, manifest scanning, and manifest verification. |
| Recovery saves          | Moderate, localized complexity mostly in `RecoveryManager` and recovery UI logic.                            |

### 3. Test Strategy

The test strategy uses:

* unit tests for helper methods and edge cases,
* integration tests for feature flows across classes,
* performance tests for mob logging overhead,
* filesystem-based tests for save integrity and recovery behaviour,
* tampering and corruption simulation for save validation.

### 4. Regression Considerations

Regression risk is managed by keeping feature behaviour localized:

* mob logging observes AI transitions without changing the original AI decision structure,
* recovery saves runs before destructive deletion and then allows the original deletion flow to continue,
* save integrity checks are integrated into existing save/load helpers and include legacy compatibility paths.

No CI workflow is checked into this repository. Verification evidence in this README is from local Gradle runs on 2026-05-23.

---

## Test Results & Coverage

The current verification command is:

```bash
cd shattered-pixel-dungeon-3.0.2
./gradlew featureTest --no-daemon
```

Last local verification: 2026-05-23, using JDK 17 and `GRADLE_USER_HOME=.gradle`. Result: **172 tests passed**, with 0 failures, 0 errors, and 0 skipped tests.

| Suite | Tests |
| ----- | ----: |
| `:core:test` | 56 |
| `:core:integrationTest` | 27 |
| `:SPD-classes:test` | 65 |
| `:SPD-classes:integrationTest` | 24 |
| **Total** | **172** |

JaCoCo coverage is intentionally scoped by `core/build.gradle` and `SPD-classes/build.gradle` to the assignment-added or assignment-modified components, not the entire upstream game.

| Scope | Classes Included | Line Coverage | Branch Coverage | Instruction Coverage |
| ----- | ---------------- | ------------: | --------------: | -------------------: |
| Mob behaviour logging | `MobBehaviourLogger*` | 96.0% | 100.0% | 95.8% |
| Recovery saves | `RecoveryManager*` | 100.0% | 100.0% | 100.0% |
| Save integrity checksum | `Bundle*`, `FileUtils*`, `GameSettings*`, `CrashReportWriter*` | 100.0% | 98.9% | 99.9% |

### Coverage Interpretation

| Feature                 | Interpretation                                                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Mob behaviour logging   | High coverage across logger internals, mob instrumentation, integration paths, and performance guard tests.                    |
| Save integrity checksum | Strong coverage across bundle signing, manifest validation, tamper detection, settings interaction, and crash-report handling. |
| Recovery saves          | Full coverage for `RecoveryManager`; UI classes are validated by source review and integration-path tests rather than the filtered JaCoCo scope. |

Generated JaCoCo report paths:

```text
shattered-pixel-dungeon-3.0.2/core/build/reports/jacoco/test/html/index.html
shattered-pixel-dungeon-3.0.2/core/build/reports/jacoco/test/jacocoTestReport.xml
shattered-pixel-dungeon-3.0.2/core/build/reports/jacoco/test/jacocoTestReport.csv
shattered-pixel-dungeon-3.0.2/SPD-classes/build/reports/jacoco/test/html/index.html
shattered-pixel-dungeon-3.0.2/SPD-classes/build/reports/jacoco/test/jacocoTestReport.xml
shattered-pixel-dungeon-3.0.2/SPD-classes/build/reports/jacoco/test/jacocoTestReport.csv
```

---

## Cyclomatic Complexity Analysis

Cyclomatic complexity is reported using:

```text
V(G) = decisions + 1
```

### Summary

| Feature                 | Complexity Placement                                                          | Result                                                                 |
| ----------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| Mob behaviour logging   | Added to logging helpers and event-recording paths.                           | Existing mob AI decision flow remains unchanged.                       |
| Recovery saves          | Added mainly to `RecoveryManager` and recovery UI logic.                      | New behaviour is localized and easy to test.                           |
| Save integrity checksum | Added to bundle signing, manifest scanning, manifest parsing, and validation. | Highest complexity, but supported by broad unit and integration tests. |

### Important Notes

#### Mob Behaviour Logging

The feature report states that the original mob AI control flow is preserved. Direct assignments were replaced by helper calls, but existing gameplay branches remain the same.

Main added complexity appears in:

```text
Mob.setState
Mob.setEnemy
Mob.markAlerted
MobBehaviourLogger.logSpawn
MobBehaviourLogger.logStateChange
MobBehaviourLogger.logAlert
MobBehaviourLogger.logTarget
MobBehaviourLogger.record
MobBehaviourLogger.dispatch
MobBehaviourLogger.safe
```

#### Recovery Saves

Recovery complexity is mostly new and localized.

Main added complexity appears in:

```text
RecoveryManager.moveToRecovery
RecoveryManager.listRecoveryEntries
RecoveryManager.restoreEntry
RecoveryManager.slotFromEntryName
RecoveryManager.isRecoveryEntryName
RecoveryManager.copyGameDir
RecoveryManager.pruneOldEntries
WndRecoverySave
```

#### Save Integrity Checksum

Save integrity has the largest complexity increase because it handles canonical JSON serialization, checksum verification, manifest update, manifest parsing, and compatibility behaviour.

Main added complexity appears in:

```text
Bundle.readChecksum
Bundle.verifyChecksum
Bundle.appendObject
Bundle.appendArray
Bundle.appendValue
FileUtils.updateSaveManifest
FileUtils.verifySaveManifest
FileUtils.collectManifestFiles
FileUtils.isManifestProtectedFile
FileUtils.isActiveGameDir
FileUtils.manifestEntriesFromManifest
FileUtils.legacyManifestFileChecksum
CrashReportWriter
CrashReportWriter.GdxReportStore
```

---

## Cross-Feature Integration

The three features improve different parts of the game:

| Feature                 | Area                                         |
| ----------------------- | -------------------------------------------- |
| Mob behaviour logging   | AI observability                             |
| Recovery saves          | Save deletion and restoration                |
| Save integrity checksum | Save validation and protected file integrity |

### Integration Considerations

* **Mob behaviour logging** is mostly independent from the save system. It improves observability without changing the original AI decision structure.
* **Recovery saves** and **save integrity checksum** both operate around save files, so they should be reviewed together for save lifecycle correctness.
* **Save integrity checksum** refreshes manifests after save-related file operations such as delete and overwrite.
* **Recovery saves** preserves deleted slot files in a recovery location and supports restoring them into target slots.

### Cross-Feature Test Evidence

`core/src/integrationTest/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/MergedFeatureIntegrationTest.java` covers nine scenarios where the features interact:

* mob logging does not affect save manifest validation,
* recovery directories are excluded from the save manifest,
* delete-to-recovery then restore keeps checksummed saves readable,
* restored saves reject later tampering,
* helper deletion updates the manifest while recovery keeps original bytes,
* logger sink failures do not block manifest or recovery workflows,
* `Dungeon.deleteGame` -> restore -> load -> tamper rejection works end to end.

---

## How to Build & Run

### Prerequisites

* JDK 17 is recommended for local build and coverage runs. The upstream desktop guide also recommends Java 17.
* Gradle wrapper: `8.10.2`.
* Android Gradle Plugin: `8.7.1`.
* Java source compatibility: `1.8`.
* Android compile SDK: `34`; min SDK: `14`; target SDK: `34`.
* Android builds require an Android SDK configured through Android Studio, `ANDROID_HOME`, or `local.properties`.

Java 25 can run some tasks, but JaCoCo `0.8.11` may print instrumentation warnings for Java 25 class files. Use JDK 17 for clean coverage output.

### Clone the Repository

```bash
git clone git@github.com:S-A-M-22/ELEC5618-A3.git
cd ELEC5618-A3
```

Gradle commands must be run from the nested game project:

```bash
cd shattered-pixel-dungeon-3.0.2
```

### Inspect Available Gradle Tasks

Use the Gradle wrapper if it exists in the repository:

```bash
./gradlew tasks
```

On Windows:

```bat
gradlew.bat tasks
```

### Desktop Build / Run

```bash
./gradlew desktop:debug --no-daemon
```

Build a distributable desktop JAR:

```bash
./gradlew desktop:release --no-daemon
```

The release JAR is generated under:

```text
desktop/build/libs/
```

### Android Build

```bash
./gradlew android:assembleDebug --no-daemon
```

If this fails with a missing SDK error, configure Android SDK location before rerunning.

---

## How to Run Tests

Run these commands from `shattered-pixel-dungeon-3.0.2/`.

### Full Feature Suite

```bash
./gradlew featureTest --no-daemon
```

This command runs both unit and integration tests for `:core` and `:SPD-classes`, then generates the scoped JaCoCo reports.

### Unit Tests

```bash
./gradlew core:test SPD-classes:test --no-daemon
```

### Integration Tests

```bash
./gradlew core:integrationTest SPD-classes:integrationTest --no-daemon
```

### Coverage Reports

```bash
./gradlew core:jacocoTestReport SPD-classes:jacocoTestReport --no-daemon
```

---

## Fixture Scripts

Run fixture scripts from the assignment repository root, not from `shattered-pixel-dungeon-3.0.2/`.

```bash
scripts/install_modified_save_and_run.sh --no-launch
scripts/install_empty_save_and_run.sh --no-launch
```

Both scripts back up existing numeric `gameN` saves before changing the save directory. For safe dry runs, point them at a temporary save directory:

```bash
SAVE_DIR=/tmp/spd-test-saves scripts/install_modified_save_and_run.sh --no-launch
```

The fixture generator writes into an existing fixture tree under `shattered-pixel-dungeon-3.0.2/save-fixtures/quality-issue-latent-clean`. Move or remove that output directory before regenerating fixtures.

```bash
python3 scripts/create_quality_save_fixtures.py
```

---

## Gradle Tasks Reference

Run these from `shattered-pixel-dungeon-3.0.2/`.

| Task | Purpose |
| ---- | ------- |
| `./gradlew tasks --all --no-daemon` | Lists available tasks. |
| `./gradlew featureTest --no-daemon` | Runs assignment feature tests and JaCoCo reports for `:core` and `:SPD-classes`. |
| `./gradlew core:test SPD-classes:test --no-daemon` | Runs unit tests. |
| `./gradlew core:integrationTest SPD-classes:integrationTest --no-daemon` | Runs integration tests. |
| `./gradlew core:jacocoTestReport SPD-classes:jacocoTestReport --no-daemon` | Generates scoped coverage reports. |
| `./gradlew desktop:debug --no-daemon` | Runs the desktop game in debug mode. |
| `./gradlew desktop:release --no-daemon` | Builds the desktop release JAR. |
| `./gradlew android:assembleDebug --no-daemon` | Builds an Android debug APK when Android SDK is configured. |
| `./gradlew listRecoverySaves` | Lists recovery saves in the desktop save directory. |
| `./gradlew restoreRecoverySave -PrecoveryIndex=N -PtargetSlot=M` | Restores a recovery entry into a target slot. |
| `./gradlew deleteRecoverySave -PrecoveryIndex=N` | Permanently deletes one recovery entry. |

---

## Assignment Deliverables

This repository should include:

```text
README.md
FEATURE_COMPLEXITY_AND_TEST_REPORT.md
Source code for the three implemented features
Unit tests
Integration tests
Coverage evidence
```

---

## Acknowledgement

This project is based on [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) v3.0.2. The added work in this repository is for ELEC5618 Assignment 3 and focuses on software quality improvements through observability, recovery, and save integrity.
