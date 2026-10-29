package dev.serko.safariutils.client;
/** Profile-specific outline backend; the shared policy never depends on this library. */
public final class SafariGlowBackend {
    private SafariGlowBackend() { }
    public static void initialize() {
        // Render Chest is 26.2-only. Keep the established depth-tested markers here.
    }
}
