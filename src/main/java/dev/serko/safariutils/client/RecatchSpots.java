package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.CritterSpawnRanges;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders capture transitions and recatch pins for exact attempts selected and
 * resolved by {@link CritterState}. This class owns no target, pity, or outcome logic.
 */
public final class RecatchSpots {

	/** Dropped after this long with nothing resolving it, so a stale box cannot linger. */
	private static final long HOLD_MILLIS = 40_000;
	/** Long enough to cover the server replacing a thrown-at body with its capsule animation. */
	private static final long CAPTURE_TRANSITION_MILLIS = 10_000;
	/** Duration of the server's visual body-to-capsule shrink. */
	private static final long CAPTURE_SHRINK_MILLIS = 550;
	/** Briefly retains a resolved throw while its capsule entities finish disappearing. */
	private static final long RESOLVED_TRANSITION_MILLIS = 5_000;

	/** One active visual pin. */
	private record Pin(Critter critter, AABB box, boolean sparkling, long pinnedAt) {
	}

	/** One pin, for callers outside this class — which species, where, which individual, sparkling or not. */
	public record ActivePin(Critter critter, AABB box, UUID entityId, boolean sparkling) {
	}
	private record CaptureShrink(Critter critter, long startedAt) { }

	/** Body IDs observed before the current capture transition, used to reject scaffolding. */
	private static final Map<UUID, Critter> knownBodies = new HashMap<>();
	/** Active pins by the entity ID supplied by CritterState. */
	private static final Map<UUID, Pin> pins = new LinkedHashMap<>();
	/** Exact identity attempt owning each pin; avoids species-order outcome guesses. */
	private static final Map<Long, UUID> attemptPins = new HashMap<>();
	/** Failed throws retain their marker until the escaped body is visible again. */
	private static final Set<UUID> escapedPins = new HashSet<>();
	/**
	 * Bodies already resolved as caught during this run. The server can retain one for
	 * a few scans while its capture animation finishes; it must never become the target
	 * of a later overlapping throw during that removal grace period.
	 */
	private static final Set<UUID> caughtBodies = new HashSet<>();
	/** Exact attempted bodies whose live hitboxes are shrinking into their capsules. */
	private static final Map<UUID, CaptureShrink> captureShrinks = new HashMap<>();
	/** Outstanding throws by species; bodyless labels during these windows are capsules, not critters. */
	private static final Map<Critter, Deque<Long>> captureTransitions = new HashMap<>();
	/** The sweep these sightings came from, so a cached list is not re-timestamped. */
	private static long lastScan;
	/** Detects a live Eagle setting change so obsolete pity state is removed immediately. */
	private static int lastEagleRarity = Integer.MIN_VALUE;

	private RecatchSpots() {
	}

	/** Every pin currently active. */
	public static List<ActivePin> active() {
		if (!ConfigManager.get().display.recatchHelper) return List.of();
		List<ActivePin> result = new ArrayList<>();
		for (Map.Entry<UUID, Pin> entry : pins.entrySet()) {
			Pin pin = entry.getValue();
			if (isGuaranteed(pin.critter(), entry.getKey())) continue;
			result.add(new ActivePin(pin.critter(), pin.box(), entry.getKey(), pin.sparkling()));
		}
		return result;
	}

	/** Whether this exact individual is currently pinned. */
	public static boolean isPinned(UUID entityId) {
		if (!ConfigManager.get().display.recatchHelper) return false;
		Pin pin = pins.get(entityId);
		if (pin == null) return false;
		// Not pinned for display purposes once its pity has reached the threshold
		// that guarantees this exact throw — a base-guaranteed critter is never pinned for the
		// same reason, this is just the same fact arrived at individually rather than
		// known in advance. Bookkeeping (pins itself) still holds the entry, so a
		// FAILED or catch line still resolves against it correctly; only the visible
		// pin, and the hitbox suppression that comes with one, is skipped — the normal
		// hitbox takes over instead, showing the same "(2/2)" the pin would have.
		return !isGuaranteed(pin.critter(), entityId);
	}

	/** Whether this individual's pity is already at the threshold that guarantees its next throw. */
	private static boolean isGuaranteed(Critter critter, UUID entityId) {
		return CritterCatchRules.guaranteedWithoutPity(critter)
			|| CritterState.pityFor(entityId) >= Markers.pityThreshold(critter.rarity());
	}

