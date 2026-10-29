package dev.serko.safariutils.client;

/** One clock for HUD, alerts and persisted contest identity. No server state is changed. */
final class ContestSchedule {
    static final long CYCLE = 1_200_000L;
    static final long ACTIVE = 1_169_000L;
    static final long OFFSET = 904_000L;
    // Observed New Year scoring resumed with approximately 8:40 remaining.
    static final long NEW_YEAR_DELAY = ACTIVE - 520_000L;
    static long cycle(long now) { return Math.floorDiv(now - OFFSET, CYCLE); }
    static long cycleStart(long now) { return cycle(now) * CYCLE + OFFSET; }
    static long startDelay(long cycleStart) {
        return cycleStart - SkyblockCalendar.yearStart(cycleStart) == 4_000L ? NEW_YEAR_DELAY : 0;
    }
    static long startsAt(long now) { long start = cycleStart(now); return start + startDelay(start); }
    static long remaining(long now) {
        long start = cycleStart(now), phase = now - start;
        return phase >= startDelay(start) && phase < ACTIVE ? ACTIVE - phase : -1;
    }
    static long untilNext(long now) {
        long start = cycleStart(now), effective = start + startDelay(start);
        if (now < effective) return effective - now;
        long next = start + CYCLE;
        return next + startDelay(next) - now;
    }
    private ContestSchedule() { }
}
