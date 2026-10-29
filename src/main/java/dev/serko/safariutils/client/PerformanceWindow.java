package dev.serko.safariutils.client;

import java.util.*;

/** Small fixed-bin histograms. Inclusive elapsed wall time, not CPU/GPU attribution. */
final class PerformanceWindow {
    static final int MAX_STAGES = 192;
    record Summary(long calls, double meanMs, double maxMs, double p95UpperMs, long over16ms, long over50ms) { }
    record Stage(String name, Summary timing) { }
    record Snapshot(List<Stage> stages, long droppedStages) { }
    private final Map<String, Histogram[]> stages = new HashMap<>();
    private long dropped;
    void record(String name, long nanos) { record(name, nanos, false); }
    synchronized void record(String name, long nanos, boolean worker) {
        if (nanos < 0 || name == null) return;
        Histogram[] pair = stages.get(name);
        if (pair == null) {
            if (stages.size() >= MAX_STAGES) { dropped++; return; }
            pair = new Histogram[2]; stages.put(name, pair);
        }
        int slot = worker ? 1 : 0;
        if (pair[slot] == null) pair[slot] = new Histogram();
        pair[slot].add(nanos);
    }
    synchronized Snapshot drain() {
        List<Stage> result = new ArrayList<>(stages.size());
        stages.forEach((name, pair) -> {
            if (pair[0] != null) result.add(new Stage("client/" + name, pair[0].summary()));
            if (pair[1] != null) result.add(new Stage("worker/" + name, pair[1].summary()));
        });
        result.sort(Comparator.comparing(Stage::name));
        Snapshot snapshot = new Snapshot(List.copyOf(result), dropped);
        stages.clear(); dropped = 0;
        return snapshot;
    }
    synchronized void clear() { stages.clear(); dropped = 0; }
    private static final class Histogram {
        private final long[] bins = new long[129];
        private long count, max, over16, over50;
        private double total;
        void add(long nanos) {
            count++; total += nanos; max = Math.max(max, nanos);
            bins[(int)Math.min(128, nanos / 1_000_000L)]++;
            if (nanos > 16_666_667L) over16++;
            if (nanos > 50_000_000L) over50++;
        }
        Summary summary() {
            long target = count - count / 20, accumulated = 0;
            double percentile = 0;
            for (int i = 0; i < bins.length; i++) {
                accumulated += bins[i];
                if (accumulated >= target) { percentile = i == 128 ? max / 1e6 : i + 1; break; }
            }
            return new Summary(count, total / count / 1e6, max / 1e6, percentile, over16, over50);
        }
    }
}
