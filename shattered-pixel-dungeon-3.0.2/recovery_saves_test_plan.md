# Recovery Saves Test Plan

## 1. Improvement Overview

This test plan verifies the recovery saves improvement implemented for Shattered Pixel Dungeon 3.0.2.

The improvement changes the save deletion workflow so that deleted game saves are preserved in a designated recovery location before the original save slot is removed. The system supports recovery of up to five most recently deleted saves. This directly addresses the predefined Assignment 3 improvement requirement for recovery of deleted game saves.

The main implementation files are:

| File | Purpose |
|---|---|
| `RecoveryManager.java` | Handles recovery save creation, listing, retention limit, and restore operations. |
| `WndRecoverySave.java` | Provides the user interface for selecting and restoring deleted saves. |
| `Dungeon.java` | Integrates recovery creation into the existing save deletion workflow. |
| `StartScene.java` | Adds access to the recovery save interface when recovery entries exist. |
| `build.gradle` | Provides Gradle tasks for listing, restoring, and deleting recovery saves. |

## 2. Quality Attribute Mapping

| ISO/IEC 25010 Attribute | Sub-characteristic | Relevance |
|---|---|---|
| Reliability | Recoverability | Deleted saves can be recovered instead of being permanently lost. |
| Functional Suitability | Functional Correctness | The system correctly preserves, lists, and restores deleted saves. |
| Operability | Ease of Use | The recovery window allows users to access recovery saves through the game interface. |
| Maintainability | Testability | Recovery logic is separated into `RecoveryManager`, making the core behaviour easier to test. |

## 3. Test Scope

The test scope covers:

- Creation of recovery entries when saves are deleted.
- Retention of no more than five recovery entries.
- Listing of recovery saves.
- Restoring a recovery save to a target slot.
- Visibility of the recovery option in the start scene.
- Gradle-based recovery operations.
- Edge cases such as empty recovery directories and invalid recovery indexes.

The test scope does not cover full gameplay progression after recovery, except for basic confirmation that restored saves are available to the game.

## 4. Test Environment

| Item | Description |
|---|---|
| Project | Shattered Pixel Dungeon 3.0.2 |
| Branch | `feat/recovery-saves` |
| IDE | IntelliJ IDEA |
| Build Tool | Gradle |
| Main platform | Desktop / Windows |
| Test type | Manual test, system test, Gradle task test, and code-level review |

## 5. Test Cases

### RS-01: Deleted save is preserved in recovery directory

| Field | Description |
|---|---|
| Purpose | Verify that deleting a save creates a recovery entry. |
| Precondition | At least one valid game save exists in a save slot. |
| Input / Action | Delete the save through the normal game delete workflow. |
| Expected Result | A new recovery entry is created in the recovery directory. |
| Validation | Check that the recovery directory contains a timestamped entry for the deleted slot. |
| Quality Attribute | Reliability - Recoverability |

### RS-02: Recovery entry contains the original save files

| Field | Description |
|---|---|
| Purpose | Verify that the deleted save data is preserved, not only the folder name. |
| Precondition | A valid game save exists and is deleted. |
| Input / Action | Inspect the generated recovery entry. |
| Expected Result | The recovery entry contains the original save files needed for restoration. |
| Validation | Compare the files in the recovery entry with the expected save-slot files. |
| Quality Attribute | Functional Suitability - Functional Correctness |

### RS-03: Only five most recent recovery saves are retained

| Field | Description |
|---|---|
| Purpose | Verify the predefined requirement that up to five recently deleted saves are recoverable. |
| Precondition | More than five saves are deleted sequentially. |
| Input / Action | Create six or more recovery entries by repeatedly deleting saves. |
| Expected Result | Only five recovery entries remain. The oldest recovery entry is removed. |
| Validation | Use the recovery list or file inspection to confirm the number and order of entries. |
| Quality Attribute | Reliability - Recoverability |

### RS-04: Recovery entries are listed correctly

| Field | Description |
|---|---|
| Purpose | Verify that available recovery saves can be listed. |
| Precondition | One or more recovery entries exist. |
| Input / Action | Open the recovery interface or run the Gradle listing task. |
| Expected Result | Existing recovery entries are displayed clearly. |
| Validation | Confirm that each listed entry corresponds to a recovery folder. |
| Quality Attribute | Operability |

