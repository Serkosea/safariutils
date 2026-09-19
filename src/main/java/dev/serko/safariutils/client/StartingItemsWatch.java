package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Activates a run and credits starting items from the full inventory.
 * Any normal Critter Capsule appearing during a fresh Safari visit is authoritative
 * proof that the server accepted a ticket. The run starts immediately, then one
 * inventory snapshot is taken 250 ms later so the rest of the allocation can settle.
 * Keeping that snapshot immutable prevents later floor drops or manual inventory
 * changes from changing the Starting Items message while party chat becomes ready.
 */
public final class StartingItemsWatch {

	private static final long POST_CAPSULE_DELAY_MILLIS = 250;
	private static final long ITEM_SYNC_WINDOW_MILLIS = 30_000;
	private static final long RETRY_DELAY_MILLIS = 250;

	private static long scanAtMillis;
	private static long scanDeadlineMillis;
	private static long capsulesDetectedAtMillis;
	private static int[] startingInventory;
	private static boolean startingItemsAnnounced;
	private static String activationTrigger = "capsule allocation";

	private StartingItemsWatch() {
	}

	/**
	 * Called every client tick. Inventory contents are frozen once, 250 ms after the
	 * normal capsule allocation first appears. Only message delivery may
	 * retry after that point; the inventory is never sampled again for this run.
	 */
	public static void tick() {
		if (!SafariLocation.inside()) {
			cancelPendingRun();
			return;
		}
		if (scanAtMillis == 0 || System.currentTimeMillis() < scanAtMillis) return;
		scanAtMillis = 0;

		Player player = Minecraft.getInstance().player;
		if (player == null) {
			// A visit may outlive an unusually slow player load. Keep the capsule watcher
			// alive for the visit rather than allowing a transient null player to disarm it.
			scanAtMillis = System.currentTimeMillis() + RETRY_DELAY_MILLIS;
			return;
		}
		Inventory inventory = player.getInventory();

		long now = System.currentTimeMillis();
		if (startingInventory == null) {
			int normalCapsules = countNormalCapsules(inventory);
			if (capsulesDetectedAtMillis == 0 && normalCapsules <= 0) {
				// Keep watching for the whole visit. Ticket interaction is only an early
				// hint, so it must never be required for a later allocation to be seen.
				scanAtMillis = now + RETRY_DELAY_MILLIS;
				DebugLog.line("HEADSTART", "waiting for starting capsule allocation capsules="
					+ normalCapsules);
				return;
			}
			if (capsulesDetectedAtMillis == 0) {
				capsulesDetectedAtMillis = now;
				scanDeadlineMillis = now + ITEM_SYNC_WINDOW_MILLIS;
				if (dev.serko.safariutils.session.SessionManager.current() == null) {
					dev.serko.safariutils.session.SessionManager.startSession(
						activationTrigger + " + capsule allocation");
				}
				scanAtMillis = now + POST_CAPSULE_DELAY_MILLIS;
				DebugLog.line("HEADSTART", "starting capsule allocation detected capsules="
					+ normalCapsules);
				return;
			}
			if (now - capsulesDetectedAtMillis < POST_CAPSULE_DELAY_MILLIS) {
				scanAtMillis = capsulesDetectedAtMillis + POST_CAPSULE_DELAY_MILLIS;
				return;
			}

			startingInventory = countStartingItems(inventory);
			int berries = startingInventory[6];
			int worms = startingInventory[7];
			int seeds = startingInventory[8];
			if (seeds + worms + berries > 0) {
				BirdfeederWatch.creditFeedFound(seeds, worms, berries);
			}
			dev.serko.safariutils.api.PartyItemSyncProviders.onStartingItems(startingInventory);
			if (startingInventory[4] > 0) ShiningCoinWatch.creditFound(startingInventory[4]);
			DebugLog.line("HEADSTART", "starting inventory frozen capsules=" + normalCapsules
				+ " found=" + java.util.Arrays.toString(startingInventory));
		}

		if (!startingItemsAnnounced) {
			if (dev.serko.safariutils.api.PartyItemSyncProviders.suppressStartingItems()) {
				startingItemsAnnounced = true;
				return;
			}
			SafariConfig.PartyConfig party = ConfigManager.get().party;
			String items = StartingItems.format(startingInventory, party.startingItemsMask);
			String command = party.startingItems().command();
			boolean verifiedParty = "pc".equals(command)
				&& dev.serko.safariutils.session.SessionManager.expectedRunPlayers() > 1;
			boolean partyUnavailable = "pc".equals(command) && !verifiedParty
				&& !PartyRosterWatch.canSendPartyChat();
			if (items.isEmpty() || command == null) {
				startingItemsAnnounced = true;
			} else if (!partyUnavailable) {
				String message = AlertText.format(party.startingItemsChatText, "<ITEMS>", items);
				if (!message.isBlank()) {
					if (verifiedParty) ChatQueue.enqueueVerifiedParty(command + " " + message);
					else EncounterAlerts.post(party.startingItems(), message);
				}
				startingItemsAnnounced = true;
			} else if (now < scanDeadlineMillis) {
				scanAtMillis = now + RETRY_DELAY_MILLIS;
			} else {
				startingItemsAnnounced = true;
			}
		}
	}

	private static int countNormalCapsules(Inventory inventory) {
		int capsules = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && "Critter Capsule".equals(stack.getHoverName().getString())) {
				capsules += stack.getCount();
			}
		}
		return capsules;
	}

	private static int[] countStartingItems(Inventory inventory) {
		int[] found = new int[StartingItems.ORDERED.size()];
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			int item = StartingItems.indexOf(stack.getHoverName().getString());
			if (item >= 0) found[item] += stack.getCount();
		}
		return found;
	}

	/** Watches a fresh Safari visit for the capsule allocation that proves run start. */
	public static void onSafariVisitStarted() {
		long now = System.currentTimeMillis();
		activationTrigger = "capsule allocation";
		scanAtMillis = now;
		scanDeadlineMillis = now + ITEM_SYNC_WINDOW_MILLIS;
		capsulesDetectedAtMillis = 0;
		startingInventory = null;
		startingItemsAnnounced = false;
	}

	/** Records an early ticket signal; capsule allocation remains authoritative. */
	public static void onTicketSubmitted(String trigger) {
		if (dev.serko.safariutils.session.SessionManager.current() != null) return;
		activationTrigger = trigger;
		long now = System.currentTimeMillis();
		scanAtMillis = scanAtMillis == 0 ? now : Math.min(scanAtMillis, now);
		scanDeadlineMillis = Math.max(scanDeadlineMillis, now + ITEM_SYNC_WINDOW_MILLIS);
		DebugLog.line("ACTIVATE", "ticket submitted; awaiting capsule allocation via " + trigger);
	}

	/** Discards an unfinished allocation watch when its Safari visit ends. */
	public static void cancelPendingRun() {
		scanAtMillis = 0;
		scanDeadlineMillis = 0;
		capsulesDetectedAtMillis = 0;
		startingInventory = null;
		startingItemsAnnounced = false;
	}
}
