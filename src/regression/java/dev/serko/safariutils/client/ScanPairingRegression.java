package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Reference assignment comparison and ownership contracts for the live scan orchestrator. */
public final class ScanPairingRegression {
	private record Label(int id, int priority, double distance, List<Integer> candidates) { }
	public static int run() {
		int checks = 0;
		var random = new Random(20261015);
		for (int scenario = 0; scenario < 1000; scenario++) {
			var labels = new ArrayList<Label>();
			for (int label = 0, count = random.nextInt(25); label < count; label++) {
				var candidates = new ArrayList<Integer>();
				for (int i = 0, n = random.nextInt(8); i < n; i++) candidates.add(random.nextInt(12));
				labels.add(new Label(label, random.nextInt(3), random.nextInt(5), List.copyOf(candidates)));
			}
			int[] rankCalls = {0}, resolveCalls = {0};
			List<ScanPairing.Pair<Label, Integer>> actual = ScanPairing.assign(labels, label -> {
				rankCalls[0]++;
				return new ScanPairing.Rank(label.priority(), label.distance());
			}, (label, claimed) -> {
				resolveCalls[0]++;
				return label.candidates().stream().filter(id -> !claimed.contains(id)).findFirst().orElse(null);
			}, value -> value);
			var order = new ArrayList<>(labels);
			order.sort(Comparator.comparingInt(Label::priority).thenComparingDouble(Label::distance));
			var taken = new HashSet<Integer>();
			var expected = new java.util.HashMap<Integer, Integer>();
			for (Label label : order) {
				for (int body : label.candidates()) {
					if (taken.add(body)) { expected.put(label.id(), body); break; }
				}
			}
			check(actual.size() == labels.size(), "unpaired labels dropped"); checks++;
			check(rankCalls[0] == labels.size() && resolveCalls[0] == labels.size(), "adapter work repeated"); checks++;
			var unique = new HashSet<Integer>();
			for (int i = 0; i < actual.size(); i++) {
				var pair = actual.get(i);
				check(pair.label().equals(labels.get(i)), "output changed source order"); checks++;
				check(java.util.Objects.equals(pair.body(), expected.get(pair.label().id())), "assignment changed rank/tie/claim behavior"); checks++;
				check(pair.body() == null || unique.add(pair.body()), "body assigned twice"); checks++;
			}
			try { actual.clear(); throw new AssertionError("mutable assignment publication"); }
			catch (UnsupportedOperationException expectedFailure) { checks++; }
		}
		var conflicts = ScanPairing.assign(List.of(1, 2), label -> new ScanPairing.Rank(0, 0),
			(label, claimed) -> 7, body -> body);
		check(conflicts.getFirst().body() == 7 && !conflicts.getFirst().ownershipConflict()
			&& conflicts.getLast().body() == null && conflicts.getLast().ownershipConflict(),
			"faulty adapter stole existing ownership or discarded valid scan results"); checks++;
		try {
			ScanPairing.assign(List.of(1), label -> new ScanPairing.Rank(0, 0), (label, claimed) -> {
				claimed.clear(); return 1;
			}, body -> body);
			throw new AssertionError("adapter modified central ownership");
		} catch (UnsupportedOperationException expected) { checks++; }
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
	private ScanPairingRegression() { }
}
