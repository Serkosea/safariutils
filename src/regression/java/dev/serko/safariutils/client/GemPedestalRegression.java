package dev.serko.safariutils.client;

import java.util.ArrayList;

public final class GemPedestalRegression {
    public static int run() throws Exception {
        for (int mask = 0; mask < 8; mask++) {
            var markers = new ArrayList<Markers.Marker>();
            Markers.addGemPedestals(markers, mask);
            if (markers.size() != 3 - Integer.bitCount(mask)) throw new AssertionError("Wrong remaining pedestal count");
            for (var marker : markers) {
                int bit = switch (marker.label()) { case "Lime" -> 1; case "Orange" -> 2; case "Purple" -> 4; default -> throw new AssertionError("Unexpected pedestal"); };
                if ((mask & bit) != 0) throw new AssertionError("Placed gem still marked");
            }
        }
        for (var pedestal : GemPedestals.ALL) {
            int expected = switch (pedestal.label()) { case "Purple" -> 0xFF9D39DB; case "Lime" -> 0xFF9DFF00; case "Orange" -> 0xFFFF5B24; default -> throw new AssertionError(); };
            if (pedestal.colour() != expected) throw new AssertionError("Incorrect approved pedestal color");
            if (!pedestal.matches(pedestal.support(), net.minecraft.world.level.block.Blocks.PLAYER_HEAD)
                || pedestal.matches(pedestal.support(), net.minecraft.world.level.block.Blocks.AIR)
                || pedestal.matches(net.minecraft.world.level.block.Blocks.STONE, net.minecraft.world.level.block.Blocks.PLAYER_HEAD))
                throw new AssertionError("Incorrect gem block signature");
        }
        var field = ConfigManager.class.getDeclaredField("config"); field.setAccessible(true);
        Object previous = field.get(null);
        var config = new SafariConfig(); field.set(null, config);
        try {
            SafariObjectives.reset();
            config.advanced.safeDetectionHud = true;
            SafariObjectives.observeGemPlacements(9, 0);
            if (SafariObjectives.placedGemMask() != 0 || SafariObjectives.confirmedGemMask() != 0)
                throw new AssertionError("Unseen gem leaked to safe mode or party sync");
            config.advanced.safeDetectionHud = false;
            if (dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.SAFE_MODE_CONTROLS)
                && SafariObjectives.placedGemMask() != 1) throw new AssertionError("Extra observation missing");
            SafariObjectives.observeGemPlacements(1, 3);
            config.advanced.safeDetectionHud = true;
            if (SafariObjectives.placedGemMask() != 1 || SafariObjectives.confirmedGemMask() != 1)
                throw new AssertionError("Visible observation not promoted or absent gem accepted");
            SafariObjectives.reset();
            if (SafariObjectives.placedGemMask() != 0) throw new AssertionError("Gem evidence survived reset");
        } finally { SafariObjectives.reset(); field.set(null, previous); }
        return 18;
    }
    private GemPedestalRegression() { }
}
