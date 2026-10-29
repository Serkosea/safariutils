package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariRuntimeState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Observations only: numeric IDs are not identity or prediction rules. Client-thread owner. */
public final class EntityResearchLog {
    private static final Gson JSON = new Gson();
    private static final int MAX_TRACKED = 4096, MAX_EVENTS = 50000, BATCH = 64;
    private static final EntityResearchIndex seen = new EntityResearchIndex(MAX_TRACKED);
    private static final Map<BlockPos, List<Integer>> floorGroups = new HashMap<>();
    private static final List<Row> pending = new ArrayList<>(BATCH);
    private static Object world;
    private static SafariEpoch epoch;
    private static long generation, sequence, events, omitted, lastSample = Long.MIN_VALUE;
    private static boolean baseline;
    private record Row(long sequence, long time, long nano, String action, Object data) { }
    private record Body(int networkId, UUID uuid, String type, String name, String item, int count,
                        double x, double y, double z, float width, float height, String bodyState) { }
    private record Use(String action, String hand, Body target, String item, String name, int count) { }
    private record Floor(int x, int y, int z, List<Integer> networkIds) { }
    private record Pickup(String message, List<String> candidates, String attribution) { }
    private EntityResearchLog() { }

    public static void install() {
        if (!BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)) return;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (StructuredDiagnostics.enabled()) flush();
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (ready() && level == world) observe(entity, "load");
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!ready() || level != world || entity instanceof Player) return;
            seen.unload(entity.getId(), entity.getUUID());
            emit("unload", body(entity));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
        long performanceStarted = dev.serko.safariutils.client.PerformanceDiagnostics.begin();
        try {
            if (!ready()) return;
            long tick = client.level.getGameTime();
            // Metadata (including late names/items) is sampled, not copied every render frame.
            if (events < MAX_EVENTS && (baseline || lastSample == Long.MIN_VALUE || tick - lastSample >= 5 || tick < lastSample)) {
                lastSample = tick;
                for (Entity entity : WorldEntities.current()) observe(entity, baseline ? "baseline" : "observed");
                baseline = false;
            }
            flush();

        } finally { dev.serko.safariutils.client.PerformanceDiagnostics.end("diagnostics/entity-research", performanceStarted); }
    });
    }

    static boolean ready() {
        if (!StructuredDiagnostics.enabled() || !SafariLocation.inSafari()
                || Minecraft.getInstance().level == null) {
            if (world != null) {
                if (StructuredDiagnostics.enabled()) { emit("end", "left observation scope"); flush(); }
                clear();
            }
            return false;
        }
        Object level = Minecraft.getInstance().level;
        SafariEpoch current = SafariRuntimeState.current();
        if (world != level || epoch == null || epoch.visit() != current.visit()
                || epoch.connection() != current.connection()) {
            flush(); clear(); world = level; epoch = current; generation++; baseline = true;
            emit("begin", "Baseline is partial; load/unload means client visibility, not server creation/destruction. Metadata sample=5 ticks.");
        }
        // Ticket use can change run/phase without changing the world or visit.
        if (!epoch.equals(current)) { flush(); epoch = current; }
        return true;
    }

    private static void clear() {
        seen.clear(); floorGroups.clear(); pending.clear(); world = null; epoch = null;
        sequence = events = omitted = 0; lastSample = Long.MIN_VALUE; baseline = false;
    }

    private static Body body(Entity entity) {
        ItemStack stack = entity instanceof Display.ItemDisplay display && display.itemRenderState() != null
            ? display.itemRenderState().itemStack() : ItemStack.EMPTY;
        String item = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = entity.getCustomName() == null ? "" : bounded(entity.getCustomName().getString());
        return new Body(entity.getId(), entity.getUUID(), EntityTypeIds.key(entity), name, item,
            stack.getCount(), entity.getX(), entity.getY(), entity.getZ(), entity.getBbWidth(), entity.getBbHeight(),
            entity instanceof net.minecraft.world.entity.animal.armadillo.Armadillo armadillo ? armadillo.getState().name() : "");
    }

    private static void observe(Entity entity, String reason) {
        if (entity instanceof Player) return;
        Body body = body(entity);
        var identity = new EntityResearchIndex.Identity(body.uuid(), body.type(), body.name(), body.item(), body.count(), body.bodyState());
        switch (seen.observe(body.networkId(), identity)) {
            case SAME -> { }
            case FULL -> omit();
            case NEW -> emit(reason, body);
            case METADATA -> emit("metadata", body);
            case REUSED -> emit("id-reused", body);
        }
    }

    static void interaction(String action, Entity entity, String hand) {
        if (!ready() || entity == null || entity instanceof Player) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = "OFF_HAND".equals(hand) ? player.getOffhandItem() : player.getMainHandItem();
        emit("interaction", new Use(action, hand, body(entity),
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), bounded(stack.getHoverName().getString()), stack.getCount()));
    }

    /** Group evidence is collected in the existing floor scan; no additional world walk. */
    static void floors(Map<BlockPos, List<Integer>> groups) {
        if (!ready()) return;
        for (var entry : groups.entrySet()) {
            List<Integer> ids = entry.getValue().stream().sorted().toList();
            if (ids.equals(floorGroups.get(entry.getKey()))) continue;
            if (!floorGroups.containsKey(entry.getKey()) && floorGroups.size() >= MAX_TRACKED) { omit(); continue; }
            floorGroups.put(entry.getKey(), ids);
            BlockPos pos = entry.getKey();
            emit("floor-components", new Floor(pos.getX(), pos.getY(), pos.getZ(), ids));
        }
        var iterator = floorGroups.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (groups.containsKey(entry.getKey())) continue;
            BlockPos pos = entry.getKey();
            emit("floor-not-observed", new Floor(pos.getX(), pos.getY(), pos.getZ(), entry.getValue()));
            iterator.remove();
        }
    }

    static void floorClick(BlockPos clicked, BlockPos tracked) {
        if (!ready()) return;
        emit("floor-click", List.of(clicked.getX(), clicked.getY(), clicked.getZ(), tracked.getX(), tracked.getY(), tracked.getZ()));
    }

    static void pickup(String message, Map<BlockPos, Long> candidates) {
        if (!ready()) return;
        // Keep all pending alternatives: oldest-click attribution is not ground truth.
        long now = System.currentTimeMillis();
        List<String> positions = candidates.entrySet().stream().limit(64).map(e ->
            e.getKey().getX() + "," + e.getKey().getY() + "," + e.getKey().getZ() + " ageMs=" + (now - e.getValue())).toList();
        emit("floor-reward", new Pickup(bounded(message), positions,
            candidates.size() == 1 ? "single-pending-candidate" : candidates.isEmpty() ? "unmatched" : "ambiguous"));
        if (candidates.size() > 64) omit();
    }

    public static void onChatMessage(String message) {
        if (message == null || !(message.startsWith("This particular Scrappy ") || message.startsWith("The Scrappy "))) return;
        if (ready()) emit("scrappy-reply", bounded(message));
        // Replies carry no target identity. Correlate explicit interaction records offline.
    }

    public static String status() {
        if (!StructuredDiagnostics.enabled()) return "Automatic Logging is off; entity research is not recording.";
        if (!ready()) return "Automatic Logging is on; entity research starts at Safari or its entrance. Enable before entering for best coverage.";
        return "Entity research: visit " + epoch.visit() + ", world " + generation + ", " + seen.size()
            + " identities, " + events + " events, " + omitted + " omitted. Baseline may be partial. /safari debug save saves this visit.";
    }

    static String mark(String note) {
        if (!ready()) return "Enable Automatic Logging and enter Safari before adding a research note.";
        if (events >= MAX_EVENTS) return "Research event limit reached; the log is marked incomplete. Start a new visit for more evidence.";
        emit("note", bounded(note)); flush(); return "Research note added to the current diagnostic stream.";
    }

    private static String bounded(String text) { return text.length() <= 256 ? text : text.substring(0, 256); }
    private static void omit() {
        omitted++;
        if (omitted == 1) {
            pending.add(new Row(++sequence, System.currentTimeMillis(), System.nanoTime(), "gap", "Research limit reached; evidence incomplete."));
            flush();
        }
    }
    private static void emit(String action, Object data) {
        if (events >= MAX_EVENTS) { omit(); return; }
        events++;
        pending.add(new Row(++sequence, System.currentTimeMillis(), System.nanoTime(), action, data));
        if (events == MAX_EVENTS) omit();
        else if (pending.size() >= BATCH) flush();
    }
    static void flush() {
        if (pending.isEmpty() || epoch == null) return;
        List<Row> rows = List.copyOf(pending); pending.clear();
        SafariEpoch context = epoch; long scope = generation;
        // Only immutable records reach the writer; Gson/compression never read live entities.
        OperationalLog.structuredDeferred(() -> {
            JsonObject json = new JsonObject();
            json.addProperty("k", "er"); json.addProperty("schema", 1);
            json.addProperty("t", rows.getFirst().time()); json.addProperty("c", context.connection());
            json.addProperty("v", context.visit()); json.addProperty("r", context.run());
            json.addProperty("world", scope); json.add("events", JSON.toJsonTree(rows));
            return json.toString();
        });
    }
}
