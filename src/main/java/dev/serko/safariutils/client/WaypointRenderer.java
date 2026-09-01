package dev.serko.safariutils.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
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
		WaypointRenderBackend.register(WaypointRenderer::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;
		if (!SafariLocation.inside()) return;
		framePartialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		List<Markers.Marker> markers = Markers.collect();

		// The pose is at the camera, so world positions are drawn relative to it.
		PoseStack poses = context.poseStack();
		WaypointRenderBackend backend = new WaypointRenderBackend(context);
		Vec3 camera = backend.cameraPosition();

		if (!markers.isEmpty()) {
			for (Markers.Marker marker : markers) {
				if (tooFar(marker, camera)) continue;

				// A highlight is drawn with the vanilla line type, which is
				// depth-tested, so it only shows where the thing itself would be
				// visible, regardless of seeThrough — that field only matters for
				// a waypoint, which otherwise defaults to through-terrain. See
				// Marker.seeThrough's own doc for why a waypoint needed this at
				// all: floor drops and nests both needed to depth-test their own
				// box specifically, without losing their label the way switching
				// them to HIGHLIGHT entirely would have.
				boolean seeThrough = marker.style() == Markers.Style.WAYPOINT && marker.seeThrough();
				RenderType lineType = seeThrough ? LINES : RenderTypes.LINES;

				AABB box = marker.box();
				poses.pushPose();
				poses.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
				backend.geometry(lineType, (pose, lines) -> box(pose, lines, (float) box.getXsize(),
					(float) box.getYsize(), (float) box.getZsize(), marker.colour()));
				poses.popPose();
			}
			// Flushed here rather than left to the end of the frame, so every box is
			// drawn before the first label and a waypoint reads as one thing.
			backend.flush(LINES);
			backend.flush(RenderTypes.LINES);

			for (Markers.Marker marker : markers) {
				// Only a waypoint is named: a highlight sits on something you can
				// already see, so a label over it is just something else to read.
				if (marker.style() != Markers.Style.WAYPOINT || tooFar(marker, camera)) continue;
				label(poses, backend, marker, camera,
					marker.box().getCenter().distanceTo(camera), marker.seeThrough());
			}
		}

		renderHitboxes(context, camera);
		renderSparklingMarkers(context, camera);
		renderTrackedWaypoints(context, camera);
		renderFloorDropFaces(context, camera);
		renderDiagnosticHitboxes(context, camera);
	}

	/** Animated colour shared by every Sparkling world-space element. */
	private static int sparklingColour() {
		float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
		return 0xFF000000 | (java.awt.Color.HSBtoRGB(phase, 0.55f, 1f) & 0xFFFFFF);
	}
	private static final double SPARKLING_BEAM_WIDTH = 0.56;
	private static final double SPARKLING_BEAM_CORE_WIDTH = 0.22;
	private static final double SPARKLING_BEAM_TOP = 320.0;

	/** Species with their own dedicated waypoint further down. */
	private static final Set<String> EXCLUDED_FROM_HITBOXES =
		Set.of("Hideonwall", "Duplico", "Hideonfloor", "Bloodbat");

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
			d -> d.highlightHideonfloor, d -> d.hideonfloorColour, SafariBiome.FOREST, false));

	private static final String FISH_NAME = "Flavor Packed Fish";
	private static final double FISH_PAIR_RADIUS = 2.5;

	/** Draws enabled diagnostic entity types in a distinct color. */
	private static void renderDiagnosticHitboxes(LevelRenderContext context, Vec3 camera) {
		if (!BuildVersion.DEVELOPER) return;
		if (!AdvancedUnlock.isUnlocked()) return;
		SafariConfig.AdvancedConfig advanced = ConfigManager.get().advanced;
		if (!advanced.showAllArmorStands && !advanced.showAllItemDisplays && !advanced.showAllInteractions
			&& !advanced.showAllBlockDisplays && !advanced.showAllTextDisplays) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		PoseStack poses = context.poseStack();
		WaypointRenderBackend backend = new WaypointRenderBackend(context);
		// Not RenderTypes.LINES: a debug hitbox exists specifically to find something
		// unidentified, which is exactly the situation where it might be behind
		// something — Safe Mode's whole point is withholding information the player
		// would not otherwise have, and a diagnostic tool the player deliberately
		// turned on is the opposite of that, so it takes precedence unconditionally.
		int colour = 0xFFFF00FF;
		double maxDistanceSq = (double) ConfigManager.get().display.hitboxDistance
			* ConfigManager.get().display.hitboxDistance;
		boolean anyDrawn = false;

		record Found(Markers.Marker marker, double distance) {
		}
		List<Found> drawn = new java.util.ArrayList<>();

		for (Entity entity : client.level.entitiesForRendering()) {
			EntityType<?> type = entity.getType();
			boolean wanted = (EntityTypeIds.is(type, "armor_stand") && advanced.showAllArmorStands)
				|| (EntityTypeIds.is(type, "item_display") && advanced.showAllItemDisplays)
				|| (EntityTypeIds.is(type, "interaction") && advanced.showAllInteractions)
				|| (EntityTypeIds.is(type, "block_display") && advanced.showAllBlockDisplays)
				|| (EntityTypeIds.is(type, "text_display") && advanced.showAllTextDisplays);
			if (!wanted) continue;

			AABB box = hitboxFor(entity);
			if (box.getCenter().distanceToSqr(camera) > maxDistanceSq) continue;
			drawBox(poses, backend, LINES, box, camera, colour);
			anyDrawn = true;

			String label = entity.hasCustomName() ? entity.getCustomName().getString() : "(unnamed)";
			drawn.add(new Found(new Markers.Marker(box, label, colour, Markers.Style.HIGHLIGHT),
				box.getCenter().distanceTo(camera)));
		}

		if (!anyDrawn) return;
		backend.flush(LINES);

		for (Found found : drawn) {
			label(poses, backend, found.marker(), camera, found.distance(), true);
		}
	}

	/** Draws depth-tested top faces without changing through-wall waypoint outlines. */
	private static void renderFloorDropFaces(LevelRenderContext context, Vec3 camera) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.floorDrops) return;
		SafariBiome biome = SafariLocation.biome();
		if (biome == null) return;
		if (SparklingMode.hideFloorDrops(biome, SessionManager.current())) return;

		PoseStack poses = context.poseStack();
		WaypointRenderBackend backend = new WaypointRenderBackend(context);
		int colour = Colours.argb(display.floorDropFaceColour, 0xFFA0FFD3);
		boolean anyDrawn = false;

		for (BlockPos pos : FloorDrops.positions(biome)) {
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
	private static void renderHitboxes(LevelRenderContext context, Vec3 camera) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.enableHitboxes) return;

		PoseStack poses = context.poseStack();
		WaypointRenderBackend backend = new WaypointRenderBackend(context);
		// Sparkling hitboxes specifically get the same non-depth-tested type the
		// tracked-waypoint species already use — a sparkling is rare enough to be
		// worth spotting through a wall the same way, whereas an ordinary hitbox
		// staying depth-tested is deliberate: see the class doc.
		int fixedColour = Colours.argb(display.hitboxColour, 0xFFFFFFFF);
		double maxDistanceSq = (double) display.hitboxDistance * display.hitboxDistance;
		boolean anyDrawn = false;
		boolean anyThroughWalls = false;

		record Found(Markers.Marker marker, double distance, boolean seeThrough) {
		}
		List<Found> drawn = new java.util.ArrayList<>();

		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			String name = sighting.critter().name();
			if (EXCLUDED_FROM_HITBOXES.contains(name)) continue;

			Entity entity = sighting.mob();
			if (entity == null) continue;
			if (EntityTypeIds.is(entity, "player")) continue;

			// A recatch pin replaces only that individual's ordinary hitbox.
			if (isRecatchPinned(entity.getUUID())) continue;

			// Diagnostic, Sparkling, unique-status, and configured colors apply in that order.
			boolean sparkling = SparklingWatch.isSparkling(sighting);
			if (SparklingMode.hideOrdinaryHitbox(sighting.critter(), sparkling)) continue;
			boolean diagnostic = BuildVersion.DEVELOPER && AdvancedUnlock.isUnlocked()
				&& ConfigManager.get().advanced.showAllCritterHitboxes;
			int uniqueColour = SparklingMode.uniqueHitboxColour(sighting.critter(),
				SessionManager.current());
			int colour = diagnostic ? 0xFFFF00FF
				: sparkling ? sparklingColour()
				: uniqueColour != 0 ? uniqueColour
				: display.hitboxRarityColour ? 0xFF000000 | sighting.critter().rarity().colour() : fixedColour;

			AABB box = hitboxFor(entity);
			if (box.getCenter().distanceToSqr(camera) > maxDistanceSq) continue;
			// Diagnostics override Safe Mode depth testing; ordinary hitboxes do not.
			boolean seeThrough = !SafeMode.critterHitboxes(sparkling) || diagnostic;
			if (seeThrough) {
				drawBox(poses, backend, LINES, box, camera, colour);
				anyThroughWalls = true;
			} else {
				drawBox(poses, backend, RenderTypes.LINES, box, camera, colour);
				anyDrawn = true;
			}

			String label = (sparkling ? "SPARKLING " : "") + sighting.critter().name()
				+ (display.hitboxPityTitle ? Markers.pityLabel(sighting.critter(), entity.getUUID()) : "");
			drawn.add(new Found(new Markers.Marker(box, label, colour, Markers.Style.HIGHLIGHT),
				box.getCenter().distanceTo(camera), seeThrough));
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			List<Entity> fishLabels = new java.util.ArrayList<>();
			List<Entity> interactions = new java.util.ArrayList<>();
			for (Entity fishLabel : client.level.entitiesForRendering()) {
				if (EntityTypeIds.is(fishLabel, "interaction")) interactions.add(fishLabel);
				else if (EntityTypeIds.is(fishLabel, "armor_stand") && fishLabel.hasCustomName()
					&& fishLabel.getCustomName().getString().contains(FISH_NAME)) {
					fishLabels.add(fishLabel);
				}
			}
			for (Entity fishLabel : fishLabels) {

				Entity wrapper = nearestInteraction(interactions, fishLabel);
				if (wrapper == null) continue;

				// The armor-stand label follows the falling fish with the same interpolation
				// as its visible model. Hypixel's interaction wrapper updates in coarser
				// steps, which made only this hitbox visibly hop while falling.
				Vec3 pos = renderPosition(fishLabel);
				double halfWidth = wrapper.getBbWidth() / 2.0;
				double height = wrapper.getBbHeight();
				AABB box = new AABB(pos.x - halfWidth, pos.y, pos.z - halfWidth,
					pos.x + halfWidth, pos.y + height, pos.z + halfWidth);
				if (box.getCenter().distanceToSqr(camera) > maxDistanceSq) continue;
				drawBox(poses, backend, RenderTypes.LINES, box, camera, fixedColour);
				anyDrawn = true;
			}
		}

		if (anyDrawn) backend.flush(RenderTypes.LINES);
		if (anyThroughWalls) backend.flush(LINES);
		if (!anyDrawn && !anyThroughWalls) return;

		for (Found found : drawn) {
			label(poses, backend, found.marker(), camera, found.distance(), found.seeThrough());
		}
	}

	/** Marks every detected Sparkling independently of the ordinary hitbox setting. */
	private static void renderSparklingMarkers(LevelRenderContext context, Vec3 camera) {
		PoseStack poses = context.poseStack();
		WaypointRenderBackend backend = new WaypointRenderBackend(context);
		boolean drawn = false;
		RenderType beamCore = RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false);
		RenderType beamGlow = RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true);

		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!SparklingWatch.isOutstanding(sighting) || sighting.mob() == null) continue;
			// Extra mode may remember a Sparkling before it is in view. Safe Mode
			// must not turn that retained knowledge into a beam through terrain.
			if (SafeMode.sparklingCritters()
				&& !VisibilityCheck.canSee(sighting.mob())
				&& !VisibilityCheck.canSeeVisibleName(sighting.label())) continue;
			AABB body = hitboxFor(sighting.mob());
			if (body.getCenter().distanceToSqr(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;

			double centreX = (body.minX + body.maxX) * 0.5;
			double centreZ = (body.minZ + body.maxZ) * 0.5;
			double half = SPARKLING_BEAM_WIDTH * 0.5;
			double top = Math.max(SPARKLING_BEAM_TOP, body.maxY + 64.0);
			AABB beam = new AABB(centreX - half, body.maxY, centreZ - half,
				centreX + half, top, centreZ + half);
			int colour = sparklingColour();
			drawBeaconBeam(poses, backend, beamGlow, beam, camera,
				(0x4C << 24) | (colour & 0xFFFFFF));
			double coreHalf = SPARKLING_BEAM_CORE_WIDTH * 0.5;
			AABB core = new AABB(centreX - coreHalf, body.maxY, centreZ - coreHalf,
				centreX + coreHalf, top, centreZ + coreHalf);
			drawBeaconBeam(poses, backend, beamCore, core, camera,
				(0xD0 << 24) | (colour & 0xFFFFFF));
			drawn = true;
		}

		if (!drawn) return;
		backend.flush(beamCore);
		backend.flush(beamGlow);
	}

	/** Uses Minecraft's own beacon texture and pipelines for a continuous animated beam. */
	private static void drawBeaconBeam(PoseStack poses, WaypointRenderBackend backend,
			RenderType type, AABB beam, Vec3 camera, int colour) {
		poses.pushPose();
		poses.translate(beam.minX - camera.x, beam.minY - camera.y, beam.minZ - camera.z);
		backend.geometry(type, (pose, quads) -> beaconSides(pose, quads,
			(float) beam.getXsize(), (float) beam.getYsize(), (float) beam.getZsize(), colour));
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

	/** One tracked individual's last logged draw state, so only a change is written. */
	private static final java.util.Map<java.util.UUID, String> lastWaypointState = new java.util.HashMap<>();

	/**
	 * Logs a tracked individual's draw state only when it actually changes — this runs
	 * every frame, so logging every call would be an unreadable flood. What this shows
	 * that nothing else does is the renderer's own decision: not just that a sighting
	 * exists or a pin is active, but which of the two actually won for a given
	 * individual at a given moment, which is exactly the question a "why did the wrong
	 * one show" bug report turns on.
	 */
	private static void logWaypointState(Critter critter, java.util.UUID id, String state) {
		if (!DebugLog.isEnabled()) return;
		if (state.equals(lastWaypointState.get(id))) return;
		lastWaypointState.put(id, state);
		DebugLog.line("DRAW", critter.name() + " id=" + id.toString().substring(0, 8) + " -> " + state);
	}

	/** The box to draw for one sighting — its real size at this rendered frame. */
	private static AABB hitboxFor(Entity entity) {
		Vec3 pos = renderPosition(entity);
		double halfWidth = entity.getBbWidth() / 2.0;
		double height = entity.getBbHeight();
		return new AABB(pos.x - halfWidth, pos.y, pos.z - halfWidth,
			pos.x + halfWidth, pos.y + height, pos.z + halfWidth);
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
	private static void renderTrackedWaypoints(LevelRenderContext context, Vec3 camera) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		SafariBiome biome = SafariLocation.biome();

		PoseStack poses = context.poseStack();
		WaypointRenderBackend backend = new WaypointRenderBackend(context);
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
				boolean diagnostic = BuildVersion.DEVELOPER && AdvancedUnlock.isUnlocked()
					&& ConfigManager.get().advanced.showAllCritterHitboxes;
				int baseColour = diagnostic ? 0xFFFF00FF
					: display.hitboxEntityColorOverride
					? (display.hitboxRarityColour
						? 0xFF000000 | critter.rarity().colour()
						: Colours.argb(display.hitboxColour, 0xFFFFFFFF))
					: Colours.argb(tracked.colour().apply(display), 0xFFAA55FF);
				if (waypointEnabled && !hideOrdinary && !display.hidePossibleWaypoints) {
					for (BlockPos candidate : StillCritters.candidatesFor(critter)) {
						AABB box = "Bloodbat".equals(critter.name())
							? approximateHitbox(candidate) : new AABB(candidate);
						drawBox(poses, backend, LINES, box, camera, baseColour);
						anyDrawn = true;
						anyThroughWalls = true;
						drawn.add(new Found(new Markers.Marker(box, tracked.label() + " (Possible)",
							baseColour, Markers.Style.WAYPOINT, true), box.getCenter().distanceTo(camera), true));
					}
				}
				// Once confirmed, these are critter hitboxes rather than possible-location
				// waypoints and follow the global hitbox toggle.
				if (!display.enableHitboxes) continue;
				Set<java.util.UUID> liveIds = new java.util.HashSet<>();

				// Every currently loaded individual, not just the first found — there
				// is usually more than one, and stopping at one meant only ever
				// drawing whichever the scan happened to reach first that frame.
				for (CritterEntities.Sighting sighting : CritterEntities.all()) {
					if (!tracked.critterName().equals(sighting.critter().name())) continue;
					boolean sparkling = SparklingWatch.isSparkling(sighting);
					if (SparklingMode.hideOrdinaryHitbox(critter, sparkling)) continue;
					Entity entity = sighting.mob();
					if (entity == null) continue;
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

					AABB box = tracked.useRealHitbox() ? hitboxFor(entity) : new AABB(entity.blockPosition());
					boolean seeThrough = StillCritters.persistentThroughWalls(entity.getUUID())
						|| !SafeMode.hiddenCritter(critter, sparkling);
					drawBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES, box, camera, colour);
					anyDrawn = true;
					if (seeThrough) anyThroughWalls = true;
					else anyDepthTested = true;

					String label = (sparkling ? "SPARKLING " : "") + tracked.label()
						+ Markers.pityLabel(critter, entity.getUUID());
					drawn.add(new Found(new Markers.Marker(box, label, colour, Markers.Style.WAYPOINT),
						box.getCenter().distanceTo(camera), seeThrough));
				}

				// Every remembered individual not already covered by a live sighting
				// above — the ones out of range right now but seen recently enough to
				// still trust, per StillCritters.
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
						? approximateHitbox(remembered.pos()) : new AABB(remembered.pos());
					int uniqueColour = display.hitboxEntityColorOverride
						? SparklingMode.uniqueHitboxColour(critter, SessionManager.current()) : 0;
					int colour = diagnostic ? 0xFFFF00FF
						: remembered.sparkling() ? sparklingColour()
						: uniqueColour != 0 ? uniqueColour : baseColour;
					boolean seeThrough = remembered.persistentThroughWalls()
						|| !SafeMode.hiddenCritter(critter, remembered.sparkling());
					drawBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES, box, camera, colour);
					anyDrawn = true;
					if (seeThrough) anyThroughWalls = true;
					else anyDepthTested = true;
					String label = (remembered.sparkling() ? "SPARKLING " : "") + tracked.label()
						+ Markers.pityLabel(critter, remembered.id());
					drawn.add(new Found(new Markers.Marker(box, label, colour, Markers.Style.WAYPOINT),
						box.getCenter().distanceTo(camera), seeThrough));
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
				&& !SparklingMode.onlyShowSparkling()
				? HideyhoSolver.candidates() : java.util.Set.<BlockPos>of()) {
				AABB box = new AABB(
					possible.getX(), possible.getY() - 2, possible.getZ(),
					possible.getX() + 1, possible.getY(), possible.getZ() + 1);
				drawBox(poses, backend, LINES, box, camera, colour);
				anyDrawn = true;
				anyThroughWalls = true;
				drawn.add(new Found(new Markers.Marker(box, "Hideyho (Possible)", colour,
					Markers.Style.WAYPOINT, true), box.getCenter().distanceTo(camera), true));
			}
			BlockPos hideyho = HideyhoSolver.position();
			if (display.enableHitboxes && hideyho != null
				&& !SparklingMode.hideOrdinaryHitbox(hideyhoCritter, HideyhoSolver.sparkling())) {
				// Two blocks tall, shifted down so the top lands where a single-block
				// box would have sat — Hideyho is player-sized, and one block only ever
				// covered its lower half.
				AABB box = new AABB(
					hideyho.getX(), hideyho.getY() - 2, hideyho.getZ(),
					hideyho.getX() + 1, hideyho.getY(), hideyho.getZ() + 1);
				// This phase's position was directly confirmed already and Hideyho cannot
				// move again until its explicit chat transition changes the phase.
				boolean seeThrough = true;
				int uniqueColour = display.hitboxEntityColorOverride
					? SparklingMode.uniqueHitboxColour(hideyhoCritter, SessionManager.current()) : 0;
				int liveColour = HideyhoSolver.sparkling() ? sparklingColour()
					: uniqueColour != 0 ? uniqueColour : colour;
				drawBox(poses, backend, seeThrough ? LINES : RenderTypes.LINES, box, camera, liveColour);
				anyDrawn = true;
				if (seeThrough) anyThroughWalls = true;
				else anyDepthTested = true;

				String hideyhoLabel = (HideyhoSolver.sparkling() ? "SPARKLING " : "")
					+ (HideyhoSolver.phase() == HideyhoSolver.Phase.END
					? "Hideyho (END)" : "Hideyho (START)");
				drawn.add(new Found(new Markers.Marker(box, hideyhoLabel, liveColour, Markers.Style.WAYPOINT),
					box.getCenter().distanceTo(camera), seeThrough));
			}
		}

		if (!anyDrawn) return;
		if (anyDepthTested) backend.flush(RenderTypes.LINES);
		if (anyThroughWalls) backend.flush(LINES);

		for (Found found : drawn) {
			label(poses, backend, found.marker(), camera, found.distance(), found.seeThrough());
		}
	}

	/** Draws one box already positioned in world space, relative to the camera. */
	private static void drawBox(PoseStack poses, WaypointRenderBackend backend, RenderType lineType,
							AABB box, Vec3 camera, int colour) {
		poses.pushPose();
		poses.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
		backend.geometry(lineType, (pose, lines) -> box(pose, lines, (float) box.getXsize(),
			(float) box.getYsize(), (float) box.getZsize(), colour));
		poses.popPose();
	}

	private static boolean tooFar(Markers.Marker marker, Vec3 camera) {
		return marker.box().getCenter().distanceToSqr(camera) > MAX_DISTANCE * MAX_DISTANCE;
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

	/** Four textured vertical faces, matching the structure of vanilla's beacon beam. */
	private static void beaconSides(PoseStack.Pose stackPose, VertexConsumer quad,
			float xSize, float ySize, float zSize, int colour) {
		var pose = stackPose.pose();
		float scroll = -(System.currentTimeMillis() % 2_000L) / 2_000f;
		float vBottom = scroll;
		float vTop = scroll + ySize / 4f;
		float[][] faces = {
			{0, 0, 0, xSize, 0, 0, xSize, ySize, 0, 0, ySize, 0},
			{xSize, 0, 0, xSize, 0, zSize, xSize, ySize, zSize, xSize, ySize, 0},
			{xSize, 0, zSize, 0, 0, zSize, 0, ySize, zSize, xSize, ySize, zSize},
			{0, 0, zSize, 0, 0, 0, 0, ySize, 0, 0, ySize, zSize}
		};
		for (float[] face : faces) {
			for (int i = 0; i < 4; i++) {
				int offset = i * 3;
				float u = i == 1 || i == 2 ? 1f : 0f;
				float v = i >= 2 ? vTop : vBottom;
				quad.addVertex(pose, face[offset], face[offset + 1], face[offset + 2])
					.setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(stackPose, 0, 1, 0);
			}
		}
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

		float[][] edges = {
			{x0, y0, z0, x1, y0, z0}, {x1, y0, z0, x1, y0, z1},
			{x1, y0, z1, x0, y0, z1}, {x0, y0, z1, x0, y0, z0},
			{x0, y1, z0, x1, y1, z0}, {x1, y1, z0, x1, y1, z1},
			{x1, y1, z1, x0, y1, z1}, {x0, y1, z1, x0, y1, z0},
			{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0},
			{x1, y0, z1, x1, y1, z1}, {x0, y0, z1, x0, y1, z1},
		};
		for (float[] e : edges) {
			line(pose, lines, e[0], e[1], e[2], e[3], e[4], e[5], red, green, blue, alpha);
		}
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
		Component text = marker.label().startsWith("SPARKLING ")
			? rainbowLabel(marker.label()) : Component.literal(marker.label());
		if (ConfigManager.get().display.waypointDistance) {
			text = text.copy().append(Component.literal(" " + Math.round(distance) + "m")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		}

		poses.pushPose();
		poses.translate(
			box.getCenter().x - camera.x,
			box.maxY + LABEL_HEIGHT - camera.y,
			box.getCenter().z - camera.z);
		poses.mulPose(backend.cameraRotation());
		poses.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

		float x = -font.width(text) / 2.0f;
		// SEE_THROUGH renders the text with no depth test at all, independent of
		// whatever type the hitbox itself used — the box respecting depth says
		// nothing about the label floating above it doing the same, since text goes
		// through an entirely separate rendering path. NORMAL is what actually ties
		// the label to the same wall the box now respects under Safe Mode.
		backend.text(poses, text.getVisualOrderText(), x,
			seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
			marker.colour() | 0xFF000000, 0x40000000, LightCoordsUtil.FULL_BRIGHT);
		poses.popPose();
	}

	/** Builds a per-character rainbow component for Sparkling waypoint labels. */
	private static Component rainbowLabel(String value) {
		var result = Component.empty();
		float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
		for (int i = 0; i < value.length(); i++) {
			int colour = java.awt.Color.HSBtoRGB(
				(phase + i / (float) Math.max(1, value.length()) * 0.55f) % 1f,
				0.55f, 1f) & 0xFFFFFF;
			result.append(Component.literal(String.valueOf(value.charAt(i)))
				.withStyle(style -> style.withColor(colour)));
		}
		return result;
	}

	/** The same priority chain used by ordinary critter hitboxes and recatch markers. */
	public static int critterHitboxColour(Critter critter, boolean sparkling) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		boolean diagnostic = BuildVersion.DEVELOPER && AdvancedUnlock.isUnlocked()
			&& ConfigManager.get().advanced.showAllCritterHitboxes;
		boolean dedicatedWaypointCritter = EXCLUDED_FROM_HITBOXES.contains(critter.name())
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
