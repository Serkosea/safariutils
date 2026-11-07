package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.Critter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks Hideyho through START, PENDING, END and DONE using exact chat transitions.
 * START and END positions come from live sightings and remain known after the
 * entity unloads. PENDING intentionally shows no marker; DONE stops tracking.
 */
public final class HideyhoSolver {

	private static final String NAME = "Hideyho";
	private static final Critter CRITTER = Critters.byName(NAME);

	/** How its own lines arrive once formatting is stripped. */
	private static final String SPEAKER = "[MOB] Hideyho:";

	/**
	 * Said on confirming [Sure] — the real start of hiding, and the point the START
	 * mark finally hides. It also says "Hehe, you found me!" the moment it is first
	 * hit, well before this — that line changes nothing here on purpose, so the mark
	 * stays visible and tracking it live for as long as the prompt sits unanswered,
	 * rather than vanishing on a hit that is easy to make without meaning to confirm.
	 */
	private static final String HIDE_PENDING = "Hehe, ok!";

	/** Said right before the actual teleport — the real start of tracking for END. */
	private static final String HIDE_STARTED = "No peeking!";

	/**
	 * Said once actually caught. The exact phrase, not the looser "you found me!" —
	 * "Hehe, you found me!" at the very start of the encounter also contains that,
	 * and matching on it loosely was mistaken for the real catch.
	 */
	private static final String CAUGHT = "Aah! You found me!";

	/** Lets the teleport finish before showing the final candidate set. */
	private static final long END_CANDIDATE_DELAY_MILLIS = 3_250;

	public enum Phase {START, PENDING, END, DONE}

	private static Phase phase = Phase.START;
	private static BlockPos position;
	private static Vec3 positionExact;
	private static Vec3 lastSightingExact;
	private static boolean live;
	private static boolean alertedThisPhase;
	private static boolean sparkling;
	private static boolean seenSparkling;
	private static boolean visiblyConfirmed;
	private static boolean lastSightingVisible;
	private static final Set<BlockPos> unchecked = new LinkedHashSet<>();

	/** Where START was confirmed — END never reuses this spot, so a repeat is discarded. */
	private static BlockPos startPosition;

	private static long endPhaseSinceMillis;

	private HideyhoSolver() {
	}

	public static void tick() {
		// Caught, for the rest of this round — no more sightings are worth anything,
		// including one that is really just the caught entity still on its way out.
		if (phase == Phase.DONE) return;

		BlockPos seen = fromSightings();
		if (seen == null) {
			pruneVisibleEmptyCandidates();
			// Out of range does not mean gone: a confirmed position is still good until
			// its own chat line says otherwise.
			live = false;
			return;
		}

		if (position != null) {
			position = seen;
			positionExact = lastSightingExact;
			live = true;
			visiblyConfirmed |= lastSightingVisible;
			if (lastSightingVisible) unchecked.removeIf(pos -> matchesCandidate(pos, seen));
			return;
		}

		if (phase == Phase.START) {
			// Nothing to wait for yet — shown the instant it is seen.
			confirm(seen, lastSightingExact);
			startPosition = seen;
			return;
		}

		if (phase == Phase.PENDING) {
			// It has not moved yet — a sighting here is the START spot again, not
			// something worth tracking. Nothing happens until "No peeking!" arrives.
			return;
		}

        // The server's hiding transition and a new location are sufficient evidence.
        // Do not add an artificial timer once a valid END sighting is available.
        if (!newEndLocation(startPosition, seen)) return;
        unchecked.add(seen);
        confirm(seen, lastSightingExact);
	}

	private static void confirm(BlockPos pos, Vec3 exact) {
		position = pos;
		positionExact = exact;
		live = true;
		sparkling = seenSparkling;
		visiblyConfirmed = lastSightingVisible;
		DebugLog.line("HIDEYHO", "CONFIRM " + phase + " block=" + pos(pos)
			+ " xyz=" + exact.x + "," + exact.y + "," + exact.z);
		if (visiblyConfirmed) unchecked.clear();
		if (alertedThisPhase) return;
		alertedThisPhase = true;
		EncounterAlerts.fireHideyho();
	}

	/** Where it is, or was last seen this phase. {@code null} before anything is confirmed. */
	public static BlockPos position() {
		return displayAllowed() ? position : null;
	}

	/** Shared eligibility for the confirmed marker and its gameplay alert, not camera culling. */
	public static boolean markerEnabled() {
		return ConfigManager.get().display.enableHitboxes && position() != null
			&& !SparklingMode.hideOrdinaryHitbox(CRITTER, sparkling);
	}

	/** Exact centre of the confirmed entity, preserving half-block hiding positions. */
	public static Vec3 positionExact() {
		return displayAllowed() ? positionExact : null;
	}

	/** Whether it is loaded right now, as opposed to only remembered. */
	public static boolean live() {
		return live && displayAllowed();
	}

	public static boolean sparkling() {
		return sparkling;
	}

	/** Which phase the mark belongs to — decides the {@code (START)}/{@code (END)} label. */
	public static Phase phase() {
		return phase;
	}

	/** The shared location pool still unchecked in the current START or END search. */
	public static Set<BlockPos> candidates() {
		if (!SafeMode.hideyho() || position != null && visiblyConfirmed
			|| (phase != Phase.START && phase != Phase.END)) return Set.of();
		if (phase == Phase.END
			&& System.currentTimeMillis() - endPhaseSinceMillis < END_CANDIDATE_DELAY_MILLIS) return Set.of();
		return Set.copyOf(unchecked);
	}

