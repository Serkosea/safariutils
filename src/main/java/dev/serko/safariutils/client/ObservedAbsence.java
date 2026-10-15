package dev.serko.safariutils.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Client-thread evidence window; time without observations never proves absence. */
final class ObservedAbsence<K> {
	private record Window(long first, long latest) { }
	private final Map<K, Window> windows = new HashMap<>();
	private final long duration;
	private final long maximumGap;

	ObservedAbsence(long duration, long maximumGap) {
		if (duration <= 0 || maximumGap <= 0) throw new IllegalArgumentException();
		this.duration = duration;
		this.maximumGap = maximumGap;
	}

	boolean observe(K key, long now) {
		Window previous = windows.get(key);
		long gap = previous == null ? -1 : now - previous.latest();
		long first = previous == null || gap < 0 || gap > maximumGap ? now : previous.first();
		windows.put(key, new Window(first, now));
		return now - first >= duration;
	}

	void remove(K key) { windows.remove(key); }
	void retain(Set<K> keys) { windows.keySet().retainAll(keys); }
	void clear() { windows.clear(); }
}
