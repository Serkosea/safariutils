package dev.serko.safariutils.session;

import java.util.HashSet;
import java.util.List;

/** History-only intervals: collection edits and API ownership never establish or erase a catch date. */
public final class SparklingRunIntervals {
    private static long historyRevision = -1;
    private static Intervals cached = new Intervals(-1, -1);
    public record Intervals(int any, int unique) { }
    private SparklingRunIntervals() { }
    public static int sinceAny() { refresh(); return cached.any(); }
    public static int sinceNew() { refresh(); return cached.unique(); }
    private static void refresh() {
        if (historyRevision == RunHistory.revision()) return;
        historyRevision = RunHistory.revision();
        cached = calculate(RunHistory.runs());
    }
    static Intervals calculate(List<RunRecord> runs) {
        var seen = new HashSet<String>();
        int lastNew = -1, lastAny = -1;
        for (int i = 0; i < runs.size(); i++) {
            var run = runs.get(i);
            for (var event : run.sparklings) {
                lastAny = i;
                boolean firstRecorded = seen.add(event.species());
                // Modern runs explicitly distinguish new catches from known duplicates.
                // Legacy runs lack that flag, so use the first occurrence in history.
                if (run.newSparklingSpecies == null ? firstRecorded
                        : run.newSparklingSpecies.contains(event.species())) lastNew = i;
            }
        }
        return new Intervals(lastAny < 0 ? -1 : runs.size()-1-lastAny,
            lastNew < 0 ? -1 : runs.size()-1-lastNew);
    }
}
