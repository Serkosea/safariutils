package dev.serko.safariutils.api;

import dev.serko.safariutils.client.HudPanel;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/** Safe facade around the private party-sync provider, when one was included in the jar. */
public final class PartyItemSyncProviders {
	private static final Optional<PartyItemSyncProvider> PROVIDER = load();

	private PartyItemSyncProviders() {
	}

	private static Optional<PartyItemSyncProvider> load() {
		try {
			return ServiceLoader.load(PartyItemSyncProvider.class).findFirst();
		} catch (RuntimeException error) {
			OperationalLog.error("SYNC/LOAD", error);
			return Optional.empty();
		}
	}

	public static boolean allowMessage(Component message, boolean overlay) {
		return PROVIDER.map(provider -> provider.allowMessage(message, overlay)).orElse(true);
	}

	public static void tick() {
		PROVIDER.ifPresent(PartyItemSyncProvider::tick);
	}

	public static void onRunStarted() {
		PROVIDER.ifPresent(PartyItemSyncProvider::onRunStarted);
	}

	public static void onStartingItems(int[] counts) {
		PROVIDER.ifPresent(provider -> provider.onStartingItems(counts.clone()));
	}

	public static void onInventoryFeed(int seeds, int worms, int berries) {
		PROVIDER.ifPresent(provider -> provider.onInventoryFeed(seeds, worms, berries));
	}
	public static void onBirdfeederState(int feedType, int count) {
		PROVIDER.ifPresent(provider -> provider.onBirdfeederState(feedType, count));
	}
	public static void onNestConfirmed(BlockPos pos) {
		PROVIDER.ifPresent(provider -> provider.onNestConfirmed(pos.immutable()));
	}
	public static void onServerMessage(String line) {
		PROVIDER.ifPresent(provider -> provider.onServerMessage(line));
	}
	public static void onPartyMembershipChanged() {
		PROVIDER.ifPresent(PartyItemSyncProvider::onPartyMembershipChanged);
	}

	public static boolean active() {
		return PROVIDER.map(PartyItemSyncProvider::active).orElse(false);
	}

	public static boolean suppressStartingItems() {
		return PROVIDER.map(PartyItemSyncProvider::suppressStartingItems).orElse(false);
	}

	public static boolean whitelistedName(String name) {
		return PROVIDER.map(provider -> provider.whitelistedName(name)).orElse(false);
	}
	public static Set<String> whitelistedNames() {
		return PROVIDER.map(PartyItemSyncProvider::whitelistedNames).orElse(Set.of());
	}

	public static void rememberIdentity(String uuid, String name) {
		PROVIDER.ifPresent(provider -> provider.rememberIdentity(uuid, name));
	}

	public static int feedRemaining() {
		return PROVIDER.map(PartyItemSyncProvider::feedRemaining).orElse(-1);
	}

	public static boolean forestDropsComplete() {
		return PROVIDER.map(PartyItemSyncProvider::forestDropsComplete).orElse(false);
	}

	public static boolean feedDone() {
		return PROVIDER.map(PartyItemSyncProvider::feedDone).orElse(false);
	}

	public static HudPanel objectivePanel() {
		return PROVIDER.map(PartyItemSyncProvider::objectivePanel).orElse(null);
	}

	public static boolean objectiveComplete() {
		return PROVIDER.map(PartyItemSyncProvider::objectiveComplete).orElse(false);
	}

	public static boolean objectiveAllowsFloorDropHide(SafariBiome biome) {
		return PROVIDER.map(provider -> provider.objectiveAllowsFloorDropHide(biome)).orElse(false);
	}
}
