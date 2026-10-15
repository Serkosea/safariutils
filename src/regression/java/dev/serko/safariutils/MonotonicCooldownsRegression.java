package dev.serko.safariutils;

import dev.serko.safariutils.state.MonotonicCooldowns;
import java.util.concurrent.atomic.AtomicLong;

final class MonotonicCooldownsRegression {
	static int run() {
		var now = new AtomicLong(-5_000_000_000L);
		var cooldowns = new MonotonicCooldowns<String>(now::get);
		check(cooldowns.remainingMillis("chat", 1200) == 0, "unmarked cooldown is ready even for negative nanoTime");
		cooldowns.mark("chat");
		check(cooldowns.remainingMillis("chat", 1200) == 1200, "full duration at send");
		now.addAndGet(1_199_999_999L);
		check(cooldowns.remainingMillis("chat", 1200) == 1, "fractional millisecond cannot send early");
		now.incrementAndGet();
		check(cooldowns.remainingMillis("chat", 1200) == 0, "exact boundary is ready");
		check(cooldowns.remainingMillis("party", 3100) == 0, "cooldown categories independent");
		cooldowns.mark("chat");
		cooldowns.clear();
		check(cooldowns.remainingMillis("chat", 1200) == 0, "connection reset releases cooldowns");
		now.set(Long.MAX_VALUE - 500_000);
		cooldowns.mark("wrap");
		now.addAndGet(1_000_000);
		check(cooldowns.remainingMillis("wrap", 2) == 1, "nanoTime wrap preserves elapsed duration");
		check(cooldowns.remainingMillis("wrap", 0) == 0, "disabled category is always ready");
		now.set(100);
		cooldowns.mark("regression");
		now.set(90);
		check(cooldowns.remainingMillis("regression", 1) == 1, "invalid backwards clock does not grant early send");
		return 9;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
