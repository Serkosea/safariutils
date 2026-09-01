package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pins a critter's last body position while its capsule attempt is unresolved.
 * Multiple throws may be active; species-only outcomes resolve the newest matching
 * pin. Pins never follow entity IDs across breakouts. {@link #worthPinning(Critter)}
 * excludes attempts that cannot benefit from a recatch position.
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
	/** Refuse pity transfer while the original ID was seen this recently. */
	private static final long ORPHAN_STILL_LIVE_MILLIS = 1_000;

	/**
	 * Species a pin does nothing for, whatever their rarity. Snoozle used to be here on
	 * the reasoning that it never moves, so a pin marking where it was told you nothing
	 * you could not already see — but it still visibly disappears into the ball during
	 * a capture attempt the same as anything else, and the pin is exactly what marks
	 * that spot while it is gone, not just while it is running.
	 */
	private static final Set<String> NEVER_PINNED =
		Set.of("Hideyho", "Wumpa", "Doomspiral");

	/** Where an individual's body was last seen, how big it was, its species, and whether it is sparkling. */
	private record Seen(Critter critter, AABB box, boolean sparkling, long millis) {
	}

	/** One active pin: a species, its last known spot, whether it is sparkling, and when that throw landed. */
	private record Pin(Critter critter, AABB box, boolean sparkling, long pinnedAt) {
	}

	/** One pin, for callers outside this class — which species, where, which individual, sparkling or not. */
	public record ActivePin(Critter critter, AABB box, UUID entityId, boolean sparkling) {
	}

	/** A pity count with nothing currently pinning it, waiting to see if it gets claimed. */
	private record OrphanedPity(Critter critter, AABB lastBox, int count, long orphanedAt) {
	}

	/** Every currently sighted individual's own last-seen spot, by entity id. */
	private static final Map<UUID, Seen> byEntity = new HashMap<>();
	/** Active pins, by entity id. Insertion-ordered, so "most recently pinned" is cheap. */
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
	/** The sweep these sightings came from, so a cached list is not re-timestamped. */
	private static long lastScan;

	private RecatchSpots() {
	}

	/** Every pin currently active. */
	public static List<ActivePin> active() {
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
		Pin pin = pins.get(entityId);
		if (pin == null) return false;
		// Not pinned for display purposes once its pity has reached the threshold
		// that guarantees this exact throw — a Common is never pinned at all for the
		// same reason, this is just the same fact arrived at individually rather than
		// known in advance. Bookkeeping (pins itself) still holds the entry, so a
		// FAILED or catch line still resolves against it correctly; only the visible
		// pin, and the hitbox suppression that comes with one, is skipped — the normal
		// hitbox takes over instead, showing the same "(2/2)" the pin would have.
		return !isGuaranteed(pin.critter(), entityId);
	}

	/** Whether this individual's pity is already at the threshold that guarantees its next throw. */
	private static boolean isGuaranteed(Critter critter, UUID entityId) {
		return pityFor(entityId) >= Markers.pityThreshold(critter.rarity());
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

		if (CritterEntities.scannedAt() != lastScan) {
			lastScan = CritterEntities.scannedAt();
			for (CritterEntities.Sighting sighting : CritterEntities.all()) {
				// A capsule mid-capture carries the critter's name too, so it turns up as
				// a sighting of that species — and pinning it puts the mark on the ball
				// rather than on the spot the critter will come back to. A real critter
				// has a mob under its name tag; the capsule has nothing.
				if (sighting.mob() == null) continue;
				AABB box = sighting.body().getBoundingBox();
				UUID id = sighting.mob().getUUID();
				byEntity.put(id, new Seen(sighting.critter(), box,
					SparklingWatch.isSparkling(sighting), now));

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
			it.remove();
		}

		orphanedPity.entrySet().removeIf(e -> now - e.getValue().orphanedAt() > ORPHAN_MILLIS);
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

			// A still-visible original entity kept its id, so its pity is not orphaned.
			Seen originalStillSeen = byEntity.get(entry.getKey());
			if (originalStillSeen != null
				&& System.currentTimeMillis() - originalStillSeen.millis() < ORPHAN_STILL_LIVE_MILLIS) {
				continue;
			}

			double distSq = box.getCenter().distanceToSqr(orphan.lastBox().getCenter());
			if (distSq >= bestDistSq) continue;
			bestDistSq = distSq;
			bestOrphanId = entry.getKey();
		}
		if (bestOrphanId == null) return;
		OrphanedPity claimed = orphanedPity.remove(bestOrphanId);
		pity.put(newId, claimed.count());
		DebugLog.line("RECATCH", "PITY-CARRY " + critter.name() + " id " + shortId(bestOrphanId)
			+ " -> " + shortId(newId) + " count=" + claimed.count());
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
			SparklingWatch.onCaptureInteraction(event.critter());
		}
		if (!ConfigManager.get().display.recatchHelper) return;

		DebugLog.line("CHAT", event.type() + " " + event.critter().name() + " raw=\"" + line + "\"");

		// A Masterful Critter Capsule always catches, so a pin for it would never once
		// be used: "You threw a Masterful Critter Capsule at the X!", confirmed
		// directly rather than guessed at.
		if (event.type() == CritterEvent.Type.ATTEMPT && line.contains("Masterful Critter Capsule")) {
			DebugLog.line("RECATCH", "SKIP " + event.critter().name() + " (master capsule, guaranteed catch)");
			return;
		}

		switch (event.type()) {
			case ATTEMPT -> pin(event.critter());
			// Resolved, one way or the other — the pin's job was only ever to mark
			// this one throw while it was unresolved. Applied to the most recently
			// pinned individual of this species, the best guess at which one the
			// line is about — see the class doc. Its pity count is set aside as
			// orphaned, not lost, in case the same individual is what turns up next.
			case FAILED -> withOldestPin(event.critter(), (id, pinEntry) -> {
				DebugLog.line("RECATCH", "CLEAR " + event.critter().name() + " id=" + shortId(id) + " (escaped)");
				pins.remove(id);
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
			if (oldest != null && oldest.pinnedAt() <= entry.getValue().pinnedAt()) continue;
			oldestId = entry.getKey();
			oldest = entry.getValue();
		}
		return oldestId;
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

	/** Returns whether a failed catch can benefit from a saved recatch position. */
	private static boolean worthPinning(Critter critter) {
		if (critter.rarity() == Critter.Rarity.COMMON) return false;
		return !NEVER_PINNED.contains(critter.name());
	}

	private static void pin(Critter critter) {
		if (!worthPinning(critter)) {
			DebugLog.line("RECATCH", "SKIP " + critter.name() + " (not worth pinning)");
			return;
		}

		// The nearest-to-aim sighting of the species — several can be in view at
		// once, and only one was actually thrown at. This is a one-time choice made
		// at the moment of the throw, not an ongoing search: nothing tracks this
		// individual by proximity again afterward.
		Player player = Minecraft.getInstance().player;
		Seen best = null;
		UUID bestId = null;
		double bestScore = Double.MAX_VALUE;
		long now = System.currentTimeMillis();
		for (Map.Entry<UUID, Seen> entry : byEntity.entrySet()) {
			Seen seen = entry.getValue();
			if (!critter.equals(seen.critter())) continue;
			if (now - seen.millis() > SIGHTING_MILLIS) continue;
			double score = aimScore(player, seen.box());
			if (score >= bestScore) continue;
			bestScore = score;
			best = seen;
			bestId = entry.getKey();
		}

		if (best == null) {
			DebugLog.line("RECATCH", "SKIP " + critter.name() + " (no recent sighting to pin)");
			return;
		}

		pins.put(bestId, new Pin(critter, best.box(), best.sparkling(), now));
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
	}

	/** Forgotten between runs; a spot from the last one is meaningless in this one. */
	public static void reset() {
		byEntity.clear();
		pity.clear();
		orphanedPity.clear();
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
