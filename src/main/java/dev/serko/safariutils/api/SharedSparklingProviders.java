package dev.serko.safariutils.api;

import dev.serko.safariutils.client.OperationalLog;

import java.util.Optional;
import java.util.ServiceLoader;

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

	private static Optional<SharedSparklingProvider> load() {
		try {
			return ServiceLoader.load(SharedSparklingProvider.class).findFirst();
		} catch (RuntimeException error) {
			OperationalLog.error("PROVIDER/SHARED_SPARKLINGS_LOAD", error);
			return Optional.empty();
		}
	}
}
