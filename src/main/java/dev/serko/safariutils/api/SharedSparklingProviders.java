package dev.serko.safariutils.api;


import java.net.URI;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.gui.screens.Screen;

/** Loads the shared key-free API client; backend credentials never ship in a build. */
public final class SharedSparklingProviders {
	private static final ServiceAccess<SharedSparklingProvider> PROVIDER =
		ServiceAccess.load(SharedSparklingProvider.class, "PROVIDER/SHARED_SPARKLINGS");

	private SharedSparklingProviders() {
	}

	public static boolean available() {
		return PROVIDER.available();
	}

	public static URI updatePage(URI fallback) {
		return PROVIDER.read("update-page", SharedSparklingProvider::updatePage, Optional.<URI>empty()).orElse(fallback);
	}

	public static void openTicketTradingTest(Screen parent) {
		PROVIDER.run("trading-test", provider -> provider.openTicketTradingTest(parent));
	}

	public static void tick() {
		PROVIDER.run("tick", SharedSparklingProvider::tick);
	}

	public static boolean onSharedCatch(String species) {
		return PROVIDER.read("shared-catch", provider -> provider.onSharedCatch(species), !available());
	}

	public static void onPartyMembershipChanged() {
		PROVIDER.run("membership", SharedSparklingProvider::onPartyMembershipChanged);
	}

	public static void shutdown() {
		PROVIDER.run("shutdown", SharedSparklingProvider::shutdown);
	}

	public static java.util.concurrent.CompletableFuture<Integer> refreshNameColour(
			String name, String uuid) {
		return available() ? PROVIDER.request("rank", provider -> provider.refreshNameColour(name, uuid))
			: java.util.concurrent.CompletableFuture.completedFuture(-1);
	}

	public static boolean specialName(String name) {
		return PROVIDER.read("special-name", provider -> provider.specialName(name), false);
	}

	public static Set<String> specialNames() {
		return PROVIDER.read("special-names", SharedSparklingProvider::specialNames, Set.of());
	}

	public static void rememberIdentity(String uuid, String name) {
		PROVIDER.run("identity", provider -> provider.rememberIdentity(uuid, name));
	}

	public static PartyRefreshStatus partyRefreshStatus() {
		return PROVIDER.read("refresh-status", SharedSparklingProvider::partyRefreshStatus, new PartyRefreshStatus(null, false));
	}

	public static PartySparklingSnapshot partySnapshot() {
		return PROVIDER.read("party-snapshot", SharedSparklingProvider::partySnapshot, new PartySparklingSnapshot(java.util.List.of(), false));
	}

	public static Optional<SparklingPlayerLookup> cachedLocalCollection() {
		return PROVIDER.read("local-collection", SharedSparklingProvider::cachedLocalCollection, Optional.empty());
	}

	public static long lookupAvailableAt() {
		return PROVIDER.read("lookup-cooldown", SharedSparklingProvider::lookupAvailableAt, 0L);
	}

	public static java.util.concurrent.CompletableFuture<SparklingPlayerLookup> lookupPlayer(String username) {
		return PROVIDER.request("lookup", provider -> provider.lookupPlayer(username));
	}
}
