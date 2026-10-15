package dev.serko.safariutils.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-thread ownership index. Attempt IDs increase monotonically within a cleared visit. */
final class CaptureOwnership {
	record Claim(boolean accepted, Long displaced) { }
	private final Map<UUID, Long> owners = new HashMap<>();
	private final Map<Long, Set<UUID>> aliases = new HashMap<>();

	Long owner(UUID body) { return owners.get(body); }
	boolean containsKey(UUID body) { return owners.containsKey(body); }
	Set<UUID> bodies(long attempt) {
		Set<UUID> bodies = aliases.get(attempt);
		return bodies == null ? Set.of() : Collections.unmodifiableSet(bodies);
	}
	Map<UUID, Long> entries() { return Collections.unmodifiableMap(owners); }

	/** A failed correction leaves the original aliases intact. */
	Claim claim(long attempt, UUID body, boolean replaceAliases) {
		if (attempt <= 0 || body == null) throw new IllegalArgumentException("Invalid capture owner");
		Long previous = owners.get(body);
		if (previous != null && previous > attempt) return new Claim(false, null);
		Long displaced = previous != null && previous != attempt ? previous : null;
		if (replaceAliases) release(attempt);
		if (displaced != null) release(displaced);
		owners.put(body, attempt);
		aliases.computeIfAbsent(attempt, ignored -> new HashSet<>()).add(body);
		return new Claim(true, displaced);
	}

	void release(long attempt) {
		Set<UUID> bodies = aliases.remove(attempt);
		if (bodies != null) for (UUID body : bodies) owners.remove(body, attempt);
	}

	void clear() {
		owners.clear();
		aliases.clear();
	}
}
