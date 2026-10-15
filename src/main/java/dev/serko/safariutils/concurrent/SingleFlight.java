package dev.serko.safariutils.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Publishes ownership before starting work, including work that completes synchronously. */
public final class SingleFlight {
	private SingleFlight() { }

	public static <K, V> CompletableFuture<V> request(
			ConcurrentMap<K, CompletableFuture<V>> pending, K key,
			Supplier<CompletableFuture<V>> start) {
		CompletableFuture<V> owner = new CompletableFuture<>();
		CompletableFuture<V> existing = pending.putIfAbsent(key, owner);
		if (existing != null) return existing.copy();
		try {
			start.get().whenComplete((value, error) -> {
				pending.remove(key, owner);
				if (error == null) owner.complete(value);
				else owner.completeExceptionally(error);
			});
		} catch (Throwable error) {
			pending.remove(key, owner);
			owner.completeExceptionally(error);
		}
		// A consumer cancelling its subscription must not cancel another consumer's work.
		return owner.copy();
	}
}
