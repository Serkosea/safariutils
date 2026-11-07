package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import java.util.*;

public final class FloorDropPatternRegression {
    public static int run() throws Exception {
        int checks = 0;
        require(FloorDrops.observationLobby(null, null).equals("pending"), "early visit must collect before lobby metadata arrives"); checks++;
        require(FloorDrops.observationLobby("m123", null).equals("m123"), "scoreboard gap must not reset pre-ticket evidence"); checks++;
        require(FloorDrops.observationLobby("m123", "").equals("m123"), "blank lobby metadata must preserve evidence"); checks++;
        require(FloorDrops.observationLobby("pending", "m123").equals("m123"), "initial lobby ID must attach to pending visit"); checks++;
        require(FloorDrops.observationLobby("m123", "m456").equals("m456"), "new lobby must reset old patterns"); checks++;
        var mound = new net.minecraft.core.BlockPos(-80, 57, 61);
        var neighbour = new net.minecraft.core.BlockPos(-80, 59, 61);
        var moundBodies = new HashMap<net.minecraft.core.BlockPos, net.minecraft.world.phys.Vec3>();
        var texture = new net.minecraft.world.phys.Vec3(-79.5, 58, 61.5);
        require(mound.equals(MoundSpotter.mergeTextureMound(texture, Set.of(mound), moundBodies)), "texture-only mound must use canonical objective slot"); checks++;
        require(MoundSpotter.mergeTextureMound(texture, Set.of(mound, neighbour), moundBodies) == null && moundBodies.size() == 1, "duplicate visual cannot claim neighbouring slot"); checks++;
        moundBodies.clear();
        moundBodies.put(mound, new net.minecraft.world.phys.Vec3(-79.5, 57, 61.5));
        require(MoundSpotter.mergeTextureMound(texture, Set.of(mound), moundBodies) == null, "texture and interaction doubled mound"); checks++;
        require(MoundSpotter.mergeTextureMound(new net.minecraft.world.phys.Vec3(-69.5, 56, 58.5), Set.of(mound), moundBodies) != null && moundBodies.size() == 2, "independent textured mound lost"); checks++;
        require(MoundSpotter.coveredByTexture(texture, Set.of(mound), moundBodies, Set.of(mound)), "interaction must not duplicate textured mound"); checks++;
        require(!MoundSpotter.coveredByTexture(new net.minecraft.world.phys.Vec3(-60, 45, 57), Set.of(mound), moundBodies, Set.of(mound)), "unrelated interaction fallback suppressed"); checks++;
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
            require(model.coinSupply() == coins, "coin supply must match observed ordering"); checks++;
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
            require(model.coinSupply() == -1, "contradictory coin supply must be unknown"); checks++;
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
