package dev.serko.safariutils.client;

public final class PerformanceRegression {
    public static int run() throws Exception {
        PerformanceWindow window = new PerformanceWindow();
        for (int i = 0; i < 95; i++) window.record("frame", 10_000_000);
        for (int i = 0; i < 5; i++) window.record("frame", 200_000_000);
        window.record("frame", 3_000_000, true);
        var result = window.drain();
        var client = result.stages().stream().filter(s -> s.name().equals("client/frame")).findFirst().orElseThrow().timing();
        require(client.calls() == 100 && client.p95UpperMs() == 11 && client.maxMs() == 200, "histogram and spike retention");
        require(client.over50ms() == 5 && client.meanMs() == 19.5, "mean and thresholds");
        require(result.stages().size() == 2, "worker isolated from client");
        require(window.drain().stages().isEmpty(), "drain resets window");
        for (int i = 0; i < PerformanceWindow.MAX_STAGES + 10; i++) window.record("key" + i, 100);
        result = window.drain();
        require(result.stages().size() == PerformanceWindow.MAX_STAGES && result.droppedStages() == 10, "bounded names");
        Thread a = new Thread(() -> { for (int i=0;i<10000;i++) window.record("parallel", 1000); });
        Thread b = new Thread(() -> { for (int i=0;i<10000;i++) window.record("parallel", 2000); });
        a.start(); b.start(); a.join(); b.join();
        require(window.drain().stages().getFirst().timing().calls() == 20000, "concurrent callers");
        window.record("overflow", 5_000_000_000L);
        require(window.drain().stages().getFirst().timing().p95UpperMs() == 5000, "long pauses remain visible");
        window.record("invalid", -1); window.clear();
        require(window.drain().stages().isEmpty(), "disabled reset");
        require(PerformanceDiagnostics.frameRate(null) == null, "no sample is not zero FPS");
        require(PerformanceDiagnostics.frameRate(new PerformanceWindow.Summary(0, 0, 0, 0, 0, 0)) == null, "empty frame window");
        require(PerformanceDiagnostics.frameRate(new PerformanceWindow.Summary(60, 20, 20, 21, 60, 0)) == 50, "frame-rate conversion");
        return 11;
    }
    private static void require(boolean pass, String reason) {
        if (!pass) throw new AssertionError("Performance: " + reason);
    }
}
