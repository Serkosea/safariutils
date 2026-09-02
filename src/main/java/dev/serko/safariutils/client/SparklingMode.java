package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SparklingStats;
import dev.serko.safariutils.api.SharedSparklingProviders;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Public/manual shared-Sparkling state and the decisions derived from it. */
public final class SparklingMode {
	private static final Set<Critter> shared = new LinkedHashSet<>();
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
		expectedPlayers = Math.max(1, SafariPartyWatch.joinedPlayers());
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
			expectedPlayers = Math.max(expectedPlayers, SafariPartyWatch.joinedPlayers());
		}
	}

	/** Everyone present at activation also received this catch unless the count dropped. */
	public static void onSparklingCaught(Critter critter) {
		if (critter == null) return;
		if (SafariPartyWatch.joinedPlayers() >= expectedPlayers) {
			shared.add(critter);
			SharedSparklingProviders.onSharedCatch(critter.name());
		}
	}

	/** Definitive party-system messages, unlike temporary Safari-tab disconnects. */
	public static void onChatMessage(String line) {
		String lower = line.toLowerCase(java.util.Locale.ROOT);
		boolean membershipChanged = lower.endsWith(" joined the party.")
			|| lower.endsWith(" has left the party.")
			|| lower.contains(" was removed from your party")
			|| lower.contains("removed from the party")
			|| lower.contains(" was kicked from the party")
			|| (lower.startsWith("you have joined ") && lower.endsWith("'s party!"))
			|| lower.contains("you left the party")
			|| lower.contains("party was disbanded");
		if (membershipChanged) {
			// Keep the current result until the next run's stable roster replaces it.
			// Clearing first can lose a valid cached result when the same party reconnects.
			SharedSparklingProviders.onPartyMembershipChanged();
		}
	}

	public static boolean isShared(Critter critter) {
		return shared.contains(critter);
	}

	/** Ignore Uniques hides ordinary shared critters, never a Sparkling duplicate. */
	public static boolean hideOrdinaryHitbox(Critter critter, boolean sparkling) {
		return !sparkling && (onlyShowSparkling() || ignoreUniques() && isShared(critter));
	}

	/** Ordinary recatch markers disappear after the run unique; live hitboxes remain. */
	public static boolean hideOrdinarySpecies(Critter critter, SafariSession session) {
		return onlyShowSparkling() || enabled() && isShared(critter)
			&& (ignoreUniques() || session != null && session.caughtByParty(critter));
	}

	/** Tracked ordinary waypoints stop helping after the run unique is secured. */
	public static boolean hideOrdinaryWaypoint(Critter critter, SafariSession session) {
		return onlyShowSparkling() || enabled() && (ignoreUniques() && isShared(critter)
			|| session != null && session.caughtByParty(critter));
	}

	public static boolean hideFloorDrops(SafariBiome biome, SafariSession session) {
		if (!enabled() || session == null) return false;
		return switch (biome) {
			case FOREST -> forestFloorDropsExhausted()
				|| allSharedFinished(session, "Bluebird", "Parakeet", "Macaw");
			case CAVERN -> sharedFinished(session, "Gemzie") || SafariObjectives.allGemsFound();
			case ICY -> icyFloorDropsExhausted(session);
			case HAUNTED -> (sharedFinished(session, "Doomspiral")
					|| SafariObjectives.incenseSecured() >= 4)
				&& (sharedFinished(session, "Gimmiegold")
					|| isShared(Critters.byName("Gimmiegold"))
						&& SafariObjectives.shiningCoinsHeld() >= 1);
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
		int required = troodon != null && isShared(troodon) ? 1 : 3;
		return brokenTroodonWalls() + SafariObjectives.icebreakersHeld() >= required;
	}

	private static long brokenTroodonWalls() {
		return WallTracker.TROODON.walls().stream()
			.filter(wall -> wall.state() == WallTracker.State.BROKEN).count();
	}

	public static boolean hideBirdFeed(SafariSession session) {
		return enabled() && allSharedFinished(session, "Bluebird", "Parakeet", "Macaw");
	}

	public static boolean hideNests(SafariSession session) {
		return sharedFinished(session, "Honeybug");
	}

	public static boolean hideMounds(SafariSession session) {
		return sharedFinished(session, "Rockmite");
	}

	public static boolean hideSnoozleWalls(SafariSession session) {
		return sharedFinished(session, "Snoozle");
	}

	public static boolean hideTroodonWalls(SafariSession session) {
		return sharedFinished(session, "Troodon");
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
