package dev.serko.safariutils.client;

import java.util.Set;

/** Headless fixtures for the production artifact-window registry. */
public final class CaptureTransitionsRegression {
	private static int checks;
	public static int run() {
		checks = 0;
		var windows = new Fixture();
		windows.start(1, "bat", 0);
		windows.start(2, "bat", 1000);
		check(windows.resolve(2, 2000), "second attempt can resolve first");
		check(windows.active("bat", 9000), "resolving second does not shorten first");
		check(!windows.resolve(2, 6000), "duplicate result cannot extend grace");
		check(windows.prune(7001) && windows.size() == 1, "resolved second expires independently");
		check(windows.active("bat", 10000) && !windows.active("bat", 10001), "inclusive pending boundary retained");
		check(windows.prune(10001) && windows.size() == 0, "all expired windows removed");
		check(!windows.resolve(2, 10002) && windows.size() == 0, "late result cannot resurrect window");
		windows.start(3, "bat", 20000);
		windows.start(4, "bat", 20001);
		windows.resolve(3, 20002);
		check(windows.active("bat", 29000), "old cleanup cannot shorten immediate retry");
		windows.start(4, "bat", 29000);
		check(!windows.active("bat", 30002), "repeated start cannot reset timer");
		windows.start(5, "gazer", 40000);
		windows.start(6, "bat", 40000);
		check(windows.activeSpecies(40000).equals(Set.of("gazer", "bat")), "render projection aggregates only active species");
		check(!windows.resolve(999, 40001), "unknown cleanup cannot consume any species window");
		windows.resolve(5, 49999);
		windows.prune(50001);
		check(windows.active("gazer", 54000) && !windows.active("bat", 54000), "out-of-order expiry does not retain unrelated windows");
		windows.clear();
		check(windows.size() == 0 && windows.activeSpecies(54000).isEmpty(), "visit reset clears all windows");
		windows.start(7, "bat", 60000); // event may remain unbound to a body
		check(windows.active("bat", 65000), "deferred attempt has protection before body binding");
		check(windows.resolve(7, 70001) && !windows.active("bat", 70001), "expired unpruned window cannot gain fresh grace");
		var clock = new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE - 10_000_000L);
		var wrapped = new CaptureTransitions<String>(20, 5, clock::get);
		wrapped.start(1, "bat");
		clock.addAndGet(20_000_000L);
		check(wrapped.active("bat"), "monotonic wrap preserves inclusive pending boundary");
		check(wrapped.resolve(1), "resolution across nanoTime wrap retains ownership");
		clock.addAndGet(5_000_000L);
		check(wrapped.active("bat"), "resolved window retains exact inclusive boundary");
		clock.incrementAndGet();
		check(!wrapped.active("bat") && wrapped.prune(), "one nanosecond past grace expires");
		return checks;
	}

	/** Existing scenario times are milliseconds; the production registry receives only its clock. */
	private static final class Fixture {
		private final java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
		private final CaptureTransitions<String> windows = new CaptureTransitions<>(10000, 5000, clock::get);
		private void at(long millis) { clock.set(millis * 1_000_000L); }
		void start(long id, String species, long time) { at(time); windows.start(id, species); }
		boolean resolve(long id, long time) { at(time); return windows.resolve(id); }
		boolean prune(long time) { at(time); return windows.prune(); }
		boolean active(String species, long time) { at(time); return windows.active(species); }
		Set<String> activeSpecies(long time) { at(time); return windows.activeSpecies(); }
		int size() { return windows.size(); }
		void clear() { windows.clear(); }
	}
	private static void check(boolean value, String reason) {
		if (!value) throw new AssertionError(reason);
		checks++;
	}
}
