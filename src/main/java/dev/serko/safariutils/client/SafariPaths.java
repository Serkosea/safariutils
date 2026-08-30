package dev.serko.safariutils.client;

import dev.serko.safariutils.SafariUtils;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Central paths and one-time migration for every SafariUtils config file and log. */
public final class SafariPaths {

	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir();
	private static final Path ROOT = CONFIG.resolve("safariutils");
	private static final Path LOGS = ROOT.resolve("logs");

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

	public static Path staticWaypoints() {
		return ROOT.resolve("safariutils-static-waypoints.json");
	}

	public static Path staticEntities() {
		return ROOT.resolve("safariutils-static-entities.json");
	}

	public static Path logs() {
		return LOGS;
	}

	/** Moves legacy files into the organized layout without overwriting any destination. */
	public static void migrateLegacyFiles() {
		try {
			Files.createDirectories(LOGS);
			moveIfNeeded(CONFIG.resolve("safariutils.json"), settings());
			moveIfNeeded(CONFIG.resolve("safariutils-runs.json"), runHistory());
			moveLegacyOutputLogs(CONFIG.resolve("safariutils-debug-logs"));
		} catch (IOException migrationError) {
			SafariUtils.LOGGER.error("Could not migrate SafariUtils config files", migrationError);
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
}
