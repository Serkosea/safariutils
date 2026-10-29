package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SparklingStats;
import dev.serko.safariutils.api.SharedSparklingProviders;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/** Public/manual shared-Sparkling state and the decisions derived from it. */
public final class SparklingMode {
	/** Ordinals match the grouped Always Active Markers setting picker. */
	private enum WaypointKind {
		CAVERN_FLOOR, MOUNDS, SNOOZLE_WALLS, ICY_FLOOR, TROODON_WALLS,
		HAUNTED_FLOOR, FOREST_FLOOR, BEE_NESTS, GEM_PEDESTALS
	}

	private static final Set<Critter> shared = new LinkedHashSet<>();
	private static final Map<Critter, Integer> ALWAYS_ACTIVE_BITS = alwaysActiveBits();
	/** Distinguishes an intentionally empty list from one that was never supplied. */
	private static boolean sharedConfigured;
	private static int expectedPlayers = 1;

	private SparklingMode() {
	}

	public static boolean enabled() {
		return ConfigManager.get().sparkling.sparklingMode;
	}

	public static boolean ignoreUniques() {
		return enabled() && ConfigManager.get().sparkling.sparklingIgnoreUniques;
	}

	public static boolean onlyShowSparkling() {
		return enabled() && ConfigManager.get().sparkling.sparklingOnlyShowSparkling;
	}

	/** Optional ordinary-hitbox color for whether this run has its unique catch. */
	public static int uniqueHitboxColour(Critter critter, SafariSession session) {
		if (!ConfigManager.get().display.uniqueHitboxColours) return 0;
		return session != null && session.caughtByParty(critter) ? 0xFF55FF55 : 0xFFFF7777;
	}

	public static Set<Critter> shared() {
		return Set.copyOf(shared);
	}

	public static boolean sharedConfigured() {
		return sharedConfigured;
	}

	public static void replaceShared(Set<Critter> species) {
		shared.clear();
		Critters.all().stream().filter(species::contains).forEach(shared::add);
		sharedConfigured = true;
	}

	public static void clearShared() {
		shared.clear();
		sharedConfigured = false;
	}

	public static void onRunStarted() {
		expectedPlayers = dev.serko.safariutils.session.SessionManager.expectedRunPlayers();
		// Solo players share their own collection by definition. If neither the manual
		// command nor the private provider supplied a list, use the saved unique set.
		if (expectedPlayers == 1 && shared.isEmpty()) {
			Critters.all().stream().filter(critter -> SparklingStats.count(critter) > 0)
				.forEach(shared::add);
		}
	}

	/** Late arrivals raise the expected roster; disconnects never lower it mid-run. */
	public static void tick() {
		if (dev.serko.safariutils.session.SessionManager.current() != null) {
			expectedPlayers = Math.max(expectedPlayers,
				dev.serko.safariutils.session.SessionManager.expectedRunPlayers());
		}
	}

	/** Everyone present at activation also received this catch unless the count dropped. */
	public static void onSparklingCaught(Critter critter) {
		if (critter == null) return;
		boolean fullRosterPresent = SafariPartyWatch.joinedPlayers() >= expectedPlayers;
		boolean sharedCatch = SharedSparklingProviders.available()
			? SharedSparklingProviders.onSharedCatch(critter.name()) : fullRosterPresent;
		if (sharedCatch) {
			shared.add(critter);
		}
	}

	public static boolean isShared(Critter critter) {
		return shared.contains(critter);
	}

	/** A user-selected species bypasses Sparkling Mode's ordinary-species suppression. */
	public static boolean alwaysActive(Critter critter) {
		Integer bit = ALWAYS_ACTIVE_BITS.get(critter);
		return enabled() && bit != null
			&& (ConfigManager.get().sparkling.sparklingAlwaysActiveCritters & (1L << bit)) != 0;
	}

