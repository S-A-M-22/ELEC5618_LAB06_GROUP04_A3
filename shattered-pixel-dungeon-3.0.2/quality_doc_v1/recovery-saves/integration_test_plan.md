# Integration Test Plan — Recovery Saves Workflow

## 1. Scope

Integration tests that verify the end-to-end recovery saves workflow by exercising multiple `RecoveryManager` methods together. Tests use a real temporary file system and confirm that the components interact correctly as a system.

Unlike unit tests, each case exercises a complete user-visible scenario: delete → list → restore.

## 2. Test Framework and Dependencies

| Item | Value |
|---|---|
| Framework | JUnit 4 (`junit:junit:4.13.2`) |
| File I/O bootstrap | `gdx-backend-headless` (initialises `Gdx.files` without a display) |
| Temp directory | JUnit `TemporaryFolder` rule — auto-deleted after each test |
| File type | `Files.FileType.Absolute` pointed at the temp root |

Same bootstrap strategy as the unit tests. The integration tests differ in that they call multiple `RecoveryManager` methods in sequence and assert on the combined result.

## 3. Test Cases

### IT-01 — Full delete-and-restore cycle preserves file content

| Field | Detail |
|---|---|
| Methods exercised | `moveToRecovery` → `listRecoveryEntries` → `restoreEntry` |
| Precondition | `game1/` contains `game.dat` with known byte content |
| Steps | 1. Call `moveToRecovery(1)`. 2. List entries; take first. 3. Delete `game1/`. 4. Call `restoreEntry(entry, 1)`. |
| Expected | `game1/game.dat` exists and its content is byte-for-byte identical to the original |
| Rationale | End-to-end correctness: a recovered save must be identical to the deleted save. |

### IT-02 — Correct entry is restored to the specified target slot

| Field | Detail |
|---|---|
| Methods exercised | `moveToRecovery` (×3) → `listRecoveryEntries` → `restoreEntry` |
| Precondition | Three slots (`game1`, `game2`, `game3`) each contain a uniquely named marker file |
| Steps | Delete all three. Restore the second entry (originally `game2`) to slot 3. |
| Expected | `game3/` contains the marker file that was originally in `game2/`; `game1/` and `game2/` remain absent |
| Rationale | Restore must place files in the requested target slot, not the original slot. |

### IT-03 — Restore over an occupied target slot overwrites existing files

| Field | Detail |
|---|---|
| Methods exercised | `moveToRecovery` → `restoreEntry` |
| Precondition | `game1/` is deleted and a recovery entry exists; `game2/` contains a different save |
| Steps | Call `restoreEntry(entry, 2)`. |
| Expected | `game2/` contains the restored files, not the previous occupant's files |
| Rationale | `WndRecoverySave` shows a confirmation dialog before overwriting; the backend must honour the overwrite when confirmed. |

### IT-04 — Retention limit: sixth deletion removes only the oldest entry

| Field | Detail |
|---|---|
| Methods exercised | `moveToRecovery` (×6) → `listRecoveryEntries` |
| Precondition | None |
| Steps | Delete slot 1 six times, re-creating the slot directory with a unique marker file before each call. Record the entry name produced by the first call. |
| Expected | `listRecoveryEntries().size() == 5`; the entry name recorded from the first call is absent; the five remaining entries correspond to calls 2–6 in ascending timestamp order |
| Rationale | Predefined requirement: at most 5 saves recoverable; the oldest (not a random one) must be the one discarded. |

### IT-05 — Recovery entry is not deleted after a successful restore

| Field | Detail |
|---|---|
| Methods exercised | `moveToRecovery` → `restoreEntry` → `listRecoveryEntries` |
| Precondition | One recovery entry exists |
| Steps | Restore the entry to slot 2. Then list entries again. |
| Expected | `listRecoveryEntries()` still contains the same entry |
| Rationale | The design intent is that a player can restore the same deleted save multiple times (e.g. into different slots for experimentation). |
