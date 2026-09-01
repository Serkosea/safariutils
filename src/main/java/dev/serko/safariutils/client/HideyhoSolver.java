package dev.serko.safariutils.client;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks Hideyho through START, PENDING, END and DONE using exact chat transitions.
 * START and stable END positions come from live sightings and remain known after the
 * entity unloads. PENDING intentionally shows no marker; DONE stops tracking.
 */
public final class HideyhoSolver {

	private static final String NAME = "Hideyho";

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

	/** How long a candidate end-position must hold still before it is shown. */
	private static final long STABLE_MILLIS = 1000;
	/** Lets the teleport finish before showing the final candidate set. */
	private static final long END_CANDIDATE_DELAY_MILLIS = 3_250;

	public enum Phase {START, PENDING, END, DONE}

	private static Phase phase = Phase.START;
	private static BlockPos position;
	private static boolean live;
	private static boolean alertedThisPhase;
	private static boolean sparkling;
	private static boolean seenSparkling;
	private static final Set<BlockPos> unchecked = new LinkedHashSet<>();

	/** Where START was confirmed — END never reuses this spot, so a repeat is discarded. */
	private static BlockPos startPosition;

	/** A not-yet-confirmed end-phase sighting, held back until it repeats in place. */
	private static BlockPos candidate;
	private static long candidateSinceMillis;
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
			// its own chat line says otherwise. An unconfirmed candidate has nothing
			// worth keeping — start fresh next time it is sighted.
			live = false;
			candidate = null;
			return;
		}

		if (position != null) {
			position = seen;
			live = true;
			return;
		}

		if (phase == Phase.START) {
			// Nothing to wait for yet — shown the instant it is seen.
			DebugLog.line("HIDEYHO", "CONFIRM START pos=" + pos(seen));
			confirm(seen);
			startPosition = seen;
			return;
		}

		if (phase == Phase.PENDING) {
			// It has not moved yet — a sighting here is the START spot again, not
			// something worth tracking. Nothing happens until "No peeking!" arrives.
			return;
		}

		// Phase.END: held back until the position repeats, so a sighting mid-teleport
		// is not mistaken for where it settles. A sighting matching the START spot is
		// discarded outright rather than buffered — it is never where this phase ends.
		if (seen.equals(startPosition)) {
			if (candidate != null) DebugLog.line("HIDEYHO", "DISCARD matches START pos=" + pos(seen));
			candidate = null;
			return;
		}
		// A location inspected earlier may become the real end spot after teleporting.
		// Restore that one candidate until the stable final marker replaces it.
		unchecked.add(seen);

		long now = System.currentTimeMillis();
		if (!seen.equals(candidate)) {
			DebugLog.line("HIDEYHO", "CANDIDATE pos=" + pos(seen)
				+ (candidate == null ? " (first sighting)" : " (was " + pos(candidate) + ")"));
			candidate = seen;
			candidateSinceMillis = now;
			return;
		}
		if (now - candidateSinceMillis < STABLE_MILLIS) return;

		DebugLog.line("HIDEYHO", "CONFIRM END pos=" + pos(candidate)
			+ " (held " + (now - candidateSinceMillis) + "ms)");
		confirm(candidate);
		candidate = null;
	}

	private static void confirm(BlockPos pos) {
		position = pos;
		live = true;
		sparkling = seenSparkling;
		StaticEntityCatalog.learn(NAME, pos);
		unchecked.clear();
		if (alertedThisPhase) return;
		alertedThisPhase = true;
		EncounterAlerts.fireHideyho();
	}

	/** Where it is, or was last seen this phase. {@code null} before anything is confirmed. */
	public static BlockPos position() {
		return position;
	}

	/** Whether it is loaded right now, as opposed to only remembered. */
	public static boolean live() {
		return live;
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
		if (!SafeMode.hideyho() || position != null
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
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (NAME.equals(sighting.critter().name())) {
				loaded.add(sighting.label().blockPosition());
			}
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
			live = false;
			candidate = null;
			alertedThisPhase = false;
			return;
		}

		if (line.contains(HIDE_STARTED)) {
			DebugLog.line("HIDEYHO", "CHAT HIDE_STARTED, " + phase + " -> END raw=\"" + line + "\"");
			phase = Phase.END;
			position = null;
			live = false;
			candidate = null;
			alertedThisPhase = false;
			endPhaseSinceMillis = System.currentTimeMillis();
			restoreCandidates();
			return;
		}

		if (line.contains(CAUGHT)) {
			DebugLog.line("HIDEYHO", "CHAT CAUGHT, " + phase + " -> DONE raw=\"" + line + "\"");
			phase = Phase.DONE;
			position = null;
			live = false;
			candidate = null;
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
		live = false;
		sparkling = false;
		candidate = null;
		alertedThisPhase = false;
		startPosition = null;
		endPhaseSinceMillis = 0;
		restoreCandidates();
	}

	/** A spot from the last run says nothing about this one. */
	public static void reset() {
		clear();
	}

	private static BlockPos fromSightings() {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!NAME.equals(sighting.critter().name())) continue;
			// Hideyho's named entity is Hideyho itself. Generic body pairing can select
			// an unrelated nearby critter and would poison the learned location catalog.
			var target = sighting.label();
			// Under Safe Mode, in range is not enough — the doc above already
			// explains why the entity is loaded well before it is ever seen; this is
			// what stops that gap from being used to find it before it is genuinely
			// visible on the player's own screen.
			boolean sparkling = SparklingWatch.isSparkling(sighting);
			if (SafeMode.hiddenCritter(sighting.critter(), sparkling)
				&& !VisibilityCheck.canSee(target)) continue;
			seenSparkling = sparkling;
			return target.blockPosition();
		}
		return null;
	}

	private static String pos(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
