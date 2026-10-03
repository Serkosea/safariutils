package dev.serko.safariutils.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.client.SafariPaths;
import dev.serko.safariutils.io.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** On-demand, non-guessing validation and repair for saved run history. */
public final class RunDataIntegrity {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter BACKUP_TIME =
		DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	public record Audit(boolean readable, int runs, int issues, int repairable,
		List<String> details, String message) {
		public boolean healthy() { return readable && issues == 0; }
	}

	public record Repair(boolean success, Audit audit, Path backup, String message) { }

	private RunDataIntegrity() { }

	public static Audit audit() {
		Path path = SafariPaths.runHistory();
		if (!Files.isRegularFile(path)) {
			return new Audit(true, 0, 0, 0, List.of(), "No saved runs yet");
		}
		try {
			List<RunRecord> records = read(path);
			return inspect(records);
		} catch (IOException | RuntimeException invalid) {
			return new Audit(false, 0, 1, 0,
				List.of("The run-history JSON cannot be read safely"),
				"Unreadable history; the original file was left unchanged");
		}
	}

	public static Repair repair() {
		Path path = SafariPaths.runHistory();
		if (!Files.isRegularFile(path)) {
			Audit audit = audit();
			return new Repair(true, audit, null, "No saved runs need repair");
		}
		try {
			List<RunRecord> records = read(path);
			Audit before = inspect(records);
			if (!before.readable || before.repairable == 0) {
				return new Repair(before.healthy(), before, null,
					before.healthy() ? "Run history is already healthy" : "No safe automatic repair is available");
			}
			Path backup = SafariPaths.uniqueJsonBackup(SafariPaths.runBackups(),
				"safariutils-runs-backup-"
				+ BACKUP_TIME.format(LocalDateTime.now()));
			Files.createDirectories(backup.getParent());
			Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
			repairRecords(records);
			AtomicFiles.writeString(path, GSON.toJson(records));
			RunHistory.load(path);
			Audit after = inspect(records);
			return new Repair(true, after, backup,
				"Safe structural repairs applied; original history backed up");
		} catch (IOException | RuntimeException failed) {
			OperationalLog.error("HISTORY/REPAIR", failed);
			return new Repair(false, audit(), null, "Repair failed; original history was left unchanged");
		}
	}

	private static List<RunRecord> read(Path path) throws IOException {
		List<RunRecord> records = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
			new TypeToken<List<RunRecord>>() { }.getType());
		if (records == null) throw new IllegalArgumentException("History root is not a list");
		return records;
	}

	private static Audit inspect(List<RunRecord> records) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (RunRecord run : records) {
			if (run == null) {
				add(counts, "Empty run entries", 1);
				continue;
			}
			if (run.ended < run.started) add(counts, "Runs ending before they started", 1);
			if (run.started < 0 || run.ended < 0) add(counts, "Negative timestamps", 1);
			inspectMap(run.own, counts);
			inspectMap(run.shared, counts);
			inspectMap(run.attempts, counts);
			inspectMap(run.shards, counts);
			if (run.ownShards < 0 || run.totalShards < 0 || run.safariEssence < 0
					|| run.rainbowFeathers < 0) add(counts, "Negative reward totals", 1);
			if (run.players == null) add(counts, "Missing player lists", 1);
			else {
				LinkedHashSet<String> unique = new LinkedHashSet<>();
				for (String player : run.players) {
					if (player == null || player.isBlank() || !unique.add(player.toLowerCase(java.util.Locale.ROOT))) {
						add(counts, "Blank or duplicate players", 1);
					}
				}
			}
			if (run.sparklings == null) add(counts, "Missing Sparkling lists", 1);
			else for (RunRecord.SparklingRecord sparkling : run.sparklings) {
				if (sparkling == null || sparkling.species() == null || sparkling.species().isBlank()) {
					add(counts, "Invalid Sparkling entries", 1);
				}
			}
		}
		int issues = counts.values().stream().mapToInt(Integer::intValue).sum();
		List<String> details = counts.entrySet().stream()
			.map(entry -> entry.getKey() + ": " + entry.getValue()).toList();
		String message = issues == 0 ? "All saved runs passed integrity checks"
			: issues + " safe structural issue" + (issues == 1 ? "" : "s") + " found";
		return new Audit(true, records.size(), issues, issues, details, message);
	}

	private static void inspectMap(Map<String, Integer> values, Map<String, Integer> counts) {
		if (values == null) {
			add(counts, "Missing count maps", 1);
			return;
		}
		for (Map.Entry<String, Integer> entry : values.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				add(counts, "Invalid count entries", 1);
			} else if (entry.getValue() < 0) add(counts, "Negative counts", 1);
		}
	}

	private static void repairRecords(List<RunRecord> records) {
		records.removeIf(java.util.Objects::isNull);
		for (RunRecord run : records) {
			run.started = Math.max(0, run.started);
			run.ended = Math.max(run.started, run.ended);
			run.self = run.self == null ? "" : run.self;
			run.own = repairMap(run.own);
			run.shared = repairMap(run.shared);
			run.attempts = repairMap(run.attempts);
			run.shards = repairMap(run.shards);
			run.ownShards = Math.max(0, run.ownShards);
			run.totalShards = Math.max(0, run.totalShards);
			run.safariEssence = Math.max(0, run.safariEssence);
			run.rainbowFeathers = Math.max(0, run.rainbowFeathers);
			LinkedHashMap<String, String> players = new LinkedHashMap<>();
			if (run.players != null) for (String player : run.players) {
				if (player != null && !player.isBlank()) players.putIfAbsent(
					player.toLowerCase(java.util.Locale.ROOT), player);
			}
			run.players = new ArrayList<>(players.values());
			if (run.sparklings == null) run.sparklings = new ArrayList<>();
			else run.sparklings.removeIf(sparkling -> sparkling == null
				|| sparkling.species() == null || sparkling.species().isBlank());
		}
	}

	private static Map<String, Integer> repairMap(Map<String, Integer> values) {
		Map<String, Integer> repaired = new LinkedHashMap<>();
		if (values == null) return repaired;
		for (Map.Entry<String, Integer> entry : values.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
			repaired.put(entry.getKey(), Math.max(0, entry.getValue()));
		}
		return repaired;
	}

	private static void add(Map<String, Integer> counts, String issue, int amount) {
		counts.merge(issue, amount, Integer::sum);
	}
}
