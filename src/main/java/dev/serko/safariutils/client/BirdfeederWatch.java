package dev.serko.safariutils.client;

/** Tracks feed pickups and the birds produced by the Birdfeeder. */
public final class BirdfeederWatch {

	private static final String NAME = "Macaw";

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
	private static final int[] lastHeld = new int[3];
	/** Every Birdfeeder spawn event observed this run, whatever species it produced. */
	private static int spawnEventsObserved;
	/** Feed this player has stably transferred into the feeder this run. */
	private static int personalFeedDeposited;
	/** Spawn count when the first personal deposit was confirmed. */
	private static int personalSpawnBaseline = -1;
	/** Inventory decreases waiting out the same rejection/cursor buffer as the final stack. */
	private static int pendingPersonalDeposit;
	private static long pendingPersonalDepositAt;
	/** Bird species produced by observed feed uses this run. */
	private static final java.util.Set<dev.serko.safariutils.data.Critter> spawnedBirds =
		new java.util.HashSet<>();
	/** Feed alerts become meaningful after a proven clear or this player's ninth pickup. */
	private static boolean feedAlertsReady;
	private static boolean totalFeedAnnounced;
	private static boolean feedGoneAnnounced;
	/** Set only when the final held feed leaves inventory through the Birdfeeder. */
	private static boolean allFeedDeposited;
	/** A short stable-inventory check prevents cursor clicks from looking like deposits. */
	private static long pendingAllFeedDepositAt;
	/** Briefly identifies inventory losses caused by clicking the Birdfeeder. */
	private static long birdfeederInteractionUntil;
	private static net.minecraft.world.inventory.AbstractContainerMenu observedFeeder;
	private static boolean feederHadFeed;
	private static int emptyFeederTicks;
	private static int feederType = -1;
	private static int feederCount;
	private static long lastEmptyAlertAt;
	/** Feed left on the cursor when the feeder closes can disappear until Hypixel resyncs it. */
	private static final int[] closingCursorFeed = new int[3];
	private static long closingCursorFeedUntil;
	private static final long CURSOR_RESYNC_MILLIS = 12_000L;
	private static final long POSSIBLE_CURSOR_RESYNC_MILLIS = 2_000L;

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
		// Keep the transaction window alive while the menu is open. If the player
		// closes it immediately after depositing, the next inventory scan can still
		// attribute the accepted decrease to the feeder instead of an unrelated loss.
		birdfeederInteractionUntil = System.currentTimeMillis() + 500L;
		if (menu != observedFeeder) {
			observedFeeder = menu;
			feederHadFeed = false;
			emptyFeederTicks = 0;
		}
		if (menu.slots.size() <= 22) return;
		var contents = menu.getSlot(22).getItem();
		var carried = menu.getCarried();
		int carriedType = carried.isEmpty() ? -1 : feedTypeIn(carried.getHoverName().getString());
		if (carriedType >= 0) {
			java.util.Arrays.fill(closingCursorFeed, 0);
			closingCursorFeed[carriedType] = carried.getCount();
			int feederType = contents.isEmpty() ? -1 : feedTypeIn(contents.getHoverName().getString());
			boolean rejected = feederType >= 0 && feederType != carriedType;
			closingCursorFeedUntil = System.currentTimeMillis()
				+ (rejected ? CURSOR_RESYNC_MILLIS : POSSIBLE_CURSOR_RESYNC_MILLIS);
		} else {
			clearClosingCursorFeed();
		}
		String name = contents.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
		boolean hasFeed = !contents.isEmpty()
			&& (name.contains("seed") || name.contains("wriggleworm") || name.contains("yogi berr"));
		if (hasFeed) {
			feederHadFeed = true;
			emptyFeederTicks = 0;
			setFeederState(feedTypeIn(contents.getHoverName().getString()), contents.getCount());
		} else if (!feederHadFeed) {
			setFeederState(-1, 0);
		} else if (++emptyFeederTicks >= 2) {
			// Two observations avoid firing on a one-tick container refresh gap.
			feederHadFeed = false;
			emptyFeederTicks = 0;
			setFeederState(-1, 0);
			if (!dev.serko.safariutils.api.PartyItemSyncProviders.active()) {
				EncounterAlerts.onBirdfeederEmpty();
			}
		}
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		String normalized = line.toLowerCase(java.util.Locale.ROOT);
		if (normalized.contains("birdfeeder") && normalized.contains("already")
			&& normalized.contains("feed")) {
			pendingAllFeedDepositAt = 0;
			allFeedDeposited = false;
			pendingPersonalDeposit = 0;
			pendingPersonalDepositAt = 0;
			if (personalFeedDeposited == 0) personalSpawnBaseline = -1;
			DebugLog.line("INVENTORY", "Birdfeeder rejected deposit; pending all-feed alert cancelled");
			return;
		}
		// Starting feed is credited from StartingItemsWatch's frozen run-start snapshot.
		if (line.startsWith("FLOOR DROP!")) {
			int type = feedTypeIn(line);
			if (type >= 0) {
				feedFound++;
				feedAcquired++;
				floorFeedFound++;
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
		if (feederCount > 0) setFeederState(feederCount == 1 ? -1 : feederType,
			Math.max(0, feederCount - 1));
		spawnedBirds.add(bird);
		if (NAME.equals(bird.name())) {
			announce();
		} else {
			EncounterAlerts.fireAllBirdSpawn(bird);
		}
	}

	/** Credited by {@link StartingItemsWatch} from the frozen run-start inventory. */
	public static void creditFeedFound(int seeds, int worms, int berries) {
		int total = seeds + worms + berries;
		feedFound += total;
		feedAcquired += total;
	}

	public static int feedAcquired() {
		return feedAcquired;
	}

	/** Best local-client account of feed discovered this run. */
	public static int feedFound() {
		return feedFound;
	}

	/** Birdfeeder spawn lines observed by this client during the run. */
	public static int feedUsed() {
		return spawnEventsObserved;
	}

	/** Whether every feed found this run has produced a spawn event. */
	public static boolean allFeedUsed() {
		boolean foundFeedResolved = feedFound > 0 && spawnEventsObserved >= feedFound;
		// When this player is the only feeder user, accepted personal deposits and the
		// later spawn lines form a second complete account even if pickup tracking missed
		// a starting item. In a shared stack this remains only a fallback, never a reason
		// to override the feeder GUI or an item still held by the player.
		boolean personalDepositsResolved = allFeedDeposited
			&& feedAcquired > 0
			&& personalFeedDeposited >= feedAcquired
			&& personalSpawnBaseline >= 0
			&& spawnEventsObserved - personalSpawnBaseline >= personalFeedDeposited;
		return foundFeedResolved || personalDepositsResolved;
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
		int synchronizedRemaining = dev.serko.safariutils.api.PartyItemSyncProviders.feedRemaining();
		if (synchronizedRemaining >= 0) return synchronizedRemaining;
		return dev.serko.safariutils.session.SessionManager.current() == null
			? Math.max(0, feedFound - spawnEventsObserved) : SafariObjectives.birdFeedHeld();
	}

	public static int floorFeedFound() {
		return floorFeedFound;
	}

	/** Suppresses duplicate/local-useless empty alerts before they reach banner logic. */
	public static boolean claimEmptyAlert() {
		long now = System.currentTimeMillis();
		if (java.util.Arrays.stream(lastHeld).sum() <= 0 || now - lastEmptyAlertAt < 3_000L) {
			return false;
		}
		lastEmptyAlertAt = now;
		return true;
	}

	public static int feederType() {
		return feederType;
	}

	public static int feederCount() {
		return feederCount;
	}

	private static void setFeederState(int type, int count) {
		type = count > 0 ? type : -1;
		count = Math.max(0, count);
		if (feederType == type && feederCount == count) return;
		feederType = type;
		feederCount = count;
		dev.serko.safariutils.api.PartyItemSyncProviders.onBirdfeederState(type, count);
	}

	/** Marks the short transaction window opened by using the Birdfeeder NPC. */
	public static void onEntityUse(net.minecraft.world.entity.Entity entity) {
		if (!SafariLocation.inside() || entity == null
			|| !entity.getName().getString().contains("Birdfeeder")) return;
		birdfeederInteractionUntil = System.currentTimeMillis() + 500L;
	}

	/** Replaces unspent feed after death with the inventory's authoritative balance. */
	public static void reconcileInventory(int seeds, int worms, int berries) {
		int[] held = {seeds, worms, berries};
		feedFound = spawnEventsObserved + Math.max(0, seeds + worms + berries);
		for (int i = 0; i < held.length; i++) {
			lastHeld[i] = Math.max(0, held[i]);
		}
		onStableInventoryUpdated(held);
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
		onStableInventoryUpdated(stableHeldCounts(seeds, worms, berries));
	}

	/** Returns one cursor-safe feed snapshot for every local HUD and sync consumer. */
	static int[] stableHeldCounts(int seeds, int worms, int berries) {
		int[] held = {Math.max(0, seeds), Math.max(0, worms), Math.max(0, berries)};
		includeCarriedFeed(held);
		return held;
	}

	/** Processes a snapshot already normalized by {@link #stableHeldCounts(int, int, int)}. */
	static void onStableInventoryUpdated(int[] held) {
		dev.serko.safariutils.api.PartyItemSyncProviders.onInventoryFeed(held[0], held[1], held[2]);
		long now = System.currentTimeMillis();
		boolean birdfeederDeposit = isBirdfeederOpen()
			|| now <= birdfeederInteractionUntil;
		boolean depositedThisScan = false;
		int depositedAmount = 0;
		int returnedAmount = 0;
		for (int type = 0; type < held.length; type++) {
			int previous = lastHeld[type];
			lastHeld[type] = held[type];
			if (birdfeederDeposit && held[type] < previous) {
				depositedThisScan = true;
				depositedAmount += previous - held[type];
			} else if (held[type] > previous) {
				returnedAmount += held[type] - previous;
			}
		}
		if (returnedAmount > 0 && pendingPersonalDeposit > 0) {
			pendingPersonalDeposit = Math.max(0, pendingPersonalDeposit - returnedAmount);
			if (pendingPersonalDeposit == 0) {
				pendingPersonalDepositAt = 0;
				if (personalFeedDeposited == 0) personalSpawnBaseline = -1;
			}
		}
		if (depositedAmount > 0) {
			if (personalSpawnBaseline < 0) personalSpawnBaseline = spawnEventsObserved;
			pendingPersonalDeposit += depositedAmount;
			pendingPersonalDepositAt = now + 150L;
			DebugLog.line("INVENTORY", "Birdfeeder personal deposit awaiting confirmation: +"
				+ depositedAmount);
		} else if (pendingPersonalDeposit > 0 && now >= pendingPersonalDepositAt) {
			personalFeedDeposited += pendingPersonalDeposit;
			DebugLog.line("INVENTORY", "Birdfeeder personal deposit confirmed: +"
				+ pendingPersonalDeposit + " (total " + personalFeedDeposited + ")");
			pendingPersonalDeposit = 0;
			pendingPersonalDepositAt = 0;
		}
		int totalHeld = held[0] + held[1] + held[2];
		if (totalHeld > 0) {
			allFeedDeposited = false;
			pendingAllFeedDepositAt = 0;
		} else if (depositedThisScan) {
			// A rejected click briefly moves the stack onto the cursor. Wait for the
			// server to accept the transfer instead of trusting that transient frame.
			pendingAllFeedDepositAt = now + 150L;
			DebugLog.line("INVENTORY", "Birdfeeder final-feed deposit awaiting confirmation");
		} else if (pendingAllFeedDepositAt > 0 && now >= pendingAllFeedDepositAt) {
			pendingAllFeedDepositAt = 0;
			allFeedDeposited = true;
			DebugLog.line("INVENTORY", "Birdfeeder final-feed deposit confirmed");
		}
		if (!feedAlertsReady) return;
		if (!totalFeedAnnounced) {
			// At zero feed, wait for an in-flight feeder click to settle before choosing
			// between the mutually exclusive No Feed and All Feed Used outcomes.
			if (totalHeld == 0 && (pendingPersonalDeposit > 0 || pendingAllFeedDepositAt > 0)) return;
			totalFeedAnnounced = true;
			boolean feedWasUsed = personalFeedDeposited > 0 || allFeedDeposited;
			if (totalHeld > 0 || !feedWasUsed) {
				EncounterAlerts.onTotalFeed(SafariObjectives.bagOfSeedsHeld(),
					SafariObjectives.wrigglewormsHeld(), SafariObjectives.yogiBerriesHeld());
				// Choosing No Feed locks out a later All Feed Used message for this run.
				if (totalHeld == 0) feedGoneAnnounced = true;
			}
		}
		if (feedGoneAnnounced || !allFeedDeposited || totalHeld > 0) return;
		feedGoneAnnounced = true;
		if (!dev.serko.safariutils.api.PartyItemSyncProviders.active()) {
			EncounterAlerts.onFeedGone();
		}
	}

	/** A stack held by the cursor has not entered the feeder yet. */
	private static void includeCarriedFeed(int[] held) {
		var screen = ClientCompat.screen();
		if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) {
			var carried = container.getMenu().getCarried();
			if (!carried.isEmpty()) {
				int type = feedTypeIn(carried.getHoverName().getString());
				if (type >= 0) {
					held[type] += carried.getCount();
					return;
				}
			}
		}
		long now = System.currentTimeMillis();
		if (closingCursorFeedUntil == 0 || now > closingCursorFeedUntil) {
			clearClosingCursorFeed();
			return;
		}
		for (int type = 0; type < held.length; type++) {
			int missing = closingCursorFeed[type];
			if (missing <= 0) continue;
			if (held[type] >= missing) {
				closingCursorFeed[type] = 0;
			} else {
				held[type] += missing;
			}
		}
		if (java.util.Arrays.stream(closingCursorFeed).allMatch(value -> value == 0)) {
			clearClosingCursorFeed();
		}
	}

	private static void clearClosingCursorFeed() {
		java.util.Arrays.fill(closingCursorFeed, 0);
		closingCursorFeedUntil = 0;
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
		java.util.Arrays.fill(lastHeld, 0);
		spawnEventsObserved = 0;
		personalFeedDeposited = 0;
		personalSpawnBaseline = -1;
		pendingPersonalDeposit = 0;
		pendingPersonalDepositAt = 0;
		spawnedBirds.clear();
		feedAlertsReady = false;
		totalFeedAnnounced = false;
		feedGoneAnnounced = false;
		allFeedDeposited = false;
		pendingAllFeedDepositAt = 0;
		birdfeederInteractionUntil = 0;
		observedFeeder = null;
		feederHadFeed = false;
		emptyFeederTicks = 0;
		feederType = -1;
		feederCount = 0;
		lastEmptyAlertAt = 0;
		clearClosingCursorFeed();
	}
}
