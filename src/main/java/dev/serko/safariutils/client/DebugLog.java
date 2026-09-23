package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes timestamped entity and event diagnostics to a new file per enabled session.
 * Callers may log unconditionally; this class owns the enabled check.
 */
public final class DebugLog {

	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
	private static final DateTimeFormatter LINE_STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final long FLUSH_INTERVAL_MILLIS = 1_000L;
	private static final int FLUSH_LINE_LIMIT = 128;

	private static PrintWriter writer;
	private static boolean wasEnabled;
	private static int pendingLines;
	private static long lastFlushAt;
	private static long retryAt;

	private DebugLog() {
	}

	/** Opens or closes the file to match the setting; cheap enough to call every tick. */
	public static void tick() {
		boolean enabled = isEnabled();
		if (enabled == wasEnabled) {
			if (enabled && writer == null && System.currentTimeMillis() >= retryAt) open();
			if (enabled && writer != null && pendingLines > 0
				&& System.currentTimeMillis() - lastFlushAt >= FLUSH_INTERVAL_MILLIS) flush();
			return;
		}
		wasEnabled = enabled;
		if (enabled) open();
		else close();
	}

	public static boolean isEnabled() {
		return BuildVersion.DEVELOPER
			&& ConfigManager.get().advanced.debugLog
			&& AdvancedUnlock.isUnlocked();
	}

	private static void open() {
		try {
			Path dir = SafariPaths.logs();
			Files.createDirectories(dir);
			String base = "debug-" + LocalDateTime.now().format(FILE_STAMP);
			Path file = dir.resolve(base + ".log");
			for (int suffix = 2; Files.exists(file); suffix++) file = dir.resolve(base + "-" + suffix + ".log");
			writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
			retryAt = 0;
			lastFlushAt = System.currentTimeMillis();
			pendingLines = 0;
			line("LOG", "started, writing to " + file);
		} catch (IOException e) {
			writer = null;
			retryAt = System.currentTimeMillis() + 5_000L;
			OperationalLog.error("DEBUG_LOG/OPEN", e);
		}
	}

	private static void close() {
		if (writer == null) return;
		line("LOG", "stopped");
		flush();
		if (writer != null) writer.close();
		writer = null;
	}

	/** Flush the final partial buffer when Minecraft exits normally. */
	public static void shutdown() {
		close();
	}

	/**
	 * Writes one timestamped line, tagged with a short category so a file can be
	 * searched by what kind of thing it is — {@code CHAT}, {@code SIGHTING},
	 * {@code RECATCH}, {@code STILL}, {@code WALL}. A no-op when logging is off, or
	 * when the category itself is switched off in Output Log Options, so every call
	 * site can log unconditionally without its own check either way.
	 */
	public static void line(String category, String message) {
		if (writer == null) return;
		if (!categoryEnabled(category)) return;
		writer.println("[" + LocalDateTime.now().format(LINE_STAMP) + "] " + pad(category) + message);
		if (++pendingLines >= FLUSH_LINE_LIMIT) flush();
	}

	private static void flush() {
		if (writer == null) return;
		writer.flush();
		pendingLines = 0;
		lastFlushAt = System.currentTimeMillis();
		if (writer.checkError()) {
			writer.close();
			writer = null;
			retryAt = System.currentTimeMillis() + 5_000L;
			OperationalLog.error("DEBUG_LOG/WRITE", new IOException("Could not write debug log"));
		}
	}

	/**
	 * Whether {@code category} is one of the ones Output Log Options can actually
	 * turn off. {@code LOG} itself (the open/close lines this file always writes)
	 * is deliberately not one of them — it says whether the file itself started or
	 * stopped, which is not optional the way everything written into it is.
	 */
	private static boolean categoryEnabled(String category) {
		SafariConfig.AdvancedConfig advanced = ConfigManager.get().advanced;
		return switch (category) {
			case "RAW" -> advanced.logRaw;
			case "CHAT" -> advanced.logChat;
			case "RUN" -> advanced.logRun;
			case "OBJECTIVE" -> advanced.logObjectives;
			case "ACTIVATE", "JOINTIME" -> advanced.logActivation;
			case "LOCATION" -> advanced.logLocation;
			case "PARTY" -> advanced.logPartyRoster;
			case "PARTYTIME" -> advanced.logPartyTiming;
			case "SYNC" -> advanced.logPartySync;
			case "PARTYAPI" -> advanced.logPartyApi;
			case "INTERACT" -> advanced.logInterfaces;
			case "TABLIST" -> advanced.logTabList;
			case "SCORE" -> advanced.logScoreboard;
			case "INVENTORY" -> advanced.logInventory;
			case "SIGHTING+", "SIGHTING-", "SIGHTING~" -> advanced.logSighting;
			case "NEARBY" -> advanced.logNearby;
			case "PAIR" -> advanced.logPair;
			case "BALL" -> advanced.logBall;
			case "RECATCH" -> advanced.logRecatch;
			case "DRAW" -> advanced.logDraw;
			case "STILL" -> advanced.logStill;
			case "WALL" -> advanced.logWall;
			case "FLOOR" -> advanced.logFloor;
			case "HIDEYHO" -> advanced.logHideyho;
			case "HEADSTART" -> advanced.logHeadstart;
			case "NEST" -> advanced.logNest;
			case "COUNT" -> advanced.logCritterCounts;
			case "WAYPOINT" -> advanced.logStaticWaypoints;
			case "PARTICLE" -> advanced.logParticles;
			case "SPARKLING" -> advanced.logSparkling;
			case "PKT-TRANS" -> advanced.logPacketTransitions;
			case "PKT-HUD" -> advanced.logPacketHud;
			case "PKT-INV" -> advanced.logPacketInventory;
			case "PKT-ENTITY" -> advanced.logPacketEntities;
			case "PKT-WORLD" -> advanced.logPacketWorld;
			case "PKT-CHANNEL" -> advanced.logPacketChannels;
			default -> true;
		};
	}

	private static String pad(String category) {
		return (category + " ".repeat(10)).substring(0, Math.max(category.length(), 10)) + " ";
	}
}
