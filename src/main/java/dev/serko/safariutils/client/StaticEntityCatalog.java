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
	private static final int CURRENT_SCHEMA = 9;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type DATA_TYPE = new TypeToken<Data>() { }.getType();
	private static final String BUNDLED = "/assets/safariutils/static-entities.json";
	/** Reviewed sightings that occur only while Hideyho transitions, not hiding spots. */
	private static final Set<BlockPos> TRANSIENT_HIDEYHO = Set.of(
		new BlockPos(-21, 79, -52), new BlockPos(1, 78, -83));
	private static final long SAVE_DELAY_MILLIS = 1_000;
	private static Data local;
	private static Data bundled;
	private static boolean dirty;
	private static long dirtyAt;
	private static final Map<String, Set<BlockPos>> positionCache = new LinkedHashMap<>();
	private static java.util.List<Candidate> candidateCache;

	public record Candidate(String critter, BlockPos pos) { }

	private StaticEntityCatalog() {
	}

	public static Set<BlockPos> positions(String critter) {
		return positionCache.computeIfAbsent(critter, name -> {
			Set<String> encoded = new LinkedHashSet<>();
			encoded.addAll(getBundled().positions.getOrDefault(name, Set.of()));
			encoded.addAll(getLocal().positions.getOrDefault(name, Set.of()));
			return Set.copyOf(decode(name, encoded));
		});
	}

	/** Locally learned blocks not in the shipped catalog, cached until a new block arrives. */
	public static java.util.List<Candidate> learnedCandidates() {
		if (candidateCache != null) return candidateCache;
		java.util.List<Candidate> result = new java.util.ArrayList<>();
		Data saved = getLocal();
		Data shipped = getBundled();
		for (var entry : saved.positions.entrySet()) {
			Set<String> existing = shipped.positions.getOrDefault(entry.getKey(), Set.of());
			for (String encoded : entry.getValue()) {
				if (existing.contains(encoded)) continue;
				BlockPos pos = decodeOne(entry.getKey(), encoded);
				if (pos != null) result.add(new Candidate(entry.getKey(), pos));
			}
		}
		candidateCache = java.util.List.copyOf(result);
		return candidateCache;
	}

	public static void learn(String critter, BlockPos pos) {
		if (!"Hideonfloor".equals(critter)
			|| !ConfigManager.get().advanced.testingSaveLearnedLocations
			|| !SafariLocation.inside()
			|| !SafariPartyWatch.readyForLocationLearning() || pos == null) return;
		var species = dev.serko.safariutils.data.Critters.byName(critter);
		if (species == null || SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != species.biome()) return;
		if ("Hideyho".equals(critter) && TRANSIENT_HIDEYHO.contains(pos)) return;
		String encoded = encode(critter, pos);
		Data data = getLocal();
		Set<String> positions = data.positions.computeIfAbsent(critter, ignored -> new LinkedHashSet<>());
		boolean newBlock = !getBundled().positions.getOrDefault(critter, Set.of()).contains(encoded)
			&& positions.add(encoded);
		if (newBlock) {
			positionCache.remove(critter);
			candidateCache = null;
			DebugLog.line("WAYPOINT", "learned entity/" + critter + " at " + encoded);
			markDirty();
		}
	}

	private static void markDirty() {
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
		boolean hasSavedCatalog = Files.isRegularFile(SafariPaths.staticEntities());
		try {
			if (hasSavedCatalog) {
				local = GSON.fromJson(Files.readString(SafariPaths.staticEntities()), DATA_TYPE);
			}
		} catch (IOException | RuntimeException unreadable) {
			OperationalLog.error("CATALOG/ENTITIES_LOAD", unreadable);
		}
		if (local == null) local = new Data();
		normalize(local);
		if (!hasSavedCatalog) {
			local.schema = CURRENT_SCHEMA;
			return local;
		}
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
					if (pos != null) corrected.add(encode("Hideonfloor", pos.below()));
				}
				local.positions.put("Hideonfloor", corrected);
			}
		}
		boolean recentered = normalizePositionKeys(local, local.schema < CURRENT_SCHEMA);
		if (recentered) {
			dirty = true;
			dirtyAt = 0;
		}
		if (local.schema < CURRENT_SCHEMA) {
			local.schema = CURRENT_SCHEMA;
			dirty = true;
			dirtyAt = 0;
		}
		if (local.positions.keySet().removeIf(name -> !"Hideonfloor".equals(name))) {
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
		} catch (IOException | RuntimeException unreadable) {
			OperationalLog.error("CATALOG/ENTITIES_BUNDLED", unreadable);
		}
		if (bundled == null) bundled = new Data();
		normalize(bundled);
		normalizePositionKeys(bundled, false);
		sanitize(bundled);
		return bundled;
	}

	private static void save() {
		var path = SafariPaths.staticEntities();
		try {
			AtomicFiles.writeString(path, GSON.toJson(getLocal(), DATA_TYPE));
			dirty = false;
		} catch (IOException failed) {
			// Keep retrying, but no faster than the normal coalesced-save interval.
			dirtyAt = System.currentTimeMillis();
			OperationalLog.error("CATALOG/ENTITIES_SAVE", failed);
		}
	}

	private static void normalize(Data data) {
		if (data.positions == null) data.positions = new LinkedHashMap<>();
	}

	/** Schema 9 keys Hideyho by the center of its upper cube, not its old Y anchor. */
	private static boolean normalizePositionKeys(Data data, boolean oldHideyhoY) {
		boolean changed = false;
		for (var entry : data.positions.entrySet()) {
			Set<String> canonical = new LinkedHashSet<>();
			for (String stored : entry.getValue()) {
				BlockPos pos = oldHideyhoY ? StaticLocationKeys.decode(stored)
					: decodeOne(entry.getKey(), stored);
				canonical.add(pos == null ? stored : encode(entry.getKey(), pos));
			}
			if (canonical.equals(entry.getValue())) continue;
			entry.getValue().clear();
			entry.getValue().addAll(canonical);
			changed = true;
		}
		return changed;
	}

	private static boolean sanitize(Data data) {
		boolean changed = false;
		Set<BlockPos> otherStaticPositions = new LinkedHashSet<>();
		collectNonHideyhoPositions(data, otherStaticPositions);
		if (data != bundled) collectNonHideyhoPositions(getBundled(), otherStaticPositions);
		for (var entry : data.positions.entrySet()) {
			var species = dev.serko.safariutils.data.Critters.byName(entry.getKey());
			changed |= entry.getValue().removeIf(encoded -> {
				BlockPos pos = decodeOne(entry.getKey(), encoded);
				if (species == null || pos == null
					|| SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != species.biome()) return true;
				if ("Hideonwall".equals(entry.getKey()) && pos.distSqr(new BlockPos(16, 78, -69)) <= 4.0) return true;
				if ("Duplico".equals(entry.getKey()) && pos.distSqr(new BlockPos(1, 68, -54)) <= 4.0) return true;
				// The upper of the two learned Bloodbat markers around this beam is not a
				// spawn; the lower position remains valid.
				if ("Bloodbat".equals(entry.getKey()) && pos.equals(new BlockPos(-11, 85, -79))) return true;
				if ("Hideyho".equals(entry.getKey()) && pos.distSqr(new BlockPos(-7, 79, -90)) <= 4.0) return true;
				if ("Hideyho".equals(entry.getKey()) && TRANSIENT_HIDEYHO.contains(pos)) return true;
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

	private static String encode(String critter, BlockPos pos) {
		return StaticLocationKeys.encode(pos, "Hideyho".equals(critter) ? -1 : 0);
	}

	private static Set<BlockPos> decode(String critter, Set<String> encoded) {
		Set<BlockPos> result = new LinkedHashSet<>();
		for (String value : encoded) {
			BlockPos pos = decodeOne(critter, value);
			if (pos != null) result.add(pos);
		}
		return result;
	}

	private static BlockPos decodeOne(String critter, String value) {
		return StaticLocationKeys.decode(value, "Hideyho".equals(critter) ? -1 : 0);
	}

	private static BlockPos decodeOne(String value) {
		return StaticLocationKeys.decode(value);
	}

	private static final class Data {
		int schema;
		Map<String, Set<String>> positions = new LinkedHashMap<>();
	}
}
