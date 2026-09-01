package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.serko.safariutils.io.AtomicFiles;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Bundled master and locally learned spawn positions for initially stationary critters. */
public final class StaticEntityCatalog {
	private static final int CURRENT_SCHEMA = 5;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type DATA_TYPE = new TypeToken<Data>() { }.getType();
	private static final String BUNDLED = "/assets/safariutils/static-entities.json";
	private static final long SAVE_DELAY_MILLIS = 1_000;
	private static Data local;
	private static Data bundled;
	private static boolean dirty;
	private static long dirtyAt;

	private StaticEntityCatalog() {
	}

	public static Set<BlockPos> positions(String critter) {
		Set<String> encoded = new LinkedHashSet<>();
		encoded.addAll(getBundled().positions.getOrDefault(critter, Set.of()));
		encoded.addAll(getLocal().positions.getOrDefault(critter, Set.of()));
		return decode(encoded);
	}

	public static void learn(String critter, BlockPos pos) {
		if (!TestingMode.saveLearnedLocations()
			|| !SafariLocation.inside()
			|| !SafariPartyWatch.confirmedSoloForLearning()) return;
		var species = dev.serko.safariutils.data.Critters.byName(critter);
		if (species == null || SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != species.biome()) return;
		String encoded = encode(pos);
		if (getBundled().positions.getOrDefault(critter, Set.of()).contains(encoded)) return;
		Set<String> positions = getLocal().positions.computeIfAbsent(critter, ignored -> new LinkedHashSet<>());
		if (!positions.add(encoded)) return;
		DebugLog.line("WAYPOINT", "learned entity/" + critter + " at " + encoded);
		dirty = true;
		dirtyAt = System.currentTimeMillis();
	}

	public static void tick() {
		if (dirty && System.currentTimeMillis() - dirtyAt >= SAVE_DELAY_MILLIS) save();
	}

	public static void shutdown() {
		if (dirty) save();
	}

	private static Data getLocal() {
		if (local != null) return local;
		try {
			if (Files.isRegularFile(SafariPaths.staticEntities())) {
				local = GSON.fromJson(Files.readString(SafariPaths.staticEntities()), DATA_TYPE);
			}
		} catch (IOException | RuntimeException ignored) {
		}
		if (local == null) local = new Data();
		normalize(local);
		// Schema 2 discards Hideonfloor positions learned after the dormant critter moved.
		if (local.schema < 2) {
			local.positions.remove("Hideonfloor");
		}
		// Older builds learned the Hideonfloor label, which is exactly one block
		// above its paired shulker body. Schema 5 stores the actual spawn block.
		if (local.schema >= 2 && local.schema < 5) {
			Set<String> old = local.positions.get("Hideonfloor");
			if (old != null) {
				Set<String> corrected = new LinkedHashSet<>();
				for (String encoded : old) {
					BlockPos pos = decodeOne(encoded);
					if (pos != null) corrected.add(encode(pos.below()));
				}
				local.positions.put("Hideonfloor", corrected);
			}
		}
		if (local.schema < CURRENT_SCHEMA) {
			local.schema = CURRENT_SCHEMA;
			dirty = true;
			dirtyAt = 0;
		}
		if (sanitize(local)) {
			dirty = true;
			dirtyAt = 0;
		}
		return local;
	}

	private static Data getBundled() {
		if (bundled != null) return bundled;
		try (var stream = StaticEntityCatalog.class.getResourceAsStream(BUNDLED)) {
			if (stream != null) bundled = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), DATA_TYPE);
		} catch (IOException | RuntimeException ignored) {
		}
		if (bundled == null) bundled = new Data();
		normalize(bundled);
		sanitize(bundled);
		return bundled;
	}

	private static void save() {
		var path = SafariPaths.staticEntities();
		try {
			AtomicFiles.writeString(path, GSON.toJson(getLocal(), DATA_TYPE),
				TestingMode.saveLearnedLocations());
			dirty = false;
		} catch (IOException ignored) {
		}
	}

	private static void normalize(Data data) {
		if (data.positions == null) data.positions = new LinkedHashMap<>();
	}

	private static boolean sanitize(Data data) {
		boolean changed = false;
		Set<BlockPos> otherStaticPositions = new LinkedHashSet<>();
		collectNonHideyhoPositions(data, otherStaticPositions);
		if (data != bundled) collectNonHideyhoPositions(getBundled(), otherStaticPositions);
		for (var entry : data.positions.entrySet()) {
			var species = dev.serko.safariutils.data.Critters.byName(entry.getKey());
			changed |= entry.getValue().removeIf(encoded -> {
				BlockPos pos = decodeOne(encoded);
				if (species == null || pos == null
					|| SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != species.biome()) return true;
				if ("Hideonwall".equals(entry.getKey()) && pos.distSqr(new BlockPos(16, 78, -69)) <= 4.0) return true;
				if ("Duplico".equals(entry.getKey()) && pos.distSqr(new BlockPos(1, 68, -54)) <= 4.0) return true;
				// The upper of the two learned Bloodbat markers around this beam is not a
				// spawn; the lower position remains valid.
				if ("Bloodbat".equals(entry.getKey()) && pos.equals(new BlockPos(-11, 85, -79))) return true;
				if ("Hideyho".equals(entry.getKey()) && pos.distSqr(new BlockPos(-7, 79, -90)) <= 4.0) return true;
				return "Hideyho".equals(entry.getKey()) && (pos.getY() < 68
					|| otherStaticPositions.stream().anyMatch(other -> other.distSqr(pos) <= 5.0));
			});
		}
		return changed;
	}

	private static void collectNonHideyhoPositions(Data data, Set<BlockPos> result) {
		for (var entry : data.positions.entrySet()) {
			if ("Hideyho".equals(entry.getKey())) continue;
			for (String encoded : entry.getValue()) {
				BlockPos pos = decodeOne(encoded);
				if (pos != null) result.add(pos);
			}
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
		Map<String, Set<String>> positions = new LinkedHashMap<>();
	}
}
