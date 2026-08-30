package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
	private static List<Nest> cachedNests = List.of();
	private static String preparedLobby;

	/** A nest and whether it still needs punching. */
	public record Nest(BlockPos pos, boolean unpunched, double distance) {
	}

	private NestTracker() {
	}

	/** Records a punch on a nest. Hooked to the attack event, hence the block check. */
	public static void onAttack(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;
		if (client.level.getBlockState(pos).getBlock() != Blocks.BEE_NEST) return;
		BlockPos immutable = pos.immutable();
		known.add(immutable);
		present.add(immutable);
		StaticWaypointCatalog.learnNest(immutable);
		punched.add(immutable);
		cachedTick = Long.MIN_VALUE;
	}

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
		topUp();
	}

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
		if (tick == cachedTick) return cachedNests;
		cachedTick = tick;
		List<Nest> result = new ArrayList<>();

		for (BlockPos pos : known) {
			// An unloaded chunk reports air, which would read as punched. Only a loaded
			// chunk can say either way, so anything else is left out of the count.
			if (!client.level.isLoaded(pos)) {
				if (!SafeMode.nests() || punched.contains(pos)) continue;
				result.add(new Nest(pos, true, Math.sqrt(client.player.position()
					.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))));
				continue;
			}
			boolean nestPresent = client.level.getBlockState(pos).getBlock() == Blocks.BEE_NEST;
			if (nestPresent) present.add(pos);
			// An absent catalog candidate was never necessarily a nest this run. Normal
			// Mode may clear only a nest it previously confirmed; Safe Mode may also
			// clear a candidate once the player visibly checks its empty location.
			if (!nestPresent && (present.contains(pos)
				|| SafeMode.nests() && VisibilityCheck.canInspectCandidate(pos))) punched.add(pos);
			// Normal detection reports actual blocks, never unverified catalog candidates.
			if (!SafeMode.nests() && !present.contains(pos)) continue;
			boolean unpunched = !punched.contains(pos);
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
		confirmedVisible.clear();
		topupTicks = 0;
		topupActive = false;
		topupFrom = null;
		topupCursor = 0;
		topupTotal = 0;
		cachedTick = Long.MIN_VALUE;
		cachedNests = List.of();
	}
}
