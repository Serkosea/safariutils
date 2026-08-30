package dev.serko.safariutils.client;

/** Tracks feed pickups and the birds produced by the Birdfeeder. */
public final class BirdfeederWatch {

	private static final String NAME = "Macaw";

	/** Feed items found on the Forest floor. Head-start items are added separately. */
	private static final java.util.Set<String> FEED_ITEMS =
		java.util.Set.of("Bag of Seeds", "Wriggleworm", "Yogi Berry");

	/**
	 * The Birdfeeder's own line, from its start, with the count and the species left
	 * open: {@code A Bluebird was attracted to the Birdfeeder!}, {@code Two Macaws were
	 * attracted to the Birdfeeder!}. Anchored, so a player quoting it cannot set it off.
	 */
	private static final java.util.regex.Pattern BIRDFEEDER = java.util.regex.Pattern.compile(
		"^\\S+ ([A-Za-z ]+?)s? (?:were|was) attracted to the Birdfeeder!$");

	/** Every piece of feed found this run, floor drops and the run's own starting item alike. */
	private static int feedFound;
	/** Feed acquired this run, retained even if some is later lost on death. */
	private static int feedAcquired;
	/** Forest pickups only; starting feed does not consume one of its nine drops. */
	private static int floorFeedFound;
	/** Every Birdfeeder spawn event observed this run, whatever species it produced. */
	private static int spawnEventsObserved;
	/** Bird species produced by observed feed uses this run. */
	private static final java.util.Set<dev.serko.safariutils.data.Critter> spawnedBirds =
		new java.util.HashSet<>();

	private BirdfeederWatch() {
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		// Starting feed is handled by HeadStartWatch because its chat line can omit items.
		if (line.startsWith("FLOOR DROP!")) {
			if (FEED_ITEMS.stream().anyMatch(line::contains)) {
				feedFound++;
				feedAcquired++;
				floorFeedFound++;
			}
			return;
		}

		java.util.regex.Matcher matcher = BIRDFEEDER.matcher(line);
		if (!matcher.matches()) return;

		dev.serko.safariutils.data.Critter bird =
			dev.serko.safariutils.data.Critters.byName(matcher.group(1).trim());
		if (bird == null && matcher.group(1).trim().endsWith("s")) {
			String singular = matcher.group(1).trim();
			bird = dev.serko.safariutils.data.Critters.byName(
				singular.substring(0, singular.length() - 1));
		}
		// A wording this loose will match anything shaped like the sentence, so the
		// species has to be one this mod knows before it is announced as a spawn.
		if (bird == null) return;

		spawnEventsObserved++;
		spawnedBirds.add(bird);
		if (NAME.equals(bird.name())) {
			announce();
		} else {
			EncounterAlerts.fireBirdfeederBird(bird);
		}
	}

	/** Credited by {@link HeadStartWatch} for feed found in the inventory scan. */
	public static void creditFeedFound(int amount) {
		feedFound += amount;
		feedAcquired += amount;
	}

	public static int feedAcquired() {
		return feedAcquired;
	}

	/** Whether every feed found this run has produced a spawn event. */
	public static boolean allFeedUsed() {
		return feedFound > 0 && spawnEventsObserved >= feedFound;
	}

	/** Only a full nine-feed Forest clear can prove that an unspawned bird is absent. */
	public static boolean allForestFeedUsed() {
		return spawnEventsObserved >= 9 && allFeedUsed();
	}

	/** Whether an observed feed use produced this bird at least once this run. */
	public static boolean everSpawned(dev.serko.safariutils.data.Critter bird) {
		return bird != null && spawnedBirds.contains(bird);
	}

	/** Feed found this run against feed already spent on a spawn event. */
	public static int remaining() {
		return dev.serko.safariutils.session.SessionManager.current() == null
			? Math.max(0, feedFound - spawnEventsObserved) : SafariObjectives.birdFeedHeld();
	}

	public static int floorFeedFound() {
		return floorFeedFound;
	}

	/** Replaces unspent feed after death with the inventory's authoritative balance. */
	public static void reconcileInventory(int held) {
		feedFound = spawnEventsObserved + Math.max(0, held);
	}

	private static void announce() {
		// Each server line represents a separate feed use. Several Macaw events can
		// legitimately occur in one run, so none are collapsed behind a cooldown.
		EncounterAlerts.onMacawSpawn();
	}

	/** A Macaw from the last run says nothing about this one. */
	public static void reset() {
		feedFound = 0;
		feedAcquired = 0;
		floorFeedFound = 0;
		spawnEventsObserved = 0;
		spawnedBirds.clear();
	}
}
