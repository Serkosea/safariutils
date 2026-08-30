package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** The positions worth highlighting, each behind its own setting. */
public final class Markers {
	private static Object cachedLevel;
	private static long cachedTick = Long.MIN_VALUE;
	private static List<Marker> cachedMarkers = List.of();

	/** How a marker is drawn. */
	public enum Style {
		/** Through the terrain, with its name and distance floating above it. */
		WAYPOINT,
		/** Depth-tested and unnamed: a box on the thing, seen only when the thing is. */
		HIGHLIGHT
	}

	/** A labeled box to render. Only waypoint boxes can ignore depth. */
	public record Marker(AABB box, String label, int colour, Style style, boolean seeThrough) {
		public Marker(AABB box, String label, int colour, Style style) {
			this(box, label, colour, style, true);
		}
	}

	private Markers() {
	}

	public static List<Marker> collect() {
		net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
		if (client.level != null && client.level == cachedLevel
			&& client.level.getGameTime() == cachedTick) return cachedMarkers;
		List<Marker> result = collectFresh();
		cachedLevel = client.level;
		cachedTick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
		cachedMarkers = List.copyOf(result);
		return cachedMarkers;
	}

	/** Builds the current marker set once; render frames in the same game tick reuse it. */
	private static List<Marker> collectFresh() {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		SafariBiome biome = SafariLocation.biome();
		SafariSession session = SessionManager.current();
		List<Marker> markers = new ArrayList<>();

		// Each objective is only marked in its own biome.
		if (display.highlightSnooperWalls) {
			addWalls(markers, WallTracker.SNOOPER, biome,
				Colours.argb(display.snooperWallColour, 0xFFFFAA00));
		}
		if (display.highlightTroodonWalls) {
			addWalls(markers, WallTracker.TROODON, biome,
				Colours.argb(display.troodonWallColour, 0xFF55AAFF));
		}

		if (display.highlightNests && biome == SafariBiome.FOREST
			&& !SparklingMode.hideNests(session)) {
			List<NestTracker.Nest> allNests = NestTracker.nests();
			int shown = 0;
			for (NestTracker.Nest nest : allNests) {
				if (!nest.unpunched()) continue;
				// Safe Mode deliberately shows every learned candidate. It removes an
				// empty one only after that block is visibly checked by NestTracker.
				boolean seeThrough = true;
				String label = SafeMode.nests() && !NestTracker.isConfirmedVisible(nest.pos())
					? "Nest (Possible)" : "Nest";
				markers.add(new Marker(new AABB(nest.pos()), label,
					Colours.argb(display.nestColour, 0xFF55FF55), Style.WAYPOINT, seeThrough));
				if (seeThrough) shown++;
			}
			logRenderDiagnostic("NEST", allNests.size(), shown, biome, SafeMode.nests());
		}

		if (display.highlightMounds && biome == SafariBiome.CAVERN
			&& !SparklingMode.hideMounds(session)) {
			for (BlockPos pos : MoundSpotter.mounds()) {
				String label = SafeMode.mounds() && !MoundSpotter.isConfirmedVisible(pos)
					? "Mound (Possible)" : "Mound";
				markers.add(block(pos, label, Colours.argb(display.moundColour, 0xFFCC7744)));
			}
		}

		// Not gated on the biome: each is pinned by a capsule you threw, so it is
		// wherever you were standing when you threw it. Every active pin is drawn, not
		// just one — several can be open at once when more than one capsule is out.
		if (display.recatchHelper) {
			for (RecatchSpots.ActivePin pin : RecatchSpots.active()) {
				if (!pin.sparkling() && SparklingMode.hideOrdinarySpecies(pin.critter(), session)) continue;
				int colour = display.recatchRarityColour
					? WaypointRenderer.critterHitboxColour(pin.critter(), pin.sparkling())
					: Colours.argb(display.recatchColour, 0xFFFFFF55);
				String label = pin.critter().name()
					+ (display.recatchPityTitle ? pityLabel(pin.critter(), pin.entityId()) : "");
				markers.add(new Marker(pin.box(), label, colour, Style.WAYPOINT));
			}
		}

		// Filtered inside FloorDrops.positions() itself now, by the biome each
		// position was actually detected in — recorded once, reliably, at that
		// moment. An earlier version filtered here instead, with a fresh
		// per-position SafariAreaMap lookup that disagreed with the player's own
		// resolved biome often enough that drops stopped rendering even when
		// freshly confirmed and directly on screen.
		if (display.floorDrops && biome != null && !SparklingMode.hideFloorDrops(biome, session)) {
			List<BlockPos> drops = FloorDrops.positions(biome);
			int shown = 0;
			for (BlockPos pos : drops) {
				// Candidate locations are safe to reveal; whether this run actually
				// spawned one remains unknown until the player can see the block.
				boolean seeThrough = true;
				String label = SafeMode.floorDrops() && !FloorDrops.isConfirmedVisible(pos)
					? "Floor Drop (Possible)" : "Floor Drop";
				markers.add(new Marker(new AABB(pos), label,
					Colours.argb(display.floorDropColour, 0xFF55FFAA), Style.WAYPOINT, seeThrough));
				if (seeThrough) shown++;
			}
			logRenderDiagnostic("FLOOR", drops.size(), shown, biome, SafeMode.floorDrops());
		}
		return markers;
	}

