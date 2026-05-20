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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

public final class CrashReportWriter {

	public enum ReportType {
		HANDLED_EXCEPTION,
		UNCAUGHT_EXCEPTION
	}

	private static final String REPORT_DIR = "crash-reports";
	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final int MAX_REPORT_BYTES = 256 * 1024;
	private static final Object WRITE_LOCK = new Object();
	private static final AtomicLong COUNTER = new AtomicLong();
	private static final ThreadLocal<Boolean> WRITING = new ThreadLocal<Boolean>();

	private static volatile ReportStore store;

	private CrashReportWriter() {
	}

	public static void configure( Files.FileType type, String basePath ) {
		if (type == null) {
			store = null;
		} else {
			store = new GdxReportStore(type, basePath == null ? "" : basePath);
		}
	}

	public static String writeReport( Throwable tr, ReportType type ) {
		ReportStore activeStore = store;
		if (activeStore == null || Boolean.TRUE.equals(WRITING.get())) {
			return null;
		}

		WRITING.set(true);
		try {
			byte[] report = reportBytes(tr, type);
			synchronized (WRITE_LOCK) {
				activeStore.ensureDirectory();
				String finalName = null;
				String tempName = null;

				for (int i = 0; i < 10; i++) {
					finalName = fileName(type);
					if (!activeStore.exists(finalName)) {
						break;
					}
					finalName = null;
				}

				if (finalName == null) {
					return null;
				}

				tempName = finalName + ".tmp";
				try {
					writeTemp(activeStore, tempName, report);
					activeStore.moveTempToFinal(tempName, finalName);
					return finalName;
				} catch (IOException e) {
					activeStore.deleteTemp(tempName);
					return null;
				}
			}
		} catch (Throwable ignored) {
			return null;
		} finally {
			WRITING.remove();
		}
	}

	public static String stackTraceToString( Throwable tr ) {
		if (tr == null) {
			return "No throwable was supplied.";
		}

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		tr.printStackTrace(pw);
		pw.flush();
		return sw.toString();
	}

	private static void writeTemp( ReportStore store, String tempName, byte[] report ) throws IOException {
		OutputStream output = null;
		try {
			output = store.writeTemp(tempName);
			output.write(report);
			output.flush();
		} finally {
			if (output != null) {
				output.close();
			}
		}
	}

	private static byte[] reportBytes( Throwable tr, ReportType type ) {
		String report = buildReport(tr, type);
		byte[] bytes = report.getBytes(UTF_8);
		if (bytes.length <= MAX_REPORT_BYTES) {
			return bytes;
		}

		String marker = "\n\n... crash report truncated from " + bytes.length + " to " + MAX_REPORT_BYTES + " bytes ...\n\n";
		byte[] markerBytes = marker.getBytes(UTF_8);
		int remaining = Math.max(0, MAX_REPORT_BYTES - markerBytes.length);
		int headBytes = remaining / 2;
		String head = prefixByUtf8Bytes(report, headBytes);
		int used = head.getBytes(UTF_8).length + markerBytes.length;
		String tail = suffixByUtf8Bytes(report, Math.max(0, MAX_REPORT_BYTES - used));
		return (head + marker + tail).getBytes(UTF_8);
	}

	private static String buildReport( Throwable tr, ReportType type ) {
		ReportType safeType = type == null ? ReportType.HANDLED_EXCEPTION : type;
		StringBuilder report = new StringBuilder();
		report.append("Shattered Pixel Dungeon Crash Report\n");
		report.append("Generated: ").append(timestampForReport()).append("\n");
		report.append("Report Type: ").append(safeType.name()).append("\n");
		report.append("Version: ").append(Game.version == null ? "unknown" : Game.version).append("\n");
		report.append("Version Code: ").append(Game.versionCode).append("\n");
		report.append("Platform: ").append(platformName()).append("\n");
		report.append("Thread: ").append(threadName()).append("\n");
		report.append("Exception Class: ").append(tr == null ? "unknown" : tr.getClass().getName()).append("\n");
		report.append("Exception Message: ").append(tr == null || tr.getMessage() == null ? "none" : tr.getMessage()).append("\n");
		report.append("\nStack Trace:\n");
		report.append(stackTraceToString(tr));
		return report.toString();
	}

