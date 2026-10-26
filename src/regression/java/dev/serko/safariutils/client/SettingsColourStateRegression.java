package dev.serko.safariutils.client;

public final class SettingsColourStateRegression {
    public static int run() {
        var random = new java.util.Random(20261025L);
        var state = new SettingsColourState(0);
        for (int i = 0; i < 10000; i++) {
            int colour = random.nextInt();
            state.load(colour);
            check(state.argb() == colour, "ARGB round trip");
        }
        state.load(0xff000000, 0.75f);
        state.saturation(1); state.brightness(1);
        check(state.argb() == 0xff8000ff, "remembered hue on black");
        int before = state.argb();
        state.hue(Float.NaN); state.saturation(Float.POSITIVE_INFINITY); state.brightness(Float.NEGATIVE_INFINITY);
        check(state.argb() == before, "invalid control values preserve state");
        state.hue(2); state.saturation(-1); state.brightness(2); state.alpha(-1);
        check(state.argb() == 0x00ffffff, "control clamps");
        var safari = new SettingsColourState(1);
        safari.load(0x00123456);
        check(safari.argb() == 0x01123456, "Safari alpha floor");
        var opaque = new SettingsColourState(255);
        opaque.load(0x12123456);
        check(opaque.argb() == 0xff123456, "Bridge/chat opaque policy");
        state.load(0xffff0000, 0.75f);
        check(state.hue() == 0, "coloured input replaces remembered hue");
        return 10006;
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