### RS-05: Restore recovery save to an empty target slot

| Field | Description |
|---|---|
| Purpose | Verify that a deleted save can be restored into an available slot. |
| Precondition | A recovery entry exists and the target slot is empty. |
| Input / Action | Restore the recovery entry to the target slot. |
| Expected Result | The target slot contains the restored save. |
| Validation | Confirm the save appears in the game save list or exists in the expected save location. |
| Quality Attribute | Reliability - Recoverability |

### RS-06: Restore recovery save to an occupied target slot

| Field | Description |
|---|---|
| Purpose | Verify that restoring into an occupied slot is handled safely. |
| Precondition | A recovery entry exists and the target slot already contains a save. |
| Input / Action | Attempt to restore the recovery entry to the occupied slot. |
| Expected Result | The user is warned before overwrite, or the operation is prevented. |
| Validation | Confirm that the existing slot is not silently overwritten without confirmation. |
| Quality Attribute | Functional Suitability - Functional Correctness |

### RS-07: Empty recovery directory is handled correctly

| Field | Description |
|---|---|
| Purpose | Verify that the system behaves safely when no recovery saves exist. |
| Precondition | No recovery entries exist. |
| Input / Action | Open the start scene or run the recovery listing task. |
| Expected Result | No crash occurs. The recovery option is hidden or an empty-list message is shown. |
| Validation | Confirm normal start scene operation continues. |
| Quality Attribute | Reliability - Fault Tolerance |

### RS-08: Invalid recovery index is handled safely

| Field | Description |
|---|---|
| Purpose | Verify that invalid command-line recovery input does not crash the system. |
| Precondition | Recovery Gradle tasks are available. |
| Input / Action | Run restore or delete task with an invalid recovery index. |
| Expected Result | The task fails gracefully with a clear message. |
| Validation | Confirm no unrelated save files are modified. |
| Quality Attribute | Reliability - Fault Tolerance |

### RS-09: Gradle list recovery saves task works

| Field | Description |
|---|---|
| Purpose | Verify command-line support for listing recovery saves. |
| Precondition | One or more recovery entries exist. |
| Input / Action | Run `gradlew listRecoverySaves`. |
| Expected Result | Recovery entries are printed in the console. |
| Validation | Compare console output with recovery directory contents. |
| Quality Attribute | Operability |

### RS-10: Gradle restore recovery save task works

| Field | Description |
|---|---|
| Purpose | Verify command-line restore support. |
| Precondition | A recovery entry exists. |
| Input / Action | Run `gradlew restoreRecoverySave -PrecoveryIndex=1 -PtargetSlot=1`. |
| Expected Result | The selected recovery save is restored to the target slot. |
| Validation | Confirm restored save files exist in the target slot. |
| Quality Attribute | Reliability - Recoverability |

## 6. Review Checklist

| Check | Result |
|---|---|
| Deleted saves are preserved before deletion completes. | To be verified |
| Recovery entries are stored in a designated recovery directory. | To be verified |
| No more than five recovery entries are retained. | To be verified |
| Recovery entries can be listed. | To be verified |
| Recovery entries can be restored. | To be verified |
| Existing saves are not silently overwritten. | To be verified |
| Empty recovery state does not crash the game. | To be verified |
| Gradle recovery tasks work as documented. | To be verified |
| No hard-coded local absolute path is used. | To be verified |

## 7. Evidence to Collect for Video

The following evidence should be collected for the final video:

1. Code screenshot of `RecoveryManager.moveToRecovery()`.
2. Code screenshot showing the five-entry retention logic.
3. Code screenshot showing `Dungeon.java` calling the recovery logic before deletion.
4. UI screenshot showing the Recover option in the start scene.
5. UI screenshot showing the recovery save selection window.
6. Console screenshot of `listRecoverySaves`.
7. Console screenshot of `restoreRecoverySave`.
8. File explorer screenshot showing the recovery directory contains recovery entries.

## 8. Limitations

The current test plan focuses on recovery save behaviour and does not fully test long-term gameplay after a restored save is loaded. This is acceptable because the improvement target is recoverability of deleted saves rather than gameplay mechanics.

If the recovery implementation assumes that save-slot directories contain flat files only, this assumption should be documented as matching the current save structure of Shattered Pixel Dungeon 3.0.2.

## 9. Conclusion

