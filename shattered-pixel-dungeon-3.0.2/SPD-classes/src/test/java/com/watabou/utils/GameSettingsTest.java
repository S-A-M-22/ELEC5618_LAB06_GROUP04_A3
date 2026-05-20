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
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameSettingsTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private Files previousFiles;
	private RecordingPreferences prefs;

	@Before
	public void setUp() {
		previousFiles = Gdx.files;
		Gdx.files = new LocalTestFiles(temporaryFolder.getRoot());
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
		prefs = new RecordingPreferences();
		GameSettings.set(prefs);
	}

	@After
	public void tearDown() {
		GameSettings.set(null);
		Gdx.files = previousFiles;
	}

	@Test
	public void containsAndGettersReturnStoredOrDefaultValues() {
		assertFalse(GameSettings.contains("missing"));

		prefs.putInteger("int", 3);
		prefs.putLong("long", 4L);
		prefs.putBoolean("boolean", true);
		prefs.putString("string", "value");

		assertTrue(GameSettings.contains("int"));
		assertEquals(3, GameSettings.getInt("int", 0));
		assertEquals(9, GameSettings.getInt("missing_int", 9));
		assertEquals(4L, GameSettings.getLong("long", 0L));
		assertEquals(8L, GameSettings.getLong("missing_long", 8L));
		assertTrue(GameSettings.getBoolean("boolean", false));
		assertFalse(GameSettings.getBoolean("missing_boolean", false));
		assertEquals("value", GameSettings.getString("string", "default"));
		assertEquals("default", GameSettings.getString("missing_string", "default"));
	}

	@Test
	public void numericGettersClampOutOfRangeValuesAndPersistClamp() {
		prefs.putInteger("low_int", -10);
		prefs.putInteger("high_int", 50);
		prefs.putLong("low_long", -20L);
		prefs.putLong("high_long", 60L);

		assertEquals(0, GameSettings.getInt("low_int", 5, 0, 10));
		assertEquals(10, GameSettings.getInt("high_int", 5, 0, 10));
		assertEquals(0L, GameSettings.getLong("low_long", 5L, 0L, 10L));
		assertEquals(10L, GameSettings.getLong("high_long", 5L, 0L, 10L));
		assertEquals(4, prefs.flushCount);
		assertEquals(0, prefs.getInteger("low_int"));
		assertEquals(10, prefs.getInteger("high_int"));
		assertEquals(0L, prefs.getLong("low_long"));
		assertEquals(10L, prefs.getLong("high_long"));
	}

	@Test
	public void stringGetterRejectsOverLengthValuesAndPersistsDefault() {
		prefs.putString("too_long", "abcdef");
		prefs.putString("accepted", "abc");

		assertEquals("fallback", GameSettings.getString("too_long", "fallback", 3));
		assertEquals("abc", GameSettings.getString("accepted", "fallback", 3));
		assertEquals(1, prefs.flushCount);
		assertEquals("fallback", prefs.getString("too_long"));
	}

	@Test
	public void getterExceptionsReturnDefaultsAndResetRecoverableValues() {
		prefs.throwOnGet = true;

		assertEquals(5, GameSettings.getInt("int", 5));
		assertEquals(6L, GameSettings.getLong("long", 6L));
		assertTrue(GameSettings.getBoolean("boolean", true));
		assertEquals("safe", GameSettings.getString("string", "safe"));

		assertEquals(3, prefs.flushCount);
		assertEquals(5, prefs.getInteger("int"));
		assertEquals(6L, prefs.getLong("long"));
		assertEquals("safe", prefs.getString("string"));
	}

	@Test
	public void putMethodsFlushAfterEachSupportedSettingType() {
		GameSettings.put("int", 1);
		GameSettings.put("long", 2L);
		GameSettings.put("boolean", true);
		GameSettings.put("string", "value");

		assertEquals(4, prefs.flushCount);
		assertEquals(1, prefs.getInteger("int"));
		assertEquals(2L, prefs.getLong("long"));
		assertTrue(prefs.getBoolean("boolean"));
		assertEquals("value", prefs.getString("string"));
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

	private static class RecordingPreferences implements Preferences {

		private final HashMap<String, Object> values = new HashMap<>();
		private int flushCount;
		private boolean throwOnGet;

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
			if (throwOnGet) throw new RuntimeException("get failed");
			return values.containsKey(key) ? getBoolean(key) : defValue;
		}

		@Override
		public int getInteger( String key, int defValue ) {
			if (throwOnGet) throw new RuntimeException("get failed");
			return values.containsKey(key) ? getInteger(key) : defValue;
		}

		@Override
		public long getLong( String key, long defValue ) {
			if (throwOnGet) throw new RuntimeException("get failed");
			return values.containsKey(key) ? getLong(key) : defValue;
		}

		@Override
		public float getFloat( String key, float defValue ) {
			if (throwOnGet) throw new RuntimeException("get failed");
			return values.containsKey(key) ? getFloat(key) : defValue;
		}

		@Override
		public String getString( String key, String defValue ) {
			if (throwOnGet) throw new RuntimeException("get failed");
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
		}
	}
}
