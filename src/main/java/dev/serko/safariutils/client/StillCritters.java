package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.CritterSpawnRanges;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;
import dev.serko.safariutils.session.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Catalogs last-detected positions for stationary and concealed critters. Exact
 * attempts and outcomes arrive from {@link CritterState}; this class only maintains
 * world-location knowledge and its presentation state.
 */
public final class StillCritters {

	private static final Set<String> TRACKED = Set.of("Duplico", "Hideonwall", "Hideonfloor", "Bloodbat",
		"Snoozle", "Troodon", "Fluffling");
	private static final Set<String> STATIC_TRACKED = Set.of("Duplico", "Hideonwall", "Hideonfloor",
		"Snoozle", "Fluffling");
	/** Species whose body returns to the same stationary spawn after every breakout. */
	private static final Set<String> STATIC_AFTER_BREAKOUT = Set.of("Snoozle", "Fluffling");
	private static final double TROODON_WALL_DISTANCE = 4.0;
	private static final List<Critter> TRACKED_CRITTERS = TRACKED.stream()
		.map(dev.serko.safariutils.data.Critters::byName)
		.filter(java.util.Objects::nonNull)
		.toList();
	/** How long a mobile, unconfirmed entry can go unseen before it is dropped. */
	private static final long STALE_MILLIS = 20_000;
	/** Safety expiry for an ATTEMPT that never receives a catch or breakout response. */
	private static final long RESOLUTION_MILLIS = 40_000;
	/** Several loaded entity sweeps must agree before a remembered static location is retired. */
	private static final long STATIC_ABSENCE_MILLIS = 1_500;
	/** Stay well inside entity-tracking range before treating a static location as empty. */
	private static final double STATIC_INSPECTION_DISTANCE_SQ = 12.0 * 12.0;
	/**
	 * How close a fresh sighting has to land to an existing entry of the same species
	 * to be treated as that same individual under a new id, not a genuinely different
	 * one standing nearby. Deliberately tight — this is only meant to catch an id
	 * change happening essentially in place, not to guess across any real distance.
	 */
	private static final double SUPERSEDE_DISTANCE = 2.0;

	private record Entry(Critter critter, BlockPos pos, AABB box, boolean sparkling,
			long millis, boolean visiblyConfirmed, boolean persistentThroughWalls) {
	}
	private record LiveStatic(UUID id, BlockPos pos) { }
	/** Exact static-marker ownership keyed by the authoritative attempt ID. */
	private record Resolution(long attemptId, UUID id, BlockPos pos, long millis) { }

	private static final Map<UUID, Entry> remembered = new HashMap<>();
	/** First continuous loaded-and-empty observation for each remembered static critter. */
	private static final Map<UUID, Long> staticAbsenceSince = new HashMap<>();
	private static final Set<UUID> cataloguedIds = new java.util.HashSet<>();
	private static final Map<UUID, Vec3> learningOrigins = new HashMap<>();
	private static final Map<UUID, Integer> learningStableScans = new HashMap<>();
	private static final Set<UUID> movedForLearning = new java.util.HashSet<>();
	/** Consecutive stationary scans before a Duplico pairing may confirm a spawn. */
	private static final Map<UUID, BlockPos> duplicoPairOrigins = new HashMap<>();
	private static final Map<UUID, UUID> duplicoPairLabels = new HashMap<>();
	private static final Map<UUID, Integer> duplicoStableScans = new HashMap<>();
	private static final Set<Critter> catalogClosed = new java.util.HashSet<>();
	private static final Map<Critter, Set<BlockPos>> unchecked = new HashMap<>();
	private static final Map<Critter, java.util.ArrayDeque<Resolution>> resolving = new HashMap<>();
	/** Canonical Hideonwall perches caught during this run; those spawns cannot return. */
	private static final Set<BlockPos> caughtHideonwallPositions = new java.util.HashSet<>();
	/** Other one-use static spawns confirmed caught during this run. */
	private static final Map<Critter, Set<BlockPos>> caughtStaticPositions = new HashMap<>();
	/** Bodies resolved by a catch but still lingering in the client entity list. */
	private static final Set<UUID> suppressedBodies = new java.util.HashSet<>();
	/** Moving Hideon IDs visually confirmed directly or inherited from their concealed body. */
	private static final Set<UUID> confirmedMovingBodies = new java.util.HashSet<>();
	private static long lastScan = Long.MIN_VALUE;
	private static String preparedLobby;

	/** One remembered individual, for the renderer — which one, and where. */
	public record Sighted(UUID id, BlockPos pos, AABB box, boolean sparkling,
			boolean persistentThroughWalls) {
	}
	/** Remembered world evidence that can own an attempt even while its chunk is unloaded. */
	public record AttemptTarget(UUID id, AABB box, boolean sparkling) { }

	private StillCritters() {
	}

