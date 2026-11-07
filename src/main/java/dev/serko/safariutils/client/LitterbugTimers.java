package dev.serko.safariutils.client;

import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.state.MonotonicTime;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Tracks actual surface-to-underground transitions, never disappearance from entity range. */
public final class LitterbugTimers {
    private static final Map<UUID, Track> tracks = new HashMap<>();
    private static Object session;
    private static final class Track {
        final Cycle cycle = new Cycle();
        Vec3 surface;
        boolean sparkling, visibleSurface;
    }
    /** Minimum return window; a late observation never invents a hiding start. */
    static final class Cycle {
        private long seen = -1, hiddenAt = -1;
        private static final long MINIMUM_RETURN_MILLIS = 8_000;
        private boolean hidden, continuous;
        long observe(boolean below, long now) {
            boolean adjacent = seen >= 0 && now - seen <= 1000;
            long measured = -1;
            if (!below && hidden && continuous && adjacent) {
                measured = now - hiddenAt;

            }
            if (below && !hidden) { hiddenAt = now; continuous = adjacent; }
            else if (!adjacent) continuous = false;
            if (!below) hiddenAt = -1;
            hidden = below; seen = now;
            return measured;
        }
        boolean timed() { return hidden && continuous; }
        boolean hidden() { return hidden; }
        long remaining(long now) { return Math.max(0, MINIMUM_RETURN_MILLIS - (now - hiddenAt)); }
        boolean stale(long now) { return seen < 0 || now - seen > 1000; }
    }
    private LitterbugTimers() { }
    public static void tick() {
        Object current = SessionManager.current();
        if (session != current || !SafariLocation.inside()) { tracks.clear(); session = current; }
        if (current == null) return;
        long now = MonotonicTime.nowMillis();
        for (var sighting : CritterEntities.all()) {
            if (!sighting.critter().name().equals("Litterbug") || sighting.mob() == null) continue;
            var body = sighting.mob();
            if (body.isRemoved() || !EntityTypeIds.is(body, "endermite")
                    || RecatchSpots.isCaptureArtifact(sighting.critter(), body,
                        RecatchSpots.snapshot().captureInProgress(sighting.critter()))) {
                tracks.remove(body.getUUID()); continue;
            }
            boolean below = body.getY() < 20;
            Track track = tracks.computeIfAbsent(body.getUUID(), ignored -> new Track());
            if (!below && body.getY() >= 60) {
                track.surface = body.position();
                track.visibleSurface |= VisibilityCheck.canSee(body);
            }
            track.sparkling = SparklingWatch.presentsAsSparkling(sighting);
            if (below && track.surface == null && !SafeMode.critterHitboxes(track.sparkling)) {
                track.surface = projectedSurface(body.position());
            }
            long duration = track.cycle.observe(below, now);
            if (duration >= 0) DebugLog.line("LITTERBUG/TIMER", "body=" + body.getId() + " observedHiddenMs=" + duration);
        }
        tracks.values().removeIf(track -> track.cycle.stale(now));
    }
    /** Recorded Litterbug paths run across the Haunted room floor at Y=69.
     * Project only onto loaded, supporting terrain; never fetch chunks or use a roof heightmap. */
    private static Vec3 projectedSurface(Vec3 underground) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return null;
        var feet = net.minecraft.core.BlockPos.containing(underground.x, 69, underground.z);
        if (!level.isLoaded(feet) || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return null;
        var floor = feet.below();
        var shape = level.getBlockState(floor).getCollisionShape(level, floor);
        if (shape.isEmpty()) return null;
        return new Vec3(underground.x, floor.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), underground.z);
    }
    static boolean replaces(CritterEntities.Sighting sighting) {
        if (!ConfigManager.get().display.litterbugTimers || sighting.mob() == null) return false;
        Track track = tracks.get(sighting.mob().getUUID());
        return track != null && track.cycle.hidden() && track.surface != null && (!SafeMode.critterHitboxes(track.sparkling) || track.visibleSurface);
    }
    static void addMarkers(List<Markers.Marker> markers) {
        var display = ConfigManager.get().display;
        if (!display.litterbugTimers || !display.enableHitboxes) return;
        var critter = dev.serko.safariutils.data.Critters.byName("Litterbug");
        long now = MonotonicTime.nowMillis();
        for (Track track : tracks.values()) {
            if (!CritterMarkerGeometry.visibleInBiome(critter, track.sparkling, SafariLocation.biome())
                    || !track.cycle.hidden() || track.surface == null || SafeMode.critterHitboxes(track.sparkling) && !track.visibleSurface
                    || SparklingMode.hideOrdinaryHitbox(critter, track.sparkling)) continue;
            long remaining = track.cycle.timed() ? track.cycle.remaining(now) : 0;
            String time = !track.cycle.timed() ? "Hidden" : remaining == 0 ? "Returning..." : String.format(Locale.ROOT, "~%.1fs", remaining / 1000.0);
            Vec3 at = track.surface;
            markers.add(new Markers.Marker(new AABB(at.x - .25, at.y, at.z - .25, at.x + .25, at.y + .2, at.z + .25),
                (track.sparkling ? "SPARKLING " : "") + "Litterbug (" + time + ")",
                WaypointRenderer.critterHitboxColour(critter, track.sparkling), Markers.Style.WAYPOINT,
                !SafeMode.critterHitboxes(track.sparkling), false, true));
        }
    }
}
