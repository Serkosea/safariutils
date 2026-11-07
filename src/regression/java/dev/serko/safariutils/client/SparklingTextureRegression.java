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
        var areita = Critters.byName("Areita");
        var status = new CritterScan.Sample(UUID.randomUUID(), "armor_stand", "CAPTURING", -4.15625, 85.875, -50.5);
        var captureLabel = new CritterScan.Sample(UUID.randomUUID(), "armor_stand", "SPARKLING Areita", -4.15625, 85.5, -50.5);
        var captureHead = new CritterScan.Sample(UUID.randomUUID(), "item_display", null, -4.15625, 87.40625, -50.5, areita);
        matches = new ArrayList<>(List.of(new CritterScan.Match(areita, captureLabel, null, true, false)));
        SparklingTextureEvidence.merge(List.of(status, captureLabel, captureHead), matches, Set.of());
        check(matches.size() == 1 && matches.getFirst().body() == null, "recorded teammate capture retains label without false body");
        var realPeer = new CritterScan.Sample(UUID.randomUUID(), "player", null, -4.15625, 85.5, -50.5, areita);
        SparklingTextureEvidence.merge(List.of(status, captureLabel, captureHead, realPeer), matches, Set.of());
        check(matches.size() == 2 && matches.getLast().body() == realPeer, "real same-species peer survives beside capture");
        matches.clear();
        SparklingTextureEvidence.merge(List.of(captureLabel, captureHead), matches, Set.of(captureLabel.id()));
        check(matches.isEmpty(), "capsule metadata excludes its head before status arrives");
        var ordinary = SparklingTextures.appearance(property("https://textures.minecraft.net/texture/3504f1f2327a5110e643bb8667082512815fa434a29ed37f4ca83bb16d2db533"));
        check(ordinary != null && !ordinary.sparkling() && ordinary.species() == Critters.byName("Hideyho") && ordinary.type().equals("player"), "typed ordinary Hideyho recognition");
        var ordinaryBody = new CritterScan.Sample(UUID.randomUUID(), "player", null, 100, 60, 100, ordinary.species(), false);
        matches.clear();
        SparklingTextureEvidence.merge(List.of(ordinaryBody), matches, Set.of());
        check(matches.size() == 1 && !matches.getFirst().sparkling(), "ordinary appearance must never imply Sparkling");
        var lateLabel = new CritterScan.Sample(UUID.randomUUID(), "armor_stand", "Hideyho", 100, 61, 100);
        matches = new ArrayList<>(List.of(new CritterScan.Match(ordinary.species(), lateLabel, ordinaryBody, false, false)));
        SparklingTextureEvidence.merge(List.of(ordinaryBody, lateLabel), matches, Set.of());
        check(matches.size() == 1 && matches.getFirst().textureIds().contains(ordinaryBody.id()) && !matches.getFirst().sparkling(), "late ordinary label retains same identity");
        var wumpa = Critters.byName("Wumpa");
        var sleepingSkin = new CritterScan.Sample(UUID.randomUUID(), "item_display", null, 10,70,10,wumpa);
        var textureFirst = new ArrayList<>(CritterScan.scan(List.of(sleepingSkin), Map.of(), ignored -> {}));
        SparklingTextureEvidence.merge(List.of(sleepingSkin), textureFirst, Set.of());
        check(textureFirst.size() == 1 && textureFirst.getFirst().sparkling(), "sleeping Wumpa texture detected before label");
        var sleepLabel = new CritterScan.Sample(UUID.randomUUID(), "armor_stand", "Wumpa zZz",10,72,10);
        var sleepingBody = new CritterScan.Sample(UUID.randomUUID(), "ravager", null,10,70,10);
        var paired = new ArrayList<>(CritterScan.scan(List.of(sleepLabel,sleepingBody,sleepingSkin), Map.of(), ignored -> {}));
        SparklingTextureEvidence.merge(List.of(sleepLabel,sleepingBody,sleepingSkin), paired, Set.of());
        check(paired.size() == 1 && paired.getFirst().sparkling() && paired.getFirst().body() == sleepingBody,
            "ordinary sleeping name cannot erase Sparkling texture or create duplicate Wumpa");
        SparklingTextures.clear();
        var mound = SparklingTextures.appearance(property("https://textures.minecraft.net/texture/5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800"));
        check(mound != null && mound.mound() && mound.species() == null && !mound.sparkling(), "mound must not identify a living Rockmite");
        check(SparklingTextures.fromProperty(property("https://textures.minecraft.net/texture/ca2a103d108246de1935dbdf1a284898c5ffdffee18b65ec97eb01ecdc12fad9")) == Critters.byName("Rockmite"), "sparkling Rockmite remains detectable");
        return 22;
    }
    private static String property(String url) {
        return Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(StandardCharsets.UTF_8));
    }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
}
