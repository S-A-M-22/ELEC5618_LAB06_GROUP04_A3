package com.shatteredpixel.shatteredpixeldungeon.actors.mobs;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.RecoveryManager;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Cross-feature integration tests for the three merged quality improvements:
 * mob behaviour logging, save integrity checksums, and recovery saves.
 */
public class MergedFeatureIntegrationTest {

	private static final String MANIFEST = "save_checksums.dat";

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private MobBehaviourLogger logger;

	@BeforeClass
	public static void initGdx() {
		if (Gdx.app == null) {
			new HeadlessApplication(new ApplicationAdapter() {});
		}
	}

	@Before
	public void setUp() {
		FileUtils.setDefaultFileProperties(
				Files.FileType.Absolute,
				tempFolder.getRoot().getAbsolutePath() + "/"
		);
		for (int slot = 1; slot <= GamesInProgress.MAX_SLOTS; slot++) {
			GamesInProgress.setUnknown(slot);
		}
		logger = MobBehaviourLogger.getInstance();
		logger.clear();
		logger.setEnabled(true);
		logger.setTerminalSink(s -> {});
		logger.setFileSink(s -> {});
	}

	@Test
	public void saveWriteThenMobLog_keepsManifestValidAndIgnoresLogFile() throws IOException {
		writeSaveSlot(1, 11, 12);
		useRealMobLogFileSink();

		triggerMobEvents();

		assertTrue(file(MobBehaviourLogger.DEFAULT_LOG_FILE).exists());
		Bundle manifest = FileUtils.bundleFromFile(MANIFEST);
		assertArrayEquals(new String[]{"game*/depth1.dat", "game*/game.dat"},
				manifest.getStringArray("paths"));
		assertEquals(11, FileUtils.bundleFromFile(GamesInProgress.gameFile(1)).getInt("value"));
	}

	@Test
	public void deleteToRecovery_thenRestore_preservesChecksumReadableSave() throws Exception {
		writeSaveSlot(1, 21, 22);
		RecoveryManager.moveToRecovery(1);
		deleteSlotThroughFileUtils(1);

		String entry = onlyRecoveryEntry();
		RecoveryManager.restoreEntry(entry, 2);

		assertEquals(21, FileUtils.bundleFromFile(GamesInProgress.gameFile(2)).getInt("value"));
		assertEquals(22, FileUtils.bundleFromFile(GamesInProgress.depthFile(2, 1, 0)).getInt("value"));
	}

	@Test
	public void restoredSaveTampering_isRejectedAfterRecoveryRestore() throws Exception {
		writeSaveSlot(1, 31, 32);
		RecoveryManager.moveToRecovery(1);
		deleteSlotThroughFileUtils(1);
		RecoveryManager.restoreEntry(onlyRecoveryEntry(), 2);

		writeText(GamesInProgress.gameFile(2), "{\"value\":999}");

		assertBundleRejected(GamesInProgress.gameFile(2));
	}

	@Test
	public void mobLogBeforeDelete_isCopiedOnlyIfInsideSlotAndDoesNotBreakRecovery() throws Exception {
		writeSaveSlot(1, 41, 42);
		writeText("game1/debug.log", "diagnostic log");

		RecoveryManager.moveToRecovery(1);

		File entryDir = file("recovery/" + onlyRecoveryEntry());
		assertTrue(new File(entryDir, "game.dat").exists());
		assertTrue(new File(entryDir, "depth1.dat").exists());
		assertTrue(new File(entryDir, "debug.log").exists());
		assertArrayEquals(new String[]{"game*/depth1.dat", "game*/game.dat"},
				FileUtils.bundleFromFile(MANIFEST).getStringArray("paths"));
		assertEquals(41, FileUtils.bundleFromFile(GamesInProgress.gameFile(1)).getInt("value"));
	}

	@Test
	public void recoveryDirectoryContents_areExcludedFromSaveManifest() throws Exception {
		writeSaveSlot(1, 51, 52);
		RecoveryManager.moveToRecovery(1);

		Bundle manifest = FileUtils.bundleFromFile(MANIFEST);

		for (String path : manifest.getStringArray("paths")) {
			assertFalse(path.startsWith(RecoveryManager.RECOVERY_DIR + "/"));
		}
		assertEquals(51, FileUtils.bundleFromFile(GamesInProgress.gameFile(1)).getInt("value"));
	}

	@Test
	public void multipleRecoveredSlots_restoreChosenEntryAndManifestValidatesTarget() throws Exception {
		writeSaveSlot(1, 61, 62);
		RecoveryManager.moveToRecovery(1);
		Thread.sleep(5);
		writeSaveSlot(2, 71, 72);
		RecoveryManager.moveToRecovery(2);

		ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
		assertEquals(2, entries.size());
		RecoveryManager.restoreEntry(entries.get(1), 3);

		assertEquals(71, FileUtils.bundleFromFile(GamesInProgress.gameFile(3)).getInt("value"));
		assertEquals(72, FileUtils.bundleFromFile(GamesInProgress.depthFile(3, 1, 0)).getInt("value"));
	}

