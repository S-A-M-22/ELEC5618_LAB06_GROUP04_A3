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
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class FileUtils {
	
	// Helper methods for setting/using a default base path and file address mode
	
	private static Files.FileType defaultFileType = null;
	private static String defaultPath = "";

	private static final String SAVE_MANIFEST_FILE = "save_checksums.dat";
	private static final String MANIFEST_VERSION = "version";
	private static final String MANIFEST_PATHS = "paths";
	private static final String MANIFEST_CHECKSUMS = "checksums";
	private static final String MANIFEST_OVERALL_CHECKSUM = "overall_checksum";
	private static final int CURRENT_MANIFEST_VERSION = 1;
	private static final String CHECKSUM_PREFIX = "sha256:";
	private static final int CHECKSUM_BUFFER = 1024*4;
	private static boolean updatingManifest = false;
	
	public static void setDefaultFileProperties( Files.FileType type, String path ){
		defaultFileType = type;
		defaultPath = path;
	}
	
	public static FileHandle getFileHandle( String name ){
		return getFileHandle( defaultFileType, defaultPath, name );
	}
	
	public static FileHandle getFileHandle( Files.FileType type, String name ){
		return getFileHandle( type, "", name );
	}
	
	public static FileHandle getFileHandle( Files.FileType type, String basePath, String name ){
		switch (type){
			case Classpath:
				return Gdx.files.classpath( basePath + name );
			case Internal:
				return Gdx.files.internal( basePath + name );
			case External:
				return Gdx.files.external( basePath + name );
			case Absolute:
				return Gdx.files.absolute( basePath + name );
			case Local:
				return Gdx.files.local( basePath + name );
			default:
				return null;
		}
	}
	
	// Files

	//looks to see if there is any evidence of interrupted saving
	public static boolean cleanTempFiles(){
		boolean foundTemp = cleanTempFiles("");
		if (foundTemp) {
			try {
				updateSaveManifest();
			} catch (IOException e) {
				//temp cleanup is best-effort, save validation will still catch bad files later
			}
		}
		return foundTemp;
	}

	public static boolean cleanTempFiles( String dirName ){
		FileHandle dir = getFileHandle(dirName);
		boolean foundTemp = false;
		for (FileHandle file : dir.list()){
			if (file.isDirectory()){
				foundTemp = cleanTempFiles(dirName + file.name()) || foundTemp;
			} else if (file.length() == 0) {
				file.delete();
			} else {
				if (file.name().endsWith(".tmp")){
					FileHandle temp = file;
					FileHandle original = getFileHandle( defaultFileType, "", temp.path().replace(".tmp", "") );

					//replace the base file with the temp one if base is invalid or temp is valid and newer
					try {
						bundleFromStream(temp.read());

						try {
							bundleFromStream(original.read());

							if (temp.lastModified() > original.lastModified()) {
								temp.moveTo(original);
							} else {
								temp.delete();
							}

						} catch (Exception e) {
							temp.moveTo(original);
						}

					} catch (Exception e) {
						temp.delete();
					}

					foundTemp = true;
				}
			}
		}
		return foundTemp;
	}
	
	public static boolean fileExists( String name ){
		FileHandle file = getFileHandle( name );
		return file.exists() && !file.isDirectory() && file.length() > 0;
	}

	//returns length of a file in bytes, or 0 if file does not exist
	public static long fileLength( String name ){
		FileHandle file = getFileHandle( name );
		if (!file.exists() || file.isDirectory()){
			return 0;
		} else {
			return file.length();
		}
	}
	
	public static boolean deleteFile( String name ){
		boolean deleted = getFileHandle( name ).delete();
		if (deleted) {
			try {
				updateSaveManifest();
			} catch (IOException e) {
				//deletion already happened, save validation will still catch stale manifests later
			}
		}
		return deleted;
	}

	//replaces a file with junk data, for as many bytes as given
	//This is helpful as some cloud sync systems do not persist deleted, empty, or zeroed files
	public static void overwriteFile( String name, int bytes ){
		byte[] data = new byte[bytes];
		Arrays.fill(data, (byte)1);
		getFileHandle( name ).writeBytes(data, false);
		try {
			updateSaveManifest();
		} catch (IOException e) {
			//overwriting already happened, save validation will still catch stale manifests later
		}
	}
	
	// Directories
	
	public static boolean dirExists( String name ){
		FileHandle dir = getFileHandle( name );
		return dir.exists() && dir.isDirectory();
	}
	
	public static boolean deleteDir( String name ){
		FileHandle dir = getFileHandle( name );
		
		if (dir == null || !dir.isDirectory()){
			return false;
		} else {
			return dir.deleteDirectory();
		}
	}

	public static ArrayList<String> filesInDir( String name ){
		FileHandle dir = getFileHandle( name );
		ArrayList result = new ArrayList();
		if (dir != null && dir.isDirectory()){
			for (FileHandle file : dir.list()){
				result.add(file.name());
			}
		}
		return result;
	}
	
	// bundle reading
	
	//only works for base path
	public static Bundle bundleFromFile( String fileName ) throws IOException{
		try {
			verifySaveManifest(fileName);
			FileHandle file = getFileHandle( fileName );
			if (!file.exists() || file.isDirectory() || file.length() == 0) {
				throw new IOException("file does not exist!");
			}
			return bundleFromStream(file.read());
		} catch (GdxRuntimeException e){
			//game classes expect an IO exception, so wrap the GDX exception in that
			throw new IOException(e);
		}
	}
	
	private static Bundle bundleFromStream( InputStream input ) throws IOException{
		Bundle bundle = Bundle.read( input );
		input.close();
		return bundle;
	}
	
	// bundle writing
	
	//only works for base path
	public static void bundleToFile( String fileName, Bundle bundle ) throws IOException{
		bundleToFile(fileName, bundle, true);
	}

	private static void bundleToFile( String fileName, Bundle bundle, boolean updateManifest ) throws IOException{
		try {
			FileHandle file = getFileHandle(fileName);

			//write to a temp file, then move the files.
			// This helps prevent save corruption if writing is interrupted
			if (file.exists()){
				FileHandle temp = getFileHandle(fileName + ".tmp");
				bundleToStream(temp.write(false), bundle);
				file.delete();
				temp.moveTo(file);
			} else {
				bundleToStream(file.write(false), bundle);
			}

			if (updateManifest && !SAVE_MANIFEST_FILE.equals(fileName)){
				updateSaveManifest();
			}

		} catch (GdxRuntimeException e){
			//game classes expect an IO exception, so wrap the GDX exception in that
			throw new IOException(e);
		}
	}
	
	private static void bundleToStream( OutputStream output, Bundle bundle ) throws IOException{
		Bundle.write( bundle, output );
		output.close();
	}

	public static void updateSaveManifest() throws IOException {
		if (defaultFileType == null || updatingManifest) {
			return;
		}

		FileHandle root = getFileHandle("");
		if (root == null || !root.exists() || !root.isDirectory()) {
			return;
		}

		updatingManifest = true;
		try {
			ArrayList<ManifestEntry> entries = manifestEntries();
			Bundle manifest = new Bundle();
			manifest.put(MANIFEST_VERSION, CURRENT_MANIFEST_VERSION);
			manifest.put(MANIFEST_PATHS, manifestPaths(entries));
			manifest.put(MANIFEST_CHECKSUMS, manifestChecksums(entries));
			manifest.put(MANIFEST_OVERALL_CHECKSUM, manifestOverallChecksum(entries));

			bundleToFile(SAVE_MANIFEST_FILE, manifest, false);
		} finally {
			updatingManifest = false;
		}
	}

	private static void verifySaveManifest( String requestedFile ) throws IOException {
		if (SAVE_MANIFEST_FILE.equals(requestedFile)) {
			return;
		}

		FileHandle manifestFile = getFileHandle(SAVE_MANIFEST_FILE);
		if (!manifestFile.exists() || manifestFile.isDirectory() || manifestFile.length() == 0) {
			return;
		}

		Bundle manifest = bundleFromStream(manifestFile.read());
		String expected = manifest.getString(MANIFEST_OVERALL_CHECKSUM);
		if (expected == null || expected.length() == 0) {
			throw new IOException("save manifest is invalid");
		}

		String actual = manifestOverallChecksum(manifestEntries());
		if (!expected.equals(actual)) {
			throw new IOException("save manifest checksum mismatch");
		}
	}

	private static ArrayList<ManifestEntry> manifestEntries() throws IOException {
		ArrayList<ManifestEntry> entries = new ArrayList<>();
		collectManifestFiles(getFileHandle(""), "", entries);
		Collections.sort(entries, new Comparator<ManifestEntry>() {
			@Override
			public int compare( ManifestEntry a, ManifestEntry b ) {
				return a.path.compareTo(b.path);
			}
		});
		return entries;
	}

	private static void collectManifestFiles( FileHandle dir, String dirName, ArrayList<ManifestEntry> entries ) throws IOException {
		for (FileHandle file : dir.list()) {
			String relative = dirName.length() == 0 ? file.name() : dirName + "/" + file.name();
			if (file.isDirectory()) {
				if (isActiveGameDir(relative)) {
					collectManifestFiles(file, relative, entries);
				}
			} else if (isManifestProtectedFile(relative)) {
				entries.add(new ManifestEntry(relative, manifestFileChecksum(relative, file)));
			}
		}
	}

	private static boolean isManifestProtectedFile( String relative ) {
		if (relative.endsWith(".tmp") || relative.equals(SAVE_MANIFEST_FILE)) {
			return false;
		}

		if (relative.indexOf('/') == -1) {
			return relative.endsWith(".dat") || relative.equals(GameSettings.DEFAULT_PREFS_FILE);
		}

		int slash = relative.indexOf('/');
		return isActiveGameDir(relative.substring(0, slash))
				&& relative.indexOf('/', slash + 1) == -1
				&& relative.endsWith(".dat");
	}

	private static boolean isActiveGameDir( String relative ) {
		if (!relative.startsWith("game") || relative.length() == 4) {
			return false;
		}
		for (int i = 4; i < relative.length(); i++) {
			char c = relative.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	private static String manifestFileChecksum( String relative, FileHandle file ) throws IOException {
		if (relative.endsWith(".dat") && file.length() > 1) {
			String bundleChecksum = Bundle.readChecksum(file.read());
			if (bundleChecksum != null) {
				return "bundle:" + bundleChecksum;
			}
		}
		return "bytes:" + fileChecksum(file);
	}

	private static String[] manifestPaths( ArrayList<ManifestEntry> entries ) {
		String[] result = new String[entries.size()];
		for (int i = 0; i < entries.size(); i++) {
			result[i] = entries.get(i).path;
		}
		return result;
	}

	private static String[] manifestChecksums( ArrayList<ManifestEntry> entries ) {
		String[] result = new String[entries.size()];
		for (int i = 0; i < entries.size(); i++) {
			result[i] = entries.get(i).checksum;
		}
		return result;
	}

	private static String manifestOverallChecksum( ArrayList<ManifestEntry> entries ) throws IOException {
		StringBuilder builder = new StringBuilder();
		for (ManifestEntry entry : entries) {
			builder.append(entry.path);
			builder.append('\n');
			builder.append(entry.checksum);
			builder.append('\n');
		}
		return stringChecksum(builder.toString());
	}

	private static String fileChecksum( FileHandle file ) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			InputStream input = file.read();
			try {
				byte[] buffer = new byte[CHECKSUM_BUFFER];
				int read;
				while ((read = input.read(buffer)) != -1) {
					digest.update(buffer, 0, read);
				}
			} finally {
				input.close();
			}
			return CHECKSUM_PREFIX + bytesToHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static String stringChecksum( String text ) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return CHECKSUM_PREFIX + bytesToHex(digest.digest(text.getBytes("UTF-8")));
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static String bytesToHex( byte[] bytes ) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(bytes[i] & 0xff);
			if (hex.length() == 1) result.append('0');
			result.append(hex);
		}
		return result.toString();
	}

	private static class ManifestEntry {
		private final String path;
		private final String checksum;

		private ManifestEntry( String path, String checksum ) {
			this.path = path;
			this.checksum = checksum;
		}
	}

}
