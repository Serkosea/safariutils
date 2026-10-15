package dev.serko.safariutils.state;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Owner-thread bounded deduplicating inbox for observations awaiting missing context. */
public final class DeferredObservations<T> {
	private final LinkedHashMap<T, Long> pending = new LinkedHashMap<>();
	private final int limit;
	private final long lifetimeNanos;
	private final LongSupplier clock;
	private long generation;

	public DeferredObservations(int limit, long lifetimeNanos) {
		this(limit, lifetimeNanos, System::nanoTime);
	}

	public DeferredObservations(int limit, long lifetimeNanos, LongSupplier clock) {
		if (limit <= 0 || lifetimeNanos <= 0) throw new IllegalArgumentException();
		this.limit = limit;
		this.lifetimeNanos = lifetimeNanos;
		this.clock = Objects.requireNonNull(clock);
	}

	/** Returns false for a duplicate. Oldest pending observation is evicted at capacity. */
	public boolean offer(T observation) {
		Objects.requireNonNull(observation);
		long now = clock.getAsLong();
		pending.values().removeIf(start -> now - start >= lifetimeNanos);
		if (pending.containsKey(observation)) return false;
		if (pending.size() == limit) pending.pollFirstEntry();
		pending.put(observation, now);
		return true;
	}

	/** Predicate returns true once handled or rejected; expired observations are never delivered. */
	public void drain(Predicate<T> handled) {
		if (pending.isEmpty()) return;
		long now = clock.getAsLong();
		long expected = generation;
		// Handling can end a visit and clear the inbox. Never iterate a live map across that callback.
		for (var entry : java.util.List.copyOf(pending.entrySet())) {
			if (generation != expected) break;
			if (!pending.remove(entry.getKey(), entry.getValue())) continue;
			if (now - entry.getValue() >= lifetimeNanos) continue;
			boolean consumed = false;
			try { consumed = handled.test(entry.getKey()); }
			finally {
				if (!consumed && generation == expected && pending.size() < limit)
					pending.putIfAbsent(entry.getKey(), entry.getValue());
			}
		}
	}

	public int size() { return pending.size(); }
	public void clear() { generation++; pending.clear(); }
}
