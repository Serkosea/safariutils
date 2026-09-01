package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.io.AtomicFiles;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persistent candidate positions for stationary Safari objectives. */
public final class StaticWaypointCatalog {
	private static final int CURRENT_SCHEMA = 2;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type DATA_TYPE = new TypeToken<Data>() { }.getType();
	private static final long SAVE_DELAY_MILLIS = 1_000;
	private static final String BUNDLED = "/assets/safariutils/static-waypoints.json";
	private static Data data;
	private static Data bundled;
	private static boolean dirty;
	private static long dirtyAt;

	private StaticWaypointCatalog() {
	}

	public static Set<BlockPos> floorDrops(SafariBiome biome) {
		return decode(merged(getBundled().floorDrops.get(biome.name()),
			get().floorDrops.get(biome.name())));
	}

	public static Set<BlockPos> nests() {
		return decode(merged(getBundled().nests, get().nests));
	}

	public static Set<BlockPos> mounds() {
		return decode(merged(getBundled().mounds, get().mounds));
	}

	public static void learnFloorDrop(SafariBiome biome, BlockPos pos) {
		if (!canLearn(pos) || SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != biome) return;
		String encoded = encode(pos);
		if (getBundled().floorDrops.getOrDefault(biome.name(), Set.of()).contains(encoded)) return;
		Set<String> positions = get().floorDrops.computeIfAbsent(biome.name(), ignored -> new LinkedHashSet<>());
		if (!positions.add(encoded)) return;
		learned("FloorDrop/" + biome.displayName(), encoded);
	}

	public static void learnNest(BlockPos pos) {
		if (!canLearn(pos) || SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != SafariBiome.FOREST) return;
		String encoded = encode(pos);
		if (getBundled().nests.contains(encoded) || !get().nests.add(encoded)) return;
		learned("Nest", encoded);
	}

	public static void learnMound(BlockPos pos) {
		if (!canLearn(pos)
			|| SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != SafariBiome.CAVERN) return;
		String encoded = encode(pos);
		if (getBundled().mounds.contains(encoded) || !get().mounds.add(encoded)) return;
		learned("Mound", encoded);
	}

	private static boolean canLearn(BlockPos pos) {
		return pos != null && TestingMode.saveLearnedLocations()
			&& SafariLocation.inside() && SafariPartyWatch.confirmedSoloForLearning();
	}

	private static void learned(String type, String encoded) {
		DebugLog.line("WAYPOINT", "learned objective/" + type + " at " + encoded);
		dirty = true;
		dirtyAt = System.currentTimeMillis();
	}

	/** Coalesces a whole scan's discoveries into one small disk write. */
	public static void tick() {
		if (dirty && System.currentTimeMillis() - dirtyAt >= SAVE_DELAY_MILLIS) save();
	}

	public static void shutdown() {
		if (dirty) save();
	}

	private static Data get() {
		if (data != null) return data;
		try {
			if (Files.isRegularFile(SafariPaths.staticWaypoints())) {
				data = GSON.fromJson(Files.readString(SafariPaths.staticWaypoints()), DATA_TYPE);
			}
		} catch (IOException | RuntimeException ignored) {
		}
		if (data == null) data = new Data();
		if (data.floorDrops == null) data.floorDrops = new java.util.LinkedHashMap<>();
		if (data.nests == null) data.nests = new LinkedHashSet<>();
		if (data.mounds == null) data.mounds = new LinkedHashSet<>();
		// Schema 2 rebuilds the mound catalog after water interaction boxes polluted it.
		if (data.schema < CURRENT_SCHEMA) {
			data.mounds.clear();
			data.schema = CURRENT_SCHEMA;
			dirty = true;
			dirtyAt = 0;
		}
		if (sanitize(data)) {
			dirty = true;
			dirtyAt = 0;
		}
		return data;
	}

	private static Data getBundled() {
		if (bundled != null) return bundled;
		try (var stream = StaticWaypointCatalog.class.getResourceAsStream(BUNDLED)) {
			if (stream != null) bundled = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), DATA_TYPE);
		} catch (IOException | RuntimeException ignored) {
		}
		if (bundled == null) bundled = new Data();
		normalize(bundled);
		return bundled;
	}

	private static Set<String> merged(Set<String> first, Set<String> second) {
		Set<String> result = new LinkedHashSet<>();
		if (first != null) result.addAll(first);
		if (second != null) result.addAll(second);
		return result;
	}

	private static void normalize(Data value) {
		if (value.floorDrops == null) value.floorDrops = new java.util.LinkedHashMap<>();
		if (value.nests == null) value.nests = new LinkedHashSet<>();
		if (value.mounds == null) value.mounds = new LinkedHashSet<>();
	}

	private static boolean sanitize(Data value) {
		boolean changed = false;
		for (var entry : value.floorDrops.entrySet()) {
			SafariBiome biome;
			try {
				biome = SafariBiome.valueOf(entry.getKey());
			} catch (IllegalArgumentException invalid) {
				changed |= !entry.getValue().isEmpty();
				entry.getValue().clear();
				continue;
			}
			changed |= entry.getValue().removeIf(encoded -> biomeAt(encoded) != biome);
		}
		changed |= value.nests.removeIf(encoded -> biomeAt(encoded) != SafariBiome.FOREST);
		changed |= value.mounds.removeIf(encoded -> {
			BlockPos pos = decodeOne(encoded);
			return pos == null || biomeAt(encoded) != SafariBiome.CAVERN
				|| pos.getX() == -83 && pos.getZ() == 63;
		});
		return changed;
	}

	private static SafariBiome biomeAt(String encoded) {
		BlockPos pos = decodeOne(encoded);
		return pos == null ? null : SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ());
	}

	private static void save() {
		var path = SafariPaths.staticWaypoints();
		try {
			AtomicFiles.writeString(path, GSON.toJson(get(), DATA_TYPE),
				TestingMode.saveLearnedLocations());
			dirty = false;
		} catch (IOException ignored) {
		}
	}

	private static String encode(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static Set<BlockPos> decode(Set<String> encoded) {
		Set<BlockPos> result = new LinkedHashSet<>();
		for (String value : encoded) {
			BlockPos pos = decodeOne(value);
			if (pos != null) result.add(pos);
		}
		return result;
	}

	private static BlockPos decodeOne(String value) {
		String[] parts = value.split(",", -1);
		if (parts.length != 3) return null;
		try {
			return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static final class Data {
		int schema;
		Map<String, Set<String>> floorDrops = new java.util.LinkedHashMap<>();
		Set<String> nests = new LinkedHashSet<>();
		Set<String> mounds = new LinkedHashSet<>();
	}
}
