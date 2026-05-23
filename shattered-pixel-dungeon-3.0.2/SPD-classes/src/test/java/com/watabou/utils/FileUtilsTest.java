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

package com.watabou.utils;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileUtilsTest {

	private static final String SAVE_MANIFEST_FILE = "save_checksums.dat";
	private static final String MANIFEST_VERSION = "version";
	private static final String MANIFEST_PATHS = "paths";
	private static final String MANIFEST_CHECKSUMS = "checksums";
	private static final String MANIFEST_OVERALL_CHECKSUM = "overall_checksum";

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private Files previousFiles;

	@Before
	public void setUp() {
		previousFiles = Gdx.files;
		Gdx.files = new LocalTestFiles(temporaryFolder.getRoot());
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
	}

	@After
	public void tearDown() {
		FileUtils.setDefaultFileProperties(null, "");
		Gdx.files = previousFiles;
	}

	@Test
	public void protectedBundleWriteCreatesSaveManifest() throws IOException {
		writeBundle("game1/game.dat", 1);

		File manifestFile = new File(temporaryFolder.getRoot(), SAVE_MANIFEST_FILE);
		assertTrue(manifestFile.isFile());
		assertTrue(manifestFile.length() > 0);

		Bundle manifest = manifest();
		assertEquals(1, manifest.getInt(MANIFEST_VERSION));
		assertTrue(manifestPaths().contains("game*/game.dat"));
	}

	@Test
	public void manifestStoresProtectedFilePathsCorrectly() throws IOException {
		writeRaw("game1/notes.txt", "ignored");
		writeRaw("game1/nested/depth2.dat", "ignored");
		writeRaw("orphan.txt", "ignored");

		writeBundle("game2/depth1.dat", 2);
		writeBundle("game1/game.dat", 1);
		writeBundle("rankings.dat", 3);

		List<String> paths = manifestPaths();
		assertEquals(Arrays.asList("game*/depth1.dat", "game*/game.dat", "rankings.dat"), paths);

		String[] checksums = manifest().getStringArray(MANIFEST_CHECKSUMS);
		assertNotNull(checksums);
		assertEquals(paths.size(), checksums.length);
		for (String checksum : checksums) {
			assertTrue(checksum.startsWith("bytes:sha256:"));
		}
	}

	@Test
	public void manifestExcludesRecoveryDirectoryFiles() throws IOException {
		writeBundle("game1/game.dat", 1);
		writeBundle("recovery/123_game1/game.dat", 2);

		List<String> paths = manifestPaths();
		assertEquals(Collections.singletonList("game*/game.dat"), paths);
		for (String path : paths) {
			assertFalse(path.contains("recovery"));
		}
	}

	@Test
	public void manifestUpdatesAfterSaveOverwriteOrDelete() throws IOException {
		writeBundle("game1/game.dat", 1);
		Bundle original = manifest();
		String originalOverallChecksum = original.getString(MANIFEST_OVERALL_CHECKSUM);
		String originalFileChecksum = original.getStringArray(MANIFEST_CHECKSUMS)[0];

		FileUtils.overwriteFile("game1/game.dat", 8);
		Bundle afterOverwrite = manifest();
		assertEquals(Collections.singletonList("game*/game.dat"), manifestPaths(afterOverwrite));
		assertNotEquals(originalFileChecksum, afterOverwrite.getStringArray(MANIFEST_CHECKSUMS)[0]);
		assertNotEquals(originalOverallChecksum, afterOverwrite.getString(MANIFEST_OVERALL_CHECKSUM));

		assertTrue(FileUtils.deleteFile("game1/game.dat"));
		Bundle afterDelete = manifest();
		assertTrue(manifestPaths(afterDelete).isEmpty());
		assertNotEquals(afterOverwrite.getString(MANIFEST_OVERALL_CHECKSUM), afterDelete.getString(MANIFEST_OVERALL_CHECKSUM));
	}

	private void writeBundle( String path, int value ) throws IOException {
		Bundle bundle = new Bundle();
		bundle.put("value", value);
		FileUtils.bundleToFile(path, bundle);
	}

	private void writeRaw( String path, String text ) throws IOException {
		File file = new File(temporaryFolder.getRoot(), path);
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			assertTrue(parent.mkdirs());
		}
		java.nio.file.Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private Bundle manifest() throws IOException {
		return FileUtils.bundleFromFile(SAVE_MANIFEST_FILE);
	}

	private List<String> manifestPaths() throws IOException {
		return manifestPaths(manifest());
	}

	private List<String> manifestPaths( Bundle manifest ) {
		String[] paths = manifest.getStringArray(MANIFEST_PATHS);
		assertNotNull(paths);
		return Arrays.asList(paths);
	}

	private static class LocalTestFiles implements Files {

		private final File root;

		private LocalTestFiles( File root ) {
			this.root = root;
		}

		@Override
		public FileHandle getFileHandle( String path, FileType type ) {
			return local(path);
		}

		@Override
		public FileHandle classpath( String path ) {
			return local(path);
		}

		@Override
		public FileHandle internal( String path ) {
			return local(path);
		}

		@Override
		public FileHandle external( String path ) {
			return local(path);
		}

		@Override
		public FileHandle absolute( String path ) {
			return new FileHandle(new File(path));
		}

		@Override
		public FileHandle local( String path ) {
			return new FileHandle(path.length() == 0 ? root : new File(root, path));
		}

		@Override
		public String getExternalStoragePath() {
			return root.getAbsolutePath();
		}

		@Override
		public boolean isExternalStorageAvailable() {
			return true;
		}

		@Override
		public String getLocalStoragePath() {
			return root.getAbsolutePath();
		}

		@Override
		public boolean isLocalStorageAvailable() {
			return true;
		}
	}
}
