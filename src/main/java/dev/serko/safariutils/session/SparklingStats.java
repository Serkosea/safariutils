package dev.serko.safariutils.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.serko.safariutils.SafariUtils;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.io.AtomicFiles;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent lifetime totals, including catches predating saved run history. */
public final class SparklingStats {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data data = new Data();
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
				return;
			} catch (Exception unreadable) {
				SafariUtils.LOGGER.warn("Could not read Sparkling statistics", unreadable);
			}
		}

		// Seed the ledger once from every run that was already saved before it existed.
		data = new Data();
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
		increment(critter.name());
		save();
	}

	public static void recordRainbowFeather() {
		data.rainbowFeathers++;
		save();
	}

	public static int count(Critter critter) {
		return critter == null ? 0 : Math.max(0, data.species.getOrDefault(critter.name(), 0));
	}

	public static int unique() {
		return (int) Critters.all().stream().filter(critter -> count(critter) > 0).count();
	}

	public static int total() {
		return Critters.all().stream().mapToInt(SparklingStats::count).sum();
	}

	public static int duplicates() {
		return Math.max(0, total() - unique());
	}

	public static int rainbowFeathers() {
		return Math.max(0, data.rainbowFeathers);
	}

	public static boolean set(Critter critter, int count) {
		if (critter == null || count < 0) return false;
		if (count == 0) data.species.remove(critter.name());
		else data.species.put(critter.name(), count);
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
		data.species.merge(species, 1, Integer::sum);
	}

	private static void save() {
		if (file == null) return;
		try {
			AtomicFiles.writeString(file, GSON.toJson(data));
		} catch (Exception failed) {
			SafariUtils.LOGGER.warn("Could not save Sparkling statistics", failed);
		}
	}

	private static final class Data {
		Map<String, Integer> species = new LinkedHashMap<>();
		int rainbowFeathers;
	}
}