	/** The nearest active pin to the player, or {@code null} when nothing is pinned. */
	public static ActivePin nearest() {
		if (!ConfigManager.get().display.recatchHelper) return null;
		Minecraft client = Minecraft.getInstance();
		if (pins.isEmpty() || client.player == null) return null;
		Vec3 playerPos = client.player.position();
		UUID bestId = null;
		Pin best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (Map.Entry<UUID, Pin> entry : pins.entrySet()) {
			if (isGuaranteed(entry.getValue().critter(), entry.getKey())) continue;
			double distSq = entry.getValue().box().getCenter().distanceToSqr(playerPos);
			if (distSq >= bestDistSq) continue;
			bestDistSq = distSq;
			best = entry.getValue();
			bestId = entry.getKey();
		}
		return best == null ? null : new ActivePin(best.critter(), best.box(), bestId, best.sparkling());
	}

	/** Distance from the player to the nearest pin, or -1 when nothing is pinned. */
	public static double distance() {
		Minecraft client = Minecraft.getInstance();
		ActivePin pin = nearest();
		if (pin == null || client.player == null) return -1;
		return client.player.position().distanceTo(pin.box().getCenter());
	}

	public static void tick() {
		long now = System.currentTimeMillis();
		int eagleRarity = ConfigManager.get().display.eagleRarity;
		if (eagleRarity != lastEagleRarity) {
			lastEagleRarity = eagleRarity;
			removeGuaranteedState();
		}
		captureTransitions.values().forEach(expiries -> {
			while (!expiries.isEmpty() && expiries.peekFirst() < now) expiries.removeFirst();
		});
		captureTransitions.entrySet().removeIf(entry -> entry.getValue().isEmpty());

		if (CritterEntities.scannedAt() != lastScan) {
			lastScan = CritterEntities.scannedAt();
			for (CritterEntities.Sighting sighting : CritterEntities.all()) {
				// A capsule mid-capture carries the critter's name too, so it turns up as
				// a sighting of that species — and pinning it puts the mark on the ball
				// rather than on the spot the critter will come back to. A real critter
				// has a mob under its name tag; the capsule has nothing.
				if (sighting.mob() == null) continue;
				Entity body = sighting.mob();
				UUID id = body.getUUID();
				if (caughtBodies.contains(id)) continue;
				// Some capture animations briefly expose a mob-like helper beneath the
				// retained critter label. Never teach that fresh ID as a real body while
				// the throw is unresolved; already-known same-species peers remain valid.
				if (isCaptureArtifact(sighting.critter(), body)) continue;
				knownBodies.put(id, sighting.critter());
				if (escapedPins.remove(id)) {
					pins.remove(id);
					DebugLog.line("RECATCH", "REJOIN " + sighting.critter().name()
						+ " id=" + shortId(id) + " (same body)");
				}
			}
			caughtBodies.removeIf(id -> !CritterState.isCaught(id));
		}

		// Dropped after HOLD_MILLIS with nothing resolving it — the only cleanup a
		// pin gets now, since FAILED and a catch both clear it directly the moment
		// they are heard. This is purely a safety net for a resolution line that,
		// for whatever reason, never arrives.
		Iterator<Map.Entry<UUID, Pin>> it = pins.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pin> entry = it.next();
			Pin pin = entry.getValue();
			if (now - pin.pinnedAt() <= HOLD_MILLIS) continue;
			DebugLog.line("RECATCH", "TIMEOUT " + pin.critter().name() + " id=" + shortId(entry.getKey())
				+ " held " + HOLD_MILLIS + "ms unresolved");
			escapedPins.remove(entry.getKey());
			it.remove();
		}

