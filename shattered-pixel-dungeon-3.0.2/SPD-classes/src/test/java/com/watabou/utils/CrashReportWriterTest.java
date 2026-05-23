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
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
	private Files previousFiles;
	private Application previousApp;

	@Before
	public void setUp() throws IOException {
		Game.version = "test-version";
		Game.versionCode = 123;
		previousFiles = Gdx.files;
		previousApp = Gdx.app;
		store = new LocalStore(temporaryFolder.newFolder("crash-reports"));
		CrashReportWriter.setStoreForTesting(store);
	}

	@After
	public void tearDown() {
		CrashReportWriter.resetForTesting();
		Gdx.files = previousFiles;
		Gdx.app = previousApp;
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
	public void nullConfigurationAndReentrantWriteReturnNull() throws IOException {
		CrashReportWriter.configure(null, "unused");
		assertNull(CrashReportWriter.writeReport(new RuntimeException("disabled"), null));

		CrashReportWriter.setStoreForTesting(new ReentrantStore(temporaryFolder.newFolder("reentrant-store")));
		assertNotNull(CrashReportWriter.writeReport(
				new RuntimeException("outer"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));
	}

	@Test
	public void writeReportReturnsNullWhenAllCandidateNamesAlreadyExist() {
		CrashReportWriter.setStoreForTesting(new AlwaysExistsStore());

		assertNull(CrashReportWriter.writeReport(
				new RuntimeException("collision"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));
	}

	@Test
	public void runtimeFailureFromStoreReturnsNull() {
		CrashReportWriter.setStoreForTesting(new RuntimeFailingStore());

		assertNull(CrashReportWriter.writeReport(
				new RuntimeException("runtime failure"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));
	}

	@Test
	public void gdxConfiguredStoreWritesToCrashReportDirectory() throws IOException {
		File root = temporaryFolder.newFolder("gdx-store");
		Gdx.files = new LocalFiles(root);
		CrashReportWriter.configure(Files.FileType.Local, null);

		String fileName = CrashReportWriter.writeReport(
				new RuntimeException("via gdx store"),
				null);

		assertNotNull(fileName);
		String report = new String(java.nio.file.Files.readAllBytes(
				new File(new File(root, "crash-reports"), fileName).toPath()), StandardCharsets.UTF_8);
		assertTrue(report.contains("Report Type: HANDLED_EXCEPTION"));
		assertTrue(report.contains("Exception Message: via gdx store"));
	}

	@Test
	public void gdxConfiguredStoreWritesToExistingCrashReportDirectory() throws IOException {
		File root = temporaryFolder.newFolder("gdx-store-existing-dir");
		assertTrue(new File(root, "crash-reports").mkdirs());
		Gdx.files = new LocalFiles(root);
		CrashReportWriter.configure(Files.FileType.Local, "");

		String fileName = CrashReportWriter.writeReport(
				new RuntimeException("existing dir"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		assertNotNull(fileName);
		String report = new String(java.nio.file.Files.readAllBytes(
				new File(new File(root, "crash-reports"), fileName).toPath()), StandardCharsets.UTF_8);
		assertTrue(report.contains("Exception Message: existing dir"));
	}

	@Test
	public void gdxConfiguredStoreRejectsReportDirectoryThatIsAFile() throws IOException {
		File root = temporaryFolder.newFolder("gdx-store-file-conflict");
		assertTrue(new File(root, "crash-reports").createNewFile());
		Gdx.files = new LocalFiles(root);
		CrashReportWriter.configure(Files.FileType.Local, "");

		assertNull(CrashReportWriter.writeReport(
				new RuntimeException("file conflict"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));
	}

	@Test
	public void gdxConfiguredStoreHandlesDirectoryCreationWriteMoveDeleteAndExistsFailures() throws IOException {
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.DIR_CANNOT_BE_CREATED);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.DIR_CREATED_AS_FILE);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.REPORT_DIR_EXISTS_THROWS);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.WRITE_THROWS);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.FINAL_ALREADY_EXISTS);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.FINAL_CREATED_ON_TEMP_WRITE);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.FINAL_MISSING_AFTER_MOVE);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.MOVE_THROWS);
		assertGdxStoreWriteReturnsNull(ScriptedFiles.Mode.EXISTS_THROWS);
	}

	@Test
	public void platformNameIsWrittenWhenGdxAppIsAvailable() throws IOException {
		Gdx.app = (Application) Proxy.newProxyInstance(
				Application.class.getClassLoader(),
				new Class[]{Application.class},
				(proxy, method, args) -> {
					if ("getType".equals(method.getName())) {
						return Application.ApplicationType.Desktop;
					}
					return defaultValue(method.getReturnType());
				});

		String fileName = CrashReportWriter.writeReport(
				new RuntimeException("known platform"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		assertTrue(store.read(fileName).contains("Platform: Desktop"));
	}

	@Test
	public void platformLookupFailureFallsBackToUnknown() throws IOException {
		Gdx.app = (Application) Proxy.newProxyInstance(
				Application.class.getClassLoader(),
				new Class[]{Application.class},
				(proxy, method, args) -> {
					if ("getType".equals(method.getName())) {
						throw new RuntimeException("no platform");
					}
					return defaultValue(method.getReturnType());
				});

		String fileName = CrashReportWriter.writeReport(
				new RuntimeException("platform fallback"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		assertTrue(store.read(fileName).contains("Platform: unknown"));
	}

	@Test
	public void nullPlatformFallsBackToUnknown() throws IOException {
		Gdx.app = null;

		String fileName = CrashReportWriter.writeReport(
				new RuntimeException("null platform"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		assertTrue(store.read(fileName).contains("Platform: unknown"));
	}

	@Test
	public void unknownVersionAndNullTypeUseFallbackReportValues() throws IOException {
		Game.version = null;

		String fileName = CrashReportWriter.writeReport(
				new RuntimeException("fallback values"),
				null);

		String report = store.read(fileName);
		assertTrue(report.contains("Report Type: HANDLED_EXCEPTION"));
		assertTrue(report.contains("Version: unknown"));
	}

	private void assertGdxStoreWriteReturnsNull( ScriptedFiles.Mode mode ) throws IOException {
		File root = temporaryFolder.newFolder("scripted-" + mode.name());
		Gdx.files = new ScriptedFiles(root, mode);
		CrashReportWriter.configure(Files.FileType.Local, "");

		assertNull(CrashReportWriter.writeReport(
				new RuntimeException(mode.name()),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION));
	}

	private static Object defaultValue( Class<?> type ) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == Boolean.TYPE) {
			return false;
		}
		if (type == Character.TYPE) {
			return '\0';
		}
		return 0;
	}

	@Test
	public void writeReportPersistsHandledReportThroughWriter() throws IOException {
		CrashReportWriter.writeReport(
				new RuntimeException("from writer"),
				CrashReportWriter.ReportType.HANDLED_EXCEPTION);

		File[] reports = store.dir.listFiles();
		assertNotNull(reports);
		assertEquals(1, reports.length);
		String report = new String(java.nio.file.Files.readAllBytes(reports[0].toPath()), StandardCharsets.UTF_8);
		assertTrue(report.contains("Report Type: HANDLED_EXCEPTION"));
		assertTrue(report.contains("Exception Message: from writer"));
	}

	@Test
	public void utf8TruncationHelpersHandleEmptyAndPartialCharacters() throws Exception {
		Method prefix = CrashReportWriter.class.getDeclaredMethod("prefixByUtf8Bytes", String.class, int.class);
		Method suffix = CrashReportWriter.class.getDeclaredMethod("suffixByUtf8Bytes", String.class, int.class);
		prefix.setAccessible(true);
		suffix.setAccessible(true);

		assertEquals("", prefix.invoke(null, "", 10));
		assertEquals("", suffix.invoke(null, "", 10));
		assertEquals("", prefix.invoke(null, "é", 1));
		assertEquals("", suffix.invoke(null, "é", 1));
		assertEquals("é", prefix.invoke(null, "éx", 2));
		assertEquals("é", suffix.invoke(null, "xé", 2));
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

	private static class ReentrantStore extends LocalStore {

		private ReentrantStore( File dir ) {
			super(dir);
		}

		@Override
		public void ensureDirectory() {
			assertNull(CrashReportWriter.writeReport(
					new RuntimeException("nested"),
					CrashReportWriter.ReportType.HANDLED_EXCEPTION));
		}
	}

	private static class AlwaysExistsStore extends LocalStore {

		private AlwaysExistsStore() {
			super(new File("."));
		}

		@Override
		public boolean exists( String finalName ) {
			return true;
		}
	}

	private static class RuntimeFailingStore extends LocalStore {

		private RuntimeFailingStore() {
			super(new File("."));
		}

		@Override
		public void ensureDirectory() {
			throw new RuntimeException("boom");
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

	private static class LocalFiles implements Files {

		protected final File root;

		private LocalFiles( File root ) {
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
			return new FileHandle(new File(root, path));
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

	private static class ScriptedFiles extends LocalFiles {

		private enum Mode {
			DIR_CANNOT_BE_CREATED,
			DIR_CREATED_AS_FILE,
			REPORT_DIR_EXISTS_THROWS,
			WRITE_THROWS,
			FINAL_ALREADY_EXISTS,
			FINAL_CREATED_ON_TEMP_WRITE,
			FINAL_MISSING_AFTER_MOVE,
			MOVE_THROWS,
			EXISTS_THROWS
		}

		private final Mode mode;

		private ScriptedFiles( File root, Mode mode ) {
			super(root);
			this.mode = mode;
		}

		@Override
		public FileHandle local( String path ) {
			return new ScriptedHandle(new File(root, path), path, mode);
		}
	}

	private static class ScriptedHandle extends FileHandle {

		private final String logicalPath;
		private final ScriptedFiles.Mode mode;

		private ScriptedHandle( File file, String logicalPath, ScriptedFiles.Mode mode ) {
			super(file);
			this.logicalPath = logicalPath;
			this.mode = mode;
		}

		@Override
		public FileHandle child( String name ) {
			String childPath = logicalPath.length() == 0 ? name : logicalPath + "/" + name;
			return new ScriptedHandle(new File(file(), name), childPath, mode);
		}

		@Override
		public boolean exists() {
			if (mode == ScriptedFiles.Mode.REPORT_DIR_EXISTS_THROWS && "crash-reports".equals(logicalPath)) {
				throw new GdxRuntimeException("directory exists failed");
			}
			if (mode == ScriptedFiles.Mode.DIR_CANNOT_BE_CREATED && "crash-reports".equals(logicalPath)) {
				return false;
			}
			if (mode == ScriptedFiles.Mode.FINAL_ALREADY_EXISTS && logicalPath.endsWith(".txt")) {
				return true;
			}
			if (mode == ScriptedFiles.Mode.FINAL_MISSING_AFTER_MOVE && logicalPath.endsWith(".txt")) {
				return false;
			}
			if (mode == ScriptedFiles.Mode.EXISTS_THROWS && logicalPath.endsWith(".txt")) {
				throw new GdxRuntimeException("exists failed");
			}
			return super.exists();
		}

		@Override
		public boolean isDirectory() {
			if (mode == ScriptedFiles.Mode.DIR_CREATED_AS_FILE && "crash-reports".equals(logicalPath)) {
				return super.isDirectory();
			}
			return "crash-reports".equals(logicalPath) || super.isDirectory();
		}

		@Override
		public void mkdirs() {
			if (mode == ScriptedFiles.Mode.DIR_CREATED_AS_FILE) {
				try {
					File parent = file().getParentFile();
					if (parent != null) {
						parent.mkdirs();
					}
					file().createNewFile();
				} catch (IOException e) {
					throw new GdxRuntimeException(e);
				}
			} else if (mode != ScriptedFiles.Mode.DIR_CANNOT_BE_CREATED) {
				super.mkdirs();
			}
		}

		@Override
		public OutputStream write( boolean append ) {
			if (mode == ScriptedFiles.Mode.WRITE_THROWS && logicalPath.endsWith(".tmp")) {
				throw new GdxRuntimeException("write failed");
			}
			if (mode == ScriptedFiles.Mode.FINAL_CREATED_ON_TEMP_WRITE && logicalPath.endsWith(".tmp")) {
				try {
					File finalFile = new File(file().getPath().substring(0, file().getPath().length() - 4));
					File parent = finalFile.getParentFile();
					if (parent != null) {
						parent.mkdirs();
					}
					finalFile.createNewFile();
				} catch (IOException e) {
					throw new GdxRuntimeException(e);
				}
			}
			return new java.io.ByteArrayOutputStream();
		}

		@Override
		public void moveTo( FileHandle dest ) {
			if (mode == ScriptedFiles.Mode.MOVE_THROWS) {
				throw new GdxRuntimeException("move failed");
			}
			if (mode != ScriptedFiles.Mode.FINAL_MISSING_AFTER_MOVE) {
				super.moveTo(dest);
			}
		}

		@Override
		public boolean delete() {
			if (mode == ScriptedFiles.Mode.WRITE_THROWS && logicalPath.endsWith(".tmp")) {
				throw new GdxRuntimeException("delete failed");
			}
			return super.delete();
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
