package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.CritterSpawnRanges;
import dev.serko.safariutils.state.LinearTimeline;
import dev.serko.safariutils.state.MonotonicTime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Projects authoritative {@link CritterState} capture attempts into visuals.
 * Target, pity, and outcome decisions remain exclusively in CritterState.
 */
public final class RecatchSpots {

	private static final long HOLD_MILLIS = 40_000;
	private static final long CAPTURE_TRANSITION_MILLIS = 10_000;
	private static final long CAPTURE_SHRINK_MILLIS = 550;
	private static final long RESOLVED_TRANSITION_MILLIS = 5_000;

	private enum PresentationMode { PIN, SHRINK }

	/** Complete visual ownership of one unresolved capture attempt. */
	private static final class AttemptPresentation {
		private final long attemptId;
		private final Critter critter;
		private PresentationMode mode;
		private UUID currentId;
		private AABB box;
		private boolean sparkling;
		private final long attemptStartedAt;
		private final LinearTimeline animation;

		private AttemptPresentation(long attemptId, Critter critter,
				PresentationMode mode, UUID entityId, AABB box,
				boolean sparkling, long attemptStartedAt) {
			this.attemptId = attemptId;
			this.critter = critter;
			this.mode = mode;
			this.currentId = entityId;
			this.box = box;
			this.sparkling = sparkling;
			this.attemptStartedAt = attemptStartedAt;
			this.animation = new LinearTimeline(System.nanoTime(), CAPTURE_SHRINK_MILLIS * 1_000_000L);
		}
	}

	/** A post-breakout pin that outlives its resolved attempt until the body returns. */
	private record Pin(Critter critter, AABB box, boolean sparkling, long pinnedAt) { }

	public record ActivePin(Critter critter, AABB box, UUID entityId, boolean sparkling) { }
	public record ActiveShrink(Critter critter, AABB box, UUID entityId, boolean sparkling) { }
	public record ShrinkState(Critter critter, AABB box, UUID entityId, boolean sparkling,
			LinearTimeline animation) { }

	/** Frame adapter supplies position only; ownership, timing and size stay in this pipeline. */
	@FunctionalInterface
	interface FrameProjection {
		AABB project(ShrinkState state, float partialTick, double scale);
	}
	private static final FrameProjection LIVE_FRAME = (state, partialTick, scale) ->
		framePosition(state.critter(), state.box(), WorldEntities.find(state.entityId()), partialTick, scale);

	/**
	 * Immutable presentation ownership consumed for an entire render snapshot. Render
	 * code never mixes a sighting from one tick with recatch state from another.
	 */
	public record PresentationSnapshot(List<ActivePin> pins, List<ShrinkState> shrinkStates,
			Set<UUID> pinnedBodies, Set<UUID> animatedBodies,
			Set<UUID> nativeShrinkingBodies, Set<Critter> transitioning) {
		public PresentationSnapshot {
			pins = List.copyOf(pins);
			shrinkStates = List.copyOf(shrinkStates);
			pinnedBodies = Set.copyOf(pinnedBodies);
			animatedBodies = Set.copyOf(animatedBodies);
			nativeShrinkingBodies = Set.copyOf(nativeShrinkingBodies);
			transitioning = Set.copyOf(transitioning);
		}

		public boolean isPinned(UUID entityId) {
			return entityId != null && pinnedBodies.contains(entityId);
		}

		public boolean isAnimated(UUID entityId) {
			return entityId != null && animatedBodies.contains(entityId);
		}

		public boolean suppresses(UUID entityId) {
			return isPinned(entityId) || isAnimated(entityId)
				|| nativeShrinkingBodies.contains(entityId);
		}

		public boolean captureInProgress(Critter critter) {
			return critter != null && transitioning.contains(critter);
		}

		/** Guaranteed-catch boxes projected from one shared frame timestamp. */
		public List<ActiveShrink> shrinking(long frameNanos, float partialTick) {
			return shrinking(frameNanos, partialTick, LIVE_FRAME);
		}

		/** The production frame loop, with explicit body input for headless pipeline checks. */
		List<ActiveShrink> shrinking(long frameNanos, float partialTick, FrameProjection projection) {
			if (shrinkStates.isEmpty()) return List.of();
			List<ActiveShrink> result = new ArrayList<>(shrinkStates.size());
			for (ShrinkState state : shrinkStates) {
				double progress = state.animation.progress(frameNanos);
				if (progress >= 1.0) continue;
				AABB rendered = projection.project(state, partialTick, 1.0 - progress);
				result.add(new ActiveShrink(state.critter, rendered, state.entityId, state.sparkling));
			}
			return List.copyOf(result);
		}
	}

