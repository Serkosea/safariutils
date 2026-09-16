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

	private static final String HEAD_START_LINE = "HEAD START!";
	/** Brief wait for head-start items to appear in the client inventory. */
	private static final long POST_MESSAGE_DELAY_MILLIS = 500;
	private static final long ITEM_SYNC_WINDOW_MILLIS = 8_000;
	private static final long RETRY_DELAY_MILLIS = 250;
	private static final long INVENTORY_STABLE_MILLIS = 1_000;
	private static final long MINIMUM_SCAN_DELAY_MILLIS = 1_000;

	private static long scanAtMillis;
	private static long headStartAtMillis;
	private static long scanDeadlineMillis;
	private static long inventoryStableSince;
	private static String lastInventorySignature = "";
	/** The inventory counts as of the last scan, so a second one only credits what changed. */
	private static final int[] lastScanned = new int[StartingItems.ORDERED.size()];
	private static boolean headStartSeen;
	private static boolean startingItemsAnnounced;

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
		headStartAtMillis = System.currentTimeMillis();
		headStartSeen = true;
		scanAtMillis = headStartAtMillis + POST_MESSAGE_DELAY_MILLIS;
		scanDeadlineMillis = headStartAtMillis + ITEM_SYNC_WINDOW_MILLIS;
	}

	/**
	 * Called every client tick; fires the scan when its scheduled time has passed.
	 * Safe to run more than once: only the delta since {@link #lastScanned}
	 * is ever credited, so a second scan finding
	 * the same items already counted by the first credits nothing further.
	 */
	public static void tick() {
		if (scanAtMillis == 0 || System.currentTimeMillis() < scanAtMillis) return;
		scanAtMillis = 0;

		Player player = Minecraft.getInstance().player;
		if (player == null) return;
		Inventory inventory = player.getInventory();

		int[] found = new int[StartingItems.ORDERED.size()];
		StringBuilder inventorySignature = new StringBuilder();
		boolean hasCritterCapsule = false;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			String name = stack.getHoverName().getString();
			inventorySignature.append(i).append(':').append(name).append(':')
				.append(stack.getCount()).append('|');
			if (name.endsWith("Critter Capsule")) hasCritterCapsule = true;
			int item = StartingItems.indexOf(name);
			if (item >= 0) found[item] += stack.getCount();
		}
		long now = System.currentTimeMillis();
		String signature = inventorySignature.toString();
		if (!signature.equals(lastInventorySignature)) {
			lastInventorySignature = signature;
			inventoryStableSince = now;
		}
		boolean inventoryReady = hasCritterCapsule
			&& now - headStartAtMillis >= MINIMUM_SCAN_DELAY_MILLIS
			&& now - inventoryStableSince >= INVENTORY_STABLE_MILLIS;

		int[] newlyFound = new int[found.length];
		for (int i = 0; i < newlyFound.length; i++) {
			newlyFound[i] = Math.max(0, found[i] - lastScanned[i]);
			lastScanned[i] = found[i];
		}
		int newBerries = newlyFound[6];
		int newWorms = newlyFound[7];
		int newSeeds = newlyFound[8];
		if (newSeeds + newWorms + newBerries > 0) {
			BirdfeederWatch.creditFeedFound(newSeeds, newWorms, newBerries);
		}
		if (newlyFound[4] > 0) ShiningCoinWatch.creditFound(newlyFound[4]);
		if (headStartSeen && !startingItemsAnnounced) {
			if (!inventoryReady && now < scanDeadlineMillis) {
				scanAtMillis = now + RETRY_DELAY_MILLIS;
				DebugLog.line("HEADSTART", "waiting for stable populated inventory capsule="
					+ hasCritterCapsule + " found=" + java.util.Arrays.toString(found));
				return;
			}
			SafariConfig.PartyConfig party = ConfigManager.get().party;
			String items = StartingItems.format(found, party.startingItemsMask);
			String command = party.startingItems().command();
			boolean verifiedParty = "pc".equals(command)
				&& SafariPartyWatch.joinedPlayers() > 1;
			boolean partyUnavailable = "pc".equals(command) && !verifiedParty
				&& !PartyRosterWatch.canSendPartyChat();
			if (!items.isEmpty() && !partyUnavailable) {
				String message = AlertText.format(party.startingItemsChatText, "<ITEMS>", items);
				if (!message.isBlank()) {
					if (verifiedParty) ChatQueue.enqueueVerifiedParty(command + " " + message);
					else EncounterAlerts.post(party.startingItems(), message);
				}
				startingItemsAnnounced = true;
			} else if (command == null) {
				startingItemsAnnounced = true;
			} else if (now < scanDeadlineMillis) {
				scanAtMillis = now + RETRY_DELAY_MILLIS;
			} else {
				startingItemsAnnounced = true;
			}
		}
		DebugLog.line("HEADSTART", "scan found=" + java.util.Arrays.toString(found)
			+ " credited=" + java.util.Arrays.toString(newlyFound));
	}

	/** Clears the old baseline and scans every starting item after activation. */
	public static void reset() {
		long now = System.currentTimeMillis();
		boolean recentHeadStart = headStartAtMillis > 0
			&& now - headStartAtMillis <= ITEM_SYNC_WINDOW_MILLIS;
		scanAtMillis = now + POST_MESSAGE_DELAY_MILLIS;
		java.util.Arrays.fill(lastScanned, 0);
		lastInventorySignature = "";
		inventoryStableSince = now;
		headStartSeen = recentHeadStart;
		if (recentHeadStart) scanDeadlineMillis = headStartAtMillis + ITEM_SYNC_WINDOW_MILLIS;
		else scanDeadlineMillis = 0;
		startingItemsAnnounced = false;
	}
}
