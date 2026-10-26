package dev.serko.safariutils.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.CritterSpawnRanges;
import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.io.RecoverySnapshot;
import dev.serko.safariutils.io.PersistentJson;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.client.SafariPaths;
import dev.serko.safariutils.BuildVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists completed {@link RunRecord}s across restarts. Active runs are never saved,
 * and incomplete history is protected without disrupting live tracking.
 */
public final class RunHistory {
	private static final int CURRENT_SCHEMA = 1;
	private static final String SCHEMA_KEY = "schemaVersion";
	private static final String RUNS_KEY = "runs";

	/** High enough that normal use never trims history while still bounding corrupted/imported input. */
	private static final int MAX_RUNS = 99_999;
	/** Runs with nothing in them are noise — leaving and re-entering makes plenty. */
	private static final int MIN_CATCHES = 1;

	/** Compact output cuts large histories substantially without changing their data. */
	private static final Gson GSON = new GsonBuilder().create();

	private static final List<RunRecord> runs = new ArrayList<>();
	private static final List<RunRecord> RUNS_VIEW = Collections.unmodifiableList(runs);
	/** Aggregates rebuilt only when history changes, never once per rendered frame. */
	private static List<SpeciesStat> speciesStats = List.of();
	private static Map<Critter, SpeciesStat> speciesStatsByCritter = Map.of();
	private static int pricedRuns;
	private static long totalTimeMillis;
	private static int totalCatches;
	private static int totalShards;
	private static int totalSafariEssence;
	private static int totalRainbowFeathers;
    private static int totalEmeraldDyes;
    public static int totalEmeraldDyes() { return totalEmeraldDyes; }
	private static int totalSparklings;
	private static int runsSinceLastSparkling = -1;
	/** Monotonic view invalidation token; unlike size, this also catches a same-size reload. */
	private static long revision;
	private static Path file;
	private static JsonObject preservedRoot;
	private static boolean pendingUpgradeBackup;
	private static boolean pendingVersionBackup;
	private static boolean writesBlocked;
	/** New runs must remain durable even when the source cannot safely be rewritten. */
	private static final List<RunRecord> recoveryRuns = new ArrayList<>();
	private static final RecoverySnapshot recoverySnapshot = new RecoverySnapshot("safariutils-runs-recovered");

	/** One species' lifetime total across every saved run. */
	public record SpeciesStat(Critter critter, int total) {
	}

	private RunHistory() {
	}

	/** Points the history at a file and reads whatever is in it. */
	public static synchronized void load(Path path) {
		file = path;
		runs.clear();
		preservedRoot = null;
		pendingUpgradeBackup = false;
		pendingVersionBackup = false;
		writesBlocked = false;
		recoveryRuns.clear();
		recoverySnapshot.reset();
		if (path == null || !Files.isRegularFile(path)) {
			rebuildStats();
			return;
		}

		boolean migratedStore = false;
		try {
			try {
				PersistentJson.backupBeforeVersion(path, SafariPaths.runBackups(),
					"safariutils-runs", BuildVersion.releaseVersion());
			} catch (IOException backupFailed) {
				// A snapshot failure must not make otherwise valid history unreadable.
				pendingVersionBackup = true;
				OperationalLog.error("HISTORY/VERSION_BACKUP", backupFailed);
			}
			LoadedHistory loaded = readStore(path);
			preservedRoot = loaded.root();
			writesBlocked = loaded.readOnly();
			if (PersistentJson.schema(preservedRoot, SCHEMA_KEY) > CURRENT_SCHEMA) {
				writesBlocked = true;
				OperationalLog.info("HISTORY/FUTURE_SCHEMA",
					"Run history schema is newer or unrecognized; using it read-only");
			}
			if (loaded.rejected() > 0) {
				OperationalLog.info("HISTORY/PARTIAL_LOAD", "readable=" + loaded.runs().size()
					+ " rejected=" + loaded.rejected() + " source=read-only");
			}
			runs.addAll(loaded.runs());
			migratedStore = loaded.migrated();
			if (loaded.migrated()) {
				try {
					PersistentJson.backupBeforeUpgrade(path, SafariPaths.runBackups(),
						"safariutils-runs", CURRENT_SCHEMA);
				} catch (IOException backupFailed) {
					pendingUpgradeBackup = true;
					OperationalLog.error("HISTORY/BACKUP", backupFailed);
				}
			}
		} catch (IOException | RuntimeException unreadable) {
			runs.clear();
			OperationalLog.error("HISTORY/LOAD", unreadable);
			boolean preserved = false;
			try {
				Path backup = dev.serko.safariutils.client.SafariPaths.quarantineUnreadableJson(
					path, dev.serko.safariutils.client.SafariPaths.runBackups(),
					"safariutils-runs");
				preserved = backup != null;
				if (backup != null) OperationalLog.info("HISTORY/RECOVERY",
					"Preserved unreadable history at " + backup.toAbsolutePath());
			} catch (IOException backupFailed) {
				OperationalLog.error("HISTORY/RECOVERY", backupFailed);
			}
			writesBlocked |= !preserved && Files.exists(path);
		}
		validateExpectedRanges("load", runs);
		rebuildStats();
		if (migratedStore) save();
	}