	public static void tick() {
		prepareLobby();
		if (!SafariLocation.inSafari()) return;
		long now = System.currentTimeMillis();
		pruneResolutions(now);
		resolveHideonwallCapsules(now);
		Iterator<Map.Entry<UUID, Entry>> staleIterator = remembered.entrySet().iterator();
		while (staleIterator.hasNext()) {
			Map.Entry<UUID, Entry> rememberedEntry = staleIterator.next();
			Entry entry = rememberedEntry.getValue();
			if ("Troodon".equals(entry.critter().name())
				&& !staticTracked(entry.critter(), entry.pos())) {
				DebugLog.line("STILL", "RELEASE Troodon id=" + shortId(rememberedEntry.getKey())
					+ " (corresponding wall broken)");
				staticAbsenceSince.remove(rememberedEntry.getKey());
				staleIterator.remove();
				continue;
			}
			// Static locations remain valid knowledge until their loaded position is
			// positively observed empty. Only mobile Bloodbats use a time-based expiry.
			if (staticTracked(entry.critter(), entry.pos()) || entry.visiblyConfirmed()
				|| now - entry.millis() <= STALE_MILLIS) continue;
			DebugLog.line("STILL", "EXPIRE " + entry.critter().name()
				+ " (unconfirmed " + STALE_MILLIS + "ms)");
			staticAbsenceSince.remove(rememberedEntry.getKey());
			staleIterator.remove();
		}
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;

		List<CritterEntities.Sighting> sightings = CritterEntities.all();
		// Snapshot all paired bodies before processing either one. Otherwise the first
		// nearby critter can evict the second, which then evicts the first on this scan.
		Set<UUID> liveBodies = new HashSet<>();
		for (CritterEntities.Sighting sighting : sightings) {
			if (sighting.mob() != null) liveBodies.add(sighting.mob().getUUID());
		}

		for (CritterEntities.Sighting sighting : sightings) {
			if (!TRACKED.contains(sighting.critter().name())) continue;

			Entity entity = sighting.mob();
			BlockPos observedPos = entity == null
				? sighting.label().blockPosition() : entity.blockPosition();
			// A Hideon sheds its stationary shulker body when disturbed and moves as a
			// silverfish. That mobile form is rendered live and must never become another
			// remembered static spawn.
			if (mobileHideon(sighting, entity)) {
				boolean confirmed = VisibilityCheck.canSee(entity)
					|| remembered.values().stream().anyMatch(entry ->
						entry.critter().equals(sighting.critter()) && entry.visiblyConfirmed()
							&& sameSpawn(entry.pos(), entity.blockPosition()));
				supersedeNearby(sighting.critter(), entity.getUUID(), entity.blockPosition(), liveBodies);
				if (confirmed && confirmedMovingBodies.add(entity.getUUID())) {
					DebugLog.line("STILL", "CONFIRM moving " + sighting.critter().name()
						+ " id=" + shortId(entity.getUUID()));
				}
				continue;
			}
			boolean staticTracked = staticTracked(sighting.critter(), observedPos);
			if ("Troodon".equals(sighting.critter().name()) && !staticTracked) {
				UUID id = entity == null ? sighting.label().getUUID() : entity.getUUID();
				remembered.remove(id);
				staticAbsenceSince.remove(id);
				continue;
			}
			if (staticTracked
				&& RecatchSpots.captureInProgress(sighting.critter())) continue;
			// Capsule animations briefly reuse the critter label without a body. Never
			// turn that moving label into a new static Hideon marker.
			if (entity == null && RecatchSpots.captureInProgress(sighting.critter())) continue;
			if (entity != null && RecatchSpots.isCaptureArtifact(sighting.critter(), entity)) continue;
			if (entity != null && suppressedBodies.contains(entity.getUUID())) continue;
			if (caughtStatic(sighting)) {
				if (entity != null) suppressedBodies.add(entity.getUUID());
				continue;
			}
			learnInitialPosition(sighting, entity);
			if (entity == null) {
				// Duplico always has a persistent interaction body. A label without that
				// body is capture/capsule scaffolding and must never promote a candidate.
				if ("Duplico".equals(sighting.critter().name())) continue;
				// Some dormant critters are label-only. The hidden label establishes that
				// the candidate is real only after the player directly inspects its spot.
				if (TRACKED.contains(sighting.critter().name())) {
					BlockPos pos = sighting.label().blockPosition();
					boolean inspected = unchecked.getOrDefault(sighting.critter(), Set.of()).stream()
						.anyMatch(candidate -> sameSpawn(candidate, pos)
							&& ("Hideonwall".equals(sighting.critter().name())
								? VisibilityCheck.canInspectPaintingCandidate(candidate)
								: VisibilityCheck.canInspectCandidate(candidate)));
					if (inspected) {
						unchecked.getOrDefault(sighting.critter(), Set.of())
							.removeIf(candidate -> sameSpawn(candidate, pos));
						remembered.put(sighting.label().getUUID(), new Entry(sighting.critter(), pos,
							new AABB(pos),
							SparklingWatch.isSparkling(sighting), now, true, true));
					}
				}
				continue;
			}
			UUID id = entity.getUUID();
			BlockPos pos = entity.blockPosition();
			boolean duplico = "Duplico".equals(sighting.critter().name());
			boolean stableDuplicoPair = !duplico || stableDuplicoPair(sighting, entity);
			boolean directlyVisible = duplico
				? stableDuplicoPair && VisibilityCheck.canSeeDecoratedEntity(entity)
				: VisibilityCheck.canSee(entity);
			boolean sparkling = SparklingWatch.isSparkling(sighting);
			if (directlyVisible) unchecked.computeIfAbsent(sighting.critter(),
				ignored -> new java.util.LinkedHashSet<>())
				.removeIf(candidate -> sameSpawn(candidate, pos));

			if (!remembered.containsKey(id)) {
				DebugLog.line("STILL", "REMEMBER " + sighting.critter().name() + " id=" + shortId(id)
					+ " pos=" + pos(pos));
				supersedeNearby(sighting.critter(), id, pos, liveBodies);
			}
			Entry previous = remembered.get(id);
			boolean stationary = entity.getDeltaMovement().lengthSqr() < 1.0e-4;
			boolean persistent = stationary && (directlyVisible
				|| previous != null && previous.persistentThroughWalls() && previous.pos().equals(pos));
			remembered.put(id, new Entry(sighting.critter(), pos, entity.getBoundingBox(), sparkling, now,
				directlyVisible || previous != null && previous.visiblyConfirmed(), persistent));
		}
		pruneMissingStaticCritters(now);
		pruneVisibleEmptyCandidates();
		staticAbsenceSince.keySet().removeIf(id -> !remembered.containsKey(id));
	}

