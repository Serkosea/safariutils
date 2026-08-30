package dev.serko.safariutils.client;

import dev.serko.safariutils.SafariUtils;
import dev.serko.safariutils.data.SafariBiome;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Maps Safari positions to biomes using a precomputed node table. */
public final class SafariAreaMap {

	private static final String RESOURCE = "/assets/safariutils/safari_areas.txt";
	/** Past this distance from every known node, the player is not on the Safari map. */
	private static final double MAX_NODE_DISTANCE = 40.0;
	/**
	 * Past this distance from every known node, treated as off the mapped island
	 * entirely — Torrhus Canyon, elsewhere — rather than merely outside a specific
	 * biome. Far more generous than {@link #MAX_NODE_DISTANCE}: the node file includes
	 * untagged hub and pathway nodes as well as the four biomes, so this only needs to
	 * catch "nowhere near the island", not pin down which part of it.
	 */
	private static final double MAX_ISLAND_DISTANCE = 150.0;
	/**
	 * How much the Y axis counts toward {@link #biomeAt}'s own nearest-node distance,
	 * relative to X and Z at full weight — see that method's own doc for why this
	 * exists and is not simply 1.0 like every other distance calculation here.
	 */
	private static final double Y_WEIGHT = 0.3;

	private static int[] xs;
	private static int[] ys;
	private static int[] zs;
	private static byte[] areas;
	private static boolean loaded;

	/* biomeAt() and onIsland() are normally queried back-to-back for one player tick. */
	private static long cachedXBits = Long.MIN_VALUE;
	private static long cachedYBits;
	private static long cachedZBits;
	private static SafariBiome cachedBiome;
	private static boolean cachedOnIsland;

	private SafariAreaMap() {
	}

