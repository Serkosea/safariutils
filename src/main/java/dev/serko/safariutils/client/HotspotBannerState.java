package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;

/** One destination banner per visit, with a bounded fallback if assignment is unavailable. */
final class HotspotBannerState {
    record Notice(SafariBiome biome, boolean assigned) { }
    private long firstSeen = -1;
    private boolean shown, assignmentShown;
    Notice poll(SafariBiome actual, SafariBiome assigned, boolean assigning, long now) {
        if (actual == null) return null;
        if (firstSeen < 0) firstSeen = now;
        if (assigning && assigned != null) {
            if (assignmentShown) return null;
            shown = assignmentShown = true;
            return new Notice(assigned, true);
        }
        if (shown || assigning && now - firstSeen < 6500) return null;
        shown = true;
        return new Notice(actual, false);
    }
    void reset() { firstSeen = -1; shown = assignmentShown = false; }
}
