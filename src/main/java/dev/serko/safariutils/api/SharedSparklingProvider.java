package dev.serko.safariutils.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Optional source for the Sparkling species shared by a party. */
public interface SharedSparklingProvider {
	/** Reports automatic loading state and whether manual fallback controls are needed. */
	default PartyRefreshStatus partyRefreshStatus() {
		return new PartyRefreshStatus(null, false);
	}

	/** Cached mutual collection and member names for the currently observed roster. */
	default PartySparklingSnapshot partySnapshot() {
		return new PartySparklingSnapshot(java.util.List.of(), false);
	}

	/** Latest local-player collection obtained as part of a party refresh. */
	default Optional<SparklingPlayerLookup> cachedLocalCollection() {
		return Optional.empty();
	}

	/** Fetches every remote-player value displayed by the Sparkling screen at once. */
	CompletableFuture<SparklingPlayerLookup> lookupPlayer(String username);

	/** Earliest time another uncached authenticated profile request may begin. */
	default long lookupAvailableAt() {
		return 0L;
	}

	/** Observes roster changes; public builds have no provider and never call an API. */
	default void tick() {
	}

	/** Updates cached members and reports whether the catch reached the whole roster. */
	default boolean onSharedCatch(String species) {
		return true;
	}

	/** Clears roster stability after a definitive party membership change. */
	default void onPartyMembershipChanged() {
	}

	/** Releases resources owned by an optional provider when Minecraft closes. */
	default void shutdown() {
	}
}
