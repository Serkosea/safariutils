package dev.serko.safariutils.backend;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Bounded shared collection snapshots. Derived party intersections are never cached separately. */
final class PlayerCollections {
	private final int limit;
	private final Map<String, Set<String>> players = new LinkedHashMap<>(16, 0.75f, true);
	private final Map<String, Map<String, Long>> observations = new LinkedHashMap<>(16, 0.75f, true);
	private long revision;

	PlayerCollections(int limit) {
		if (limit < 1) throw new IllegalArgumentException("Collection cache limit must be positive");
		this.limit = limit;
	}

	synchronized Set<String> get(String uuid) {
		return players.get(uuid);
	}

	synchronized long checkpoint() {
		return revision;
	}

	synchronized void put(String uuid, Set<String> species) {
		put(uuid, species, revision);
	}

	synchronized void put(String uuid, Set<String> species, long requestedAt) {
		players.put(java.util.Objects.requireNonNull(uuid), merge(uuid, Set.copyOf(species), requestedAt));
		trim();
	}

	/** Validate/copy the entire batch before publishing any member. */
	void putAll(Map<String, Set<String>> snapshots, long requestedAt) {
		Map<String, Set<String>> prepared = new LinkedHashMap<>();
		snapshots.forEach((uuid, species) -> prepared.put(java.util.Objects.requireNonNull(uuid), Set.copyOf(species)));
		synchronized (this) {
			prepared.forEach((uuid, species) -> players.put(uuid, merge(uuid, species, requestedAt)));
			trim();
		}
	}

	synchronized boolean recordPartyCatch(Collection<String> roster, Set<String> presentPlayers, String species) {
		long observedAt = ++revision;
		boolean shared = true;
		for (String uuid : roster) {
			Set<String> previous = players.get(uuid);
			if (!presentPlayers.contains(uuid)) {
				if (previous == null || !previous.contains(species)) shared = false;
				continue;
			}
			// An observed catch cannot invent a complete collection for an unknown player.
			observations.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>()).put(species, observedAt);
			if (previous == null) continue;
			if (previous.contains(species)) continue;
			Set<String> updated = new LinkedHashSet<>(previous);
			updated.add(species);
			players.put(uuid, Set.copyOf(updated));
		}
		trim();
		return !roster.isEmpty() && shared;
	}

	/** UI readiness checks do not allocate or compute an intersection. */
	synchronized boolean containsAll(Collection<String> roster) {
		if (roster.isEmpty()) return false;
		for (String uuid : roster) if (!players.containsKey(uuid)) return false;
		return true;
	}

	/** Null means incomplete data; an empty set means a known empty intersection. */
	synchronized Set<String> shared(Collection<String> roster) {
		if (roster.isEmpty()) return null;
		Set<String> shared = null;
		for (String uuid : roster) {
			Set<String> species = players.get(uuid);
			if (species == null) return null;
			if (shared == null) shared = new LinkedHashSet<>(species);
			else shared.retainAll(species);
		}
		return Set.copyOf(shared);
	}

	private void trim() {
		while (players.size() > limit) {
			String uuid = players.keySet().iterator().next();
			players.remove(uuid);
			observations.remove(uuid);
		}
		// First-load catches need evidence even without a complete player snapshot.
		// Bound this independently so unknown players cannot grow a second unbounded cache.
		while (observations.size() > limit) observations.remove(observations.keySet().iterator().next());
	}

	private Set<String> merge(String uuid, Set<String> snapshot, long requestedAt) {
		Map<String, Long> observed = observations.get(uuid);
		if (observed == null) return snapshot;
		Set<String> merged = new LinkedHashSet<>(snapshot);
		observed.forEach((species, at) -> {
			if (at > requestedAt) merged.add(species);
		});
		return Set.copyOf(merged);
	}
}