The recovery saves improvement is expected to improve reliability by increasing recoverability after accidental save deletion. The tests above verify whether the implementation satisfies the predefined assignment requirement and whether the feature is integrated safely into the existing save deletion and start scene workflows.

## 10. Executed Test Evidence

The full recovery-saves test plan defines ten planned test cases. The table below records the verification activities that have been executed so far and can be used as evidence for the final video demonstration.

| Evidence ID | Command / Action | Result | Status |
|---|---|---|---|
| EV-01 | `.\gradlew.bat build` | Full project build completed successfully. Gradle reported `BUILD SUCCESSFUL` with 115 executed tasks. | PASS |
| EV-02 | `.\gradlew.bat listRecoverySaves` with an empty recovery directory | The task resolved the save directory and recovery folder correctly, then reported `No recovery saves found.` The task completed successfully. | PASS |
| EV-03 | `.\gradlew.bat restoreRecoverySave -PrecoveryIndex=1 -PtargetSlot=1` with no recovery entries | The task stopped with a clear error message: `No recovery saves found`. This is an expected controlled failure because no recovery entry existed. | PASS |
| EV-04 | Created a synthetic recovery entry at `recovery/1716000000000_game1` with `game.dat` and `depth1.dat` | The synthetic recovery entry was created successfully for controlled command-line testing. | PASS |
| EV-05 | `.\gradlew.bat listRecoverySaves` after creating the synthetic recovery entry | The task listed the recovery entry with index `1`, original slot `game1`, and a parsed deletion timestamp. | PASS |
| EV-06 | `.\gradlew.bat restoreRecoverySave -PrecoveryIndex=1 -PtargetSlot=1` | The task restored `1716000000000_game1` to game slot 1 and reported `2 file(s) copied`. | PASS |
| EV-07 | `dir "C:\Users\meiru\AppData\Roaming\.shatteredpixel\Shattered Pixel Dungeon\game1"` | File inspection confirmed that `game1` contains `game.dat` and `depth1.dat` after restoration. | PASS |
| EV-08 | `.\gradlew.bat restoreRecoverySave -PrecoveryIndex=1 -PtargetSlot=7` | The task rejected the invalid target slot with the message `targetSlot must be between 1 and 6 (got 7)`. This is an expected controlled failure. | PASS |
| EV-09 | `.\gradlew.bat restoreRecoverySave -PrecoveryIndex=99 -PtargetSlot=1` | The task rejected the invalid recovery index with the message `recoveryIndex 99 is out of range (1-1)`. This is an expected controlled failure. | PASS |
| EV-10 | `.\gradlew.bat deleteRecoverySave -PrecoveryIndex=1` | The task successfully deleted the recovery entry `1716000000000_game1`. | PASS |
| EV-11 | `.\gradlew.bat listRecoverySaves` after deletion | The task reported `No recovery saves found`, confirming that the recovery entry had been removed. | PASS |
| EV-12A | Code inspection of `RecoveryManager.java` | The code defines `MAX_RECOVERY_SAVES = 5`. After a deleted save is copied into the recovery directory, `moveToRecovery()` calls `pruneOldEntries()`. The pruning logic removes the oldest sorted recovery entry while the entry count is greater than five. | PASS |
| EV-12B | `.\gradlew.bat desktop:testRecoveryRetention` using a temporary verification harness | The harness created six synthetic recovery entries, invoked the recovery pruning logic, and confirmed that the count changed from 6 before pruning to 5 after pruning. The oldest entry was removed and Gradle reported `BUILD SUCCESSFUL`. | PASS |

### Notes on Expected Controlled Failures

Some verification commands intentionally used invalid input values. In those cases, `BUILD FAILED` is the expected result because the Gradle task correctly rejects unsafe or impossible recovery operations. These cases support the reliability and fault-tolerance claim because the implementation fails safely with clear error messages. 

### Notes on Synthetic Test 

EV-12B used a temporary local verification harness only for evidence collection. The harness and temporary Gradle task were not part of the production implementation and were removed after the screenshot was captured.
A synthetic recovery entry was used to verify the Gradle listing, restore, input validation, and deletion behaviours without requiring full gameplay progression. This validates the command-line recovery mechanism and file restoration logic. Full gameplay loading of a real recovered save should be covered separately by manual system testing if time permits.