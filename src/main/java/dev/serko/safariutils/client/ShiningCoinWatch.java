package dev.serko.safariutils.client;

/**
 * Tracks Shining Coins acquired this run for Gimmiegold progress. Head-start coins
 * come from the inventory scan because Hypixel may omit extra items from chat.
 */
public final class ShiningCoinWatch {

	private static final String FLOOR_DROP = "FLOOR DROP!";
	private static final String COIN = "Shining Coin";

	private static int found;
	/** Coins acquired this run, retained even if some are spent or lost. */
	private static int acquired;
	private static int floorFound;

	private ShiningCoinWatch() {
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		if (!line.startsWith(FLOOR_DROP)) return;
		if (!line.contains(COIN)) return;
		found++;
		acquired++;
		floorFound++;
	}

	/** Credited by {@link HeadStartWatch} for coins found in the inventory scan. */
	public static void creditFound(int amount) {
		found += amount;
		acquired += amount;
	}

	public static int acquired() {
		return acquired;
	}

	public static int floorFound() {
		return floorFound;
	}

	/** Coins picked up this run. */
	public static int found() {
		return found;
	}

	/** Keeps already-used coins while replacing the post-death unspent balance. */
	public static void reconcileInventory(int held, int ownGimmiegoldsCaught) {
		found = Math.max(0, held) + Math.max(0, ownGimmiegoldsCaught);
	}

	/** A count from the last run says nothing about this one. */
	public static void reset() {
		found = 0;
		acquired = 0;
		floorFound = 0;
	}
}
