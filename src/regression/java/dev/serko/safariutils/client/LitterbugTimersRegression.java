package dev.serko.safariutils.client;
public final class LitterbugTimersRegression {
    public static int run() {
        var cycle = new LitterbugTimers.Cycle();
        cycle.observe(true, 0);
        check(!cycle.timed(), "initial underground observation does not invent a hide start");
        cycle.observe(false, 500);
        cycle.observe(true, 1000);
        check(cycle.timed() && cycle.remaining(1000) == 8000, "observed transition starts estimated countdown");
        for (long time = 1500; time < 9000; time += 500) cycle.observe(true, time);
        check(cycle.observe(false, 9000) == 8000, "continuous cycle measures actual interval");
        cycle.observe(true, 9500);
        check(cycle.remaining(9500) == 8000, "next cycle retains minimum interval");
        cycle.observe(true, 12000);
        check(!cycle.timed(), "unloaded gap invalidates countdown rather than fabricating time");
        check(cycle.stale(14000), "lost body expires quickly");
        var longCycle = new LitterbugTimers.Cycle();
        longCycle.observe(false, 0); longCycle.observe(true, 500);
        for (long time=1000; time<16500; time+=500) longCycle.observe(true,time);
        check(longCycle.hidden() && longCycle.remaining(16000)==0, "marker remains after earliest return time passes");
        longCycle.observe(false,16500); longCycle.observe(true,17000);
        check(longCycle.remaining(17000)==8000, "long cycle must not lengthen the next countdown");
        return 8;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
