package dev.serko.safariutils.api;

import java.util.Set;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Optional source for the Sparkling species shared by a party. */
public interface SharedSparklingProvider {
	/** Forces a new lookup for every member of the current stable Safari roster. */
	CompletableFuture<Set<String>> refreshCurrentParty();

	/** Reports cooldown and roster validity for the Sparkling screen's refresh button. */
	default PartyRefreshStatus partyRefreshStatus() {
		return new PartyRefreshStatus(0, 0L, null);
	}

	/** Cached mutual collection and member names for the currently observed roster. */
	default PartySparklingSnapshot partySnapshot() {
		return new PartySparklingSnapshot(java.util.List.of(), java.util.Set.of(), false);
	}

	/** Latest local-player collection obtained as part of a party refresh. */
	default Optional<SparklingPlayerLookup> cachedLocalCollection() {
		return Optional.empty();
	}

	/** Fetches every remote-player value displayed by the Sparkling screen at once. */
	CompletableFuture<SparklingPlayerLookup> lookupPlayer(String username);

	/** Observes roster changes; public builds have no provider and never call an API. */
	default void tick() {
	}

	/** Updates cached members after a catch known to have reached the whole roster. */
	default void onSharedCatch(String species) {
	}

	/** Clears roster stability after a definitive party membership change. */
	default void onPartyMembershipChanged() {
	}

	/** Releases resources owned by an optional provider when Minecraft closes. */
	default void shutdown() {
	}
}
