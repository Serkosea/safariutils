package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import java.util.*;

/** Visit-local inference from observed triplets. Missing entities are never negative evidence. */
final class FloorDropPattern {
    enum Kind {
        UNKNOWN("Floor Drop", 0, null, -1), LOOT("Loot", 0, null, -1),
        LIME("Lime Gem", 0x9DFF00, SafariBiome.CAVERN, 9),
        PURPLE("Purple Gem", 0x9D39DB, SafariBiome.CAVERN, 14),
        ORANGE("Orange Gem", 0xFF5B24, SafariBiome.CAVERN, 15),
        WORM("Wriggleworm", 0xFF94C7, SafariBiome.FOREST, 11),
        BERRY("Yogi Berry", 0x3964DB, SafariBiome.FOREST, 16),
        SEEDS("Bag of Seeds", 0xD6B596, SafariBiome.FOREST, 17),
        INCENSE("Soothing Incense", 0x37D3DB, SafariBiome.HAUNTED, 12),
        COIN("Shining Coin", 0xFFEF63, SafariBiome.HAUNTED, 13),
        ICE("Icebreaker", 0xFF422E, SafariBiome.ICY, 10);
        final String title;
        final int colour;
        final int faceColour;
        final SafariBiome biome;
        final int alwaysBit;
        Kind(String title, int colour, SafariBiome biome, int alwaysBit) {
            this.title = title; this.colour = colour; this.biome = biome; this.alwaysBit = alwaysBit;
            this.faceColour = Colours.softFaceRgb(colour);
        }
        static Kind reward(String message) {
            for (Kind kind : values()) if (kind.biome != null && message.contains(kind.title)) return kind;
            if (message.contains("Hunting") || message.contains("Safari Essence") || message.contains("Shard")) return LOOT;
            return UNKNOWN;
        }
    }
    private record Candidate(int base, int coins, int ice) {
        Kind kind(int id) {
            long delta = (long) id - base;
            if (delta < 0) return Kind.UNKNOWN;
            if (delta >= 3L * (20 + coins + ice)) return Kind.LOOT;
            if (delta % 3 != 0) return Kind.UNKNOWN;
            int ordinal = (int) (delta / 3);
            if (ordinal < 6) return switch (ordinal % 3) { case 0 -> Kind.LIME; case 1 -> Kind.PURPLE; default -> Kind.ORANGE; };
            if (ordinal < 15) return switch ((ordinal - 6) / 3) { case 0 -> Kind.WORM; case 1 -> Kind.BERRY; default -> Kind.SEEDS; };
            if (ordinal < 20) return Kind.INCENSE;
            return ordinal < 20 + coins ? Kind.COIN : Kind.ICE;
        }
    }
    private final NavigableMap<Integer, SafariBiome> observed = new TreeMap<>();
    private final Map<Integer, Kind> rewards = new HashMap<>();
    private List<Candidate> candidates = List.of();
    private boolean dirty, invalid;

    void observe(int id, SafariBiome biome) {
        if (biome == null || invalid) return;
        if (observed.size() >= 512 && !observed.containsKey(id)) { invalid = true; return; }
        SafariBiome old = observed.putIfAbsent(id, biome);
        if (old != null && old != biome) invalid = true;
        if (old == null) dirty = true;
    }
    void reward(int id, Kind kind) {
        if (kind == Kind.UNKNOWN || !observed.containsKey(id)) return;
        Kind old = rewards.putIfAbsent(id, kind);
        if (old != null && old != kind) invalid = true;
        dirty = true;
    }
    Kind kind(int id) {
        refresh();
        if (invalid || candidates.isEmpty() || !observed.containsKey(id)) return Kind.UNKNOWN;
        Kind result = candidates.getFirst().kind(id);
        for (Candidate candidate : candidates) if (candidate.kind(id) != result) return Kind.UNKNOWN;
        return result;
    }
    int candidateCount() { refresh(); return invalid ? 0 : candidates.size(); }
    private void refresh() {
        if (!dirty || invalid) return;
        dirty = false;
        candidates = List.of();
        if (observed.isEmpty()) return;
        int base = observed.firstKey();
        for (int ordinal = 0; ordinal < 20; ordinal++) {
            SafariBiome expected = ordinal < 6 ? SafariBiome.CAVERN : ordinal < 15 ? SafariBiome.FOREST : SafariBiome.HAUNTED;
            if (observed.get(base + ordinal * 3) != expected) return;
        }
        List<Candidate> valid = new ArrayList<>();
        for (int coins = 3; coins <= 6; coins++) for (int ice = 4; ice <= 5; ice++) {
            Candidate candidate = new Candidate(base, coins, ice);
            boolean fits = true;
            for (var entry : observed.entrySet()) {
                Kind kind = candidate.kind(entry.getKey());
                if (kind == Kind.UNKNOWN || kind.biome != null && kind.biome != entry.getValue()
                        || rewards.containsKey(entry.getKey()) && rewards.get(entry.getKey()) != kind) { fits = false; break; }
            }
            if (fits) valid.add(candidate);
        }
        // Five is the observed spawn rule. Keep four only as an evidence-driven
        // fallback: a confirmed non-ice reward at the boundary can overturn it.
        if (valid.stream().anyMatch(candidate -> candidate.ice() == 5))
            valid.removeIf(candidate -> candidate.ice() != 5);
        candidates = List.copyOf(valid);
    }
    void reset() { observed.clear(); rewards.clear(); candidates = List.of(); dirty = false; invalid = false; }
}
