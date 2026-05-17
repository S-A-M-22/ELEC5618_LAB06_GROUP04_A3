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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
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

	private static String writeUncompressed( Bundle bundle ) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertTrue(Bundle.write(bundle, output, false));
		return output.toString("UTF-8");
	}

	private static Bundle read( String json ) throws IOException {
		return Bundle.read(new ByteArrayInputStream(json.getBytes("UTF-8")));
	}
}
