package dev.serko.safariutils.api;

import dev.serko.safariutils.SafariUtils;

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

	public static void onSharedCatch(String species) {
		PROVIDER.ifPresent(provider -> provider.onSharedCatch(species));
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
			SafariUtils.LOGGER.error("Could not load the private shared-Sparkling provider", error);
			return Optional.empty();
		}
	}
}
