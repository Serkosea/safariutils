package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Detects Rockmite mound interaction entities across the loaded Cavern. Candidates
 * must match the measured size range, sit on the floor and a block center, and contain
 * no mob. {@link #describeAll()} provides diagnostics for recalibrating these rules.
 */
public final class MoundSpotter {
	private record Column(int x, int z) {
	}

	/** Fixed decorative interaction columns confirmed not to be Rockmite mounds. */
	private static final Set<Column> FALSE_POSITIVE_COLUMNS = Set.of(
		new Column(-80, 67),
		new Column(-83, 63),
		new Column(-82, 65),
		new Column(-84, 65),
		new Column(-85, 65),
		new Column(-92, 69));

	/** Mounds vary; these bracket the one measured example with room either side. */
	private static final double MIN_WIDTH = 0.35;
	private static final double MAX_WIDTH = 1.10;
	private static final double MIN_HEIGHT = 0.25;
	private static final double MAX_HEIGHT = 0.95;
	/** Added around the tagged-node bounds, since a mound need not sit on a node itself. */
	private static final double MARGIN = 16.0;

	/** Cavern bounds derived from the biome map and cached after first access. */
	private static int[] cavernBounds;
	private static boolean cavernBoundsLoaded;

	/**
	 * Mounds are on the Cavern floor, which is below this. Hitbox size alone is weak
	 * evidence — plenty of props wear a squat interaction box — so anything higher up is
	 * something else, whatever it measures.
	 */
	private static final double MAX_Y = 65.0;

	/** How far off a block's centre the box may sit and still count as placed on it. */
	private static final double CENTRE_TOLERANCE = 0.05;
	/** How close a creature has to be to count as being inside a box rather than near it. */
	private static final double INSIDE_RADIUS = 0.35;
	private static final double INSIDE_HEIGHT = 1.0;

	private MoundSpotter() {
	}

	/** Loads the Cavern bounds once; a failed load is not retried every call. */
	private static int[] loadCavernBounds() {
		if (!cavernBoundsLoaded) {
			cavernBoundsLoaded = true;
			cavernBounds = SafariAreaMap.boundsOf(SafariBiome.CAVERN);
		}
		return cavernBounds;
	}

	/** Whether a position falls within the Cavern's bounds, margin included. */
	private static boolean inCavern(double x, double y, double z) {
		int[] bounds = loadCavernBounds();
		if (bounds == null) return false;
		return x >= bounds[0] - MARGIN && x <= bounds[3] + MARGIN
			&& y >= bounds[1] - MARGIN && y <= bounds[4] + MARGIN
			&& z >= bounds[2] - MARGIN && z <= bounds[5] + MARGIN;
	}

	/** Both the HUD and the waypoints ask every frame; the answer changes far slower. */
	private static final long CACHE_MILLIS = 150;
	/** A missing interaction entity must stay absent before the mound is considered broken. */
	private static final long ABSENCE_CONFIRM_MILLIS = 3_000;
	private static List<BlockPos> cached = List.of();
	private static long cachedAt;

	/** Static mound positions remain known until their disappearance is confirmed. */
	private static final Set<BlockPos> everSeen = new HashSet<>();
	/** Catalog candidates positively matched to a live mound entity this run. */
	private static final Set<BlockPos> detectedLive = new HashSet<>();
	/** Live mounds the player has directly inspected this run. */
	private static final Set<BlockPos> confirmedVisible = new HashSet<>();
	/** Completed positions cannot be re-added by a stale interaction entity. */
	private static final Set<BlockPos> completed = new HashSet<>();
	private static final Map<BlockPos, Long> absentSince = new java.util.HashMap<>();
	/**
	 * Whether any mound at all has ever been detected this run — a mound does not
	 * move, so this alone tells "no mounds spawned this run at all, bad RNG" apart
	 * from "some spawned, and every one of them is already broken".
	 */
	private static boolean everDetectedAny;
	private static int ticks;
	private static String preparedLobby;
	private static boolean lastSafeMode;

	/** Runs a cheap loaded-entity scan even before Cavern is entered. */
	public static void tick() {
		if (!SafariLocation.inSafari()) {
			preparedLobby = null;
			return;
		}
		String lobby = SafariLocation.lobbyId() == null ? "pending" : SafariLocation.lobbyId();
		if (!lobby.equals(preparedLobby)) {
			preparedLobby = lobby;
			reset();
		}
		if (++ticks < 20) return;
		ticks = 0;
		mounds();
	}
	/** Every currently known, unbroken mound position. */
	public static List<BlockPos> mounds() {
		long now = System.currentTimeMillis();
		boolean safeMode = SafeMode.mounds();
		boolean playerInCavern = SafariLocation.biome() == SafariBiome.CAVERN;
		if (safeMode != lastSafeMode) {
			lastSafeMode = safeMode;
			cachedAt = 0;
			if (safeMode) {
				for (BlockPos pos : StaticWaypointCatalog.mounds()) {
					if (!completed.contains(pos)) everSeen.add(pos);
				}
			}
		}
		if (now - cachedAt < CACHE_MILLIS) return cached;
		cachedAt = now;
		// Mode switches only change presentation. Restore unresolved catalog candidates
		// when Safe Mode is enabled instead of requiring a lobby reset.
		if (safeMode) {
			for (BlockPos pos : StaticWaypointCatalog.mounds()) {
				if (!completed.contains(pos)) everSeen.add(pos);
			}
		}

		List<BlockPos> liveNow = scan();
		// Water props can use the same centred interaction hitbox as a mound. A real
		// mound is dry, so discard a remembered candidate once its loaded block proves
		// otherwise instead of retaining that false waypoint for the rest of the run.
		Minecraft client = Minecraft.getInstance();
		if (playerInCavern && client.level != null) {
			for (BlockPos pos : List.copyOf(everSeen)) {
				if (!client.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
					|| isDry(client, pos)) continue;
				// A mound cannot occupy fluid or float directly above it. This is a map
				// validity rule, not hidden run information, so no visual inspection is
				// required before discarding the impossible catalog position.
				everSeen.remove(pos);
				completed.add(pos);
			}
		}
		// Under Safe Mode, a position is only worth remembering once the player has
		// actually, visibly seen it — detection alone (this scan) only proves it is
		// scanned, not seen. Once it clears that bar, though, it is treated exactly
		// like every other confirmed mound: kept and shown regardless of whether the
		// player is currently looking at it, since a mound does not move and this is
		// no longer information the player never had.
		//
		// completed.contains(pos) excluded from both branches — see the doc on
		// completed itself for why this check exists at all.
		int beforeSize = everSeen.size();
		for (BlockPos pos : liveNow) {
			if (completed.contains(pos)) continue;
			detectedLive.add(pos);
			if (VisibilityCheck.canInspectCandidate(pos)) confirmedVisible.add(pos);
			if (everSeen.add(pos)) StaticWaypointCatalog.learnMound(pos);
		}
		Set<BlockPos> liveSet = new HashSet<>(liveNow);
		for (BlockPos pos : List.copyOf(everSeen)) {
			if (liveSet.contains(pos)) {
				absentSince.remove(pos);
				continue;
			}
			// A scan from another biome can discover a loaded mound, but it cannot
			// reliably prove a Cavern location is empty or broken.
			if (!playerInCavern) continue;
			if (client.level == null || !client.level.isLoaded(pos)) continue;
			if (safeMode && VisibilityCheck.canInspectCandidate(pos)) {
				completed.add(pos);
				everSeen.remove(pos);
				absentSince.remove(pos);
				continue;
			}
			long missingAt = absentSince.computeIfAbsent(pos, ignored -> now);
			if (now - missingAt < ABSENCE_CONFIRM_MILLIS) continue;
			// A catalog-only position may be ruled out only by a visible Safe Mode
			// inspection. Normal Mode can complete only a mound confirmed live first.
			if (!detectedLive.contains(pos) && !safeMode) continue;
			if (safeMode) continue;
			completed.add(pos);
			everSeen.remove(pos);
			absentSince.remove(pos);
		}
		// A live scan or newly remembered mound proves this run contained one.
		if (!liveNow.isEmpty() || everSeen.size() > beforeSize) everDetectedAny = true;

		cached = everSeen.stream()
			.filter(pos -> safeMode || detectedLive.contains(pos))
			.toList();
		return cached;
	}

	/** Whether any mound at all has been detected this run, even if all are now broken. */
	public static boolean everDetectedAny() {
		return everDetectedAny;
	}

	public static boolean isConfirmedVisible(BlockPos pos) {
		return confirmedVisible.contains(pos);
	}

	public static void reset() {
		everSeen.clear();
		everSeen.addAll(StaticWaypointCatalog.mounds());
		detectedLive.clear();
		confirmedVisible.clear();
		completed.clear();
		absentSince.clear();
		everDetectedAny = false;
		cached = List.of();
		cachedAt = 0;
		ticks = 0;
		lastSafeMode = SafeMode.mounds();
	}

	/** Uses the local break message as an immediate confirmation when available. */
	public static void onChatMessage(String line) {
		String lower = line.toLowerCase(java.util.Locale.ROOT);
		boolean broke = lower.contains("mound falls apart") || lower.contains("mound fell apart");
		if (!broke) return;
		// The message has no coordinates. Let the next entity scan remove the exact
		// interaction that vanished instead of guessing among nearby mounds.
		cachedAt = 0;
	}

	private static List<BlockPos> scan() {
		Minecraft client = Minecraft.getInstance();
		List<BlockPos> found = new ArrayList<>();
		if (client.level == null || client.player == null) return found;

		List<Entity> candidates = new ArrayList<>();
		List<Entity> creatures = new ArrayList<>();
		Set<BlockPos> catalog = StaticWaypointCatalog.mounds();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!inCavern(entity.getX(), entity.getY(), entity.getZ())) continue;
			if (!EntityTypeIds.is(entity, "interaction")) {
				if (isCreature(entity)) creatures.add(entity);
				continue;
			}
			if (entity.getY() > MAX_Y) continue;
			BlockPos entityPos = entity.blockPosition();
			BlockPos catalogPos = nearestCatalog(entityPos, catalog);
			// Learned spawn positions are stronger evidence than generic hitbox
			// dimensions. This catches valid mounds whose interaction box changes while
			// still retaining the dry-position and creature-wrapper safeguards below.
			if (catalogPos != null && isDry(client, catalogPos) && isDry(client, entityPos)
				&& isBlockCentred(entity)
				&& !FALSE_POSITIVE_COLUMNS.contains(new Column(catalogPos.getX(), catalogPos.getZ()))) {
				candidates.add(entity);
				continue;
			}
			if (!inBand(entity.getBoundingBox().getXsize(), entity.getBoundingBox().getYsize())) continue;
			if (!isBlockCentred(entity)) continue;
			if (!isDry(client, entity.blockPosition())) continue;
			if (FALSE_POSITIVE_COLUMNS.contains(new Column(entity.blockPosition().getX(), entity.blockPosition().getZ()))) continue;
			candidates.add(entity);
		}

		// Resolve the strongest catalog matches first and let each live interaction
		// claim only one position. Without this, two close mounds can both snap to the
		// same catalog entry and breaking either one appears to remove its neighbour.
		candidates.sort(java.util.Comparator.comparingDouble(
			candidate -> nearestCatalogDistance(candidate.blockPosition(), catalog)));
		Set<BlockPos> claimedCatalogPositions = new HashSet<>();
		for (Entity candidate : candidates) {
			// Fish and other creatures can carry a mound-sized interaction box. A
			// learned position strengthens the location match, not the entity match.
			if (wrapsACreature(candidate, creatures)) continue;
			BlockPos catalogPos = nearestCatalog(candidate.blockPosition(), catalog,
				claimedCatalogPositions);
			if (catalogPos != null) claimedCatalogPositions.add(catalogPos);
			found.add(catalogPos == null ? candidate.blockPosition() : catalogPos);
		}
		return found;
	}

	/** Interaction boxes can report the block above or below their learned mound. */
	private static BlockPos nearestCatalog(BlockPos entityPos, Set<BlockPos> catalog) {
		return nearestCatalog(entityPos, catalog, Set.of());
	}

	private static BlockPos nearestCatalog(BlockPos entityPos, Set<BlockPos> catalog,
										   Set<BlockPos> excluded) {
		BlockPos nearest = null;
		double best = 2.26; // Within 1.5 blocks, squared.
		for (BlockPos pos : catalog) {
			if (excluded.contains(pos)) continue;
			double dx = pos.getX() - entityPos.getX();
			double dy = pos.getY() - entityPos.getY();
			double dz = pos.getZ() - entityPos.getZ();
			double distance = dx * dx + dy * dy + dz * dz;
			if (distance >= best) continue;
			best = distance;
			nearest = pos;
		}
		return nearest;
	}

	private static double nearestCatalogDistance(BlockPos entityPos, Set<BlockPos> catalog) {
		BlockPos nearest = nearestCatalog(entityPos, catalog);
		return nearest == null ? Double.MAX_VALUE : nearest.distSqr(entityPos);
	}

	private static boolean isDry(Minecraft client, BlockPos pos) {
		return client.level != null && client.level.getFluidState(pos).isEmpty()
			&& client.level.getFluidState(pos.below()).isEmpty();
	}

	/**
	 * Whether the box is centred on a block.
	 *
	 * <p>A mound is placed on the map, so its box sits at a block's centre every time.
	 * The breakable fish that fall down the waterfalls also come wrapped in interaction
	 * boxes, and a falling entity is at whatever fraction of a block it has reached —
	 * so this separates the two without needing to know what a fish is.
	 */
	private static boolean isBlockCentred(Entity entity) {
		return offsetFromCentre(entity.getX()) < CENTRE_TOLERANCE
			&& offsetFromCentre(entity.getZ()) < CENTRE_TOLERANCE;
	}

	private static double offsetFromCentre(double coordinate) {
		return Math.abs(coordinate - (Math.floor(coordinate) + 0.5));
	}

	/**
	 * Whether the box has a creature inside it.
	 *
	 * <p>The fish are real entities that an interaction box is drawn around; a mound is
	 * only the box. So anything with a mob sitting in it is not a mound — which also
	 * catches a fish that has landed and stopped moving, where being block-centred alone
	 * could be a coincidence.
	 */
	private static boolean wrapsACreature(Entity box, List<Entity> creatures) {
		for (Entity creature : creatures) {
			// Tight enough that a critter walking past a mound does not hide it: the
			// creature has to be essentially inside the box.
			if (Math.abs(creature.getX() - box.getX()) > INSIDE_RADIUS) continue;
			if (Math.abs(creature.getZ() - box.getZ()) > INSIDE_RADIUS) continue;
			if (Math.abs(creature.getY() - box.getY()) > INSIDE_HEIGHT) continue;
			return true;
		}
		return false;
	}

	/** Excludes the scaffolding entities Hypixel builds its props out of. */
	private static boolean isCreature(Entity entity) {
		EntityType<?> type = entity.getType();
		return !EntityTypeIds.is(type, "armor_stand")
			&& !EntityTypeIds.is(type, "item_display")
			&& !EntityTypeIds.is(type, "block_display")
			&& !EntityTypeIds.is(type, "text_display")
			&& !EntityTypeIds.is(type, "player")
			&& !EntityTypeIds.is(type, "item");
	}

	/**
	 * Nearby interaction entities grouped by hitbox size, commonest first.
	 *
	 * <p>Grouped rather than listed one by one: what matters is which sizes exist and
	 * how many of each, since that is what shows where the mounds sit and whether the
	 * band is picking up anything it should not.
	 */
	public static List<String> describeAll() {
		Minecraft client = Minecraft.getInstance();
		List<String> lines = new ArrayList<>();
		if (client.level == null || client.player == null) return lines;

		Map<String, Integer> bySize = new TreeMap<>();
		// Each rule is counted separately rather than filtered out: a size that only ever
		// appears too high up, off a block centre, or wrapped around a mob is exactly
		// what those rules are there to exclude, and seeing that is the point of this.
		Map<String, Integer> lowEnough = new TreeMap<>();
		Map<String, Integer> centred = new TreeMap<>();
		Map<String, Integer> counted = new TreeMap<>();

		List<Entity> creatures = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!EntityTypeIds.is(entity, "interaction") && isCreature(entity)) creatures.add(entity);
		}

		for (Entity entity : client.level.entitiesForRendering()) {
			if (!EntityTypeIds.is(entity, "interaction")) continue;
			if (!inCavern(entity.getX(), entity.getY(), entity.getZ())) continue;
			String size = "%.2f x %.2f".formatted(
				entity.getBoundingBox().getXsize(), entity.getBoundingBox().getYsize());
			bySize.merge(size, 1, Integer::sum);
			if (entity.getY() <= MAX_Y) lowEnough.merge(size, 1, Integer::sum);
			if (isBlockCentred(entity)) centred.merge(size, 1, Integer::sum);
			if (entity.getY() <= MAX_Y && isBlockCentred(entity) && !wrapsACreature(entity, creatures)) {
				counted.merge(size, 1, Integer::sum);
			}
		}

		bySize.entrySet().stream()
			.sorted((a, b) -> b.getValue() - a.getValue())
			.forEach(e -> {
				String[] parts = e.getKey().split(" x ");
				boolean matched = inBand(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
				String note = matched
					? "<- mound-sized: %d counted (low %d, centred %d, of %d)".formatted(
						counted.getOrDefault(e.getKey(), 0), lowEnough.getOrDefault(e.getKey(), 0),
						centred.getOrDefault(e.getKey(), 0), e.getValue())
					: "";
				lines.add("  %-14s x%-3d %s".formatted(e.getKey(), e.getValue(), note));
			});
		return lines;
	}

	private static boolean inBand(double w, double h) {
		return w >= MIN_WIDTH && w <= MAX_WIDTH && h >= MIN_HEIGHT && h <= MAX_HEIGHT && h <= w + 0.1;
	}
}
