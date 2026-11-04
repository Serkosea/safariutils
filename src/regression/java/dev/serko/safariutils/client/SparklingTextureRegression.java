package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class SparklingTextureRegression {
    public static int run() {
        var chuck = Critters.byName("Chuckwalla");
        String hash = "7abe66bc685731ad2d764bee6d2f3b6ff7c0bb041670f64c21620f014204f385";
        check(SparklingTextures.fromProperty(property("https://textures.minecraft.net/texture/" + hash)) == chuck, "known skin without network");
        check(SparklingTextures.fromProperty(property("https://example.com/texture/" + hash)) == null, "foreign URL cannot masquerade as skin");
        check(SparklingTextures.fromProperty("not base64") == null, "malformed metadata");
        check(SparklingTextures.fromProperty("A".repeat(8193)) == null, "bounded input");
        check(SparklingTextures.fromProperty(property("https://textures.minecraft.net/texture/unknown")) == null, "unknown skin");
        var skin = new CritterScan.Sample(UUID.randomUUID(), "player", null, 0, 0, 0, chuck);
        var label = new CritterScan.Sample(UUID.randomUUID(), "armor_stand", "Chuckwalla", 0, 1, 0);
        List<CritterScan.Match> matches = new ArrayList<>();
        SparklingTextureEvidence.merge(List.of(skin), matches, Set.of());
        check(matches.size() == 1 && matches.getFirst().body() == skin && matches.getFirst().sparkling(), "skin-only discovery");
        check(matches.getFirst().textureIds().contains(skin.id()), "appearance identity retained");
        matches = new ArrayList<>(List.of(new CritterScan.Match(chuck, label, skin, false, false)));
        SparklingTextureEvidence.merge(List.of(skin, label), matches, Set.of());
        check(matches.size() == 1 && matches.getFirst().sparkling() && matches.getFirst().label() == label, "late label enriches instead of duplicates");
        matches = new ArrayList<>(List.of(new CritterScan.Match(chuck, label, null, false, false)));
        SparklingTextureEvidence.merge(List.of(skin, label), matches, Set.of(label.id()));
        check(matches.size() == 2 && !matches.getFirst().sparkling() && matches.getFirst().body() == null, "capsule label cannot steal appearance body");
        var other = new CritterScan.Sample(UUID.randomUUID(), "player", null, .25, 0, 0, chuck);
        matches = new ArrayList<>(List.of(new CritterScan.Match(chuck, label, null, false, false)));
        SparklingTextureEvidence.merge(List.of(skin, other, label), matches, Set.of());
        check(matches.size() == 1 && !matches.getFirst().sparkling(), "ambiguous nearby skins do not choose a label");
        matches = new ArrayList<>(List.of(new CritterScan.Match(Critters.byName("Flitter"), label, skin, false, false)));
        SparklingTextureEvidence.merge(List.of(skin), matches, Set.of());
        check(matches.size() == 1 && !matches.getFirst().sparkling(), "conflicting species is not overwritten");
        var distantBody = new CritterScan.Sample(UUID.randomUUID(), "silverfish", null, 2, 0, 0);
        matches = new ArrayList<>(List.of(new CritterScan.Match(chuck, label, distantBody, false, false)));
        SparklingTextureEvidence.merge(List.of(skin), matches, Set.of());
        check(matches.size() == 2 && !matches.getFirst().sparkling(), "nearby independent body does not inherit skin evidence");
        SparklingTextures.clear();
        return 12;
    }
    private static String property(String url) {
        return Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(StandardCharsets.UTF_8));
    }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
}
