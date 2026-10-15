package dev.serko.safariutils.client;

public final class TickCacheRegression {
	public static int run() {
		var cache = new TickCache<String>();
		Object world = new String("world"), otherWorld = new String("world");
		check(cache.get(world, 1, 1, () -> "first").equals("first"), "initial build");
		check(cache.get(world, 1, 1, () -> { throw new AssertionError("unnecessary rebuild"); }).equals("first"), "same context reused");
		try {
			cache.get(world, 2, 1, () -> { throw new IllegalStateException("failed build"); });
			throw new AssertionError("failure swallowed");
		} catch (IllegalStateException expected) { }
		check(cache.get(world, 2, 1, () -> "retry").equals("retry"), "failed build cannot cache stale data under new tick");
		check(cache.get(world, 2, 2, () -> "settings").equals("settings"), "config change rebuilds");
		check(cache.get(otherWorld, 2, 2, () -> "new-world").equals("new-world"), "level uses identity not equality");
		cache.invalidate();
		check(cache.get(otherWorld, 2, 2, () -> "invalidated").equals("invalidated"), "explicit invalidation rebuilds same tick");
		cache.invalidate();
		check(cache.get(world, 3, 2, () -> { cache.invalidate(); return "temporary"; }).equals("temporary"), "current consumer receives completed result");
		check(cache.get(world, 3, 2, () -> "rebuilt").equals("rebuilt"), "invalidation during build is not lost");
		cache.invalidate();
		check(cache.get(null, 0, 0, () -> null) == null, "null is a valid cached result");
		check(cache.get(null, 0, 0, () -> { throw new AssertionError("null recomputed"); }) == null, "null result reused");
		return 10;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
