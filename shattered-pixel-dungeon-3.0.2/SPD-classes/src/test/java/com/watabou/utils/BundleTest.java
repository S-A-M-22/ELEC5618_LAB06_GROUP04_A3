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

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BundleTest {

	@Test
	public void writesChecksumAndRemovesItAfterRead() throws IOException {
		Bundle bundle = new Bundle();
		bundle.put("depth", 3);
		bundle.put("seed", "abc");

		String json = writeUncompressed(bundle);
		assertTrue(json.contains("\"__checksum\":\"sha256:"));

		Bundle restored = read(json);
		assertEquals(3, restored.getInt("depth"));
		assertEquals("abc", restored.getString("seed"));
		assertFalse(restored.contains("__checksum"));
	}

	@Test
	public void readsSaveWithoutChecksum() throws IOException {
		Bundle restored = read("{\"depth\":1,\"seed\":\"old-save\"}");

		assertEquals(1, restored.getInt("depth"));
		assertEquals("old-save", restored.getString("seed"));
	}

	@Test
	public void compressedSaveRoundTripsWithChecksum() throws IOException {
		Bundle bundle = new Bundle();
		bundle.put("depth", 5);
		bundle.put("seed", "compressed");

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertTrue(Bundle.write(bundle, output));

		Bundle restored = Bundle.read(new ByteArrayInputStream(output.toByteArray()));
		assertEquals(5, restored.getInt("depth"));
		assertEquals("compressed", restored.getString("seed"));
		assertFalse(restored.contains("__checksum"));
	}

	@Test
	public void rejectsSaveWithChecksumMismatch() throws IOException {
		Bundle bundle = new Bundle();
		bundle.put("depth", 3);
		bundle.put("seed", "abc");

		String modified = writeUncompressed(bundle).replace("\"depth\":3", "\"depth\":4");

		PrintStream originalErr = System.err;
		try {
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
			try {
				read(modified);
				fail("Expected checksum mismatch to throw IOException");
			} catch (IOException expected) {
				// expected
			}
		} finally {
			System.setErr(originalErr);
		}
	}

	@Test
	public void checksumDoesNotDependOnInsertionOrder() throws IOException {
		Bundle first = new Bundle();
		first.put("z", 1);
		first.put("a", "same");

		Bundle second = new Bundle();
		second.put("a", "same");
		second.put("z", 1);

		assertEquals(writeUncompressed(first), writeUncompressed(second));
	}

	@Test
	public void checksumCoversNestedObjectsArraysBooleansAndNulls() throws IOException {
		Bundle nested = new Bundle();
		nested.put("flag", true);
		nested.put("numbers", new int[]{1, 2, 3});

		Bundle bundle = read("{\"nested\":{\"flag\":true,\"numbers\":[1,2,3]},\"nothing\":null}");
		bundle.put("nested", nested);

		String json = writeUncompressed(bundle);

		assertTrue(json.contains("\"nested\":{\"flag\":true,\"numbers\":[1,2,3]}"));
		assertTrue(json.contains("\"nothing\":null"));
		assertEquals(3, read(json).getBundle("nested").getIntArray("numbers").length);
	}

	@Test
	public void checksumChangesWhenContentChanges() throws IOException {
		Bundle first = new Bundle();
		first.put("depth", 1);

		Bundle second = new Bundle();
		second.put("depth", 2);

		assertNotEquals(
				Bundle.readChecksum(new ByteArrayInputStream(writeUncompressed(first).getBytes("UTF-8"))),
				Bundle.readChecksum(new ByteArrayInputStream(writeUncompressed(second).getBytes("UTF-8"))));
	}

	@Test
	public void readChecksumReturnsBundleChecksum() throws IOException {
		Bundle bundle = new Bundle();
		bundle.put("depth", 7);

		String checksum = Bundle.readChecksum(new ByteArrayInputStream(writeUncompressed(bundle).getBytes("UTF-8")));

		assertTrue(checksum.startsWith("sha256:"));
	}

	@Test
	public void readChecksumReturnsNullForLegacyBundle() throws IOException {
		String checksum = Bundle.readChecksum(new ByteArrayInputStream("{\"depth\":7}".getBytes("UTF-8")));

		assertEquals(null, checksum);
	}

	@Test
	public void readChecksumRejectsMalformedChecksumField() throws IOException {
		try {
			read("{\"__checksum\":123,\"depth\":7}");
			fail("Expected malformed checksum to throw IOException");
		} catch (IOException expected) {
			// expected
		}
	}

	@Test
	public void primitiveValuesClassesKeysAndRemovalRoundTrip() {
		Bundle bundle = new Bundle();

		bundle.put("flag", true);
		bundle.put("int", 4);
		bundle.put("long", 5L);
		bundle.put("float", 1.25f);
		bundle.put("string", "value");
		bundle.put("class", TestBundlable.class);
		bundle.put("mode", TestMode.SECOND);

		assertEquals("{}", new Bundle().toString());
		assertTrue(bundle.contains("flag"));
		assertTrue(bundle.getBoolean("flag"));
		assertEquals(4, bundle.getInt("int"));
		assertEquals(5L, bundle.getLong("long"));
		assertEquals(1.25f, bundle.getFloat("float"), 0.001f);
		assertEquals("value", bundle.getString("string"));
		assertEquals(TestBundlable.class, bundle.getClass("class"));
		assertEquals(TestMode.SECOND, bundle.getEnum("mode", TestMode.class));
		assertTrue(bundle.getKeys().contains("string"));
		assertTrue(bundle.remove("string"));
		assertFalse(bundle.contains("string"));
		assertFalse(bundle.remove("missing"));
	}

	@Test
	public void arrayValuesRoundTripForAllSupportedTypes() {
		Bundle bundle = new Bundle();

		bundle.put("ints", new int[]{1, 2});
		bundle.put("longs", new long[]{3L, 4L});
		bundle.put("floats", new float[]{1.5f, 2.5f});
		bundle.put("booleans", new boolean[]{true, false});
		bundle.put("strings", new String[]{"a", "b"});
		bundle.put("classes", new Class[]{TestBundlable.class, BundleTest.class});

		assertArrayEquals(new int[]{1, 2}, bundle.getIntArray("ints"));
		assertArrayEquals(new long[]{3L, 4L}, bundle.getLongArray("longs"));
		assertArrayEquals(new float[]{1.5f, 2.5f}, bundle.getFloatArray("floats"), 0.001f);
		assertArrayEquals(new boolean[]{true, false}, bundle.getBooleanArray("booleans"));
		assertArrayEquals(new String[]{"a", "b"}, bundle.getStringArray("strings"));
		assertArrayEquals(new Class[]{TestBundlable.class, BundleTest.class}, bundle.getClassArray("classes"));
	}

	@Test
	public void nestedBundlesRootArraysAndBundleArraysAreReadable() throws IOException {
		Bundle nested = new Bundle();
		nested.put("depth", 9);
		Bundle bundle = new Bundle();
		bundle.put("nested", nested);

		assertEquals(9, bundle.getBundle("nested").getInt("depth"));
		assertTrue(bundle.getBundle("missing").isNull());

		Bundle fromRootArray = read("[{\"depth\":1},{\"depth\":2}]");
		Bundle[] defaultArray = fromRootArray.getBundleArray();
		assertEquals(2, defaultArray.length);
		assertEquals(1, defaultArray[0].getInt("depth"));

		Bundle fromNamedArray = read("{\"levels\":[{\"depth\":3},{\"depth\":4}]}");
		Bundle[] namedArray = fromNamedArray.getBundleArray("levels");
		assertEquals(2, namedArray.length);
		assertEquals(4, namedArray[1].getInt("depth"));
	}

	@Test
	public void bundlableObjectsCollectionsAndAliasesRestore() throws IOException {
		Bundle.addAlias(TestBundlable.class, "old.save.TestBundlable");
		Bundle bundle = new Bundle();
		bundle.put("single", new TestBundlable(7, "single"));
		bundle.put("collection", Arrays.asList(
				new TestBundlable(1, "first"),
				null,
				new TestBundlable(2, "second")));

		TestBundlable single = (TestBundlable)bundle.get("single");
		assertEquals(7, single.value);
		assertEquals("single", single.name);

		Collection<Bundlable> restored = bundle.getCollection("collection");
		assertEquals(2, restored.size());

		Bundle legacy = read("{\"single\":{\"__className\":\"old.save.TestBundlable\",\"value\":8,\"name\":\"alias\"}}");
		TestBundlable aliasRestored = (TestBundlable)legacy.get("single");
		assertNotNull(aliasRestored);
		assertEquals(8, aliasRestored.value);
		assertEquals("alias", aliasRestored.name);
	}

	@Test
	public void classAliasesAndInvalidObjectsUseFallbacks() throws IOException {
		Bundle.addAlias(TestBundlable.class, "legacy.TestBundlable");
		Bundle bundle = new Bundle();
		bundle.put("empty", "");
		bundle.put("alias", "legacy.TestBundlable");

		assertNull(bundle.getClass("empty"));
		assertEquals(TestBundlable.class, bundle.getClass("alias"));
		assertArrayEquals(
				new Class[]{TestBundlable.class},
				read("{\"classes\":[\"legacy.TestBundlable\"]}").getClassArray("classes"));
		assertNull(read("{\"bad\":{\"__className\":\"missing.Type\"}}").get("bad"));
	}

	@Test
	public void missingMalformedValuesReturnFallbacks() {
		Bundle bundle = new Bundle();
		bundle.put("mode", "missing");

		PrintStream originalErr = System.err;
		try {
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
			assertEquals(TestMode.FIRST, bundle.getEnum("mode", TestMode.class));
			assertEquals(TestMode.FIRST, new Bundle().getEnum("missing_mode", TestMode.class));
			assertNull(bundle.getIntArray("missing_ints"));
			assertNull(bundle.getLongArray("missing_longs"));
			assertNull(bundle.getFloatArray("missing_floats"));
			assertNull(bundle.getBooleanArray("missing_booleans"));
			assertNull(bundle.getStringArray("missing_strings"));
			assertNull(bundle.getClassArray("missing_classes"));
			assertNull(bundle.getBundleArray("missing_bundles"));
			assertTrue(bundle.getCollection("missing_collection").isEmpty());
			assertNull(bundle.get("missing_object"));
		} finally {
			System.setErr(originalErr);
		}
	}

	@Test
	public void writeReturnsFalseWhenOutputStreamFails() {
		Bundle bundle = new Bundle();
		bundle.put("depth", 1);

		assertFalse(Bundle.write(bundle, new OutputStream() {
			@Override
			public void write( int b ) throws IOException {
				throw new IOException("write failed");
			}
		}, false));
	}

	@Test
	public void legacyLibGdxJsonDuplicateKeysArePrunedRecursively() throws Exception {
		JsonValue root = new JsonReader().parse("{depth:1,depth:2,nested:{floor:3,floor:4,kept:5}}");

		Method method = Bundle.class.getDeclaredMethod("killDuplicateKeysInLibGDXJSON", JsonValue.class);
		method.setAccessible(true);
		method.invoke(null, root);

		assertEquals(2, root.size);
		assertEquals(1, countChildrenNamed(root, "depth"));
		assertEquals(2, root.get("nested").size);
		assertEquals(1, countChildrenNamed(root.get("nested"), "floor"));
		assertEquals(1, countChildrenNamed(root.get("nested"), "kept"));
	}

	private static String writeUncompressed( Bundle bundle ) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertTrue(Bundle.write(bundle, output, false));
		return output.toString("UTF-8");
	}

	private static Bundle read( String json ) throws IOException {
		return Bundle.read(new ByteArrayInputStream(json.getBytes("UTF-8")));
	}

	private static int countChildrenNamed( JsonValue parent, String name ) {
		int count = 0;
		for (JsonValue child = parent.child; child != null; child = child.next) {
			if (name.equals(child.name)) {
				count++;
			}
		}
		return count;
	}

	private enum TestMode {
		FIRST,
		SECOND
	}

	public static class TestBundlable implements Bundlable {

		int value;
		String name;

		public TestBundlable() {
		}

		TestBundlable( int value, String name ) {
			this.value = value;
			this.name = name;
		}

		@Override
		public void restoreFromBundle( Bundle bundle ) {
			value = bundle.getInt("value");
			name = bundle.getString("name");
		}

		@Override
		public void storeInBundle( Bundle bundle ) {
			bundle.put("value", value);
			bundle.put("name", name);
		}
	}
}
