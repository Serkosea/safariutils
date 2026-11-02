package dev.serko.safariutils.client;
/** Profile-specific outline backend; the shared policy never depends on this library. */
public final class SafariGlowBackend {
    private SafariGlowBackend() { }
    public static void initialize() {
        net.azureaaron.renderchest.api.CustomGlowCallback.EVENT.register((entity, state) ->
            SafariGlows.colour(entity));
    }
}