	private static LoadedHistory readStore(Path path) throws IOException {
		return readStore(path, true);
	}

	private static LoadedHistory readStore(Path path, boolean normalizeRecords) throws IOException {
		JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
		return decodeStore(parsed, normalizeRecords);
	}

	static LoadedHistory decodeStore(JsonElement parsed, boolean normalizeRecords) {
		JsonObject root;
		JsonArray entries;
		boolean migrated = false;
		if (parsed.isJsonArray()) {
			root = new JsonObject();
			root.addProperty(SCHEMA_KEY, 0);
			entries = parsed.getAsJsonArray();
			root.add(RUNS_KEY, entries.deepCopy());
			migrated = true;
		} else if (parsed.isJsonObject()) {
			root = parsed.getAsJsonObject().deepCopy();
			if (!root.has(RUNS_KEY) || !root.get(RUNS_KEY).isJsonArray()) {
				if (PersistentJson.schema(root, SCHEMA_KEY) > CURRENT_SCHEMA) {
					return new LoadedHistory(root, List.of(), false, 0, true);
				}
				throw new IllegalArgumentException("History root has no runs array");
			}
			entries = root.getAsJsonArray(RUNS_KEY);
		} else {
			throw new IllegalArgumentException("History root is neither an object nor an array");
		}
		int schema = PersistentJson.schema(root, SCHEMA_KEY);
		if (schema < CURRENT_SCHEMA) {
			migrateRoot(root, schema);
			migrated = true;
		}
		List<RunRecord> loaded = new ArrayList<>(entries.size());
		int rejected = 0;
		for (JsonElement entry : entries) {
			if (entry == null || !entry.isJsonObject()) { rejected++; continue; }
			try {
				RunRecord record = RunRecord.fromJson(entry.getAsJsonObject());
				if (record == null) { rejected++; continue; }
				if (normalizeRecords) record.normalize();
				loaded.add(record);
			} catch (RuntimeException invalidRecord) {
				// Recover readable neighbors, but never persist this incomplete view over its source.
				rejected++;
			}
		}
		return new LoadedHistory(root, List.copyOf(loaded), migrated, rejected,
			rejected > 0 || schema > CURRENT_SCHEMA);
	}

	record LoadedHistory(JsonObject root, List<RunRecord> runs, boolean migrated, int rejected, boolean readOnly) {
		void requireWritable() throws IOException {
			if (readOnly) throw new IOException("Run history is incomplete or uses an unsupported schema; repair refused");
		}
	}

	/** Ordered schema steps; append a case whenever the envelope changes. */
	private static void migrateRoot(JsonObject root, int version) {
		while (version < CURRENT_SCHEMA) {
			switch (version) {
				case 0 -> { /* The first schema introduces only the versioned envelope. */ }
				default -> throw new IllegalStateException(
					"Missing run-history migration from schema " + version);
			}
			root.addProperty(SCHEMA_KEY, ++version);
		}
	}

	/**
	 * Saves a finished run.
	 *
	 * <p>An empty run is dropped. Walking through the entrance and out again produces
	 * one, and a history full of those buries the runs that happened.
	 */
	public static synchronized void record(SafariSession session) {
		if (session == null) return;
		boolean diagnostics = dev.serko.safariutils.client.DebugLog.isEnabled();
        long began = diagnostics ? System.nanoTime() : 0;
		RunRecord record = RunRecord.of(session);
		if (record.partyTotal() < MIN_CATCHES && record.safariEssence == 0
			&& record.rainbowFeathers == 0 && record.rareDrops.isEmpty()
			&& (record.sparklings == null || record.sparklings.isEmpty())) return;

		runs.add(record);
		if (writesBlocked) recoveryRuns.add(record);
		validateExpectedRanges("save", List.of(record));
		while (runs.size() > MAX_RUNS) runs.removeFirst();
        long snapshotDone = diagnostics ? System.nanoTime() : 0;
        rebuildStats();
        long statsDone = diagnostics ? System.nanoTime() : 0;
        save();
        if (diagnostics) dev.serko.safariutils.client.DebugLog.line("RUN/SAVE_TIMING",
            "runs="+runs.size()+" snapshotMicros="+(snapshotDone-began)/1000
            +" statsMicros="+(statsDone-snapshotDone)/1000+" saveMicros="+(System.nanoTime()-statsDone)/1000);
	}

