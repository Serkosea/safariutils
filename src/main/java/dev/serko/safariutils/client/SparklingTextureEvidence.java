package dev.serko.safariutils.client;

import java.util.*;

/** Folds appearance evidence into the shared scan; ambiguous neighbours never acquire it. */
final class SparklingTextureEvidence {
    private SparklingTextureEvidence() { }

    static void merge(List<CritterScan.Sample> samples, List<CritterScan.Match> matches, Set<UUID> capsuleLabels) {
        List<CritterScan.Sample> captureStatuses = samples.stream()
            .filter(sample -> sample.type().equals("armor_stand") && "CAPTURING".equalsIgnoreCase(sample.name())).toList();
        Set<UUID> captureLabels = new HashSet<>(capsuleLabels);
        for (var match : matches) if (capturePart(match.label(), captureStatuses)) captureLabels.add(match.label().id());
        List<CritterScan.Sample> skins = samples.stream().filter(sample -> sample.textureSpecies() != null
            && !capturePart(sample, captureStatuses)
            && !(sample.type().equals("item_display") && samples.stream().anyMatch(label ->
                captureLabels.contains(label.id()) && sameColumn(sample, label)))).toList();
        if (skins.isEmpty()) return;
        int originalSize = matches.size();
        for (var skin : skins) {
            int exact = -1;
            for (int index = 0; index < originalSize; index++) {
                var match = matches.get(index);
                if (match.label().id().equals(skin.id()) || match.body() != null && match.body().id().equals(skin.id())) {
                    exact = index;
                    break;
                }
            }
            if (exact >= 0) {
                var match = matches.get(exact);
                // An authoritative conflicting label wins over an appearance inference.
                if (!captureLabels.contains(match.label().id()) && match.critter().equals(skin.textureSpecies())) matches.set(exact, sparkling(match, skin));
                continue;
            }
            int candidate = -1;
            boolean ambiguous = false;
            for (int index = 0; index < originalSize; index++) {
                var match = matches.get(index);
                if (captureLabels.contains(match.label().id()) || !near(skin, match)) continue;
                if (candidate >= 0) { ambiguous = true; break; }
                candidate = index;
            }
            if (candidate >= 0 && !ambiguous) {
                var match = matches.get(candidate);
                // A label equidistant from two distinct skins cannot choose an individual.
                for (var other : skins) {
                    if (!other.id().equals(skin.id()) && near(other, match)) { ambiguous = true; break; }
                }
                if (!ambiguous) matches.set(candidate, sparkling(match, skin));
            } else if (candidate < 0) {
                // No named representation yet: the appearance carrier is its own stable anchor.
                matches.add(new CritterScan.Match(skin.textureSpecies(), skin, skin, true, false, Set.of(skin.id())));
            }
        }
    }

    static boolean capturePart(CritterScan.Sample sample, List<CritterScan.Sample> statuses) {
        if (!sample.type().equals("item_display") && !sample.type().equals("armor_stand")) return false;
        return statuses.stream().anyMatch(status -> sameColumn(sample, status));
    }

    private static boolean sameColumn(CritterScan.Sample left, CritterScan.Sample right) {
        double x = left.x() - right.x(), z = left.z() - right.z();
        return x*x + z*z <= .5625 && Math.abs(left.y() - right.y()) < 3;
    }

    private static CritterScan.Match sparkling(CritterScan.Match match, CritterScan.Sample skin) {
        Set<UUID> aliases = new HashSet<>(match.textureIds());
        aliases.add(skin.id());
        return new CritterScan.Match(match.critter(), match.label(), match.body() == null ? skin : match.body(), true, match.conflict(), Set.copyOf(aliases));
    }

    private static boolean near(CritterScan.Sample skin, CritterScan.Match match) {
        if (!match.critter().equals(skin.textureSpecies()) || match.conflict()) return false;
        var anchor = match.body() == null ? match.label() : match.body();
        double x = skin.x() - anchor.x(), y = skin.y() - anchor.y(), z = skin.z() - anchor.z();
        // Appearance parts should share a narrow horizontal column with their owner.
        // The taller vertical allowance accommodates a head/name above the physical body.
        return x*x + z*z <= .5625 && Math.abs(y) < 3;
    }
}
