package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import java.util.*;

public final class FloorDropPatternRegression {
    public static int run() throws Exception {
        int checks = 0;
        String[] moundLines = {"Small cracks begin to form in the mound...", "The cracks seem to be getting larger, keep hitting it!",
            "Chunks of the mound begin falling away...", "The mound is about to fall to pieces! Keep going!",
            "The mound fell apart, revealing a Rockmite hidden inside!"};
        for (int stage = 0; stage < moundLines.length; stage++) {
            require(MoundSpotter.hitStage(moundLines[stage]) == stage + 1, "wrong server mound stage"); checks++;
        }
        require(MoundSpotter.hitStage("Player: Small cracks begin to form in the mound...") == 0, "player chat counted as progress"); checks++;
        for (int coins = 3; coins <= 6; coins++) for (int ice = 4; ice <= 5; ice++) {
            FloorDropPattern model = new FloorDropPattern();
            int base = 1000;
            List<Integer> order = new ArrayList<>();
            for (int ordinal = 0; ordinal < 20 + coins + ice; ordinal++) order.add(ordinal);
            Collections.shuffle(order, new Random(coins * 10L + ice));
            for (int ordinal : order) {
                SafariBiome biome = ordinal < 6 ? SafariBiome.CAVERN : ordinal < 15 ? SafariBiome.FOREST
                    : ordinal < 20 + coins ? SafariBiome.HAUNTED : SafariBiome.ICY;
                model.observe(base + ordinal * 3, biome);
            }
            require(model.kind(base) == FloorDropPattern.Kind.LIME, "unordered arrival changed gem anchor"); checks++;
            require(model.kind(base + 18) == FloorDropPattern.Kind.WORM, "wrong bird feed"); checks++;
            require(model.kind(base + (20 + coins - 1) * 3) == FloorDropPattern.Kind.COIN, "coin boundary lost"); checks++;
            require(model.kind(base + (20 + coins + 3) * 3) == FloorDropPattern.Kind.ICE, "fourth icebreaker ambiguous"); checks++;
            int fifth = base + (20 + coins + 4) * 3;
            model.observe(fifth, SafariBiome.ICY);
            require(model.kind(fifth) == FloorDropPattern.Kind.ICE, "observed five-icebreaker default missing"); checks++;
            model.reward(fifth, ice == 5 ? FloorDropPattern.Kind.ICE : FloorDropPattern.Kind.LOOT);
            require(model.kind(fifth) == (ice == 5 ? FloorDropPattern.Kind.ICE : FloorDropPattern.Kind.LOOT), "reward did not constrain boundary"); checks++;
            model.observe(base + 999, SafariBiome.FOREST);
            require(model.kind(base + 999) == FloorDropPattern.Kind.LOOT, "late gaps treated as quest ordinals"); checks++;
            model.reward(base, FloorDropPattern.Kind.COIN);
            require(model.kind(base + 18) == FloorDropPattern.Kind.UNKNOWN, "contradiction left confident predictions"); checks++;
            model.reset();
            model.observe(base + 999, SafariBiome.FOREST);
            require(model.kind(base + 999) == FloorDropPattern.Kind.UNKNOWN, "visit reset retained anchor"); checks++;
        }
        for (String fixture : new String[] {"/floor-pattern-five-runs.json", "/floor-pattern-oct29-runs.json"})
        try (var input = FloorDropPatternRegression.class.getResourceAsStream(fixture)) {
            if (input == null) throw new AssertionError("Missing five-run fixture");
            var runs = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            for (var run : runs) {
                FloorDropPattern model = new FloorDropPattern();
                List<com.google.gson.JsonObject> seen = new ArrayList<>();
                for (var row : run.getAsJsonArray()) {
                    var drop = row.getAsJsonObject(); seen.add(drop);
                    model.observe(drop.get("id").getAsInt(), SafariBiome.valueOf(drop.get("biome").getAsString()));
                    for (var previous : seen) {
                        var predicted = model.kind(previous.get("id").getAsInt());
                        var truth = FloorDropPattern.Kind.valueOf(previous.get("truth").getAsString());
                        require(predicted == FloorDropPattern.Kind.UNKNOWN || truth == FloorDropPattern.Kind.UNKNOWN || predicted == truth,
                            "Recorded arrival-prefix prediction disagrees with actual reward"); checks++;
                    }
                }
                require(model.candidateCount() > 0, "Recorded run failed to establish signature"); checks++;
            }
        }
        return checks;
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