	private static boolean mobileHideon(CritterEntities.Sighting sighting, Entity entity) {
		if (sighting == null || entity == null || !EntityTypeIds.is(entity, "silverfish")) return false;
		String name = sighting.critter().name();
		return "Hideonfloor".equals(name) || "Hideonwall".equals(name);
	}

	private static boolean caughtStatic(CritterEntities.Sighting sighting) {
		BlockPos actual = sighting.mob() != null
			? sighting.mob().blockPosition() : sighting.label().blockPosition();
		if ("Hideonwall".equals(sighting.critter().name())
			&& caughtHideonwallPositions.stream().anyMatch(pos -> sameSpawn(pos, actual))) return true;
		return caughtStaticPositions.getOrDefault(sighting.critter(), Set.of()).stream()
			.anyMatch(pos -> sameSpawn(pos, actual));
	}

	/**
	 * Keeps static sightings while unloaded or while the player is elsewhere. A marker
	 * is retired only after its location is loaded in the correct biome, directly
	 * inspected, and repeatedly absent.
	 */
	private static void pruneMissingStaticCritters(long now) {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client.level == null) return;
		Map<Critter, List<LiveStatic>> liveByCritter = new HashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			BlockPos position = sighting.mob() == null
				? sighting.label().blockPosition() : sighting.mob().blockPosition();
			if (!staticTracked(sighting.critter(), position)) continue;
			Entity body = sighting.mob();
			if (body == null && RecatchSpots.captureInProgress(sighting.critter())) continue;
			if (body != null && RecatchSpots.isCaptureArtifact(sighting.critter(), body)) continue;
			if (body != null && suppressedBodies.contains(body.getUUID())) continue;
			liveByCritter.computeIfAbsent(sighting.critter(), ignored -> new ArrayList<>())
				.add(new LiveStatic(body == null ? sighting.label().getUUID() : body.getUUID(),
					body == null ? sighting.label().blockPosition() : body.blockPosition()));
		}
		Iterator<Map.Entry<UUID, Entry>> iterator = remembered.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Entry> rememberedEntry = iterator.next();
			UUID id = rememberedEntry.getKey();
			Entry entry = rememberedEntry.getValue();
			if (!staticTracked(entry.critter(), entry.pos())) continue;
			if (RecatchSpots.captureInProgress(entry.critter())) {
				staticAbsenceSince.remove(id);
				continue;
			}

			boolean live = liveByCritter.getOrDefault(entry.critter(), List.of()).stream()
				.anyMatch(current -> id.equals(current.id()) || sameSpawn(entry.pos(), current.pos()));
			if (live) {
				staticAbsenceSince.remove(id);
				continue;
			}

			boolean correctBiome = SafariLocation.biome() == entry.critter().biome();
			boolean loaded = correctBiome && client.level.isLoaded(entry.pos());
			boolean nearby = client.player != null && client.player.position()
				.distanceToSqr(Vec3.atCenterOf(entry.pos())) <= STATIC_INSPECTION_DISTANCE_SQ;
			// A loaded, visible chunk can outlive its entity-tracking range. Require the
			// player to be nearby and directly inspect the location in every mode.
			boolean inspected = "Troodon".equals(entry.critter().name())
				? true
				: "Hideonwall".equals(entry.critter().name())
				? VisibilityCheck.canInspectPaintingCandidate(entry.pos())
				: VisibilityCheck.canInspectCandidate(entry.pos());
			if (!loaded || !nearby || !inspected) {
				staticAbsenceSince.remove(id);
				continue;
			}

			long absentSince = staticAbsenceSince.computeIfAbsent(id, ignored -> now);
			if (now - absentSince < STATIC_ABSENCE_MILLIS) continue;
			DebugLog.line("STILL", "REMOVE absent " + entry.critter().name()
				+ " id=" + shortId(id) + " pos=" + pos(entry.pos()));
			if ("Troodon".equals(entry.critter().name())) {
				WallTracker.TROODON.completeNear(entry.pos(), TROODON_WALL_DISTANCE);
			}
			staticAbsenceSince.remove(id);
			iterator.remove();
		}
	}

	/**
	 * A capsule crossing a concealed perch is direct interaction evidence. Checking
	 * its swept path prevents a fast projectile from skipping the small perch between
	 * client ticks; existing critter pairings still decide whether that perch is occupied.
	 */
	private static void resolveHideonwallCapsules(long now) {
		Critter hideonwall = dev.serko.safariutils.data.Critters.byName("Hideonwall");
		if (hideonwall == null || !SafeMode.hiddenCritterCandidates(hideonwall)) return;
		Set<BlockPos> candidates = unchecked.get(hideonwall);
		if (candidates == null || candidates.isEmpty()) return;
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client.level == null) return;

		for (Entity entity : WorldEntities.current()) {
			if (!(entity instanceof Display.ItemDisplay display) || !isCritterCapsule(display)) continue;
			Vec3 previous = new Vec3(entity.xOld, entity.yOld, entity.zOld);
			Vec3 current = entity.position();
			if (previous.distanceToSqr(current) < 1.0e-6) continue;
			for (BlockPos candidate : List.copyOf(candidates)) {
				AABB target = new AABB(candidate).inflate(0.5);
				if (!target.contains(previous) && !target.contains(current)
					&& target.clip(previous, current).isEmpty()) continue;
				resolveHideonwallPerch(hideonwall, candidate, now);
			}
		}
	}

	private static void resolveHideonwallPerch(Critter hideonwall, BlockPos candidate, long now) {
		unchecked.getOrDefault(hideonwall, Set.of()).remove(candidate);
		if (caughtHideonwallPositions.stream().anyMatch(pos -> sameSpawn(pos, candidate))) return;
		// A throw already assigned to this species is resolving a catch, so it should
		// clear the candidate without reviving the body that the attempt just suppressed.
		if (hasResolving(hideonwall)) {
			remembered.entrySet().removeIf(entry -> hideonwall.equals(entry.getValue().critter())
				&& sameSpawn(candidate, entry.getValue().pos()));
			DebugLog.line("STILL", "CAPSULE cleared Hideonwall perch pos=" + pos(candidate));
			return;
		}
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!hideonwall.equals(sighting.critter())) continue;
			Entity body = sighting.mob();
			BlockPos actual = body != null ? body.blockPosition() : sighting.label().blockPosition();
			if (!sameSpawn(candidate, actual) || body != null && suppressedBodies.contains(body.getUUID())) continue;
			UUID id = body != null ? body.getUUID() : sighting.label().getUUID();
			AABB box = body == null ? new AABB(actual) : body.getBoundingBox();
			remembered.put(id, new Entry(hideonwall, actual, box, SparklingWatch.isSparkling(sighting),
				now, true, body == null || body.getDeltaMovement().lengthSqr() < 1.0e-4));
			DebugLog.line("STILL", "CAPSULE confirmed Hideonwall id=" + shortId(id)
				+ " pos=" + pos(actual));
			return;
		}
		DebugLog.line("STILL", "CAPSULE resolved empty Hideonwall perch pos=" + pos(candidate));
	}

	private static boolean isCritterCapsule(Display.ItemDisplay display) {
		Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
		if (state == null) return false;
		ItemStack stack = state.itemStack();
		return !stack.isEmpty() && stack.getHoverName().getString().endsWith("Critter Capsule");
	}

	private static void prepareLobby() {
		if (!SafariLocation.inSafari()) {
			preparedLobby = null;
			return;
		}
		String lobby = SafariLocation.lobbyId() == null ? "pending" : SafariLocation.lobbyId();
		if (lobby.equals(preparedLobby)) return;
		if ("pending".equals(preparedLobby)) {
			preparedLobby = lobby;
			return;
		}
		preparedLobby = lobby;
		reset();
	}

	private static void pruneVisibleEmptyCandidates() {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client.level == null) return;
		for (Critter critter : trackedCritters()) {
			if (!SafeMode.hiddenCritterCandidates(critter)) continue;
			Set<BlockPos> candidates = unchecked.get(critter);
			if (candidates == null || candidates.isEmpty()) continue;
			List<BlockPos> live = CritterEntities.all().stream()
				.filter(sighting -> critter.equals(sighting.critter()))
				.map(sighting -> sighting.mob() != null ? sighting.mob().blockPosition()
					: sighting.label().blockPosition())
				.filter(java.util.Objects::nonNull)
				.toList();
			candidates.removeIf(pos -> client.level.isLoaded(pos)
				&& ("Hideonwall".equals(critter.name())
					? VisibilityCheck.canInspectPaintingCandidate(pos)
					: VisibilityCheck.canInspectCandidate(pos))
				&& live.stream().noneMatch(actual -> sameSpawn(pos, actual)));
		}

	}

	/** Unchecked Safe Mode candidates; a confirmed real location is rendered separately. */
	public static Set<BlockPos> candidatesFor(Critter critter) {
		if (!SafeMode.hiddenCritterCandidates(critter)) return Set.of();
		var session = SessionManager.current();
		if (session != null && session.partyCatches(critter) >= CritterSpawnRanges.maximum(critter)) {
			return Set.of();
		}
		Set<BlockPos> result = new java.util.LinkedHashSet<>(unchecked.getOrDefault(critter, Set.of()));
		if ("Hideonwall".equals(critter.name())) {
			result.removeIf(candidate -> caughtHideonwallPositions.stream()
				.anyMatch(caught -> sameSpawn(caught, candidate)));
		}
		// Suppress the candidate copy only after the real critter is visibly confirmed.
		for (Entry entry : remembered.values()) {
			if (!critter.equals(entry.critter()) || !entry.visiblyConfirmed()) continue;
			result.removeIf(candidate -> sameSpawn(candidate, entry.pos()));
		}
		return Set.copyOf(result);
	}

	/** Clears only unresolved catalog candidates after every possible spawn was caught. */
	public static void onConfirmedCatchTotal(Critter critter, int catches) {
		if (critter == null || catches < CritterSpawnRanges.maximum(critter)) return;
		Set<BlockPos> candidates = unchecked.get(critter);
		int clearedCandidates = candidates == null ? 0 : candidates.size();
		if (candidates != null) candidates.clear();
		Set<UUID> retiredIds = new HashSet<>();
		remembered.entrySet().removeIf(entry -> {
			if (!critter.equals(entry.getValue().critter())) return false;
			retiredIds.add(entry.getKey());
			return true;
		});
		retiredIds.forEach(staticAbsenceSince::remove);
		java.util.ArrayDeque<Resolution> abandoned = resolving.remove(critter);
		if (abandoned != null) {
			for (Resolution resolution : abandoned) {
				if (resolution.id() != null) suppressedBodies.remove(resolution.id());
			}
		}
		if (clearedCandidates > 0 || !retiredIds.isEmpty()) {
			DebugLog.line("STILL", "MAX-CATCH " + critter.name() + "=" + catches
				+ " clearedCandidates=" + clearedCandidates
				+ " retiredMarkers=" + retiredIds.size());
		}
	}

	/**
	 * Replaces a nearby old ID only when its body is absent from the current scan.
	 * Two distinct critters standing together must retain separate remembered entries.
	 */
	private static void supersedeNearby(Critter critter, UUID newId, BlockPos pos, Set<UUID> liveBodies) {
		double distSq = SUPERSEDE_DISTANCE * SUPERSEDE_DISTANCE;
		Iterator<Map.Entry<UUID, Entry>> it = remembered.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Entry> entry = it.next();
			if (entry.getKey().equals(newId)) continue;
			if (liveBodies.contains(entry.getKey())) continue;
			if (!critter.equals(entry.getValue().critter())) continue;
			if (entry.getValue().pos().distSqr(pos) > distSq) continue;

			DebugLog.line("STILL", "SUPERSEDE " + critter.name() + " id " + shortId(entry.getKey())
				+ " -> " + shortId(newId) + " (new sighting within " + SUPERSEDE_DISTANCE + " blocks)");
			it.remove();
		}
	}

	/** Corrects only the oldest matching provisional throw after capsule evidence identifies its body. */
	static void correctResolvingTarget(Critter critter, UUID oldId, UUID newId, BlockPos position) {
		java.util.ArrayDeque<Resolution> attempts = resolving.get(critter);
		if (attempts == null || attempts.isEmpty()) return;
		boolean corrected = false;
		java.util.ArrayDeque<Resolution> updated = new java.util.ArrayDeque<>(attempts.size());
		for (Resolution attempt : attempts) {
			if (!corrected && oldId.equals(attempt.id())) {
				updated.addLast(new Resolution(attempt.attemptId(), newId, position, attempt.millis()));
				corrected = true;
			} else {
				updated.addLast(attempt);
			}
		}
		if (!corrected) return;
		resolving.put(critter, updated);
		if (!isStillResolving(oldId)) suppressedBodies.remove(oldId);
		suppressedBodies.add(newId);
	}

	/** Every remembered individual of {@code critter}. */
	public static List<Sighted> entriesFor(Critter critter) {
		List<Sighted> result = new ArrayList<>();
		for (Map.Entry<UUID, Entry> entry : remembered.entrySet()) {
			if (!critter.equals(entry.getValue().critter())) continue;
			// The remembered marker and live body represent the same individual. Hide
			// both while a capsule attempt is resolving; a failed attempt releases the
			// suppression, while a successful one removes the remembered entry.
			if (suppressedBodies.contains(entry.getKey())) continue;
			if (SafeMode.hiddenCritter(critter, entry.getValue().sparkling())
				&& !entry.getValue().visiblyConfirmed()) continue;
			result.add(new Sighted(entry.getKey(), entry.getValue().pos(), entry.getValue().box(),
				entry.getValue().sparkling(),
				entry.getValue().persistentThroughWalls()));
		}
		return result;
	}

	/**
	 * Strongest remembered static target for a species-only ATTEMPT line. This closes
	 * the entity-range gap without inventing a different far-catch lifecycle.
	 */
	public static AttemptTarget attemptTarget(Critter critter) {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (critter == null || player == null) return null;
		Map.Entry<UUID, Entry> selected = remembered.entrySet().stream()
			.filter(entry -> critter.equals(entry.getValue().critter()))
			.filter(entry -> staticTracked(critter, entry.getValue().pos()))
			.filter(entry -> !suppressedBodies.contains(entry.getKey()))
			.min(java.util.Comparator.comparingDouble(entry -> player.distanceToSqr(
				entry.getValue().pos().getX() + 0.5, entry.getValue().pos().getY() + 0.5,
				entry.getValue().pos().getZ() + 0.5)))
			.orElse(null);
		return selected == null ? null : new AttemptTarget(selected.getKey(),
			selected.getValue().box(), selected.getValue().sparkling());
	}

	public static boolean persistentThroughWalls(UUID id) {
		Entry entry = remembered.get(id);
		return entry != null && entry.persistentThroughWalls();
	}

	public static boolean isVisiblyConfirmed(UUID id) {
		Entry entry = remembered.get(id);
		return entry != null && entry.visiblyConfirmed() || confirmedMovingBodies.contains(id);
	}

	/** Whether a caught body is merely lingering in the client's entity list. */
	public static boolean isResolved(UUID id) {
		return suppressedBodies.contains(id);
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		onChatMessage(line, ChatParser.parse(line, null));
	}

	/** Feeds one cleaned chat line with the shared, already-parsed critter event. */
	public static void onChatMessage(String line, CritterEvent event) {
		if (event == null || event.critter() == null) return;
		if (!TRACKED.contains(event.critter().name())) return;
		if (event.type() == CritterEvent.Type.ATTEMPT) {
			catalogClosed.add(event.critter());
			return;
		}
		if (event.type() != CritterEvent.Type.SHARED_CATCH) return;
		catalogClosed.add(event.critter());
		// Shared catch chat has no individual identity. Retire only an unverified
		// remembered spawn; local attempts are resolved exclusively by CritterState.
		if (remembered.values().stream().anyMatch(entry -> event.critter().equals(entry.critter())
			&& staticTracked(entry.critter(), entry.pos()))) {
			retireSharedStaticCatch(event.critter());
		}
	}

	/** Associates an exact logical attempt with its remembered static marker. */
	static void onIdentityAttempt(long attemptId, Critter critter, UUID entityId, AABB box) {
		if (critter == null || !TRACKED.contains(critter.name())) return;
		catalogClosed.add(critter);
		Entry target = entityId == null ? null : remembered.get(entityId);
		BlockPos targetPos = target != null ? target.pos()
			: box == null ? null : BlockPos.containing(box.getCenter());
		if ("Hideonwall".equals(critter.name()) && targetPos != null) {
			targetPos = canonicalHideonwallPerch(targetPos);
		}
		if (entityId == null && targetPos == null) return;
		resolving.computeIfAbsent(critter, ignored -> new java.util.ArrayDeque<>())
			.addLast(new Resolution(attemptId, entityId, targetPos, System.currentTimeMillis()));
		if (entityId != null) suppressedBodies.add(entityId);
		DebugLog.line("STILL", "ATTEMPT A" + attemptId + " " + critter.name()
			+ " id=" + shortId(entityId));
	}

	/** Applies one exact logical result to its remembered static marker. */
	static void onIdentityResolved(long attemptId, Critter critter, UUID originalId,
			UUID currentId, AABB currentBox, CritterState.Outcome outcome) {
		if (critter == null || !TRACKED.contains(critter.name())) return;
		java.util.ArrayDeque<Resolution> attempts = resolving.get(critter);
		if (attempts == null) return;
		Resolution resolved = null;
		for (var iterator = attempts.iterator(); iterator.hasNext();) {
			Resolution candidate = iterator.next();
			if (candidate.attemptId() != attemptId) continue;
			resolved = candidate;
			iterator.remove();
			break;
		}
		if (attempts.isEmpty()) resolving.remove(critter);
		if (resolved == null) return;

		if (outcome == CritterState.Outcome.BREAKOUT) {
			Entry previous = resolved.id() == null ? null : remembered.remove(resolved.id());
			if (originalId != null && !originalId.equals(resolved.id())) {
				Entry original = remembered.remove(originalId);
				if (previous == null) previous = original;
			}
			if (resolved.id() != null) staticAbsenceSince.remove(resolved.id());
			if (originalId != null) staticAbsenceSince.remove(originalId);
			if (STATIC_AFTER_BREAKOUT.contains(critter.name()) && currentId != null) {
				Entry current = remembered.get(currentId);
				Entry source = current != null ? current : previous;
				BlockPos fixedPos = resolved.pos() != null ? resolved.pos()
					: currentBox == null ? null : BlockPos.containing(currentBox.getCenter());
				if (fixedPos != null && currentBox != null) {
					remembered.put(currentId, new Entry(critter, fixedPos, currentBox,
						source != null && source.sparkling(), System.currentTimeMillis(),
						source == null || source.visiblyConfirmed(),
						source == null || source.persistentThroughWalls()));
				}
			}
			if (resolved.id() != null && !isStillResolving(resolved.id())) {
				suppressedBodies.remove(resolved.id());
			}
			if (currentId != null) suppressedBodies.remove(currentId);
			DebugLog.line("STILL", "IDENTITY-REJOIN A" + attemptId + " " + critter.name()
				+ " id=" + shortId(currentId));
			return;
		}

		UUID resolvedId = currentId != null ? currentId
			: originalId != null ? originalId : resolved.id();
		BlockPos resolvedPos = resolved.pos();
		if (resolvedPos == null && currentBox != null) {
			resolvedPos = BlockPos.containing(currentBox.getCenter());
		}
		if (resolvedId != null) {
			suppressedBodies.add(resolvedId);
			remembered.remove(resolvedId);
		}
		if (resolved.id() != null) remembered.remove(resolved.id());
		if (resolvedPos != null && staticTracked(critter, resolvedPos)) {
			if ("Hideonwall".equals(critter.name())) suppressCaughtHideonwall(critter, resolvedPos);
			else suppressCaughtStatic(critter, resolvedPos);
		}
		DebugLog.line("STILL", "IDENTITY-CAUGHT A" + attemptId + " " + critter.name()
			+ " id=" + shortId(resolvedId));
	}

	/** Releases only the static presentation state owned by an expired attempt. */
	static void onIdentityExpired(long attemptId, Critter critter, UUID originalId) {
		if (critter == null || !TRACKED.contains(critter.name())) return;
		java.util.ArrayDeque<Resolution> attempts = resolving.get(critter);
		if (attempts == null) return;
		UUID resolvedId = originalId;
		for (var iterator = attempts.iterator(); iterator.hasNext();) {
			Resolution candidate = iterator.next();
			if (candidate.attemptId() != attemptId) continue;
			resolvedId = candidate.id() != null ? candidate.id() : resolvedId;
			iterator.remove();
			break;
		}
		if (attempts.isEmpty()) resolving.remove(critter);
		if (resolvedId != null && !isStillResolving(resolvedId)) suppressedBodies.remove(resolvedId);
		DebugLog.line("STILL", "IDENTITY-EXPIRE A" + attemptId + " " + critter.name()
			+ " id=" + shortId(resolvedId));
	}

	private static boolean hasResolving(Critter critter) {
		java.util.ArrayDeque<Resolution> attempts = resolving.get(critter);
		return attempts != null && !attempts.isEmpty();
	}

	private static boolean isStillResolving(UUID id) {
		for (java.util.ArrayDeque<Resolution> attempts : resolving.values()) {
			for (Resolution attempt : attempts) {
				if (id.equals(attempt.id())) return true;
			}
		}
		return false;
	}

	private static void pruneResolutions(long now) {
		Set<UUID> expiredIds = new HashSet<>();
		Iterator<Map.Entry<Critter, java.util.ArrayDeque<Resolution>>> iterator =
			resolving.entrySet().iterator();
		while (iterator.hasNext()) {
			java.util.ArrayDeque<Resolution> attempts = iterator.next().getValue();
			while (!attempts.isEmpty() && now - attempts.peekFirst().millis() > RESOLUTION_MILLIS) {
				Resolution expired = attempts.removeFirst();
				if (expired.id() != null) expiredIds.add(expired.id());
			}
			if (attempts.isEmpty()) iterator.remove();
		}
		for (UUID id : expiredIds) {
			if (!isStillResolving(id)) suppressedBodies.remove(id);
		}
	}

	/** Retires one unverified marker when a partymate catches the same static species. */
	private static void retireSharedStaticCatch(Critter critter) {
		Map.Entry<UUID, Entry> candidate = remembered.entrySet().stream()
			.filter(entry -> critter.equals(entry.getValue().critter()))
			.filter(entry -> !locallyVerified(entry.getKey(), entry.getValue()))
			.min(java.util.Comparator
				.comparingLong((Map.Entry<UUID, Entry> entry) -> entry.getValue().millis())
				.thenComparing(entry -> entry.getKey().toString()))
			.orElse(null);
		if (candidate == null) {
			DebugLog.line("STILL", "KEEP shared " + critter.name()
				+ " (all remembered instances locally verified)");
			return;
		}
		remembered.remove(candidate.getKey());
		staticAbsenceSince.remove(candidate.getKey());
		DebugLog.line("STILL", "SHARED-CATCH retire " + critter.name()
			+ " id=" + shortId(candidate.getKey()) + " pos=" + pos(candidate.getValue().pos()));
	}

	/** Extra Mode protects detected entities; Safe Mode protects only entities currently seen. */
	private static boolean locallyVerified(UUID rememberedId, Entry entry) {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!entry.critter().equals(sighting.critter())) continue;
			Entity body = sighting.mob();
			if (body != null && suppressedBodies.contains(body.getUUID())) continue;
			UUID liveId = body == null ? sighting.label().getUUID() : body.getUUID();
			BlockPos livePos = body == null ? sighting.label().blockPosition() : body.blockPosition();
			if (!rememberedId.equals(liveId) && !sameSpawn(entry.pos(), livePos)) continue;
			if (!SafeMode.hiddenCritter(entry.critter(), entry.sparkling())) return true;
			if (body == null) return VisibilityCheck.canSeeVisibleName(sighting.label());
			return "Duplico".equals(entry.critter().name())
				? VisibilityCheck.canSeeDecoratedEntity(body)
				: VisibilityCheck.canSee(body);
		}
		return false;
	}

	/** Clears every lingering ID at the caught perch and keeps that one-use perch closed for this run. */
	private static void suppressCaughtHideonwall(Critter hideonwall, BlockPos pos) {
		pos = canonicalHideonwallPerch(pos);
		caughtHideonwallPositions.add(pos.immutable());
		BlockPos caughtPos = pos;
		remembered.entrySet().removeIf(entry -> hideonwall.equals(entry.getValue().critter())
			&& sameSpawn(caughtPos, entry.getValue().pos()));
		Set<BlockPos> candidates = unchecked.get(hideonwall);
		if (candidates != null) candidates.removeIf(candidate -> sameSpawn(caughtPos, candidate));
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!hideonwall.equals(sighting.critter())) continue;
			Entity body = sighting.mob();
			BlockPos actual = body != null ? body.blockPosition() : sighting.label().blockPosition();
			if (sameSpawn(caughtPos, actual) && body != null) suppressedBodies.add(body.getUUID());
		}
		DebugLog.line("STILL", "CAUGHT Hideonwall perch closed pos=" + pos(caughtPos));
	}

	/** Removes every transient ID at a caught one-use static spawn. */
	private static void suppressCaughtStatic(Critter critter, BlockPos pos) {
		BlockPos caughtPos = pos.immutable();
		if ("Troodon".equals(critter.name())) {
			WallTracker.TROODON.completeNear(caughtPos, TROODON_WALL_DISTANCE);
		}
		caughtStaticPositions.computeIfAbsent(critter, ignored -> new HashSet<>()).add(caughtPos);
		remembered.entrySet().removeIf(entry -> critter.equals(entry.getValue().critter())
			&& sameSpawn(caughtPos, entry.getValue().pos()));
		staticAbsenceSince.entrySet().removeIf(entry -> !remembered.containsKey(entry.getKey()));
		Set<BlockPos> candidates = unchecked.get(critter);
		if (candidates != null) candidates.removeIf(candidate -> sameSpawn(caughtPos, candidate));
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!critter.equals(sighting.critter()) || sighting.mob() == null) continue;
			if (sameSpawn(caughtPos, sighting.mob().blockPosition())) {
				suppressedBodies.add(sighting.mob().getUUID());
			}
		}
		DebugLog.line("STILL", "CAUGHT " + critter.name() + " spawn closed pos=" + pos(caughtPos));
	}

	/** Normalizes an observed body/label position to its fixed catalog perch. */
	private static BlockPos canonicalHideonwallPerch(BlockPos observed) {
		return StaticEntityCatalog.positions("Hideonwall").stream()
			.filter(candidate -> sameSpawn(candidate, observed))
			.min(java.util.Comparator.comparingDouble(candidate -> candidate.distSqr(observed)))
			.orElse(observed);
	}

	/** A spot from the last run says nothing about this one. */
	public static void reset() {
		remembered.clear();
		staticAbsenceSince.clear();
		cataloguedIds.clear();
		learningOrigins.clear();
		learningStableScans.clear();
		movedForLearning.clear();
		duplicoPairOrigins.clear();
		duplicoPairLabels.clear();
		duplicoStableScans.clear();
		resolving.clear();
		caughtHideonwallPositions.clear();
		caughtStaticPositions.clear();
		suppressedBodies.clear();
		confirmedMovingBodies.clear();
		catalogClosed.clear();
		unchecked.clear();
		for (Critter critter : trackedCritters()) {
			unchecked.put(critter, new java.util.LinkedHashSet<>(StaticEntityCatalog.positions(critter.name())));
		}
		lastScan = Long.MIN_VALUE;
	}

	/** Rejects transient label/body pairings produced during nearby capture effects. */
	private static boolean stableDuplicoPair(CritterEntities.Sighting sighting, Entity entity) {
		UUID bodyId = entity.getUUID();
		BlockPos pos = entity.blockPosition();
		UUID labelId = sighting.label().getUUID();
		BlockPos previousPos = duplicoPairOrigins.put(bodyId, pos);
		UUID previousLabel = duplicoPairLabels.put(bodyId, labelId);
		if (!pos.equals(previousPos) || !labelId.equals(previousLabel)) {
			duplicoStableScans.put(bodyId, 1);
			return false;
		}
		return duplicoStableScans.merge(bodyId, 1, Integer::sum) >= 3;
	}

	private static void learnInitialPosition(CritterEntities.Sighting sighting, Entity entity) {
		if (!"Hideonfloor".equals(sighting.critter().name())) return;
		if (catalogClosed.contains(sighting.critter())) return;
		// Labels and bodies can arrive on different entity scans. A label by itself is
		// not enough to learn a physical spawn location.
		if (entity == null) return;
		UUID id = entity.getUUID();
		if (movedForLearning.contains(id) || cataloguedIds.contains(id)) return;
		Vec3 exact = entity.position();
		Vec3 origin = learningOrigins.putIfAbsent(id, exact);
		if (origin == null) {
			learningStableScans.put(id, 1);
			return;
		}
		// Track from the first observed scan, even before the instance roster settles.
		// A later stopped position after a hit is not evidence of the original spawn.
		if (origin.distanceToSqr(exact) > 1.0 / 256.0
			|| entity.getDeltaMovement().lengthSqr() >= 1.0e-4) {
			movedForLearning.add(id);
			learningStableScans.remove(id);
			DebugLog.line("WAYPOINT", "skip moved entity/" + sighting.critter().name()
				+ " first=" + origin.x + "," + origin.y + "," + origin.z
				+ " now=" + exact.x + "," + exact.y + "," + exact.z);
			return;
		}
		int stable = learningStableScans.merge(id, 1, Integer::sum);
		if (stable < 3 || !ConfigManager.get().advanced.testingSaveLearnedLocations
			|| !SafariPartyWatch.readyForLocationLearning()) return;
		StaticEntityCatalog.learn(sighting.critter().name(), BlockPos.containing(origin));
		cataloguedIds.add(id);
	}

	/** Hidden bodies and their labels can sit a few blocks apart vertically. */
	private static boolean sameSpawn(BlockPos first, BlockPos second) {
		int dx = first.getX() - second.getX();
		int dz = first.getZ() - second.getZ();
		return dx * dx + dz * dz <= 4 && Math.abs(first.getY() - second.getY()) <= 4;
	}

	private static boolean staticTracked(Critter critter, BlockPos position) {
		if (critter == null || position == null) return false;
		if (STATIC_TRACKED.contains(critter.name())) return true;
		if (!"Troodon".equals(critter.name())) return false;
		WallTracker.State state = WallTracker.TROODON.stateNear(position, TROODON_WALL_DISTANCE);
		return state != null && state != WallTracker.State.BROKEN;
	}

	private static List<Critter> trackedCritters() {
		return TRACKED_CRITTERS;
	}

	private static String shortId(UUID id) {
		return id == null ? "none" : id.toString().substring(0, 8);
	}

	private static String pos(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
