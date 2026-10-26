package dev.serko.safariutils.session;

import java.util.*;

/** Revision-cached projection of authoritative run records; never a second saved ledger. */
public final class PersonalBests {
    public record Key(String target, PersonalBestTracker.Group group) { }
    public record Best(RunRecord run, int runNumber, PersonalBestTracker.Split split, int clears) { }
    private static long revision = Long.MIN_VALUE;
    private static Map<Key, Best> cache = Map.of();
    private PersonalBests() { }
    public static Best best(String target, PersonalBestTracker.Group group) {
        if (revision != RunHistory.revision()) {
            cache = calculate(RunHistory.runs()); revision = RunHistory.revision();
        }
        return cache.get(new Key(target,group));
    }
    static Map<Key, Best> calculate(List<RunRecord> runs) {
        Map<Key,Best> result = new HashMap<>();
        runs = runs.stream().sorted(Comparator.comparingLong(r -> r.started)).toList();
        for (int i=0;i<runs.size();i++) {
            RunRecord run = runs.get(i);
            if (run.personalBestSplits == null) continue;
            Set<Key> counted = new HashSet<>();
            for (var split : run.personalBestSplits) {
                if (!PersonalBestTracker.valid(split) || !split.timingKnown() || !PersonalBestTracker.includesPlayer(split, run.self)) continue;
                Key key = new Key(split.target(),split.group());
                if (!counted.add(key)) continue;
                Best old = result.get(key);
                int clears = old == null ? 1 : old.clears+1;
                result.put(key, old == null || split.elapsedMillis()<old.split.elapsedMillis()
                    ? new Best(run,i+1,split,clears) : new Best(old.run,old.runNumber,old.split,clears));
            }
        }
        return Map.copyOf(result);
    }
}
