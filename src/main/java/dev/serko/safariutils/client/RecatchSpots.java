package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
 * Pins a critter's last body position while its capsule attempt is unresolved.
 * Multiple throws may be active; species-only outcomes resolve the oldest matching
 * pin. A failed pin may transfer once to the replacement body used by a rapid retry.
 * {@link #worthPinning(Critter)} excludes attempts that cannot benefit from a recatch
 * position.
 */
public final class RecatchSpots {

	/** Dropped after this long with nothing resolving it, so a stale box cannot linger. */
	private static final long HOLD_MILLIS = 40_000;
	/** A sighting older than this is not what the throw hit. */
	private static final long SIGHTING_MILLIS = 10_000;
	/** Added to the score of anything behind the player, so it always loses to what is not. */
	private static final double BEHIND_PENALTY = 1000.0;
	/** Maximum distance for transferring a short-lived orphaned pity count after an ID change. */
	private static final double PITY_CARRY_DISTANCE = 3.0;
	/** How long an orphaned pity count is still worth claiming before it is just forgotten. */
	private static final long ORPHAN_MILLIS = 10_000;
	/** Covers the small scan-to-chat gap without admitting older unloaded bodies. */
	private static final long CURRENT_TARGET_GRACE_MILLIS = 750;
	/** Long enough to cover the server replacing a thrown-at body with its capsule animation. */
	private static final long CAPTURE_TRANSITION_MILLIS = 10_000;
	/** Briefly retains a resolved throw while its capsule entities finish disappearing. */
	private static final long RESOLVED_TRANSITION_MILLIS = 5_000;

	/** Where an individual's body was last seen, how big it was, its species, and whether it is sparkling. */
	private record Seen(Critter critter, AABB box, boolean sparkling, long millis) {
	}

	/** One active pin: a species, its last known spot, whether it is sparkling, and when that throw landed. */
	private record Pin(Critter critter, AABB box, boolean sparkling, long pinnedAt) {
	}

	/** One pin, for callers outside this class — which species, where, which individual, sparkling or not. */
	public record ActivePin(Critter critter, AABB box, UUID entityId, boolean sparkling) {
	}
	/** Exact aim-selected body for the ATTEMPT event currently being dispatched. */
	public record CatchTarget(Critter critter, UUID entityId, BlockPos position) { }

	/** A pity count with nothing currently pinning it, waiting to see if it gets claimed. */
	private record OrphanedPity(Critter critter, AABB lastBox, int count, long orphanedAt) {
	}

	/** Every currently sighted individual's own last-seen spot, by entity id. */
	private static final Map<UUID, Seen> byEntity = new HashMap<>();
	/** Active pins by entity id, retaining throw order for species-only outcomes. */
	private static final Map<UUID, Pin> pins = new LinkedHashMap<>();
	/**
	 * How many times each individual has actually been thrown at, by entity id — kept
	 * per individual, not per species, so a brand new, never-attempted individual
	 * always reads as zero regardless of another one of the same species having been
	 * thrown at already.
	 */
	private static final Map<UUID, Integer> pity = new HashMap<>();
	/** A pity count whose id just went quiet, kept briefly in case it is the same individual reappearing. */
	private static final Map<UUID, OrphanedPity> orphanedPity = new HashMap<>();
	/** Failed throws retain their marker until the escaped body is visible again. */
	private static final Set<UUID> escapedPins = new HashSet<>();
	/** Outstanding throws by species; bodyless labels during these windows are capsules, not critters. */
	private static final Map<Critter, Deque<Long>> captureTransitions = new HashMap<>();
	/** The sweep these sightings came from, so a cached list is not re-timestamped. */
	private static long lastScan;
	/** Detects a live Eagle setting change so obsolete pity state is removed immediately. */
	private static int lastEagleRarity = Integer.MIN_VALUE;
	private static CatchTarget latestAttemptTarget;

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
			|| pityFor(entityId) >= Markers.pityThreshold(critter.rarity());
	}

	/**
	 * How many times this exact individual has been thrown at — 0 for one never
	 * attempted. Available for any individual this has ever seen a sighting of, not
	 * only one currently pinned, so a hitbox can carry the count before a single
	 * capsule has ever been thrown at it.
	 */
	public static int pityFor(UUID entityId) {
		return pity.getOrDefault(entityId, 0);
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
				// Some capture animations briefly expose a mob-like helper beneath the
				// retained critter label. Never teach that fresh ID as a real body while
				// the throw is unresolved; already-known same-species peers remain valid.
				if (isCaptureArtifact(sighting.critter(), body)) continue;
				AABB box = sighting.body().getBoundingBox();
				byEntity.put(id, new Seen(sighting.critter(), box,
					SparklingWatch.isSparkling(sighting), now));
				// Keep the sighting for capsule-transition identity, as with Commons, but
				// guaranteed catches need no recatch or pity bookkeeping.
				if (CritterCatchRules.guaranteedWithoutPity(sighting.critter())) {
					pity.remove(id);
					pins.remove(id);
					escapedPins.remove(id);
					continue;
				}
				if (escapedPins.remove(id)) {
					pins.remove(id);
					DebugLog.line("RECATCH", "REJOIN " + sighting.critter().name()
						+ " id=" + shortId(id) + " (same body)");
				}

				if (!pity.containsKey(id)) claimOrphanedPity(sighting.critter(), id, box);
			}
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

		orphanedPity.entrySet().removeIf(e -> now - e.getValue().orphanedAt() > ORPHAN_MILLIS);
	}

	/** Drops state that became impossible when the configured Eagle rarity changed. */
	private static void removeGuaranteedState() {
		Set<UUID> guaranteedIds = new HashSet<>();
		byEntity.forEach((id, seen) -> {
			if (CritterCatchRules.guaranteedWithoutPity(seen.critter())) guaranteedIds.add(id);
		});
		pins.forEach((id, pin) -> {
			if (CritterCatchRules.guaranteedWithoutPity(pin.critter())) guaranteedIds.add(id);
		});
		guaranteedIds.forEach(id -> {
			pins.remove(id);
			pity.remove(id);
			escapedPins.remove(id);
		});
		orphanedPity.entrySet().removeIf(entry ->
			CritterCatchRules.guaranteedWithoutPity(entry.getValue().critter()));
	}

	/**
	 * Checks whether a freshly sighted individual with no pity of its own is standing
	 * close to a pity count orphaned by a recent breakout, and if so carries the count
	 * over. See {@link #PITY_CARRY_DISTANCE} for why this exists at all despite the
	 * rest of this class deliberately avoiding anything like it.
	 */
	private static void claimOrphanedPity(Critter critter, UUID newId, AABB box) {
		UUID bestOrphanId = null;
		double bestDistSq = PITY_CARRY_DISTANCE * PITY_CARRY_DISTANCE;
		for (Map.Entry<UUID, OrphanedPity> entry : orphanedPity.entrySet()) {
			OrphanedPity orphan = entry.getValue();
			if (!orphan.critter().equals(critter)) continue;

			// Never steal pity from a genuinely concurrent peer. The shared entity scan
			// is authoritative here: once the original id is absent, a nearby fresh body
			// may claim it immediately instead of waiting on a stale timestamp.
			if (currentlySighted(entry.getKey())) continue;

			double distSq = box.getCenter().distanceToSqr(orphan.lastBox().getCenter());
			if (distSq >= bestDistSq) continue;
			bestDistSq = distSq;
			bestOrphanId = entry.getKey();
		}
		if (bestOrphanId == null) return;
		OrphanedPity claimed = orphanedPity.remove(bestOrphanId);
		Pin previousPin = pins.remove(bestOrphanId);
		// A player can throw again in the few milliseconds between the FAILED line
		// and the escaped body's replacement id arriving. Preserve that unresolved
		// retry while moving its identity; otherwise the catch clears the wrong peer
		// and leaves the original recatch marker behind.
		boolean retryPending = previousPin != null && !escapedPins.contains(bestOrphanId);
		escapedPins.remove(bestOrphanId);
		pity.put(newId, claimed.count());
		if (retryPending) {
			pins.put(newId, new Pin(critter, CritterMarkerGeometry.recatch(critter, box),
				previousPin.sparkling(), previousPin.pinnedAt()));
		}
		DebugLog.line("RECATCH", "PITY-CARRY " + critter.name() + " id " + shortId(bestOrphanId)
			+ " -> " + shortId(newId) + " count=" + claimed.count()
			+ (retryPending ? " retry=pending" : ""));
	}

	private static boolean currentlySighted(UUID entityId) {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (sighting.mob() != null && entityId.equals(sighting.mob().getUUID())) return true;
		}
		return false;
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		// selfName only matters for the entry banner, which is not one of the events
		// this cares about.
		CritterEvent event = ChatParser.parse(line, null);
		if (event == null || event.critter() == null) return;
		// Capture animations may replace a critter's body and label before the
		// result arrives. Tell the Sparkling tracker even when recatch markers are
		// disabled so that replacement cannot become a second detection.
		if (event.type() == CritterEvent.Type.ATTEMPT) {
			latestAttemptTarget = null;
			SparklingWatch.onCaptureInteraction(event.critter());
			captureTransitions.computeIfAbsent(event.critter(), ignored -> new ArrayDeque<>())
				.addLast(System.currentTimeMillis() + CAPTURE_TRANSITION_MILLIS);
		} else if (event.type() == CritterEvent.Type.FAILED
			|| event.type() == CritterEvent.Type.OWN_CATCH
			|| event.type() == CritterEvent.Type.SHARED_CATCH) {
			resolveCaptureTransition(event.critter());
		}
		DebugLog.line("CHAT", event.type() + " " + event.critter().name() + " raw=\"" + line + "\"");

		switch (event.type()) {
			case ATTEMPT -> pin(event.critter(), line.contains("Masterful Critter Capsule"));
			// Resolved, one way or the other — the pin's job was only ever to mark
			// this one throw while it was unresolved. Applied to the most recently
			// pinned individual of this species, the best guess at which one the
			// line is about — see the class doc. Its pity count is set aside as
			// orphaned, not lost, in case the same individual is what turns up next.
			case FAILED -> withOldestPin(event.critter(), (id, pinEntry) -> {
				DebugLog.line("RECATCH", "HOLD " + event.critter().name() + " id=" + shortId(id)
					+ " (awaiting escaped body)");
				escapedPins.add(id);
				orphanPity(id, pinEntry);
			});
			case OWN_CATCH, SHARED_CATCH -> withOldestPin(event.critter(), (id, pinEntry) -> {
				DebugLog.line("RECATCH", "CLEAR " + event.critter().name() + " id=" + shortId(id) + " (caught)");
				pins.remove(id);
				pity.remove(id);
				clearCaughtPity(event.critter(), pinEntry.box());
			});
			default -> {
			}
		}
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
		if (byEntity.containsKey(entityId) || pins.containsKey(entityId)) return false;
		// A newly assigned body of a verified species/type is the real breakout or a
		// concurrently spawned critter. Only unverified proximity matches stay hidden.
		return !CritterEntities.isVerifiedBody(critter, entity);
	}

	private static void resolveCaptureTransition(Critter critter) {
		Deque<Long> expiries = captureTransitions.get(critter);
		if (expiries == null) return;
		expiries.pollFirst();
		expiries.addFirst(System.currentTimeMillis() + RESOLVED_TRANSITION_MILLIS);
	}

	/** Clears replacement IDs belonging to the caught individual, not nearby peers. */
	private static void clearCaughtPity(Critter critter, AABB caughtBox) {
		double limit = PITY_CARRY_DISTANCE * PITY_CARRY_DISTANCE;
		orphanedPity.entrySet().removeIf(entry -> entry.getValue().critter().equals(critter)
			&& entry.getValue().lastBox().getCenter().distanceToSqr(caughtBox.getCenter()) <= limit);
		pity.keySet().removeIf(id -> {
			Seen seen = byEntity.get(id);
			return seen != null && seen.critter().equals(critter)
				&& seen.box().getCenter().distanceToSqr(caughtBox.getCenter()) <= limit;
		});
	}

	/**
	 * Keeps a temporary copy of pity after a breakout. Some species reuse their entity
	 * ID while others return with a new one, so both paths stay valid until reset.
	 */
	private static void orphanPity(UUID id, Pin pin) {
		Integer count = pity.get(id);
		if (count == null) return;
		orphanedPity.put(id, new OrphanedPity(pin.critter(), pin.box(), count, System.currentTimeMillis()));
	}

	private interface PinAction {
		void apply(UUID id, Pin pin);
	}

	/** Resolves the oldest matching pin; chat does not identify simultaneous targets. */
	private static void withOldestPin(Critter critter, PinAction action) {
		UUID oldestId = pendingCatchEntity(critter);
		if (oldestId != null) action.apply(oldestId, pins.get(oldestId));
	}

	/** Best entity match for the next local catch result of this species. */
	public static UUID pendingCatchEntity(Critter critter) {
		UUID oldestId = null;
		Pin oldest = null;
		for (Map.Entry<UUID, Pin> entry : pins.entrySet()) {
			if (!entry.getValue().critter().equals(critter)) continue;
			// FAILED has already resolved that throw. Its pin remains visible only to
			// guide the recatch and must not consume a later catch/failure result.
			if (escapedPins.contains(entry.getKey())) continue;
			if (oldest != null && oldest.pinnedAt() <= entry.getValue().pinnedAt()) continue;
			oldestId = entry.getKey();
			oldest = entry.getValue();
		}
		return oldestId;
	}

	/** Last body position selected for the unresolved throw at this species. */
	public static BlockPos pendingCatchPosition(Critter critter) {
		UUID id = pendingCatchEntity(critter);
		Pin pin = id == null ? null : pins.get(id);
		if (pin == null) return null;
		Vec3 center = pin.box().getCenter();
		return BlockPos.containing(center.x, center.y, center.z);
	}

	/** Target selected for the ATTEMPT line being synchronously dispatched to other trackers. */
	public static CatchTarget latestAttemptTarget(Critter critter) {
		return latestAttemptTarget != null && latestAttemptTarget.critter().equals(critter)
			? latestAttemptTarget : null;
	}

	/**
	 * How far a box is off the line the player is looking along — lower is more likely
	 * to be what a capsule was aimed at.
	 *
	 * <p>Anything behind the player scores by plain distance instead, pushed out beyond
	 * anything in front, so a critter at your back only wins if it is the only one.
	 */
	private static double aimScore(Player player, AABB box) {
		if (player == null) return Double.MAX_VALUE;
		Vec3 toBox = box.getCenter().subtract(player.getEyePosition());
		Vec3 look = player.getViewVector(1.0f);
		double along = toBox.dot(look);
		if (along <= 0) return BEHIND_PENALTY + toBox.length();
		return toBox.subtract(look.scale(along)).length();
	}

	/** Returns whether this critter can escape an ordinary capsule and need a recatch pin. */
	private static boolean worthPinning(Critter critter) {
		return !CritterCatchRules.guaranteedWithoutPity(critter)
			&& !"Hideyho".equals(critter.name());
	}

	private static void pin(Critter critter, boolean masterful) {
		// The nearest-to-aim sighting of the species — several can be in view at
		// once, and only one was actually thrown at. This is a one-time choice made
		// at the moment of the throw, not an ongoing search: nothing tracks this
		// individual by proximity again afterward.
		Player player = Minecraft.getInstance().player;
		Seen best = null;
		UUID bestId = null;
		double bestScore = Double.MAX_VALUE;
		long now = System.currentTimeMillis();
		Set<UUID> currentlySighted = new HashSet<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (critter.equals(sighting.critter()) && sighting.mob() != null) {
				currentlySighted.add(sighting.mob().getUUID());
			}
		}
		boolean haveCurrent = !currentlySighted.isEmpty();
		for (Map.Entry<UUID, Seen> entry : byEntity.entrySet()) {
			Seen seen = entry.getValue();
			if (!critter.equals(seen.critter())) continue;
			// A body in the current shared scan always beats an unloaded/stale sighting.
			// The recent-sighting fallback remains for genuinely distant throws.
			if (haveCurrent && !currentlySighted.contains(entry.getKey())
				&& now - seen.millis() > CURRENT_TARGET_GRACE_MILLIS) continue;
			if (now - seen.millis() > SIGHTING_MILLIS) continue;
			double score = aimScore(player, seen.box());
			if (score >= bestScore) continue;
			bestScore = score;
			best = seen;
			bestId = entry.getKey();
		}

		// A rapid retry may arrive before the escaped body has received its new
		// entity id. Let the retained recatch marker compete by aim direction with
		// live same-species peers, instead of blindly attaching the retry to one of
		// those peers during this short replacement gap.
		UUID escapedBestId = null;
		Pin escapedBest = null;
		for (UUID escapedId : escapedPins) {
			Pin escaped = pins.get(escapedId);
			if (escaped == null || !critter.equals(escaped.critter())) continue;
			double score = aimScore(player, escaped.box());
			if (score >= bestScore) continue;
			bestScore = score;
			escapedBestId = escapedId;
			escapedBest = escaped;
		}
		if (escapedBest != null) {
			Vec3 center = escapedBest.box().getCenter();
			latestAttemptTarget = new CatchTarget(critter, escapedBestId,
				BlockPos.containing(center.x, center.y, center.z));
			if (masterful) {
				DebugLog.line("RECATCH", "SKIP " + critter.name() + " (master capsule, guaranteed catch)");
				return;
			}
			escapedPins.remove(escapedBestId);
			pins.put(escapedBestId, new Pin(critter, escapedBest.box(), escapedBest.sparkling(), now));
			int nowPity = pity.merge(escapedBestId, 1, Integer::sum);
			orphanedPity.put(escapedBestId,
				new OrphanedPity(critter, escapedBest.box(), nowPity, now));
			DebugLog.line("RECATCH", "RETRY " + critter.name() + " id=" + shortId(escapedBestId)
				+ " pos=" + pos(escapedBest.box()) + " pity=" + nowPity
				+ " (escaped body replacement pending)");
			return;
		}

		if (best == null) {
			DebugLog.line("RECATCH", "SKIP " + critter.name() + " (no recent sighting to pin)");
			return;
		}
		Vec3 targetCenter = best.box().getCenter();
		latestAttemptTarget = new CatchTarget(critter, bestId,
			BlockPos.containing(targetCenter.x, targetCenter.y, targetCenter.z));
		if (masterful) {
			DebugLog.line("RECATCH", "SKIP " + critter.name() + " (master capsule, guaranteed catch)");
			return;
		}
		if (!worthPinning(critter)) {
			DebugLog.line("RECATCH", "SKIP " + critter.name() + " (not worth pinning)");
			return;
		}

		pins.put(bestId, new Pin(critter,
			CritterMarkerGeometry.recatch(critter, best.box()), best.sparkling(), now));
		// Claimed here too, not only from the scan loop in tick() — that runs on its
		// own schedule, separately from chat, and a throw fast enough could land before
		// it has caught up to a just-reappeared id. Claiming synchronously right before
		// the increment means the count always starts from the right place regardless
		// of whether that separate pass has run yet.
		if (!pity.containsKey(bestId)) claimOrphanedPity(critter, bestId, best.box());
		int nowPity = pity.merge(bestId, 1, Integer::sum);
		DebugLog.line("RECATCH", "PIN " + critter.name() + " id=" + shortId(bestId) + " pos=" + pos(best.box())
			+ " pity=" + nowPity);
	}

	/** Drops every active pin. */
	public static void clear() {
		pins.clear();
		escapedPins.clear();
	}

	/** Forgotten between runs; a spot from the last one is meaningless in this one. */
	public static void reset() {
		byEntity.clear();
		pity.clear();
		orphanedPity.clear();
		captureTransitions.clear();
		latestAttemptTarget = null;
		lastEagleRarity = ConfigManager.get().display.eagleRarity;
		clear();
	}

	private static String shortId(UUID id) {
		return id == null ? "none" : id.toString().substring(0, 8);
	}

	private static String pos(AABB box) {
		Vec3 c = box.getCenter();
		return "%.1f,%.1f,%.1f".formatted(c.x, c.y, c.z);
	}
}