	private static void restoreCandidates() {
		unchecked.clear();
		unchecked.addAll(StaticEntityCatalog.positions(NAME));
	}

	private static void pruneVisibleEmptyCandidates() {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (!SafeMode.hideyho() || client.level == null) return;
		Set<BlockPos> loaded = new LinkedHashSet<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all(CRITTER)) {
			var entity = target(sighting);
            loaded.add(BlockPos.containing(CritterMarkerGeometry.hideyhoUpper(entity.position(), EntityTypeIds.is(entity, "player"))));
		}
		unchecked.removeIf(pos -> client.level.isLoaded(pos)
			&& VisibilityCheck.canInspectCandidate(pos)
			&& loaded.stream().noneMatch(actual -> matchesCandidate(pos, actual)));
	}

	private static boolean matchesCandidate(BlockPos candidate, BlockPos actual) {
		int dx = candidate.getX() - actual.getX();
		int dz = candidate.getZ() - actual.getZ();
		return dx * dx + dz * dz <= 16 && Math.abs(candidate.getY() - actual.getY()) <= 4;
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		// Only what Hideyho itself says counts. Quoted in someone's chat, the same words
		// would advance a phase that has not actually changed.
		if (!line.startsWith(SPEAKER)) return;

		if (line.contains(HIDE_PENDING)) {
			DebugLog.line("HIDEYHO", "CHAT HIDE_PENDING, " + phase + " -> PENDING raw=\"" + line + "\"");
			phase = Phase.PENDING;
			position = null;
			positionExact = null;
			live = false;
			visiblyConfirmed = false;
			alertedThisPhase = false;
			return;
		}

		if (line.contains(HIDE_STARTED)) {
			DebugLog.line("HIDEYHO", "CHAT HIDE_STARTED, " + phase + " -> END raw=\"" + line + "\"");
			phase = Phase.END;
			position = null;
			positionExact = null;
			live = false;
			visiblyConfirmed = false;
			alertedThisPhase = false;
			endPhaseSinceMillis = System.currentTimeMillis();
			restoreCandidates();
			return;
		}

		if (line.contains(CAUGHT)) {
			DebugLog.line("HIDEYHO", "CHAT CAUGHT, " + phase + " -> DONE raw=\"" + line + "\"");
			// The authoritative CAPTURE/SPARKLING lines arrive after the remaining
			// dialogue. SessionManager records the catch and starts its celebration then.
			phase = Phase.DONE;
			position = null;
			positionExact = null;
			live = false;
			visiblyConfirmed = false;
			startPosition = null;
			alertedThisPhase = false;
			unchecked.clear();
		}
	}

	public static void clear() {
		if (phase != Phase.START || position != null) {
			DebugLog.line("HIDEYHO", "CLEAR (was " + phase + ")");
		}
		phase = Phase.START;
		position = null;
		positionExact = null;
		live = false;
		sparkling = false;
		seenSparkling = false;
		visiblyConfirmed = false;
		lastSightingVisible = false;
		alertedThisPhase = false;
		startPosition = null;
		endPhaseSinceMillis = 0;
		restoreCandidates();
	}

	/** A spot from the last run says nothing about this one. */
	public static void reset() {
		clear();
	}

    static boolean newEndLocation(BlockPos start, BlockPos seen) {
        // Hideyho is parked near Y=0 and moved upward during its teleport. The
        // Haunted play area is above Y=60; these transit positions are not finds.
        return seen != null && seen.getY() >= 60 && (start == null
            || seen.getX() != start.getX() || seen.getZ() != start.getZ()
            || Math.abs(seen.getY() - start.getY()) > 3);
    }
    /** The same verified skin body anchors START, END, safe visibility and beacon rendering. */
    static net.minecraft.world.entity.Entity target(CritterEntities.Sighting sighting) {
        for (var id : sighting.textureIds()) {
            var body = WorldEntities.find(id);
            if (body != null && !body.isRemoved() && EntityTypeIds.is(body, "player")) return body;
        }
        return sighting.label();
    }
	private static BlockPos fromSightings() {
		lastSightingVisible = false;
		lastSightingExact = null;
		for (CritterEntities.Sighting sighting : CritterEntities.all(CRITTER)) {
			// Hideyho's named entity is Hideyho itself. Generic body pairing can select
			// an unrelated nearby critter and would poison the learned location catalog.
			var target = target(sighting);
			// Under Safe Mode, in range is not enough — the doc above already
			// explains why the entity is loaded well before it is ever seen; this is
			// what stops that gap from being used to find it before it is genuinely
			// visible on the player's own screen.
			boolean sparkling = SparklingWatch.isSparkling(sighting);
			lastSightingVisible = VisibilityCheck.canSee(target);
			if (SafeMode.hiddenCritter(sighting.critter(), sparkling)
				&& !lastSightingVisible) continue;
			seenSparkling = sparkling;
            Vec3 exact = CritterMarkerGeometry.hideyhoUpper(target.position(), EntityTypeIds.is(target, "player"));
            BlockPos observed = BlockPos.containing(exact);
            if (observed.getY() < 60 || phase == Phase.END && !newEndLocation(startPosition, observed)) continue;
            lastSightingExact = exact;
            return observed;
		}
		return null;
	}

	private static boolean displayAllowed() {
		return position == null || !SafeMode.hiddenCritter(CRITTER, sparkling) || visiblyConfirmed;
	}

	private static String pos(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