	private static final Map<UUID, Critter> knownBodies = new HashMap<>();
	/** One authoritative visual state per unresolved attempt. */
	private static final Map<Long, AttemptPresentation> presentations = new LinkedHashMap<>();
	/** Constant-time body-to-attempt ownership for the renderer. */
	private static final CaptureOwnership presentationByBody = new CaptureOwnership();
	/** Resolved breakout pins, separate because their attempt no longer exists. */
	private static final Map<UUID, Pin> pins = new LinkedHashMap<>();
	private static final Set<UUID> escapedPins = new HashSet<>();
	private static final Set<UUID> caughtBodies = new HashSet<>();
	private static final CaptureTransitions<Critter> captureTransitions =
		new CaptureTransitions<>(CAPTURE_TRANSITION_MILLIS, RESOLVED_TRANSITION_MILLIS);
	private static final Set<String> loggedInvariantKeys = new HashSet<>();
	private static long lastScan;
	private static int lastEagleRarity = Integer.MIN_VALUE;

	private RecatchSpots() { }

	/** Captures all render-facing ownership once without mutating tracker state. */
	public static PresentationSnapshot snapshot() {
		return snapshot(ConfigManager.get().display.recatchHelper, CritterState.nativeCaptureBodies());
	}

	/** Projection inputs are explicit so transition sequences can be replayed without a world. */
	static PresentationSnapshot snapshot(boolean showPins, Set<UUID> nativeCaptureBodies) {
		List<ActivePin> activePins = new ArrayList<>();
		List<ShrinkState> activeShrinks = new ArrayList<>();
		Set<UUID> pinnedBodies = new HashSet<>();
		Set<UUID> animatedBodies = new HashSet<>();
		for (AttemptPresentation presentation : presentations.values()) {
			if (presentation.mode == PresentationMode.PIN) {
				if (!showPins) continue;
				activePins.add(new ActivePin(presentation.critter, presentation.box,
					presentation.currentId, presentation.sparkling));
				pinnedBodies.addAll(presentationByBody.bodies(presentation.attemptId));
			} else {
				activeShrinks.add(new ShrinkState(presentation.critter, presentation.box,
					presentation.currentId, presentation.sparkling,
					presentation.animation));
				animatedBodies.addAll(presentationByBody.bodies(presentation.attemptId));
			}
		}
		if (showPins) {
			for (Map.Entry<UUID, Pin> entry : pins.entrySet()) {
				Pin pin = entry.getValue();
				if (isGuaranteed(pin.critter(), entry.getKey())) continue;
				activePins.add(new ActivePin(pin.critter(), pin.box(), entry.getKey(),
					pin.sparkling()));
				pinnedBodies.add(entry.getKey());
			}
		}
		Set<Critter> transitioning = captureTransitions.activeSpecies();
		return new PresentationSnapshot(activePins, activeShrinks, pinnedBodies, animatedBodies,
			nativeCaptureBodies, transitioning);
	}

	/** Returns a read-only projection of active recatch visuals. */
	public static List<ActivePin> active() {
		return snapshot().pins();
	}

	public static boolean isPinned(UUID entityId) {
		if (!ConfigManager.get().display.recatchHelper || entityId == null) return false;
		AttemptPresentation presentation = presentationFor(entityId);
		if (presentation != null) return presentation.mode == PresentationMode.PIN;
		Pin pin = pins.get(entityId);
		return pin != null && !isGuaranteed(pin.critter(), entityId);
	}

	public static boolean isCaptureAnimated(UUID entityId) {
		AttemptPresentation presentation = presentationFor(entityId);
		return presentation != null && presentation.mode == PresentationMode.SHRINK;
	}

