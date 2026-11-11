package dev.serko.safariutils.client;

import net.minecraft.world.entity.Display;

/** The server can retain a model carrier while hiding its model during/after capture. */
final class CritterDisplayState {
    private CritterDisplayState() { }

    static boolean hidden(Display.ItemDisplay display) {
        // DisplayRenderer still draws a model with the generic invisible flag set;
        // only a collapsed model is evidence that its texture is no longer visible.
        var render = display.renderState();
        if (render == null) return false; // Initial metadata has not been applied yet.
        var scale = render.transformation().get(1.0f).scale();
        return collapsed(scale.x(), scale.y(), scale.z());
    }

    static boolean collapsed(float x, float y, float z) {
        // A thin/flat or mirrored model is still a valid visible carrier.
        return Math.abs(x) < .00001f && Math.abs(y) < .00001f && Math.abs(z) < .00001f;
    }
}
