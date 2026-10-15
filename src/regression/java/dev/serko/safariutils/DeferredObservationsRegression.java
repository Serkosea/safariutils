package dev.serko.safariutils;

import dev.serko.safariutils.state.DeferredObservations;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class DeferredObservationsRegression {
	static int run() {
		AtomicLong now = new AtomicLong();
		var inbox = new DeferredObservations<String>(2, 10, now::get);
		check(inbox.offer("a") && inbox.offer("b"), "initial offers");
		now.set(9);
		check(!inbox.offer("a"), "duplicates cannot restart lifetime");
		inbox.drain(value -> false);
		check(inbox.size() == 2, "missing context retains observations");
		now.set(10);
		List<String> seen = new ArrayList<>();
		inbox.drain(value -> { seen.add(value); return true; });
		check(seen.isEmpty() && inbox.size() == 0, "expiry occurs before delivery at boundary");
		inbox.offer("a"); inbox.offer("b"); inbox.offer("c");
		inbox.drain(value -> { seen.add(value); return true; });
		check(seen.equals(List.of("b", "c")), "capacity evicts oldest and retains order");
		inbox.offer("a"); inbox.offer("b");
		inbox.drain(value -> { inbox.clear(); return false; });
		check(inbox.size() == 0, "callback reset cannot resurrect old observations or fail iteration");
		inbox.offer("a");
		try { inbox.drain(value -> { throw new IllegalStateException("fixture"); }); }
		catch (IllegalStateException expected) { }
		check(inbox.size() == 1, "handler failure preserves retry within original lifetime");
		inbox.clear();
		now.set(Long.MAX_VALUE - 5);
		inbox.offer("wrap");
		now.addAndGet(10);
		inbox.drain(value -> { throw new AssertionError("expired value delivered"); });
		check(inbox.size() == 0, "monotonic wrap expiration");
		for (int i = 0; i < 10000; i++) inbox.offer("item" + i);
		check(inbox.size() == 2, "burst remains bounded");
		return 9;
	}
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