	/** Reports suspicious counts without rewriting or rejecting historical data. */
	private static void validateExpectedRanges(String source, List<RunRecord> records) {
		int outliers = 0;
		StringBuilder examples = new StringBuilder();
		for (int runIndex = 0; runIndex < records.size(); runIndex++) {
			RunRecord run = records.get(runIndex);
			for (Critter critter : Critters.all()) {
				int caught = run.caught(critter);
				int maximum = CritterSpawnRanges.maximum(critter);
				if (caught <= maximum) continue;
				outliers++;
				if (examples.length() >= 240) continue;
				if (!examples.isEmpty()) examples.append("; ");
				examples.append('#').append(runIndex + 1).append(' ').append(critter.name())
					.append('=').append(caught).append('>').append(maximum);
			}
		}
		if (outliers > 0) {
			OperationalLog.debug("RANGE/HISTORY", source + " outliers=" + outliers
				+ " examples=" + examples);
		}
	}

	/** Saved runs, oldest first. */
	public static List<RunRecord> runs() {
		return RUNS_VIEW;
	}

	public static int size() {
		return runs.size();
	}

	/** Changes only when the saved history itself changes. */
	public static long revision() {
		return revision;
	}

	/** Drops everything, on disk as well. */
	public static synchronized void clear() {
		runs.clear();
		// Clear is an explicit destructive action initiated by the player.
		writesBlocked = false;
		recoveryRuns.clear();
		recoverySnapshot.reset();
		rebuildStats();
		save();
	}

	/**
	 * Saved runs that kept a shard breakdown, and so can be priced.
	 */
	public static int pricedRuns() {
		return pricedRuns;
	}

	// --- stats ---------------------------------------------------------------

	/** Every species with its totals across the saved runs, most-caught first. */
	public static List<SpeciesStat> speciesStats() {
		return speciesStats;
	}

	/** The stat for one species, so a view can look one up without scanning. */
	public static SpeciesStat statFor(Critter critter) {
		SpeciesStat stat = speciesStatsByCritter.get(critter);
		return stat != null ? stat : new SpeciesStat(critter, 0);
	}

	public static long totalTimeMillis() {
		return totalTimeMillis;
	}

	public static int totalCatches() {
		return totalCatches;
	}

	public static int totalShards() {
		return totalShards;
	}

	public static int totalSafariEssence() {
		return totalSafariEssence;
	}

	public static int totalRainbowFeathers() {
		return totalRainbowFeathers;
	}

	public static int totalSparklings() {
		return totalSparklings;
	}

	/** Completed saved runs after the newest run containing a Sparkling, or -1 if none do. */
	public static int runsSinceLastSparkling() {
		return runsSinceLastSparkling;
	}

	/** Rebuilds every aggregate in one pass after the underlying history changes. */
	private static void rebuildStats() {
		revision++;
		Map<Critter, Integer> values = new HashMap<>();
		for (Critter critter : Critters.all()) values.put(critter, 0);

		pricedRuns = 0;
		totalTimeMillis = 0;
		totalCatches = 0;
		totalShards = 0;
		totalSafariEssence = 0;
		totalRainbowFeathers = 0;
        totalEmeraldDyes = 0;
		totalSparklings = 0;
		runsSinceLastSparkling = -1;
		for (RunRecord run : runs) {
			if (run.hasShardData()) pricedRuns++;
			totalTimeMillis += run.durationMillis();
			totalCatches += run.partyTotal();
			totalShards += run.totalShards;
			totalSafariEssence += run.safariEssence;
			totalRainbowFeathers += run.rainbowFeathers;
            totalEmeraldDyes += run.emeraldDyes();
			totalSparklings += run.sparklings == null ? 0 : run.sparklings.size();
			for (Critter critter : Critters.all()) {
				int caught = run.caught(critter);
				if (caught == 0) continue;
				values.merge(critter, caught, Integer::sum);
			}
		}
		for (int i = runs.size() - 1, since = 0; i >= 0; i--, since++) {
			RunRecord run = runs.get(i);
			if (run.sparklings == null || run.sparklings.isEmpty()) continue;
			runsSinceLastSparkling = since;
			break;
		}

		List<SpeciesStat> ordered = new ArrayList<>();
		Map<Critter, SpeciesStat> byCritter = new HashMap<>();
		for (Critter critter : Critters.all()) {
			SpeciesStat stat = new SpeciesStat(critter, values.get(critter));
			ordered.add(stat);
			byCritter.put(critter, stat);
		}
		speciesStats = List.copyOf(ordered);
		speciesStatsByCritter = Map.copyOf(byCritter);
	}

