package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SparklingPlayerLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Client-thread history: source ordering and successful refresh times are separate. */
final class LookupHistory {
	private final int capacity;
	private List<SparklingPlayerLookup> entries = List.of();
	private final Map<String, Long> checkedAt = new HashMap<>();

	LookupHistory(int capacity) {
		if (capacity < 1) throw new IllegalArgumentException("capacity");
		this.capacity = capacity;
	}

	List<SparklingPlayerLookup> entries() { return entries; }

	SparklingPlayerLookup remember(SparklingPlayerLookup result) {
		for (var saved : entries) {
			if (saved.username().equalsIgnoreCase(result.username())
					&& saved.fetchedAt() >= result.fetchedAt()) return saved;
		}
		var next = new ArrayList<SparklingPlayerLookup>(capacity);
		next.add(result);
		for (var saved : entries) {
			if (next.size() < capacity && !saved.username().equalsIgnoreCase(result.username())) next.add(saved);
		}
		entries = List.copyOf(next);
		checkedAt.keySet().removeIf(name -> entries.stream()
			.noneMatch(saved -> key(saved).equals(name)));
		return result;
	}

	/** Only a completed manual request acknowledges Refresh, not selecting a recent entry. */
	SparklingPlayerLookup rememberChecked(SparklingPlayerLookup result, long now) {
		SparklingPlayerLookup accepted = remember(result);
		checkedAt.put(key(accepted), now);
		return accepted;
	}

	boolean recentlyChecked(SparklingPlayerLookup result, long now, long lifetime) {
		Long checked = result == null ? null : checkedAt.get(key(result));
		return checked != null && lifetime > 0 && now >= checked && now - checked < lifetime;
	}

	private static String key(SparklingPlayerLookup result) {
		return result.username().toLowerCase(Locale.ROOT);
	}

	static boolean fresh(SparklingPlayerLookup result, long now, long lifetime) {
		if (result == null || result.fetchedAt() <= 0 || lifetime <= 0) return false;
		long age = now - result.fetchedAt();
		return age >= 0 && age < lifetime;
	}
}
