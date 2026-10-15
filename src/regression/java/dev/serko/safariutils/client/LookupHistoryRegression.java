package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SparklingPlayerLookup;
import java.util.Map;
import java.util.Set;

public final class LookupHistoryRegression {
	public static int run() {
		var history = new LookupHistory(2);
		var first = value("Alice", 100);
		check(history.remember(first) == first, "first response retained");
		var snapshot = history.entries();
		check(history.remember(value("ALICE", 99)) == first, "late older response cannot replace newer data");
		check(history.remember(value("alice", 100)) == first, "same cached response cannot refresh arrival age");
		var updated = value("Alice", 200);
		check(history.remember(updated) == updated && history.entries().size() == 1, "new response replaces same normalized player");
		check(snapshot.getFirst() == first, "published history snapshot remains immutable");
		history.remember(value("Bob", 201));
		history.remember(value("Carol", 202));
		check(history.entries().size() == 2 && history.entries().getLast().username().equals("Bob"), "history bounded to newest entries");
		check(LookupHistory.fresh(updated, 250, 100), "fresh source response");
		check(!LookupHistory.fresh(updated, 300, 100), "exact expiry refreshable");
		check(!LookupHistory.fresh(updated, 199, 100), "clock rollback does not indefinitely mask stale data");
		check(!LookupHistory.fresh(value("Unknown", 0), 50, 100), "missing source timestamp is not fresh");
		return 10;
	}
	private static SparklingPlayerLookup value(String name, long time) {
		return new SparklingPlayerLookup(name, Set.of(), 0, Map.of(), time);
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
