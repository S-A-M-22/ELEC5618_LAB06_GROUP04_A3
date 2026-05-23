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
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Unit tests for RecoveryManager — each test exercises one method in isolation.
 * See doc/recovery-saves/unit_test_plan.md for the full test plan.
 */
public class RecoveryManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void initGdx() {
        new HeadlessApplication(new ApplicationAdapter() {});
    }

    @Before
    public void setUp() {
        FileUtils.setDefaultFileProperties(
                Files.FileType.Absolute,
                tempFolder.getRoot().getAbsolutePath() + "/"
        );
    }

    @Test
    public void constructor_canBeInstantiated() {
        assertNotNull(new RecoveryManager());
    }

    // UT-01
    @Test
    public void slotFromEntryName_validEntry_returnsCorrectSlot() {
        assertEquals(1, RecoveryManager.slotFromEntryName("1716000000000_game1"));
    }

    // UT-02
    @Test
    public void slotFromEntryName_nameWithoutUnderscore_returnsNegativeOne() {
        assertEquals(-1, RecoveryManager.slotFromEntryName("nounderscore"));
    }

    // UT-03
    @Test
    public void slotFromEntryName_unknownSlotFolder_returnsNegativeOne() {
        assertEquals(-1, RecoveryManager.slotFromEntryName("1716000000000_game99"));
    }

    // UT-04
    @Test
    public void listRecoveryEntries_noRecoveryDirectory_returnsEmptyList() {
        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    // UT-05
    @Test
    public void listRecoveryEntries_multipleEntries_sortedOldestFirst() throws IOException {
        File recoveryDir = tempFolder.newFolder("recovery");
        new File(recoveryDir, "300_game1").mkdir();
        new File(recoveryDir, "100_game1").mkdir();
        new File(recoveryDir, "200_game1").mkdir();

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();

        assertEquals(3, entries.size());
        assertEquals("100_game1", entries.get(0));
        assertEquals("200_game1", entries.get(1));
        assertEquals("300_game1", entries.get(2));
    }

    @Test
    public void listRecoveryEntries_ignoresFilesInRecoveryDirectory() throws IOException {
        File recoveryDir = tempFolder.newFolder("recovery");
        new File(recoveryDir, "100_game1").mkdir();
        assertTrue(new File(recoveryDir, "200_game2.txt").createNewFile());

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();

        assertEquals(1, entries.size());
        assertEquals("100_game1", entries.get(0));
    }

    @Test
    public void listRecoveryEntries_ignoresMalformedEntryDirectories() throws IOException {
        File recoveryDir = tempFolder.newFolder("recovery");
        new File(recoveryDir, "100_game1").mkdir();
        new File(recoveryDir, "nounderscore").mkdir();
        new File(recoveryDir, "abc_game1").mkdir();
        new File(recoveryDir, "-100_game1").mkdir();
        new File(recoveryDir, "200_game99").mkdir();

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();

        assertEquals(1, entries.size());
        assertEquals("100_game1", entries.get(0));
    }

    // UT-06
    @Test
    public void moveToRecovery_nonExistentSourceSlot_doesNothing() {
        RecoveryManager.moveToRecovery(1);

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertTrue(entries.isEmpty());
    }

    // UT-07
    @Test
    public void moveToRecovery_existingSlot_createsRecoveryEntryWithFiles() throws IOException {
        File slotDir = new File(tempFolder.getRoot(), "game1");
        slotDir.mkdirs();
        new File(slotDir, "game.dat").createNewFile();
        new File(slotDir, "depth1.dat").createNewFile();

        RecoveryManager.moveToRecovery(1);

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertEquals(1, entries.size());

        File entryDir = new File(new File(tempFolder.getRoot(), "recovery"), entries.get(0));
        assertTrue(new File(entryDir, "game.dat").exists());
        assertTrue(new File(entryDir, "depth1.dat").exists());
    }

    @Test
    public void moveToRecovery_existingSlot_skipsNestedDirectories() throws IOException {
        File slotDir = new File(tempFolder.getRoot(), "game1");
        File nestedDir = new File(slotDir, "nested");
        assertTrue(nestedDir.mkdirs());
        assertTrue(new File(slotDir, "game.dat").createNewFile());
        assertTrue(new File(nestedDir, "ignored.dat").createNewFile());

        RecoveryManager.moveToRecovery(1);

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertEquals(1, entries.size());

        File entryDir = new File(new File(tempFolder.getRoot(), "recovery"), entries.get(0));
        assertTrue(new File(entryDir, "game.dat").exists());
        assertFalse(new File(entryDir, "nested").exists());
    }

    @Test
    public void restoreEntry_ignoresManifestUpdateIOException() throws IOException {
        try (MockedStatic<FileUtils> fileUtils = Mockito.mockStatic(FileUtils.class)) {
            fileUtils.when(() -> FileUtils.deleteDir("game1")).thenReturn(false);
            fileUtils.when(() -> FileUtils.filesInDir("recovery/100_game1")).thenReturn(new ArrayList<String>());
            fileUtils.when(FileUtils::updateSaveManifest).thenThrow(new IOException("manifest update failed"));

            RecoveryManager.restoreEntry("100_game1", 1);

            fileUtils.verify(() -> FileUtils.updateSaveManifest());
        }
    }

    // UT-08
    @Test
    public void moveToRecovery_prunesOldestEntryWhenMoreThanFiveEntriesExist() throws IOException, InterruptedException {
        String firstEntryName = null;

        for (int i = 0; i < 6; i++) {
            File slotDir = new File(tempFolder.getRoot(), "game1");
            slotDir.mkdirs();
            new File(slotDir, "game.dat").createNewFile();

            RecoveryManager.moveToRecovery(1);

            if (i == 0) {
                ArrayList<String> after = RecoveryManager.listRecoveryEntries();
                if (!after.isEmpty()) {
                    firstEntryName = after.get(0);
                }
            }

            // Small delay so each entry gets a distinct millisecond timestamp.
            Thread.sleep(5);

            deleteRecursive(slotDir);
        }

        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        assertEquals(RecoveryManager.MAX_RECOVERY_SAVES, entries.size());
        if (firstEntryName != null) {
            assertFalse("Oldest entry should have been pruned", entries.contains(firstEntryName));
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            for (File child : f.listFiles()) deleteRecursive(child);
        }
        f.delete();
    }
}
