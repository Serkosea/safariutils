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
	private static final int[] lastScannedFeed = new int[3];
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

		int[] feedFound = new int[3];
		int coinsFound = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			String name = stack.getHoverName().getString();
			if ("Bag of Seeds".equals(name)) feedFound[0] += stack.getCount();
			else if ("Wriggleworm".equals(name)) feedFound[1] += stack.getCount();
			else if ("Yogi Berry".equals(name)) feedFound[2] += stack.getCount();
			else if (COIN_ITEM.equals(name)) coinsFound += stack.getCount();
		}

		int[] newFeed = new int[3];
		for (int i = 0; i < newFeed.length; i++) {
			newFeed[i] = Math.max(0, feedFound[i] - lastScannedFeed[i]);
			lastScannedFeed[i] = feedFound[i];
		}
		int newCoins = Math.max(0, coinsFound - lastScannedCoins);
		lastScannedCoins = coinsFound;

		if (newFeed[0] + newFeed[1] + newFeed[2] > 0) {
			BirdfeederWatch.creditFeedFound(newFeed[0], newFeed[1], newFeed[2]);
		}
		if (newCoins > 0) ShiningCoinWatch.creditFound(newCoins);
		DebugLog.line("HEADSTART", "scan found feed=" + java.util.Arrays.toString(feedFound)
			+ " coins=" + coinsFound + " (credited feed=" + java.util.Arrays.toString(newFeed)
			+ " coins=" + newCoins + ")");
	}

	/** Clears the old baseline and scans every starting item after activation. */
	public static void reset() {
		scanAtMillis = System.currentTimeMillis() + POST_MESSAGE_DELAY_MILLIS;
		java.util.Arrays.fill(lastScannedFeed, 0);
		lastScannedCoins = 0;
	}
}
