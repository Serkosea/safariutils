package dev.serko.safariutils.client;

import java.util.Map;
import java.util.Set;

public final class ScrappyFeedingRegression {
    public static int run() {
        require(ScrappyFeeding.initialCandidates(Map.of(100, true)).equals(Set.of(94, 97, 100)), "missing bodies treated as absent");
        require(ScrappyFeeding.initialCandidates(Map.of(100, true, 94, false, 97, false)).equals(Set.of(100)), "positive type exclusions not applied");
        require(ScrappyFeeding.initialCandidates(Map.of(100, true, 103, true, 106, true)).equals(Set.of(100)), "initial trio ambiguous");
        require(ScrappyFeeding.initialCandidates(Map.of(100, true, 120, true)).isEmpty(), "inconsistent body IDs accepted");
        var state = new ScrappyFeeding.Progress();
        state.confirm(0, true, false, false);
        require(state.feeds == 1, "first feeding lost");
        state.confirm(0, true, false, false);
        require(state.feeds == 2, "repeated server wording incorrectly deduplicated");
        state.confirm(0, false, true, false);
        require(state.feeds == 3, "paw stage did not establish third feed");
        state.confirm(0, true, false, false);
        require(state.feeds == 3, "late cautious reply regressed progress");
        state.confirm(0, false, false, true);
        state.confirm(0, true, false, false);
        require(state.awake && state.feeds == 4, "awakened state regressed");
        return 9;
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
