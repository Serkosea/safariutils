package dev.serko.safariutils.api;

import dev.serko.safariutils.client.OperationalLog;

import java.net.URI;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import dev.serko.safariutils.client.SafariConfig;
import dev.serko.safariutils.data.Critter;

/** Loads the ignored private provider when it was explicitly included in a build. */
public final class SharedSparklingProviders {
	private static final Optional<SharedSparklingProvider> PROVIDER = load();

	private SharedSparklingProviders() {
	}

	public static Optional<SharedSparklingProvider> provider() {
		return PROVIDER;
	}

	public static boolean available() {
		return PROVIDER.isPresent();
	}

	public static URI updatePage(URI fallback) {
		return PROVIDER.flatMap(SharedSparklingProvider::updatePage).orElse(fallback);
	}

	public static void tickPrivateFeatures() {
		PROVIDER.ifPresent(SharedSparklingProvider::tickPrivateFeatures);
	}

	public static void onConnectionJoin() {
		PROVIDER.ifPresent(SharedSparklingProvider::onConnectionJoin);
	}

	public static void onConnectionExit() {
		PROVIDER.ifPresent(SharedSparklingProvider::onConnectionExit);
	}

	public static void onServerMessage(String line) {
		PROVIDER.ifPresent(provider -> provider.onServerMessage(line));
	}

	public static void onRunStarted() {
		PROVIDER.ifPresent(SharedSparklingProvider::onRunStarted);
	}

	public static void onSparklingDetected(Critter critter) {
		PROVIDER.ifPresent(provider -> provider.onSparklingDetected(critter));
	}

	public static void onLootSharedBy(String player) {
		PROVIDER.ifPresent(provider -> provider.onLootSharedBy(player));
	}

	public static void sanitizePrivateSettings(SafariConfig.SparklingConfig config) {
		PROVIDER.ifPresent(provider -> provider.sanitizePrivateSettings(config));
	}

	public static void openPrivateSettings() {
		PROVIDER.ifPresent(SharedSparklingProvider::openPrivateSettings);
	}

	public static void tick() {
		PROVIDER.ifPresent(SharedSparklingProvider::tick);
	}

	public static boolean onSharedCatch(String species) {
		return PROVIDER.map(provider -> provider.onSharedCatch(species)).orElse(true);
	}

	public static void onPartyMembershipChanged() {
		PROVIDER.ifPresent(SharedSparklingProvider::onPartyMembershipChanged);
	}

	public static void shutdown() {
		PROVIDER.ifPresent(SharedSparklingProvider::shutdown);
	}

	public static int nameColour(String name) {
		return PROVIDER.map(provider -> provider.nameColour(name)).orElse(-1);
	}

	public static java.util.concurrent.CompletableFuture<Integer> refreshNameColour(
			String name, String uuid) {
		return PROVIDER.map(provider -> provider.refreshNameColour(name, uuid))
			.orElseGet(() -> java.util.concurrent.CompletableFuture.completedFuture(-1));
	}

	public static boolean specialName(String name) {
		return PROVIDER.map(provider -> provider.specialName(name)).orElse(false);
	}

	public static Set<String> specialNames() {
		return PROVIDER.map(SharedSparklingProvider::specialNames).orElse(Set.of());
	}

	public static void rememberIdentity(String uuid, String name) {
		PROVIDER.ifPresent(provider -> provider.rememberIdentity(uuid, name));
	}

	private static Optional<SharedSparklingProvider> load() {
		try {
			return ServiceLoader.load(SharedSparklingProvider.class).findFirst();
		} catch (RuntimeException error) {
			OperationalLog.error("PROVIDER/SHARED_SPARKLINGS_LOAD", error);
			return Optional.empty();
		}
	}
}
