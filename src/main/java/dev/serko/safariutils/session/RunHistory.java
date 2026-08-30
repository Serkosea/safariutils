package dev.serko.safariutils.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.io.AtomicFiles;

import java.io.IOException;
import java.io.Reader;
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
 * and unreadable history is ignored without disrupting live tracking.
 */
public final class RunHistory {

	/** High enough that in practice nothing gets trimmed; the file stays a plain, readable list either way. */
	private static final int MAX_RUNS = 99_999;
	/** Runs with nothing in them are noise — leaving and re-entering makes plenty. */
	private static final int MIN_CATCHES = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final List<RunRecord> runs = new ArrayList<>();
	/** Aggregates rebuilt only when history changes, never once per rendered frame. */
	private static List<SpeciesStat> speciesStats = List.of();
	private static Map<Critter, SpeciesStat> speciesStatsByCritter = Map.of();
	private static int pricedRuns;
	private static long totalTimeMillis;
	private static int totalCatches;
	private static int totalShards;
	private static int totalSafariEssence;
	private static int totalRainbowFeathers;
	private static int totalSparklings;
	private static Path file;

	/** One species' lifetime total across every saved run. */
	public record SpeciesStat(Critter critter, int total) {
	}

	private RunHistory() {
	}

	/** Points the history at a file and reads whatever is in it. */
	public static void load(Path path) {
		file = path;
		runs.clear();
		if (path == null || !Files.isRegularFile(path)) {
			rebuildStats();
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			List<RunRecord> loaded = GSON.fromJson(reader,
				new TypeToken<List<RunRecord>>() {
				}.getType());
			if (loaded != null) runs.addAll(loaded);
		} catch (IOException | RuntimeException unreadable) {
			// Left in place rather than deleted: the next save overwrites it, and if
			// something else is wrong the file is still there to look at.
			runs.clear();
		}
		rebuildStats();
	}

	/**
	 * Saves a finished run.
	 *
	 * <p>An empty run is dropped. Walking through the entrance and out again produces
	 * one, and a history full of those buries the runs that happened.
	 */
	public static void record(SafariSession session) {
		if (session == null) return;
		RunRecord record = RunRecord.of(session);
		if (record.partyTotal() < MIN_CATCHES && record.safariEssence == 0
			&& record.rainbowFeathers == 0
			&& (record.sparklings == null || record.sparklings.isEmpty())) return;

		runs.add(record);
		while (runs.size() > MAX_RUNS) runs.removeFirst();
		rebuildStats();
		save();
	}

	/** Saved runs, oldest first. */
	public static List<RunRecord> runs() {
		return Collections.unmodifiableList(runs);
	}

	public static int size() {
		return runs.size();
	}

	/** Drops everything, on disk as well. */
	public static void clear() {
		runs.clear();
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

	/** Rebuilds every aggregate in one pass after the underlying history changes. */
	private static void rebuildStats() {
		Map<Critter, Integer> values = new HashMap<>();
		for (Critter critter : Critters.all()) values.put(critter, 0);

		pricedRuns = 0;
		totalTimeMillis = 0;
		totalCatches = 0;
		totalShards = 0;
		totalSafariEssence = 0;
		totalRainbowFeathers = 0;
		totalSparklings = 0;
		for (RunRecord run : runs) {
			if (run.hasShardData()) pricedRuns++;
			totalTimeMillis += run.durationMillis();
			totalCatches += run.partyTotal();
			totalShards += run.totalShards;
			totalSafariEssence += run.safariEssence;
			totalRainbowFeathers += run.rainbowFeathers;
			totalSparklings += run.sparklings == null ? 0 : run.sparklings.size();
			for (Critter critter : Critters.all()) {
				int caught = run.caught(critter);
				if (caught == 0) continue;
				values.merge(critter, caught, Integer::sum);
			}
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
		try {
			AtomicFiles.writeString(file, GSON.toJson(runs));
		} catch (IOException | RuntimeException failed) {
			// Losing the history is not worth interrupting a run over.
		}
	}
}