	@Test
	public void loggerSinkFailure_doesNotPreventSaveManifestOrRecoveryWorkflow() throws Exception {
		logger.setFileSink(line -> {
			throw new RuntimeException("sink failed");
		});

		triggerMobEvents();
		writeSaveSlot(1, 81, 82);
		RecoveryManager.moveToRecovery(1);

		assertEquals(4, logger.size());
		assertEquals(1, RecoveryManager.listRecoveryEntries().size());
		assertEquals(81, FileUtils.bundleFromFile(GamesInProgress.gameFile(1)).getInt("value"));
	}

	@Test
	public void helperDeletionWorkflow_updatesManifestAndRecoveryKeepsOriginalBytes() throws Exception {
		writeSaveSlot(1, 91, 92);
		byte[] originalGame = readBytes(GamesInProgress.gameFile(1));
		byte[] originalDepth = readBytes(GamesInProgress.depthFile(1, 1, 0));

		RecoveryManager.moveToRecovery(1);
		deleteSlotThroughFileUtils(1);

		assertBundleRejected(GamesInProgress.gameFile(1));
		assertNotEquals(originalGame.length, file(GamesInProgress.gameFile(1)).length());

		File entryDir = file("recovery/" + onlyRecoveryEntry());
		assertArrayEquals(originalGame, readBytes(new File(entryDir, "game.dat")));
		assertArrayEquals(originalDepth, readBytes(new File(entryDir, "depth1.dat")));
	}

	@Test
	public void deleteGameRestoreLoadThenTamper_lifecycleKeepsRecoveryReadableAndRejectsTamper() throws Exception {
		writeSaveSlot(1, 101, 102);
		useRealMobLogFileSink();

		triggerMobEvents();

		assertEquals(4, logger.size());
		assertTrue(file(MobBehaviourLogger.DEFAULT_LOG_FILE).exists());

		Dungeon.deleteGame(1, true);

		assertFalse(GamesInProgress.gameExists(1));
		assertArrayEquals(new String[]{"game*/game.dat"},
				FileUtils.bundleFromFile(MANIFEST).getStringArray("paths"));
		String entry = onlyRecoveryEntry();

		RecoveryManager.restoreEntry(entry, 1);

		assertArrayEquals(new String[]{"game*/depth1.dat", "game*/game.dat"},
				FileUtils.bundleFromFile(MANIFEST).getStringArray("paths"));
		assertEquals(101, FileUtils.bundleFromFile(GamesInProgress.gameFile(1)).getInt("value"));
		assertEquals(102, FileUtils.bundleFromFile(GamesInProgress.depthFile(1, 1, 0)).getInt("value"));

		writeText(GamesInProgress.gameFile(1), "{\"value\":999}");

		assertBundleRejected(GamesInProgress.gameFile(1));
	}

	private void triggerMobEvents() {
		TestMob mob = new TestMob();
		TestMob target = new TestMob();
		mob.pos = 7;
		target.pos = 8;
		mob.onAdd();
		mob.setState(mob.HUNTING, "integration");
		mob.setEnemy(target, "integration");
		mob.markAlerted("integration");
	}

	private void useRealMobLogFileSink() {
		logger.setFileSink(line -> FileUtils.getFileHandle(MobBehaviourLogger.DEFAULT_LOG_FILE)
				.writeString(line + System.lineSeparator(), true));
	}

	private void writeSaveSlot( int slot, int gameValue, int depthValue ) throws IOException {
		FileUtils.bundleToFile(GamesInProgress.gameFile(slot), bundle(gameValue));
		FileUtils.bundleToFile(GamesInProgress.depthFile(slot, 1, 0), bundle(depthValue));
	}

	private void deleteSlotThroughFileUtils( int slot ) {
		FileUtils.deleteFile(GamesInProgress.depthFile(slot, 1, 0));
		FileUtils.overwriteFile(GamesInProgress.gameFile(slot), 1);
		GamesInProgress.delete(slot);
	}

	private String onlyRecoveryEntry() {
		ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
		assertEquals(1, entries.size());
		return entries.get(0);
	}

	private static Bundle bundle( int value ) {
		Bundle bundle = new Bundle();
		bundle.put("value", value);
		return bundle;
	}

	private File file( String relative ) {
		return new File(tempFolder.getRoot(), relative);
	}

	private void writeText( String relative, String text ) throws IOException {
		File target = file(relative);
		File parent = target.getParentFile();
		if (parent != null) {
			assertTrue(parent.exists() || parent.mkdirs());
		}
		FileOutputStream output = new FileOutputStream(target);
		try {
			output.write(text.getBytes(StandardCharsets.UTF_8));
		} finally {
			output.close();
		}
	}

	private byte[] readBytes( String relative ) throws IOException {
		return readBytes(file(relative));
	}

	private byte[] readBytes( File source ) throws IOException {
		FileInputStream input = new FileInputStream(source);
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
		return output.toByteArray();
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

	private static class TestMob extends Mob {
		TestMob() {
			HP = HT = 10;
			EXP = 1;
			defenseSkill = 5;
		}

		@Override
		public int damageRoll() {
			return 1;
		}

		@Override
		public int attackSkill( Char target ) {
			return 1;
		}

		@Override
		public String name() {
			return "TestMob";
		}
	}
}
