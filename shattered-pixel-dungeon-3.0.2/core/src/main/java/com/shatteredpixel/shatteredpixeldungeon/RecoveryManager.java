/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2024 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon;

import com.badlogic.gdx.files.FileHandle;
import com.watabou.utils.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Intercepts game-slot deletion and preserves save files in a recovery directory.
 *
 * <p>When {@link #moveToRecovery(int)} is called, every file inside the slot's folder
 * is copied into {@code recovery/<timestamp>_<slotFolder>/} before the originals are
 * overwritten by {@link Dungeon#deleteGame}.  At most {@value #MAX_RECOVERY_SAVES}
 * recovery entries are kept; the oldest (lowest timestamp) is pruned automatically
 * whenever the limit is exceeded.
 *
 * <p>To restore a recovery save outside the game, run the Gradle tasks defined in
 * {@code build.gradle}:
 * <pre>
 *   ./gradlew listRecoverySaves
 *   ./gradlew restoreRecoverySave -PrecoveryIndex=N -PtargetSlot=M
 * </pre>
 */
public class RecoveryManager {

    public static final String RECOVERY_DIR = "recovery";
    public static final int MAX_RECOVERY_SAVES = 5;

    /**
     * Copies all files from the given game slot into a new timestamped recovery entry,
     * then removes the oldest entry when the total exceeds {@value #MAX_RECOVERY_SAVES}.
     *
     * <p>This must be called before the slot's files are overwritten or deleted.
     *
     * @param slot the game-slot number whose save files should be preserved
     */
    public static void moveToRecovery(int slot) {
        String sourceDir = GamesInProgress.gameFolder(slot);
        if (!FileUtils.dirExists(sourceDir)) {
            return;
        }

        // Timestamp prefix makes entries naturally sortable oldest-first.
        String entryName = System.currentTimeMillis() + "_" + sourceDir;
        copyGameDir(sourceDir, RECOVERY_DIR + "/" + entryName);
        pruneOldEntries();
    }

    /**
     * Returns the names of all valid recovery-entry directories inside {@value #RECOVERY_DIR},
     * sorted oldest-first (ascending by the timestamp prefix embedded in each name).
     *
     * @return list of entry names; empty if no recoveries exist
     */
    public static ArrayList<String> listRecoveryEntries() {
        ArrayList<String> entries = new ArrayList<>();
        for (String name : FileUtils.filesInDir(RECOVERY_DIR)) {
            FileHandle handle = FileUtils.getFileHandle(RECOVERY_DIR + "/" + name);
            if (handle.isDirectory() && isRecoveryEntryName(name)) {
                entries.add(name);
            }
        }
        Collections.sort(entries);
        return entries;
    }

    /**
     * Copies all files from the given recovery entry back into the specified game slot,
     * then invalidates the slot's in-memory state so the next {@link GamesInProgress#check}
     * reads fresh data from disk.
     *
     * <p>The recovery entry itself is NOT deleted; the player may want to restore again.
     *
     * @param entryName  a name returned by {@link #listRecoveryEntries()}
     * @param targetSlot destination game-slot number (1-based)
     */
    public static void restoreEntry(String entryName, int targetSlot) {
        // Clear any existing files in the target slot so the restore is a clean replacement,
        // matching the behaviour of the Gradle restoreRecoverySave task.
        FileUtils.deleteDir(GamesInProgress.gameFolder(targetSlot));
        copyGameDir(RECOVERY_DIR + "/" + entryName, GamesInProgress.gameFolder(targetSlot));
        GamesInProgress.setUnknown(targetSlot);
        try {
            FileUtils.updateSaveManifest();
        } catch (IOException e) {
            // Restore already happened; later save validation will still catch manifest issues.
        }
    }

    /**
     * Parses the original game-slot number from a recovery entry name.
     * Entry names are formatted as "{timestamp}_{slotFolder}", e.g. "1716000000000_game2".
     *
     * @return the slot number, or -1 if the entry name cannot be parsed
     */
    public static int slotFromEntryName(String entryName) {
        String[] parts = entryName.split("_", 2);
        if (parts.length < 2) return -1;
        String folder = parts[1];
        for (int i = 1; i <= GamesInProgress.MAX_SLOTS; i++) {
            if (GamesInProgress.gameFolder(i).equals(folder)) return i;
        }
        return -1;
    }

    private static boolean isRecoveryEntryName(String entryName) {
        String[] parts = entryName.split("_", 2);
        if (parts.length < 2) return false;
        try {
            return Long.parseLong(parts[0]) >= 0 && slotFromEntryName(entryName) != -1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Copies every non-directory file from srcDir into dstDir (flat, no recursion needed
    // because game-slot folders contain only flat files).
    private static void copyGameDir(String srcDir, String dstDir) {
        for (String fileName : FileUtils.filesInDir(srcDir)) {
            FileHandle src = FileUtils.getFileHandle(srcDir + "/" + fileName);
            if (!src.isDirectory()) {
                src.copyTo(FileUtils.getFileHandle(dstDir + "/" + fileName));
            }
        }
    }

    // Deletes the oldest entry when there are more than MAX_RECOVERY_SAVES.
    private static void pruneOldEntries() {
        ArrayList<String> entries = listRecoveryEntries();
        while (entries.size() > MAX_RECOVERY_SAVES) {
            FileUtils.deleteDir(RECOVERY_DIR + "/" + entries.remove(0));
        }
    }
}
