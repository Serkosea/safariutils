package dev.serko.safariutils;

import dev.serko.safariutils.state.MonotonicTime;
import java.util.concurrent.atomic.AtomicLong;

public final class MonotonicTimeRegression {
	public static int run() {
		int checks = 0;
		for (long origin : new long[] {0, -5_000_000_000L, Long.MAX_VALUE - 500_000L}) {
			var nanos = new AtomicLong(origin);
			var clock = new MonotonicTime(nanos::get);
			check(clock.millis() == 1, "zero remains reserved"); checks++;
			for (long elapsed : new long[] {1, 999_999, 1_000_000, 550_000_000,
				1_500_000_000L, 40_000_000_000L, 86_400_000_000_000L}) {
				nanos.set(origin + elapsed);
				check(clock.millis() == 1 + elapsed / 1_000_000, "elapsed time including nano rollover");
				checks++;
			}
		}
		return checks;
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
