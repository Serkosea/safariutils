package dev.serko.safariutils.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.SafariUtils;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders labeled waypoint boxes and nearby critter hitboxes. Waypoints use a custom
 * through-wall pipeline; moving critter hitboxes remain depth-tested.
 */
public final class WaypointRenderer {

	private static final float MAX_DISTANCE = 200.0f;
	private static final float LINE_WIDTH = 3.0f;
	/** Where the label sits above the top of the box. */
	private static final float LABEL_HEIGHT = 0.4f;
	/** Vanilla's name-tag scale, so labels match the size of mob names. */
	private static final float LABEL_SCALE = 0.025f;
	/** Shared by every live box submitted during the current render pass. */
	private static float framePartialTick;
	private static Set<java.util.UUID> replacedVanillaNames = new java.util.HashSet<>();
	private static Set<java.util.UUID> nextReplacedVanillaNames = new java.util.HashSet<>();
	private static final int LABEL_CACHE_LIMIT = 512;
	private static final int PROVISIONAL_SHAPE_CACHE_LIMIT = 256;
	private static final int BOUNDS_CACHE_LIMIT = 512;
	/** One server tick, smoothing discrete entity-size updates without adding lag. */
	private static final long BOUNDS_TRANSITION_NANOS = 50_000_000L;
	private static final Map<LabelKey, CachedLabel> LABEL_CACHE =
		new LinkedHashMap<>(LABEL_CACHE_LIMIT, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<LabelKey, CachedLabel> eldest) {
				return size() > LABEL_CACHE_LIMIT;
			}
		};
	/** Last exact label-to-body geometry, reused only while that same label is bodyless. */
	private static final Map<java.util.UUID, ProvisionalShape> PROVISIONAL_SHAPES =
		new LinkedHashMap<>(PROVISIONAL_SHAPE_CACHE_LIMIT, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<java.util.UUID, ProvisionalShape> eldest) {
				return size() > PROVISIONAL_SHAPE_CACHE_LIMIT;
			}
		};
	private static final Map<java.util.UUID, BoundsTransition> BOUNDS_TRANSITIONS =
		new LinkedHashMap<>(BOUNDS_CACHE_LIMIT, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<java.util.UUID, BoundsTransition> eldest) {
				return size() > BOUNDS_CACHE_LIMIT;
			}
		};
	private record LabelKey(String label, long distance, long rainbowFrame) { }
	private record CachedLabel(FormattedCharSequence sequence, int width) { }
	private record VisibleMarker(Markers.Marker marker, double distance, boolean seeThrough) { }
	private record ProvisionalShape(Vec3 bodyOffset, double width, double height) { }
	private static final class BoundsTransition {
		double fromWidth;
		double fromHeight;
		double targetWidth;
		double targetHeight;
		double displayedWidth;
		double displayedHeight;
		double baseWidth;
		double baseHeight;
		long startedAt;

		BoundsTransition(double width, double height, long now) {
			fromWidth = targetWidth = width;
			fromHeight = targetHeight = height;
			displayedWidth = width;
			displayedHeight = height;
			baseWidth = width;
			baseHeight = height;
			startedAt = now;
		}
	}

	/**
	 * The lines pipeline with the depth test disabled, so the box shows through terrain.
	 *
	 * <p>Registered so the game precompiles it along with its own; it would be compiled
	 * on first use either way.
	 */
	private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID, "pipeline/lines_through_walls"))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build());

	private static final RenderType LINES = RenderType.create(
		SafariUtils.MOD_ID + ":lines_through_walls",
		RenderSetup.builder(LINES_THROUGH_WALLS)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup());

	/** Textured translucent beacon pipeline that remains visible through terrain. */
	private static final RenderPipeline BEACON_THROUGH_WALLS_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID,
				"pipeline/beacon_through_walls"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build());

	private static final RenderType BEACON_THROUGH_WALLS = RenderType.create(
		SafariUtils.MOD_ID + ":beacon_through_walls",
		RenderSetup.builder(BEACON_THROUGH_WALLS_PIPELINE)
			.withTexture("Sampler0", BeaconRenderer.BEAM_LOCATION)
			.sortOnUpload()
			.createRenderSetup());

	/** Ordinary depth-tested beam used when Safe Mode requires visual confirmation. */
	private static final RenderPipeline BEACON_DEPTH_TESTED_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID,
				"pipeline/beacon_depth_tested"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build());

	private static final RenderType BEACON_DEPTH_TESTED = RenderType.create(
		SafariUtils.MOD_ID + ":beacon_depth_tested",
		RenderSetup.builder(BEACON_DEPTH_TESTED_PIPELINE)
			.withTexture("Sampler0", BeaconRenderer.BEAM_LOCATION)
			.sortOnUpload()
			.createRenderSetup());

	/** Depth-tested fill used for the visible top face of a floor drop. */
	private static final RenderType FILLED_FACE = RenderType.create(
		SafariUtils.MOD_ID + ":filled_face",
		RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup());

	private WaypointRenderer() {
	}

	public static void register() {
		WaypointRenderBackend.register(context ->
			OperationalLog.run("RENDER/waypoints", () -> render(context)));
	}

	private static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!HypixelConnection.active() || client.player == null || ClientCompat.hudHidden()) return;
		if (!SafariLocation.inside()) return;
		nextReplacedVanillaNames.clear();
		framePartialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		List<Markers.Marker> markers = Markers.collect();

		// The pose is at the camera, so world positions are drawn relative to it.
		PoseStack poses = context.poseStack();
		WaypointRenderBackend backend = new WaypointRenderBackend(context);
		Vec3 camera = backend.cameraPosition();

		if (!markers.isEmpty()) {
			List<VisibleMarker> visibleMarkers = new java.util.ArrayList<>(markers.size());
			for (Markers.Marker marker : markers) {
				double distanceSq = distanceSquared(marker.box(), camera);
				float maxDistance = marker.researchCandidate() ? 512.0f : MAX_DISTANCE;
				if (distanceSq > maxDistance * maxDistance || !visible(marker.box())) continue;

				// A highlight is drawn with the vanilla line type, which is
				// depth-tested, so it only shows where the thing itself would be
				// visible, regardless of seeThrough — that field only matters for
				// a waypoint, which otherwise defaults to through-terrain. See
				// Marker.seeThrough's own doc for why a waypoint needed this at
				// all: floor drops and nests both needed to depth-test their own
				// box specifically, without losing their label the way switching
				// them to HIGHLIGHT entirely would have.
				boolean seeThrough = marker.style() == Markers.Style.WAYPOINT && marker.seeThrough();
				visibleMarkers.add(new VisibleMarker(marker,
					marker.style() == Markers.Style.WAYPOINT ? Math.sqrt(distanceSq) : 0.0,
					seeThrough));
				RenderType lineType = seeThrough ? LINES : RenderTypes.LINES;

				AABB box = marker.box();
				if (marker.label().startsWith("SPARKLING ")) {
					drawRainbowBox(poses, backend, lineType, box, camera);
				} else {
					poses.pushPose();
					poses.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
					backend.geometry(lineType, (pose, lines) -> box(pose, lines,
						(float) box.getXsize(), (float) box.getYsize(),
						(float) box.getZsize(), marker.colour()));
					poses.popPose();
				}
			}
			// Flushed here rather than left to the end of the frame, so every box is
			// drawn before the first label and a waypoint reads as one thing.
			backend.flush(LINES);
			backend.flush(RenderTypes.LINES);

			for (VisibleMarker visible : visibleMarkers) {
				Markers.Marker marker = visible.marker();
				// Only a waypoint is named: a highlight sits on something you can
				// already see, so a label over it is just something else to read.
				if (marker.style() != Markers.Style.WAYPOINT
					|| marker.critterLabel() && !showsWaypointNametags()) continue;
				label(poses, backend, marker, camera, visible.distance(), visible.seeThrough());
			}
		}

		renderHitboxes(context, backend, camera);
		renderSparklingMarkers(context, backend, camera);
		renderTrackedWaypoints(context, backend, camera);
		renderFloorDropFaces(context, backend, camera);
		renderDiagnosticHitboxes(context, backend, camera);
		// Publish only after the complete pass. Vanilla entity names may be extracted
		// before or after this callback, so they use one stable frame snapshot.
		Set<java.util.UUID> previous = replacedVanillaNames;
		replacedVanillaNames = nextReplacedVanillaNames;
		nextReplacedVanillaNames = previous;
	}

	/** Rendering-only test used by the entity-name mixin. */
	public static boolean replacesVanillaName(Entity entity) {
		if (!HypixelConnection.active() || showsVanillaNametags() || !SafariLocation.inside()) return false;
		if (replacedVanillaNames.contains(entity.getUUID())) return true;
		// Entity render states may be extracted before this frame's waypoint pass.
		// Evaluate the shared hitbox conditions directly instead of depending only on
		// the previous published render snapshot.
		CritterEntities.Sighting sighting = CritterEntities.sightingFor(entity.getUUID());
		if (ConfigManager.get().display.enableHitboxes
			&& sighting != null && rendersLiveHitbox(sighting)) return true;
		Critter bodyless = CritterEntities.bodylessLabelCritter(entity.getUUID());
		if (bodyless != null && captureTransitionReplacesName(bodyless)) return true;
		return sighting != null && captureTransitionReplacesName(sighting.critter());
	}

	/** Only dedicated waypoint species replace their vanilla name during a recatch pin. */
	private static boolean captureTransitionReplacesName(Critter critter) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		return display.recatchHelper
			&& EXCLUDED_FROM_HITBOXES.contains(critter.name())
			&& trackedWaypointEnabled(critter.name(), display)
			&& RecatchSpots.captureInProgress(critter);
	}

	/** Mirrors live-render eligibility without relying on render-pass ordering. */
	private static boolean rendersLiveHitbox(CritterEntities.Sighting sighting) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		Critter critter = sighting.critter();
		Entity body = sighting.mob();
		boolean sparkling = SparklingWatch.presentsAsSparkling(sighting);

		if (body != null && RecatchSpots.isCaptureArtifact(critter, body)) return false;
		if (sparkling && SparklingWatch.isOutstanding(sighting)) {
			if (body == null && !"Hideyho".equals(critter.name())
				&& (!SparklingWatch.provisionalMarkerAllowed(critter)
					|| RecatchSpots.captureInProgress(critter))) return false;
			if (SafeMode.sparklingCritters()
				&& (body == null || !VisibilityCheck.canSee(body))
				&& !VisibilityCheck.canSeeVisibleName(sighting.label())) return false;
			return body == null || !isRecatchPinned(body.getUUID());
		}

		if (body == null) {
			if ("Hideyho".equals(critter.name()) || RecatchSpots.captureInProgress(critter)) return false;
			if (EXCLUDED_FROM_HITBOXES.contains(critter.name())
				&& !trackedWaypointEnabled(critter.name(), display)) return false;
			if (SparklingMode.hideOrdinaryHitbox(critter, sparkling)) return false;
			return !SafeMode.hiddenCritter(critter, sparkling)
				|| VisibilityCheck.canSeeVisibleName(sighting.label());
		}

		if (EntityTypeIds.is(body, "player") || isRecatchPinned(body.getUUID())
			|| SparklingMode.hideOrdinaryHitbox(critter, sparkling)) return false;
		if (!EXCLUDED_FROM_HITBOXES.contains(critter.name())) return true;
		if (SafariLocation.biome() != critter.biome() || StillCritters.isResolved(body.getUUID())) {
			return false;
		}
		return !SafeMode.hiddenCritter(critter, sparkling)
			|| StillCritters.isVisiblyConfirmed(body.getUUID());
	}

	/** Both the floating label and some critter bodies can independently render a name. */
	private static void markVanillaNameReplaced(CritterEntities.Sighting sighting) {
		if (showsVanillaNametags()) return;
		nextReplacedVanillaNames.add(sighting.label().getUUID());
		if (sighting.mob() != null) nextReplacedVanillaNames.add(sighting.mob().getUUID());
	}

	private static boolean showsVanillaNametags() {
		int mode = ConfigManager.get().display.displayNametags;
		return mode == 1 || mode == 3;
	}

	private static boolean showsWaypointNametags() {
		int mode = ConfigManager.get().display.displayNametags;
		return mode == 2 || mode == 3;
	}

	/** Animated colour shared by every Sparkling world-space element. */
	private static int sparklingColour() {
		return RainbowColours.shared(0f, 0.55f);
	}
	private static final double SPARKLING_BEAM_WIDTH = 0.56;
	private static final double SPARKLING_BEAM_CORE_WIDTH = 0.22;
	private static final double SPARKLING_BEAM_TOP = 320.0;
	private static final int SPARKLING_BEAM_GRADIENT_SEGMENTS = 24;

	/** Species with their own dedicated waypoint further down. */
	private static final Set<String> CUSTOM_WAYPOINT_CRITTERS =
		Set.of("Hideonwall", "Duplico", "Hideonfloor", "Bloodbat");
	private static final Set<String> GENERIC_REMEMBERED_CRITTERS =
		Set.of("Snoozle", "Troodon", "Fluffling");
	private static final Set<String> EXCLUDED_FROM_HITBOXES = Set.of(
		"Hideyho", "Hideonwall", "Duplico", "Hideonfloor", "Bloodbat", "Snoozle", "Troodon", "Fluffling");

	/**
	 * Each tracked critter's toggle, colour, and the biome it is looked for in.
	 *
	 * <p>Bloodbat uses its real hitbox while loaded. Other species and remembered
	 * positions use a stable block-sized marker.
	 */
	private record TrackedWaypoint(String critterName, String label,
									java.util.function.Predicate<SafariConfig.DisplayConfig> enabled,
									java.util.function.Function<SafariConfig.DisplayConfig, String> colour,
									SafariBiome biome, boolean useRealHitbox) {
	}

	private static final List<TrackedWaypoint> TRACKED_WAYPOINTS = List.of(
		new TrackedWaypoint("Hideonwall", "Hideonwall",
			d -> d.highlightHideonwalls, d -> d.hideonwallColour, SafariBiome.HAUNTED, false),
		new TrackedWaypoint("Duplico", "Duplico",
			d -> d.highlightDuplico, d -> d.duplicoColour, SafariBiome.HAUNTED, false),
		new TrackedWaypoint("Bloodbat", "Bloodbat",
			d -> d.highlightBloodbat, d -> d.bloodbatColour, SafariBiome.HAUNTED, true),
		new TrackedWaypoint("Hideonfloor", "Hideonfloor",
			d -> d.highlightHideonfloor, d -> d.hideonfloorColour, SafariBiome.FOREST, false),
		new TrackedWaypoint("Snoozle", "Snoozle",
			d -> true, d -> d.hitboxColour, SafariBiome.CAVERN, true),
		new TrackedWaypoint("Troodon", "Troodon",
			d -> true, d -> d.hitboxColour, SafariBiome.ICY, true),
		new TrackedWaypoint("Fluffling", "Fluffling",
			d -> true, d -> d.hitboxColour, SafariBiome.FOREST, true));

	private static final String FISH_NAME = "Flavor Packed Fish";
	private static final double FISH_PAIR_RADIUS = 2.5;
	private static final TickCache<FishEntities> FISH_ENTITIES = new TickCache<>();
	private record FishEntities(List<Entity> labels, List<Entity> interactions) { }

	/** Draws enabled diagnostic entity types in a distinct color. */
	private static void renderDiagnosticHitboxes(LevelRenderContext context,
			WaypointRenderBackend backend, Vec3 camera) {
		if (!BuildVersion.DEVELOPER) return;
		SafariConfig.AdvancedConfig advanced = ConfigManager.get().advanced;
		if (!advanced.showAllArmorStands && !advanced.showAllItemDisplays && !advanced.showAllInteractions
			&& !advanced.showAllBlockDisplays && !advanced.showAllTextDisplays) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		PoseStack poses = context.poseStack();
		// Not RenderTypes.LINES: a debug hitbox exists specifically to find something
		// unidentified, which is exactly the situation where it might be behind
		// something — Safe Mode's whole point is withholding information the player
		// would not otherwise have, and a diagnostic tool the player deliberately
		// turned on is the opposite of that, so it takes precedence unconditionally.
		int colour = 0xFFFF00FF;
		boolean anyDrawn = false;

		record Found(Markers.Marker marker, double distance) {
		}
		List<Found> drawn = new java.util.ArrayList<>();

		for (Entity entity : WorldEntities.current()) {
			EntityType<?> type = entity.getType();
			boolean wanted = (EntityTypeIds.is(type, "armor_stand") && advanced.showAllArmorStands)
				|| (EntityTypeIds.is(type, "item_display") && advanced.showAllItemDisplays)
				|| (EntityTypeIds.is(type, "interaction") && advanced.showAllInteractions)
				|| (EntityTypeIds.is(type, "block_display") && advanced.showAllBlockDisplays)
				|| (EntityTypeIds.is(type, "text_display") && advanced.showAllTextDisplays);
			if (!wanted) continue;

			AABB box = hitboxFor(entity);
			if (!drawBox(poses, backend, LINES, box, camera, colour)) continue;
			anyDrawn = true;

			String label = entity.hasCustomName() ? entity.getCustomName().getString() : "(unnamed)";
			drawn.add(new Found(new Markers.Marker(box, label, colour, Markers.Style.HIGHLIGHT),
				Math.sqrt(distanceSquared(box, camera))));
		}

		if (!anyDrawn) return;
		backend.flush(LINES);

		for (Found found : drawn) {
			label(poses, backend, found.marker(), camera, found.distance(), true);
		}
	}

	/** Draws depth-tested top faces without changing through-wall waypoint outlines. */
	private static void renderFloorDropFaces(LevelRenderContext context,
			WaypointRenderBackend backend, Vec3 camera) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.floorDrops || SessionManager.current() == null) return;
		SafariBiome biome = SafariLocation.biome();
		if (biome == null) return;
		if (SparklingMode.hideFloorDrops(biome, SessionManager.current())) return;

		PoseStack poses = context.poseStack();
		int colour = Colours.argb(display.floorDropFaceColour, 0xFFA0FFD3);
		boolean anyDrawn = false;

		for (BlockPos pos : FloorDrops.positions(biome)) {
			AABB visibleFace = new AABB(pos).inflate(0.02);
			if (!visible(visibleFace)) continue;
			poses.pushPose();
			poses.translate(pos.getX() - camera.x - 0.005, pos.getY() - camera.y,
				pos.getZ() - camera.z - 0.005);
			backend.geometry(FILLED_FACE,
				(pose, quad) -> topFace(pose, quad, 1.01f, 1.01f, 1.006f, colour));
			poses.popPose();
			anyDrawn = true;
		}

		if (anyDrawn) backend.flush(FILLED_FACE);
	}

	/** Draws live, depth-tested critter hitboxes and pity labels. */
	private static void renderHitboxes(LevelRenderContext context,
			WaypointRenderBackend backend, Vec3 camera) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.enableHitboxes) return;

		PoseStack poses = context.poseStack();
		// Sparkling hitboxes specifically get the same non-depth-tested type the
		// tracked-waypoint species already use — a sparkling is rare enough to be
		// worth spotting through a wall the same way, whereas an ordinary hitbox
		// staying depth-tested is deliberate: see the class doc.
		int fixedColour = Colours.argb(display.hitboxColour, 0xFFFFFFFF);
		boolean anyDrawn = false;
		boolean anyThroughWalls = false;

		record Found(Markers.Marker marker, double distance, boolean seeThrough) {
		}
		List<Found> drawn = new java.util.ArrayList<>();

		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			String name = sighting.critter().name();
			boolean sparkling = SparklingWatch.presentsAsSparkling(sighting);
			// Outstanding Sparklings use the dedicated renderer below so their box,
			// name, and beacon remain one continuous marker.
			if (sparkling && SparklingWatch.isOutstanding(sighting)) continue;
			Entity entity = sighting.mob();
			if (entity != null && RecatchSpots.isCaptureArtifact(sighting.critter(), entity)) continue;
			if (entity == null) {
				// Hideyho's named player is its real body and has its own exact solver.
				// Other bodyless labels receive a moving approximate box while pairing.
				if ("Hideyho".equals(name) || RecatchSpots.captureInProgress(sighting.critter())) continue;
				if (EXCLUDED_FROM_HITBOXES.contains(name)
					&& !trackedWaypointEnabled(name, display)) continue;
				if (SparklingMode.hideOrdinaryHitbox(sighting.critter(), sparkling)) continue;
				if (SafeMode.hiddenCritter(sighting.critter(), sparkling)
					&& !VisibilityCheck.canSeeVisibleName(sighting.label())) continue;

				boolean diagnostic = BuildVersion.DEVELOPER
					&& ConfigManager.get().advanced.showAllCritterHitboxes;
				int colour = critterHitboxColour(sighting.critter(), sparkling);
				AABB box = provisionalCritterHitbox(sighting.critter(), sighting.label());
				boolean seeThrough = !SafeMode.critterHitboxes(sparkling) || diagnostic;
				if (seeThrough) {
					if (!(sparkling ? drawRainbowBox(poses, backend, LINES, box, camera)
						: drawBox(poses, backend, LINES, box, camera, colour))) continue;
					anyThroughWalls = true;
				} else {
					if (!(sparkling ? drawRainbowBox(poses, backend, RenderTypes.LINES, box, camera)
						: drawBox(poses, backend, RenderTypes.LINES, box, camera, colour))) continue;
					anyDrawn = true;
				}
				drawn.add(new Found(new Markers.Marker(box,
					(sparkling ? "SPARKLING " : "") + name, colour, Markers.Style.HIGHLIGHT),
					Math.sqrt(distanceSquared(box, camera)), seeThrough));
				markVanillaNameReplaced(sighting);
				continue;
			}
			if (EXCLUDED_FROM_HITBOXES.contains(name)) continue;
			if (EntityTypeIds.is(entity, "player")) continue;
			rememberProvisionalShape(sighting.label(), entity);

			// A recatch pin replaces only that individual's ordinary hitbox.
			if (isRecatchPinned(entity.getUUID())) continue;

			// Diagnostic, Sparkling, unique-status, and configured colors apply in that order.
			if (SparklingMode.hideOrdinaryHitbox(sighting.critter(), sparkling)) continue;
			boolean diagnostic = BuildVersion.DEVELOPER
				&& ConfigManager.get().advanced.showAllCritterHitboxes;
			int uniqueColour = SparklingMode.uniqueHitboxColour(sighting.critter(),
				SessionManager.current());
			int colour = diagnostic ? 0xFFFF00FF
				: sparkling ? sparklingColour()
				: uniqueColour != 0 ? uniqueColour
				: display.hitboxRarityColour ? 0xFF000000 | sighting.critter().rarity().colour() : fixedColour;

			AABB box = presentedHitbox(sighting.critter(), entity);
			// Diagnostics override Safe Mode depth testing; ordinary hitboxes do not.
			boolean seeThrough = !SafeMode.critterHitboxes(sparkling) || diagnostic;
			if (seeThrough) {
				if (!(sparkling ? drawRainbowBox(poses, backend, LINES, box, camera)
					: drawBox(poses, backend, LINES, box, camera, colour))) continue;
				anyThroughWalls = true;
			} else {
				if (!(sparkling ? drawRainbowBox(poses, backend, RenderTypes.LINES, box, camera)
					: drawBox(poses, backend, RenderTypes.LINES, box, camera, colour))) continue;
				anyDrawn = true;
			}

			String label = (sparkling ? "SPARKLING " : "") + sighting.critter().name()
				+ (display.hitboxPityTitle ? Markers.pityLabel(sighting.critter(), entity.getUUID()) : "");
			drawn.add(new Found(new Markers.Marker(box, label, colour, Markers.Style.HIGHLIGHT),
				Math.sqrt(distanceSquared(box, camera)), seeThrough));
			markVanillaNameReplaced(sighting);
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			FishEntities fish = FISH_ENTITIES.get(WaypointRenderer::collectFishEntities);
			for (Entity fishLabel : fish.labels()) {

				Entity wrapper = nearestInteraction(fish.interactions(), fishLabel);
				if (wrapper == null) continue;

				// The armor-stand label follows the falling fish with the same interpolation
				// as its visible model. Hypixel's interaction wrapper updates in coarser
				// steps, which made only this hitbox visibly hop while falling.
				Vec3 pos = renderPosition(fishLabel);
				double halfWidth = wrapper.getBbWidth() / 2.0;
				double height = wrapper.getBbHeight();
				AABB box = new AABB(pos.x - halfWidth, pos.y, pos.z - halfWidth,
					pos.x + halfWidth, pos.y + height, pos.z + halfWidth);
				if (drawBox(poses, backend, RenderTypes.LINES, box, camera, fixedColour)) {
					anyDrawn = true;
				}
			}
		}

		if (anyDrawn) backend.flush(RenderTypes.LINES);
		if (anyThroughWalls) backend.flush(LINES);
		if (!anyDrawn && !anyThroughWalls) return;

		if (showsWaypointNametags()) {
			for (Found found : drawn) {
				label(poses, backend, found.marker(), camera, found.distance(), found.seeThrough());
			}
		}
	}

	/** Keeps provisional tracked-species boxes under the same toggle as their exact box. */
	private static boolean trackedWaypointEnabled(String critterName,
			SafariConfig.DisplayConfig display) {
		for (TrackedWaypoint tracked : TRACKED_WAYPOINTS) {
			if (tracked.critterName().equals(critterName)) return tracked.enabled().test(display);
		}
		return false;
	}

	/** One entity sweep per game tick supplies every render frame's fish pairing. */
	private static FishEntities collectFishEntities() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return new FishEntities(List.of(), List.of());
		List<Entity> labels = new java.util.ArrayList<>();
		List<Entity> interactions = new java.util.ArrayList<>();
		for (Entity entity : WorldEntities.current()) {
			if (EntityTypeIds.is(entity, "interaction")) interactions.add(entity);
			else if (EntityTypeIds.is(entity, "armor_stand") && entity.hasCustomName()
					&& entity.getCustomName().getString().contains(FISH_NAME)) labels.add(entity);
		}
		return new FishEntities(List.copyOf(labels), List.copyOf(interactions));
	}

	/** Marks every detected Sparkling independently of the ordinary hitbox setting. */
	private static void renderSparklingMarkers(LevelRenderContext context,
			WaypointRenderBackend backend, Vec3 camera) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		PoseStack poses = context.poseStack();
		boolean beamsDrawn = false;
		boolean throughBoxesDrawn = false;
		boolean depthBoxesDrawn = false;
		RenderType beamCore = SafeMode.sparklingCritters()
			? BEACON_DEPTH_TESTED : BEACON_THROUGH_WALLS;
		RenderType beamGlow = beamCore;
		record Found(Markers.Marker marker, double distance, boolean seeThrough) { }
		List<Found> labels = new java.util.ArrayList<>();

		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!SparklingWatch.isOutstanding(sighting)) continue;
			// Hideyho's solver owns its box and START/END title. This path retains only
			// its Sparkling beacon so a generic pity marker can never appear beside it.
			boolean hideyho = "Hideyho".equals(sighting.critter().name());
			Entity body = sighting.mob();
			if (body != null && RecatchSpots.isCaptureArtifact(sighting.critter(), body)) continue;
			if (body == null && !hideyho
				&& (!SparklingWatch.provisionalMarkerAllowed(sighting.critter())
					|| RecatchSpots.captureInProgress(sighting.critter()))) continue;
			// Safe Mode retains a confirmed marker but depth-tests its beacon; Extra Mode
			// may continue using the beacon as a through-terrain guide.
			if (body != null) rememberProvisionalShape(sighting.label(), body);
			AABB box = hideyho
				? hideyhoHitbox(renderPosition(sighting.label()), sighting.label().blockPosition())
				: "Duplico".equals(sighting.critter().name()) ? duplicoHitbox(sighting.label())
				: body != null ? presentedHitbox(sighting.critter(), body)
				: provisionalCritterHitbox(sighting.critter(), sighting.label());
			double distanceSq = distanceSquared(box, camera);
			int colour = sparklingColour();

			if (!hideyho && display.enableHitboxes
				&& (body == null || !isRecatchPinned(body.getUUID()))) {
				boolean seeThrough = !SafeMode.critterHitboxes(true);
				if (drawRainbowBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES,
						box, camera)) {
					if (seeThrough) throughBoxesDrawn = true;
					else depthBoxesDrawn = true;
					String pity = display.hitboxPityTitle && body != null
						? Markers.pityLabel(sighting.critter(), body.getUUID()) : "";
					labels.add(new Found(new Markers.Marker(box,
						"SPARKLING " + sighting.critter().name() + pity, colour,
						Markers.Style.HIGHLIGHT), Math.sqrt(distanceSq), seeThrough));
					markVanillaNameReplaced(sighting);
				}
			}

			// Hitboxes follow every loaded detection; the tall beacon keeps its normal
			// 200-block budget because its geometry is much larger than a small box.
			if (distanceSq > MAX_DISTANCE * MAX_DISTANCE) continue;
			double centreX = (box.minX + box.maxX) * 0.5;
			double centreZ = (box.minZ + box.maxZ) * 0.5;
			double half = SPARKLING_BEAM_WIDTH * 0.5;
			double top = Math.max(SPARKLING_BEAM_TOP, box.maxY + 64.0);
			AABB beam = new AABB(centreX - half, box.maxY, centreZ - half,
				centreX + half, top, centreZ + half);
			if (!visible(beam)) continue;
			drawRainbowBeaconBeam(poses, backend, beamGlow, beam, camera, 0x4C / 255f);
			double coreHalf = SPARKLING_BEAM_CORE_WIDTH * 0.5;
			AABB core = new AABB(centreX - coreHalf, box.maxY, centreZ - coreHalf,
				centreX + coreHalf, top, centreZ + coreHalf);
			drawRainbowBeaconBeam(poses, backend, beamCore, core, camera, 0xD0 / 255f);
			beamsDrawn = true;
		}

		if (throughBoxesDrawn) backend.flush(LINES);
		if (depthBoxesDrawn) backend.flush(RenderTypes.LINES);
		if (beamsDrawn) {
			backend.flush(beamCore);
			if (beamGlow != beamCore) backend.flush(beamGlow);
		}
		if (showsWaypointNametags()) {
			for (Found found : labels) {
				label(poses, backend, found.marker(), camera, found.distance(), found.seeThrough());
			}
		}
	}

	/** Duplico's named label follows its movement more smoothly than its swapped bodies. */
	private static AABB duplicoHitbox(Entity label) {
		Vec3 pos = renderPosition(label).add(0.0, -1.25, 0.0);
		return CritterMarkerGeometry.blockSized(pos);
	}

	/** Temporary body-sized anchor until the named label pairs with its real mob. */
	private static AABB provisionalCritterHitbox(Critter critter, Entity label) {
		ProvisionalShape shape = PROVISIONAL_SHAPES.get(label.getUUID());
		if (shape == null) {
			// First-scan fallback only. Once paired, this label retains the body's exact
			// dimensions and offset through short wake/movement transitions.
			shape = new ProvisionalShape(new Vec3(0.0, -1.25, 0.0), 1.0, 1.0);
		}
		Vec3 pos = renderPosition(label).add(shape.bodyOffset());
		if (CritterMarkerGeometry.usesBlockSize(critter)) {
			return CritterMarkerGeometry.presented(critter, pos);
		}
		double halfWidth = shape.width() * 0.5;
		return new AABB(pos.x - halfWidth, pos.y, pos.z - halfWidth,
			pos.x + halfWidth, pos.y + shape.height(), pos.z + halfWidth);
	}

	private static void rememberProvisionalShape(Entity label, Entity body) {
		Vec3 labelPos = renderPosition(label);
		Vec3 bodyPos = renderPosition(body);
		PROVISIONAL_SHAPES.put(label.getUUID(), new ProvisionalShape(
			bodyPos.subtract(labelPos), body.getBbWidth(), body.getBbHeight()));
	}

	/** Uses Minecraft's beacon texture with a vertically animated rainbow. */
	private static void drawRainbowBeaconBeam(PoseStack poses, WaypointRenderBackend backend,
			RenderType type, AABB beam, Vec3 camera, float alpha) {
		poses.pushPose();
		poses.translate(beam.minX - camera.x, beam.minY - camera.y, beam.minZ - camera.z);
		backend.geometry(type, (pose, quads) -> rainbowBeaconSides(pose, quads,
			(float) beam.getXsize(), (float) beam.getYsize(), (float) beam.getZsize(), alpha));
		poses.popPose();
	}

	private static Entity nearestInteraction(List<Entity> interactions, Entity near) {
		Entity best = null;
		double bestSq = FISH_PAIR_RADIUS * FISH_PAIR_RADIUS;
		for (Entity candidate : interactions) {
			double distanceSq = candidate.position().distanceToSqr(near.position());
			if (distanceSq >= bestSq) continue;
			bestSq = distanceSq;
			best = candidate;
		}
		return best;
	}

	/** One tracked individual's last logged draw state, bounded for long debug sessions. */
	private static final java.util.Map<java.util.UUID, String> lastWaypointState =
		new java.util.LinkedHashMap<>(128, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(java.util.Map.Entry<java.util.UUID, String> eldest) {
				return size() > 512;
			}
		};

	/**
	 * Logs a tracked individual's draw state only when it actually changes — this runs
	 * every frame, so logging every call would be an unreadable flood. What this shows
	 * that nothing else does is the renderer's own decision: not just that a sighting
	 * exists or a pin is active, but which of the two actually won for a given
	 * individual at a given moment, which is exactly the question a "why did the wrong
	 * one show" bug report turns on.
	 */
	private static void logWaypointState(Critter critter, java.util.UUID id, String state) {
		if (!DebugLog.isEnabled()) {
			if (!lastWaypointState.isEmpty()) lastWaypointState.clear();
			return;
		}
		if (state.equals(lastWaypointState.get(id))) return;
		lastWaypointState.put(id, state);
		DebugLog.line("DRAW", critter.name() + " id=" + id.toString().substring(0, 8) + " -> " + state);
	}

	/**
	 * The vanilla bounding box translated to this frame's interpolated position. Size
	 * changes arrive on game ticks, so interpolate those too; this keeps the capture
	 * shrink as smooth as the entity's rendered movement.
	 */
	private static AABB hitboxFor(Entity entity) {
		AABB raw = entity.getBoundingBox();
		BoundsTransition dimensions = smoothDimensions(entity, raw.getXsize(), raw.getYsize());
		double captureScale = RecatchSpots.captureScale(entity.getUUID());
		double renderedWidth = captureScale < 1.0
			? dimensions.baseWidth * captureScale : dimensions.displayedWidth;
		double renderedHeight = captureScale < 1.0
			? dimensions.baseHeight * captureScale : dimensions.displayedHeight;
		Vec3 rendered = renderPosition(entity);
		double centerX = rendered.x + raw.getCenter().x - entity.getX();
		double centerZ = rendered.z + raw.getCenter().z - entity.getZ();
		double minY = rendered.y + raw.minY - entity.getY();
		double halfWidth = renderedWidth * 0.5;
		return new AABB(centerX - halfWidth, minY, centerZ - halfWidth,
			centerX + halfWidth, minY + renderedHeight, centerZ + halfWidth);
	}

	/** Returns frame-smooth dimensions while retaining the latest server value as truth. */
	private static BoundsTransition smoothDimensions(Entity entity, double width, double height) {
		long now = System.nanoTime();
		BoundsTransition transition = BOUNDS_TRANSITIONS.computeIfAbsent(entity.getUUID(),
			ignored -> new BoundsTransition(width, height, now));
		transition.baseWidth = Math.max(transition.baseWidth, width);
		transition.baseHeight = Math.max(transition.baseHeight, height);
		double progress = Math.min(1.0,
			(double) (now - transition.startedAt) / BOUNDS_TRANSITION_NANOS);
		transition.displayedWidth = transition.fromWidth
			+ (transition.targetWidth - transition.fromWidth) * progress;
		transition.displayedHeight = transition.fromHeight
			+ (transition.targetHeight - transition.fromHeight) * progress;
		if (Math.abs(width - transition.targetWidth) > 1.0e-4
			|| Math.abs(height - transition.targetHeight) > 1.0e-4) {
			transition.fromWidth = transition.displayedWidth;
			transition.fromHeight = transition.displayedHeight;
			transition.targetWidth = width;
			transition.targetHeight = height;
			transition.startedAt = now;
		}
		return transition;
	}

	/** Applies presentation geometry consistently in ordinary and Sparkling paths. */
	private static AABB presentedHitbox(Critter critter, Entity entity) {
		return CritterMarkerGeometry.usesBlockSize(critter)
			? scaledBlockHitbox(critter, entity, renderPosition(entity))
			: hitboxFor(entity);
	}

	/** Keeps custom geometry fixed except during a uniform entity-scale animation. */
	private static AABB scaledBlockHitbox(Critter critter, Entity entity, Vec3 base) {
		AABB raw = entity.getBoundingBox();
		BoundsTransition dimensions = smoothDimensions(entity, raw.getXsize(), raw.getYsize());
		double captureScale = RecatchSpots.captureScale(entity.getUUID());
		double widthScale = dimensions.baseWidth <= 0.0
			? 1.0 : dimensions.displayedWidth / dimensions.baseWidth;
		double heightScale = dimensions.baseHeight <= 0.0
			? 1.0 : dimensions.displayedHeight / dimensions.baseHeight;
		double scale = captureScale < 1.0 ? captureScale
			: Math.abs(widthScale - heightScale) <= 0.08
				? Math.max(0.0, Math.min(1.0, (widthScale + heightScale) * 0.5)) : 1.0;
		return CritterMarkerGeometry.presented(critter, base, scale);
	}

	/**
	 * Keeps the configured marker size while following its rendered anchor. Duplico's
	 * interaction body trails its label while moving, so the label is smoother.
	 */
	private static AABB trackedHitboxFor(TrackedWaypoint tracked,
			CritterEntities.Sighting sighting) {
		Entity body = sighting.mob();
		if (tracked.useRealHitbox()) return hitboxFor(body);
		if ("Duplico".equals(tracked.critterName())) {
			// Duplico's label is centred above its disguised body. Preserve the same
			// vertical anchor used by its normal live marker while applying capture
			// shrink, otherwise the box jumps upward whenever the body is in range.
			return scaledBlockHitbox(sighting.critter(), body,
				renderPosition(sighting.label()).add(0.0, -1.25, 0.0));
		}
		return scaledBlockHitbox(sighting.critter(), body, renderPosition(body));
	}

	/** Matches vanilla entity rendering instead of stepping between 20 tick positions. */
	private static Vec3 renderPosition(Entity entity) {
		return entity.getPosition(framePartialTick);
	}

	/**
	 * Uses the confirmed Bat dimensions when only a remembered position remains,
	 * avoiding a visible size change as the live entity enters or leaves range.
	 */
	private static AABB approximateHitbox(BlockPos pos) {
		double halfWidth = 0.25;
		double height = 0.9;
		double centreX = pos.getX() + 0.5;
		double centreZ = pos.getZ() + 0.5;
		return new AABB(centreX - halfWidth, pos.getY(), centreZ - halfWidth,
			centreX + halfWidth, pos.getY() + height, centreZ + halfWidth);
	}

	/** Whether this exact entity already has a recatch pin. */
	private static boolean isRecatchPinned(java.util.UUID entityId) {
		return RecatchSpots.isPinned(entityId);
	}

	/**
	 * Draws live or remembered waypoints for tracked stationary/hidden critters and
	 * Hideyho. Live sightings win over memory. Capturing or recatch-pinned individuals
	 * are suppressed so the recatch marker is the only active mark.
	 */
	private static void renderTrackedWaypoints(LevelRenderContext context,
			WaypointRenderBackend backend, Vec3 camera) {
		// This remains session-independent so a Hideonfloor seen from the starting
		// ship's Forest boundary can be marked before the player submits a ticket.
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		SafariBiome biome = SafariLocation.biome();

		PoseStack poses = context.poseStack();
		// Safe Mode uses depth-tested rendering for hidden-entity waypoints.
		boolean anyDrawn = false;
		boolean anyDepthTested = false;
		boolean anyThroughWalls = false;

		record Found(Markers.Marker marker, double distance, boolean seeThrough) {
		}
		List<Found> drawn = new java.util.ArrayList<>();

		if (biome != null) {
			for (TrackedWaypoint tracked : TRACKED_WAYPOINTS) {
				if (biome != tracked.biome()) continue;

				Critter critter = Critters.byName(tracked.critterName());
				if (critter == null) continue;
				boolean waypointEnabled = tracked.enabled().test(display);
				boolean hideOrdinary = SparklingMode.hideOrdinaryWaypoint(critter,
					SessionManager.current());

				// Use the shared entity color rules; diagnostic color has highest priority.
				boolean diagnostic = BuildVersion.DEVELOPER
					&& ConfigManager.get().advanced.showAllCritterHitboxes;
				int baseColour = critterHitboxColour(critter, false);
				boolean genericRemembered = GENERIC_REMEMBERED_CRITTERS.contains(critter.name());
				if (waypointEnabled && !hideOrdinary && !display.hidePossibleWaypoints) {
					for (BlockPos candidate : StillCritters.candidatesFor(critter)) {
						AABB box = "Bloodbat".equals(critter.name())
							? approximateHitbox(candidate) : new AABB(candidate);
						if (!drawBox(poses, backend, LINES, box, camera, baseColour)) continue;
						anyDrawn = true;
						anyThroughWalls = true;
					drawn.add(new Found(new Markers.Marker(box, tracked.label() + " (Possible)",
							baseColour, genericRemembered ? Markers.Style.HIGHLIGHT : Markers.Style.WAYPOINT,
							true),
							Math.sqrt(distanceSquared(box, camera)), true));
					}
				}
				// Once confirmed, these are critter hitboxes rather than possible-location
				// waypoints and follow the global hitbox toggle.
				if (!display.enableHitboxes) continue;
				Set<java.util.UUID> liveIds = new java.util.HashSet<>();

				// Every currently loaded individual, not just the first found — there
				// is usually more than one, and stopping at one meant only ever
				// drawing whichever the scan happened to reach first that frame.
				for (CritterEntities.Sighting sighting : CritterEntities.all(critter)) {
					boolean sparkling = SparklingWatch.presentsAsSparkling(sighting);
					Entity entity = sighting.mob();
					if (entity == null) continue;
					if (RecatchSpots.isCaptureArtifact(critter, entity)) continue;
					if (sparkling && SparklingWatch.isOutstanding(sighting)) {
						liveIds.add(entity.getUUID());
						continue;
					}
					if (SparklingMode.hideOrdinaryHitbox(critter, sparkling)) continue;
					if (StillCritters.isResolved(entity.getUUID())) continue;
					if (SafeMode.hiddenCritter(critter, sparkling)
						&& !StillCritters.isVisiblyConfirmed(entity.getUUID())) continue;
					liveIds.add(entity.getUUID());

					// Recatch takes precedence, but only for the specific individual
					// pinned: matching on species alone hid every individual of that
					// species while any one of them was pinned.
					if (isRecatchPinned(entity.getUUID())) {
						logWaypointState(critter, entity.getUUID(), "SUPPRESSED (recatch, live)");
						continue;
					}
					logWaypointState(critter, entity.getUUID(), "LIVE");

					// Sparkling wins over whatever colour this species would otherwise
					// use, the same as the generic hitbox renderer does.
					int uniqueColour = display.hitboxEntityColorOverride
						? SparklingMode.uniqueHitboxColour(critter, SessionManager.current()) : 0;
					int colour = diagnostic ? 0xFFFF00FF
						: sparkling ? sparklingColour()
						: uniqueColour != 0 ? uniqueColour : baseColour;

					AABB box = trackedHitboxFor(tracked, sighting);
					boolean seeThrough = genericRemembered
						? !SafeMode.critterHitboxes(sparkling) || diagnostic
						: StillCritters.persistentThroughWalls(entity.getUUID())
							|| !SafeMode.hiddenCritter(critter, sparkling);
					if (!(sparkling
						? drawRainbowBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES,
							box, camera)
						: drawBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES,
							box, camera, colour))) continue;
					anyDrawn = true;
					if (seeThrough) anyThroughWalls = true;
					else anyDepthTested = true;

					String pity = !genericRemembered || display.hitboxPityTitle
						? Markers.pityLabel(critter, entity.getUUID()) : "";
					String label = (sparkling ? "SPARKLING " : "") + tracked.label() + pity;
					drawn.add(new Found(new Markers.Marker(box, label, colour,
						genericRemembered ? Markers.Style.HIGHLIGHT : Markers.Style.WAYPOINT),
						Math.sqrt(distanceSquared(box, camera)), seeThrough));
					markVanillaNameReplaced(sighting);
				}

				// Every remembered individual not already covered by a live sighting.
				// Static sightings remain trusted until their location is checked empty.
				for (StillCritters.Sighted remembered : StillCritters.entriesFor(critter)) {
					if (hideOrdinary && !remembered.sparkling()
						|| SparklingMode.hideOrdinaryHitbox(critter, remembered.sparkling())) continue;
					if (SafeMode.hiddenCritter(critter, remembered.sparkling())
						&& !StillCritters.isVisiblyConfirmed(remembered.id())) continue;
					if (liveIds.contains(remembered.id())) continue;
					if (isRecatchPinned(remembered.id())) {
						logWaypointState(critter, remembered.id(), "SUPPRESSED (recatch, remembered)");
						continue;
					}
					logWaypointState(critter, remembered.id(), "REMEMBERED");

					AABB box = tracked.useRealHitbox()
						? remembered.box() : new AABB(remembered.pos());
					int uniqueColour = display.hitboxEntityColorOverride
						? SparklingMode.uniqueHitboxColour(critter, SessionManager.current()) : 0;
					int colour = diagnostic ? 0xFFFF00FF
						: remembered.sparkling() ? sparklingColour()
						: uniqueColour != 0 ? uniqueColour : baseColour;
					boolean seeThrough = genericRemembered
						? !SafeMode.critterHitboxes(remembered.sparkling()) || diagnostic
						: remembered.persistentThroughWalls()
							|| !SafeMode.hiddenCritter(critter, remembered.sparkling());
					if (!(remembered.sparkling()
						? drawRainbowBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES,
							box, camera)
						: drawBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES,
							box, camera, colour))) continue;
					anyDrawn = true;
					if (seeThrough) anyThroughWalls = true;
					else anyDepthTested = true;
					String pity = !genericRemembered || display.hitboxPityTitle
						? Markers.pityLabel(critter, remembered.id()) : "";
					String label = (remembered.sparkling() ? "SPARKLING " : "")
						+ tracked.label() + pity;
					drawn.add(new Found(new Markers.Marker(box, label, colour,
						genericRemembered ? Markers.Style.HIGHLIGHT : Markers.Style.WAYPOINT),
						Math.sqrt(distanceSquared(box, camera)), seeThrough));
				}
			}
		}

		if (biome == SafariBiome.HAUNTED) {
			Critter hideyhoCritter = Critters.byName("Hideyho");
			int configuredColour = Colours.argb(display.hideyhoColour, 0xFFFF55FF);
			int colour = display.hitboxEntityColorOverride
				? display.hitboxRarityColour && hideyhoCritter != null
					? 0xFF000000 | hideyhoCritter.rarity().colour()
					: Colours.argb(display.hitboxColour, 0xFFFFFFFF)
				: configuredColour;
			for (BlockPos possible : display.hideyhoSolver && !display.hidePossibleWaypoints
				&& SparklingMode.showHideyhoLocations()
				? HideyhoSolver.candidates() : java.util.Set.<BlockPos>of()) {
				AABB box = new AABB(
					possible.getX(), possible.getY() - 2, possible.getZ(),
					possible.getX() + 1, possible.getY(), possible.getZ() + 1);
				if (!drawBox(poses, backend, LINES, box, camera, colour)) continue;
				anyDrawn = true;
				anyThroughWalls = true;
				drawn.add(new Found(new Markers.Marker(box, "Hideyho (Possible)", colour,
					Markers.Style.WAYPOINT, true), Math.sqrt(distanceSquared(box, camera)), true));
			}
			BlockPos hideyho = HideyhoSolver.position();
			if (display.enableHitboxes && hideyho != null
				&& !SparklingMode.hideOrdinaryHitbox(hideyhoCritter, HideyhoSolver.sparkling())) {
				// Confirmed Hideyhos can stand on a block edge. Preserve the entity's
				// exact horizontal centre instead of snapping its box to blockPosition().
				Vec3 exact = HideyhoSolver.positionExact();
				double centreX = exact == null ? hideyho.getX() + 0.5 : exact.x;
				double centreZ = exact == null ? hideyho.getZ() + 0.5 : exact.z;
				// Keep the established vertical placement: two blocks tall, with its top
				// at the catalog's Hideyho position.
				AABB box = hideyhoHitbox(new Vec3(centreX, hideyho.getY(), centreZ), hideyho);
				// This phase's position was directly confirmed already and Hideyho cannot
				// move again until its explicit chat transition changes the phase.
				boolean seeThrough = !HideyhoSolver.sparkling()
					|| !SafeMode.critterHitboxes(true);
				int uniqueColour = display.hitboxEntityColorOverride
					? SparklingMode.uniqueHitboxColour(hideyhoCritter, SessionManager.current()) : 0;
				int liveColour = HideyhoSolver.sparkling() ? sparklingColour()
					: uniqueColour != 0 ? uniqueColour : colour;
				boolean boxDrawn = HideyhoSolver.sparkling()
					? drawRainbowBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES,
						box, camera)
					: drawBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES,
						box, camera, liveColour);
				if (boxDrawn) {
					anyDrawn = true;
					if (seeThrough) anyThroughWalls = true;
					else anyDepthTested = true;

					String hideyhoLabel = (HideyhoSolver.sparkling() ? "SPARKLING " : "")
						+ (HideyhoSolver.phase() == HideyhoSolver.Phase.END
						? "Hideyho (END)" : "Hideyho (START)");
					drawn.add(new Found(new Markers.Marker(box, hideyhoLabel, liveColour,
						Markers.Style.WAYPOINT), Math.sqrt(distanceSquared(box, camera)), seeThrough));
					for (CritterEntities.Sighting sighting : CritterEntities.all()) {
						if ("Hideyho".equals(sighting.critter().name())) {
							markVanillaNameReplaced(sighting);
						}
					}
				}
			}
		}

		if (!anyDrawn) return;
		if (anyDepthTested) backend.flush(RenderTypes.LINES);
		if (anyThroughWalls) backend.flush(LINES);

		if (showsWaypointNametags()) {
			for (Found found : drawn) {
				label(poses, backend, found.marker(), camera, found.distance(), found.seeThrough());
			}
		}
	}

	/** Hideyho's label is its body, but its entity box is not the desired 1x2 solver box. */
	private static AABB hideyhoHitbox(Vec3 exact, BlockPos upperBlock) {
		double centreX = exact == null ? upperBlock.getX() + 0.5 : exact.x;
		double centreZ = exact == null ? upperBlock.getZ() + 0.5 : exact.z;
		return new AABB(centreX - 0.5, upperBlock.getY() - 2, centreZ - 0.5,
			centreX + 0.5, upperBlock.getY(), centreZ + 0.5);
	}

	/** Draws one box already positioned in world space, relative to the camera. */
	private static boolean drawBox(PoseStack poses, WaypointRenderBackend backend, RenderType lineType,
							AABB box, Vec3 camera, int colour) {
		if (!visible(box)) return false;
		poses.pushPose();
		poses.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
		backend.geometry(lineType, (pose, lines) -> box(pose, lines, (float) box.getXsize(),
			(float) box.getYsize(), (float) box.getZsize(), colour));
		poses.popPose();
		return true;
	}

	/** Draws one animated multi-colour Sparkling box without creating per-frame geometry objects. */
	private static boolean drawRainbowBox(PoseStack poses, WaypointRenderBackend backend,
			RenderType lineType, AABB box, Vec3 camera) {
		if (!visible(box)) return false;
		float phase = RainbowColours.phase(RainbowColours.frameId());
		poses.pushPose();
		poses.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
		backend.geometry(lineType, (pose, lines) -> rainbowBox(pose, lines,
			(float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize(), phase));
		poses.popPose();
		return true;
	}

	private static double distanceSquared(AABB box, Vec3 point) {
		double dx = (box.minX + box.maxX) * 0.5 - point.x;
		double dy = (box.minY + box.maxY) * 0.5 - point.y;
		double dz = (box.minZ + box.maxZ) * 0.5 - point.z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** Conservatively culls geometry fully outside the camera frustum. */
	private static boolean visible(AABB box) {
		var frustum = ClientCompat.camera().getCullFrustum();
		return frustum == null || frustum.isVisible(box.inflate(1.0).expandTowards(0, 1.0, 0));
	}

	/**
	 * A single filled quad at the top of a block-sized box — the floor drop face
	 * highlight. Position and colour only, in the winding order confirmed directly
	 * against Fabric's own rendering documentation for this exact vanilla pipeline.
	 */
	private static void topFace(PoseStack.Pose stackPose, VertexConsumer quad,
							float xSize, float zSize, float y, int colour) {
		var pose = stackPose.pose();
		float red = ((colour >>> 16) & 0xFF) / 255f;
		float green = ((colour >>> 8) & 0xFF) / 255f;
		float blue = (colour & 0xFF) / 255f;
		float alpha = ((colour >>> 24) & 0xFF) / 255f;

		quad.addVertex(pose, 0, y, zSize).setColor(red, green, blue, alpha);
		quad.addVertex(pose, xSize, y, zSize).setColor(red, green, blue, alpha);
		quad.addVertex(pose, xSize, y, 0).setColor(red, green, blue, alpha);
		quad.addVertex(pose, 0, y, 0).setColor(red, green, blue, alpha);

		// Submit the reverse winding too. The floor-drop position can put this plane
		// above or below the camera as the player jumps, and the filled pipeline culls
		// whichever side faces away from the camera.
		quad.addVertex(pose, 0, y, 0).setColor(red, green, blue, alpha);
		quad.addVertex(pose, xSize, y, 0).setColor(red, green, blue, alpha);
		quad.addVertex(pose, xSize, y, zSize).setColor(red, green, blue, alpha);
		quad.addVertex(pose, 0, y, zSize).setColor(red, green, blue, alpha);
	}

	/** Four segmented faces let the beacon interpolate smoothly through several hues. */
	private static void rainbowBeaconSides(PoseStack.Pose stackPose, VertexConsumer quad,
			float xSize, float ySize, float zSize, float alpha) {
		float scroll = -(System.currentTimeMillis() % 2_000L) / 2_000f;
		float phase = RainbowColours.phase(RainbowColours.frameId());
		for (int segment = 0; segment < SPARKLING_BEAM_GRADIENT_SEGMENTS; segment++) {
			float fraction0 = segment / (float) SPARKLING_BEAM_GRADIENT_SEGMENTS;
			float fraction1 = (segment + 1) / (float) SPARKLING_BEAM_GRADIENT_SEGMENTS;
			float y0 = ySize * fraction0;
			float y1 = ySize * fraction1;
			float v0 = scroll + y0 / 4f;
			float v1 = scroll + y1 / 4f;
			// Two complete gradients keep several colours visible even on a short beam.
			int colour0 = RainbowColours.phased(phase, fraction0 * 2f, 0.55f, alpha);
			int colour1 = RainbowColours.phased(phase, fraction1 * 2f, 0.55f, alpha);
			beaconFace(stackPose, quad, colour0, colour1, v0, v1,
				0, y0, 0, xSize, y0, 0, xSize, y1, 0, 0, y1, 0);
			beaconFace(stackPose, quad, colour0, colour1, v0, v1,
				xSize, y0, 0, xSize, y0, zSize, xSize, y1, zSize, xSize, y1, 0);
			beaconFace(stackPose, quad, colour0, colour1, v0, v1,
				xSize, y0, zSize, 0, y0, zSize, 0, y1, zSize, xSize, y1, zSize);
			beaconFace(stackPose, quad, colour0, colour1, v0, v1,
				0, y0, zSize, 0, y0, 0, 0, y1, 0, 0, y1, zSize);
		}
	}

	private static void beaconFace(PoseStack.Pose stackPose, VertexConsumer quad,
			int bottomColour, int topColour, float vBottom, float vTop,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3) {
		var pose = stackPose.pose();
		quad.addVertex(pose, x0, y0, z0).setColor(bottomColour).setUv(0, vBottom)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(stackPose, 0, 1, 0);
		quad.addVertex(pose, x1, y1, z1).setColor(bottomColour).setUv(1, vBottom)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(stackPose, 0, 1, 0);
		quad.addVertex(pose, x2, y2, z2).setColor(topColour).setUv(1, vTop)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(stackPose, 0, 1, 0);
		quad.addVertex(pose, x3, y3, z3).setColor(topColour).setUv(0, vTop)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(stackPose, 0, 1, 0);
	}

	private static void box(PoseStack.Pose pose, VertexConsumer lines,
							float xSize, float ySize, float zSize, int colour) {
		float o = 0.005f;
		float x0 = -o;
		float y0 = -o;
		float z0 = -o;
		float x1 = xSize + o;
		float y1 = ySize + o;
		float z1 = zSize + o;
		float red = ((colour >>> 16) & 0xFF) / 255f;
		float green = ((colour >>> 8) & 0xFF) / 255f;
		float blue = (colour & 0xFF) / 255f;
		// The picker offers alpha, so a colour set part-transparent draws that way.
		float alpha = ((colour >>> 24) & 0xFF) / 255f;

		line(pose, lines, x0, y0, z0, x1, y0, z0, red, green, blue, alpha);
		line(pose, lines, x1, y0, z0, x1, y0, z1, red, green, blue, alpha);
		line(pose, lines, x1, y0, z1, x0, y0, z1, red, green, blue, alpha);
		line(pose, lines, x0, y0, z1, x0, y0, z0, red, green, blue, alpha);
		line(pose, lines, x0, y1, z0, x1, y1, z0, red, green, blue, alpha);
		line(pose, lines, x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
		line(pose, lines, x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
		line(pose, lines, x0, y1, z1, x0, y1, z0, red, green, blue, alpha);
		line(pose, lines, x0, y0, z0, x0, y1, z0, red, green, blue, alpha);
		line(pose, lines, x1, y0, z0, x1, y1, z0, red, green, blue, alpha);
		line(pose, lines, x1, y0, z1, x1, y1, z1, red, green, blue, alpha);
		line(pose, lines, x0, y0, z1, x0, y1, z1, red, green, blue, alpha);
	}

	/** One full animated gradient around each half of the Sparkling box. */
	private static void rainbowBox(PoseStack.Pose pose, VertexConsumer lines,
			float xSize, float ySize, float zSize, float phase) {
		float o = 0.005f;
		float x0 = -o;
		float y0 = -o;
		float z0 = -o;
		float x1 = xSize + o;
		float y1 = ySize + o;
		float z1 = zSize + o;
		int c0 = RainbowColours.phased(phase, 0.00f, 0.55f, 1f);
		int c1 = RainbowColours.phased(phase, 0.25f, 0.55f, 1f);
		int c2 = RainbowColours.phased(phase, 0.50f, 0.55f, 1f);
		int c3 = RainbowColours.phased(phase, 0.75f, 0.55f, 1f);
		int c4 = RainbowColours.phased(phase, 1.00f, 0.55f, 1f);
		int c5 = RainbowColours.phased(phase, 1.25f, 0.55f, 1f);
		int c6 = RainbowColours.phased(phase, 1.50f, 0.55f, 1f);
		int c7 = RainbowColours.phased(phase, 1.75f, 0.55f, 1f);

		gradientLine(pose, lines, x0, y0, z0, x1, y0, z0, c0, c1);
		gradientLine(pose, lines, x1, y0, z0, x1, y0, z1, c1, c2);
		gradientLine(pose, lines, x1, y0, z1, x0, y0, z1, c2, c3);
		gradientLine(pose, lines, x0, y0, z1, x0, y0, z0, c3, c4);
		gradientLine(pose, lines, x0, y1, z0, x1, y1, z0, c4, c5);
		gradientLine(pose, lines, x1, y1, z0, x1, y1, z1, c5, c6);
		gradientLine(pose, lines, x1, y1, z1, x0, y1, z1, c6, c7);
		gradientLine(pose, lines, x0, y1, z1, x0, y1, z0, c7, c0);
		gradientLine(pose, lines, x0, y0, z0, x0, y1, z0, c0, c4);
		gradientLine(pose, lines, x1, y0, z0, x1, y1, z0, c1, c5);
		gradientLine(pose, lines, x1, y0, z1, x1, y1, z1, c2, c6);
		gradientLine(pose, lines, x0, y0, z1, x0, y1, z1, c3, c7);
	}

	private static void gradientLine(PoseStack.Pose pose, VertexConsumer lines,
			float x1, float y1, float z1, float x2, float y2, float z2,
			int colour1, int colour2) {
		float nx = x2 - x1;
		float ny = y2 - y1;
		float nz = z2 - z1;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length == 0) return;
		nx /= length;
		ny /= length;
		nz /= length;
		lines.addVertex(pose, x1, y1, z1).setColor(colour1)
			.setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
		lines.addVertex(pose, x2, y2, z2).setColor(colour2)
			.setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
	}

	private static void line(PoseStack.Pose pose, VertexConsumer lines,
							 float x1, float y1, float z1, float x2, float y2, float z2,
							 float r, float g, float b, float a) {
		// The line format wants a normal along the segment and a width per vertex.
		// Leaving the width off is a hard crash — "Missing elements in vertex" — rather
		// than a default, which is what the first version of this did.
		float nx = x2 - x1;
		float ny = y2 - y1;
		float nz = z2 - z1;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length == 0) return;
		nx /= length;
		ny /= length;
		nz /= length;

		lines.addVertex(pose, x1, y1, z1).setColor(r, g, b, a)
			.setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
		lines.addVertex(pose, x2, y2, z2).setColor(r, g, b, a)
			.setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
	}

	/**
	 * The name above the box, turned to face the camera.
	 *
	 * <p>Built the way vanilla builds a mob's name tag: translate to the spot, apply the
	 * camera's rotation so it always faces you, then scale down to text size. Drawn
	 * see-through and full-bright so it reads at any light level and through walls, like
	 * the box under it.
	 */
	private static void label(PoseStack poses, WaypointRenderBackend backend,
							  Markers.Marker marker, Vec3 camera, double distance, boolean seeThrough) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		AABB box = marker.box();
		boolean showDistance = ConfigManager.get().display.waypointDistance;
		long roundedDistance = showDistance ? Math.round(distance) : -1;
		boolean sparkling = marker.label().startsWith("SPARKLING ");
		LabelKey key = new LabelKey(marker.label(), roundedDistance,
			sparkling ? RainbowColours.phaseBucket(100) : -1);
		CachedLabel cached = LABEL_CACHE.computeIfAbsent(key, ignored -> {
			Component text = sparkling ? rainbowLabel(marker.label()) : Component.literal(marker.label());
			if (showDistance) {
				text = text.copy().append(Component.literal(" " + roundedDistance + "m")
					.withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			return new CachedLabel(text.getVisualOrderText(), font.width(text));
		});

		poses.pushPose();
		poses.translate(
			box.getCenter().x - camera.x,
			box.maxY + LABEL_HEIGHT - camera.y,
			box.getCenter().z - camera.z);
		poses.mulPose(backend.cameraRotation());
		poses.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

		float x = -cached.width() / 2.0f;
		// SEE_THROUGH renders the text with no depth test at all, independent of
		// whatever type the hitbox itself used — the box respecting depth says
		// nothing about the label floating above it doing the same, since text goes
		// through an entirely separate rendering path. NORMAL is what actually ties
		// the label to the same wall the box now respects under Safe Mode.
		backend.text(poses, cached.sequence(), x,
			seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
			sparkling ? 0xFFFFFFFF : marker.colour() | 0xFF000000,
			0x40000000, LightCoordsUtil.FULL_BRIGHT);
		poses.popPose();
	}

	/** Builds a per-character rainbow component for Sparkling waypoint labels. */
	private static Component rainbowLabel(String value) {
		var result = Component.empty();
		int cursor = 0;
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < value.length(); i++) {
			String character = String.valueOf(value.charAt(i));
			int colour = UIDraw.rainbowAt(cursor, 0.55f) & 0xFFFFFF;
			result.append(Component.literal(character)
				.withStyle(style -> style.withColor(colour)));
			cursor += font.width(character);
		}
		return result;
	}

	/** The same priority chain used by ordinary critter hitboxes and recatch markers. */
	public static int critterHitboxColour(Critter critter, boolean sparkling) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		boolean diagnostic = BuildVersion.DEVELOPER
			&& ConfigManager.get().advanced.showAllCritterHitboxes;
		boolean dedicatedWaypointCritter = CUSTOM_WAYPOINT_CRITTERS.contains(critter.name())
			|| "Hideyho".equals(critter.name());
		boolean allowSharedColourRules = !dedicatedWaypointCritter
			|| display.hitboxEntityColorOverride;
		int uniqueColour = allowSharedColourRules
			? SparklingMode.uniqueHitboxColour(critter, SessionManager.current()) : 0;
		int configured = !allowSharedColourRules ? switch (critter.name()) {
			case "Hideyho" -> Colours.argb(display.hideyhoColour, 0xFF5F00FF);
			case "Hideonwall" -> Colours.argb(display.hideonwallColour, 0xFFFF00FF);
			case "Duplico" -> Colours.argb(display.duplicoColour, 0xFFFF0000);
			case "Bloodbat" -> Colours.argb(display.bloodbatColour, 0xFFBFFF00);
			case "Hideonfloor" -> Colours.argb(display.hideonfloorColour, 0xFFFF00FF);
			default -> display.hitboxRarityColour ? 0xFF000000 | critter.rarity().colour()
				: Colours.argb(display.hitboxColour, 0xFFFFFFFF);
		} : display.hitboxRarityColour ? 0xFF000000 | critter.rarity().colour()
			: Colours.argb(display.hitboxColour, 0xFFFFFFFF);
		return diagnostic ? 0xFFFF00FF
			: sparkling ? sparklingColour()
				: uniqueColour != 0 ? uniqueColour
					: configured;
	}
}
