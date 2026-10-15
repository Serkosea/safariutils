package dev.serko.safariutils.backend;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerCollectionsRegression {
	public static int run() {
		var cache = new PlayerCollections(3);
		int checks = 0;
		checks += check(cache.shared(List.of("a")) == null, "missing is not empty");
		var source = new LinkedHashSet<>(Set.of("BAT"));
		cache.put("a", source);
		source.add("FROG");
		checks += check(cache.get("a").equals(Set.of("BAT")), "snapshot isolates caller mutation");
		cache.put("b", Set.of("FROG"));
		checks += check(cache.shared(List.of("a", "b")).isEmpty(), "known empty intersection");
		checks += check(!cache.recordPartyCatch(List.of("a", "b"), Set.of("a"), "BIRD"), "absent member is not credited");
		checks += check(cache.get("a").contains("BIRD"), "present catch retained even if not shared");
		checks += check(cache.recordPartyCatch(List.of("a", "b"), Set.of("b"), "BIRD"), "absent member already owns species");
		checks += check(cache.shared(List.of("a", "b")).equals(Set.of("BIRD")), "all compositions derive fresh intersections");
		cache.put("b", Set.of("BAT"));
		checks += check(cache.shared(List.of("a", "b")).equals(Set.of("BAT")), "lookup replaces derived party collection");
		cache.recordPartyCatch(List.of("unknown"), Set.of("unknown"), "BAT");
		checks += check(cache.get("unknown") == null, "partial observation cannot invent full snapshot");
		Map<String, Set<String>> invalid = new LinkedHashMap<>();
		invalid.put("a", Set.of("CHANGED"));
		invalid.put("broken", null);
		try {
			cache.putAll(invalid, cache.checkpoint());
			throw new AssertionError("malformed batch accepted");
		} catch (NullPointerException expected) { }
		checks += check(cache.get("a").contains("BAT"), "malformed batch publishes nothing");
		cache.put("c", Set.of());
		cache.get("a");
		cache.put("d", Set.of());
		checks += check(cache.get("b") == null && cache.get("a") != null, "bounded least-recently-used eviction");
		checks += check(cache.shared(List.of("a", "b")) == null, "evicted member requires load not stale party data");
		checks += check(cache.shared(List.of()) == null, "no party is not a loaded collection");
		long requested = cache.checkpoint();
		cache.recordPartyCatch(List.of("a"), Set.of("a"), "NEW");
		cache.putAll(Map.of("a", Set.of("BAT")), requested);
		checks += check(cache.get("a").contains("NEW"), "slow batch preserves intervening catch");
		cache.put("a", Set.of("BAT"), requested);
		checks += check(cache.get("a").contains("NEW"), "slow manual lookup preserves intervening catch");
		cache.put("a", Set.of("OTHER_PROFILE"), cache.checkpoint());
		checks += check(cache.get("a").equals(Set.of("OTHER_PROFILE")), "fresh later snapshot may replace old observations");
		var loading = new PlayerCollections(3);
		long initialRequest = loading.checkpoint();
		loading.recordPartyCatch(List.of("first", "absent"), Set.of("first"), "BAT");
		checks += check(!loading.containsAll(List.of("first")) && loading.shared(List.of("first")) == null,
			"first-load evidence is not a complete collection");
		loading.putAll(Map.of("first", Set.of("FROG"), "absent", Set.of()), initialRequest);
		checks += check(loading.get("first").equals(Set.of("BAT", "FROG")),
			"first API result retains a catch observed while loading");
		checks += check(loading.get("absent").isEmpty(), "absent unknown player receives no catch evidence");
		loading.put("first", Set.of(), loading.checkpoint());
		checks += check(loading.get("first").isEmpty(), "later independent snapshot can replace first-load evidence");
		var bounded = new PlayerCollections(3);
		long beforeBurst = bounded.checkpoint();
		for (int i = 0; i < 1000; i++) {
			String uuid = "unknown-" + i;
			bounded.recordPartyCatch(List.of(uuid), Set.of(uuid), "BAT");
		}
		bounded.put("unknown-0", Set.of(), beforeBurst);
		bounded.put("unknown-999", Set.of(), beforeBurst);
		checks += check(bounded.get("unknown-0").isEmpty(), "unknown evidence has a hard bound");
		checks += check(bounded.get("unknown-999").contains("BAT"), "recent unknown evidence survives bounded eviction");
		return checks;
	}

	private static int check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
		return 1;
	}
}
