package dev.serko.safariutils.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Central paths and one-time migration for every SafariUtils config file and log. */
public final class SafariPaths {

	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir();
	private static final Path ROOT = CONFIG.resolve("safariutils");
	private static final Path LOGS = ROOT.resolve("logs");
	private static final Path BACKUPS = ROOT.resolve("backups");
	private static final Path RUN_BACKUPS = BACKUPS.resolve("runs");
	private static final Path SETTINGS_BACKUPS = BACKUPS.resolve("settings");

	private SafariPaths() {
	}

	public static Path settings() {
		return ROOT.resolve("safariutils.json");
	}

	public static Path runHistory() {
		return ROOT.resolve("safariutils-runs.json");
	}

	public static Path sparklingStats() {
		return ROOT.resolve("safariutils-sparkling.json");
	}

	public static Path staticEntities() {
		return ROOT.resolve("safariutils-static-entities.json");
	}

	public static Path logs() {
		return LOGS;
	}

	public static Path runBackups() {
		return RUN_BACKUPS;
	}

	public static Path settingsBackups() {
		return SETTINGS_BACKUPS;
	}

	/** Returns the first unused JSON backup path, preserving every earlier backup. */
	public static Path uniqueJsonBackup(Path directory, String stem) {
		Path candidate = directory.resolve(stem + ".json");
		for (int part = 2; Files.exists(candidate); part++) {
			candidate = directory.resolve(stem + "_P" + part + ".json");
		}
		return candidate;
	}

	public static Path operationalLog() {
		return LOGS.resolve("safariutils.log");
	}

	public static Path operationalLog(String sessionName, int part) {
		String suffix = part <= 1 ? "" : "_P" + part;
		return LOGS.resolve(sessionName + suffix + ".log");
	}

	/** Moves legacy files into the organized layout without overwriting any destination. */
	public static void migrateLegacyFiles() {
		try {
			Files.createDirectories(LOGS);
			Files.createDirectories(RUN_BACKUPS);
			Files.createDirectories(SETTINGS_BACKUPS);
			moveIfNeeded(CONFIG.resolve("safariutils.json"), settings());
			moveIfNeeded(CONFIG.resolve("safariutils-runs.json"), runHistory());
			moveLegacyOutputLogs(CONFIG.resolve("safariutils-debug-logs"));
			moveRootBackups("safariutils-runs-backup-", RUN_BACKUPS);
			moveRootBackups("safariutils-settings-backup-", SETTINGS_BACKUPS);
		} catch (IOException migrationError) {
			OperationalLog.error("CONFIG/MIGRATION", migrationError);
		}
	}

	private static void moveIfNeeded(Path source, Path destination) throws IOException {
		if (!Files.isRegularFile(source) || Files.exists(destination)) return;
		Files.move(source, destination);
	}

	private static void moveLegacyOutputLogs(Path legacyDirectory) throws IOException {
		if (!Files.isDirectory(legacyDirectory)) return;
		try (var files = Files.list(legacyDirectory)) {
			for (Path source : files.filter(Files::isRegularFile).toList()) {
				moveIfNeeded(source, LOGS.resolve(source.getFileName()));
			}
		}
		try (var remaining = Files.list(legacyDirectory)) {
			if (remaining.findAny().isEmpty()) Files.delete(legacyDirectory);
		}
	}

	private static void moveRootBackups(String prefix, Path destinationDirectory) throws IOException {
		try (var files = Files.list(ROOT)) {
			for (Path source : files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().startsWith(prefix)).toList()) {
				String fileName = source.getFileName().toString();
				int extension = fileName.lastIndexOf('.');
				String stem = extension < 0 ? fileName : fileName.substring(0, extension);
				String suffix = extension < 0 ? "" : fileName.substring(extension);
				Path destination = destinationDirectory.resolve(fileName);
				for (int part = 2; Files.exists(destination); part++) {
					destination = destinationDirectory.resolve(stem + "_P" + part + suffix);
				}
				Files.move(source, destination);
			}
		}
	}
}