		captureShrinks.entrySet().removeIf(entry ->
			now - entry.getValue().startedAt() > CAPTURE_SHRINK_MILLIS + 1_000L);
	}

	/** Linear live-hitbox scale for an active capture animation; one otherwise. */
	public static double captureScale(UUID entityId) {
		CaptureShrink shrink = captureShrinks.get(entityId);
		if (shrink == null) return 1.0;
		double progress = (double) (System.currentTimeMillis() - shrink.startedAt())
			/ CAPTURE_SHRINK_MILLIS;
		return Math.max(0.0, 1.0 - Math.min(1.0, progress));
	}

	/** Drops state that became impossible when the configured Eagle rarity changed. */
	private static void removeGuaranteedState() {
		Set<UUID> guaranteedIds = new HashSet<>();
		knownBodies.forEach((id, critter) -> {
			if (CritterCatchRules.guaranteedWithoutPity(critter)) guaranteedIds.add(id);
		});
		pins.forEach((id, pin) -> {
			if (CritterCatchRules.guaranteedWithoutPity(pin.critter())) guaranteedIds.add(id);
		});
		guaranteedIds.forEach(id -> {
			pins.remove(id);
			escapedPins.remove(id);
		});
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		// selfName only matters for the entry banner, which is not one of the events
		// this cares about.
		onChatMessage(line, ChatParser.parse(line, null));
	}

	/** Feeds one cleaned chat line with the shared, already-parsed critter event. */
	public static void onChatMessage(String line, CritterEvent event) {
		if (event == null || event.critter() == null) return;
		DebugLog.line("CHAT", event.type() + " " + event.critter().name() + " raw=\"" + line + "\"");
	}

	/** Starts the capsule transition immediately, even before its target can be bound. */
	static void onCaptureStarted(Critter critter) {
		if (critter == null) return;
		captureTransitions.computeIfAbsent(critter, ignored -> new ArrayDeque<>())
			.addLast(System.currentTimeMillis() + CAPTURE_TRANSITION_MILLIS);
	}

	/** Creates visuals for the exact target selected by the authoritative identity model. */
	static void onIdentityAttempt(long attemptId, Critter critter, UUID entityId,
			AABB box, boolean sparkling, int logicalPity, boolean masterful) {
		if (critter == null || entityId == null || box == null) return;
		long now = System.currentTimeMillis();
		captureShrinks.put(entityId, new CaptureShrink(critter, now));
		attemptPins.put(attemptId, entityId);
		escapedPins.remove(entityId);
		if (!masterful && worthPinning(critter)
			&& logicalPity < Markers.pityThreshold(critter.rarity())) {
			pins.put(entityId, new Pin(critter, CritterMarkerGeometry.recatch(critter, box),
				sparkling, now));
		}
		DebugLog.line("RECATCH", "ATTEMPT A" + attemptId + " " + critter.name()
			+ " id=" + shortId(entityId) + " pity=" + logicalPity
			+ (masterful ? " masterful" : ""));
	}

	/** Applies one resolved logical attempt to the exact pin that throw created. */
	static void onIdentityResolved(long attemptId, Critter critter, UUID originalId,
			UUID currentId, AABB currentBox, boolean sparkling, int logicalPity,
			CritterState.Outcome outcome, boolean visible) {
		resolveCaptureTransition(critter);
		UUID pinnedId = attemptPins.remove(attemptId);
		if (pinnedId == null) pinnedId = originalId;
		Pin pin = pinnedId == null ? null : pins.remove(pinnedId);
		if (pinnedId != null) escapedPins.remove(pinnedId);
		if (outcome == CritterState.Outcome.CAUGHT) {
			if (originalId != null) {
				caughtBodies.add(originalId);
				pins.remove(originalId);
			}
			if (currentId != null) {
				caughtBodies.add(currentId);
				pins.remove(currentId);
			}
			DebugLog.line("RECATCH", "IDENTITY-CLEAR A" + attemptId + " " + critter.name()
				+ " id=" + shortId(pinnedId) + " (caught)");
			return;
		}

		UUID resolvedId = currentId != null ? currentId : originalId != null ? originalId : pinnedId;
		AABB resolvedBox = currentBox != null ? CritterMarkerGeometry.recatch(critter, currentBox)
			: pin != null ? pin.box() : null;
		if (visible) {
			pins.remove(resolvedId);
			escapedPins.remove(resolvedId);
			DebugLog.line("RECATCH", "IDENTITY-REJOIN A" + attemptId + " " + critter.name()
				+ " id=" + shortId(resolvedId) + " pity=" + logicalPity);
		} else if (resolvedBox != null && worthPinning(critter)
			&& logicalPity < Markers.pityThreshold(critter.rarity())) {
			pins.put(resolvedId, new Pin(critter, resolvedBox,
				pin != null ? pin.sparkling() : sparkling, System.currentTimeMillis()));
			escapedPins.add(resolvedId);
			DebugLog.line("RECATCH", "IDENTITY-HOLD A" + attemptId + " " + critter.name()
				+ " id=" + shortId(resolvedId) + " pity=" + logicalPity);
		}
	}

	/** Clears presentation state when an unresolved authoritative attempt ages out. */
	static void onIdentityExpired(long attemptId, Critter critter, UUID originalId) {
		resolveCaptureTransition(critter);
		UUID pinnedId = attemptPins.remove(attemptId);
		if (pinnedId == null) pinnedId = originalId;
		if (pinnedId != null) {
			pins.remove(pinnedId);
			escapedPins.remove(pinnedId);
			captureShrinks.remove(pinnedId);
		}
		DebugLog.line("RECATCH", "IDENTITY-EXPIRE A" + attemptId + " " + critter.name()
			+ " id=" + shortId(pinnedId));
	}

	/** Moves one provisional attempt after capsule-impact evidence identifies its real body. */
	static void correctIdentityTarget(long attemptId, Critter critter, UUID oldId, UUID newId,
			AABB newBox, boolean sparkling, int logicalPity) {
		if (oldId == null || newId == null || oldId.equals(newId)) return;
		UUID pinnedId = attemptPins.get(attemptId);
		Pin oldPin = pins.remove(oldId);
		if (oldId.equals(pinnedId)) {
			attemptPins.put(attemptId, newId);
			if (oldPin != null && worthPinning(critter)
				&& logicalPity < Markers.pityThreshold(critter.rarity())) {
				pins.put(newId, new Pin(critter, CritterMarkerGeometry.recatch(critter, newBox),
					oldPin.sparkling() || sparkling, oldPin.pinnedAt()));
			}
		}
		CaptureShrink shrink = captureShrinks.remove(oldId);
		if (shrink != null) captureShrinks.put(newId, shrink);
		escapedPins.remove(oldId);
		DebugLog.line("RECATCH", "IDENTITY-RETARGET A" + attemptId + " " + critter.name()
			+ " " + shortId(oldId) + " -> " + shortId(newId)
			+ " pity=" + logicalPity);
	}

	/** Whether a bodyless label of this species currently belongs to a thrown capsule. */
	public static boolean captureInProgress(Critter critter) {
		Deque<Long> expiries = captureTransitions.get(critter);
		if (expiries == null) return false;
		long now = System.currentTimeMillis();
		while (!expiries.isEmpty() && expiries.peekFirst() < now) expiries.removeFirst();
		if (expiries.isEmpty()) {
			captureTransitions.remove(critter);
			return false;
		}
		return true;
	}

	/** Whether a fresh body ID appeared only after this species entered capture animation. */
	public static boolean isCaptureArtifact(Critter critter, Entity entity) {
		if (entity == null || !captureInProgress(critter)) return false;
		UUID entityId = entity.getUUID();
		if (knownBodies.containsKey(entityId) || pins.containsKey(entityId)) return false;
		// A newly assigned body of a verified species/type is the real breakout or a
		// concurrently spawned critter. Only unverified proximity matches stay hidden.
		return !CritterEntities.isVerifiedBody(critter, entity);
	}

	/** Ends provisional caught-body suppression in the same scan that restores it live. */
	static void onCaughtBodyRestored(UUID entityId) {
		if (entityId != null) caughtBodies.remove(entityId);
	}

	private static void resolveCaptureTransition(Critter critter) {
		Deque<Long> expiries = captureTransitions.get(critter);
		if (expiries == null) return;
		expiries.pollFirst();
		expiries.addFirst(System.currentTimeMillis() + RESOLVED_TRANSITION_MILLIS);
	}

	/**
	 * No marker or pity state can remain valid after the run has confirmed every
	 * possible individual of this species caught.
	 */
	public static void onConfirmedCatchTotal(Critter critter, int catches) {
		if (critter == null || catches < CritterSpawnRanges.maximum(critter)) return;
		Set<UUID> speciesIds = new HashSet<>();
		pins.forEach((id, pin) -> {
			if (critter.equals(pin.critter())) speciesIds.add(id);
		});
		int pinCount = speciesIds.size();
		pins.entrySet().removeIf(entry -> critter.equals(entry.getValue().critter()));
		attemptPins.entrySet().removeIf(entry -> speciesIds.contains(entry.getValue()));
		escapedPins.removeAll(speciesIds);
		if (pinCount > 0) {
			DebugLog.line("RECATCH", "MAX-CATCH " + critter.name() + "=" + catches
				+ " clearedPins=" + pinCount);
		}
	}

	/** Returns whether this critter can escape an ordinary capsule and need a recatch pin. */
	private static boolean worthPinning(Critter critter) {
		return !CritterCatchRules.guaranteedWithoutPity(critter)
			&& !"Hideyho".equals(critter.name());
	}

	/** Drops every active pin. */
	public static void clear() {
		pins.clear();
		attemptPins.clear();
		escapedPins.clear();
	}

	/** Forgotten between runs; a spot from the last one is meaningless in this one. */
	public static void reset() {
		knownBodies.clear();
		caughtBodies.clear();
		captureShrinks.clear();
		captureTransitions.clear();
		lastEagleRarity = ConfigManager.get().display.eagleRarity;
		clear();
	}

	private static String shortId(UUID id) {
		return id == null ? "none" : id.toString().substring(0, 8);
	}

}
