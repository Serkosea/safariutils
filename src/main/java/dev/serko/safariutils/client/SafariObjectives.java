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
	private static long gemzieDoorOpenedAt;
	private static boolean gemzieCaught;
	private static int placedGemMask;
	private static int incenseUsed;
	private static boolean doomspiralSpawned;
	private static boolean doomspiralCaught;
	private static boolean doomspiralRetreated;
	private static boolean wumpaSpawned;
	private static boolean wumpaCaught;
	private static boolean wumpaRetreated;
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
			count(inventory.getItem(slot));
		}
		int rawSeeds = currentInventory[5];
		int rawWorms = currentInventory[6];
		int rawBerries = currentInventory[7];
		// A carried stack is still owned by the player. Counting the menu cursor keeps
		// HUD and synchronized availability stable while an item is being rearranged.
		if (client.player.containerMenu != null) count(client.player.containerMenu.getCarried());
		// Forest feed also has a bounded post-close cursor guard for Hypixel's delayed
		// inventory resynchronization. Reuse that one normalized snapshot everywhere.
		int[] stableFeed = BirdfeederWatch.stableHeldCounts(rawSeeds, rawWorms, rawBerries);
		currentInventory[5] = stableFeed[0];
		currentInventory[6] = stableFeed[1];
		currentInventory[7] = stableFeed[2];
		if (reconcileDeathAt > 0 && System.currentTimeMillis() >= reconcileDeathAt) {
			reconcileDeathAt = 0;
			BirdfeederWatch.reconcileInventory(bagOfSeedsHeld(), wrigglewormsHeld(), yogiBerriesHeld());
			var session = dev.serko.safariutils.session.SessionManager.current();
			var gimmiegold = dev.serko.safariutils.data.Critters.byName("Gimmiegold");
			int caught = session == null || gimmiegold == null ? 0 : session.ownCatches(gimmiegold);
			ShiningCoinWatch.reconcileInventory(shiningCoinsHeld(), caught);
		}
		BirdfeederWatch.onStableInventoryUpdated(stableFeed);
	}

	private static void count(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		String name = stack.getHoverName().getString();
		for (int i = 0; i < TRACKED.length; i++) {
			if (!TRACKED[i].equals(name)) continue;
			currentInventory[i] += stack.getCount();
			return;
		}
	}

	public static int orangeGemsHeld() { return currentInventory[0]; }

	public static int purpleGemsHeld() { return currentInventory[1]; }

	public static int limeGemsHeld() { return currentInventory[2]; }

	public static int incenseHeld() { return currentInventory[4]; }

	public static int incenseUsed() { return incenseUsed; }

	public static boolean gemzieDoorOpened() { return gemzieDoorOpened; }

	public static boolean gemzieCaught() { return gemzieCaught; }

	/** The authoritative door message has had time to match the visible opening. */
	public static boolean gemzieDoorDisplayReady() {
		return gemzieDoorOpened && gemzieDoorOpenedAt > 0
			&& System.currentTimeMillis() - gemzieDoorOpenedAt >= 2_500L;
	}

	public static boolean doomspiralSpawned() { return doomspiralSpawned; }

	public static boolean doomspiralCaught() { return doomspiralCaught; }

	public static boolean doomspiralRetreated() { return doomspiralRetreated; }

	/** Haunted is terminal after either a catch or the one-attempt encounter retreating. */
	public static boolean doomspiralComplete() {
		return doomspiralCaught || doomspiralRetreated;
	}

	public static boolean wumpaSpawned() { return wumpaSpawned; }

	public static boolean wumpaCaught() { return wumpaCaught; }

	public static boolean wumpaRetreated() { return wumpaRetreated; }

	/** Icy is terminal after either catching Wumpa or fainting during its one attempt. */
	public static boolean wumpaComplete() {
		return wumpaCaught || wumpaRetreated;
	}

	/** Confirmed podium placements, or all three once the chamber door opens. */
	public static int placedGemMask() {
		return gemzieDoorOpened ? 7 : placedGemMask;
	}

	public static boolean canPersonallyOpenGemzieDoor(int placedMask) {
		if (gemzieDoorOpened) return true;
		return ((placedMask & 1) != 0 || limeGemsHeld() > 0)
			&& ((placedMask & 2) != 0 || orangeGemsHeld() > 0)
			&& ((placedMask & 4) != 0 || purpleGemsHeld() > 0);
	}

	public static boolean canPersonallyFinishDoomspiral(int candlesLit) {
		return doomspiralSpawned || incenseHeld() >= Math.max(0, 4 - candlesLit);
	}

	public static int icebreakersHeld() {
		return currentInventory[3];
	}

	public static int birdFeedHeld() {
		return currentInventory[5] + currentInventory[6] + currentInventory[7];
	}

	public static int bagOfSeedsHeld() {
		return currentInventory[5];
	}

	public static int wrigglewormsHeld() {
		return currentInventory[6];
	}

	public static int yogiBerriesHeld() {
		return currentInventory[7];
	}

	public static int shiningCoinsHeld() {
		return currentInventory[8];
	}

	/** Records objectives whose items have already been safely consumed. */
	public static void onChatMessage(String line) {
		placedGemMask |= placedGemMaskFromMessage(line);
		if (line.startsWith("A rumbling sound can be heard")) {
			gemzieDoorOpened = true;
			if (gemzieDoorOpenedAt == 0L) gemzieDoorOpenedAt = System.currentTimeMillis();
			placedGemMask = 7;
		}
		if (line.startsWith("You used the Soothing Incense to light the candle")) {
			incenseUsed = Math.min(4, incenseUsed + 1);
		}
		if (doomspiralObjectiveCompleteMessage(line)) doomspiralSpawned = true;
		if (line.startsWith("The Doomspiral retreats back underground")) {
			doomspiralSpawned = true;
			doomspiralRetreated = true;
		}
		if (line.startsWith("The Wumpa has awoken")) wumpaSpawned = true;
		if (line.contains("fainted by a Wumpa") && line.endsWith("lost some of your items!")) {
			wumpaSpawned = true;
			wumpaRetreated = true;
		}
		if (line.endsWith("lost some of your items!")) {
			reconcileDeathAt = System.currentTimeMillis() + 750;
		}
	}

	/** Records terminal encounter catches separately from their earlier spawn states. */
	public static void onCatch(String critterName) {
		if ("Gemzie".equals(critterName)) {
			gemzieDoorOpened = true;
			placedGemMask = 7;
			gemzieCaught = true;
		} else if ("Doomspiral".equals(critterName)) {
			doomspiralSpawned = true;
			doomspiralCaught = true;
		} else if ("Wumpa".equals(critterName)) {
			wumpaSpawned = true;
			wumpaCaught = true;
		}
	}

	/** Returns the established bit for an authoritative Gemzie podium message. */
	public static int placedGemMaskFromMessage(String line) {
		if (line.startsWith("You placed the Lime Gem on its podium!")) return 1;
		if (line.startsWith("You placed the Orange Gem on its podium!")) return 2;
		if (line.startsWith("You placed the Purple Gem on its podium!")) return 4;
		return 0;
	}

	/** Spawn and completion lines both prove that the four-candle objective finished. */
	public static boolean doomspiralObjectiveCompleteMessage(String line) {
		return line.startsWith("Your ritual summoned a Doomspiral")
			|| line.startsWith("Something stirs in the Haunted Biome")
			|| line.startsWith("The darkness in the Haunted Biome fades away");
	}

	public static void reset() {
		java.util.Arrays.fill(currentInventory, 0);
		gemzieDoorOpened = false;
		gemzieDoorOpenedAt = 0L;
		gemzieCaught = false;
		placedGemMask = 0;
		incenseUsed = 0;
		doomspiralSpawned = false;
		doomspiralCaught = false;
		doomspiralRetreated = false;
		wumpaSpawned = false;
		wumpaCaught = false;
		wumpaRetreated = false;
		reconcileDeathAt = 0;
		scanTicks = 0;
	}
}
