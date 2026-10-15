package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Ordered, one-body-per-label scan assignment; adapters own species/distance eligibility. */
final class ScanPairing {
	record Rank(int priority, double distanceSq) implements Comparable<Rank> {
		@Override public int compareTo(Rank other) {
			int compared = Integer.compare(priority, other.priority);
			return compared != 0 ? compared : Double.compare(distanceSq, other.distanceSq);
		}
	}
	private record Ranked<L>(int index, L label, Rank rank) { }
	record Pair<L, B>(L label, B body, boolean ownershipConflict) { }

	static <L, B, I> List<Pair<L, B>> assign(List<L> labels, Function<L, Rank> rank,
			BiFunction<L, Set<I>, B> resolve, Function<B, I> identity) {
		List<Ranked<L>> order = new ArrayList<>(labels.size());
		List<Pair<L, B>> result = new ArrayList<>(Collections.nCopies(labels.size(), null));
		for (int i = 0; i < labels.size(); i++) order.add(new Ranked<>(i, labels.get(i), rank.apply(labels.get(i))));
		// Stable sort retains scan order for equally ranked candidates.
		order.sort(Comparator.comparing(Ranked::rank));
		Set<I> claimed = new HashSet<>();
		Set<I> claimedView = Collections.unmodifiableSet(claimed);
		for (Ranked<L> item : order) {
			B body = resolve.apply(item.label(), claimedView);
			boolean conflict = body != null && !claimed.add(identity.apply(body));
			// A faulty adapter must not steal another label's body or discard the entire scan.
			result.set(item.index(), new Pair<>(item.label(), conflict ? null : body, conflict));
		}
		return List.copyOf(result);
	}
	private ScanPairing() { }
}
