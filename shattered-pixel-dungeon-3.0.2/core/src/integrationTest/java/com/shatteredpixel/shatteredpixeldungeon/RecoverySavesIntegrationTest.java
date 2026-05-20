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

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.watabou.utils.FileUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Integration tests for the recovery saves workflow.
 * Each test exercises multiple RecoveryManager methods together as a complete scenario.
 * See doc/recovery-saves/integration_test_plan.md for the full test plan.
 */
public class RecoverySavesIntegrationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void initGdx() {
        new HeadlessApplication(new ApplicationAdapter() {});
    }

    @Before
    public void setUp() {
        FileUtils.setDefaultFileProperties(
                com.badlogic.gdx.Files.FileType.Absolute,
                tempFolder.getRoot().getAbsolutePath() + "/"
        );
    }

    // IT-01
    @Test
    public void moveToRecoveryThenRestore_preservesFileContentIntact() throws IOException {
        byte[] originalContent = {0x01, 0x02, 0x03, 0x04, 0x05};
        File slotDir = createSlotWithContent(1, "game.dat", originalContent);

        RecoveryManager.moveToRecovery(1);
        deleteRecursive(slotDir);

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertEquals(1, entries.size());

        RecoveryManager.restoreEntry(entries.get(0), 1);

        File restoredFile = new File(new File(tempFolder.getRoot(), "game1"), "game.dat");
        assertTrue(restoredFile.exists());
        assertArrayEquals(originalContent, java.nio.file.Files.readAllBytes(restoredFile.toPath()));
    }

    // IT-02
    @Test
    public void restoreEntry_copiesSelectedRecoveryEntryToTargetSlot() throws IOException, InterruptedException {
        createSlotWithContent(1, "marker_slot1.dat", new byte[]{1});
        RecoveryManager.moveToRecovery(1);
        Thread.sleep(5);

        createSlotWithContent(2, "marker_slot2.dat", new byte[]{2});
        RecoveryManager.moveToRecovery(2);
        Thread.sleep(5);

        createSlotWithContent(3, "marker_slot3.dat", new byte[]{3});
        RecoveryManager.moveToRecovery(3);

        // Restore the second entry (originally game2) into slot 3.
        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertEquals(3, entries.size());
        RecoveryManager.restoreEntry(entries.get(1), 3);

        File slot3 = new File(tempFolder.getRoot(), "game3");
        assertTrue(new File(slot3, "marker_slot2.dat").exists());
        assertFalse(new File(slot3, "marker_slot1.dat").exists());
        assertFalse(new File(slot3, "marker_slot3.dat").exists());
    }

    // IT-03
    @Test
    public void restoreEntry_overwritesOccupiedTargetSlot() throws IOException {
        createSlotWithContent(1, "original.dat", new byte[]{10, 20});
        RecoveryManager.moveToRecovery(1);
        deleteRecursive(new File(tempFolder.getRoot(), "game1"));

        // Create a different occupant in slot 2.
        createSlotWithContent(2, "occupant.dat", new byte[]{99});

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        RecoveryManager.restoreEntry(entries.get(0), 2);

        File slot2 = new File(tempFolder.getRoot(), "game2");
        assertTrue(new File(slot2, "original.dat").exists());
        assertFalse("Occupant file should be gone after overwrite",
                new File(slot2, "occupant.dat").exists());
    }

    // IT-04
    @Test
    public void moveToRecovery_prunesOldestEntryWhenRetentionLimitExceeded() throws IOException, InterruptedException {
        String firstEntryName = null;

        for (int i = 0; i < 6; i++) {
            File slotDir = createSlotWithContent(1, "save_" + i + ".dat", new byte[]{(byte) i});
            RecoveryManager.moveToRecovery(1);

            if (i == 0) {
                ArrayList<String> snapshot = RecoveryManager.listRecoveryEntries();
                assertFalse(snapshot.isEmpty());
                firstEntryName = snapshot.get(0);
            }

            Thread.sleep(5);
            deleteRecursive(slotDir);
        }

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertEquals(RecoveryManager.MAX_RECOVERY_SAVES, entries.size());
        assertNotNull(firstEntryName);
        assertFalse("Oldest entry must have been pruned", entries.contains(firstEntryName));

        // Entries must still be in ascending timestamp order.
        for (int i = 0; i < entries.size() - 1; i++) {
            assertTrue(entries.get(i).compareTo(entries.get(i + 1)) < 0);
        }
    }

    // IT-05
    @Test
    public void restoreEntry_doesNotDeleteRecoveryEntry() throws IOException {
        createSlotWithContent(1, "game.dat", new byte[]{7});
        RecoveryManager.moveToRecovery(1);
        deleteRecursive(new File(tempFolder.getRoot(), "game1"));

        ArrayList<String> beforeRestore = RecoveryManager.listRecoveryEntries();
        assertEquals(1, beforeRestore.size());
        String entryName = beforeRestore.get(0);

        RecoveryManager.restoreEntry(entryName, 2);

        ArrayList<String> afterRestore = RecoveryManager.listRecoveryEntries();
        assertEquals(1, afterRestore.size());
        assertEquals(entryName, afterRestore.get(0));
    }

    private File createSlotWithContent(int slot, String fileName, byte[] content) throws IOException {
        File slotDir = new File(tempFolder.getRoot(), "game" + slot);
        slotDir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(slotDir, fileName))) {
            fos.write(content);
        }
        return slotDir;
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        f.delete();
    }
}
