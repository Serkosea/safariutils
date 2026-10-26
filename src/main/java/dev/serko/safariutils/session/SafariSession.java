package dev.serko.safariutils.session;

import dev.serko.safariutils.client.SafeMode;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;

import java.util.ArrayList;
import java.util.Collections;
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
    private final long personalBestStarted = dev.serko.safariutils.state.MonotonicTime.nowMillis();
    private PersonalBestTracker personalBests = new PersonalBestTracker(false, null);
    public void startPersonalBests(boolean timingKnown) { personalBests = new PersonalBestTracker(timingKnown, selfName); }
    public PersonalBestTracker personalBests() { return personalBests; }
    public long personalBestElapsed() { return Math.max(0, dev.serko.safariutils.state.MonotonicTime.nowMillis()-personalBestStarted); }


	private final Map<Critter, Integer> ownCatches = new LinkedHashMap<>();
	private final Map<Critter, Integer> attempts = new LinkedHashMap<>();
	private final Map<Critter, Integer> failures = new LinkedHashMap<>();
	/** critter -> partymate name -> how many times they caught it. */
	private final Map<Critter, Map<String, Integer>> sharedCatches = new LinkedHashMap<>();
	private final java.util.Set<String> participants =
		new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	/** Shared totals kept beside the detailed player map for constant-time HUD reads. */
	private final Map<Critter, Integer> sharedCatchTotals = new LinkedHashMap<>();
	private final Map<SafariBiome, Integer> ownTotalsByBiome = new EnumMap<>(SafariBiome.class);
	private final Map<SafariBiome, Integer> ownUniquesByBiome = new EnumMap<>(SafariBiome.class);
	private final Map<SafariBiome, Integer> sharedTotalsByBiome = new EnumMap<>(SafariBiome.class);
	/** Single authoritative unique state; counters are derived so they cannot drift. */
	private final Set<Critter> partyCaughtSpecies = new LinkedHashSet<>();
	private final Map<SafariBiome, Integer> partyUniquesByBiome = new EnumMap<>(SafariBiome.class);
	private final List<SparklingOccurrence> sparklingOccurrences = new ArrayList<>();
	private final Set<Critter> sparklingSpecies = new LinkedHashSet<>();
	private final Set<Critter> sparklingSpeciesView = Collections.unmodifiableSet(sparklingSpecies);
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
	private int ownTotal;
	private int sharedTotal;
	private int totalAttempts;
	private int totalFailures;
	/** The same shards as ownShards/sharedShards, but broken down by species — for pricing. */
	private final Map<Critter, Integer> shardCounts = new LinkedHashMap<>();
	private final Map<Critter, Integer> shardCountsView = Collections.unmodifiableMap(shardCounts);
	private long catchRevision;
	private long uniquePerPlayerRevision = Long.MIN_VALUE;
	private Map<String, Map<SafariBiome, Integer>> uniquePerPlayerCache = Map.of();
	private int safariEssence;
	private int rainbowFeathers;
    private final Map<String, Integer> rareDrops = new LinkedHashMap<>();
    private final Map<String, RunRecord.RareDropValue> rareDropValues = new LinkedHashMap<>();
    public Map<String, RunRecord.RareDropValue> rareDropValues() { return Collections.unmodifiableMap(rareDropValues); }
    public void recordRareDropValue(String id, double price, String source, long observedAt, long at) {
        rareDropValues.merge(id, new RunRecord.RareDropValue(1,price,source,observedAt,at), (a,b) ->
            new RunRecord.RareDropValue(a.pricedQuantity()+1,a.estimatedTotal()+price, a.source().equals(source)?source:"mixed", Math.min(a.observedAt(),observedAt),at));
    }
    private final Set<String> newSparklingSpecies = new LinkedHashSet<>();
    public Map<String, Integer> rareDrops() { return Collections.unmodifiableMap(rareDrops); }
    public Set<String> newSparklingSpecies() { return Collections.unmodifiableSet(newSparklingSpecies); }
    public void recordRareDrop(String id) { rareDrops.merge(id, 1, Integer::sum); }
    public void recordNewSparkling(Critter critter) { newSparklingSpecies.add(critter.name()); }
	private Integer lastEssenceBalance;
	private long lastEventMillis;
	private boolean interrupted;

	public SafariSession(String selfName, long startedAtMillis) {
		this.selfName = selfName == null ? "You" : selfName;
		this.startedAtMillis = startedAtMillis;
		this.lastEventMillis = startedAtMillis;
		participants.add(this.selfName);
	}

	/** Startup regression check for duplicate catches and the Macaw milestone. */
	public static void verifyCompletionLogic() {
		SafariSession test = new SafariSession("test", 0L);
		long at = 1L;
		for (Critter critter : Critters.all()) {
			if (critter.equals(Critters.MACAW)) continue;
			test.record(new CritterEvent(CritterEvent.Type.OWN_CATCH, critter, null, 1, false), at++);
		}
		if (!test.allButMacawComplete() || test.dexComplete()
			|| test.partyUnique() != Critters.total() - 1) {
			throw new IllegalStateException("Safari completion self-test failed before Macaw");
		}
		test.record(new CritterEvent(CritterEvent.Type.OWN_CATCH, Critters.MACAW, null, 1, false), at++);
		test.record(new CritterEvent(CritterEvent.Type.OWN_CATCH, Critters.MACAW, null, 1, false), at);
		if (test.allButMacawComplete() || !test.dexComplete()
			|| test.partyUnique() != Critters.total()) {
			throw new IllegalStateException("Safari completion self-test failed after Macaw");
		}
	}

	public void record(CritterEvent event, long atMillis) {
		lastEventMillis = atMillis;
		Critter critter = event.critter();
		if (critter == null) return;

		switch (event.type()) {
			case OWN_CATCH -> {
				recordPartySpecies(critter);
				boolean newlyOwnCaught = !caughtByYou(critter);
				ownCatches.merge(critter, 1, Integer::sum);
				ownTotal++;
				ownTotalsByBiome.merge(critter.biome(), 1, Integer::sum);
				if (newlyOwnCaught) ownUniquesByBiome.merge(critter.biome(), 1, Integer::sum);
				ownShards += event.shards();
				shardCounts.merge(critter, event.shards(), Integer::sum);
				catchRevision++;
			}
			case SHARED_CATCH -> {
				addParticipant(event.catcher());
				recordPartySpecies(critter);
				sharedCatches.computeIfAbsent(critter, c -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
					.merge(event.catcher(), 1, Integer::sum);
				sharedCatchTotals.merge(critter, 1, Integer::sum);
				sharedTotal++;
				sharedTotalsByBiome.merge(critter.biome(), 1, Integer::sum);
				sharedShards += event.shards();
				shardCounts.merge(critter, event.shards(), Integer::sum);
				catchRevision++;
			}
			case ATTEMPT -> {
				attempts.merge(critter, 1, Integer::sum);
				totalAttempts++;
			}
			case FAILED -> {
				failures.merge(critter, 1, Integer::sum);
				totalFailures++;
			}
			case ENTERED_SAFARI -> {
				return;
			}
		}

		if (event.sparkling()) rainbowFeathers++;
	}

	private void recordPartySpecies(Critter critter) {
		if (partyCaughtSpecies.add(critter)) {
			partyUniquesByBiome.merge(critter.biome(), 1, Integer::sum);
		}
	}

	/** Adds a globally announced Sparkling without conflating it with the reward line. */
	public void recordSparkling(Critter critter, String catcher, long atMillis) {
		lastEventMillis = atMillis;
		sparklingOccurrences.add(new SparklingOccurrence(critter, catcher, atMillis));
		sparklingSpecies.add(critter);
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
	 * A shard awarded outside a catch, currently from a floor drop or Shard Trader.
	 * This bypasses {@link #record}: nothing was caught, attempted, or failed, so only
	 * shard income and the activity timestamp move.
	 */
	public void recordShardReward(Critter critter, int amount, long atMillis) {
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
		return ownUniquesByBiome.getOrDefault(biome, 0);
	}

	/** Every catch you made, duplicates included. */
	public int ownTotal() {
		return ownTotal;
	}

	public int ownTotal(SafariBiome biome) {
		return ownTotalsByBiome.getOrDefault(biome, 0);
	}

	// --- party progress (yours + loot share) ---------------------------------

	/** True once anyone in the party has caught {@code critter} at least once. */
	public boolean caughtByParty(Critter critter) {
		return partyCaughtSpecies.contains(critter);
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
		if (!SafeMode.conservativeCompletion()
			&& TrackingMode.isUnavailable(critter) && partyCatches(critter) == 0) return true;
		return partyCatches(critter) >= required(critter);
	}

	/** True when the run cannot produce {@code critter} and none was caught. */
	public boolean isUnavailable(Critter critter) {
		return !SafeMode.conservativeCompletion()
			&& TrackingMode.isUnavailable(critter) && partyCatches(critter) == 0;
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
		return partyCaughtSpecies.size();
	}

	public int partyUnique(SafariBiome biome) {
		return partyUniquesByBiome.getOrDefault(biome, 0);
	}

	/** Every catch by anyone in the party, duplicates included. */
	public int partyTotal() {
		return ownTotal() + sharedTotal();
	}

	public int partyTotal(SafariBiome biome) {
		return ownTotal(biome) + sharedTotalsByBiome.getOrDefault(biome, 0);
	}

	private int sharedTotal() {
		return sharedTotal;
	}

	/** How many times {@code critter} was caught this run by anyone in the party. */
	public int partyCatches(Critter critter) {
		return ownCatches.getOrDefault(critter, 0) + sharedCatchTotals.getOrDefault(critter, 0);
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

	/** True only when Macaw itself is the single allowed missing species. */
	public boolean allButMacawComplete() {
		return !caughtByParty(Critters.MACAW) && allCaughtExcept(Critters.MACAW);
	}

	/** True once all 37 species have been caught by someone this run. */
	public boolean dexComplete() {
		return partyCaughtSpecies.containsAll(Critters.all());
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
		if (uniquePerPlayerRevision == catchRevision) return uniquePerPlayerCache;
		Map<String, Map<SafariBiome, Integer>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		Map<SafariBiome, Integer> mine = new EnumMap<>(SafariBiome.class);
		for (Critter critter : ownCatches.keySet()) {
			mine.merge(critter.biome(), 1, Integer::sum);
		}
		if (!mine.isEmpty()) result.put(selfName, mine);

		Map<String, Map<SafariBiome, Integer>> others = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<Critter, Map<String, Integer>> entry : sharedCatches.entrySet()) {
			SafariBiome biome = entry.getKey().biome();
			for (String player : entry.getValue().keySet()) {
				others.computeIfAbsent(player, p -> new EnumMap<>(SafariBiome.class))
					.merge(biome, 1, Integer::sum);
			}
		}
		result.putAll(others);
		result.replaceAll((player, counts) -> Collections.unmodifiableMap(counts));
		uniquePerPlayerCache = Collections.unmodifiableMap(result);
		uniquePerPlayerRevision = catchRevision;
		return uniquePerPlayerCache;
	}

	/** Every player seen this run, alphabetically by display name. */
	public List<String> players() {
		return new ArrayList<>(participants);
	}

	/** Adds a confirmed run participant even when they have not produced a catch yet. */
	public void addParticipant(String player) {
		if (player != null && !player.isBlank()) participants.add(player);
	}

	// --- misc ----------------------------------------------------------------

    /** Snapshot of existing catch evidence; no second tracker or additional chat parsing. */
    public Map<String, Map<String, Integer>> catchesPerPlayer() {
        Map<String, Map<String, Integer>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (!ownCatches.isEmpty()) {
            Map<String,Integer> mine = new LinkedHashMap<>();
            ownCatches.forEach((critter,count) -> mine.put(critter.name(),count));
            result.put(selfName,mine);
        }
        sharedCatches.forEach((critter,players) -> players.forEach((player,count) ->
            result.computeIfAbsent(player,ignored -> new LinkedHashMap<>()).merge(critter.name(),count,Integer::sum)));
        result.replaceAll((player,counts) -> Collections.unmodifiableMap(counts));
        return Collections.unmodifiableMap(result);
    }

	/** Your catches by species for saved history and live summaries. */
	public Map<Critter, Integer> ownCatchCounts() {
		return Map.copyOf(ownCatches);
	}

	/** Partymates' catches by species, summed across whoever made them. */
	public Map<Critter, Integer> sharedCatchCounts() {
		return Map.copyOf(sharedCatchTotals);
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
		return totalAttempts;
	}

	public int totalFailures() {
		return totalFailures;
	}

	public Set<Critter> sparklings() {
		return sparklingSpeciesView;
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
		return shardCountsView;
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

	/** Marks a ticket-activated run that ended without the normal reward-summary boundary. */
	public void finishInterrupted(long atMillis) {
		interrupted = true;
		finish(atMillis);
	}

	public boolean interrupted() {
		return interrupted;
	}

	/** True when nothing has been recorded yet — used to suppress an empty HUD. */
	public boolean isEmpty() {
		return ownCatches.isEmpty() && sharedCatches.isEmpty() && attempts.isEmpty()
			&& safariEssence == 0 && rainbowFeathers == 0 && sparklingOccurrences.isEmpty() && rareDrops.isEmpty();
	}

	public record SparklingOccurrence(Critter critter, String catcher, long atMillis) {
	}
}