	private static void save() {
		if (file == null) return;
		if (writesBlocked) {
			OperationalLog.info("HISTORY/SAVE_BLOCKED",
				"Run history source is protected; refusing to overwrite it");
			if (!recoveryRuns.isEmpty()) {
				try {
					recoverySnapshot.write(SafariPaths.runBackups(), GSON.toJson(storeJson(recoveryRuns, null)));
					OperationalLog.info("HISTORY/RECOVERY_SAVED", "newRuns=" + recoveryRuns.size());
				} catch (IOException | RuntimeException failed) {
					OperationalLog.error("HISTORY/RECOVERY_SAVE", failed);
				}
			}
			return;
		}
		try {
			if (pendingVersionBackup && Files.isRegularFile(file)) {
				PersistentJson.backupBeforeVersion(file, SafariPaths.runBackups(),
					"safariutils-runs", BuildVersion.releaseVersion());
				pendingVersionBackup = false;
			}
			if (pendingUpgradeBackup) {
				PersistentJson.backupBeforeUpgrade(file, SafariPaths.runBackups(),
					"safariutils-runs", CURRENT_SCHEMA);
				pendingUpgradeBackup = false;
			}
			JsonObject written = storeJson(runs, preservedRoot);
			AtomicFiles.writeString(file, GSON.toJson(written));
			preservedRoot = written;
		} catch (IOException | RuntimeException failed) {
			// Losing the history is not worth interrupting a run over.
			OperationalLog.error("HISTORY/SAVE", failed);
		}
	}

	/** Separate current-schema snapshot; never overlays the protected source document. */
	static void writeRecovery(Path path, List<RunRecord> records) throws IOException {
		AtomicFiles.writeString(path, GSON.toJson(storeJson(records, null)));
	}

	private static JsonObject storeJson(List<RunRecord> records, JsonObject source) {
		JsonArray entries = new JsonArray(records.size());
		for (RunRecord record : records) {
			if (record == null) throw new IllegalArgumentException("Cannot save incomplete run history");
			entries.add(record.toJson());
		}
		JsonObject known = new JsonObject();
		// Never advertise a lower schema merely because an older build opened a
		// newer file. Unknown root and record fields are retained alongside it.
		known.addProperty(SCHEMA_KEY,
			Math.max(CURRENT_SCHEMA, PersistentJson.schema(source, SCHEMA_KEY)));
		known.add(RUNS_KEY, entries);
		return PersistentJson.mergeRecord(source, known);
	}

	/** Startup invariant for legacy migration and future-field preservation. */
	public static void verifyPersistenceModel() {
		JsonObject legacyRecord = new JsonObject();
		legacyRecord.addProperty("started", 11L);
		legacyRecord.addProperty("ended", 29L);
		legacyRecord.addProperty("futureRecordValue", "keep-record");
		JsonArray legacy = new JsonArray();
		legacy.add(legacyRecord);
		LoadedHistory decoded = decodeStore(legacy, true);
		if (!decoded.migrated() || decoded.runs().size() != 1
				|| decoded.runs().getFirst().durationMillis() != 18L) {
			throw new IllegalStateException("Legacy run history did not migrate losslessly");
		}

		JsonObject futureRoot = decoded.root();
		futureRoot.addProperty(SCHEMA_KEY, CURRENT_SCHEMA + 3);
		futureRoot.addProperty("futureRootValue", "keep-root");
		JsonObject encoded = storeJson(decoded.runs(), futureRoot);
		if (PersistentJson.schema(encoded, SCHEMA_KEY) != CURRENT_SCHEMA + 3
				|| !"keep-root".equals(encoded.get("futureRootValue").getAsString())
				|| !"keep-record".equals(encoded.getAsJsonArray(RUNS_KEY).get(0)
					.getAsJsonObject().get("futureRecordValue").getAsString())) {
			throw new IllegalStateException("Future run-history fields were not preserved");
		}
	}

	/** Shared decoder for the audit tool so storage rules cannot diverge. */
	static List<RunRecord> readForIntegrity(Path path) throws IOException {
		LoadedHistory loaded = readStore(path, false);
		loaded.requireWritable();
		return new ArrayList<>(loaded.runs());
	}

	/** Shared writer for safe repairs, preserving root and per-run future fields. */
	static void writeForIntegrity(Path path, List<RunRecord> records) throws IOException {
		JsonObject source = null;
		if (Files.isRegularFile(path)) {
			LoadedHistory loaded = readStore(path, false);
			loaded.requireWritable();
			source = loaded.root();
		}
		AtomicFiles.writeString(path, GSON.toJson(storeJson(records, source)));
	}
}
