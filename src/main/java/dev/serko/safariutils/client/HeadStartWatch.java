package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Credits Best Safari Ticket items from the full inventory after a run starts.
 * Hypixel's HEAD START message may omit additional items, so inventory state is the
 * sole source to avoid both undercounting and duplicate chat credit.
 */
public final class HeadStartWatch {

	private static final java.util.Set<String> FEED_ITEMS =
		java.util.Set.of("Bag of Seeds", "Wriggleworm", "Yogi Berry");
	private static final String COIN_ITEM = "Shining Coin";
	private static final String HEAD_START_LINE = "HEAD START!";
	/** Brief wait for head-start items to appear in the client inventory. */
	private static final long POST_MESSAGE_DELAY_MILLIS = 500;

	private static long scanAtMillis;
	/** The inventory counts as of the last scan, so a second one only credits what changed. */
	private static int lastScannedFeed;
	private static int lastScannedCoins;

	private HeadStartWatch() {
	}

	/**
	 * The one trigger: brings the scan forward to shortly after the actual signal
	 * that an item has been granted. Not one-shot — a run with more than one
	 * starting item could in principle see this line more than once in a way this
	 * has no way to rule out, so a later line still re-arms the scan rather than
	 * being ignored; see {@link #tick} for why running more than once is safe.
	 */
	public static void onChatMessage(String line) {
		if (!line.startsWith(HEAD_START_LINE)) return;
		scanAtMillis = System.currentTimeMillis() + POST_MESSAGE_DELAY_MILLIS;
	}

	/**
	 * Called every client tick; fires the scan when its scheduled time has passed.
	 * Safe to run more than once: only the delta since {@link #lastScannedFeed}
	 * and {@link #lastScannedCoins} is ever credited, so a second scan finding
	 * the same items already counted by the first credits nothing further.
	 */
	public static void tick() {
		if (scanAtMillis == 0 || System.currentTimeMillis() < scanAtMillis) return;
		scanAtMillis = 0;

		Player player = Minecraft.getInstance().player;
		if (player == null) return;
		Inventory inventory = player.getInventory();

		int feedFound = 0;
		int coinsFound = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			String name = stack.getHoverName().getString();
			if (FEED_ITEMS.contains(name)) feedFound += stack.getCount();
			else if (COIN_ITEM.equals(name)) coinsFound += stack.getCount();
		}

		int newFeed = Math.max(0, feedFound - lastScannedFeed);
		int newCoins = Math.max(0, coinsFound - lastScannedCoins);
		lastScannedFeed = feedFound;
		lastScannedCoins = coinsFound;

		if (newFeed > 0) BirdfeederWatch.creditFeedFound(newFeed);
		if (newCoins > 0) ShiningCoinWatch.creditFound(newCoins);
		DebugLog.line("HEADSTART", "scan found feed=" + feedFound + " coins=" + coinsFound
			+ " (credited feed=" + newFeed + " coins=" + newCoins + ")");
	}

	/** Clears the old baseline and scans every starting item after activation. */
	public static void reset() {
		scanAtMillis = System.currentTimeMillis() + POST_MESSAGE_DELAY_MILLIS;
		lastScannedFeed = 0;
		lastScannedCoins = 0;
	}
}