	/** Guaranteed-catch boxes at their exact linear scale for this render frame. */
	public static List<ActiveShrink> shrinking() {
		return snapshot().shrinking(System.nanoTime(), 1.0f);
	}

	private static boolean isGuaranteed(Critter critter, UUID entityId) {
		return CritterCatchRules.guaranteedWithoutPity(critter)
			|| CritterState.pityFor(entityId) >= Markers.pityThreshold(critter.rarity());
	}

	public static void tick() {
		long now = MonotonicTime.nowMillis();
		int eagleRarity = ConfigManager.get().display.eagleRarity;
		if (eagleRarity != lastEagleRarity) {
			lastEagleRarity = eagleRarity;
			removeGuaranteedState();
		}
		if (captureTransitions.prune()) MarkerRegistry.invalidate();
		followActiveBodies();

		if (CritterEntities.scanRevision() != lastScan) {
			lastScan = CritterEntities.scanRevision();
			for (CritterEntities.Sighting sighting : CritterEntities.all()) {
				if (sighting.mob() == null) continue;
				Entity body = sighting.mob();
				UUID id = body.getUUID();
				if (caughtBodies.contains(id) || isCaptureArtifact(sighting.critter(), body)) continue;
				knownBodies.put(id, sighting.critter());
				if (escapedPins.remove(id)) {
					pins.remove(id);
					DebugLog.line("RECATCH", "REJOIN " + sighting.critter().name()
						+ " id=" + shortId(id) + " (same body)");
					MarkerRegistry.invalidate();
				}
			}
			caughtBodies.removeIf(id -> !CritterState.isCaught(id));
		}

		List<Long> expiredAttempts = new ArrayList<>();
		for (AttemptPresentation presentation : presentations.values()) {
			if (now - presentation.attemptStartedAt > HOLD_MILLIS) {
				expiredAttempts.add(presentation.attemptId);
			}
		}
		for (long attemptId : expiredAttempts) {
			AttemptPresentation presentation = removePresentation(attemptId);
			if (presentation != null) {
				DebugLog.line("RECATCH", "PRESENTATION-TIMEOUT A" + attemptId + " "
					+ presentation.critter.name());
			}
		}

		Iterator<Map.Entry<UUID, Pin>> iterator = pins.entrySet().iterator();
		boolean removed = !expiredAttempts.isEmpty();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Pin> entry = iterator.next();
			Pin pin = entry.getValue();
			if (now - pin.pinnedAt() <= HOLD_MILLIS) continue;
			DebugLog.line("RECATCH", "TIMEOUT " + pin.critter().name() + " id="
				+ shortId(entry.getKey()) + " held " + HOLD_MILLIS + "ms unresolved");
			escapedPins.remove(entry.getKey());
			iterator.remove();
			removed = true;
		}
		if (removed) MarkerRegistry.invalidate();
		if (DebugLog.isEnabled()) auditInvariants();
	}

	private static void removeGuaranteedState() {
		List<Long> attempts = new ArrayList<>();
		for (AttemptPresentation presentation : presentations.values()) {
			if (presentation.mode == PresentationMode.PIN
				&& CritterCatchRules.guaranteedWithoutPity(presentation.critter)) {
				attempts.add(presentation.attemptId);
			}
		}
		attempts.forEach(RecatchSpots::removePresentation);
		Set<UUID> pinIds = new HashSet<>();
		pins.forEach((id, pin) -> {
			if (CritterCatchRules.guaranteedWithoutPity(pin.critter())) pinIds.add(id);
		});
		pinIds.forEach(pins::remove);
		escapedPins.removeAll(pinIds);
		if (!attempts.isEmpty() || !pinIds.isEmpty()) MarkerRegistry.invalidate();
	}

	static void onCaptureStarted(long attemptId, Critter critter) {
		if (critter == null) return;
		captureTransitions.start(attemptId, critter);
		StructuredDiagnostics.decision("CAPTURE/WINDOW", "started",
			"attempt=" + attemptId + " retained=" + captureTransitions.size());
		MarkerRegistry.invalidate();
	}

	/** Creates exactly one visual mode from the attempt's authoritative guarantee decision. */
	static void onIdentityAttempt(long attemptId, Critter critter, UUID entityId,
			AABB box, boolean sparkling, int logicalPity, boolean guaranteed,
			long attemptStartedAt) {
		if (critter == null || entityId == null || box == null) return;
		// Replayed evidence may refine an existing attempt, but must never restart its clock.
		if (presentations.containsKey(attemptId)) return;
		PresentationMode mode = guaranteed ? PresentationMode.SHRINK
			: worthPinning(critter) ? PresentationMode.PIN : null;
		if (mode != null) {
			AttemptPresentation presentation = new AttemptPresentation(attemptId, critter, mode,
				entityId, CritterMarkerGeometry.recatch(critter, box), sparkling,
				attemptStartedAt);
			if (!registerBody(presentation, entityId, false)) return;
			presentations.put(attemptId, presentation);
		}
		MarkerRegistry.invalidate();
		DebugLog.line("RECATCH", "ATTEMPT A" + attemptId + " " + critter.name()
			+ " id=" + shortId(entityId) + " pity=" + logicalPity
			+ (mode == PresentationMode.SHRINK ? " guaranteed-shrink"
				: mode == PresentationMode.PIN ? " recatch-pin" : " no-presentation")
			+ " age=" + Math.max(0L, MonotonicTime.nowMillis() - attemptStartedAt) + "ms");
	}

	/**
	 * Keeps every unresolved presentation on its owned live body without inheriting
	 * Minecraft's stepped native hitbox resize. Position follows once per client tick;
	 * size remains the stable full-size geometry and is scaled only by {@link #shrinking()}.
	 */
	private static void followActiveBodies() {
		if (presentations.isEmpty()) return;
		boolean moved = false;
		for (AttemptPresentation presentation : presentations.values()) {
			Entity entity = WorldEntities.find(presentation.currentId);
			if (entity == null) continue;
			AABB followed = CritterMarkerGeometry.follow(presentation.critter,
				presentation.box, entity.getBoundingBox());
			if (followed.equals(presentation.box)) continue;
			presentation.box = followed;
			moved = true;
		}
		if (moved) MarkerRegistry.invalidate();
	}

	/** Tick and frame paths share species offsets and fixed capture dimensions. */
	private static AABB framePosition(Critter critter, AABB stable, Entity entity, float partialTick, double scale) {
		if (entity == null) return CritterMarkerGeometry.captureFrame(critter, stable, null, 0, 0, 0, scale);
		AABB current = entity.getBoundingBox();
		Vec3 rendered = entity.getPosition(partialTick);
		Vec3 position = entity.position();
		return CritterMarkerGeometry.captureFrame(critter, stable, current,
			rendered.x - position.x, rendered.y - position.y, rendered.z - position.z, scale);
	}

	/**
	 * Accepts the shared identity scan's latest anchor as an additional correction path.
	 * Per-tick following above provides smooth movement; this remains authoritative when
	 * a body disappears between scans.
	 */
	static void updateIdentityAnchor(long attemptId, UUID entityId, Critter critter,
			AABB box) {
		if (entityId == null || critter == null || box == null) return;
		AttemptPresentation presentation = presentations.get(attemptId);
		if (presentation == null || !entityId.equals(presentation.currentId)) return;
		AABB updated = CritterMarkerGeometry.follow(critter, presentation.box, box);
		if (updated.equals(presentation.box)) return;
		presentation.box = updated;
		MarkerRegistry.invalidate();
	}

	/** Records the final body-owned pin location once, when that body leaves the scan. */
	static void freezeIdentityAnchor(long attemptId, UUID entityId) {
		if (entityId == null) return;
		AttemptPresentation presentation = presentations.get(attemptId);
		if (presentation == null || presentation.mode != PresentationMode.PIN
			|| !entityId.equals(presentation.currentId)) return;
		DebugLog.line("RECATCH", "ANCHOR A" + attemptId + " "
			+ presentation.critter.name() + " id=" + shortId(entityId)
			+ " at=" + format(presentation.box.getCenter()));
	}

	/** Atomically retires an attempt presentation and optionally creates one breakout hold. */
	static void onIdentityResolved(long attemptId, Critter critter, UUID originalId,
			UUID currentId, AABB currentBox, boolean sparkling, int logicalPity,
			CritterState.Outcome outcome, boolean visible) {
		resolveCaptureTransition(attemptId);
		AttemptPresentation presentation = presentations.get(attemptId);
		Set<UUID> ownedBodies = Set.copyOf(presentationByBody.bodies(attemptId));
		removePresentation(attemptId);
		Set<UUID> relatedBodies = new HashSet<>(ownedBodies);
		if (originalId != null) relatedBodies.add(originalId);
		if (currentId != null) relatedBodies.add(currentId);
		// After releasing this attempt, any surviving owner belongs to another capture.
		// Old aliases are evidence, not permission to clear or suppress that newer owner.
		if (relatedBodies.removeIf(presentationByBody::containsKey)) {
			DebugLog.line("RECATCH", "OWNER-PRESERVED A" + attemptId
				+ " (outcome cannot retire another active presentation)");
		}
		for (UUID id : relatedBodies) {
			pins.remove(id);
			escapedPins.remove(id);
		}

		if (outcome == CritterState.Outcome.CAUGHT) {
			caughtBodies.addAll(relatedBodies);
			DebugLog.line("RECATCH", "IDENTITY-CLEAR A" + attemptId + " " + critter.name()
				+ " id=" + shortId(currentId != null ? currentId : originalId) + " (caught)");
			MarkerRegistry.invalidate();
			return;
		}

		UUID resolvedId = currentId != null ? currentId
			: originalId != null ? originalId
			: presentation != null ? presentation.currentId : null;
		AABB resolvedBox = currentBox != null ? CritterMarkerGeometry.recatch(critter, currentBox)
			: presentation != null ? presentation.box : null;
		boolean resolvedSparkling = sparkling || presentation != null && presentation.sparkling;
		if (visible) {
			DebugLog.line("RECATCH", "IDENTITY-REJOIN A" + attemptId + " " + critter.name()
				+ " id=" + shortId(resolvedId) + " pity=" + logicalPity);
		} else if (resolvedId != null && relatedBodies.contains(resolvedId)
			&& resolvedBox != null && worthPinning(critter)
			&& logicalPity < Markers.pityThreshold(critter.rarity())) {
			pins.put(resolvedId, new Pin(critter, resolvedBox, resolvedSparkling,
				MonotonicTime.nowMillis()));
			escapedPins.add(resolvedId);
			DebugLog.line("RECATCH", "IDENTITY-HOLD A" + attemptId + " " + critter.name()
				+ " id=" + shortId(resolvedId) + " pity=" + logicalPity);
		}
		MarkerRegistry.invalidate();
	}

	static void onIdentityExpired(long attemptId, Critter critter, UUID originalId) {
		resolveCaptureTransition(attemptId);
		AttemptPresentation presentation = removePresentation(attemptId);
		if (originalId != null && !presentationByBody.containsKey(originalId)) {
			pins.remove(originalId);
			escapedPins.remove(originalId);
		}
		MarkerRegistry.invalidate();
		DebugLog.line("RECATCH", "IDENTITY-EXPIRE A" + attemptId + " " + critter.name()
			+ " id=" + shortId(presentation != null ? presentation.currentId : originalId));
	}

	/** A correction replaces mistaken ownership; the old peer becomes renderable again. */
	static void correctIdentityTarget(long attemptId, Critter critter, UUID oldId, UUID newId,
			AABB newBox, boolean sparkling, int logicalPity, boolean guaranteed) {
		if (oldId == null || newId == null || oldId.equals(newId)) return;
		AttemptPresentation presentation = presentations.get(attemptId);
		if (presentation != null) {
			if (!registerBody(presentation, newId, true)) return;
			presentation.currentId = newId;
			presentation.box = CritterMarkerGeometry.recatch(critter, newBox);
			presentation.sparkling |= sparkling;
			PresentationMode correctedMode = guaranteed ? PresentationMode.SHRINK
				: worthPinning(critter) ? PresentationMode.PIN : null;
			if (correctedMode == null) {
				removePresentation(attemptId);
			} else {
				presentation.mode = correctedMode;
			}
		}
		if (!presentationByBody.containsKey(oldId)) {
			pins.remove(oldId);
			escapedPins.remove(oldId);
		}
		DebugLog.line("RECATCH", "IDENTITY-RETARGET A" + attemptId + " " + critter.name()
			+ " " + shortId(oldId) + " -> " + shortId(newId) + " pity=" + logicalPity
			+ " presentation=" + (guaranteed ? "guaranteed-shrink" : "recatch-pin"));
		MarkerRegistry.invalidate();
	}

	/** A rekey retains old-body suppression while transferring the visible identity. */
	static void reassignIdentity(long attemptId, Critter critter, UUID oldId, UUID newId,
			AABB newBox, boolean sparkling, int logicalPity) {
		if (oldId == null || newId == null || oldId.equals(newId)) return;
		AttemptPresentation presentation = presentations.get(attemptId);
		if (presentation != null) {
			if (!registerBody(presentation, newId, false)) return;
			presentation.currentId = newId;
			presentation.sparkling |= sparkling;
			if (presentation.mode == PresentationMode.PIN) {
				presentation.box = CritterMarkerGeometry.recatch(critter, newBox);
			}
		}
		Critter known = knownBodies.remove(oldId);
		if (known != null) knownBodies.put(newId, known);
		caughtBodies.remove(oldId);
		DebugLog.line("RECATCH", "IDENTITY-REKEY A" + attemptId + " " + critter.name()
			+ " " + shortId(oldId) + " -> " + shortId(newId) + " pity=" + logicalPity);
		MarkerRegistry.invalidate();
	}

	public static boolean captureInProgress(Critter critter) {
		return captureTransitions.active(critter);
	}

	public static boolean isCaptureArtifact(Critter critter, Entity entity) {
		return isCaptureArtifact(critter, entity, captureInProgress(critter));
	}

	/** Pure render overload using the frame's immutable transition decision. */
	static boolean isCaptureArtifact(Critter critter, Entity entity, boolean captureInProgress) {
		if (entity == null || !captureInProgress) return false;
		UUID entityId = entity.getUUID();
		if (knownBodies.containsKey(entityId) || presentationByBody.containsKey(entityId)
			|| pins.containsKey(entityId)) return false;
		// Pairing has stronger species-specific evidence than transition timing. A real
		// peer can enter the scan for the first time while another same-species critter
		// is already shrinking (notably adjacent Gazers); suppressing that verified body
		// would make its waypoint vanish before its own attempt is registered.
		if (CritterEntities.isVerifiedBody(critter, entity)) return false;
		// Unverified proximity matches exposed during a capture transition are helper
		// entities, while verified or already-known peers remain visible.
		return true;
	}

	static void onCaughtBodyRestored(UUID entityId) {
		if (entityId != null) caughtBodies.remove(entityId);
	}

	private static void resolveCaptureTransition(long attemptId) {
		boolean changed = captureTransitions.resolve(attemptId);
		if (changed) StructuredDiagnostics.decision("CAPTURE/WINDOW", "settled",
			"attempt=" + attemptId + " retained=" + captureTransitions.size());
	}

	public static void onConfirmedCatchTotal(Critter critter, int catches) {
		if (critter == null || catches < CritterSpawnRanges.maximum(critter)) return;
		List<Long> attemptIds = new ArrayList<>();
		for (AttemptPresentation presentation : presentations.values()) {
			if (critter.equals(presentation.critter)) attemptIds.add(presentation.attemptId);
		}
		attemptIds.forEach(RecatchSpots::removePresentation);
		Set<UUID> pinIds = new HashSet<>();
		pins.forEach((id, pin) -> {
			if (critter.equals(pin.critter())) pinIds.add(id);
		});
		pinIds.forEach(pins::remove);
		escapedPins.removeAll(pinIds);
		int cleared = attemptIds.size() + pinIds.size();
		if (cleared > 0) {
			DebugLog.line("RECATCH", "MAX-CATCH " + critter.name() + "=" + catches
				+ " clearedPresentations=" + cleared);
			MarkerRegistry.invalidate();
		}
	}

	private static boolean worthPinning(Critter critter) {
		return !CritterCatchRules.guaranteedWithoutPity(critter)
			&& !"Hideyho".equals(critter.name());
	}

	public static void clear() {
		knownBodies.clear();
		presentations.clear();
		presentationByBody.clear();
		pins.clear();
		escapedPins.clear();
		caughtBodies.clear();
		captureTransitions.clear();
		loggedInvariantKeys.clear();
		MarkerRegistry.invalidate();
	}

	private static AttemptPresentation presentationFor(UUID entityId) {
		if (entityId == null) return null;
		Long attemptId = presentationByBody.owner(entityId);
		return attemptId == null ? null : presentations.get(attemptId);
	}

	/** Enforces the invariant that one body has one attempt owner. */
	private static boolean registerBody(AttemptPresentation presentation, UUID entityId, boolean replaceAliases) {
		CaptureOwnership.Claim claim = presentationByBody.claim(presentation.attemptId, entityId, replaceAliases);
		if (!claim.accepted()) {
			invariant("stale-claim:" + presentation.attemptId + ":" + entityId,
				"stale presentation claim rejected attempt=A" + presentation.attemptId);
			return false;
		}
		if (claim.displaced() != null) {
			presentations.remove(claim.displaced());
			DebugLog.line("INVARIANT", "conflicting presentation retired id=" + shortId(entityId)
				+ " A" + claim.displaced() + " -> A" + presentation.attemptId);
		}
		pins.remove(entityId);
		escapedPins.remove(entityId);
		caughtBodies.remove(entityId);
		return true;
	}

	private static AttemptPresentation removePresentation(long attemptId) {
		AttemptPresentation presentation = presentations.remove(attemptId);
		presentationByBody.release(attemptId);
		return presentation;
	}

	/** Development safety net for the one-attempt/one-presentation ownership contract. */
	private static void auditInvariants() {
		for (AttemptPresentation presentation : presentations.values()) {
			Set<UUID> bodyIds = presentationByBody.bodies(presentation.attemptId);
			if (!bodyIds.contains(presentation.currentId)) {
				invariant("current:" + presentation.attemptId,
					"presentation current body is not owned attempt=A" + presentation.attemptId);
			}
			for (UUID id : bodyIds) {
				if (!Long.valueOf(presentation.attemptId).equals(presentationByBody.owner(id))) {
					invariant("body-index:" + presentation.attemptId + ":" + id,
						"presentation body index mismatch attempt=A" + presentation.attemptId
							+ " id=" + shortId(id));
				}
				if (pins.containsKey(id)) {
					invariant("active-resolved:" + id,
						"body has active presentation and resolved pin id=" + shortId(id));
				}
				if (caughtBodies.contains(id)) {
					invariant("active-caught:" + id,
						"active presentation body is suppressed as caught id=" + shortId(id));
				}
			}
		}
		for (Map.Entry<UUID, Long> entry : presentationByBody.entries().entrySet()) {
			AttemptPresentation presentation = presentations.get(entry.getValue());
			if (presentation == null || !presentationByBody.bodies(entry.getValue()).contains(entry.getKey())) {
				invariant("index-owner:" + entry.getKey(),
					"body index has no matching presentation id=" + shortId(entry.getKey())
						+ " attempt=A" + entry.getValue());
			}
		}
	}

	private static void invariant(String key, String message) {
		if (loggedInvariantKeys.add(key)) DebugLog.line("INVARIANT", message);
	}

	public static void reset() {
		clear();
		lastEagleRarity = ConfigManager.get().display.eagleRarity;
		lastScan = Long.MIN_VALUE;
	}

	private static String shortId(UUID id) {
		return id == null ? "none" : id.toString().substring(0, 8);
	}

	private static String format(net.minecraft.world.phys.Vec3 position) {
		return "%.2f,%.2f,%.2f".formatted(position.x, position.y, position.z);
	}
}
