package dev.serko.safariutils.state;

import java.util.LinkedHashMap;

/** Owner-thread cache ordered by last publication; transient timestamps must be monotonic. */
public final class RecentCache<K, V> {
	private record Entry<V>(V value, long seenAt) { }
	private final int capacity;
	private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>();

	public RecentCache(int capacity) {
		if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
		this.capacity = capacity;
	}

	public V get(K key, long now, long window) {
		if (window <= 0) throw new IllegalArgumentException("Window must be positive");
		var iterator = entries.values().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().seenAt() < window) break;
			iterator.remove();
		}
		Entry<V> found = entries.get(key);
		return found == null ? null : found.value();
	}

	public void put(K key, V value, long now) {
		entries.remove(key);
		entries.put(key, new Entry<>(value, now));
		while (entries.size() > capacity) entries.pollFirstEntry();
	}

	public int size() { return entries.size(); }
	public void clear() { entries.clear(); }
}
