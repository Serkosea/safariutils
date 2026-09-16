package dev.serko.safariutils.api;

import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Everything shown by one remote-player lookup, derived from one profile response. */
public record SparklingPlayerLookup(String username, Set<String> species, int duplicates,
		Map<String, Long> tickets, long fetchedAt) {
	public SparklingPlayerLookup {
		species = Set.copyOf(species);
		tickets = Collections.unmodifiableMap(new LinkedHashMap<>(tickets));
	}
}
