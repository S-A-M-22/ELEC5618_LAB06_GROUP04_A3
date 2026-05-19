# Recovery Saves — Implementation Notes

**Feature:** Predetermined Item #2 — Recover Recently Deleted Saves  
**Branch:** `feat/recovery-saves`  
**Author:** XINYU  
**Reviewer:** ELEVEN

---

## What This Feature Does

When a player erases a save slot, the game now copies all save files into a
`recovery/` directory before overwriting them. Up to **5** recovery entries are
kept; when a sixth is added the oldest is removed automatically.

Saves can be restored either **in-game** (via a UI button on the save-slot
screen) or **from the command line** (via Gradle tasks, no game required).

---

## Files Changed

### New Files

| File | Location |
|------|----------|
| `RecoveryManager.java` | `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` |
| `WndRecoverySave.java` | `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/windows/` |

### Modified Files

| File | Change |
|------|--------|
| `Dungeon.java` | `deleteGame()` now calls `RecoveryManager.moveToRecovery(slot)` before overwriting files |
| `StartScene.java` | Adds a **Recover** button (visible only when recovery entries exist) |
| `build.gradle` | Adds three Gradle tasks: `listRecoverySaves`, `restoreRecoverySave`, `deleteRecoverySave` |
| `core/.../messages/windows/windows.properties` | Adds `wndrecoverysave.*` message keys |
| `core/.../messages/scenes/scenes.properties` | Adds `startscene.recover` and `startscene.months_ago` |

---

## Recovery Directory Layout

Recovery entries are stored alongside the normal game-slot folders:

```
<save-dir>/
  game1/          <- active save slot 1
  game2/          <- active save slot 2
  recovery/
    1716000000000_game1/    <- deleted slot 1, timestamp prefix
        game.dat
        depth1.dat
        depth2.dat
        ...
    1716000001000_game2/    <- deleted slot 2
        game.dat
        ...
```

Entry names use a millisecond timestamp prefix so they sort
**oldest-first** lexicographically — no extra metadata file needed.

Desktop save directory locations:

| OS | Path |
|----|------|
| Windows | `%USERPROFILE%\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon\` |
| macOS | `~/Library/Application Support/Shattered Pixel Dungeon/` |
| Linux | `$XDG_DATA_HOME/.shatteredpixel/shattered-pixel-dungeon/` |

---

## In-Game UI Flow

```
Title Screen  →  Play  →  StartScene (save-slot list)
  →  click an existing slot  →  WndGameInProgress
  →  click "Erase"  →  confirm
  →  files backed up automatically  →  StartScene refreshes
  →  [Recover] button appears in the bottom-right of the slot list
  →  click [Recover]  →  WndRecoverySave (newest entry first)
  →  click [Restore] on a row
       - slot is empty  →  restore immediately  →  StartScene refreshes
       - slot is occupied  →  confirmation dialog  →  overwrite  →  StartScene refreshes
```

The **[Recover]** button is hidden when there are no recovery entries.

---

## Gradle Tasks (Command-Line Restore)

Run from the `shattered-pixel-dungeon-3.0.2/` directory.

```bash
# List all recovery entries with index, original slot, and deletion time
./gradlew listRecoverySaves

# Restore entry #2 into game slot 1
./gradlew restoreRecoverySave -PrecoveryIndex=2 -PtargetSlot=1

# Permanently delete recovery entry #1
./gradlew deleteRecoverySave -PrecoveryIndex=1
```

Override the default save directory if needed:

```bash
./gradlew listRecoverySaves -PsaveDir="C:/custom/path/to/saves"
```

---

## Public API — RecoveryManager

```java
// Called automatically by Dungeon.deleteGame(); copies slot files to recovery/
RecoveryManager.moveToRecovery(int slot)

// Returns entry directory names sorted oldest-first
RecoveryManager.listRecoveryEntries()  // -> ArrayList<String>

// Copies files from a recovery entry back into the target slot
RecoveryManager.restoreEntry(String entryName, int targetSlot)

// Parses the original slot number from an entry name; returns -1 if unparseable
RecoveryManager.slotFromEntryName(String entryName)  // -> int

// Constants
RecoveryManager.RECOVERY_DIR        // "recovery"
RecoveryManager.MAX_RECOVERY_SAVES  // 5
```

---

## Testing Guide (for ELEVEN)

### Quick Setup

To test without playing through the game, copy an existing save fixture into
the desktop save directory:

```powershell
# Windows PowerShell
$saveDir = "$env:USERPROFILE\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon"
New-Item -ItemType Directory -Force "$saveDir\game1"
Copy-Item save-fixtures\quality-issue-latent-clean\regular\game1\* "$saveDir\game1\"
```

Then launch the desktop build and erase the save to trigger a recovery entry.

### Test Cases

| # | Scenario | Steps | Expected Result |
|---|----------|-------|-----------------|
| T1 | Entry created on delete | Erase a save in-game | `recovery/<timestamp>_game<N>/` folder exists with `game.dat` and all depth files |
| T2 | Max 5 entries enforced | Erase 6 different saves | Only 5 entries remain; the oldest (smallest timestamp) is removed |
| T3 | Entries sorted oldest-first | Call `listRecoveryEntries()` after multiple deletes | List is in ascending timestamp order |
| T4 | Restore to empty slot | Click Restore when target slot is empty | Files copied back; `GamesInProgress.check(slot)` returns non-null |
| T5 | Restore to occupied slot | Click Restore when target slot has a save | Confirmation dialog shown; after confirming, slot contains restored files |
| T6 | Empty recovery dir — no crash | Call `listRecoveryEntries()` with no `recovery/` folder | Returns empty list without exception |
| T7 | Recover button visibility | Start scene with no recovery entries | **[Recover]** button is not visible |
| T8 | Recover button visibility | Start scene after at least one delete | **[Recover]** button is visible |
| T9 | Gradle list task | Run `./gradlew listRecoverySaves` | Prints entry table; exits with BUILD SUCCESSFUL |
| T10 | Gradle restore task | Run `restoreRecoverySave -PrecoveryIndex=1 -PtargetSlot=1` | Files appear in `game1/`; game recognises the restored slot |

### Key Assertions for Unit Tests

```java
// T1
assertTrue(FileUtils.dirExists("recovery"));
assertFalse(RecoveryManager.listRecoveryEntries().isEmpty());

// T2
assertEquals(RecoveryManager.MAX_RECOVERY_SAVES,
             RecoveryManager.listRecoveryEntries().size());

// T3
ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
for (int i = 1; i < entries.size(); i++) {
    assertTrue(entries.get(i - 1).compareTo(entries.get(i)) < 0);
}

// T6
// (delete the recovery/ dir first, then:)
assertEquals(0, RecoveryManager.listRecoveryEntries().size());

// T4 — after restoreEntry:
assertNotNull(GamesInProgress.check(targetSlot));
assertTrue(FileUtils.fileLength(GamesInProgress.gameFile(targetSlot)) > 1);
```