	private static Map<Critter, Integer> alwaysActiveBits() {
		Map<Critter, Integer> bits = new HashMap<>();
		List<Critter> ordered = Critters.selectionOrder();
		for (int index = 0; index < ordered.size(); index++) bits.put(ordered.get(index), index);
		return Map.copyOf(bits);
	}

	private static boolean alwaysActiveWaypoint(WaypointKind kind) {
		return enabled() && (ConfigManager.get().sparkling.sparklingAlwaysActiveWaypoints
			& (1 << kind.ordinal())) != 0;
	}

	public static boolean showHideyhoLocations() {
		return !onlyShowSparkling() || alwaysActive(Critters.byName("Hideyho"));
	}

	/** Ignore Uniques hides ordinary shared critters, never a Sparkling duplicate. */
	public static boolean hideOrdinaryHitbox(Critter critter, boolean sparkling) {
		return !sparkling && !alwaysActive(critter)
			&& (onlyShowSparkling() || ignoreUniques() && isShared(critter));
	}

	/** Ordinary recatch markers disappear after the run unique; live hitboxes remain. */
	public static boolean hideOrdinarySpecies(Critter critter, SafariSession session) {
		return !alwaysActive(critter) && (onlyShowSparkling() || enabled() && isShared(critter)
			&& (ignoreUniques() || session != null && session.caughtByParty(critter)));
	}

	/** Tracked ordinary waypoints stop helping after the run unique is secured. */
	public static boolean hideOrdinaryWaypoint(Critter critter, SafariSession session) {
		return !alwaysActive(critter) && (onlyShowSparkling() || enabled()
			&& (ignoreUniques() && isShared(critter)
				|| session != null && session.caughtByParty(critter)));
	}

    /** Aggregate alert policy: any retained category prevents an early 'done' alert. */
    public static boolean hideFloorDrops(SafariBiome biome, SafariSession session) {
        return hideFloorDrop(biome, session, FloorDropPattern.Kind.UNKNOWN);
    }

    static boolean hideFloorDrop(SafariBiome biome, SafariSession session, FloorDropPattern.Kind kind) {
        if (biome == null) return false;
        int lootBit = switch (biome) { case CAVERN -> 0; case ICY -> 3; case HAUNTED -> 5; case FOREST -> 6; };
        int selected = enabled() ? ConfigManager.get().sparkling.sparklingAlwaysActiveWaypoints : 0;
        if (kind == FloorDropPattern.Kind.UNKNOWN) {
            // Unknown contents stay visible if any possible category is still useful.
            if (!hideFloorDrop(biome, session, FloorDropPattern.Kind.LOOT)) return false;
            for (var possible : FloorDropPattern.Kind.values())
                if (possible.biome == biome && !hideFloorDrop(biome, session, possible)) return false;
            return true;
        }
        int bit = kind == FloorDropPattern.Kind.LOOT ? lootBit : kind.alwaysBit;
        if ((selected & (1 << bit)) != 0) return false;
        if (kind == FloorDropPattern.Kind.LOOT) return enabled();
        return switch (kind) {
            case LIME, PURPLE, ORANGE -> {
                int gemBit = kind == FloorDropPattern.Kind.LIME ? 1 : kind == FloorDropPattern.Kind.ORANGE ? 2 : 4;
                int held = kind == FloorDropPattern.Kind.LIME ? SafariObjectives.limeGemsHeld()
                    : kind == FloorDropPattern.Kind.ORANGE ? SafariObjectives.orangeGemsHeld() : SafariObjectives.purpleGemsHeld();
                yield sharedFinished(session, "Gemzie") || held > 0
                    || (SafariObjectives.placedGemMask() & gemBit) != 0
                    || dev.serko.safariutils.api.PartyItemSyncProviders.objectiveAllowsFloorDropHide(biome);
            }
            case INCENSE -> sharedFinished(session, "Doomspiral")
                || dev.serko.safariutils.api.PartyItemSyncProviders.objectiveAllowsFloorDropHide(biome)
                || SafariObjectives.canPersonallyFinishDoomspiral(SafariObjectives.incenseUsed());
            // Each coin and feed can spawn another critter. A held item alone cannot
            // prove that all outstanding spawns (including Sparklings) are unnecessary.
            case COIN -> sharedFinished(session, "Gimmiegold")
                || enabled() && isShared(Critters.byName("Gimmiegold")) && SafariObjectives.shiningCoinsHeld() >= 1;
            case WORM, BERRY, SEEDS -> forestFloorDropsExhausted()
                || allSharedFinished(session, "Bluebird", "Parakeet", "Macaw");
            case ICE -> icyFloorDropsExhausted(session);
            default -> false;
        };
    }

