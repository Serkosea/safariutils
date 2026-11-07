package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;

public final class FloorDropVisibilityRegression {
    public static int run() throws Exception {
        var field = ConfigManager.class.getDeclaredField("config"); field.setAccessible(true);
        Object previous = field.get(null);
        var config = new SafariConfig(); field.set(null, config);
        int checks = 0;
        for (String pipelineName : new String[] {"DEPTH_LINES_PIPELINE", "FACE_PIPELINE", "BEACON_DEPTH_TESTED_PIPELINE"}) {
            var pipelineField = MarkerPipelines.class.getDeclaredField(pipelineName); pipelineField.setAccessible(true);
            var pipeline = pipelineField.get(null);
            var depth = pipeline.getClass().getMethod("getDepthStencilState").invoke(pipeline);
            var defaults = depth.getClass().getField("DEFAULT").get(null);
            var test = depth.getClass().getMethod("depthTest");
            check(test.invoke(depth).equals(test.invoke(defaults))); checks++;
            check(!(boolean)depth.getClass().getMethod("writeDepth").invoke(depth)); checks++;
        }
        check(new SafariConfig().display.separateFloorDropCounts); checks++;
        check(!SparklingMode.icebreakersSufficient(1, 3, false)); checks++;
        check(SparklingMode.icebreakersSufficient(1, 3, true)); checks++;
        check(SparklingMode.icebreakersSufficient(3, 3, false)); checks++;
        check(!SparklingMode.icebreakersSufficient(0, 3, false)); checks++;
        check(SparklingMode.icebreakersSufficient(0, 0, false)); checks++;
        try {
            SafariObjectives.reset();
            config.sparkling.sparklingMode = false;
            for (int mask = 0; mask < 4; mask++) {
                config.display.floorDropLoot = (mask & 1) != 0;
                config.display.floorDropQuestItems = (mask & 2) != 0;
                check(FloorDrops.markerEnabled(FloorDropPattern.Kind.LOOT) == ((mask & 1) != 0)); checks++;
                check(FloorDrops.markerEnabled(FloorDropPattern.Kind.LIME) == ((mask & 2) != 0)); checks++;
                check(FloorDrops.markerEnabled(FloorDropPattern.Kind.UNKNOWN) == (mask != 0)); checks++;
            }
            check(!hidden(FloorDropPattern.Kind.LOOT)); checks++;
            check(!hidden(FloorDropPattern.Kind.LIME)); checks++;
            SafariObjectives.observeGemPlacements(1, 1);
            check(hidden(FloorDropPattern.Kind.LIME)); checks++;
            check(!hidden(FloorDropPattern.Kind.PURPLE)); checks++;
            check(!hidden(FloorDropPattern.Kind.LOOT)); checks++;
            SafariObjectives.observeGemPlacements(7, 7);
            check(hidden(FloorDropPattern.Kind.ORANGE) && hidden(FloorDropPattern.Kind.PURPLE)); checks++;
            config.sparkling.sparklingMode = true;
            check(hidden(FloorDropPattern.Kind.LOOT)); checks++;
            config.sparkling.sparklingAlwaysActiveWaypoints = 1 << 9;
            check(!hidden(FloorDropPattern.Kind.LIME) && hidden(FloorDropPattern.Kind.PURPLE)); checks++;
            check(!hidden(FloorDropPattern.Kind.UNKNOWN)); checks++;
            // Sparkling mode must retain incense needed for an explicitly retained Doomspiral.
            config.sparkling.sparklingIgnoreUniques = true;
            config.sparkling.sparklingAlwaysActiveCritters = dev.serko.safariutils.data.Critters.selectionMask(
                dev.serko.safariutils.data.Critters.byName("Doomspiral"));
            config.sparkling.sparklingAlwaysActiveWaypoints = 0;
            var inventoryField = SafariObjectives.class.getDeclaredField("currentInventory");
            inventoryField.setAccessible(true);
            int[] inventory = (int[]) inventoryField.get(null);
            for (int held = 0; held <= 4; held++) {
                inventory[4] = held;
                check(SparklingMode.hideFloorDrop(SafariBiome.HAUNTED, null, FloorDropPattern.Kind.INCENSE)
                    == (held == 4)); checks++;
            }
            config.sparkling.sparklingAlwaysActiveCritters = 0;
            config.sparkling.alwaysShowProfitableCritters = false;
            inventory[4] = 0;
            check(dev.serko.safariutils.session.SparklingStats.count(dev.serko.safariutils.data.Critters.byName("Doomspiral")) == 0); checks++;
            check(!SparklingMode.hideFloorDrop(SafariBiome.HAUNTED, null, FloorDropPattern.Kind.INCENSE)); checks++;
            check(!SparklingMode.hideFloorDrop(SafariBiome.HAUNTED, null, FloorDropPattern.Kind.UNKNOWN)); checks++;
            check(!SparklingMode.hideFloorDrop(SafariBiome.HAUNTED, null, FloorDropPattern.Kind.COIN)); checks++;
            check(!SparklingMode.hideFloorDrop(SafariBiome.FOREST, null, FloorDropPattern.Kind.BERRY)); checks++;
            check(!SparklingMode.hideFloorDrop(SafariBiome.FOREST, null, FloorDropPattern.Kind.WORM)); checks++;
            check(!SparklingMode.hideFloorDrop(SafariBiome.FOREST, null, FloorDropPattern.Kind.SEEDS)); checks++;
            check(SparklingMode.hideFloorDrop(SafariBiome.HAUNTED, null, FloorDropPattern.Kind.LOOT)); checks++;
            config.sparkling.sparklingAlwaysActiveWaypoints = 1;
            check(!hidden(FloorDropPattern.Kind.LOOT)); checks++;
        } finally { SafariObjectives.reset(); field.set(null, previous); }
        return checks;
    }
    private static boolean hidden(FloorDropPattern.Kind kind) {
        return SparklingMode.hideFloorDrop(SafariBiome.CAVERN, null, kind);
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Floor visibility crossed type, evidence, or override boundaries");
    }
}
