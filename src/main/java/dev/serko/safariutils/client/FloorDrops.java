package dev.serko.safariutils.client;

import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects floor drops as groups of three string displays. Confirmed drops persist
 * briefly through scan gaps. Interaction only queues a candidate; Hypixel's pickup
 * message completes the oldest pending interaction because it contains no position.
 */
public final class FloorDrops {

	/** Frequent enough that a picked-up drop clears promptly. */
	private static final int SCAN_INTERVAL_TICKS = 5;
	/** Exactly this many string displays make a drop. Fewer is scenery. */
	private static final int STRING_DISPLAYS = 3;
	/** How long a detected drop survives a temporary scan gap. */
	private static final long HOLD_MILLIS = 5_000;

	private static final Map<BlockPos, Long> confirmed = new HashMap<>();
	/** Catalog candidates positively matched to the three-display drop this run. */
	private static final java.util.Set<BlockPos> detectedActual = new java.util.HashSet<>();
	/** Biome resolved from each stationary drop's coordinates, not the player's position. */
	private static final Map<BlockPos, dev.serko.safariutils.data.SafariBiome> confirmedBiome = new HashMap<>();
	/** Completed positions cannot be rediscovered during the same run. */
	private static final java.util.Set<BlockPos> completed = new java.util.HashSet<>();
	/** Safe Mode positions seen at least once; stationary drops remain known afterward. */
	private static final java.util.Set<BlockPos> confirmedVisible = new java.util.HashSet<>();

	/** Positions not to re-confirm yet, keyed to when that stops applying. */
	private static final Map<BlockPos, Long> suppressed = new HashMap<>();
	/** Long enough for the server to confirm a pickup and the displays to actually go. */
	private static final long SUPPRESS_MILLIS = 3_000;
	/** The line Hypixel sends once a pickup has actually gone through. */
	private static final String PICKUP_CONFIRMED = "FLOOR DROP!";
	/** How long a click is still trusted as "waiting on the pickup line" before giving up. */
	private static final long PENDING_MILLIS = 3_000;
	private static int ticks;
	private static String preparedLobby;

	/** Every distinct drop seen this run, grouped by its own biome. */
	private static final Map<dev.serko.safariutils.data.SafariBiome, java.util.Set<BlockPos>> everSeenByBiome
		= new HashMap<>();
	/** Confirmed pickups this run, by biome. */
	private static final Map<dev.serko.safariutils.data.SafariBiome, Integer> collectedByBiome = new HashMap<>();
	/** Biomes the done alert has already fired for this run, so it fires at most once each. */
	private static final java.util.Set<dev.serko.safariutils.data.SafariBiome> doneAlerted = new java.util.HashSet<>();

	/**
	 * Every click still awaiting the chat line that confirms it, oldest first — a
	 * {@link LinkedHashMap} keeps that order automatically, since a repeat click on a
	 * key already present does not move it.
	 */
	private static final Map<BlockPos, Long> pending = new LinkedHashMap<>();
	private static final java.util.EnumMap<dev.serko.safariutils.data.SafariBiome, Long>
		visibilityCheckedAt = new java.util.EnumMap<>(dev.serko.safariutils.data.SafariBiome.class);

	private FloorDrops() {
	}

	public static void tick() {
		prepareLobby();
		if (++ticks >= SCAN_INTERVAL_TICKS) {
			ticks = 0;
			scan();
		}
		checkDoneAlert();
	}

	private static void prepareLobby() {
		if (!SafariLocation.inSafari()) {
			preparedLobby = null;
			return;
		}
		String lobby = SafariLocation.lobbyId() == null ? "pending" : SafariLocation.lobbyId();
		if (lobby.equals(preparedLobby)) return;
		preparedLobby = lobby;
		reset();
	}

	/** Fires once when all known drops are collected or mode objectives no longer need them. */
	private static void checkDoneAlert() {
		SafariSession session = SessionManager.current();
		dev.serko.safariutils.data.SafariBiome biome = SafariLocation.biome();
		if (session == null || biome == null || doneAlerted.contains(biome)) return;
		// Active positions are authoritative in split parties too: a partymate's pickup
		// removes the drop without incrementing this client's personal pickup counter.
		boolean allCollected = everSeen(biome) > 0 && positions(biome).isEmpty();
		boolean objectiveDone = SparklingMode.enabled()
			&& SparklingMode.hideFloorDrops(biome, session);
		if (!allCollected && !objectiveDone) return;
		doneAlerted.add(biome);
		EncounterAlerts.fireFloorDropsDone(biome);
	}

