package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable bundled positions for stationary Safari objectives. */
public final class StaticWaypointCatalog {
	private static final Gson GSON = new GsonBuilder().create();
	private static final Type DATA_TYPE = new TypeToken<Data>() { }.getType();
	private static final String BUNDLED = "/assets/safariutils/static-waypoints.json";
	private static final Map<SafariBiome, Set<BlockPos>> floorDropCache =
		new EnumMap<>(SafariBiome.class);
	private static Data bundled;
	private static Set<BlockPos> nestCache;
	private static Set<BlockPos> moundCache;

	private StaticWaypointCatalog() { }

	public static Set<BlockPos> floorDrops(SafariBiome biome) {
		return floorDropCache.computeIfAbsent(biome, value ->
			decode(data().floorDrops.getOrDefault(value.name(), Set.of())));
	}

	public static Set<BlockPos> nests() {
		if (nestCache == null) nestCache = decode(data().nests);
		return nestCache;
	}

	public static Set<BlockPos> mounds() {
		if (moundCache == null) moundCache = decode(data().mounds);
		return moundCache;
	}

	private static Data data() {
		if (bundled != null) return bundled;
		try (var stream = StaticWaypointCatalog.class.getResourceAsStream(BUNDLED)) {
			if (stream != null) bundled = GSON.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), DATA_TYPE);
		} catch (IOException | RuntimeException unreadable) {
			OperationalLog.error("CATALOG/WAYPOINTS_BUNDLED", unreadable);
		}
		if (bundled == null) bundled = new Data();
		if (bundled.floorDrops == null) bundled.floorDrops = new LinkedHashMap<>();
		if (bundled.nests == null) bundled.nests = new LinkedHashSet<>();
		if (bundled.mounds == null) bundled.mounds = new LinkedHashSet<>();
		return bundled;
	}

	private static Set<BlockPos> decode(Set<String> encoded) {
		Set<BlockPos> result = new LinkedHashSet<>();
		for (String value : encoded) {
			BlockPos pos = StaticLocationKeys.decode(value);
			if (pos != null) result.add(pos);
		}
		return Set.copyOf(result);
	}

	private static final class Data {
		Map<String, Set<String>> floorDrops = new LinkedHashMap<>();
		Set<String> nests = new LinkedHashSet<>();
		Set<String> mounds = new LinkedHashSet<>();
	}
}
