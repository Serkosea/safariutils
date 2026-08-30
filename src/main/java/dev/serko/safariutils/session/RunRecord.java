package dev.serko.safariutils.session;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable summary of a finished run. Species are stored by name so roster edits
 * do not invalidate history; transient live-session state is omitted.
 */
public class RunRecord {

	public long started;
	public long ended;
	public String self;
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

	public static RunRecord of(SafariSession session) {
		RunRecord record = new RunRecord();
		record.started = session.startedAtMillis();
		record.ended = session.startedAtMillis() + session.durationMillis();
		record.self = session.selfName();
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
		return own.getOrDefault(critter.name(), 0) + shared.getOrDefault(critter.name(), 0);
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
		return counts == null ? 0 : counts.values().stream().mapToInt(Integer::intValue).sum();
	}
}
