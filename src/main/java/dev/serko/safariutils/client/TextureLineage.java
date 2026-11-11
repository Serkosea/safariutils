package dev.serko.safariutils.client;

import java.util.*;

/** Retains identity across an observed texture collapse and an unambiguous nearby breakout. */
final class TextureLineage {
    private record Pending(CritterScan.Sample sample, UUID root, long expires) { }
    private final Map<UUID, UUID> roots = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private Set<UUID> visible = Set.of();

    void clear() { roots.clear(); pending.clear(); visible = Set.of(); }

    void observe(List<CritterScan.Sample> samples, List<CritterScan.Sample> hidden, long now) {
        var skins = samples.stream().filter(s -> s.textureSpecies() != null && s.type().equals("item_display")).toList();
        Set<UUID> loaded = new HashSet<>(), current = new HashSet<>();
        for (var skin : skins) { loaded.add(skin.id()); current.add(skin.id()); }
        for (var skin : hidden) {
            loaded.add(skin.id());
            // A hidden entity first seen on arrival is not proof of a local capture transition.
            if (visible.contains(skin.id())) {
                UUID root = roots.getOrDefault(skin.id(), skin.id());
                pending.put(root, new Pending(skin, root, now + 10_000));
            }
        }
        pending.values().removeIf(p -> p.expires <= now || current.contains(p.sample.id()));
        var newcomers = skins.stream().filter(s -> !roots.containsKey(s.id())).toList();
        Map<UUID, UUID> transfers = new HashMap<>();
        for (var skin : newcomers) {
            var candidates = pending.values().stream().filter(p -> near(skin, p.sample)
                && skins.stream().noneMatch(live -> p.root.equals(roots.get(live.id())))).toList();
            if (candidates.size() != 1) continue;
            var candidate = candidates.getFirst();
            // Decide over the complete batch, never let iteration order select a peer.
            if (newcomers.stream().filter(other -> near(other, candidate.sample)).count() == 1)
                transfers.put(skin.id(), candidate.root);
        }
        for (var skin : skins) roots.putIfAbsent(skin.id(), transfers.getOrDefault(skin.id(), skin.id()));
        for (UUID root : transfers.values()) pending.remove(root);
        roots.keySet().retainAll(loaded);
        visible = current;
    }

    void enrich(List<CritterScan.Match> matches) {
        for (int i = 0; i < matches.size(); i++) {
            var match = matches.get(i);
            Set<UUID> aliases = new HashSet<>(match.textureIds());
            for (UUID id : match.textureIds()) {
                UUID root = roots.get(id);
                if (root != null) aliases.add(root);
            }
            if (!aliases.equals(match.textureIds())) matches.set(i, new CritterScan.Match(match.critter(),
                match.label(), match.body(), match.sparkling(), match.conflict(), Set.copyOf(aliases)));
        }
    }

    private static boolean near(CritterScan.Sample left, CritterScan.Sample right) {
        double x = left.x() - right.x(), z = left.z() - right.z();
        return left.textureSpecies() == right.textureSpecies() && left.textureSparkling() == right.textureSparkling()
            && x*x + z*z <= .5625 && Math.abs(left.y() - right.y()) <= .75;
    }
}
