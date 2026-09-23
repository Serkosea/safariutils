package dev.serko.safariutils.api;

import dev.serko.safariutils.client.HudPanel;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Opt-in party objective synchronization over parsed party chat messages. */
public interface PartyItemSyncProvider {
	default boolean allowMessage(Component message, boolean overlay) {
		return true;
	}

	default void tick() { }

	default void onRunStarted() { }

	default void onStartingItems(int[] counts) { }

	default void onInventoryFeed(int seeds, int worms, int berries) { }

	default void onBirdfeederState(int feedType, int count) { }

	default void onNestConfirmed(BlockPos pos) { }

	default void onServerMessage(String line) { }

	default void onPartyMembershipChanged() { }

	default boolean active() {
		return false;
	}

	default boolean suppressStartingItems() {
		return active();
	}

	default int feedRemaining() {
		return -1;
	}

	default boolean forestDropsComplete() {
		return false;
	}

	default boolean feedDone() {
		return false;
	}

	/** Biome-specific party objective panel supplied while synchronization is active. */
	default HudPanel objectivePanel() {
		return null;
	}

	/** Whether the objective in the local player's current biome is complete. */
	default boolean objectiveComplete() {
		return feedDone();
	}

	/**
	 * Whether this client may stop showing objective floor drops in {@code biome}.
	 * Completion is shared, while a held-item shortcut must remain local to the
	 * player who can personally finish the objective.
	 */
	default boolean objectiveAllowsFloorDropHide(SafariBiome biome) {
		return biome == SafariBiome.FOREST && forestDropsComplete();
	}
}
