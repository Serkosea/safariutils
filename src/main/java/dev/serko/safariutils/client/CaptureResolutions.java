package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Exact attempt ownership for remembered-body projections; no species-order attribution. */
final class CaptureResolutions<S, P> {
	record Resolution<S, P>(long attemptId, S species, UUID id, P pos, long millis) { }
	private final Map<Long, Resolution<S, P>> attempts = new LinkedHashMap<>();

	boolean start(long id, S species, UUID body, P position, long now) {
		if (id <= 0 || species == null) throw new IllegalArgumentException();
		return attempts.putIfAbsent(id, new Resolution<>(id, species, body, position, now)) == null;
	}

	Resolution<S, P> get(long id, S species) {
		var entry = attempts.get(id);
		return entry != null && entry.species().equals(species) ? entry : null;
	}

	boolean correct(long id, S species, UUID oldBody, UUID newBody, P position) {
		var entry = get(id, species);
		if (entry == null || !Objects.equals(entry.id(), oldBody)) return false;
		attempts.put(id, new Resolution<>(id, species, newBody, position, entry.millis()));
		return true;
	}

	Resolution<S, P> remove(long id, S species) {
		var entry = get(id, species);
		if (entry != null) attempts.remove(id);
		return entry;
	}

	boolean hasSpecies(S species) {
		for (var entry : attempts.values()) if (entry.species().equals(species)) return true;
		return false;
	}

	boolean hasBody(UUID body) {
		if (body == null) return false;
		for (var entry : attempts.values()) if (body.equals(entry.id())) return true;
		return false;
	}

	/** Position evidence is species-scoped and never inferred from a missing anchor. */
	boolean hasPosition(S species, P position, java.util.function.BiPredicate<P, P> samePosition) {
		if (position == null) return false;
		for (var entry : attempts.values()) {
			if (entry.species().equals(species) && entry.pos() != null
				&& samePosition.test(entry.pos(), position)) return true;
		}
		return false;
	}

	List<Resolution<S, P>> removeSpecies(S species) {
		return removeMatching(entry -> entry.species().equals(species));
	}

	List<Resolution<S, P>> expire(long now, long lifetime) {
		return removeMatching(entry -> now - entry.millis() > lifetime);
	}

	private List<Resolution<S, P>> removeMatching(java.util.function.Predicate<Resolution<S, P>> predicate) {
		List<Resolution<S, P>> removed = new ArrayList<>();
		var iterator = attempts.values().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (predicate.test(entry)) { removed.add(entry); iterator.remove(); }
		}
		return removed;
	}

	void clear() { attempts.clear(); }
}
