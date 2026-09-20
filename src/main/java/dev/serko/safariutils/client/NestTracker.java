package dev.serko.safariutils.client;

import dev.serko.safariutils.api.PartyItemSyncProviders;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Finds nearby bee nests without scanning the entire Forest volume. */
public final class NestTracker {

	/** How often the incremental scan samples the loaded area around the player. */
	private static final int TOPUP_INTERVAL_TICKS = 300;
	private static final int TOPUP_RADIUS = 20;
	private static final int TOPUP_HEIGHT = 12;
	private static final int TOPUP_BLOCKS_PER_TICK = 2_000;
	private static int topupTicks;
	private static boolean topupActive;
	private static BlockPos topupFrom;
	private static long topupCursor;
	private static long topupTotal;

	private static final Set<BlockPos> known = new LinkedHashSet<>();
	/** Candidates whose bee-nest block has actually been loaded and confirmed this run. */
	private static final Set<BlockPos> present = new LinkedHashSet<>();
	private static final Set<BlockPos> punched = new LinkedHashSet<>();
	/** Resolutions supported by direct inspection, local interaction, or trusted sync. */
	private static final Set<BlockPos> safePunched = new LinkedHashSet<>();
	private static final long SPAWN_CONFIRM_WINDOW_MILLIS = 5_000;
	private static final double SPAWN_CONFIRM_RADIUS_SQ = 12.0 * 12.0;
	private static final Map<BlockPos, PendingInteraction> pending = new HashMap<>();
	private static long checkedSightingScan = Long.MIN_VALUE;
	private record PendingInteraction(long startedAt, Set<UUID> existingHoneybugs) { }
	/**
	 * Every nest position ever confirmed genuinely, visually seen by the player,
	 * kept separately and forever — the same "seen once, known forever" principle
	 * mounds already use. Grown and checked in {@link #nests}, using
	 * {@link VisibilityCheck#canSeeBeeNest} — a bee nest is a solid block, usually
	 * surrounded by tree foliage, so the raycast needs to try several directions
	 * off its centre rather than only one to reliably succeed.
	 */
	private static final Set<BlockPos> confirmedVisible = new LinkedHashSet<>();
	private static long cachedTick = Long.MIN_VALUE;
	private static long cachedConfigRevision = Long.MIN_VALUE;
	private static List<Nest> cachedNests = List.of();
	private static String preparedLobby;

	/** A nest and whether it still needs punching. */
	public record Nest(BlockPos pos, boolean unpunched, double distance) {
	}

	private NestTracker() {
	}

	/** Starts a confirmation window for either a left- or right-clicked nest. */
	public static void onInteract(BlockPos pos) {
		if (!SafariLocation.inside()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;
		if (client.level.getBlockState(pos).getBlock() != Blocks.BEE_NEST) return;
		BlockPos immutable = pos.immutable();
		known.add(immutable);
		present.add(immutable);
		StaticWaypointCatalog.learnNest(immutable);
		pending.computeIfAbsent(immutable, ignored -> new PendingInteraction(
			System.currentTimeMillis(), currentHoneybugIds()));
		cachedTick = Long.MIN_VALUE;
	}

	public static void tick() {
		if (!SafariLocation.inSafari()) {
			preparedLobby = null;
			return;
		}
		String lobby = SafariLocation.lobbyId() == null ? "pending" : SafariLocation.lobbyId();
		if (!lobby.equals(preparedLobby)) {
			if ("pending".equals(preparedLobby)) {
				preparedLobby = lobby;
				topUp();
				confirmHoneybugSpawns();
				return;
			}
			preparedLobby = lobby;
			reset();
		}
		topUp();
		confirmHoneybugSpawns();
	}

	private static Set<UUID> currentHoneybugIds() {
		Set<UUID> ids = new LinkedHashSet<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all())
			if ("Honeybug".equals(sighting.critter().name())) ids.add(sighting.label().getUUID());
		return ids;
	}

