package dev.serko.safariutils.client;

import java.util.*;

/** Folds appearance evidence into the shared scan; ambiguous neighbours never acquire it. */
final class SparklingTextureEvidence {
    private SparklingTextureEvidence() { }

    static void merge(List<CritterScan.Sample> samples, List<CritterScan.Match> matches, Set<UUID> capsuleLabels) {
        merge(samples, matches, capsuleLabels, new HashMap<>(), new HashMap<>(), ignored -> false);
    }

    static void merge(List<CritterScan.Sample> samples, List<CritterScan.Match> matches, Set<UUID> capsuleLabels,
                      Map<UUID, UUID> owners, Map<UUID, Set<UUID>> capturedSkins, java.util.function.Predicate<UUID> capturing) {
        Set<UUID> loaded = new HashSet<>();
        for (var sample : samples) loaded.add(sample.id());
        owners.keySet().retainAll(loaded);
        capturedSkins.keySet().retainAll(loaded);
        Set<UUID> presentBodies = new HashSet<>();
        for (var match : matches) if (match.body() != null) presentBodies.add(match.body().id());
        List<CritterScan.Sample> captureStatuses = captureStatuses(samples);
        Set<UUID> captureLabels = new HashSet<>(capsuleLabels);
        for (var match : matches) if (capturePart(match.label(), captureStatuses)) captureLabels.add(match.label().id());
        List<CritterScan.Sample> skins = samples.stream().filter(sample -> sample.textureSpecies() != null
            && !capturePart(sample, captureStatuses)
            && !(sample.type().equals("item_display") && samples.stream().anyMatch(label ->
                captureLabels.contains(label.id()) && sameColumn(sample, label)))).toList();
        if (skins.isEmpty()) return;
        int originalSize = matches.size();
        for (var skin : skins) {
            UUID owner = owners.get(skin.id());
            // Retain an established owner when peers crowd together; never transfer its skin by proximity.
            int retained = -1;
            if (owner != null) for (int index = 0; index < originalSize; index++) {
                var match = matches.get(index);
                if (match.body() != null && owner.equals(match.body().id()) && near(skin, match)
                    && !captureLabels.contains(match.label().id())) { retained = index; break; }
            }
            if (retained >= 0) {
                matches.set(retained, sparkling(matches.get(retained), skin));
                capturedSkins.remove(skin.id());
                continue;
            }
            if (owner != null && capturing.test(owner)) capturedSkins.computeIfAbsent(skin.id(), ignored -> Set.copyOf(presentBodies));
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
                // A peer that was already alive when the owner vanished is not its breakout.
                if (capturedSkins.containsKey(skin.id()) && (match.body() == null
                    || capturedSkins.get(skin.id()).contains(match.body().id()))) continue;
                // A label equidistant from two distinct skins cannot choose an individual.
                for (var other : skins) {
                    if (!other.id().equals(skin.id()) && near(other, match)) { ambiguous = true; break; }
                }
                if (!ambiguous) {
                    matches.set(candidate, sparkling(match, skin));
                    if (match.body() != null && !match.body().id().equals(skin.id())) {
                        owners.put(skin.id(), match.body().id());
                        capturedSkins.remove(skin.id());
                    }
                }
            } else if (candidate < 0 && !capturedSkins.containsKey(skin.id())) {
                // No named representation yet: the appearance carrier is its own stable anchor.
                matches.add(new CritterScan.Match(skin.textureSpecies(), skin, skin, skin.textureSparkling(), false, Set.of(skin.id())));
            }
        }
    }

    static boolean capturePart(CritterScan.Sample sample, List<CritterScan.Sample> statuses) {
        if (!sample.type().equals("item_display") && !sample.type().equals("armor_stand")) return false;
        return statuses.stream().anyMatch(status -> status.capsulePaper() ? capsuleNeighbour(sample, status) : captureColumn(sample, status));
    }

    /** Distant capsule stands arrive without their custom names. Recognize the complete
     * two-label/head assembly rather than treating its copied skin as another critter. */
    static List<CritterScan.Sample> captureStatuses(List<CritterScan.Sample> samples) {
        List<CritterScan.Sample> result = new ArrayList<>();
        // Paper capsule and copied head are delivered farther away than their label stands.
        for (var paper : samples) if (paper.capsulePaper() && samples.stream().anyMatch(head ->
            head.type().equals("item_display") && head.textureSpecies() != null && capsuleNeighbour(head, paper))) result.add(paper);
        List<CritterScan.Sample> stands = samples.stream()
            .filter(sample -> sample.type().equals("armor_stand")).toList();
        for (var stand : stands) if ("CAPTURING".equalsIgnoreCase(stand.name())) result.add(stand);
        for (var head : samples) {
            if (!head.type().equals("item_display") || head.textureSpecies() == null) continue;
            CritterScan.Sample upper = null, lower = null;
            for (var stand : stands) {
                // Named non-capture entities cannot qualify merely by proximity.
                if (stand.name() != null && !stand.name().isBlank()
                    && !"CAPTURING".equalsIgnoreCase(stand.name())
                    && CritterScan.parseLabel(stand.name()) == null) continue;
                if (Math.abs(head.x() - stand.x()) > .03125 || Math.abs(head.z() - stand.z()) > .03125) continue;
                double offset = head.y() - stand.y();
                if (Math.abs(offset - 1.53125) <= .03125) upper = stand;
                if (Math.abs(offset - 1.90625) <= .03125) lower = stand;
            }
            if (upper != null && lower != null && !result.contains(upper)) result.add(upper);
        }
        return result;
    }

    /** Capture assembly parts share a quantized column; nearby critters do not. */
    private static boolean captureColumn(CritterScan.Sample left, CritterScan.Sample right) {
        return Math.abs(left.x() - right.x()) <= .03125
            && Math.abs(left.z() - right.z()) <= .03125
            && Math.abs(left.y() - right.y()) < 3;
    }

    private static boolean capsuleNeighbour(CritterScan.Sample left, CritterScan.Sample right) {
        double x = left.x() - right.x(), y = left.y() - right.y(), z = left.z() - right.z();
        return x*x + y*y + z*z <= .0625;
    }

    private static boolean sameColumn(CritterScan.Sample left, CritterScan.Sample right) {
        double x = left.x() - right.x(), z = left.z() - right.z();
        return x*x + z*z <= .5625 && Math.abs(left.y() - right.y()) < 3;
    }

    private static CritterScan.Match sparkling(CritterScan.Match match, CritterScan.Sample skin) {
        Set<UUID> aliases = new HashSet<>(match.textureIds());
        aliases.add(skin.id());
        return new CritterScan.Match(match.critter(), match.label(), match.body() == null ? skin : match.body(), match.sparkling() || skin.textureSparkling(), match.conflict(), Set.copyOf(aliases));
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
