package dev.serko.safariutils.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.io.RecoverySnapshot;
import dev.serko.safariutils.io.PersistentJson;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.client.SafariPaths;
import dev.serko.safariutils.BuildVersion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent lifetime totals, including catches predating saved run history. */
public final class SparklingStats {

	private static final int CURRENT_SCHEMA = 1;
	private static final String SCHEMA_KEY = "schemaVersion";
	private static final Gson GSON = new GsonBuilder().create();
	private static final long API_PROPAGATION_MILLIS = 10 * 60_000L;
	private static Data data = new Data();
	private static int unique;
	private static int total;
	private static Path file;
	private static JsonObject preservedRoot;
	private static boolean pendingUpgradeBackup;
	private static boolean pendingVersionBackup;
	private static boolean writesBlocked;
	private static final RecoverySnapshot recoverySnapshot = new RecoverySnapshot("safariutils-sparkling-recovery");

	private SparklingStats() {
	}

	public static void load(Path path) {
		file = path;
		preservedRoot = null;
		pendingUpgradeBackup = false;
		pendingVersionBackup = false;
		writesBlocked = false;
		recoverySnapshot.reset();
		if (path != null && Files.isRegularFile(path)) {
			try {
				try {
					PersistentJson.backupBeforeVersion(path, SafariPaths.sparklingBackups(),
						"safariutils-sparkling", BuildVersion.releaseVersion());
				} catch (Exception backupFailed) {
					pendingVersionBackup = true;
					OperationalLog.error("SPARKLING_STATS/VERSION_BACKUP", backupFailed);
				}
				JsonObject root = JsonParser.parseString(
					Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
				int schema = PersistentJson.schema(root, SCHEMA_KEY);
				if (schema > CURRENT_SCHEMA) {
					writesBlocked = true;
					OperationalLog.info("SPARKLING_STATS/FUTURE_SCHEMA",
						"Sparkling data schema is newer or unrecognized; using it read-only");
				}
				if (schema < CURRENT_SCHEMA) {
					try {
						PersistentJson.backupBeforeUpgrade(path, SafariPaths.sparklingBackups(),
							"safariutils-sparkling", CURRENT_SCHEMA);
					} catch (Exception backupFailed) {
						pendingUpgradeBackup = true;
						OperationalLog.error("SPARKLING_STATS/BACKUP", backupFailed);
					}
					migrateRoot(root, schema);
				}
				Decoded loaded = decodeStore(root);
				data = loaded.data();
				writesBlocked |= loaded.rejected() > 0;
				if (loaded.rejected() > 0) OperationalLog.info("SPARKLING_STATS/PARTIAL",
					"Recovered readable counters; protected source; rejected=" + loaded.rejected());
				preservedRoot = root.deepCopy();
				normalize();
				rebuildTotals();
				if (schema < CURRENT_SCHEMA) save();
				return;
			} catch (Exception unreadable) {
				OperationalLog.error("SPARKLING_STATS/LOAD", unreadable);
				boolean preserved = false;
				try {
					Path backup = dev.serko.safariutils.client.SafariPaths.quarantineUnreadableJson(
						path, dev.serko.safariutils.client.SafariPaths.sparklingBackups(),
						"safariutils-sparkling");
					preserved = backup != null;
					if (backup != null) OperationalLog.info("SPARKLING_STATS/RECOVERY",
						"Preserved unreadable stats at " + backup.toAbsolutePath());
				} catch (Exception backupFailed) {
					OperationalLog.error("SPARKLING_STATS/RECOVERY", backupFailed);
				}
				writesBlocked |= !preserved && Files.exists(path);
			}
		}

		// Seed the ledger once from every run that was already saved before it existed.
		data = new Data();
		preservedRoot = null;
		unique = 0;
		total = 0;
		for (RunRecord run : RunHistory.runs()) {
			if (run.sparklings != null) {
				for (RunRecord.SparklingRecord sparkling : run.sparklings) {
					if (Critters.byName(sparkling.species()) != null) increment(sparkling.species());
				}
			}
			data.rainbowFeathers += Math.max(0, run.rainbowFeathers);
		}
		save();
	}

	public static void recordSparkling(Critter critter) {
		if (critter == null) return;
		boolean duplicate = count(critter) > 0;
		int previousDuplicates = knownDuplicates();
		increment(critter.name());
		if (duplicate && data.importedDuplicates >= 0) {
			data.importedDuplicates = previousDuplicates + 1;
			if (data.importedSetDuplicates >= 0) data.importedSetDuplicates++;
		}
		// Hypixel's profile endpoint can trail a newly caught Sparkling. Require a
		// genuinely newer API snapshot before offering to replace local collection data.
		data.apiComparisonNotBefore = Math.max(data.apiComparisonNotBefore,
			System.currentTimeMillis() + API_PROPAGATION_MILLIS);
		save();
	}

	/** Whether this response is new enough to compare after the latest local catch. */
	public static boolean apiComparisonAllowed(long fetchedAt) {
		return data.apiComparisonNotBefore == 0L || fetchedAt >= data.apiComparisonNotBefore;
	}

	public static void recordRainbowFeather() {
		data.rainbowFeathers++;
		save();
	}

	public static int count(Critter critter) {
		if (critter == null) return 0;
		Integer count = data.species.get(critter.name());
		return count == null ? 0 : Math.max(0, count);
	}

	public static int unique() {
		return unique;
	}

	public static int total() {
		return total;
	}

	public static int duplicates() {
		return Math.max(0, total() - unique());
	}

	/** API-imported aggregate, or {@code -1} when only per-species totals are known. */
	public static int importedDuplicates() {
		return data.importedDuplicates < 0 ? -1 : knownDuplicates();
	}

	public static boolean hasImportedDuplicates() {
		return data.importedDuplicates >= 0;
	}

	public static boolean importedSetUnchanged() {
		return data.importedSetDuplicates < 0 || duplicates() == data.importedSetDuplicates;
	}

	/** Both sources are lower bounds; delayed API results must never undo catches. */
	public static int knownDuplicates() {
		return Math.max(duplicates(), data.importedDuplicates);
	}

	public static boolean apiCollectionAddsInformation(java.util.Set<String> species, int duplicates) {
		if (duplicates > knownDuplicates()) return true;
		for (Critter critter : Critters.all()) {
			String id = critter.name().trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
			if (count(critter) == 0 && species.contains(id)) return true;
		}
		return false;
	}

	/** Imports ownership without assigning aggregate duplicates to arbitrary species. */
	public static void importApiCollection(java.util.Set<String> species, int duplicates) {
		int previousDuplicates = knownDuplicates();
		for (Critter critter : Critters.all()) {
			String id = critter.name().trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
			if (species.contains(id) && count(critter) == 0) data.species.put(critter.name(), 1);
		}
		rebuildTotals();
		data.importedDuplicates = Math.max(previousDuplicates, duplicates);
		data.importedSetDuplicates = SparklingStats.duplicates();
		data.apiComparisonNotBefore = 0L;
		save();
	}

	public static int rainbowFeathers() {
		return Math.max(0, data.rainbowFeathers);
	}

	public static boolean set(Critter critter, int count) {
		if (critter == null || count < 0) return false;
		int previous = SparklingStats.count(critter);
		if (count == 0) data.species.remove(critter.name());
		else data.species.put(critter.name(), count);
		total += count - previous;
		if (previous == 0 && count > 0) unique++;
		else if (previous > 0 && count == 0) unique--;
		save();
		return true;
	}

	public static boolean setRainbowFeathers(int count) {
		if (count < 0) return false;
		data.rainbowFeathers = count;
		save();
		return true;
	}

	private static void increment(String species) {
		Integer stored = data.species.get(species);
		int previous = stored == null ? 0 : Math.max(0, stored);
		data.species.put(species, previous + 1);
		if (previous == 0) unique++;
		total++;
	}

	private static void normalize() {
		if (data.schemaVersion <= CURRENT_SCHEMA) data.schemaVersion = CURRENT_SCHEMA;
		if (data.species == null) data.species = new LinkedHashMap<>();
		else data.species.entrySet().removeIf(entry -> entry.getKey() == null
			|| entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0);
		data.rainbowFeathers = Math.max(0, data.rainbowFeathers);
		data.importedDuplicates = Math.max(-1, data.importedDuplicates);
		data.importedSetDuplicates = Math.max(-1, data.importedSetDuplicates);
		data.apiComparisonNotBefore = Math.max(0L, data.apiComparisonNotBefore);
	}

	/** Ordered schema steps; append a case whenever the persisted model changes. */
	private static void migrateRoot(JsonObject root, int version) {
		while (version < CURRENT_SCHEMA) {
			switch (version) {
				case 0 -> { /* Schema one adds explicit versioning without changing totals. */ }
				default -> throw new IllegalStateException(
					"Missing Sparkling-data migration from schema " + version);
			}
			root.addProperty(SCHEMA_KEY, ++version);
		}
	}

	private static void rebuildTotals() {
		unique = 0;
		total = 0;
		for (Critter critter : Critters.all()) {
			int count = count(critter);
			if (count > 0) unique++;
			total += count;
		}
	}

	private static void save() {
		if (file == null) return;
		if (writesBlocked) {
			try {
				recoverySnapshot.write(SafariPaths.sparklingBackups(), GSON.toJson(data));
				OperationalLog.info("SPARKLING_STATS/RECOVERY_SAVED",
					"Saved working counters separately; protected source unchanged");
			} catch (Exception failed) {
				OperationalLog.error("SPARKLING_STATS/RECOVERY_SAVE", failed);
			}
			return;
		}
		try {
			if (pendingVersionBackup && Files.isRegularFile(file)) {
				PersistentJson.backupBeforeVersion(file, SafariPaths.sparklingBackups(),
					"safariutils-sparkling", BuildVersion.releaseVersion());
				pendingVersionBackup = false;
			}
			if (pendingUpgradeBackup) {
				PersistentJson.backupBeforeUpgrade(file, SafariPaths.sparklingBackups(),
					"safariutils-sparkling", CURRENT_SCHEMA);
				pendingUpgradeBackup = false;
			}
			JsonObject written = PersistentJson.mergeRecord(preservedRoot,
				GSON.toJsonTree(data).getAsJsonObject());
			AtomicFiles.writeString(file, GSON.toJson(written));
			preservedRoot = written;
		} catch (Exception failed) {
			OperationalLog.error("SPARKLING_STATS/SAVE", failed);
		}
	}

	/** Startup invariant for schema and future-field preservation. */
	public static void verifyPersistenceModel() {
		Data probe = new Data();
		probe.species.put("Cavernfish", 2);
		probe.rainbowFeathers = 7;
		JsonObject future = new JsonObject();
		future.addProperty(SCHEMA_KEY, CURRENT_SCHEMA + 2);
		future.addProperty("futureSparklingValue", "keep-me");
		JsonObject encoded = PersistentJson.mergeRecord(future,
			GSON.toJsonTree(probe).getAsJsonObject());
		// Preserve a future schema just as a newer file would carry it in Data.
		encoded.addProperty(SCHEMA_KEY, CURRENT_SCHEMA + 2);
		Data decoded = GSON.fromJson(encoded, Data.class);
		if (decoded == null || decoded.species.getOrDefault("Cavernfish", 0) != 2
				|| decoded.rainbowFeathers != 7
				|| !"keep-me".equals(encoded.get("futureSparklingValue").getAsString())) {
			throw new IllegalStateException("Sparkling data did not round-trip losslessly");
		}
	}

	/** Decode counters independently; callers must protect any partially readable source. */
	static Decoded decodeStore(JsonObject root) {
		JsonObject safe = new JsonObject();
		safe.addProperty(SCHEMA_KEY, CURRENT_SCHEMA);
		int rejected = 0;
		for (String field : java.util.List.of("rainbowFeathers", "importedDuplicates",
				"importedSetDuplicates", "apiComparisonNotBefore")) {
			if (!root.has(field)) continue;
			try {
				long minimum = field.startsWith("imported") ? -1 : 0;
				long maximum = field.equals("apiComparisonNotBefore") ? Long.MAX_VALUE : Integer.MAX_VALUE;
				safe.addProperty(field, counter(root.get(field), minimum, maximum));
			} catch (RuntimeException invalid) { rejected++; }
		}
		JsonObject species = new JsonObject();
		if (root.has("species")) {
			if (!root.get("species").isJsonObject()) rejected++;
			else for (var entry : root.getAsJsonObject("species").entrySet()) {
				try {
					if (entry.getKey().isBlank()) throw new IllegalArgumentException();
					species.addProperty(entry.getKey(), counter(entry.getValue(), 0, Integer.MAX_VALUE));
				} catch (RuntimeException invalid) { rejected++; }
			}
		}
		safe.add("species", species);
		return new Decoded(GSON.fromJson(safe, Data.class), rejected);
	}

	private static long counter(com.google.gson.JsonElement value, long minimum, long maximum) {
		if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException();
		long count = value.getAsBigDecimal().longValueExact();
		if (count < minimum || count > maximum) throw new IllegalArgumentException();
		return count;
	}

	record Decoded(Data data, int rejected) { }

	static final class Data {
		int schemaVersion = CURRENT_SCHEMA;
		Map<String, Integer> species = new LinkedHashMap<>();
		int rainbowFeathers;
		int importedDuplicates = -1;
		int importedSetDuplicates = -1;
		long apiComparisonNotBefore;
	}
}
