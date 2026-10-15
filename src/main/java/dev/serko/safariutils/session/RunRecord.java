package dev.serko.safariutils.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.io.PersistentJson;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;

/**
 * Serializable summary of a finished run. Species are stored by name so roster edits
 * do not invalidate history; transient live-session state is omitted.
 */
public class RunRecord {
	private static final Gson GSON = new GsonBuilder().create();
	/** Original record retained only to preserve fields introduced by newer builds. */
	private transient JsonObject preservedJson;

	public long started;
	public long ended;
	/** True when an activated run ended via disconnect, quit, or an unexpected transfer. */
	public boolean interrupted;
	public String self;
	/** Confirmed participants, including ticket-trading arrivals without catches. */
	public java.util.List<String> players = new java.util.ArrayList<>();
	/** Species name -> times you caught it. */
	public Map<String, Integer> own = new LinkedHashMap<>();
	/** Species name -> times a partymate caught it, summed across the party. */
	public Map<String, Integer> shared = new LinkedHashMap<>();
	/** Species name -> capsules thrown at it. */
	public Map<String, Integer> attempts = new LinkedHashMap<>();
	/**
	 * Species name -> shards that reached you, yours and loot-shared together.
	 *
	 * <p>Absent from runs saved before this was kept, which is why {@link #hasShardData()}
	 * exists: those runs know how many shards they gave but not of what, so they can never
	 * be priced and are left out of the money totals rather than guessed at.
	 */
	public Map<String, Integer> shards = new LinkedHashMap<>();
	public int ownShards;
	public int totalShards;
	public int safariEssence;
	public int rainbowFeathers;
	/** Every Sparkling occurrence; repeated species are intentionally retained. */
	public java.util.List<SparklingRecord> sparklings = new java.util.ArrayList<>();

	/** Gson needs this; nothing else should use it. */
	public RunRecord() {
	}

	static RunRecord fromJson(JsonObject json) {
		RunRecord record = GSON.fromJson(json, RunRecord.class);
		if (record != null) record.preservedJson = json.deepCopy();
		return record;
	}

	JsonObject toJson() {
		return PersistentJson.mergeRecord(preservedJson,
			GSON.toJsonTree(this).getAsJsonObject());
	}

	public static RunRecord of(SafariSession session) {
		RunRecord record = new RunRecord();
		record.started = session.startedAtMillis();
		record.ended = session.startedAtMillis() + session.durationMillis();
		record.interrupted = session.interrupted();
		record.self = session.selfName();
		record.players.addAll(session.players());
		session.ownCatchCounts().forEach((critter, count) -> record.own.put(critter.name(), count));
		session.sharedCatchCounts().forEach((critter, count) -> record.shared.put(critter.name(), count));
		session.attemptCounts().forEach((critter, count) -> record.attempts.put(critter.name(), count));
		session.shardCounts().forEach((critter, count) -> record.shards.put(critter.name(), count));
		record.ownShards = session.ownShards();
		record.totalShards = session.totalShards();
		record.safariEssence = session.safariEssence();
		record.rainbowFeathers = session.rainbowFeathers();
		for (SafariSession.SparklingOccurrence occurrence : session.sparklingOccurrences()) {
			record.sparklings.add(new SparklingRecord(occurrence.critter().name(),
				occurrence.catcher(), occurrence.atMillis()));
		}
		return record;
	}

	public record SparklingRecord(String species, String catcher, long atMillis) {
	}

	public long durationMillis() {
		return Math.max(0, ended - started);
	}

	/** Whether this run knows which species its shards came from, and so can be priced. */
	public boolean hasShardData() {
		return shards != null && !shards.isEmpty();
	}

	/** How many {@code critter} shards this run put in your inventory. */
	public int shards(Critter critter) {
		return shards == null ? 0 : shards.getOrDefault(critter.name(), 0);
	}

	/** How many times anyone in the party caught {@code critter} in this run. */
	public int caught(Critter critter) {
		return value(own, critter.name()) + value(shared, critter.name());
	}

	/** Distinct species the party caught. */
	public int partyUnique() {
		return (int) Critters.all().stream().filter(c -> caught(c) > 0).count();
	}

	/** Every catch in the run, duplicates included. */
	public int partyTotal() {
		return sum(own) + sum(shared);
	}

	private static int sum(Map<String, Integer> counts) {
		if (counts == null) return 0;
		int total = 0;
		for (Integer count : counts.values()) total += count == null ? 0 : Math.max(0, count);
		return total;
	}

	/** Repairs valid JSON with missing/null legacy fields before history is exposed. */
	void normalize() {
		Map<String, String> uniquePlayers = new LinkedHashMap<>();
		if (players != null) for (String player : players) {
			if (player != null && !player.isBlank()) uniquePlayers.putIfAbsent(
				player.toLowerCase(java.util.Locale.ROOT), player);
		}
		players = new ArrayList<>(uniquePlayers.values());
		own = normalizeCounts(own);
		shared = normalizeCounts(shared);
		attempts = normalizeCounts(attempts);
		shards = normalizeCounts(shards);
		ownShards = Math.max(0, ownShards);
		totalShards = Math.max(ownShards, totalShards);
		safariEssence = Math.max(0, safariEssence);
		rainbowFeathers = Math.max(0, rainbowFeathers);
		if (sparklings == null) sparklings = new ArrayList<>();
		else sparklings.removeIf(entry -> entry == null || entry.species() == null
			|| entry.species().isBlank());
	}

	private static int value(Map<String, Integer> counts, String species) {
		if (counts == null) return 0;
		Integer count = counts.get(species);
		return count == null ? 0 : Math.max(0, count);
	}

	private static Map<String, Integer> normalizeCounts(Map<String, Integer> counts) {
		Map<String, Integer> normalized = new LinkedHashMap<>();
		if (counts == null) return normalized;
		counts.forEach((species, count) -> {
			if (species != null && !species.isBlank() && count != null && count > 0) {
				normalized.put(species, count);
			}
		});
		return normalized;
	}
}