	private static String fileName( ReportType type ) {
		ReportType safeType = type == null ? ReportType.HANDLED_EXCEPTION : type;
		return "crash-" + timestampForFileName() + "-" + String.format(Locale.ROOT, "%06d", COUNTER.incrementAndGet()) + "-" +
				safeType.name().toLowerCase(Locale.ROOT) + ".txt";
	}

	private static String timestampForReport() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date());
	}

	private static String timestampForFileName() {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date());
	}

	private static String platformName() {
		try {
			Application app = Gdx.app;
			return app == null ? "unknown" : String.valueOf(app.getType());
		} catch (Throwable ignored) {
			return "unknown";
		}
	}

	private static String threadName() {
		Thread thread = Thread.currentThread();
		return thread == null || thread.getName() == null ? "unknown" : thread.getName();
	}

	private static String prefixByUtf8Bytes( String value, int maxBytes ) {
		StringBuilder result = new StringBuilder();
		int used = 0;
		for (int i = 0; i < value.length();) {
			int codePoint = value.codePointAt(i);
			String next = new String(Character.toChars(codePoint));
			int nextBytes = next.getBytes(UTF_8).length;
			if (used + nextBytes > maxBytes) {
				break;
			}
			result.append(next);
			used += nextBytes;
			i += Character.charCount(codePoint);
		}
		return result.toString();
	}

	private static String suffixByUtf8Bytes( String value, int maxBytes ) {
		StringBuilder result = new StringBuilder();
		int used = 0;
		for (int i = value.length(); i > 0;) {
			int codePoint = value.codePointBefore(i);
			String next = new String(Character.toChars(codePoint));
			int nextBytes = next.getBytes(UTF_8).length;
			if (used + nextBytes > maxBytes) {
				break;
			}
			result.insert(0, next);
			used += nextBytes;
			i -= Character.charCount(codePoint);
		}
		return result.toString();
	}

	static void setStoreForTesting( ReportStore testStore ) {
		store = testStore;
		COUNTER.set(0);
	}

	static void resetForTesting() {
		store = null;
		COUNTER.set(0);
		WRITING.remove();
	}

	interface ReportStore {
		void ensureDirectory() throws IOException;
		OutputStream writeTemp( String tempName ) throws IOException;
		void moveTempToFinal( String tempName, String finalName ) throws IOException;
		void deleteTemp( String tempName );
		boolean exists( String finalName ) throws IOException;
	}

	private static class GdxReportStore implements ReportStore {

		private final Files.FileType type;
		private final String basePath;

		private GdxReportStore( Files.FileType type, String basePath ) {
			this.type = type;
			this.basePath = basePath;
		}

		@Override
		public void ensureDirectory() throws IOException {
			try {
				FileHandle dir = reportDir();
				if (dir.exists() && !dir.isDirectory()) {
					throw new IOException("crash report path is not a directory");
				}
				if (!dir.exists()) {
					dir.mkdirs();
				}
				if (!dir.exists() || !dir.isDirectory()) {
					throw new IOException("could not create crash report directory");
				}
			} catch (GdxRuntimeException e) {
				throw new IOException(e);
			}
		}

		@Override
		public OutputStream writeTemp( String tempName ) throws IOException {
			try {
				return reportDir().child(tempName).write(false);
			} catch (GdxRuntimeException e) {
				throw new IOException(e);
			}
		}

		@Override
		public void moveTempToFinal( String tempName, String finalName ) throws IOException {
			try {
				FileHandle temp = reportDir().child(tempName);
				FileHandle finalFile = reportDir().child(finalName);
				if (finalFile.exists()) {
					throw new IOException("crash report already exists");
				}
				temp.moveTo(finalFile);
				if (!finalFile.exists()) {
					throw new IOException("crash report move failed");
				}
			} catch (GdxRuntimeException e) {
				throw new IOException(e);
			}
		}

		@Override
		public void deleteTemp( String tempName ) {
			try {
				reportDir().child(tempName).delete();
			} catch (Throwable ignored) {
				// Crash reporting must never interfere with the original exception path.
			}
		}

		@Override
		public boolean exists( String finalName ) throws IOException {
			try {
				return reportDir().child(finalName).exists();
			} catch (GdxRuntimeException e) {
				throw new IOException(e);
			}
		}

		private FileHandle reportDir() {
			return FileUtils.getFileHandle(type, basePath, REPORT_DIR);
		}
	}
}
