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

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileUtilsSaveManifestTest {

	private static final String MANIFEST = "save_checksums.dat";

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private File root;
	private Files previousFiles;
	private Application previousApp;

	@Before
	public void setUp() throws IOException {
		root = temporaryFolder.newFolder("save-root");
		previousFiles = Gdx.files;
		previousApp = Gdx.app;
		Gdx.files = new LocalTestFiles(root);
		Gdx.app = null;
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
		GameSettings.set(null);
	}

	@After
	public void tearDown() {
		Gdx.files = previousFiles;
		Gdx.app = previousApp;
		GameSettings.set(null);
	}

	@Test
	public void bundleWritesCreateManifestAndDetectExternalTampering() throws IOException {
		mkdirs("game1");
		Bundle original = bundle("depth", 1);

		FileUtils.bundleToFile("game1/game.dat", original);

		Bundle manifest = FileUtils.bundleFromFile(MANIFEST);
		assertEquals(1, manifest.getInt("version"));
		assertArrayEquals(new String[]{"game*/game.dat"}, manifest.getStringArray("paths"));
		assertTrue(manifest.getStringArray("checksums")[0].startsWith("bytes:sha256:"));
		assertTrue(manifest.getString("overall_checksum").startsWith("sha256:"));
		assertEquals(1, FileUtils.bundleFromFile("game1/game.dat").getInt("depth"));

		writeText("game1/game.dat", "{\"depth\":2}");

		try {
			FileUtils.bundleFromFile("game1/game.dat");
			fail("Expected manifest mismatch to reject externally modified save");
		} catch (IOException expected) {
			// expected
		}

		FileUtils.updateSaveManifest();
		assertEquals(2, FileUtils.bundleFromFile("game1/game.dat").getInt("depth"));
		assertTrue(FileUtils.bundleFromFile(MANIFEST).getStringArray("checksums")[0].startsWith("bytes:sha256:"));
	}

	@Test
	public void manifestIncludesOnlyProtectedSaveFilesInStableOrder() throws IOException {
		mkdirs("game1");
		mkdirs("game1/nested");
		mkdirs("game");
		mkdirs("gameA");

		writeSignedBundle("game1/depth1.dat", bundle("depth", 1));
		writeSignedBundle("game1/depth1.dat.tmp", bundle("depth", 99));
		writeSignedBundle("game1/nested/depth2.dat", bundle("depth", 2));
		writeSignedBundle("game/depth1.dat", bundle("depth", 3));
		writeSignedBundle("gameA/depth1.dat", bundle("depth", 4));
		writeText("rankings.dat", "{\"score\":1}");
		writeText(GameSettings.DEFAULT_PREFS_FILE, "<settings/>");
		writeText("notes.txt", "ignore");
		writeText(MANIFEST, "old manifest");

		FileUtils.updateSaveManifest();

		Bundle manifest = FileUtils.bundleFromFile(MANIFEST);
		assertArrayEquals(new String[]{
				"game*/depth1.dat",
				"rankings.dat",
				GameSettings.DEFAULT_PREFS_FILE
		}, manifest.getStringArray("paths"));
		assertTrue(manifest.getStringArray("checksums")[0].startsWith("bytes:sha256:"));
		assertTrue(manifest.getStringArray("checksums")[1].startsWith("bytes:sha256:"));
		assertTrue(manifest.getStringArray("checksums")[2].startsWith("bytes:sha256:"));
	}

	@Test
	public void randomByteFlipsInAllGameSaveFilesAreRejected() throws IOException {
		String[] gameSaveFiles = {
				"game1/game.dat",
				"game1/depth1.dat",
				"game1/depth2-branch1.dat"
		};

		for (int i = 0; i < gameSaveFiles.length; i++) {
			useFreshRoot("byte-flip-" + i);
			writeGameSaveSet();

			flipRandomByte(gameSaveFiles[i]);

			assertBundleRejected(gameSaveFiles[i]);
		}
	}

	@Test
	public void renamingGameRootFolderKeepsManifestValid() throws IOException {
		writeGameSaveSet();

		assertTrue(file("game1").renameTo(file("game2")));

		assertEquals(1, FileUtils.bundleFromFile("game2/game.dat").getInt("depth"));
		assertEquals(2, FileUtils.bundleFromFile("game2/depth1.dat").getInt("depth"));
		assertEquals(3, FileUtils.bundleFromFile("game2/depth2-branch1.dat").getInt("depth"));
	}

	@Test
	public void renamingProtectedGameSaveFileIsRejected() throws IOException {
		mkdirs("game1");
		FileUtils.bundleToFile("game1/game.dat", bundle("depth", 1));

		assertTrue(file("game1/game.dat").renameTo(file("game1/depth1.dat")));

		assertBundleRejected("game1/depth1.dat");
	}

	@Test
	public void movingRootSaveFileIntoGameFolderIsRejected() throws IOException {
		mkdirs("game1");
		FileUtils.bundleToFile("rankings.dat", bundle("score", 10));

		assertTrue(file("rankings.dat").renameTo(file("game1/game.dat")));

		assertBundleRejected("game1/game.dat");
	}

	@Test
	public void blindJsonValueEditWithStaleChecksumIsRejected() throws IOException {
		mkdirs("game1");
		writeSignedBundle("game1/game.dat", bundle("gold", 50), false);
		FileUtils.updateSaveManifest();

		String json = readText("game1/game.dat");
		assertTrue(json.contains("\"gold\":50"));
		writeText("game1/game.dat", json.replace("\"gold\":50", "\"gold\":99"));

		assertBundleRejected("game1/game.dat");
	}

	@Test
	public void deleteAndOverwriteRefreshManifest() throws IOException {
		FileUtils.bundleToFile("rankings.dat", bundle("score", 10));
		assertArrayEquals(new String[]{"rankings.dat"}, FileUtils.bundleFromFile(MANIFEST).getStringArray("paths"));

		assertTrue(FileUtils.deleteFile("rankings.dat"));
		assertFalse(FileUtils.deleteFile("missing.dat"));
		assertEquals(0, FileUtils.bundleFromFile(MANIFEST).getStringArray("paths").length);

		FileUtils.overwriteFile(GameSettings.DEFAULT_PREFS_FILE, 3);

		Bundle manifest = FileUtils.bundleFromFile(MANIFEST);
		assertArrayEquals(new String[]{GameSettings.DEFAULT_PREFS_FILE}, manifest.getStringArray("paths"));
		assertTrue(manifest.getStringArray("checksums")[0].startsWith("bytes:sha256:"));
	}

	@Test
	public void cleanTempFilesRefreshesManifestAfterRecoveringNewerTempFile() throws Exception {
		FileUtils.bundleToFile("rankings.dat", bundle("score", 1));
		File original = file("rankings.dat");
		File temp = file("rankings.dat.tmp");
		writeSignedBundle(temp, bundle("score", 2));
		assertTrue(original.setLastModified(System.currentTimeMillis() - 5000));
		assertTrue(temp.setLastModified(System.currentTimeMillis() + 5000));

		assertTrue(FileUtils.cleanTempFiles());

		assertFalse(temp.exists());
		assertEquals(2, FileUtils.bundleFromFile("rankings.dat").getInt("score"));
		assertArrayEquals(new String[]{"rankings.dat"}, FileUtils.bundleFromFile(MANIFEST).getStringArray("paths"));
	}

	@Test
	public void updateManifestNoopsWhenFileUtilsIsNotReadyOrAlreadyUpdating() throws Exception {
		Field updatingManifest = FileUtils.class.getDeclaredField("updatingManifest");
		updatingManifest.setAccessible(true);

		FileUtils.setDefaultFileProperties(null, "");
		FileUtils.updateSaveManifest();
		assertFalse(file(MANIFEST).exists());

		Gdx.files = new LocalTestFiles(temporaryFolder.newFile("not-a-directory"));
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
		FileUtils.updateSaveManifest();
		assertFalse(file(MANIFEST).exists());

		Gdx.files = new LocalTestFiles(root);
		updatingManifest.setBoolean(null, true);
		try {
			FileUtils.updateSaveManifest();
			assertFalse(file(MANIFEST).exists());
		} finally {
			updatingManifest.setBoolean(null, false);
		}
	}

	@Test
	public void gameSettingsWritesRefreshManifestForEachSupportedType() throws IOException {
		PersistingPreferences prefs = new PersistingPreferences(file(GameSettings.DEFAULT_PREFS_FILE));
		GameSettings.set(prefs);

		GameSettings.put("int", 1);
		GameSettings.put("long", 2L);
		GameSettings.put("boolean", true);
		GameSettings.put("string", "value");

		assertEquals(4, prefs.flushCount);
		assertEquals(1, prefs.getInteger("int"));
		assertEquals(2L, prefs.getLong("long"));
		assertTrue(prefs.getBoolean("boolean"));
		assertEquals("value", prefs.getString("string"));
		assertArrayEquals(new String[]{GameSettings.DEFAULT_PREFS_FILE},
				FileUtils.bundleFromFile(MANIFEST).getStringArray("paths"));
	}

	@Test
	public void gameSettingsFlushDoesNotPropagateManifestUpdateFailures() {
		PersistingPreferences prefs = new PersistingPreferences(file(GameSettings.DEFAULT_PREFS_FILE));
		GameSettings.set(prefs);
		Gdx.files = new ThrowingFiles();

		PrintStream originalErr = System.err;
		try {
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
			GameSettings.put("int", 1);
		} finally {
			System.setErr(originalErr);
		}

		assertEquals(1, prefs.flushCount);
	}

	private static Bundle bundle( String key, int value ) {
		Bundle bundle = new Bundle();
		bundle.put(key, value);
		return bundle;
	}

	private void mkdirs( String relative ) {
		assertTrue(file(relative).mkdirs());
	}

	private void useFreshRoot( String name ) throws IOException {
		root = temporaryFolder.newFolder(name);
		Gdx.files = new LocalTestFiles(root);
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
	}

	private void writeGameSaveSet() throws IOException {
		mkdirs("game1");
		FileUtils.bundleToFile("game1/game.dat", bundle("depth", 1));
		FileUtils.bundleToFile("game1/depth1.dat", bundle("depth", 2));
		FileUtils.bundleToFile("game1/depth2-branch1.dat", bundle("depth", 3));
	}

	private File file( String relative ) {
		return new File(root, relative);
	}

	private void writeText( String relative, String text ) throws IOException {
		writeText(file(relative), text);
	}

	private void writeText( File file, String text ) throws IOException {
		File parent = file.getParentFile();
		if (parent != null) {
			assertTrue(parent.exists() || parent.mkdirs());
		}
		FileOutputStream output = new FileOutputStream(file);
		try {
			output.write(text.getBytes(StandardCharsets.UTF_8));
		} finally {
			output.close();
		}
	}

	private String readText( String relative ) throws IOException {
		FileInputStream input = new FileInputStream(file(relative));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			byte[] buffer = new byte[1024];
			int read;
			while ((read = input.read(buffer)) != -1) {
				output.write(buffer, 0, read);
			}
		} finally {
			input.close();
		}
		return output.toString("UTF-8");
	}

	private void writeSignedBundle( String relative, Bundle bundle ) throws IOException {
		writeSignedBundle(file(relative), bundle, true);
	}

	private void writeSignedBundle( String relative, Bundle bundle, boolean compressed ) throws IOException {
		writeSignedBundle(file(relative), bundle, compressed);
	}

	private void writeSignedBundle( File file, Bundle bundle ) throws IOException {
		writeSignedBundle(file, bundle, true);
	}

	private void writeSignedBundle( File file, Bundle bundle, boolean compressed ) throws IOException {
		File parent = file.getParentFile();
		if (parent != null) {
			assertTrue(parent.exists() || parent.mkdirs());
		}
		FileOutputStream output = new FileOutputStream(file);
		try {
			assertTrue(Bundle.write(bundle, output, compressed));
		} finally {
			output.close();
		}
	}

	private void flipRandomByte( String relative ) throws IOException {
		RandomAccessFile target = new RandomAccessFile(file(relative), "rw");
		try {
			long length = target.length();
			assertTrue(length > 0 && length <= Integer.MAX_VALUE);
			int position = new Random(0x5618L + relative.hashCode()).nextInt((int)length);
			target.seek(position);
			int original = target.read();
			target.seek(position);
			target.write(original ^ 0x01);
		} finally {
			target.close();
		}
	}

	private void assertBundleRejected( String relative ) {
		PrintStream originalErr = System.err;
		try {
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
			try {
				FileUtils.bundleFromFile(relative);
				fail("Expected save manifest or bundle checksum to reject " + relative);
			} catch (IOException expected) {
				// expected
			}
		} finally {
			System.setErr(originalErr);
		}
	}

	private static class LocalTestFiles implements Files {

		private final File root;

		private LocalTestFiles( File root ) {
			this.root = root;
		}

		@Override
		public FileHandle getFileHandle( String path, FileType type ) {
			switch (type) {
				case Classpath:
					return classpath(path);
				case Internal:
					return internal(path);
				case External:
					return external(path);
				case Absolute:
					return absolute(path);
				case Local:
					return local(path);
				default:
					return null;
			}
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
			File requested = new File(path);
			if (path.length() == 0) {
				return new FileHandle(root);
			}
			return new FileHandle(requested.isAbsolute() ? requested : new File(root, path));
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

	private static class ThrowingFiles extends LocalTestFiles {

		private ThrowingFiles() {
			super(new File("."));
		}

		@Override
		public FileHandle local( String path ) {
			throw new GdxRuntimeException("manifest update failed");
		}
	}

	private static class PersistingPreferences implements Preferences {

		private final File file;
		private final HashMap<String, Object> values = new HashMap<>();
		private int flushCount;

		private PersistingPreferences( File file ) {
			this.file = file;
		}

		@Override
		public Preferences putBoolean( String key, boolean val ) {
			values.put(key, val);
			return this;
		}

		@Override
		public Preferences putInteger( String key, int val ) {
			values.put(key, val);
			return this;
		}

		@Override
		public Preferences putLong( String key, long val ) {
			values.put(key, val);
			return this;
		}

		@Override
		public Preferences putFloat( String key, float val ) {
			values.put(key, val);
			return this;
		}

		@Override
		public Preferences putString( String key, String val ) {
			values.put(key, val);
			return this;
		}

		@Override
		public Preferences put( Map<String, ?> vals ) {
			values.putAll(vals);
			return this;
		}

		@Override
		public boolean getBoolean( String key ) {
			return (Boolean)values.get(key);
		}

		@Override
		public int getInteger( String key ) {
			return (Integer)values.get(key);
		}

		@Override
		public long getLong( String key ) {
			return (Long)values.get(key);
		}

		@Override
		public float getFloat( String key ) {
			return (Float)values.get(key);
		}

		@Override
		public String getString( String key ) {
			return (String)values.get(key);
		}

		@Override
		public boolean getBoolean( String key, boolean defValue ) {
			return values.containsKey(key) ? getBoolean(key) : defValue;
		}

		@Override
		public int getInteger( String key, int defValue ) {
			return values.containsKey(key) ? getInteger(key) : defValue;
		}

		@Override
		public long getLong( String key, long defValue ) {
			return values.containsKey(key) ? getLong(key) : defValue;
		}

		@Override
		public float getFloat( String key, float defValue ) {
			return values.containsKey(key) ? getFloat(key) : defValue;
		}

		@Override
		public String getString( String key, String defValue ) {
			return values.containsKey(key) ? getString(key) : defValue;
		}

		@Override
		public Map<String, ?> get() {
			return values;
		}

		@Override
		public boolean contains( String key ) {
			return values.containsKey(key);
		}

		@Override
		public void clear() {
			values.clear();
		}

		@Override
		public void remove( String key ) {
			values.remove(key);
		}

		@Override
		public void flush() {
			flushCount++;
			try {
				FileOutputStream output = new FileOutputStream(file);
				try {
					output.write(values.toString().getBytes(StandardCharsets.UTF_8));
				} finally {
					output.close();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