	/** Confirms only a newly appearing Honeybug close to a recently used nest. */
	private static void confirmHoneybugSpawns() {
		if (pending.isEmpty() || checkedSightingScan == CritterEntities.scannedAt()) return;
		checkedSightingScan = CritterEntities.scannedAt();
		long now = System.currentTimeMillis();
		pending.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > SPAWN_CONFIRM_WINDOW_MILLIS);
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!"Honeybug".equals(sighting.critter().name())) continue;
			Map.Entry<BlockPos, PendingInteraction> nearest = null;
			double nearestSq = SPAWN_CONFIRM_RADIUS_SQ;
			for (Map.Entry<BlockPos, PendingInteraction> entry : pending.entrySet()) {
				if (entry.getValue().existingHoneybugs().contains(sighting.label().getUUID())) continue;
				double distanceSq = sighting.body().position().distanceToSqr(
					entry.getKey().getX() + 0.5, entry.getKey().getY() + 0.5, entry.getKey().getZ() + 0.5);
				if (distanceSq < nearestSq) { nearestSq = distanceSq; nearest = entry; }
			}
			if (nearest != null) confirm(nearest.getKey(), true);
		}
	}

	private static void confirm(BlockPos pos, boolean share) {
		BlockPos immutable = pos.immutable();
		known.add(immutable);
		punched.add(immutable);
		safePunched.add(immutable);
		pending.remove(immutable);
		cachedTick = Long.MIN_VALUE;
		DebugLog.line("NEST", "confirmed Honeybug spawn at " + immutable.toShortString());
		if (share) PartyItemSyncProviders.onNestConfirmed(immutable);
	}

	/** Applies a confirmed nest interaction received from an approved private party member. */
	public static void onPartyConfirmed(BlockPos pos) { confirm(pos, false); }

	/** Incrementally scans only the nearby loaded volume. */
	private static void topUp() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;
		if (!topupActive) {
			if (++topupTicks < TOPUP_INTERVAL_TICKS) return;
			topupTicks = 0;
			BlockPos centre = client.player.blockPosition();
			topupFrom = centre.offset(-TOPUP_RADIUS, -TOPUP_HEIGHT, -TOPUP_RADIUS);
			long side = TOPUP_RADIUS * 2L + 1;
			topupTotal = side * side * (TOPUP_HEIGHT * 2L + 1);
			topupCursor = 0;
			topupActive = true;
		}

		int side = TOPUP_RADIUS * 2 + 1;
		int height = TOPUP_HEIGHT * 2 + 1;
		long end = Math.min(topupCursor + TOPUP_BLOCKS_PER_TICK, topupTotal);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (; topupCursor < end; topupCursor++) {
			long index = topupCursor;
			int x = (int) (index % side);
			index /= side;
			int y = (int) (index % height);
			int z = (int) (index / height);
			pos.set(topupFrom.getX() + x, topupFrom.getY() + y, topupFrom.getZ() + z);
			if (!client.level.isLoaded(pos)) continue;
			if (client.level.getBlockState(pos).getBlock() != Blocks.BEE_NEST) continue;
			boolean isNew = known.add(pos.immutable());
			present.add(pos.immutable());
			if (isNew) {
				StaticWaypointCatalog.learnNest(pos.immutable());
				DebugLog.line("NEST", "found " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
					+ " (top-up sweep, " + known.size() + " known so far)");
			}
		}
		if (topupCursor >= topupTotal) topupActive = false;
	}

	/** Every nest found so far, still-to-punch ones first, then by distance. */
	public static List<Nest> nests() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return List.of();
		long tick = client.level.getGameTime();
		long configRevision = ConfigManager.revision();
		if (tick == cachedTick && configRevision == cachedConfigRevision) return cachedNests;
		cachedTick = tick;
		cachedConfigRevision = configRevision;
		List<Nest> result = new ArrayList<>();

		for (BlockPos pos : known) {
			// An unloaded chunk reports air, which would read as punched. Only a loaded
			// chunk can say either way, so anything else is left out of the count.
			if (!client.level.isLoaded(pos)) {
				if (!SafeMode.nests() || safePunched.contains(pos)) continue;
				result.add(new Nest(pos, true, Math.sqrt(client.player.position()
					.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))));
				continue;
			}
			boolean nestPresent = client.level.getBlockState(pos).getBlock() == Blocks.BEE_NEST;
			if (nestPresent) present.add(pos);
			// An absent catalog candidate was never necessarily a nest this run. Normal
			// Mode may clear only a nest it previously confirmed; Safe Mode may also
			// clear a candidate once the player visibly checks its empty location.
			if (!nestPresent) {
				boolean safelyResolved = VisibilityCheck.canInspectCandidate(pos)
					// In synchronized parties an absent, loaded candidate is authoritative.
					|| PartyItemSyncProviders.active();
				if (present.contains(pos) || safelyResolved) punched.add(pos);
				if (safelyResolved) safePunched.add(pos);
			}
			// Normal detection reports actual blocks, never unverified catalog candidates.
			if (!SafeMode.nests() && !present.contains(pos)) continue;
			boolean unpunched = !(SafeMode.nests() ? safePunched : punched).contains(pos);
			// Grown here, once per call, rather than a separate pass of its own —
			// every caller already walks this same list.
			if (!confirmedVisible.contains(pos) && VisibilityCheck.canSeeBeeNest(pos)) {
				confirmedVisible.add(pos);
			}
			double distance = Math.sqrt(client.player.position()
				.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
			result.add(new Nest(pos, unpunched, distance));
		}
		result.sort((a, b) -> a.unpunched() != b.unpunched()
			? Boolean.compare(!a.unpunched(), !b.unpunched())
			: Double.compare(a.distance(), b.distance()));
		cachedNests = List.copyOf(result);
		return cachedNests;
	}

	/**
	 * Whether {@code pos} has ever been genuinely, visually confirmed — see
	 * {@link #confirmedVisible}'s own doc for what that means and why it exists.
	 */
	public static boolean isConfirmedVisible(BlockPos pos) {
		return confirmedVisible.contains(pos);
	}

	public static long unpunchedCount() {
		return nests().stream().filter(Nest::unpunched).count();
	}

	/** Nests are per-instance, so what was found last run means nothing in this one. */
	public static void reset() {
		known.clear();
		known.addAll(StaticWaypointCatalog.nests());
		present.clear();
		punched.clear();
		safePunched.clear();
		pending.clear();
		checkedSightingScan = Long.MIN_VALUE;
		confirmedVisible.clear();
		topupTicks = 0;
		topupActive = false;
		topupFrom = null;
		topupCursor = 0;
		topupTotal = 0;
		cachedTick = Long.MIN_VALUE;
		cachedConfigRevision = Long.MIN_VALUE;
		cachedNests = List.of();
	}
}
