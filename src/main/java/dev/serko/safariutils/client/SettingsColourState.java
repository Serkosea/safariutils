package dev.serko.safariutils.client;

/** Renderer-independent colour editor. Own one per open editor, never per rendered row. */
public final class SettingsColourState {
    private final int minimumAlpha;
    private final float[] hsv = new float[3];
    private int alpha = 255;

    public SettingsColourState(int minimumAlpha) {
        if (minimumAlpha < 0 || minimumAlpha > 255) throw new IllegalArgumentException("alpha floor");
        this.minimumAlpha = minimumAlpha;
    }

    public float hue() { return hsv[0]; }
    public float saturation() { return hsv[1]; }
    public float brightness() { return hsv[2]; }
    public int alpha() { return alpha; }

    public void load(int argb) { load(argb, null); }

    /** Optional remembered hue preserves IQ's hue choice when editing black or gray. */
    public void load(int argb, Float rememberedHue) {
        java.awt.Color.RGBtoHSB(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, hsv);
        if (rememberedHue != null && (hsv[1] <= 0.001f || hsv[2] <= 0.001f)) hue(rememberedHue);
        alpha(argb >>> 24);
    }

    public void hue(float value) { if (Float.isFinite(value)) hsv[0] = Math.clamp(value, 0f, 1f); }
    public void saturation(float value) { if (Float.isFinite(value)) hsv[1] = Math.clamp(value, 0f, 1f); }
    public void brightness(float value) { if (Float.isFinite(value)) hsv[2] = Math.clamp(value, 0f, 1f); }
    public void alpha(int value) { alpha = Math.clamp(value, minimumAlpha, 255); }
    public int argb() { return argb(alpha, hsv[0], hsv[1], hsv[2]); }

    public static int argb(int alpha, float hue, float saturation, float brightness) {
        return Math.clamp(alpha, 0, 255) << 24
            | java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xffffff;
    }
}
