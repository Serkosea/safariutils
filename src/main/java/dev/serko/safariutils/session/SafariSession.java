package dev.serko.safariutils.session;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Mutable tally for one Safari run. Capture and loot-share events populate a single
 * per-player catch table used for personal, party and biome progress. Client-thread only.
 */
public final class SafariSession {

	/** Name used for the local player in per-player views. */
	private final String selfName;
	private final long startedAtMillis;

	private final Map<Critter, Integer> ownCatches = new LinkedHashMap<>();
	private final Map<Critter, Integer> attempts = new LinkedHashMap<>();
	private final Map<Critter, Integer> failures = new LinkedHashMap<>();
	/** critter -> partymate name -> how many times they caught it. */
	private final Map<Critter, Map<String, Integer>> sharedCatches = new LinkedHashMap<>();
	private final List<SparklingOccurrence> sparklingOccurrences = new ArrayList<>();
	/**
	 * How many of each species are loaded right now, replaced wholesale each scan.
	 *
	 * <p>Deliberately not cumulative. Counting distinct entity ids over time measures
	 * how many entity instances have been observed, not how many exist: a critter that
	 * escapes a capsule comes back as a new entity, so the total climbs forever.
	 */
	private final Map<Critter, Integer> nearbyCounts = new LinkedHashMap<>();

	private int ownShards;
	private int sharedShards;
	/** The same shards as ownShards/sharedShards, but broken down by species — for pricing. */
	private final Map<Critter, Integer> shardCounts = new LinkedHashMap<>();
	private int safariEssence;
	private int rainbowFeathers;
	private Integer lastEssenceBalance;
	private long lastEventMillis;

	public SafariSession(String selfName, long startedAtMillis) {
		this.selfName = selfName == null ? "You" : selfName;
		this.startedAtMillis = startedAtMillis;
		this.lastEventMillis = startedAtMillis;
	}

	public void record(CritterEvent event, long atMillis) {
		lastEventMillis = atMillis;
		Critter critter = event.critter();
		if (critter == null) return;

		switch (event.type()) {
			case OWN_CATCH -> {
				ownCatches.merge(critter, 1, Integer::sum);
				ownShards += event.shards();
				shardCounts.merge(critter, event.shards(), Integer::sum);
			}
			case SHARED_CATCH -> {
				sharedCatches.computeIfAbsent(critter, c -> new TreeMap<>())
					.merge(event.catcher(), 1, Integer::sum);
				sharedShards += event.shards();
				shardCounts.merge(critter, event.shards(), Integer::sum);
			}
			case ATTEMPT -> attempts.merge(critter, 1, Integer::sum);
			case FAILED -> failures.merge(critter, 1, Integer::sum);
			case ENTERED_SAFARI -> {
				return;
			}
		}

		if (event.sparkling()) rainbowFeathers++;
	}

	/** Adds a globally announced Sparkling without conflating it with the reward line. */
	public void recordSparkling(Critter critter, String catcher, long atMillis) {
		lastEventMillis = atMillis;
		sparklingOccurrences.add(new SparklingOccurrence(critter, catcher, atMillis));
	}

	public void recordBonusRainbowFeather(long atMillis) {
		lastEventMillis = atMillis;
		rainbowFeathers++;
	}

	/** Tracks earnings while ignoring balance reductions caused by shop purchases. */
	public void updateEssenceBalance(int balance, long atMillis) {
		if (lastEssenceBalance != null && balance > lastEssenceBalance) {
			safariEssence += balance - lastEssenceBalance;
			lastEventMillis = atMillis;
		}
		lastEssenceBalance = balance;
	}

	/** Replaces incremental tracking with Hypixel's authoritative run summary. */
	public void confirmSafariEssence(int amount, long atMillis) {
		safariEssence = Math.max(0, amount);
		lastEventMillis = atMillis;
	}

	/**
	 * A shard found on the ground rather than from a catch — see {@link ChatParser}'s
	 * class doc for why this bypasses {@link #record} entirely rather than being
	 * squeezed into a {@link CritterEvent} of some kind. Only ever touches the shard
	 * totals: nothing was caught, attempted, or failed, so nothing else here should
	 * move.
	 */
	public void recordFloorDropShard(Critter critter, int amount, long atMillis) {
		lastEventMillis = atMillis;
		ownShards += amount;
		shardCounts.merge(critter, amount, Integer::sum);
	}

	// --- your progress -------------------------------------------------------

	public boolean caughtByYou(Critter critter) {
		return ownCatches.getOrDefault(critter, 0) > 0;
	}

	/** Distinct species you personally caught, out of {@link Critters#total()}. */
	public int ownUnique() {
		return ownCatches.size();
	}

	public int ownUnique(SafariBiome biome) {
		return (int) ownCatches.keySet().stream().filter(c -> c.biome() == biome).count();
	}