	public static boolean forestFloorDropsExhausted() {
		return BirdfeederWatch.floorFeedFound() >= 9;
	}

	private static boolean icyFloorDropsExhausted(SafariSession session) {
		Critter troodon = Critters.byName("Troodon");
		int sparklingNeeded = SparklingWatch.outstandingCounts(SafariBiome.ICY)
			.getOrDefault(troodon, 0);
		if (sparklingNeeded > 0) return SafariObjectives.icebreakersHeld() >= sparklingNeeded;
		if (sharedFinished(session, "Troodon")) return true;
        var walls = WallTracker.TROODON.walls();
        if (walls.isEmpty()) return false;
        // Empty catalog slots are not completed critters. Count the actual remaining
        // walls, including unknown ones, instead of subtracting all absent locations.
        int remaining = (int) walls.stream().filter(wall -> wall.state() != WallTracker.State.BROKEN).count();
        return icebreakersSufficient(SafariObjectives.icebreakersHeld(), remaining,
            enabled() && troodon != null && isShared(troodon));
    }

    static boolean icebreakersSufficient(int held, int remainingWalls, boolean onlyOneNeeded) {
        return held >= (onlyOneNeeded ? Math.min(1, remainingWalls) : remainingWalls);
    }

	public static boolean hideBirdFeed(SafariSession session) {
		return enabled() && allSharedFinished(session, "Bluebird", "Parakeet", "Macaw");
	}

	/** Fixed, publicly visible objective locations use Gemzie's ordinary marker policy. */
	public static boolean showGemPedestals() {
		return ConfigManager.get().display.enableHitboxes
			&& (alwaysActiveWaypoint(WaypointKind.GEM_PEDESTALS)
				|| !hideOrdinaryHitbox(Critters.byName("Gemzie"), false));
	}

	public static boolean hideNests(SafariSession session) {
		return !alwaysActiveWaypoint(WaypointKind.BEE_NESTS) && sharedFinished(session, "Honeybug");
	}

	public static boolean hideMounds(SafariSession session) {
		return !alwaysActiveWaypoint(WaypointKind.MOUNDS) && sharedFinished(session, "Rockmite");
	}

	public static boolean hideSnoozleWalls(SafariSession session) {
		return !alwaysActiveWaypoint(WaypointKind.SNOOZLE_WALLS) && sharedFinished(session, "Snoozle");
	}

	public static boolean hideTroodonWalls(SafariSession session) {
		return !alwaysActiveWaypoint(WaypointKind.TROODON_WALLS) && sharedFinished(session, "Troodon");
	}

	private static boolean sharedFinished(SafariSession session, String name) {
		Critter critter = Critters.byName(name);
		return enabled() && critter != null && isShared(critter)
			&& (ignoreUniques() || session != null && session.caughtByParty(critter));
	}

	private static boolean allSharedFinished(SafariSession session, String... names) {
		for (String name : names) {
			if (!sharedFinished(session, name)) return false;
		}
		return true;
	}

	public static String describeShared() {
		if (shared.isEmpty()) return "None";
		List<String> names = shared.stream().map(Critter::name)
			.sorted(String.CASE_INSENSITIVE_ORDER).toList();
		return String.join(", ", names);
	}
}