	private static void scan() {
		if (!SafariLocation.inSafari()) {
			confirmed.clear();
			confirmedBiome.clear();
			suppressed.clear();
			pending.clear();
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		Map<BlockPos, Integer> strings = new HashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof Display.ItemDisplay display)) continue;
			if (!isString(display)) continue;
			strings.merge(entity.blockPosition(), 1, Integer::sum);
		}

		long now = System.currentTimeMillis();
		suppressed.values().removeIf(until -> now > until);

		// Keep the marker when a click never receives server confirmation.
		Iterator<Map.Entry<BlockPos, Long>> pendingIt = pending.entrySet().iterator();
		while (pendingIt.hasNext()) {
			Map.Entry<BlockPos, Long> entry = pendingIt.next();
			if (now - entry.getValue() <= PENDING_MILLIS) continue;
			DebugLog.line("FLOOR", "TIMEOUT pos=" + posOf(entry.getKey()) + " (no pickup line within "
				+ PENDING_MILLIS + "ms, treated as a miss)");
			pendingIt.remove();
		}

		for (Map.Entry<BlockPos, Integer> entry : strings.entrySet()) {
			if (entry.getValue() != STRING_DISPLAYS) continue;
			if (suppressed.containsKey(entry.getKey())) continue;
			BlockPos pos = entry.getKey();
			// A live drop is authoritative. This also recovers a catalog candidate that
			// was inspected just before its display entities finished loading.
			completed.remove(pos);
			// Safe Mode controls rendering, not discovery. Keeping those decisions
			// separate prevents visible drops from being missed during a scan.
			confirmed.put(pos, now);
			detectedActual.add(pos);

			// Resolve from the drop's coordinates. Near a border, the player may be
			// standing in a different biome from a loaded drop.
			if (!confirmedBiome.containsKey(pos)) {
				dev.serko.safariutils.data.SafariBiome resolved =
					SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ());
				if (resolved != null) confirmedBiome.put(pos, resolved);
			}
			dev.serko.safariutils.data.SafariBiome resolved = confirmedBiome.get(pos);
			if (resolved != null) {
				everSeenByBiome.computeIfAbsent(resolved, b -> new java.util.HashSet<>()).add(pos);
				StaticWaypointCatalog.learnFloorDrop(resolved, pos);
			}
		}
		// In Safe Mode, a missing drop is only cleared once its empty spot can be seen.
		if (SafeMode.floorDrops()) {
			List<BlockPos> visiblyGone = confirmed.entrySet().stream()
				.map(Map.Entry::getKey)
				// Drop positions sit against the floor; checking above the block avoids
				// the floor itself occluding an otherwise visible empty candidate.
				.filter(pos -> !strings.containsKey(pos))
				.filter(VisibilityCheck::canInspectCandidate)
				.toList();
			confirmed.keySet().removeAll(visiblyGone);
			completed.addAll(visiblyGone);
		} else {
			confirmed.values().removeIf(seen -> now - seen > HOLD_MILLIS);
		}
	}

	/** Records a click on a known drop and waits for Hypixel to confirm the pickup. */
	public static void onInteract(BlockPos pos) {
		BlockPos tracked = confirmed.containsKey(pos) ? pos : confirmed.keySet().stream()
			.min(java.util.Comparator.comparingDouble(candidate -> candidate.distSqr(pos)))
			.filter(candidate -> candidate.distSqr(pos) <= 9.0)
			.orElse(null);
		if (tracked == null) return;
		DebugLog.line("FLOOR", "CLICK pos=" + posOf(tracked));
		pending.put(tracked, System.currentTimeMillis());
	}

	/**
	 * Feeds one cleaned chat line. Clears the longest-waiting pending click once a
	 * pickup line confirms some click went through — see the class doc for why the
	 * oldest one is the best available guess when more than one is waiting.
	 */
	public static void onChatMessage(String line) {
		if (!line.startsWith(PICKUP_CONFIRMED)) return;

		// Counted toward the run's shard income whether or not this pickup could be
		// tied back to a specific click — a Gimmiegold's own floor drop is a Shining
		// Coin, not a shard, so most of the time this simply does not match at all.
		ChatParser.FloorDropShard shard = ChatParser.floorDropShard(line);
		SafariSession session = SessionManager.current();
		if (shard != null && session != null) {
			session.recordFloorDropShard(shard.critter(), shard.amount(), System.currentTimeMillis());
			DebugLog.line("FLOOR", "SHARD " + shard.critter().name() + " x" + shard.amount());
		}

		if (pending.isEmpty()) return;

		Iterator<Map.Entry<BlockPos, Long>> it = pending.entrySet().iterator();
		if (!it.hasNext()) return;
		BlockPos confirmedPos = it.next().getKey();
		it.remove();

		DebugLog.line("FLOOR", "CONFIRMED pos=" + posOf(confirmedPos) + " raw=\"" + line + "\"");
		confirmed.remove(confirmedPos);
		completed.add(confirmedPos);
		suppressed.put(confirmedPos, System.currentTimeMillis() + SUPPRESS_MILLIS);

		// The biome this position was actually detected in, not a fresh lookup —
		// see confirmedBiome's own doc for why the lookup itself is the unreliable
		// part, not this position's own recorded value.
		dev.serko.safariutils.data.SafariBiome biome = confirmedBiome.get(confirmedPos);
		if (biome != null) {
			collectedByBiome.merge(biome, 1, Integer::sum);
			// Fires once, the moment every drop this run has ever seen in this biome
			// is accounted for — everSeen() being 0 as well would just mean nothing
			// has been seen at all, not that a biome is actually cleared, so that
			// case is excluded rather than firing the instant a biome is entered.
			// Alert evaluation runs after scans in tick(), avoiding the transient empty
			// state between removing this pickup and rediscovering other live drops.
		}

		scan();
	}

	/**
	 * The floor drops currently known in {@code biome} — filtered by the biome
	 * each was actually detected in, recorded once at that moment; see
	 * {@link #confirmedBiome}. Not filtered by visibility here anymore — see
	 * {@link Markers.Marker#seeThrough} for why that moved to depth-testing the
	 * rendered box directly, rather than a raycast against this exact position.
	 */
	public static List<BlockPos> positions(dev.serko.safariutils.data.SafariBiome biome) {
		if (SafeMode.floorDrops()) seedSafeModeCandidates();
		pruneVisibleCandidates();
		List<BlockPos> result = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		long tick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
		boolean checkVisibility = visibilityCheckedAt.getOrDefault(biome, Long.MIN_VALUE) != tick;
		if (checkVisibility) visibilityCheckedAt.put(biome, tick);
		for (BlockPos pos : confirmed.keySet()) {
			if (confirmedBiome.get(pos) != biome) continue;
			if (!SafeMode.floorDrops() && !detectedActual.contains(pos)) continue;
			// Grown here, once per call, rather than gated behind an extra pass of
			// its own — every caller already walks this same list, so there is no
			// separate opportunity this would need instead.
			if (checkVisibility && !confirmedVisible.contains(pos) && VisibilityCheck.canSeeSolidBlock(pos)) {
				confirmedVisible.add(pos);
			}
			result.add(pos);
		}
		return result;
	}

	/** Removes catalog positions that the player has visibly checked and found empty. */
	private static void pruneVisibleCandidates() {
		if (!SafeMode.floorDrops()) return;
		long now = System.currentTimeMillis();
		for (BlockPos pos : List.copyOf(confirmed.keySet())) {
			Long seededAt = confirmed.get(pos);
			if (detectedActual.contains(pos) || seededAt == null || now - seededAt < 500
				|| !VisibilityCheck.canInspectCandidate(pos)) continue;
			confirmed.remove(pos);
			completed.add(pos);
		}
	}

	/**
	 * Whether {@code pos} has ever been genuinely, visually confirmed — see
	 * {@link #confirmedVisible}'s own doc for what that means and why it exists.
	 */
	public static boolean isConfirmedVisible(BlockPos pos) {
		return confirmedVisible.contains(pos);
	}

	/** How many floor drops in this biome, this run, are known but not yet collected. */
	public static int remaining(dev.serko.safariutils.data.SafariBiome biome) {
		if (!SafeMode.floorDrops()) return positions(biome).size();
		return Math.max(0, everSeen(biome) - collectedByBiome.getOrDefault(biome, 0));
	}

	/** Returns the remaining count used by the Missing HUD for the active mode. */
	public static int remainingConfirmed(dev.serko.safariutils.data.SafariBiome biome) {
		if (!SafeMode.floorDrops()) {
			// In a split party, this client may enter after teammates collected some drops.
			// Those absent positions were never seen locally, so subtracting this client's
			// collection history double-counts progress. Active detections are the server's
			// current remaining set and already drive the normal-mode waypoints.
			int active = 0;
			for (BlockPos pos : confirmed.keySet()) {
				if (confirmedBiome.get(pos) == biome) active++;
			}
			return active;
		}
		int activeSeen = 0;
		for (BlockPos pos : confirmed.keySet()) {
			if (confirmedBiome.get(pos) == biome && confirmedVisible.contains(pos)) activeSeen++;
		}
		return activeSeen;
	}

	/** How many distinct floor drop positions this run has ever confirmed in this biome. */
	public static int everSeen(dev.serko.safariutils.data.SafariBiome biome) {
		java.util.Set<BlockPos> seen = everSeenByBiome.get(biome);
		return seen == null ? 0 : seen.size();
	}

	public static void reset() {
		confirmed.clear();
		detectedActual.clear();
		confirmedBiome.clear();
		completed.clear();
		confirmedVisible.clear();
		suppressed.clear();
		pending.clear();
		everSeenByBiome.clear();
		collectedByBiome.clear();
		doneAlerted.clear();
		visibilityCheckedAt.clear();
		ticks = SCAN_INTERVAL_TICKS;
		if (SafeMode.floorDrops()) seedSafeModeCandidates();
	}

	/** Shows learned stationary candidates until the player can verify each empty spot. */
	private static void seedSafeModeCandidates() {
		long now = System.currentTimeMillis();
		for (dev.serko.safariutils.data.SafariBiome biome : dev.serko.safariutils.data.SafariBiome.values()) {
			for (BlockPos pos : StaticWaypointCatalog.floorDrops(biome)) {
				if (completed.contains(pos)) continue;
				confirmed.putIfAbsent(pos, now);
				confirmedBiome.put(pos, biome);
			}
		}
	}

	private static String posOf(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static boolean isString(Display.ItemDisplay display) {
		Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
		if (state == null) return false;
		ItemStack stack = state.itemStack();
		return !stack.isEmpty() && stack.getItem().equals(Items.STRING);
	}
}