	/** Every catch you made, duplicates included. */
	public int ownTotal() {
		return ownCatches.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int ownTotal(SafariBiome biome) {
		return ownCatches.entrySet().stream()
			.filter(e -> e.getKey().biome() == biome)
			.mapToInt(Map.Entry::getValue).sum();
	}

	// --- party progress (yours + loot share) ---------------------------------

	/** True once anyone in the party has caught {@code critter} at least once. */
	public boolean caughtByParty(Critter critter) {
		return caughtByYou(critter) || sharedCatches.containsKey(critter);
	}

	/** Replaces the live nearby counts with a fresh scan of what is loaded. */
	public void setNearby(Map<Critter, Integer> counts) {
		nearbyCounts.clear();
		nearbyCounts.putAll(counts);
	}

	/** How many of {@code critter} are loaded near the player right now. */
	public int nearby(Critter critter) {
		return nearbyCounts.getOrDefault(critter, 0);
	}

	/**
	 * How many of {@code critter} the run is considered to hold.
	 *
	 * <p>Only a fixed quota can answer this. The client cannot see the whole map, and a
	 * partymate catching something out of render distance is never observed at all, so
	 * nothing counted locally is a valid target — it would be wrong in exactly the
	 * four-player runs this mod exists for.
	 */
	public int required(Critter critter) {
		if (TrackingMode.uniqueOnly()) return 1;
		return critter.hasQuota() ? critter.spawnQuota() : 1;
	}

	/**
	 * True once the run is finished with {@code critter}, either by catching enough or
	 * because the run can no longer produce it. Treating the impossible as settled is
	 * what lets a biome read as complete instead of stalling forever on a species that
	 * is never coming.
	 */
	public boolean isComplete(Critter critter) {
		if (TrackingMode.isUnavailable(critter) && partyCatches(critter) == 0) return true;
		return partyCatches(critter) >= required(critter);
	}

	/** True when the run cannot produce {@code critter} and none was caught. */
	public boolean isUnavailable(Critter critter) {
		return TrackingMode.isUnavailable(critter) && partyCatches(critter) == 0;
	}

	/** How many more of {@code critter} are known to be left, never negative. */
	public int remaining(Critter critter) {
		return Math.max(0, required(critter) - partyCatches(critter));
	}

	/**
	 * Distinct species actually caught by the party. Objective exhaustion is deliberately
	 * excluded so progress bars can only advance from catches and loot shares.
	 */
	public int partyUnique() {
		return (int) Critters.all().stream().filter(this::caughtByParty).count();
	}

	public int partyUnique(SafariBiome biome) {
		return (int) Critters.inBiome(biome).stream().filter(this::caughtByParty).count();
	}

	/** Every catch by anyone in the party, duplicates included. */
	public int partyTotal() {
		return ownTotal() + sharedTotal();
	}

	public int partyTotal(SafariBiome biome) {
		return ownTotal(biome) + sharedCatches.entrySet().stream()
			.filter(e -> e.getKey().biome() == biome)
			.flatMap(e -> e.getValue().values().stream())
			.mapToInt(Integer::intValue).sum();
	}

	private int sharedTotal() {
		return sharedCatches.values().stream()
			.flatMap(m -> m.values().stream())
			.mapToInt(Integer::intValue).sum();
	}

	/** How many times {@code critter} was caught this run by anyone in the party. */
	public int partyCatches(Critter critter) {
		int shared = sharedCatches.getOrDefault(critter, Map.of()).values().stream()
			.mapToInt(Integer::intValue).sum();
		return ownCatches.getOrDefault(critter, 0) + shared;
	}

	/** How many times you personally caught {@code critter} this run — loot share not counted. */
	public int ownCatches(Critter critter) {
		return ownCatches.getOrDefault(critter, 0);
	}

	/** Who caught {@code critter} this run, local player included, in catch order. */
	public List<String> catchersOf(Critter critter) {
		List<String> names = new ArrayList<>();
		if (caughtByYou(critter)) names.add(selfName);
		names.addAll(sharedCatches.getOrDefault(critter, Map.of()).keySet());
		return names;
	}

	/** True once every species in {@code biome} has been caught by someone. */
	public boolean biomeComplete(SafariBiome biome) {
		return Critters.inBiome(biome).stream().allMatch(this::isComplete);
	}

	/** One catch of every species, with exhausted RNG species treated as settled. */
	public boolean biomeUniquesComplete(SafariBiome biome) {
		return Critters.inBiome(biome).stream()
			.allMatch(critter -> caughtByParty(critter) || isUnavailable(critter));
	}

	/**
	 * True when every species except {@code exception} has been caught by someone,
	 * whether or not the exception itself has been.
	 */
	public boolean allCaughtExcept(Critter exception) {
		return Critters.all().stream().allMatch(c -> c.equals(exception)
			|| caughtByParty(c) || isUnavailable(c));
	}

	/** True once all 37 species have been caught by someone this run. */
	public boolean dexComplete() {
		return partyUnique() == Critters.total();
	}

	/** Species in {@code biome} the run is not finished with yet. */
	public List<Critter> missing(SafariBiome biome) {
		return Critters.inBiome(biome).stream().filter(c -> !isComplete(c)).toList();
	}

	// --- per-player breakdown ------------------------------------------------

	/**
	 * Unique-species count per player per biome, with the local player included
	 * under their own name. This is the "who is covering which biome" view.
	 */
	public Map<String, Map<SafariBiome, Integer>> uniquePerPlayer() {
		Map<String, Map<SafariBiome, Integer>> result = new LinkedHashMap<>();

		Map<SafariBiome, Integer> mine = new EnumMap<>(SafariBiome.class);
		for (Critter critter : ownCatches.keySet()) {
			mine.merge(critter.biome(), 1, Integer::sum);
		}
		if (!mine.isEmpty()) result.put(selfName, mine);

		Map<String, Map<SafariBiome, Integer>> others = new TreeMap<>();
		for (Map.Entry<Critter, Map<String, Integer>> entry : sharedCatches.entrySet()) {
			SafariBiome biome = entry.getKey().biome();
			for (String player : entry.getValue().keySet()) {
				others.computeIfAbsent(player, p -> new EnumMap<>(SafariBiome.class))
					.merge(biome, 1, Integer::sum);
			}
		}
		result.putAll(others);
		return result;
	}

	/** Every player seen this run, local player first. */
	public List<String> players() {
		return new ArrayList<>(uniquePerPlayer().keySet());
	}

	// --- misc ----------------------------------------------------------------

	/** Your catches by species for saved history and live summaries. */
	public Map<Critter, Integer> ownCatchCounts() {
		return Map.copyOf(ownCatches);
	}

	/** Partymates' catches by species, summed across whoever made them. */
	public Map<Critter, Integer> sharedCatchCounts() {
		Map<Critter, Integer> totals = new LinkedHashMap<>();
		sharedCatches.forEach((critter, byPlayer) -> totals.put(critter,
			byPlayer.values().stream().mapToInt(Integer::intValue).sum()));
		return totals;
	}

	/** Capsules thrown, by species. */
	public Map<Critter, Integer> attemptCounts() {
		return Map.copyOf(attempts);
	}

	public int attempts(Critter critter) {
		return attempts.getOrDefault(critter, 0);
	}

	public int failures(Critter critter) {
		return failures.getOrDefault(critter, 0);
	}

	public int totalAttempts() {
		return attempts.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int totalFailures() {
		return failures.values().stream().mapToInt(Integer::intValue).sum();
	}

	public Set<Critter> sparklings() {
		Set<Critter> species = new LinkedHashSet<>();
		for (SparklingOccurrence occurrence : sparklingOccurrences) species.add(occurrence.critter());
		return Set.copyOf(species);
	}

	public List<SparklingOccurrence> sparklingOccurrences() {
		return List.copyOf(sparklingOccurrences);
	}

	public int safariEssence() { return safariEssence; }

	public int rainbowFeathers() { return rainbowFeathers; }

	public int ownShards() {
		return ownShards;
	}

	public int totalShards() {
		return ownShards + sharedShards;
	}

	/**
	 * Shards this run gave you, yours and loot-shared together, split by species — for
	 * anything pricing them. Not "shards the party got": a partymate's own catches are
	 * never itemised to this client, only the loot-share notification is, so this is
	 * exactly what reached your inventory and nothing more.
	 */
	public Map<Critter, Integer> shardCounts() {
		return Map.copyOf(shardCounts);
	}

	public String selfName() {
		return selfName;
	}

	public long startedAtMillis() {
		return startedAtMillis;
	}

	/**
	 * How long the run lasted, start to last event.
	 *
	 * <p>This is the span of a <em>finished</em> run, and deliberately not "how long ago
	 * it started": a run that ended twenty minutes ago should not still be counting, and
	 * a finished run has no current time to measure against. A live run wants
	 * {@link #elapsedMillis(long)} instead.
	 */
	public long durationMillis() {
		return Math.max(0, lastEventMillis - startedAtMillis);
	}

	/** How long the run has been going at {@code now} — the figure for a live timer. */
	public long elapsedMillis(long now) {
		return Math.max(0, now - startedAtMillis);
	}

	/** Freezes the duration at the confirmed instance transition. */
	public void finish(long atMillis) {
		lastEventMillis = Math.max(lastEventMillis, atMillis);
	}

	/** True when nothing has been recorded yet — used to suppress an empty HUD. */
	public boolean isEmpty() {
		return ownCatches.isEmpty() && sharedCatches.isEmpty() && attempts.isEmpty()
			&& safariEssence == 0 && rainbowFeathers == 0 && sparklingOccurrences.isEmpty();
	}

	public record SparklingOccurrence(Critter critter, String catcher, long atMillis) {
	}
}