	/** Logs marker eligibility once per second while output logging is enabled. */
	private static final java.util.Map<String, Long> lastDiagnosticLog = new java.util.HashMap<>();

	private static void logRenderDiagnostic(String tag, int total, int shown, SafariBiome biome, boolean safeMode) {
		if (!DebugLog.isEnabled()) return;
		long now = System.currentTimeMillis();
		Long last = lastDiagnosticLog.get(tag);
		if (last != null && now - last < 1000) return;
		lastDiagnosticLog.put(tag, now);
		DebugLog.line(tag, "render biome=" + biome + " safeMode=" + safeMode
			+ " known=" + total + " shown=" + shown);
	}

	/**
	 * The number of failed attempts on a rarity Hypixel guarantees a catch by, so
	 * showing "1/20" against a Doomspiral means one more failure and the next capsule
	 * is certain. Read off in-game observation, not documented anywhere the way the
	 * rarities and colours are — the Uncommon and Rare numbers are fairly solid, the
	 * Epic and Legendary ones less so, and worth correcting if wrong.
	 */
	public static int pityThreshold(Critter.Rarity rarity) {
		return switch (rarity) {
			case UNCOMMON -> 2;
			case RARE -> 3;
			case EPIC -> 5;
			case LEGENDARY -> 20;
			case COMMON -> 1;
		};
	}

	/** Returns this individual's pity count for hitboxes and waypoints. */
	public static String pityLabel(Critter critter, java.util.UUID entityId) {
		// A Common is caught on the throw, always — there is no escape to build pity
		// from, so a count next to one is pure clutter, not information.
		if (critter.rarity() == Critter.Rarity.COMMON) return "";
		return " (" + RecatchSpots.pityFor(entityId) + "/" + pityThreshold(critter.rarity()) + ")";
	}

	private static Marker block(BlockPos pos, String label, int colour) {
		return new Marker(new AABB(pos), label, colour, Style.WAYPOINT);
	}

	/**
	 * Marks the walls of one set that are still standing.
	 *
	 * <p>A broken wall leaves air behind, and air is also what an unloaded chunk reports,
	 * so only walls confirmed to still hold a block are marked — never a guess.
	 */
	private static void addWalls(List<Marker> markers, WallTracker walls, SafariBiome biome, int colour) {
		if (biome != walls.biome()) return;
		SafariSession session = SessionManager.current();
		if (walls == WallTracker.SNOOPER && SparklingMode.hideSnoozleWalls(session)) return;
		if (walls == WallTracker.TROODON && SparklingMode.hideTroodonWalls(session)) return;
		for (WallTracker.Wall wall : walls.walls()) {
			boolean possible = wall.state() == WallTracker.State.UNKNOWN
				&& (walls == WallTracker.SNOOPER ? SafeMode.snoozleWalls() : SafeMode.troodonWalls());
			if (wall.state() != WallTracker.State.INTACT && !possible) continue;
			markers.add(block(wall.pos(), walls.name() + " wall", colour));
		}
	}
}