	private static synchronized void load() {
		if (loaded) return;
		loaded = true;

		try (InputStream in = SafariAreaMap.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				SafariUtils.LOGGER.error("Missing {}; position-based biome detection disabled", RESOURCE);
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				var loadedX = new ArrayList<Integer>();
				var loadedY = new ArrayList<Integer>();
				var loadedZ = new ArrayList<Integer>();
				var loadedAreas = new ArrayList<Byte>();
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) continue;
					String[] parts = line.split("\\s+");
					byte area = Byte.parseByte(parts[0]);
					for (int i = 1; i < parts.length; i++) {
						String[] position = parts[i].split(",");
						if (position.length != 3) throw new IOException("Invalid Safari area position: " + parts[i]);
						loadedX.add(Integer.parseInt(position[0]));
						loadedY.add(Integer.parseInt(position[1]));
						loadedZ.add(Integer.parseInt(position[2]));
						loadedAreas.add(area);
					}
				}
				xs = loadedX.stream().mapToInt(Integer::intValue).toArray();
				ys = loadedY.stream().mapToInt(Integer::intValue).toArray();
				zs = loadedZ.stream().mapToInt(Integer::intValue).toArray();
				areas = new byte[loadedAreas.size()];
				for (int i = 0; i < areas.length; i++) areas[i] = loadedAreas.get(i);
				SafariUtils.LOGGER.info("Loaded {} Safari area nodes", xs.length);
			}
		} catch (IOException | RuntimeException e) {
			SafariUtils.LOGGER.error("Could not read {}", RESOURCE, e);
			xs = null;
		}
	}

	/**
	 * Returns the biome at a position, or {@code null} outside named biome areas. Y is
	 * weighted lightly so jumping or falling on a bridge does not change its biome.
	 */
	public static SafariBiome biomeAt(double x, double y, double z) {
		lookup(x, y, z);
		return cachedBiome;
	}

	/** Distance to the nearest known node, for {@code /su debug}. */
	public static double distanceToNearestNode(double x, double y, double z) {
		load();
		if (xs == null || xs.length == 0) return Double.NaN;

		double bestDistanceSq = Double.MAX_VALUE;
		for (int i = 0; i < xs.length; i++) {
			double dx = x - xs[i];
			double dy = y - ys[i];
			double dz = z - zs[i];
			bestDistanceSq = Math.min(bestDistanceSq, dx * dx + dy * dy + dz * dz);
		}
		return Math.sqrt(bestDistanceSq);
	}

	/**
	 * Whether a position is anywhere on the mapped Safari island — hub and connecting
	 * paths included, unlike {@link #biomeAt}, which only matches a named biome.
	 */
	public static boolean onIsland(double x, double y, double z) {
		lookup(x, y, z);
		return cachedOnIsland;
	}

	/** Computes both common position answers in one node traversal and caches that coordinate. */
	private static void lookup(double x, double y, double z) {
		load();
		long xBits = Double.doubleToLongBits(x);
		long yBits = Double.doubleToLongBits(y);
		long zBits = Double.doubleToLongBits(z);
		if (xBits == cachedXBits && yBits == cachedYBits && zBits == cachedZBits) return;

		cachedXBits = xBits;
		cachedYBits = yBits;
		cachedZBits = zBits;
		cachedBiome = null;
		cachedOnIsland = false;
		if (xs == null || xs.length == 0) return;

		int nearestBiomeNode = -1;
		double nearestBiomeDistanceSq = Double.MAX_VALUE;
		double nearestIslandDistanceSq = Double.MAX_VALUE;
		for (int i = 0; i < xs.length; i++) {
			double dx = x - xs[i];
			double dy = y - ys[i];
			double dz = z - zs[i];
			double horizontalSq = dx * dx + dz * dz;
			double islandDistanceSq = horizontalSq + dy * dy;
			if (islandDistanceSq < nearestIslandDistanceSq) {
				nearestIslandDistanceSq = islandDistanceSq;
			}
			double weightedDy = dy * Y_WEIGHT;
			double biomeDistanceSq = horizontalSq + weightedDy * weightedDy;
			if (biomeDistanceSq < nearestBiomeDistanceSq) {
				nearestBiomeDistanceSq = biomeDistanceSq;
				nearestBiomeNode = i;
			}
		}

		cachedOnIsland = nearestIslandDistanceSq <= MAX_ISLAND_DISTANCE * MAX_ISLAND_DISTANCE;
		if (nearestBiomeNode >= 0
			&& nearestBiomeDistanceSq <= MAX_NODE_DISTANCE * MAX_NODE_DISTANCE) {
			cachedBiome = fromIndex(areas[nearestBiomeNode]);
		}
	}

	public static int nodeCount() {
		load();
		return xs == null ? 0 : xs.length;
	}

	/**
	 * The box spanning every node tagged with {@code biome}, or {@code null} if the map
	 * failed to load or has no such node. Rough by design — good enough to bound a
	 * one-time area sweep, not to classify a single position; use {@link #biomeAt} for
	 * that.
	 */
	public static int[] boundsOf(SafariBiome biome) {
		load();
		if (xs == null) return null;

		byte target = toIndex(biome);
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		boolean any = false;

		for (int i = 0; i < xs.length; i++) {
			if (areas[i] != target) continue;
			any = true;
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxY = Math.max(maxY, ys[i]);
			minZ = Math.min(minZ, zs[i]);
			maxZ = Math.max(maxZ, zs[i]);
		}

		return any ? new int[]{minX, minY, minZ, maxX, maxY, maxZ} : null;
	}

	/** The reverse of {@link #fromIndex}, for {@link #boundsOf}. */
	private static byte toIndex(SafariBiome biome) {
		return switch (biome) {
			case FOREST -> 1;
			case CAVERN -> 2;
			case ICY -> 3;
			case HAUNTED -> 4;
		};
	}

	/** Index 0 is the hub and paths; 1-4 are the named biomes. */
	private static SafariBiome fromIndex(byte index) {
		return switch (index) {
			case 1 -> SafariBiome.FOREST;
			case 2 -> SafariBiome.CAVERN;
			case 3 -> SafariBiome.ICY;
			case 4 -> SafariBiome.HAUNTED;
			default -> null;
		};
	}
}
