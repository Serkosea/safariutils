package dev.serko.safariutils.state;

import dev.serko.safariutils.data.SafariBiome;
import java.util.*;

/** Mutuals 3.6.4 allocation: party-chat arrival order wins hotspots; UUID hash orders duplicates. */
public final class HotspotAllocation {
    private final LinkedHashMap<String, SafariBiome> reports = new LinkedHashMap<>();
    private String self;
    private SafariBiome actual, finished;
    private long started;
    public void begin(String name, SafariBiome hotspot, long now) {
        if (self != null) return;
        self = name.toLowerCase(Locale.ROOT); actual = hotspot; started = now;
    }
    public void report(String name, SafariBiome biome, long now) {
        if (biome == null || finished != null || self != null && now - started > 10_000) return;
        String key = name.toLowerCase(Locale.ROOT);
        if (reports.size() < 4 || reports.containsKey(key)) reports.putIfAbsent(key, biome);
    }
    public SafariBiome assignment(Set<String> members, Map<String, String> uuids, int expected, long now) {
        if (self == null) return null;
        if (finished != null) return finished;
        if (expected <= 1) return finished = actual;
        Set<String> names = new HashSet<>();
        for (String name : members) names.add(name.toLowerCase(Locale.ROOT));
        names.add(self);
        boolean complete = names.size() >= expected && reports.keySet().containsAll(names);
        if (!complete && now - started < 3500) return null;
        Map<String, SafariBiome> known = new LinkedHashMap<>();
        reports.forEach((name, biome) -> { if (names.contains(name)) known.put(name, biome); });
        known.putIfAbsent(self, actual);
        SafariBiome result = allocate(known, uuids).getOrDefault(self, actual);
        if (complete || now - started >= 10_000) finished = result;
        return result;
    }
    public boolean finished() { return finished != null; }
    public static Map<String, SafariBiome> allocate(Map<String, SafariBiome> reports, Map<String, String> uuids) {
        Map<String, SafariBiome> result = new LinkedHashMap<>();
        Set<SafariBiome> used = EnumSet.noneOf(SafariBiome.class);
        List<String> duplicates = new ArrayList<>();
        reports.forEach((name, biome) -> {
            if (used.add(biome)) result.put(name, biome); else duplicates.add(name);
        });
        duplicates.sort(Comparator.comparingInt((String name) -> identity(name, uuids).hashCode())
            .thenComparing(name -> identity(name, uuids)));
        int next = 0;
        for (SafariBiome biome : SafariBiome.values()) {
            if (!used.contains(biome) && next < duplicates.size()) result.put(duplicates.get(next++), biome);
        }
        return Map.copyOf(result);
    }
    private static String identity(String name, Map<String, String> uuids) {
        String key = name.toLowerCase(Locale.ROOT);
        String uuid = uuids.get(key);
        return uuid == null || uuid.isBlank() ? key : uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }
}
