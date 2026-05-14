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

import com.watabou.noosa.Game;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CrashReportWriterTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private LocalStore store;

	@Before
	public void setUp() throws IOException {
		Game.version = "test-version";
		Game.versionCode = 123;
		store = new LocalStore(temporaryFolder.newFolder("crash-reports"));
		CrashReportWriter.setStoreForTesting(store);
	}

	@After
	public void tearDown() {
		CrashReportWriter.resetForTesting();
	}

	@Test
	public void writesReportForHandledException() throws IOException {
		String fileName = CrashReportWriter.writeReport(
				new IllegalStateException("test failure"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		assertNotNull(fileName);
		String report = store.read(fileName);
		assertTrue(report.contains("Report Type: HANDLED_EXCEPTION"));
		assertTrue(report.contains("Version: test-version"));
		assertTrue(report.contains("Version Code: 123"));
		assertTrue(report.contains("Exception Class: java.lang.IllegalStateException"));
		assertTrue(report.contains("Exception Message: test failure"));
		assertTrue(report.contains("Stack Trace:"));
	}

	@Test
	public void handlesNullThrowableAndNullMessage() throws IOException {
		String nullThrowable = CrashReportWriter.writeReport(null, CrashReportWriter.ReportType.HANDLED_EXCEPTION);
		String nullMessage = CrashReportWriter.writeReport(new RuntimeException(), CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		assertNotNull(nullThrowable);
		assertTrue(store.read(nullThrowable).contains("No throwable was supplied."));
		assertNotNull(nullMessage);
		assertTrue(store.read(nullMessage).contains("Exception Message: none"));
	}

	@Test
	public void preservesFullStackTraceAndCauseChain() throws IOException {
		RuntimeException failure = new RuntimeException("outer", new IllegalArgumentException("inner"));

		String fileName = CrashReportWriter.writeReport(failure, CrashReportWriter.ReportType.UNCAUGHT_EXCEPTION);

		String report = store.read(fileName);
		assertTrue(report.contains("Report Type: UNCAUGHT_EXCEPTION"));
		assertTrue(report.contains("java.lang.RuntimeException: outer"));
		assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException: inner"));
	}

	@Test
	public void createsUniqueSortableFilenames() {
		ArrayList<String> fileNames = new ArrayList<String>();
		for (int i = 0; i < 5; i++) {
			fileNames.add(CrashReportWriter.writeReport(
					new RuntimeException("failure " + i),
					CrashReportWriter.ReportType.HANDLED_EXCEPTION));
		}

		assertEquals(5, new HashSet<String>(fileNames).size());
		for (int i = 1; i < fileNames.size(); i++) {
			assertTrue(fileNames.get(i - 1).compareTo(fileNames.get(i)) < 0);
		}
	}

	@Test
	public void truncatesOversizedReports() throws IOException {
		String fileName = CrashReportWriter.writeReport(
				new HugeThrowable(),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		byte[] report = store.readBytes(fileName);
		String reportText = new String(report, StandardCharsets.UTF_8);
		assertTrue(report.length <= 256 * 1024);
		assertTrue(reportText.contains("crash report truncated"));
		assertTrue(reportText.contains("head-marker"));
		assertTrue(reportText.contains("tail-marker"));
	}

	@Test
	public void writeFailureDoesNotMaskOriginalException() {
		CrashReportWriter.setStoreForTesting(new FailingStore(true, false, false));
		assertNull(CrashReportWriter.writeReport(
				new RuntimeException("failure"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));

		CrashReportWriter.setStoreForTesting(new FailingStore(false, true, false));
		assertNull(CrashReportWriter.writeReport(
				new RuntimeException("failure"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));

		CrashReportWriter.setStoreForTesting(new FailingStore(false, false, true));
		assertNull(CrashReportWriter.writeReport(
				new RuntimeException("failure"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));
	}

	@Test
	public void gameReportExceptionPersistsHandledReport() throws IOException {
		Game.reportException(new RuntimeException("from game"));

		File[] reports = store.dir.listFiles();
		assertNotNull(reports);
		assertEquals(1, reports.length);
		String report = new String(java.nio.file.Files.readAllBytes(reports[0].toPath()), StandardCharsets.UTF_8);
		assertTrue(report.contains("Report Type: HANDLED_EXCEPTION"));
		assertTrue(report.contains("Exception Message: from game"));
	}

	private static class LocalStore implements CrashReportWriter.ReportStore {

		private final File dir;

		private LocalStore( File dir ) {
			this.dir = dir;
		}

		@Override
		public void ensureDirectory() throws IOException {
			if (dir.exists() && !dir.isDirectory()) {
				throw new IOException("not a directory");
			}
			if (!dir.exists() && !dir.mkdirs()) {
				throw new IOException("could not create directory");
			}
		}

		@Override
		public OutputStream writeTemp( String tempName ) throws IOException {
			return new FileOutputStream(new File(dir, tempName));
		}

		@Override
		public void moveTempToFinal( String tempName, String finalName ) throws IOException {
			java.nio.file.Files.move(new File(dir, tempName).toPath(), new File(dir, finalName).toPath());
		}

		@Override
		public void deleteTemp( String tempName ) {
			new File(dir, tempName).delete();
		}

		@Override
		public boolean exists( String finalName ) {
			return new File(dir, finalName).exists();
		}

		private String read( String fileName ) throws IOException {
			return new String(readBytes(fileName), StandardCharsets.UTF_8);
		}

		private byte[] readBytes( String fileName ) throws IOException {
			return java.nio.file.Files.readAllBytes(new File(dir, fileName).toPath());
		}
	}

	private static class FailingStore implements CrashReportWriter.ReportStore {

		private final boolean failEnsure;
		private final boolean failWrite;
		private final boolean failMove;

		private FailingStore( boolean failEnsure, boolean failWrite, boolean failMove ) {
			this.failEnsure = failEnsure;
			this.failWrite = failWrite;
			this.failMove = failMove;
		}

		@Override
		public void ensureDirectory() throws IOException {
			if (failEnsure) {
				throw new IOException("ensure failed");
			}
		}

		@Override
		public OutputStream writeTemp( String tempName ) throws IOException {
			if (failWrite) {
				throw new IOException("write failed");
			}
			return new OutputStream() {
				@Override
				public void write( int b ) {
				}
			};
		}

		@Override
		public void moveTempToFinal( String tempName, String finalName ) throws IOException {
			if (failMove) {
				throw new IOException("move failed");
			}
		}

		@Override
		public void deleteTemp( String tempName ) {
		}

		@Override
		public boolean exists( String finalName ) {
			return false;
		}
	}

	private static class HugeThrowable extends RuntimeException {
		@Override
		public void printStackTrace( PrintWriter s ) {
			s.print("head-marker");
			for (int i = 0; i < 300000; i++) {
				s.print('x');
			}
			s.print("tail-marker");
		}
	}
}
