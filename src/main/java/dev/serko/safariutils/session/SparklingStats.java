package dev.serko.safariutils.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.client.OperationalLog;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent lifetime totals, including catches predating saved run history. */
public final class SparklingStats {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long API_PROPAGATION_MILLIS = 10 * 60_000L;
	private static Data data = new Data();
	private static int unique;
	private static int total;
	private static Path file;

	private SparklingStats() {
	}

	public static void load(Path path) {
		file = path;
		if (path != null && Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded != null) data = loaded;
				if (data.species == null) data.species = new LinkedHashMap<>();
				rebuildTotals();
				return;
			} catch (Exception unreadable) {
				OperationalLog.error("SPARKLING_STATS/LOAD", unreadable);
			}
		}

		// Seed the ledger once from every run that was already saved before it existed.
		data = new Data();
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
		increment(critter.name());
		if (duplicate && data.importedDuplicates >= 0) {
			data.importedDuplicates++;
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
		return critter == null ? 0 : Math.max(0, data.species.getOrDefault(critter.name(), 0));
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
		return data.importedDuplicates;
	}

	public static boolean hasImportedDuplicates() {
		return data.importedDuplicates >= 0;
	}

	public static boolean importedSetUnchanged() {
		return data.importedSetDuplicates < 0 || duplicates() == data.importedSetDuplicates;
	}

	/** Imports ownership without assigning aggregate duplicates to arbitrary species. */
	public static void importApiCollection(java.util.Set<String> species, int duplicates) {
		for (Critter critter : Critters.all()) {
			String id = critter.name().trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
			if (species.contains(id) && count(critter) == 0) data.species.put(critter.name(), 1);
		}
		rebuildTotals();
		data.importedDuplicates = Math.max(-1, duplicates);
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
		int previous = Math.max(0, data.species.getOrDefault(species, 0));
		data.species.put(species, previous + 1);
		if (previous == 0) unique++;
		total++;
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
		try {
			AtomicFiles.writeString(file, GSON.toJson(data));
		} catch (Exception failed) {
			OperationalLog.error("SPARKLING_STATS/SAVE", failed);
		}
	}

	private static final class Data {
		Map<String, Integer> species = new LinkedHashMap<>();
		int rainbowFeathers;
		int importedDuplicates = -1;
		int importedSetDuplicates = -1;
		long apiComparisonNotBefore;
	}
}
