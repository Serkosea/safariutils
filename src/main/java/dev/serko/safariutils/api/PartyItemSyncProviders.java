package dev.serko.safariutils.api;

import dev.serko.safariutils.client.HudPanel;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.core.BlockPos;

/** Safe facade around the optional party-sync transport implementation. */
public final class PartyItemSyncProviders {
	private static final ServiceAccess<PartyItemSyncProvider> PROVIDER =
		ServiceAccess.load(PartyItemSyncProvider.class, "SYNC/ITEMS");

	private PartyItemSyncProviders() {
	}

	public static void tick() {
		PROVIDER.run("tick", PartyItemSyncProvider::tick);
	}

	public static void onRunStarted() {
		PROVIDER.run("run-started", PartyItemSyncProvider::onRunStarted);
	}

	public static void onStartingItems(int[] counts) {
		PROVIDER.run("onStartingItems", provider -> provider.onStartingItems(counts.clone()));
	}

	public static void onInventoryFeed(int seeds, int worms, int berries) {
		PROVIDER.run("onInventoryFeed", provider -> provider.onInventoryFeed(seeds, worms, berries));
	}

	public static void onBirdfeederState(int feedType, int count) {
		PROVIDER.run("onBirdfeederState", provider -> provider.onBirdfeederState(feedType, count));
	}

	public static void onNestConfirmed(BlockPos pos) {
		PROVIDER.run("onNestConfirmed", provider -> provider.onNestConfirmed(pos.immutable()));
	}

	public static void onServerMessage(String line, CritterEvent event) {
		PROVIDER.run("onServerMessage", provider -> provider.onServerMessage(line, event));
	}

	public static void onPartyMembershipChanged() {
		PROVIDER.run("membership", PartyItemSyncProvider::onPartyMembershipChanged);
	}

	public static boolean active() {
		return PROVIDER.read("active", PartyItemSyncProvider::active, false);
	}

	public static boolean suppressStartingItems() {
		return PROVIDER.read("suppressStartingItems", PartyItemSyncProvider::suppressStartingItems, false);
	}

	public static int feedRemaining() {
		return PROVIDER.read("feedRemaining", PartyItemSyncProvider::feedRemaining, -1);
	}

	public static boolean forestDropsComplete() {
		return PROVIDER.read("forestDropsComplete", PartyItemSyncProvider::forestDropsComplete, false);
	}

	public static boolean feedDone() {
		return PROVIDER.read("feedDone", PartyItemSyncProvider::feedDone, false);
	}

	public static HudPanel objectivePanel() {
		return PROVIDER.read("objectivePanel", PartyItemSyncProvider::objectivePanel, null);
	}

	public static boolean objectiveComplete() {
		return PROVIDER.read("objectiveComplete", PartyItemSyncProvider::objectiveComplete, false);
	}

	public static boolean objectiveAllowsFloorDropHide(SafariBiome biome) {
		return PROVIDER.read("floor-drop-hide", provider -> provider.objectiveAllowsFloorDropHide(biome), false);
	}

	public static String diagnosticSnapshot() {
		return PROVIDER.read("diagnosticSnapshot", PartyItemSyncProvider::diagnosticSnapshot, "unavailable");
	}
}
