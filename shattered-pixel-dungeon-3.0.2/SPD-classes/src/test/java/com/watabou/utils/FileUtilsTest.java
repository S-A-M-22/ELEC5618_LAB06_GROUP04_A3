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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

	@Test
	public void cleanTempFilesReturnsFalseWhenNothingWasRecovered() throws IOException {
		writeRaw("present.dat", "abc");

		assertFalse(FileUtils.cleanTempFiles());
		assertTrue(new File(temporaryFolder.getRoot(), "present.dat").isFile());
	}

	@Test
	public void fileDirectoryHelpersHandleEmptyFilesDirectoriesAndNullHandles() throws IOException {
		writeRaw("present.dat", "abc");
		writeRaw("empty.dat", "");
		assertTrue(new File(temporaryFolder.getRoot(), "dir").mkdirs());

		assertTrue(FileUtils.fileExists("present.dat"));
		assertFalse(FileUtils.fileExists("missing.dat"));
		assertFalse(FileUtils.fileExists("empty.dat"));
		assertFalse(FileUtils.fileExists("dir"));
		assertFalse(FileUtils.dirExists("missing-dir"));
		assertFalse(FileUtils.dirExists("present.dat"));
		assertTrue(FileUtils.dirExists("dir"));
		assertFalse(FileUtils.filesInDir("present.dat").contains("anything"));
		assertTrue(FileUtils.deleteDir("dir"));

		Gdx.files = new NullHandleFiles(temporaryFolder.getRoot());
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
		assertFalse(FileUtils.deleteDir("null-handle"));
		assertTrue(FileUtils.filesInDir("null-handle").isEmpty());
	}

	@Test
	public void cleanTempFilesCombinesRecursiveRecoveryResultsAcrossDirectories() throws IOException {
		assertTrue(new File(temporaryFolder.getRoot(), "a").mkdirs());
		assertTrue(new File(temporaryFolder.getRoot(), "z").mkdirs());
		writeSignedBundle("a/recovered.dat.tmp", 1);
		writeRaw("z/normal.txt", "kept");

		assertTrue(FileUtils.cleanTempFiles());

		assertFalse(new File(temporaryFolder.getRoot(), "a/recovered.dat.tmp").exists());
		assertTrue(new File(temporaryFolder.getRoot(), "z/normal.txt").isFile());
	}

	@Test
	public void fileActionsSwallowManifestRefreshIoFailuresAfterPrimaryAction() throws IOException {
		writeSignedBundle("recovered.dat.tmp", 1);
		writeRaw("delete-me.dat", "delete");
		writeRaw("overwrite-me.dat", "overwrite");

		Gdx.files = new ManifestChecksumFailingFiles(temporaryFolder.getRoot());
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");

		assertTrue(FileUtils.cleanTempFiles());
		assertFalse(new File(temporaryFolder.getRoot(), "recovered.dat.tmp").exists());

		assertTrue(FileUtils.deleteFile("delete-me.dat"));
		assertFalse(new File(temporaryFolder.getRoot(), "delete-me.dat").exists());

		FileUtils.overwriteFile("overwrite-me.dat", 4);
		assertEquals(4, new File(temporaryFolder.getRoot(), "overwrite-me.dat").length());
	}

	@Test
	public void getFileHandleReturnsNullForUnmappedFileTypeSwitchEntry() throws Exception {
		Class<?> switchMapClass = Class.forName(FileUtils.class.getName() + "$2");
		Field switchMapField = switchMapClass.getDeclaredField("$SwitchMap$com$badlogic$gdx$Files$FileType");
		switchMapField.setAccessible(true);
		int[] switchMap = (int[])switchMapField.get(null);
		int ordinal = Files.FileType.Local.ordinal();
		int previousMapping = switchMap[ordinal];
		switchMap[ordinal] = 0;
		try {
			assertNull(FileUtils.getFileHandle(Files.FileType.Local, "ignored"));
		} finally {
			switchMap[ordinal] = previousMapping;
		}
	}

	@Test
	public void bundleToManifestFileDoesNotRecursivelyRefreshManifest() throws IOException {
		Bundle manual = new Bundle();
		manual.put(MANIFEST_VERSION, 7);
		manual.put(MANIFEST_PATHS, new String[]{"manual.dat"});
		manual.put(MANIFEST_CHECKSUMS, new String[]{"bytes:sha256:manual"});
		manual.put(MANIFEST_OVERALL_CHECKSUM, "sha256:manual");

		FileUtils.bundleToFile(SAVE_MANIFEST_FILE, manual);

		assertEquals(7, manifest().getInt(MANIFEST_VERSION));
		assertEquals(Collections.singletonList("manual.dat"), manifestPaths());
	}

	@Test
	public void updateSaveManifestNoopsWhenRootIsMissingOrNull() throws IOException {
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "missing/");
		FileUtils.updateSaveManifest();
		assertFalse(new File(temporaryFolder.getRoot(), "missing/" + SAVE_MANIFEST_FILE).exists());

		Gdx.files = new NullHandleFiles(temporaryFolder.getRoot());
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
		FileUtils.updateSaveManifest();
	}

	@Test
	public void emptyOrDirectoryManifestKeepsLegacyReadsCompatible() throws IOException {
		writeSignedBundle("game1/game.dat", 1);
		writeRaw(SAVE_MANIFEST_FILE, "");

		assertEquals(1, FileUtils.bundleFromFile("game1/game.dat").getInt("value"));

		assertTrue(new File(temporaryFolder.getRoot(), SAVE_MANIFEST_FILE).delete());
		assertTrue(new File(temporaryFolder.getRoot(), SAVE_MANIFEST_FILE).mkdirs());

		assertEquals(1, FileUtils.bundleFromFile("game1/game.dat").getInt("value"));
	}

	@Test
	public void malformedManifestMetadataRejectsProtectedReads() throws IOException {
		writeSignedBundle("game1/game.dat", 1);

		Bundle emptyOverall = new Bundle();
		emptyOverall.put(MANIFEST_PATHS, new String[]{"game*/game.dat"});
		emptyOverall.put(MANIFEST_CHECKSUMS, new String[]{"bytes:sha256:abc"});
		emptyOverall.put(MANIFEST_OVERALL_CHECKSUM, "");
		FileUtils.bundleToFile(SAVE_MANIFEST_FILE, emptyOverall);
		assertBundleRejected("game1/game.dat");

		Bundle missingPaths = new Bundle();
		missingPaths.put(MANIFEST_CHECKSUMS, new String[]{"bytes:sha256:abc"});
		missingPaths.put(MANIFEST_OVERALL_CHECKSUM, "sha256:not-real-but-non-empty");
		FileUtils.bundleToFile(SAVE_MANIFEST_FILE, missingPaths);
		assertBundleRejected("game1/game.dat");

		Bundle missingChecksums = new Bundle();
		missingChecksums.put(MANIFEST_PATHS, new String[]{"game*/game.dat"});
		missingChecksums.put(MANIFEST_OVERALL_CHECKSUM, "sha256:not-real-but-non-empty");
		FileUtils.bundleToFile(SAVE_MANIFEST_FILE, missingChecksums);
		assertBundleRejected("game1/game.dat");
	}

	@Test
	public void manifestPathHelpersClassifyOnlyActiveTopLevelGameFiles() throws Exception {
		Method isProtected = FileUtils.class.getDeclaredMethod("isManifestProtectedFile", String.class);
		Method normalize = FileUtils.class.getDeclaredMethod("normalizeManifestPath", String.class);
		isProtected.setAccessible(true);
		normalize.setAccessible(true);

		assertTrue((Boolean)isProtected.invoke(null, "game1/depth1.dat"));
		assertFalse((Boolean)isProtected.invoke(null, "game1/nested/depth2.dat"));
		assertFalse((Boolean)isProtected.invoke(null, "game/depth1.dat"));
		assertFalse((Boolean)isProtected.invoke(null, "other/depth1.dat"));
		assertEquals("game*/depth1.dat", normalize.invoke(null, "game1/depth1.dat"));
		assertEquals("other/depth1.dat", normalize.invoke(null, "other/depth1.dat"));
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

	private void writeSignedBundle( String path, int value ) throws IOException {
		File file = new File(temporaryFolder.getRoot(), path);
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			assertTrue(parent.mkdirs());
		}
		Bundle bundle = new Bundle();
		bundle.put("value", value);
		FileOutputStream output = new FileOutputStream(file);
		try {
			assertTrue(Bundle.write(bundle, output));
		} finally {
			output.close();
		}
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

	private void assertBundleRejected( String path ) throws IOException {
		try {
			FileUtils.bundleFromFile(path);
			fail("Expected bundle read to be rejected");
		} catch (IOException expected) {
			// expected
		}
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

	private static class NullHandleFiles extends LocalTestFiles {

		private NullHandleFiles( File root ) {
			super(root);
		}

		@Override
		public FileHandle local( String path ) {
			if (path.length() == 0 || "null-handle".equals(path)) {
				return null;
			}
			return super.local(path);
		}
	}

	private static class ManifestChecksumFailingFiles extends LocalTestFiles {

		private ManifestChecksumFailingFiles( File root ) {
			super(root);
		}

		@Override
		public FileHandle local( String path ) {
			if (path.length() == 0) {
				return new ManifestChecksumFailingRootHandle(super.local(path).file());
			}
			return super.local(path);
		}
	}

	private static class ManifestChecksumFailingRootHandle extends FileHandle {

		private ManifestChecksumFailingRootHandle( File file ) {
			super(file);
		}

		@Override
		public FileHandle[] list() {
			FileHandle[] original = super.list();
			FileHandle[] withFailingFile = Arrays.copyOf(original, original.length + 1);
			withFailingFile[original.length] = new ChecksumFailingFileHandle(new File(file(), "bad.dat"));
			return withFailingFile;
		}
	}

	private static class ChecksumFailingFileHandle extends FileHandle {

		private ChecksumFailingFileHandle( File file ) {
			super(file);
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public boolean isDirectory() {
			return false;
		}

		@Override
		public long length() {
			return 1;
		}

		@Override
		public InputStream read() {
			return new InputStream() {
				@Override
				public int read() throws IOException {
					throw new IOException("checksum read failed");
				}
			};
		}
	}
}
