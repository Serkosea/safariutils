package dev.serko.safariutils.client;

/** Tracks feed pickups and the birds produced by the Birdfeeder. */
public final class BirdfeederWatch {

	private static final String NAME = "Macaw";
	private static final String[] FEED_LABELS = {"Seeds", "Worms", "Berries"};

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
	/** Feed of each type that can still legitimately produce a bird this run. */
	private static final int[] feedRequired = new int[3];
	private static final int[] feedSpawned = new int[3];
	private static final int[] lastHeld = new int[3];
	private static final boolean[] feedTypeAnnounced = new boolean[3];
	private static final java.util.ArrayDeque<Integer> pendingFeedUses = new java.util.ArrayDeque<>();
	private static int unassignedSpawnEvents;
	/** Every Birdfeeder spawn event observed this run, whatever species it produced. */
	private static int spawnEventsObserved;
	/** Bird species produced by observed feed uses this run. */
	private static final java.util.Set<dev.serko.safariutils.data.Critter> spawnedBirds =
		new java.util.HashSet<>();
	/** Feed alerts become meaningful after a proven clear or this player's ninth pickup. */
	private static boolean feedAlertsReady;
	private static boolean totalFeedAnnounced;
	private static boolean feedGoneAnnounced;
	/** Set only when the final held feed leaves inventory through the Birdfeeder. */
	private static boolean allFeedDeposited;
	private static long feedTypeBannerUntil;
	/** Briefly identifies inventory losses caused by clicking the Birdfeeder. */
	private static long birdfeederInteractionUntil;
	private static net.minecraft.world.inventory.AbstractContainerMenu observedFeeder;
	private static boolean feederHadFeed;
	private static int emptyFeederTicks;

	private BirdfeederWatch() {
	}

