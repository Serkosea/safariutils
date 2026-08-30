package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Cumulative quest-item finds used by Sparkling Mode's early-stop rules. */
public final class SafariObjectives {
	private static final int INVENTORY_SCAN_INTERVAL_TICKS = 5;
	private static final String[] TRACKED = {
		"Orange Gem", "Purple Gem", "Lime Gem", "Icebreaker", "Soothing Incense",
		"Bag of Seeds", "Wriggleworm", "Yogi Berry", "Shining Coin"
	};
	private static final int[] currentInventory = new int[TRACKED.length];
	private static boolean gemzieDoorOpened;
	private static int incenseUsed;
	private static long reconcileDeathAt;
	private static int scanTicks;

	private SafariObjectives() {
	}

	public static void tick() {
		if (dev.serko.safariutils.session.SessionManager.current() == null) return;
		if (++scanTicks < INVENTORY_SCAN_INTERVAL_TICKS
			&& (reconcileDeathAt == 0 || System.currentTimeMillis() < reconcileDeathAt)) return;
		scanTicks = 0;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		Inventory inventory = client.player.getInventory();
		java.util.Arrays.fill(currentInventory, 0);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) continue;
			String name = stack.getHoverName().getString();
			for (int i = 0; i < TRACKED.length; i++) {
				if (TRACKED[i].equals(name)) {
					currentInventory[i] += stack.getCount();
					break;
				}
			}
		}
		if (reconcileDeathAt > 0 && System.currentTimeMillis() >= reconcileDeathAt) {
			reconcileDeathAt = 0;
			BirdfeederWatch.reconcileInventory(birdFeedHeld());
			var session = dev.serko.safariutils.session.SessionManager.current();
			var gimmiegold = dev.serko.safariutils.data.Critters.byName("Gimmiegold");
			int caught = session == null || gimmiegold == null ? 0 : session.ownCatches(gimmiegold);
			ShiningCoinWatch.reconcileInventory(shiningCoinsHeld(), caught);
		}
	}

	public static boolean allGemsFound() {
		return gemzieDoorOpened
			|| currentInventory[0] > 0 && currentInventory[1] > 0 && currentInventory[2] > 0;
	}

	public static int icebreakersHeld() {
		return currentInventory[3];
	}

	public static int incenseSecured() {
		return incenseUsed + currentInventory[4];
	}

	public static int birdFeedHeld() {
		return currentInventory[5] + currentInventory[6] + currentInventory[7];
	}

	public static int shiningCoinsHeld() {
		return currentInventory[8];
	}

	/** Records objectives whose items have already been safely consumed. */
	public static void onChatMessage(String line) {
		if (line.startsWith("A rumbling sound can be heard")) gemzieDoorOpened = true;
		if (line.startsWith("You used the Soothing Incense to light the candle")) incenseUsed++;
		if (line.endsWith("You fainted and lost some of your items!")) {
			reconcileDeathAt = System.currentTimeMillis() + 750;
		}
	}

	public static void reset() {
		java.util.Arrays.fill(currentInventory, 0);
		gemzieDoorOpened = false;
		incenseUsed = 0;
		reconcileDeathAt = 0;
		scanTicks = 0;
	}
}
