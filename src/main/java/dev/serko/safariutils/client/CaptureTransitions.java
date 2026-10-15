package dev.serko.safariutils.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Client-thread capture-artifact windows, owned by the originating attempt, not species order. */
final class CaptureTransitions<K> {
	private record Window<K>(K species, long started, long duration, boolean resolved) {
		boolean expired(long now) { return now - started > duration; }
	}
	private final Map<Long, Window<K>> windows = new HashMap<>();
	private final long pendingNanos;
	private final long resolvedNanos;
	private final LongSupplier clock;

	CaptureTransitions(long pendingMillis, long resolvedMillis) {
		this(pendingMillis, resolvedMillis, System::nanoTime);
	}

	CaptureTransitions(long pendingMillis, long resolvedMillis, LongSupplier clock) {
		if (pendingMillis <= 0 || resolvedMillis <= 0) throw new IllegalArgumentException();
		this.pendingNanos = Math.multiplyExact(pendingMillis, 1_000_000L);
		this.resolvedNanos = Math.multiplyExact(resolvedMillis, 1_000_000L);
		this.clock = Objects.requireNonNull(clock);
	}

	void start(long attempt, K species) {
		if (attempt <= 0 || species == null) throw new IllegalArgumentException();
		windows.putIfAbsent(attempt, new Window<>(species, clock.getAsLong(), pendingNanos, false));
	}

	boolean resolve(long attempt) {
		long now = clock.getAsLong();
		Window<K> window = windows.get(attempt);
		if (window == null || window.resolved()) return false;
		if (window.expired(now)) { windows.remove(attempt); return true; }
		windows.put(attempt, new Window<>(window.species(), now, resolvedNanos, true));
		return true;
	}

	boolean prune() {
		long now = clock.getAsLong();
		return windows.values().removeIf(window -> window.expired(now));
	}

	boolean active(K species) {
		long now = clock.getAsLong();
		for (Window<K> window : windows.values())
			if (!window.expired(now) && window.species().equals(species)) return true;
		return false;
	}

	Set<K> activeSpecies() {
		long now = clock.getAsLong();
		Set<K> result = new HashSet<>();
		for (Window<K> window : windows.values())
			if (!window.expired(now)) result.add(window.species());
		return result;
	}

	int size() { return windows.size(); }
	void clear() { windows.clear(); }
}