	/** Slot 22 is the feeder's contents, not the player's inventory or feed buttons. */
	public static void tickMenu() {
		var screen = ClientCompat.screen();
		if (!SafariLocation.inside()
			|| !(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container)
			|| !"Birdfeeder".equals(screen.getTitle().getString())) {
			observedFeeder = null;
			feederHadFeed = false;
			emptyFeederTicks = 0;
			return;
		}
		var menu = container.getMenu();
		if (menu != observedFeeder) {
			observedFeeder = menu;
			feederHadFeed = false;
			emptyFeederTicks = 0;
		}
		if (menu.slots.size() <= 22) return;
		var contents = menu.getSlot(22).getItem();
		String name = contents.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
		boolean hasFeed = !contents.isEmpty()
			&& (name.contains("seed") || name.contains("wriggleworm") || name.contains("yogi berr"));
		if (hasFeed) {
			feederHadFeed = true;
			emptyFeederTicks = 0;
		} else if (feederHadFeed && ++emptyFeederTicks >= 2) {
			// Two observations avoid firing on a one-tick container refresh gap.
			feederHadFeed = false;
			emptyFeederTicks = 0;
			EncounterAlerts.onBirdfeederEmpty();
		}
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		// Starting feed is handled by HeadStartWatch because its chat line can omit items.
		if (line.startsWith("FLOOR DROP!")) {
			int type = feedTypeIn(line);
			if (type >= 0) {
				feedFound++;
				feedAcquired++;
				floorFeedFound++;
				feedRequired[type]++;
				if (floorFeedFound >= 9) markFeedAlertsReady();
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
		confirmNextFeedUse();
		spawnedBirds.add(bird);
		if (NAME.equals(bird.name())) {
			announce();
		} else {
			EncounterAlerts.fireAllBirdSpawn(bird);
		}
	}

	/** Credited by {@link HeadStartWatch} for feed found in the inventory scan. */
	public static void creditFeedFound(int seeds, int worms, int berries) {
		int[] amounts = {seeds, worms, berries};
		for (int i = 0; i < amounts.length; i++) feedRequired[i] += amounts[i];
		int total = seeds + worms + berries;
		feedFound += total;
		feedAcquired += total;
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

	/** Marks the short transaction window opened by using the Birdfeeder NPC. */
	public static void onEntityUse(net.minecraft.world.entity.Entity entity) {
		if (entity == null || !entity.getName().getString().contains("Birdfeeder")) return;
		birdfeederInteractionUntil = System.currentTimeMillis() + 1_000L;
	}

	/** Replaces unspent feed after death with the inventory's authoritative balance. */
	public static void reconcileInventory(int seeds, int worms, int berries) {
		int[] held = {seeds, worms, berries};
		feedFound = spawnEventsObserved + Math.max(0, seeds + worms + berries);
		for (int i = 0; i < held.length; i++) {
			feedRequired[i] = feedSpawned[i] + Math.max(0, held[i]);
			lastHeld[i] = Math.max(0, held[i]);
		}
		pendingFeedUses.clear();
		unassignedSpawnEvents = 0;
		onInventoryUpdated(seeds, worms, berries);
	}

	/** Latches Forest completion; the next inventory scan supplies authoritative totals. */
	public static void onForestFloorDropsDone() {
		markFeedAlertsReady();
	}

	private static void markFeedAlertsReady() {
		feedAlertsReady = true;
	}

	/** Sends each feed alert once, using the latest inventory scan rather than chat timing. */
	public static void onInventoryUpdated(int seeds, int worms, int berries) {
		int[] held = {Math.max(0, seeds), Math.max(0, worms), Math.max(0, berries)};
		boolean birdfeederDeposit = isBirdfeederOpen()
			|| System.currentTimeMillis() <= birdfeederInteractionUntil;
		boolean depositedThisScan = false;
		for (int type = 0; type < held.length; type++) {
			int previous = lastHeld[type];
			if (birdfeederDeposit) {
				for (int used = held[type]; used < previous; used++) pendingFeedUses.addLast(type);
			}
			lastHeld[type] = held[type];
			// A feed stack cannot be removed from the Birdfeeder. Reaching zero during
			// its transaction therefore proves this type has been fully deposited,
			// without confusing a manual item drop for a completed feed type.
			if (birdfeederDeposit && previous > 0 && held[type] == 0) {
				announceFeedTypeGone(type);
			}
			if (birdfeederDeposit && held[type] < previous) depositedThisScan = true;
		}
		int totalHeld = held[0] + held[1] + held[2];
		if (totalHeld > 0) allFeedDeposited = false;
		else if (depositedThisScan) allFeedDeposited = true;
		while (unassignedSpawnEvents > 0 && !pendingFeedUses.isEmpty()) {
			feedSpawned[pendingFeedUses.removeFirst()]++;
			unassignedSpawnEvents--;
		}
		if (!feedAlertsReady) return;
		if (!totalFeedAnnounced) {
			totalFeedAnnounced = true;
			// Zero feed is represented by the more useful All Feed Used alert.
			if (SafariObjectives.birdFeedHeld() > 0) {
				EncounterAlerts.onTotalFeed(SafariObjectives.bagOfSeedsHeld(),
					SafariObjectives.wrigglewormsHeld(), SafariObjectives.yogiBerriesHeld());
			}
		}
		if (feedGoneAnnounced || !allFeedDeposited || totalHeld > 0) return;
		// Let the final type-specific banner finish before the broader all-feed banner.
		if (System.currentTimeMillis() < feedTypeBannerUntil) return;
		feedGoneAnnounced = true;
		EncounterAlerts.onFeedGone();
	}

	private static void confirmNextFeedUse() {
		if (pendingFeedUses.isEmpty()) unassignedSpawnEvents++;
		else feedSpawned[pendingFeedUses.removeFirst()]++;
	}

	private static void announceFeedTypeGone(int type) {
		if (feedTypeAnnounced[type] || feedRequired[type] <= 0) return;
		feedTypeAnnounced[type] = true;
		if (EncounterAlerts.onFeedTypeGone(FEED_LABELS[type])) {
			long duration = (long) (ConfigManager.get().alerts.feedTypeGoneDuration * 1000);
			feedTypeBannerUntil = Math.max(feedTypeBannerUntil,
				System.currentTimeMillis() + Math.max(500L, duration));
		}
	}

	private static boolean isBirdfeederOpen() {
		var screen = ClientCompat.screen();
		return screen != null && screen.getTitle().getString().contains("Birdfeeder");
	}

	private static int feedTypeIn(String line) {
		if (line.contains("Bag of Seeds")) return 0;
		if (line.contains("Wriggleworm")) return 1;
		if (line.contains("Yogi Berry")) return 2;
		return -1;
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
		java.util.Arrays.fill(feedRequired, 0);
		java.util.Arrays.fill(feedSpawned, 0);
		java.util.Arrays.fill(lastHeld, 0);
		java.util.Arrays.fill(feedTypeAnnounced, false);
		pendingFeedUses.clear();
		unassignedSpawnEvents = 0;
		spawnEventsObserved = 0;
		spawnedBirds.clear();
		feedAlertsReady = false;
		totalFeedAnnounced = false;
		feedGoneAnnounced = false;
		allFeedDeposited = false;
		feedTypeBannerUntil = 0;
		birdfeederInteractionUntil = 0;
		observedFeeder = null;
		feederHadFeed = false;
		emptyFeederTicks = 0;
	}
}
