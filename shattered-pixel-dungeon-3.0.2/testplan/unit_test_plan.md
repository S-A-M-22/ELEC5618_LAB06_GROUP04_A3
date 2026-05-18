# Unit Test Plan — RecoveryManager

## 1. Scope

Unit tests for `RecoveryManager.java`. Each test exercises a single method in isolation using a real but temporary file system directory, with no game runtime or UI dependencies.

## 2. Test Framework and Dependencies

| Item | Value |
|---|---|
| Framework | JUnit 4 (`junit:junit:4.13.2`) |
| File I/O bootstrap | `gdx-backend-headless` (initialises `Gdx.files` without a display) |
| Temp directory | JUnit `TemporaryFolder` rule — auto-deleted after each test |
| File type | `Files.FileType.Absolute` pointed at the temp root |

`FileUtils.setDefaultFileProperties(Absolute, tempRoot)` is called in `@Before` so every `FileUtils` call inside `RecoveryManager` resolves under the temp directory instead of the real save folder.

## 3. Test Cases

### UT-01 — `slotFromEntryName`: valid entry returns correct slot

| Field | Detail |
|---|---|
| Method | `RecoveryManager.slotFromEntryName(String)` |
| Input | `"1716000000000_game1"` |
| Expected | `1` |
| Rationale | Baseline: correct parsing of a well-formed entry name. No file I/O. |

### UT-02 — `slotFromEntryName`: name without underscore returns -1

| Field | Detail |
|---|---|
| Method | `RecoveryManager.slotFromEntryName(String)` |
| Input | `"nounderscore"` |
| Expected | `-1` |
| Rationale | Guard against malformed entry names that would cause an index error. |

### UT-03 — `slotFromEntryName`: unknown slot folder returns -1

| Field | Detail |
|---|---|
| Method | `RecoveryManager.slotFromEntryName(String)` |
| Input | `"1716000000000_game99"` |
| Expected | `-1` |
| Rationale | `game99` is outside `GamesInProgress.MAX_SLOTS`; method must not crash. |

### UT-04 — `listRecoveryEntries`: returns empty list when no recovery directory

| Field | Detail |
|---|---|
| Method | `RecoveryManager.listRecoveryEntries()` |
| Precondition | Temp root has no `recovery/` subdirectory |
| Expected | Empty `ArrayList` |
| Rationale | Game must not crash on first launch when no deleted saves exist. |

### UT-05 — `listRecoveryEntries`: returns entries sorted oldest-first

| Field | Detail |
|---|---|
| Method | `RecoveryManager.listRecoveryEntries()` |
| Precondition | Three entry directories created manually with timestamps 300, 100, 200 |
| Expected | List order: `[..._100_game1, ..._200_game1, ..._300_game1]` |
| Rationale | Pruning and UI display both depend on ascending timestamp order. |

### UT-06 — `moveToRecovery`: no-op when source slot does not exist

| Field | Detail |
|---|---|
| Method | `RecoveryManager.moveToRecovery(int)` |
| Precondition | Temp root has no `game1/` directory |
| Expected | Recovery directory remains absent or empty; no exception |
| Rationale | Deleting an already-empty slot must not create a spurious recovery entry. |

### UT-07 — `moveToRecovery`: creates recovery entry containing source files

| Field | Detail |
|---|---|
| Method | `RecoveryManager.moveToRecovery(int)` |
| Precondition | `game1/` exists with `game.dat` and `depth1.dat` |
| Expected | `recovery/` contains exactly one entry directory; that directory contains `game.dat` and `depth1.dat` |
| Rationale | Core requirement: deleted save files must be preserved. |

### UT-08 — `moveToRecovery`: sixth call removes the oldest entry

| Field | Detail |
|---|---|
| Method | `RecoveryManager.moveToRecovery(int)` (called 6 times) |
| Precondition | Fake slot re-created before each call to give unique timestamps |
| Expected | `listRecoveryEntries().size() == 5`; first entry (lowest timestamp) is no longer present |
| Rationale | Predefined requirement: at most 5 recovery saves are retained. |
