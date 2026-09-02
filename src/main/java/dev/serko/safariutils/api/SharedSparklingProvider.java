package dev.serko.safariutils.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Optional source for the Sparkling species shared by a party. */
public interface SharedSparklingProvider {
	/** Forces a new lookup for every member of the current stable Safari roster. */
	CompletableFuture<Set<String>> refreshCurrentParty();

	/** Fetches one player's Sparkling species without changing the active shared list. */
	CompletableFuture<Set<String>> lookupPlayer(String username);

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
