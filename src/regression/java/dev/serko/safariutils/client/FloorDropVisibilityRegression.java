package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;

public final class FloorDropVisibilityRegression {
    public static int run() throws Exception {
        var field = ConfigManager.class.getDeclaredField("config"); field.setAccessible(true);
        Object previous = field.get(null);
        var config = new SafariConfig(); field.set(null, config);
        int checks = 0;
        for (String pipelineName : new String[] {"DEPTH_LINES_PIPELINE", "FACE_PIPELINE", "BEACON_DEPTH_TESTED_PIPELINE"}) {
            var pipelineField = WaypointRenderer.class.getDeclaredField(pipelineName); pipelineField.setAccessible(true);
            var pipeline = (com.mojang.blaze3d.pipeline.RenderPipeline) pipelineField.get(null);
            check(pipeline.getDepthStencilState().depthTest() == com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT.depthTest()); checks++;
            check(!pipeline.getDepthStencilState().writeDepth()); checks++;
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
