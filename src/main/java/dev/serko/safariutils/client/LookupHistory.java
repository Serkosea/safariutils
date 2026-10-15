package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SparklingPlayerLookup;
import java.util.ArrayList;
import java.util.List;

/** Client-thread presentation history; freshness belongs to the source response, not its arrival. */
final class LookupHistory {
	private final int capacity;
	private List<SparklingPlayerLookup> entries = List.of();

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
		return result;
	}

	static boolean fresh(SparklingPlayerLookup result, long now, long lifetime) {
		if (result == null || result.fetchedAt() <= 0 || lifetime <= 0) return false;
		long age = now - result.fetchedAt();
		return age >= 0 && age < lifetime;
	}
}
