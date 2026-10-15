package dev.serko.safariutils.api;

import dev.serko.safariutils.client.HudPanel;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.core.BlockPos;

/** Automatic party objective synchronization with a transport-neutral state model. */
public interface PartyItemSyncProvider {
	default void tick() { }

	default void onRunStarted() { }

	default void onStartingItems(int[] counts) { }

	default void onInventoryFeed(int seeds, int worms, int berries) { }

	default void onBirdfeederState(int feedType, int count) { }

	default void onNestConfirmed(BlockPos pos) { }

	default void onServerMessage(String line, CritterEvent event) { }

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

	/** Identifier-free state used by automatic change-only diagnostics. */
	default String diagnosticSnapshot() {
		return "inactive";
	}
}
