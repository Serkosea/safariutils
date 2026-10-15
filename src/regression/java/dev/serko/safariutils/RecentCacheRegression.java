package dev.serko.safariutils;

import dev.serko.safariutils.state.RecentCache;

final class RecentCacheRegression {
	static int run() {
		int checks = 0;
		var cache = new RecentCache<String, Integer>(2);
		cache.put("a", 1, 1);
		cache.put("b", 2, 2);
		cache.put("a", 3, 3);
		check(cache.get("a", 3, 10) == 3, "replacement publishes newest value"); checks++;
		cache.put("c", 4, 4);
		check(cache.get("b", 4, 10) == null && cache.size() == 2, "evicts least recently published"); checks++;
		check(cache.get("a", 12, 10) == 3, "alive immediately before expiry"); checks++;
		check(cache.get("a", 13, 10) == null && cache.get("c", 13, 10) == 4, "exact expiry and next oldest survive"); checks++;
		check(cache.get("c", 14, 10) == null && cache.size() == 0, "expired values fully removed"); checks++;
		cache.put("a", 1, 20);
		check(cache.get("a", 22, 2) == null, "shorter configured window takes effect immediately"); checks++;
		cache.put("a", 1, 30);
		check(cache.get("a", 41, 20) == 1, "longer window applies to retained record"); checks++;
		for (int i = 42; i < 10_042; i++) {
			cache.put("entry-" + i, i, i);
			check(cache.size() == 2, "burst remains bounded without an auxiliary expiry queue"); checks++;
		}
		cache.clear();
		check(cache.size() == 0, "clear releases retained entries"); checks++;
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
