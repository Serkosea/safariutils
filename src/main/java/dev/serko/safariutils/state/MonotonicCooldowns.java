package dev.serko.safariutils.state;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Owner-thread cooldown ledger; wall-clock adjustments cannot expire or extend a cooldown. */
public final class MonotonicCooldowns<K> {
	private final Map<K, Long> started = new HashMap<>();
	private final LongSupplier clock;

	public MonotonicCooldowns(LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }

	public long remainingMillis(K key, long durationMillis) {
		if (durationMillis <= 0) return 0;
		Long start = started.get(key);
		if (start == null) return 0;
		long duration = TimeUnit.MILLISECONDS.toNanos(durationMillis);
		long elapsed = clock.getAsLong() - start;
		if (elapsed >= duration) return 0;
		long remaining = duration - Math.max(0, elapsed);
		return remaining / 1_000_000L + (remaining % 1_000_000L == 0 ? 0 : 1);
	}

	public void mark(K key) { started.put(key, clock.getAsLong()); }
	public void clear() { started.clear(); }
}
