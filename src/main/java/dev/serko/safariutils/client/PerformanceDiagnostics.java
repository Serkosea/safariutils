package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.serko.safariutils.state.SafariRuntimeState;
import net.minecraft.client.Minecraft;

/** Shared opt-in profiler for every component. No Safari-presence gate or separate writer. */
public final class PerformanceDiagnostics {
    private static final PerformanceWindow WINDOW = new PerformanceWindow();
    private static final Gson JSON = new Gson();
    private static volatile boolean active;
    private static volatile long enabledAt;
    private static Thread clientThread;
    private static long previousFrame, windowStart;
    private static String context;
    private static dev.serko.safariutils.state.SafariEpoch epoch;
    private static Object screenType, level;
    private static dev.serko.safariutils.data.SafariBiome biome;
    private static boolean focused, inSafari;
    private static volatile String lastSummary = "No completed performance window yet.";
    private PerformanceDiagnostics() { }

    public static void install() {
        if (!dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.DEVELOPER_TOOLS)) return;
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            long[] started = {0};
            String stage = "screen/" + screen.getClass().getSimpleName();
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeExtract(screen).register((current, graphics, x, y, delta) -> started[0] = begin());
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) -> end(stage, started[0]));
        });
    }

    private static final ClassValue<String> EVENT_NAMES = new ClassValue<>() {
        protected String computeValue(Class<?> type) { return "event/" + type.getName(); }
    };
    public static void endEvent(Class<?> type, long started) {
        if (started != 0 && active) end(EVENT_NAMES.get(type), started);
    }

    public static long begin() { return active ? System.nanoTime() : 0; }
    public static void end(String stage, long started) {
        if (started == 0 || !active || started < enabledAt) return;
        long elapsed = System.nanoTime() - started;
        // Fixed caller-owned names only: never usernames, URLs, entity IDs, or input text.
        WINDOW.record(stage, elapsed, Thread.currentThread() != clientThread);
    }

    /** Called once at the start of Minecraft's frame loop, including menus. */
    public static void frame() {
        if (!StructuredDiagnostics.enabled()) {
            if (active) reset();
            // Context references are owned by this client-thread hook, never the writer.
            previousFrame = windowStart = 0; context = null; screenType = level = null; biome = null; epoch = null;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        var screen = ClientCompat.screen();
        Class<?> nextScreen = screen == null ? null : screen.getClass();
        boolean nextFocus = client.isWindowActive(), nextSafari = SafariLocation.inside();
        var nextBiome = nextSafari ? SafariLocation.biome() : null;
        var nextEpoch = SafariRuntimeState.current();
        boolean changed = !active || epoch == null || !epoch.equals(nextEpoch) || screenType != nextScreen || level != client.level
            || focused != nextFocus || inSafari != nextSafari || biome != nextBiome;
        if (!active) {
            WINDOW.clear(); clientThread = Thread.currentThread(); enabledAt = now;
            previousFrame = 0; windowStart = now;
        } else if (changed) { publish(now); previousFrame = 0; }
        if (changed) {
            epoch = nextEpoch; screenType = nextScreen; level = client.level; focused = nextFocus; inSafari = nextSafari; biome = nextBiome;
            context = (level == null ? "menu/" : "world/") + (nextScreen == null ? "game" : nextScreen.getSimpleName())
                + (focused ? "/focused/" : "/unfocused/") + (inSafari ? String.valueOf(biome) : "outside-safari");
        }
        active = true;
        if (previousFrame != 0) WINDOW.record("frame/interval", now - previousFrame);
        previousFrame = now;
        if (now - windowStart >= 5_000_000_000L) publish(now);
    }

    static void boundary() {
        if (active && Thread.currentThread() == clientThread && epoch != null) {
            publish(System.nanoTime()); previousFrame = 0;
        }
    }

    private static void publish(long now) {
        var snapshot = WINDOW.drain();
        long elapsed = now - windowStart; windowStart = now;
        if (snapshot.stages().isEmpty()) return;
        var capturedEpoch = epoch;
        String capturedContext = context;
        long wall = System.currentTimeMillis();
        var heap = Jvm.MEMORY.getHeapMemoryUsage();
        long gcMillis = 0, gcCount = 0;
        for (var collector : Jvm.GC) {
            gcMillis += Math.max(0, collector.getCollectionTime());
            gcCount += Math.max(0, collector.getCollectionCount());
        }
        long collections = gcCount, collectionMillis = gcMillis;
        var frame = snapshot.stages().stream().filter(s -> s.name().equals("client/frame/interval")).findFirst();
        Double fps = frameRate(frame.map(PerformanceWindow.Stage::timing).orElse(null));
        String fpsText = fps == null ? "frame sample pending" : String.format(java.util.Locale.ROOT, "%.1f FPS", fps);
        lastSummary = "Performance: " + fpsText + ", " + snapshot.stages().size() + " stages; "
            + capturedContext + ". Five-second summaries in the automatic log.";
        OperationalLog.structuredDeferred(() -> {
            JsonObject json = new JsonObject();
            json.addProperty("k", "perf"); json.addProperty("schema", 1); json.addProperty("t", wall);
            json.addProperty("c", capturedEpoch.connection()); json.addProperty("v", capturedEpoch.visit()); json.addProperty("r", capturedEpoch.run());
            json.addProperty("context", capturedContext); json.addProperty("windowMs", elapsed / 1e6);
            json.addProperty("heapUsedBytes", heap.getUsed()); json.addProperty("heapMaxBytes", heap.getMax());
            json.addProperty("gcCount", collections); json.addProperty("gcMillis", collectionMillis);
            json.addProperty("fps", fps); json.addProperty("droppedStages", snapshot.droppedStages());
            json.add("stages", JSON.toJsonTree(snapshot.stages()));
            return json.toString();
        });
    }

    static Double frameRate(PerformanceWindow.Summary frame) {
        return frame == null || frame.calls() == 0 || frame.meanMs() <= 0 ? null : 1000 / frame.meanMs();
    }

    private static final class Jvm {
        static final java.lang.management.MemoryMXBean MEMORY = java.lang.management.ManagementFactory.getMemoryMXBean();
        static final java.util.List<java.lang.management.GarbageCollectorMXBean> GC = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
    }
    public static String status() { return active ? lastSummary : "Performance recording is off; enable Automatic Logging."; }
    static void reset() {
        active = false; WINDOW.clear();
        lastSummary = "No completed performance window yet.";
    }
}
