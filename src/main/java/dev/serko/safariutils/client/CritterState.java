package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.CritterSpawnRanges;
import dev.serko.safariutils.parse.CritterEvent;
import dev.serko.safariutils.state.EvidenceLevel;
import dev.serko.safariutils.state.MonotonicTime;
import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative logical identity model for Safari critters. Entity UUIDs are
 * observations of a logical individual, while capsule throws are independent
 * attempts that can overlap without overwriting one another.
 */
public final class CritterState {
	private static final long LOST_AFTER_MILLIS = 10_000L;
	private static final long ATTEMPT_AFTER_MILLIS = 40_000L;
	private static final long CAPSULE_AFTER_MILLIS = 5_000L;
	private static final long BREAKOUT_FALLBACK_MILLIS = 5_000L;
	/** Ordered catch messages must never leave an older presentation alive indefinitely. */
	private static final long CAUGHT_FALLBACK_MILLIS = 750L;
	private static final long OUTCOME_AFTER_MILLIS = 10_000L;
	private static final long CAUGHT_BODY_CONTRADICTION_MILLIS = 1_500L;
	private static final int CAPSULE_PATH_LIMIT = 16;
	private static final double REAPPEAR_DISTANCE_SQ = 4.0 * 4.0;
	private static final double RETRY_COALESCE_DISTANCE_SQ = 2.0 * 2.0;
	private static final double CAPSULE_ASSIGN_DISTANCE_SQ = 12.0 * 12.0;
	private static final double CAPTURE_LABEL_DISTANCE_SQ = 10.0 * 10.0;
	private static final double UNMATCHED_REAPPEAR_COST = REAPPEAR_DISTANCE_SQ + 1.0;
	private static final double SINGLETON_UNMATCHED_COST = 999_999.0;
	private static final double RAY_DISTANCE = 64.0;
	private static final long INTERACTION_TARGET_MILLIS = 1_500L;
	private static final double INTERACTION_TARGET_DISTANCE_SQ = 2.5 * 2.5;
	/** Small bodies need a forgiving visual ray while still remaining individually targeted. */
	private static final double TARGET_RAY_INFLATE = 0.5;
	/** Covers server ordering where the old body vanishes just before its ATTEMPT chat line. */
	private static final long RECENT_TARGET_MILLIS = 4_000L;
	/** Covers rapid retries whose ATTEMPT line precedes the replacement-body scan. */
	private static final long DEFERRED_ATTEMPT_MILLIS = 1_500L;
	/** Impact evidence may repair an aimed-at body chosen incorrectly in a tight group. */
	private static final double IMPACT_REASSIGN_DISTANCE_SQ = 8.0 * 8.0;
	private static final double IMPACT_REASSIGN_MARGIN_SQ = 1.0;
	/** Allows for a body scan immediately before its species-only ATTEMPT chat line. */
	private static final long DISAPPEARANCE_EVIDENCE_LEAD_MILLIS = 300L;
	/** Native capture entities scale uniformly; ordinary movement never does. */
	private static final double NATIVE_CAPTURE_SCALE_THRESHOLD = 0.92;
	/** Exact-position form swaps (for example Hideonfloor) retain one logical identity. */
	private static final double FORM_REKEY_DISTANCE_SQ = 1.5 * 1.5;
	private static final long FORM_REKEY_MILLIS = 1_000L;

	public enum Life {
		LIVE,
		ATTEMPTING,
		BREAKOUT,
		CAUGHT,
		LOST
	}

	public enum Outcome {
		PENDING,
		BREAKOUT,
		CAUGHT
	}

	public record LogicalView(long id, Critter critter, UUID entityId, AABB box,
			boolean sparkling, int pity, Life life, EvidenceLevel evidence) { }

	/** startedAt is process-relative monotonic milliseconds, not a wall-clock date. */
	public record AttemptView(long id, long logicalId, Critter critter, UUID targetEntityId,
			UUID capsuleEntityId, Vec3 anchor, Vec3 impact, Outcome outcome, long startedAt) { }

	/** Immutable scan input: identity reconciliation must not reread mutable entity geometry. */
	record BodyObservation(Critter critter, UUID labelId, UUID entityId, AABB box, boolean sparkling) {
		BodyObservation {
			java.util.Objects.requireNonNull(critter);
			java.util.Objects.requireNonNull(labelId);
			java.util.Objects.requireNonNull(entityId);
			java.util.Objects.requireNonNull(box);
		}
	}

	/** One sampled camera ray shared by every target comparison in an event. */
	record Aim(Vec3 eye, Vec3 direction) { }
	record AttemptEvidence(Aim aim, StillCritters.AttemptTarget remembered) { }
	record CapsuleObservation(UUID entityId, Vec3 position) { }
	record CaptureLabelObservation(Critter critter, UUID labelId, Vec3 position) { }

	private static Aim currentAim() {
		Player player = Minecraft.getInstance().player;
		return player == null ? null : new Aim(player.getEyePosition(), player.getViewVector(1.0f));
	}

	private static final class Logical {
		private final long id;
		private final Critter critter;
		private UUID labelId;
		private UUID entityId;
		private AABB box;
		private AABB presentationBox;
		private boolean sparkling;
		private int pity;
		private Life life = Life.LIVE;
		private EvidenceLevel evidence = EvidenceLevel.VISIBLE;
		private final long createdAt;
		private long seenAt;
		private long caughtAt;
		private long disappearedAt;
		private long activeAttempt;
		private boolean visible = true;

		private Logical(long id, Critter critter, UUID labelId, UUID entityId, AABB box,
				boolean sparkling, long seenAt) {
			this.id = id;
			this.critter = critter;
			this.labelId = labelId;
			this.entityId = entityId;
			this.box = box;
			this.presentationBox = box;
			this.sparkling = sparkling;
			this.createdAt = seenAt;
			this.seenAt = seenAt;
		}
	}

	private static final class Attempt {
		private final long id;
		private long logicalId;
		private final Critter critter;
		private UUID targetEntityId;
		private final Vec3 anchor;
		private final long startedAt;
		private final boolean exactTarget;
		private final boolean masterful;
		private UUID capsuleEntityId;
		private UUID captureLabelId;
		private Vec3 impact;
		private boolean targetCorrected;
		private boolean presentationCommitted;
		private boolean targetAbsent;
		private boolean awaitingFirstBody;
		private boolean reappearanceConfirmed;
		private Outcome outcome = Outcome.PENDING;
		private long resolvedAt;

		private Attempt(long id, Logical target, long startedAt, boolean exactTarget,
				boolean masterful) {
			this.id = id;
			this.logicalId = target.id;
			this.critter = target.critter;
			this.targetEntityId = target.entityId;
			this.anchor = target.box.getCenter();
			this.startedAt = startedAt;
			this.exactTarget = exactTarget;
			this.masterful = masterful;
			this.awaitingFirstBody = !target.visible;
		}
	}

	private static final class CapsulePath {
		private final UUID id;
		private final ArrayDeque<Vec3> points = new ArrayDeque<>(CAPSULE_PATH_LIMIT);
		private long firstSeenAt;
		private long seenAt;
		private long movedAt;

		private CapsulePath(UUID id) {
			this.id = id;
		}

		private void add(Vec3 point, long now) {
			if (firstSeenAt == 0L) firstSeenAt = now;
			if (points.isEmpty() || points.peekLast().distanceToSqr(point) > 1.0e-6) {
				points.addLast(point);
				movedAt = now;
				while (points.size() > CAPSULE_PATH_LIMIT) points.removeFirst();
			}
			seenAt = now;
		}
	}

	private record OutcomeNotice(Outcome outcome, long observedAt) { }
	private record RecentInteraction(UUID entityId, Vec3 position, long observedAt) { }
	private record DeferredAttempt(long id, Critter critter, long observedAt, boolean masterful, Aim aim) { }

	private static final Map<Long, Logical> logicals = new LinkedHashMap<>();
	private static final Map<UUID, Long> logicalByLabel = new HashMap<>();
	private static final Map<UUID, Long> logicalByEntity = new HashMap<>();
	private static final Map<Long, Attempt> attempts = new LinkedHashMap<>();
	private static final Map<UUID, CapsulePath> capsulePaths = new HashMap<>();
	private static final Map<UUID, Long> attemptByCapsule = new HashMap<>();
	private static final Map<UUID, Long> attemptByCaptureLabel = new HashMap<>();
	private static final Map<Critter, ArrayDeque<OutcomeNotice>> unmatchedOutcomes = new LinkedHashMap<>();
	/** Breakouts resolved from visible reappearance before their delayed chat acknowledgement. */
	private static final Map<Critter, ArrayDeque<Long>> earlyBreakoutAcks = new HashMap<>();
	private static final Set<Critter> exhaustedSpecies = new HashSet<>();
	private static long nextLogicalId;
	private static long nextAttemptId;
	private static long lastScan;
	private static long visitEpoch;
	private static final Set<String> loggedInvariantKeys = new HashSet<>();
	private static RecentInteraction recentInteraction;
	private static final Map<Critter, ArrayDeque<DeferredAttempt>> deferredAttempts = new LinkedHashMap<>();
	private static CaptureEffects effects = CaptureEffects.LIVE;

	private CritterState() {
	}

	/** Deterministic startup checks for the assignment cases that defeat greedy pairing. */
	public static void verifyAlgorithms() {
		int[] simple = MinimumCostAssignment.solve(new double[][] {{1.0, 9.0}, {9.0, 1.0}});
		int[] crossed = MinimumCostAssignment.solve(new double[][] {{1.0, 2.0, 17.0}, {1.1, 100.0, 17.0}});
		if (simple[0] != 0 || simple[1] != 1 || crossed[0] != 1 || crossed[1] != 0) {
			throw new IllegalStateException("Critter pairing assignment self-test failed");
		}
	}

	public static void tick() {
		SafariEpoch epoch = SafariRuntimeState.current();
		if (epoch.visit() != visitEpoch) resetForVisit(epoch.visit());
		long scan = CritterEntities.scanRevision();
		if (scan == lastScan) return;
		MarkerRegistry.invalidate();
		lastScan = scan;
		long now = MonotonicTime.nowMillis();
		observeSightings(now);
		advanceCaptureEvidence(sampleCapsules(), sampleCaptureLabels(), now);
	}

	/** Ordered evidence application after the immutable body scan has been reconciled. */
	static void advanceCaptureEvidence(List<CapsuleObservation> capsules,
			List<CaptureLabelObservation> labels, long now) {
		confirmTargetsFromNativeShrink(now);
		observeCapsules(capsules, now);
		observeCaptureLabels(labels, now);
		correctTargetsFromImpact(now);
		reconcile(now);
		prune(now);
		var trace = CaptureReplayTrace.current();
		if (trace != null) trace.evidence(capsules, labels, now);
	}

	/** Applies a visit boundary immediately instead of waiting for the next entity scan. */
	public static void onVisitStarted() {
		resetForVisit(SafariRuntimeState.current().visit());
	}

	public static void onCritterEvent(CritterEvent event, String rawLine) {
		if (event == null || event.critter() == null) return;
		MarkerRegistry.invalidate();
		long now = MonotonicTime.nowMillis();
		if (event.type() == CritterEvent.Type.ATTEMPT || event.type() == CritterEvent.Type.FAILED) {
			SparklingWatch.onCaptureInteraction(event.critter());
		}
		applyEvent(event, rawLine != null && rawLine.contains("Masterful Critter Capsule"), now, () -> {
			long scan = CritterEntities.scanRevision();
			if (scan != lastScan) {
				lastScan = scan;
				observeSightings(now);
			}
			return new AttemptEvidence(currentAim(), StillCritters.attemptTarget(event.critter()));
		});
	}

	/** Applies server evidence against the already sampled body/camera state. */
	static void applyEvent(CritterEvent event, boolean masterful, long now, Aim aim) {
		applyEvent(event, masterful, now, new AttemptEvidence(aim, null));
	}

	static void applyEvent(CritterEvent event, boolean masterful, long now, AttemptEvidence evidence) {
		applyEvent(event, masterful, now, () -> evidence);
	}

	private static void applyEvent(CritterEvent event, boolean masterful, long now,
			java.util.function.Supplier<AttemptEvidence> sampleEvidence) {
		if (event == null || event.critter() == null || exhaustedSpecies.contains(event.critter())) return;
		AttemptEvidence evidence = null;
		switch (event.type()) {
			case ATTEMPT -> {
				long attemptId = ++nextAttemptId;
				effects.started(attemptId, event.critter());
				evidence = sampleEvidence.get();
				beginAttempt(attemptId, event.critter(), now, masterful, evidence);
			}
			case FAILED -> {
				queueOutcome(event.critter(), Outcome.BREAKOUT, now);
			}
			case OWN_CATCH -> queueOutcome(event.critter(), Outcome.CAUGHT, now);
			case SHARED_CATCH, ENTERED_SAFARI -> {
			}
		}
		reconcile(now);
		var trace = CaptureReplayTrace.current();
		if (trace != null) trace.event(event, masterful, evidence, now);
	}

	/** Retains the exact entity the player interacted with until the server ATTEMPT line arrives. */
	public static void onEntityInteraction(Entity entity) {
		if (entity == null || entity instanceof Player) return;
		observeInteraction(entity.getUUID(), entity.getBoundingBox().getCenter(), MonotonicTime.nowMillis());
	}

	static void observeInteraction(UUID entityId, Vec3 position, long now) {
		if (entityId == null || position == null) return;
		recentInteraction = new RecentInteraction(entityId, position, now);
		var trace = CaptureReplayTrace.current();
		if (trace != null) trace.interaction(entityId, position, now);
	}

	public static List<LogicalView> logicalCritters() {
		List<LogicalView> result = new ArrayList<>(logicals.size());
		for (Logical logical : logicals.values()) {
			result.add(new LogicalView(logical.id, logical.critter, logical.entityId, logical.box,
				logical.sparkling, logical.pity, logical.life, logical.evidence));
		}
		return List.copyOf(result);
	}

	/** Read-only diagnostic digest without allocating a second snapshot of every logical critter. */
	static long replayFingerprint() {
		long hash = 1;
		for (Logical logical : logicals.values()) {
			hash = CaptureReplayTrace.mix(hash, logical.id);
			hash = CaptureReplayTrace.mix(hash, logical.critter.name().hashCode());
			hash = CaptureReplayTrace.mix(hash, logical.entityId); hash = CaptureReplayTrace.mix(hash, logical.box);
			hash = CaptureReplayTrace.mix(hash, logical.sparkling ? 1 : 0); hash = CaptureReplayTrace.mix(hash, logical.pity);
			hash = CaptureReplayTrace.mix(hash, logical.life.ordinal()); hash = CaptureReplayTrace.mix(hash, logical.evidence.ordinal());
		}
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING) continue;
			hash = CaptureReplayTrace.mix(hash, attempt.id); hash = CaptureReplayTrace.mix(hash, attempt.logicalId);
			hash = CaptureReplayTrace.mix(hash, attempt.targetEntityId); hash = CaptureReplayTrace.mix(hash, attempt.capsuleEntityId);
			hash = CaptureReplayTrace.mix(hash, attempt.anchor); hash = CaptureReplayTrace.mix(hash, attempt.impact);
			hash = CaptureReplayTrace.mix(hash, attempt.startedAt);
		}
		return hash;
	}

	public static List<AttemptView> activeAttempts() {
		List<AttemptView> result = new ArrayList<>();
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING) continue;
			result.add(view(attempt));
		}
		return List.copyOf(result);
	}

	/** Enables responsive entity sampling only while capture ownership is unresolved. */
	public static boolean captureTrackingActive() {
		if (!deferredAttempts.isEmpty()) return true;
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome == Outcome.PENDING) return true;
		}
		return false;
	}

	/** Authoritative logical pity for the body currently representing this individual. */
	public static int pityFor(UUID entityId) {
		Long logicalId = logicalByEntity.get(entityId);
		if (logicalId == null) logicalId = logicalByLabel.get(entityId);
		Logical logical = logicalId == null ? null : logicals.get(logicalId);
		return logical == null ? 0 : logical.pity;
	}

	/** Whether this entity currently represents a logically caught individual. */
	public static boolean isCaught(UUID entityId) {
		Long logicalId = logicalByEntity.get(entityId);
		Logical logical = logicalId == null ? null : logicals.get(logicalId);
		return logical != null && logical.life == Life.CAUGHT;
	}

	private static AttemptView view(Attempt attempt) {
		return new AttemptView(attempt.id, attempt.logicalId, attempt.critter,
			attempt.targetEntityId, attempt.capsuleEntityId, attempt.anchor,
			attempt.impact, attempt.outcome, attempt.startedAt);
	}

	private static void observeSightings(long now) {
		List<BodyObservation> observations = new ArrayList<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			Entity body = sighting.mob();
			if (body == null || CritterEntities.isCaptureScaffolding(sighting)) continue;
			observations.add(new BodyObservation(sighting.critter(), sighting.label().getUUID(),
				body.getUUID(), body.getBoundingBox(), SparklingWatch.isSparkling(sighting)));
		}
		observeBodies(observations, now);
	}

	/** The same scan reducer is used by live entity sampling and headless observation replay. */
	static void observeBodies(List<BodyObservation> observations, long now) {
		Set<UUID> live = new HashSet<>();
		// Decide label fallback against the complete batch, never its iteration order.
		for (BodyObservation observation : observations) live.add(observation.entityId());
		Set<Long> previouslyVisible = new HashSet<>();
		List<BodyObservation> fresh = new ArrayList<>();
		for (Logical logical : logicals.values()) {
			if (logical.visible) previouslyVisible.add(logical.id);
			logical.visible = false;
		}
		for (BodyObservation sighting : observations) {
			// Hideyho has a dedicated phase/location solver and no capsule pity lifecycle.
			if ("Hideyho".equals(sighting.critter().name())) continue;
			if (exhaustedSpecies.contains(sighting.critter())) continue;
			UUID entityId = sighting.entityId();
			UUID labelId = sighting.labelId();
			Long entityLogicalId = logicalByEntity.get(entityId);
			Long labelLogicalId = logicalByLabel.get(labelId);
			Logical labelOwner = labelLogicalId == null ? null : logicals.get(labelLogicalId);
			if (entityLogicalId == null && labelOwner != null
				&& returningBodyReplacesHelper(sighting, labelOwner, live)) {
				// A new full-sized body must enter the authoritative breakout matcher,
				// not inherit a transient capsule helper's zero-pity label identity.
				logicalByEntity.remove(labelOwner.entityId, labelOwner.id);
				logicalByLabel.remove(labelOwner.labelId, labelOwner.id);
				logicals.remove(labelOwner.id);
				effects.bodyReplaced(labelOwner.critter, labelOwner.entityId, entityId);
				DebugLog.line("IDENTITY", "RETURNING-FORM " + sighting.critter().name()
					+ " retiredHelper=L" + labelOwner.id + " body=" + shortId(entityId));
				labelOwner = null;
				labelLogicalId = null;
			}
			if (entityLogicalId == null && labelOwner != null
				&& (live.contains(labelOwner.entityId) || !labelOwner.critter.equals(sighting.critter()))) {
				// A transferred label does not imply replacement while its old body is still here.
				DebugLog.line("IDENTITY", "LABEL-FALLBACK-REJECT " + sighting.critter().name()
					+ " label=" + shortId(labelId) + " owner=L" + labelLogicalId
					+ " ownerObserved=" + live.contains(labelOwner.entityId));
				labelLogicalId = null;
			}
			Long logicalId = entityLogicalId != null ? entityLogicalId : labelLogicalId;
			if (entityLogicalId != null && labelLogicalId != null
				&& !entityLogicalId.equals(labelLogicalId)) {
				Long coalesced = coalesceReboundIdentity(sighting, labelLogicalId,
					entityLogicalId, live, now);
				if (coalesced != null) logicalId = coalesced;
				else DebugLog.line("IDENTITY", "LABEL-REBOUND " + sighting.critter().name()
					+ " label=" + shortId(labelId) + " L" + labelLogicalId
					+ " -> body=" + shortId(entityId) + " L" + entityLogicalId);
			}
			if (logicalId == null) {
				fresh.add(sighting);
				continue;
			}
			Logical logical = logicals.get(logicalId);
			if (logical == null) continue;
			Attempt active = logical.activeAttempt == 0L ? null : attempts.get(logical.activeAttempt);
			boolean reappeared = active != null && !active.awaitingFirstBody && active.targetAbsent
				&& (logical.life == Life.ATTEMPTING || logical.life == Life.BREAKOUT);
			UUID previousEntityId = logical.entityId;
			boolean entityRekeyed = !entityId.equals(previousEntityId);
			if (entityRekeyed) {
				logicalByEntity.remove(previousEntityId, logical.id);
				logical.entityId = entityId;
				logicalByEntity.put(entityId, logical.id);
				DebugLog.line("IDENTITY", "LABEL-FORM " + logical.critter.name()
					+ " logical=L" + logical.id + " " + shortId(previousEntityId) + " -> "
					+ shortId(entityId) + " pity=" + logical.pity + " attempt=" + logical.activeAttempt);
			}
			bindLabel(logical, labelId);
			logical.box = sighting.box();
			logical.presentationBox = stablePresentationBox(logical.presentationBox, logical.box);
			logical.sparkling |= sighting.sparkling();
			if (entityRekeyed && active != null && active.outcome == Outcome.PENDING
				&& previousEntityId.equals(active.targetEntityId)) {
				active.targetEntityId = entityId;
				effects.reassign(active.id, logical.critter, previousEntityId,
					entityId, logical.presentationBox, logical.box, logical.sparkling, logical.pity);
			} else if (entityRekeyed && active == null) {
				effects.bodyReplaced(logical.critter, previousEntityId, entityId);
			}
			if (active != null && active.outcome == Outcome.PENDING
				&& active.presentationCommitted && !active.targetAbsent) {
				effects.anchor(active.id, logical.entityId,
					logical.critter, logical.presentationBox);
			}
			logical.seenAt = now;
			logical.visible = true;
			acceptFirstAttemptBody(active, logical);
			if (reappeared) confirmReappearance(logical, now);
			if (logical.life == Life.CAUGHT
				&& now - logical.caughtAt >= CAUGHT_BODY_CONTRADICTION_MILLIS) {
				DebugLog.line("IDENTITY", "CAUGHT-CONTRADICTION " + logical.critter.name()
					+ " logical=L" + logical.id + " entity=" + shortId(logical.entityId)
					+ " remained visible; restoring LIVE");
				logical.life = Life.LIVE;
				logical.evidence = EvidenceLevel.VISIBLE;
				effects.restored(logical.entityId);
			}
			if (logical.life == Life.LOST) logical.life = Life.LIVE;
		}

		matchFreshBodies(fresh, now);
		if (!deferredAttempts.isEmpty()) bindDeferredAttempts(now);
		for (Logical logical : logicals.values()) {
			if (previouslyVisible.contains(logical.id) && !logical.visible) {
				logical.disappearedAt = now;
			}
			if (logical.activeAttempt != 0L && logical.entityId != null
				&& !live.contains(logical.entityId)) {
				Attempt attempt = attempts.get(logical.activeAttempt);
				if (attempt != null && attempt.outcome != Outcome.CAUGHT) {
					if (!attempt.targetAbsent) {
						effects.freeze(attempt.id, logical.entityId);
					}
					attempt.targetAbsent = true;
				}
			}
			if (logical.entityId != null && !live.contains(logical.entityId)
				&& logical.life == Life.LIVE && now - logical.seenAt > LOST_AFTER_MILLIS) {
				logical.life = Life.LOST;
			}
		}
		var trace = CaptureReplayTrace.current();
		if (trace != null) trace.bodies(observations, now);
	}

	/** A confirmed breakout's full-sized return outranks its transient helper label. */
	private static boolean returningBodyReplacesHelper(BodyObservation body, Logical helper, Set<UUID> live) {
		if (helper.pity != 0 || helper.activeAttempt != 0 || live.contains(helper.entityId)
			|| !helper.critter.equals(body.critter()) || helper.life == Life.CAUGHT
			|| body.box().getXsize() < helper.box.getXsize() * 1.5
			|| body.box().getYsize() < helper.box.getYsize() * 1.5) return false;
		int candidates = 0;
		for (Logical prior : logicals.values()) {
			Attempt attempt = attempts.get(prior.activeAttempt);
			if (prior == helper || !prior.critter.equals(body.critter()) || live.contains(prior.entityId)
				|| attempt == null || attempt.outcome != Outcome.PENDING || guaranteed(attempt, prior)
				|| helper.createdAt < attempt.startedAt || !hasQueuedBreakout(prior.critter, attempt.startedAt)
				|| !prior.presentationBox.inflate(.1).contains(helper.box.getCenter())
				|| !prior.presentationBox.inflate(.1).contains(body.box().getCenter())) continue;
			if (++candidates > 1) return false;
		}
		return candidates == 1;
	}

	/**
	 * Rejoins a fresh duplicate body identity to the established logical critter whose
	 * label it inherited. This is deliberately narrow: the duplicate must be recent,
	 * attempt-free, nearby, and have no pity of its own, so crossing live peers are never
	 * collapsed merely because Hypixel briefly paired their labels differently.
	 */
	private static Long coalesceReboundIdentity(BodyObservation sighting,
			long labelLogicalId, long entityLogicalId, Set<UUID> observedBodies, long now) {
		Logical labelOwner = logicals.get(labelLogicalId);
		Logical entityOwner = logicals.get(entityLogicalId);
		if (labelOwner == null || entityOwner == null
			|| !labelOwner.critter.equals(entityOwner.critter)
			|| !labelOwner.critter.equals(sighting.critter())) return null;
		if (observedBodies.contains(labelOwner.entityId) && observedBodies.contains(entityOwner.entityId)) return null;
		Logical established;
		Logical duplicate;
		if (reboundDuplicate(labelOwner, entityOwner, now)) {
			established = labelOwner;
			duplicate = entityOwner;
		} else if (reboundDuplicate(entityOwner, labelOwner, now)) {
			established = entityOwner;
			duplicate = labelOwner;
		} else return null;
		if (established.box.getCenter().distanceToSqr(duplicate.box.getCenter())
			> REAPPEAR_DISTANCE_SQ) return null;

		logicalByEntity.remove(duplicate.entityId, duplicate.id);
		logicalByLabel.remove(duplicate.labelId, duplicate.id);
		logicals.remove(duplicate.id);
		established.sparkling |= duplicate.sparkling;
		established.presentationBox = stablePresentationBox(established.presentationBox,
			duplicate.presentationBox);
		established.seenAt = Math.max(established.seenAt, duplicate.seenAt);
		logicalByEntity.put(sighting.entityId(), established.id);
		logicalByLabel.put(sighting.labelId(), established.id);
		DebugLog.line("IDENTITY", "REBOUND-COALESCE " + sighting.critter().name()
			+ " L" + duplicate.id + " -> L" + established.id
			+ " pity=" + established.pity);
		return established.id;
	}

	private static boolean reboundDuplicate(Logical established, Logical duplicate, long now) {
		if (established.pity <= 0 || duplicate.pity != 0 || duplicate.activeAttempt != 0L
			|| duplicate.life == Life.CAUGHT
			|| now - duplicate.createdAt > RECENT_TARGET_MILLIS) return false;
		if (established.activeAttempt == 0L) return true;
		// A retry may already be active when its replacement body and label settle.
		// Keep that attempt on the established identity, but never absorb a peer that
		// existed before the attempt. The caller also requires a shared label, proximity,
		// and absence of one representation from the complete observed-body batch.
		Attempt active = attempts.get(established.activeAttempt);
		return active != null && active.outcome == Outcome.PENDING
			&& duplicate.createdAt >= active.startedAt;
	}

	/**
	 * Labels are presentation entities and can jump between tightly grouped moving
	 * bodies. The body-owned logical remains authoritative, so a label transfer must
	 * detach its former owner and update both sides of the index atomically.
	 */
	private static void bindLabel(Logical logical, UUID labelId) {
		if (logical == null || labelId == null) return;
		if (labelId.equals(logical.labelId)
			&& Long.valueOf(logical.id).equals(logicalByLabel.get(labelId))) return;
		Long formerOwnerId = logicalByLabel.get(labelId);
		if (formerOwnerId != null && formerOwnerId != logical.id) {
			Logical formerOwner = logicals.get(formerOwnerId);
			if (formerOwner != null && labelId.equals(formerOwner.labelId)) {
				formerOwner.labelId = null;
			}
			logicalByLabel.remove(labelId, formerOwnerId);
		}
		if (logical.labelId != null) logicalByLabel.remove(logical.labelId, logical.id);
		logical.labelId = labelId;
		logicalByLabel.put(labelId, logical.id);
	}

	private static boolean guaranteed(Attempt attempt, Logical logical) {
		return attempt.masterful || CritterCatchRules.guaranteedWithoutPity(logical.critter)
			|| logical.pity >= Markers.pityThreshold(logical.critter.rarity());
	}

	/** Globally minimizes each same-species batch so one body cannot satisfy two breakouts. */
	private static void matchFreshBodies(List<BodyObservation> fresh, long now) {
		Set<Integer> claimedBodies = new HashSet<>();
		Map<Critter, List<Integer>> rowsBySpecies = new LinkedHashMap<>();
		for (int index = 0; index < fresh.size(); index++) {
			rowsBySpecies.computeIfAbsent(fresh.get(index).critter(), ignored -> new ArrayList<>()).add(index);
		}
		for (Map.Entry<Critter, List<Integer>> speciesRows : rowsBySpecies.entrySet()) {
			boolean singleton = CritterSpawnRanges.maximum(speciesRows.getKey()) == 1;
			List<Logical> candidates = logicals.values().stream()
				.filter(logical -> logical.critter.equals(speciesRows.getKey())
					&& logical.activeAttempt != 0L && !logical.visible)
				.filter(logical -> {
					Attempt attempt = attempts.get(logical.activeAttempt);
					// A guaranteed catch cannot produce a replacement body. Treating a
					// concurrently appearing peer as its reappearance steals that peer's
					// identity and makes the next real attempt disappear from the model.
					return attempt != null && attempt.outcome == Outcome.PENDING
						&& (!guaranteed(attempt, logical) || awaitingRetryBody(attempt, logical, now));
				})
				.toList();
			if (candidates.isEmpty()) continue;
			List<Integer> rows = speciesRows.getValue();
			double[][] distances = new double[rows.size()][candidates.size()];
			for (int row = 0; row < rows.size(); row++) {
				Vec3 body = fresh.get(rows.get(row)).box().getCenter();
				for (int column = 0; column < candidates.size(); column++) {
					Logical logical = candidates.get(column);
					Attempt attempt = attempts.get(logical.activeAttempt);
					double distance = evidenceDistanceSq(attempt, body);
					// A delayed first body is a local form replacement, not an arbitrary
					// same-species appearance near a travelling capsule.
					if (guaranteed(attempt, logical)) {
						distance = logical.box.getCenter().distanceToSqr(body);
						if (distance > FORM_REKEY_DISTANCE_SQ) distance = Double.POSITIVE_INFINITY;
					}
					distances[row][column] = distance;
				}
			}
			int[] assignment = CapturePairing.match(distances,
				singleton ? Double.POSITIVE_INFINITY : REAPPEAR_DISTANCE_SQ,
				singleton ? SINGLETON_UNMATCHED_COST : UNMATCHED_REAPPEAR_COST);
			StructuredDiagnostics.capturePairing("breakout", distances,
				singleton ? Double.POSITIVE_INFINITY : REAPPEAR_DISTANCE_SQ,
				singleton ? SINGLETON_UNMATCHED_COST : UNMATCHED_REAPPEAR_COST, assignment,
				() -> rows.stream().map(index -> fresh.get(index).entityId().toString()).toList(),
				() -> candidates.stream().map(logical -> logical.id).toList());
			int matched = 0;
			for (int row = 0; row < assignment.length; row++) {
				int column = assignment[row];
				if (column < 0) continue;
				int freshIndex = rows.get(row);
				claimedBodies.add(freshIndex);
				matched++;
				reassignBody(candidates.get(column), fresh.get(freshIndex), distances[row][column], now);
			}
			if (rows.size() > 1 || candidates.size() > 1) {
				DebugLog.line("IDENTITY", "GROUP " + speciesRows.getKey().name()
					+ " bodies=" + rows.size() + " candidates=" + candidates.size()
					+ " matched=" + matched);
			}
		}
		matchResolvedBodyForms(fresh, claimedBodies, now);
		for (int index = 0; index < fresh.size(); index++) {
			if (claimedBodies.contains(index)) continue;
			BodyObservation sighting = fresh.get(index);
			Logical logical = new Logical(++nextLogicalId, sighting.critter(), sighting.labelId(), sighting.entityId(),
				sighting.box(), sighting.sparkling(), now);
			logicals.put(logical.id, logical);
			bindLabel(logical, sighting.labelId());
			logicalByEntity.put(logical.entityId, logical.id);
		}
	}

	/**
	 * Some critters replace their entity form after a breakout. Once the prior attempt is
	 * resolved there is no active-attempt candidate for the normal reappearance matcher,
	 * so join an unclaimed body to a vanished breakout at the same location. Hideons can
	 * wait there outside observation range: their resolved identity lasts for the visit,
	 * not a one-second animation window. Delayed spatial handoffs require an unambiguous
	 * candidate in both directions; other species keep the short form-change window.
	 */
	private static void matchResolvedBodyForms(List<BodyObservation> fresh,
			Set<Integer> claimedBodies, long now) {
		Map<Critter, List<Integer>> rowsBySpecies = new LinkedHashMap<>();
		for (int index = 0; index < fresh.size(); index++) {
			if (!claimedBodies.contains(index)) {
				rowsBySpecies.computeIfAbsent(fresh.get(index).critter(), ignored -> new ArrayList<>())
					.add(index);
			}
		}
		for (Map.Entry<Critter, List<Integer>> speciesRows : rowsBySpecies.entrySet()) {
			List<Logical> candidates = logicals.values().stream()
				.filter(logical -> logical.critter.equals(speciesRows.getKey())
					&& logical.activeAttempt == 0L && logical.pity > 0 && !logical.visible
					&& logical.life == Life.BREAKOUT
					&& (now - logical.seenAt <= FORM_REKEY_MILLIS || stationaryHideonBreakout(logical)))
				.toList();
			if (candidates.isEmpty()) continue;
			List<Integer> rows = speciesRows.getValue();
			double[][] distances = new double[rows.size()][candidates.size()];
			for (int row = 0; row < rows.size(); row++) {
				Vec3 center = fresh.get(rows.get(row)).box().getCenter();
				for (int column = 0; column < candidates.size(); column++) {
					distances[row][column] = candidates.get(column).box.getCenter().distanceToSqr(center);
				}
			}
			int[] assignment = CapturePairing.match(distances,
				FORM_REKEY_DISTANCE_SQ, FORM_REKEY_DISTANCE_SQ + 1.0);
			for (int row = 0; row < assignment.length; row++) {
				int column = assignment[row];
				if (column < 0 || now - candidates.get(column).seenAt <= FORM_REKEY_MILLIS) continue;
				int bodyChoices = 0, identityChoices = 0;
				for (double distance : distances[row]) if (distance <= FORM_REKEY_DISTANCE_SQ) identityChoices++;
				for (double[] bodyDistances : distances) if (bodyDistances[column] <= FORM_REKEY_DISTANCE_SQ) bodyChoices++;
				if (bodyChoices != 1 || identityChoices != 1) assignment[row] = -1;
			}
			StructuredDiagnostics.capturePairing("form-rekey", distances,
				FORM_REKEY_DISTANCE_SQ, FORM_REKEY_DISTANCE_SQ + 1.0, assignment,
				() -> rows.stream().map(index -> fresh.get(index).entityId().toString()).toList(),
				() -> candidates.stream().map(logical -> logical.id).toList());
			for (int row = 0; row < assignment.length; row++) {
				int column = assignment[row];
				if (column < 0) continue;
				int freshIndex = rows.get(row);
				Logical logical = candidates.get(column);
				BodyObservation sighting = fresh.get(freshIndex);
				UUID oldId = logical.entityId;
				long unseenMillis = now - logical.seenAt;
				logicalByEntity.remove(oldId, logical.id);
				logical.entityId = sighting.entityId();
				bindLabel(logical, sighting.labelId());
				logical.box = sighting.box();
				logical.presentationBox = stablePresentationBox(logical.presentationBox, logical.box);
				logical.sparkling |= sighting.sparkling();
				logical.seenAt = now;
				logical.visible = true;
				logical.evidence = EvidenceLevel.VISIBLE;
				logicalByEntity.put(logical.entityId, logical.id);
				claimedBodies.add(freshIndex);
				effects.bodyReplaced(logical.critter, oldId, logical.entityId);
				DebugLog.line("IDENTITY", "FORM-REKEY " + logical.critter.name()
					+ " logical=L" + logical.id + " " + shortId(oldId) + " -> "
					+ shortId(logical.entityId) + " pity=" + logical.pity + " distance="
					+ "%.2f".formatted(Math.sqrt(distances[row][column])) + " unseenMs=" + unseenMillis);
			}
		}
	}

	private static boolean stationaryHideonBreakout(Logical logical) {
		return "Hideonfloor".equals(logical.critter.name()) || "Hideonwall".equals(logical.critter.name());
	}

	private static void reassignBody(Logical logical, BodyObservation sighting,
			double distanceSq, long now) {
		UUID oldId = logical.entityId;
		logical.entityId = sighting.entityId();
		bindLabel(logical, sighting.labelId());
		logical.box = sighting.box();
		logical.presentationBox = stablePresentationBox(logical.presentationBox, logical.box);
		logical.sparkling |= sighting.sparkling();
		logical.seenAt = now;
		logical.visible = true;
		logical.evidence = EvidenceLevel.VISIBLE;
		logicalByEntity.remove(oldId, logical.id);
		logicalByEntity.put(logical.entityId, logical.id);
		Attempt active = logical.activeAttempt == 0L ? null : attempts.get(logical.activeAttempt);
		boolean guaranteedCapture = active != null && guaranteed(active, logical);
		boolean firstBody = acceptFirstAttemptBody(active, logical);
		// A form substitution in one observation batch has no absence interval.
		// Only a body returning after confirmed absence can establish a breakout.
		boolean reappeared = active != null && active.targetAbsent && !guaranteedCapture && !firstBody;
		logical.life = reappeared ? Life.BREAKOUT : Life.ATTEMPTING;
		if (active != null && active.outcome == Outcome.PENDING
			&& oldId.equals(active.targetEntityId)) {
			active.targetEntityId = logical.entityId;
			effects.reassign(active.id, logical.critter, oldId, logical.entityId,
				logical.presentationBox, logical.box, logical.sparkling, logical.pity);
		}
		if (reappeared) confirmReappearance(logical, now);
		DebugLog.line("IDENTITY", (reappeared ? "REAPPEAR " : "BODY-FORM ") + logical.critter.name() + " logical=L" + logical.id
			+ " entity " + shortId(oldId) + " -> " + shortId(logical.entityId)
			+ " distance=" + "%.2f".formatted(Math.sqrt(distanceSq)));
	}

	private static void beginAttempt(long attemptId, Critter critter, long now, boolean masterful, AttemptEvidence evidence) {
		Aim aim = evidence.aim();
		Logical best = null;
		double bestScore = Double.MAX_VALUE;
		boolean exactTarget = false;
		if (recentInteraction != null && now - recentInteraction.observedAt() <= INTERACTION_TARGET_MILLIS) {
			Long directId = logicalByEntity.get(recentInteraction.entityId());
			if (directId == null) directId = logicalByLabel.get(recentInteraction.entityId());
			Logical direct = directId == null ? null : logicals.get(directId);
			if (direct == null || !direct.critter.equals(critter) || !direct.visible) {
				direct = logicals.values().stream()
					.filter(logical -> logical.critter.equals(critter) && logical.visible
						&& logical.box.distanceToSqr(recentInteraction.position())
							<= INTERACTION_TARGET_DISTANCE_SQ)
					.min(java.util.Comparator.comparingDouble(logical ->
						logical.box.distanceToSqr(recentInteraction.position())))
					.orElse(null);
			}
			if (direct != null && direct.life != Life.CAUGHT && direct.activeAttempt == 0L) {
				best = direct;
				bestScore = -1.0;
				exactTarget = true;
				DebugLog.line("IDENTITY", "INTERACTION-TARGET " + critter.name()
					+ " entity=" + shortId(direct.entityId));
			}
		}
		boolean interactionExact = exactTarget;
		for (Logical logical : logicals.values()) {
			if (interactionExact) break;
			if (!logical.critter.equals(critter) || logical.life == Life.CAUGHT
				|| !logical.visible && now - logical.seenAt > RECENT_TARGET_MILLIS) continue;
			if (logical.activeAttempt != 0L) {
				Attempt active = attempts.get(logical.activeAttempt);
				// An invisible breakout must first rejoin its prior attempt. Replacing the
				// active pointer here would make that reappearance resolve the new retry.
				if (active != null && (active.outcome == Outcome.PENDING || !logical.visible)) continue;
			}
			double score = targetScore(aim, logical.box);
			if (score >= bestScore) continue;
			best = logical;
			bestScore = score;
		}
		// A ray intersection is exact client-side physical evidence. Starting the
		// presentation here preserves the complete linear animation without guessing
		// among nearby same-species bodies that the crosshair did not intersect.
		if (!interactionExact && bestScore < 0.0) exactTarget = true;
		if (best == null) {
			Logical retry = retryTarget(critter, now);
			if (retry != null) {
				Attempt previous = attempts.get(retry.activeAttempt);
				boolean queuedBreakout = previous != null
					&& consumeQueuedBreakout(critter, previous.startedAt);
				if (previous != null && (queuedBreakout || previous.reappearanceConfirmed)) {
					previous.reappearanceConfirmed = true;
					resolve(previous, Outcome.BREAKOUT, now);
					if (!queuedBreakout) {
						earlyBreakoutAcks.computeIfAbsent(critter, ignored -> new ArrayDeque<>())
							.addLast(now);
					}
					DebugLog.line("IDENTITY", "RETRY-EVIDENCE A" + previous.id + " "
						+ critter.name() + " logical=L" + retry.id
						+ " (new throw confirms breakout)");
					createAttempt(attemptId, retry, now, false, interactionTargets(retry, now), masterful);
					return;
				}
			}
			StillCritters.AttemptTarget remembered = evidence.remembered();
			if (remembered != null) {
				Long logicalId = logicalByEntity.get(remembered.id());
				if (logicalId == null) logicalId = logicalByLabel.get(remembered.id());
				best = logicalId == null ? null : logicals.get(logicalId);
				if (best == null) {
					best = new Logical(++nextLogicalId, critter, remembered.id(), remembered.id(),
						remembered.box(), remembered.sparkling(), now);
					best.visible = false;
					best.life = Life.LOST;
					logicals.put(best.id, best);
					logicalByEntity.put(remembered.id(), best.id);
					logicalByLabel.put(remembered.id(), best.id);
				}
				if (best.activeAttempt == 0L && best.life != Life.CAUGHT) {
					DebugLog.line("IDENTITY", "REMEMBERED-TARGET " + critter.name()
						+ " entity=" + shortId(best.entityId));
					createAttempt(attemptId, best, now, false, false, masterful);
					return;
				}
			}
			deferredAttempts.computeIfAbsent(critter, ignored -> new ArrayDeque<>())
				.addLast(new DeferredAttempt(attemptId, critter, now, masterful, aim));
			DebugLog.line("IDENTITY", "ATTEMPT-MISSED A" + attemptId + " " + critter.name()
				+ " logicals=" + logicals.size() + " sightings=" + CritterEntities.all().size()
				+ " (deferred)");
			return;
		}
		createAttempt(attemptId, coalesceRetryIdentity(best, now), now, false, exactTarget, masterful);
	}

	private static boolean interactionTargets(Logical logical, long now) {
		if (logical == null || recentInteraction == null
			|| now - recentInteraction.observedAt() > INTERACTION_TARGET_MILLIS) return false;
		Long logicalId = logicalByEntity.get(recentInteraction.entityId());
		if (logicalId == null) logicalId = logicalByLabel.get(recentInteraction.entityId());
		return logical.id == (logicalId == null ? 0L : logicalId);
	}

	/**
	 * A rapid retry can arrive before the replacement body is visible to the entity
	 * scanner (especially for distant static critters). The new server-confirmed throw
	 * proves one prior breakout has reappeared. Bind it only when interaction evidence
	 * identifies the old logical critter, or when there is exactly one eligible prior
	 * attempt, so simultaneous groups never guess between unresolved individuals.
	 */
	private static Logical retryTarget(Critter critter, long now) {
		Logical interactionMatch = null;
		if (recentInteraction != null && now - recentInteraction.observedAt() <= INTERACTION_TARGET_MILLIS) {
			Long logicalId = logicalByEntity.get(recentInteraction.entityId());
			if (logicalId == null) logicalId = logicalByLabel.get(recentInteraction.entityId());
			Logical logical = logicalId == null ? null : logicals.get(logicalId);
			if (eligibleRetry(logical, critter)) interactionMatch = logical;
		}
		if (interactionMatch != null) return interactionMatch;

		Logical only = null;
		for (Logical logical : logicals.values()) {
			if (!eligibleRetry(logical, critter)) continue;
			if (only != null) return null;
			only = logical;
		}
		return only;
	}

	private static boolean eligibleRetry(Logical logical, Critter critter) {
		if (logical == null || !logical.critter.equals(critter) || logical.life == Life.CAUGHT
			|| logical.activeAttempt == 0L) return false;
		Attempt attempt = attempts.get(logical.activeAttempt);
		return attempt != null && attempt.outcome == Outcome.PENDING
			&& (attempt.reappearanceConfirmed || hasQueuedBreakout(critter, attempt.startedAt));
	}

	/**
	 * Reunites a replacement body that was briefly classified as capture scaffolding
	 * and therefore entered as a fresh logical identity. Creation ordering prevents an
	 * older, genuinely separate neighbour from being merged merely because it is close.
	 */
	private static Logical coalesceRetryIdentity(Logical selected, long now) {
		if (selected == null || selected.activeAttempt != 0L) return selected;
		Logical prior = null;
		Attempt priorAttempt = null;
		double bestDistance = RETRY_COALESCE_DISTANCE_SQ;
		for (Logical candidate : logicals.values()) {
			if (candidate.id == selected.id || !candidate.critter.equals(selected.critter)
				|| candidate.visible || candidate.activeAttempt == 0L) continue;
			Attempt attempt = attempts.get(candidate.activeAttempt);
			if (attempt == null || attempt.outcome != Outcome.PENDING
				|| selected.createdAt < attempt.startedAt
				|| !hasQueuedBreakout(candidate.critter, attempt.startedAt)) continue;
			double distance = candidate.box.getCenter().distanceToSqr(selected.box.getCenter());
			if (distance >= bestDistance) continue;
			bestDistance = distance;
			prior = candidate;
			priorAttempt = attempt;
		}
		if (prior == null) return selected;

		UUID oldId = prior.entityId;
		logicalByEntity.remove(oldId, prior.id);
		logicalByLabel.remove(prior.labelId, prior.id);
		logicalByEntity.remove(selected.entityId, selected.id);
		logicalByLabel.remove(selected.labelId, selected.id);
		logicals.remove(selected.id);
		prior.entityId = selected.entityId;
		prior.labelId = selected.labelId;
		prior.box = selected.box;
		prior.sparkling |= selected.sparkling;
		prior.pity = Math.max(prior.pity, selected.pity);
		prior.seenAt = now;
		prior.visible = true;
		logicalByEntity.put(prior.entityId, prior.id);
		logicalByLabel.put(prior.labelId, prior.id);
		confirmReappearance(prior, now);
		DebugLog.line("IDENTITY", "RETRY-COALESCE A" + priorAttempt.id + " "
			+ prior.critter.name() + " logical=L" + prior.id + " entity "
			+ shortId(oldId) + " -> " + shortId(prior.entityId));
		return prior;
	}

	private static boolean hasQueuedBreakout(Critter critter, long attemptStartedAt) {
		ArrayDeque<OutcomeNotice> notices = unmatchedOutcomes.get(critter);
		if (notices == null) return false;
		for (OutcomeNotice notice : notices) {
			if (notice.outcome() == Outcome.BREAKOUT && notice.observedAt() >= attemptStartedAt) {
				return true;
			}
		}
		return false;
	}

	private static boolean consumeQueuedBreakout(Critter critter, long attemptStartedAt) {
		ArrayDeque<OutcomeNotice> notices = unmatchedOutcomes.get(critter);
		if (notices == null) return false;
		for (var iterator = notices.iterator(); iterator.hasNext();) {
			OutcomeNotice notice = iterator.next();
			if (notice.outcome() != Outcome.BREAKOUT || notice.observedAt() < attemptStartedAt) continue;
			iterator.remove();
			if (notices.isEmpty()) unmatchedOutcomes.remove(critter);
			return true;
		}
		return false;
	}

	private static Attempt createAttempt(long attemptId, Logical target, long now, boolean deferred,
			boolean exactTarget, boolean masterful) {
		recentInteraction = null;
		Attempt attempt = new Attempt(attemptId, target, now, exactTarget, masterful);
		attempts.put(attempt.id, attempt);
		target.activeAttempt = attempt.id;
		target.pity++;
		target.life = Life.ATTEMPTING;
		target.evidence = EvidenceLevel.INTERACTION;
		DebugLog.line("IDENTITY", (deferred ? "DEFERRED-ATTEMPT A" : "ATTEMPT A")
			+ attempt.id + " " + target.critter.name() + " logical=L" + target.id
			+ " entity=" + shortId(target.entityId) + " pity=" + target.pity
			+ " target=" + (exactTarget ? "interaction" : deferred ? "deferred" : "inferred"));
		// A non-guaranteed attempt always presents as a reversible recatch pin. It
		// can safely begin from the provisional owner and move if later physical
		// evidence corrects that owner.
		if (!guaranteed(attempt, target)) {
			commitPresentation(attempt, target, "provisional-recatch", now);
		}
		// Interaction selection is useful ownership evidence, but labels and bodies
		// overlap in tight groups. A guaranteed animation therefore begins immediately
		// only when this is the sole eligible body; otherwise capsule intersection,
		// native shrink, or disappearance confirms the exact individual first.
		else if (isSoleEligibleTarget(attempt)) {
			commitPresentation(attempt, target,
				exactTarget ? "unique-interaction" : "unique-attempt", now);
		} else {
			DebugLog.line("IDENTITY", "PRESENTATION-WAIT A" + attempt.id + " "
				+ target.critter.name() + " entity=" + shortId(target.entityId)
				+ " reason=uncaught-peer (awaiting physical evidence)");
		}
		return attempt;
	}

	/** Binds rapid retries as soon as their replacement body becomes visible. */
	private static void bindDeferredAttempts(long now) {
		for (var entryIterator = deferredAttempts.entrySet().iterator(); entryIterator.hasNext();) {
			Map.Entry<Critter, ArrayDeque<DeferredAttempt>> entry = entryIterator.next();
			ArrayDeque<DeferredAttempt> queue = entry.getValue();
			while (!queue.isEmpty() && now - queue.peekFirst().observedAt() > DEFERRED_ATTEMPT_MILLIS) {
				DebugLog.line("IDENTITY", "DEFERRED-EXPIRE A" + queue.peekFirst().id() + " " + entry.getKey().name());
				queue.removeFirst();
			}
			while (!queue.isEmpty()) {
				// Camera movement after the throw is not evidence about that throw's target.
				DeferredAttempt pending = queue.peekFirst();
				Logical target = logicals.values().stream()
					.filter(logical -> logical.critter.equals(entry.getKey()) && logical.visible
						&& logical.life != Life.CAUGHT && logical.activeAttempt == 0L)
					.min(java.util.Comparator.comparingDouble(logical -> targetScore(pending.aim(), logical.box)))
					.orElse(null);
				if (target == null) break;
				DeferredAttempt deferred = queue.removeFirst();
				// A retry can be queued before the breakout replacement is classified.
				// Rejoin that fresh body to its prior logical identity before incrementing
				// pity or creating presentation state for the retry.
				target = coalesceRetryIdentity(target, now);
				if (target.activeAttempt != 0L) {
					queue.addFirst(deferred);
					break;
				}
				createAttempt(deferred.id(), target, deferred.observedAt(), true, false, deferred.masterful());
			}
			if (queue.isEmpty()) entryIterator.remove();
		}
	}

	private static double targetScore(Aim aim, AABB box) {
		if (aim == null) return Double.MAX_VALUE;
		Vec3 eye = aim.eye();
		Vec3 look = aim.direction();
		Vec3 end = eye.add(look.scale(RAY_DISTANCE));
		var intersection = box.inflate(TARGET_RAY_INFLATE).clip(eye, end);
		if (intersection.isPresent()) {
			return -1.0 + Math.min(1.0, eye.distanceTo(intersection.get()) / RAY_DISTANCE);
		}
		Vec3 toBox = box.getCenter().subtract(eye);
		double along = Math.max(0.0, toBox.dot(look));
		return toBox.subtract(look.scale(along)).length() + (along <= 0.0 ? 1000.0 : 0.0);
	}

	private static List<CapsuleObservation> sampleCapsules() {
		List<CapsuleObservation> result = new ArrayList<>();
		for (Entity entity : WorldEntities.current()) {
			if (!(entity instanceof Display.ItemDisplay display) || !isCritterCapsule(display)) continue;
			result.add(new CapsuleObservation(entity.getUUID(), entity.position()));
		}
		return result;
	}

	private static void observeCapsules(List<CapsuleObservation> observations, long now) {
		Set<UUID> present = new HashSet<>();
		for (CapsuleObservation observation : observations) {
			present.add(observation.entityId());
			CapsulePath path = capsulePaths.computeIfAbsent(observation.entityId(), CapsulePath::new);
			path.add(observation.position(), now);
			assignCapsule(path, now);
		}
		capsulePaths.entrySet().removeIf(entry -> !present.contains(entry.getKey())
			&& now - entry.getValue().seenAt > CAPSULE_AFTER_MILLIS);
	}

	private static void assignCapsule(CapsulePath path, long now) {
		Long assignedId = attemptByCapsule.get(path.id);
		if (assignedId != null) {
			Attempt assigned = attempts.get(assignedId);
			if (assigned != null) {
				if (assigned.outcome == Outcome.PENDING) {
					assigned.impact = path.points.peekLast();
					confirmCapsuleContact(assigned, secondLast(path.points), path.points.peekLast(), now);
				}
				return;
			}
			attemptByCapsule.remove(path.id);
		}
		Attempt best = null;
		double bestScore = Double.MAX_VALUE;
		double bestDistanceSq = Double.MAX_VALUE;
		boolean bestIntersects = false;
		Vec3 previous = secondLast(path.points);
		Vec3 latest = path.points.peekLast();
		if (latest == null) return;
		boolean firstObservation = previous == null;
		if (previous == null) previous = latest;
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING || attempt.capsuleEntityId != null
				|| now - attempt.startedAt > CAPSULE_AFTER_MILLIS) continue;
			Logical logical = logicals.get(attempt.logicalId);
			AABB target = logical == null ? null : logical.box;
			boolean intersects = capsuleTouches(target, previous, latest);
			// ATTEMPT chat describes impact, not launch. A capsule already in flight
			// can therefore predate it. Require a newly sampled entry into the target,
			// not an old segment or a stationary capsule left by an earlier attempt.
			if (path.firstSeenAt + 250L < attempt.startedAt
				&& (now - path.firstSeenAt > CAPSULE_AFTER_MILLIS || path.movedAt != now
					|| firstObservation || target == null
					|| target.inflate(0.4).contains(previous)
					|| !target.inflate(0.4).contains(latest))) continue;
			if (firstObservation && !intersects) continue;
			double distance = segmentDistanceSq(previous, latest, attempt.anchor);
			if (!intersects && distance > CAPSULE_ASSIGN_DISTANCE_SQ) continue;
			double score = intersects ? -1.0 + distance * 1.0e-6 : distance;
			if (score >= bestScore) continue;
			bestScore = score;
			bestDistanceSq = distance;
			bestIntersects = intersects;
			best = attempt;
		}
		if (best == null) return;
		best.capsuleEntityId = path.id;
		best.impact = latest;
		attemptByCapsule.put(path.id, best.id);
		confirmCapsuleContact(best, previous, latest, now);
		DebugLog.line("IDENTITY", "CAPSULE A" + best.id + " entity=" + shortId(path.id)
			+ " mode=" + (bestIntersects ? "INTERSECT" : "NEAR")
			+ " distance=" + "%.2f".formatted(Math.sqrt(bestDistanceSq))
			+ " points=" + path.points.size() + " flightMs=" + (now - path.firstSeenAt)
			+ " preImpact=" + (path.firstSeenAt + 250L < best.startedAt));
	}

	/** Initial observations inside a body are contact too, not just outside-to-inside crossings. */
	static boolean capsuleTouches(AABB box, Vec3 previous, Vec3 latest) {
		if (box == null || latest == null) return false;
		AABB contact = box.inflate(0.4);
		return contact.contains(latest) || previous != null
			&& (contact.contains(previous) || contact.clip(previous, latest).isPresent());
	}

	private static void confirmCapsuleContact(Attempt attempt, Vec3 previous, Vec3 latest, long now) {
		if (attempt.presentationCommitted) return;
		Logical target = logicals.get(attempt.logicalId);
		if (target == null || !guaranteed(attempt, target) || !capsuleTouches(target.box, previous, latest)) return;
		// Overlapping bodies still need native shrink/disappearance evidence. A capsule
		// touching two peers is not permission to animate the provisionally selected one.
		for (Logical other : logicals.values()) {
			if (other.id != target.id && other.critter.equals(target.critter) && other.visible
				&& other.life != Life.CAUGHT && capsuleTouches(other.box, previous, latest)) return;
		}
		commitPresentation(attempt, target, "capsule-contact", now);
	}

	private static List<CaptureLabelObservation> sampleCaptureLabels() {
		List<CaptureLabelObservation> result = new ArrayList<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (sighting.mob() != null) continue;
			result.add(new CaptureLabelObservation(sighting.critter(), sighting.label().getUUID(), sighting.label().position()));
		}
		return result;
	}

	private static void observeCaptureLabels(List<CaptureLabelObservation> observations, long now) {
		for (CaptureLabelObservation sighting : observations) {
			UUID labelId = sighting.labelId();
			// A previously paired live/breakout label may temporarily lose its body
			// during waking/form changes. It is not a new capsule, even when only one
			// same-species throw is pending elsewhere.
			Long bodyOwnerId = logicalByLabel.get(labelId);
			Logical bodyOwner = bodyOwnerId == null ? null : logicals.get(bodyOwnerId);
			if (bodyOwner != null) continue;
			Long assignedId = attemptByCaptureLabel.get(labelId);
			if (assignedId != null) {
				Attempt assigned = attempts.get(assignedId);
				if (assigned != null) {
					if (assigned.outcome == Outcome.PENDING) {
						assigned.impact = sighting.position();
					}
					continue;
				}
				attemptByCaptureLabel.remove(labelId);
			}
			Attempt best = null;
			Attempt sole = null;
			int eligible = 0;
			double bestDistance = Double.MAX_VALUE;
			Vec3 position = sighting.position();
			for (Attempt attempt : attempts.values()) {
				if (!attempt.critter.equals(sighting.critter()) || attempt.outcome != Outcome.PENDING
					|| attempt.captureLabelId != null
					|| now - attempt.startedAt > ATTEMPT_AFTER_MILLIS) continue;
				eligible++;
				sole = attempt;
				double distance = evidenceDistanceSq(attempt, position);
				if (distance > CAPTURE_LABEL_DISTANCE_SQ) continue;
				if (distance >= bestDistance) continue;
				bestDistance = distance;
				best = attempt;
			}
			// Species-only ATTEMPT chat can provisionally select the wrong nearby peer.
			// A sole unresolved throw still owns the sole capture label even when that
			// provisional anchor is far away; the impact then repairs the identity below.
			boolean soleFallback = best == null && eligible == 1;
			if (soleFallback) best = sole;
			if (best != null) {
				best.impact = position;
				best.captureLabelId = labelId;
				attemptByCaptureLabel.put(labelId, best.id);
				DebugLog.line("IDENTITY", "IMPACT A" + best.id + " label=" + shortId(labelId)
					+ " at=" + format(position) + (soleFallback ? " mode=SOLE" : ""));
			}
		}
	}

	/**
	 * Confirms inferred target ownership from body disappearance plus capture-label
	 * evidence. Disappearance is stronger than label distance because a nearby peer can
	 * be closer to the temporary label after tightly grouped bodies cross paths.
	 */
	private static void correctTargetsFromImpact(long now) {
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING || attempt.impact == null
				|| attempt.captureLabelId == null) continue;
			Logical current = logicals.get(attempt.logicalId);
			if (current == null) continue;
			boolean currentDisappeared = disappearedForAttempt(current, attempt);
			double currentDistance = current.box.getCenter().distanceToSqr(attempt.impact);
			Logical replacement = null;
			int replacementCount = 0;
			double replacementDistance = IMPACT_REASSIGN_DISTANCE_SQ;
			for (Logical candidate : logicals.values()) {
				if (candidate.id == current.id || !candidate.critter.equals(attempt.critter)
					|| candidate.life == Life.CAUGHT || !disappearedForAttempt(candidate, attempt)
					|| candidate.activeAttempt != 0L
					|| now - candidate.disappearedAt > RECENT_TARGET_MILLIS) continue;
				double distance = candidate.box.getCenter().distanceToSqr(attempt.impact);
				if (distance < IMPACT_REASSIGN_DISTANCE_SQ) replacementCount++;
				if (distance >= replacementDistance) continue;
				replacement = candidate;
				replacementDistance = distance;
			}
			// If the provisional body remains present while a peer disappears, the peer is
			// the actual target regardless of which center is closest to the label.
			boolean replacementWins = replacement != null && (attempt.exactTarget
				? !currentDisappeared && current.visible && replacementCount == 1
				: !currentDisappeared || replacementDistance + IMPACT_REASSIGN_MARGIN_SQ < currentDistance);
			if (replacementWins && !attempt.targetCorrected) {
				retargetAttempt(attempt, current, replacement, replacementDistance,
					"disappearance", true);
				current = replacement;
				currentDisappeared = true;
			}
			if (currentDisappeared) {
				attempt.targetAbsent = true;
				commitPresentation(attempt, current, "disappearance", now);
			} else if (isSoleEligibleTarget(attempt)) {
				commitPresentation(attempt, current, "unique-impact", now);
			}
		}
	}

	/**
	 * Uses the server's uniform body shrink as direct ownership evidence. This arrives
	 * before disappearance in grouped catches, allowing the independent smooth marker
	 * to replace the stepped native geometry without guessing which nearby peer was hit.
	 */
	private static void confirmTargetsFromNativeShrink(long now) {
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING || attempt.targetCorrected) continue;
			Logical current = logicals.get(attempt.logicalId);
			if (current == null || !guaranteed(attempt, current)) continue;
			Logical shrinking = null;
			for (Logical candidate : logicals.values()) {
				if (!candidate.critter.equals(attempt.critter) || !candidate.visible
					|| candidate.life == Life.CAUGHT || !nativeCaptureShrink(candidate)
					|| candidate.activeAttempt != 0L && candidate.activeAttempt != attempt.id) continue;
				if (shrinking != null) {
					shrinking = null;
					break;
				}
				shrinking = candidate;
			}
			if (shrinking == null) continue;
			if (shrinking.id != current.id && !attempt.targetCorrected) {
				double distance = shrinking.box.getCenter().distanceToSqr(attempt.anchor);
				retargetAttempt(attempt, current, shrinking, distance, "native-shrink", false);
				current = shrinking;
			}
			commitPresentation(attempt, current, "native-shrink", now);
		}
	}

	/** Whether this exact logical body is in Hypixel's uniform capture-scale animation. */
	private static boolean nativeCaptureShrink(Logical logical) {
		if (logical == null || logical.box == null || logical.presentationBox == null) return false;
		double fullWidth = logical.presentationBox.getXsize();
		double fullHeight = logical.presentationBox.getYsize();
		if (fullWidth <= 0.0 || fullHeight <= 0.0) return false;
		double widthScale = logical.box.getXsize() / fullWidth;
		double heightScale = logical.box.getYsize() / fullHeight;
		return isNativeCaptureScale(widthScale, heightScale);
	}

	/** Shared capture-scale predicate for tick ownership and frame-level suppression. */
	public static boolean isNativeCaptureScale(double widthScale, double heightScale) {
		return widthScale < NATIVE_CAPTURE_SCALE_THRESHOLD
			&& heightScale < NATIVE_CAPTURE_SCALE_THRESHOLD
			&& Math.abs(widthScale - heightScale) <= 0.08;
	}

	/** Immutable exact-body suppression set for the centralized render snapshot. */
	public static Set<UUID> nativeCaptureBodies() {
		Set<Critter> pendingSpecies = new HashSet<>();
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome == Outcome.PENDING) pendingSpecies.add(attempt.critter);
		}
		if (pendingSpecies.isEmpty()) return Set.of();
		Set<UUID> result = new HashSet<>();
		for (Logical logical : logicals.values()) {
			if (logical.entityId != null && logical.visible && logical.life != Life.CAUGHT
				&& pendingSpecies.contains(logical.critter)
				&& nativeCaptureShrink(logical)) result.add(logical.entityId);
		}
		return Set.copyOf(result);
	}

	private static boolean disappearedForAttempt(Logical logical, Attempt attempt) {
		return !logical.visible && logical.disappearedAt != 0L
			&& logical.disappearedAt + DISAPPEARANCE_EVIDENCE_LEAD_MILLIS >= attempt.startedAt;
	}

	private static boolean isSoleEligibleTarget(Attempt attempt) {
		int eligible = 0;
		for (Logical logical : logicals.values()) {
			// Observation freshness ranks provisional targets; it cannot prove uniqueness.
			// A distant, still-uncaught peer may be the actual target of this throw.
			if (!logical.critter.equals(attempt.critter) || logical.life == Life.CAUGHT) continue;
			eligible++;
			if (eligible > 1) return false;
		}
		return eligible == 1;
	}

	private static void retargetAttempt(Attempt attempt, Logical current,
			Logical replacement, double replacementDistance, String evidence,
			boolean targetAbsent) {
		attempt.targetCorrected = true;
		UUID oldId = attempt.targetEntityId;
		current.activeAttempt = 0L;
		if (current.life == Life.ATTEMPTING) current.life = Life.LIVE;
		current.pity = Math.max(0, current.pity - 1);
		replacement.pity++;
		replacement.activeAttempt = attempt.id;
		replacement.life = Life.ATTEMPTING;
		replacement.evidence = EvidenceLevel.INTERACTION;
		attempt.logicalId = replacement.id;
		attempt.targetEntityId = replacement.entityId;
		attempt.targetAbsent = targetAbsent;
		// Both correction paths use an already observed body (native shrink or
		// disappearance). Its next appearance is not an initial observation.
		attempt.awaitingFirstBody = false;
		if (attempt.presentationCommitted) {
			effects.correct(attempt.id, attempt.critter, oldId, replacement.entityId,
				replacement.presentationBox, replacement.box, replacement.sparkling,
				replacement.pity, guaranteed(attempt, replacement));
		}
		DebugLog.line("IDENTITY", "TARGET-RETARGET A" + attempt.id + " "
			+ attempt.critter.name() + " " + shortId(oldId) + " -> "
			+ shortId(replacement.entityId) + " evidence=" + evidence + " distance="
			+ "%.2f".formatted(Math.sqrt(replacementDistance)));
	}

	private static void commitPresentation(Attempt attempt, Logical target,
			String evidence, long now) {
		if (attempt.presentationCommitted || target == null || target.entityId == null
			|| target.box == null) return;
		attempt.presentationCommitted = true;
		boolean guaranteed = guaranteed(attempt, target);
		effects.present(attempt.id, target.critter, target.entityId, target.presentationBox,
			target.box, target.sparkling, target.pity, guaranteed, attempt.startedAt);
		DebugLog.line("IDENTITY", "PRESENTATION A" + attempt.id + " "
			+ target.critter.name() + " entity=" + shortId(target.entityId)
			+ " evidence=" + evidence + " delay=" + Math.max(0L, now - attempt.startedAt) + "ms");
	}

	/**
	 * A capsule path and its temporary capture scaffolding may overlap a second
	 * simultaneous throw. The target's position when the server accepted the throw
	 * remains independent evidence, so a later path sample must never replace it.
	 */
	private static double evidenceDistanceSq(Attempt attempt, Vec3 position) {
		double distance = attempt.anchor.distanceToSqr(position);
		if (attempt.impact != null) {
			distance = Math.min(distance, attempt.impact.distanceToSqr(position));
		}
		return distance;
	}

	/**
	 * Follows the latest body position while retaining the largest dimensions observed
	 * for that logical critter. Capture animation entities shrink their native AABB, but
	 * a recatch pin must preserve the same full-size marker the live critter used.
	 */
	private static AABB stablePresentationBox(AABB previous, AABB current) {
		if (previous == null) return current;
		double widthX = Math.max(previous.getXsize(), current.getXsize());
		double widthZ = Math.max(previous.getZsize(), current.getZsize());
		double height = Math.max(previous.getYsize(), current.getYsize());
		double centerX = (current.minX + current.maxX) * 0.5;
		double centerZ = (current.minZ + current.maxZ) * 0.5;
		return new AABB(centerX - widthX * 0.5, current.minY, centerZ - widthZ * 0.5,
			centerX + widthX * 0.5, current.minY + height, centerZ + widthZ * 0.5);
	}

	private static void queueOutcome(Critter critter, Outcome outcome, long now) {
		if (outcome == Outcome.BREAKOUT) {
			ArrayDeque<Long> acknowledgements = earlyBreakoutAcks.get(critter);
			if (acknowledgements != null) {
				while (!acknowledgements.isEmpty() && now - acknowledgements.peekFirst() > OUTCOME_AFTER_MILLIS) {
					acknowledgements.removeFirst();
				}
				if (!acknowledgements.isEmpty()) {
					long resolvedAt = acknowledgements.removeFirst();
					if (acknowledgements.isEmpty()) earlyBreakoutAcks.remove(critter);
					DebugLog.line("IDENTITY", "OUTCOME-ACK " + critter.name()
						+ " BREAKOUT delay=" + (now - resolvedAt) + "ms");
					return;
				}
			}
		}
		int pending = pendingAttempts(critter);
		if (pending == 0) {
			DebugLog.line("IDENTITY", "OUTCOME-UNMATCHED " + critter.name() + " " + outcome
				+ " (no attempt predates result)");
			return;
		}
		ArrayDeque<OutcomeNotice> queued = unmatchedOutcomes.computeIfAbsent(critter,
			ignored -> new ArrayDeque<>());
		queued.addLast(new OutcomeNotice(outcome, now));
		DebugLog.line("IDENTITY", "OUTCOME " + critter.name() + " " + outcome
			+ " queued=" + queued.size() + " pending=" + pending);
	}

	private static void reconcile(long now) {
		for (Map.Entry<Critter, ArrayDeque<OutcomeNotice>> entry : unmatchedOutcomes.entrySet()) {
			ArrayDeque<OutcomeNotice> outcomes = entry.getValue();
			// Direct reappearance evidence wins even when a catch message for another
			// simultaneous capsule arrived first.
			boolean progressed;
			do {
				progressed = false;
				for (var iterator = outcomes.iterator(); iterator.hasNext();) {
					OutcomeNotice notice = iterator.next();
					if (notice.outcome() != Outcome.BREAKOUT) continue;
					Attempt match = visibleBreakout(entry.getKey(), notice, now);
					if (match == null) continue;
					iterator.remove();
					resolve(match, Outcome.BREAKOUT, now);
					progressed = true;
					break;
				}
			} while (progressed);
			while (!outcomes.isEmpty()) {
				OutcomeNotice notice = outcomes.peekFirst();
				Attempt match = bestPending(entry.getKey(), notice, now);
				if (match == null) break;
				outcomes.removeFirst();
				Logical logical = logicals.get(match.logicalId);
				if (notice.outcome() == Outcome.BREAKOUT
					&& (logical == null || logical.life != Life.BREAKOUT)) {
					DebugLog.line("IDENTITY", "FALLBACK A" + match.id
						+ " BREAKOUT after=" + (now - notice.observedAt()) + "ms");
				} else if (notice.outcome() == Outcome.CAUGHT) {
					boolean absent = match.targetAbsent || logical == null || !logical.visible;
					String evidence = absent ? "body-absent"
						: pendingAttempts(entry.getKey()) == 1 ? "sole-attempt"
						: "ordered-fallback";
					DebugLog.line("IDENTITY", "MATCH A" + match.id + " CAUGHT evidence="
						+ evidence
						+ " after=" + (now - notice.observedAt()) + "ms");
				}
				resolve(match, notice.outcome(), now);
			}
		}
		unmatchedOutcomes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		for (Map.Entry<Critter, ArrayDeque<OutcomeNotice>> entry : unmatchedOutcomes.entrySet()) {
			entry.getValue().removeIf(notice -> {
				if (now - notice.observedAt() <= OUTCOME_AFTER_MILLIS) return false;
				DebugLog.line("IDENTITY", "OUTCOME-EXPIRE " + entry.getKey().name() + " "
					+ notice.outcome() + " (insufficient individual evidence)");
				return true;
			});
		}
		unmatchedOutcomes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		deferredAttempts.entrySet().removeIf(entry -> {
			entry.getValue().removeIf(attempt -> now - attempt.observedAt() > DEFERRED_ATTEMPT_MILLIS);
			return entry.getValue().isEmpty();
		});
		earlyBreakoutAcks.entrySet().removeIf(entry -> {
			entry.getValue().removeIf(resolvedAt -> now - resolvedAt > OUTCOME_AFTER_MILLIS);
			return entry.getValue().isEmpty();
		});
	}

	private static Attempt visibleBreakout(Critter critter, OutcomeNotice notice, long now) {
		CaptureOutcomeSelection selection = outcomeSelection(critter, notice);
		long chosen = selection.directBreakout();
		if (chosen != 0L) StructuredDiagnostics.captureOutcome(selection, true, true, now, 0L, chosen);
		return attempts.get(chosen);
	}

	private static CaptureOutcomeSelection outcomeSelection(Critter critter, OutcomeNotice notice) {
		CaptureOutcomeSelection selection = new CaptureOutcomeSelection(notice.observedAt(), StructuredDiagnostics.enabled());
		for (Attempt attempt : attempts.values()) {
			if (!attempt.critter.equals(critter)) continue;
			Logical logical = logicals.get(attempt.logicalId);
			selection.observe(attempt.id, attempt.startedAt, attempt.outcome == Outcome.PENDING,
				attempt.targetAbsent || logical == null || !logical.visible,
				logical != null && logical.life == Life.BREAKOUT);
		}
		return selection;
	}

	private static Attempt bestPending(Critter critter, OutcomeNotice notice, long now) {
		CaptureOutcomeSelection selection = outcomeSelection(critter, notice);
		boolean breakout = notice.outcome() == Outcome.BREAKOUT;
		long fallback = breakout ? BREAKOUT_FALLBACK_MILLIS : CAUGHT_FALLBACK_MILLIS;
		long chosen = selection.select(breakout, now, fallback);
		if (chosen != 0L) StructuredDiagnostics.captureOutcome(selection, breakout, false, now, fallback, chosen);
		return attempts.get(chosen);
	}

	private static void resolve(Attempt attempt, Outcome outcome, long now) {
		Logical logical = logicals.get(attempt.logicalId);
		CaptureLifecycle.Input input = new CaptureLifecycle.Input(
			attempt.outcome == Outcome.PENDING, outcome != Outcome.PENDING, attempt.id,
			logical != null, logical == null ? 0L : logical.activeAttempt,
			logical != null && logical.life == Life.CAUGHT, outcome == Outcome.CAUGHT,
			attempt.reappearanceConfirmed || logical != null && logical.life == Life.BREAKOUT);
		CaptureLifecycle.Resolution transition = CaptureLifecycle.resolve(input);
		if (transition == CaptureLifecycle.Resolution.IGNORE) return;
		attempt.outcome = outcome;
		attempt.resolvedAt = now;
		StructuredDiagnostics.captureTransition(input, attempt.logicalId, transition);
		if (transition == CaptureLifecycle.Resolution.RETIRE) {
			if (logical != null && logical.activeAttempt == attempt.id) logical.activeAttempt = 0L;
			// A projection must never outlive its authoritative attempt, even if a
			// defensive identity cleanup retired the logical first.
			effects.expired(attempt.id, attempt.critter, attempt.targetEntityId);
			DebugLog.line("IDENTITY", "RESOLVE-RETIRED A" + attempt.id + " " + outcome
				+ " entity=" + shortId(attempt.targetEntityId));
			return;
		}
		commitPresentation(attempt, logical, "outcome-" + outcome.name().toLowerCase(), now);
		if (transition == CaptureLifecycle.Resolution.CAUGHT) {
			logical.activeAttempt = 0L;
			logical.life = Life.CAUGHT;
			logical.caughtAt = now;
		} else {
			logical.life = Life.BREAKOUT;
			// Keep the attempt as the reappearance anchor when the server result wins
			// the race. A later body can still inherit this logical identity and pity.
			if (transition == CaptureLifecycle.Resolution.BREAKOUT_READY) logical.activeAttempt = 0L;
		}
		DebugLog.line("IDENTITY", "RESOLVE A" + attempt.id + " " + outcome
			+ " logical=L" + logical.id + " entity=" + shortId(logical.entityId));
		effects.resolved(attempt.id, logical.critter, attempt.targetEntityId, logical.entityId,
			logical.presentationBox, logical.box, logical.sparkling, logical.pity, outcome, logical.visible);
	}

	/** Retires remaining identity state once the run confirms the species exhausted. */
	public static void onConfirmedCatchTotal(Critter critter, int catches) {
		confirmCatchTotal(critter, catches, MonotonicTime.nowMillis());
	}

	static void confirmCatchTotal(Critter critter, int catches, long now) {
		if (critter == null || catches < CritterSpawnRanges.maximum(critter)) return;
		if (!exhaustedSpecies.add(critter)) return;
		MarkerRegistry.invalidate();
		List<Attempt> pending = attempts.values().stream()
			.filter(attempt -> critter.equals(attempt.critter)
				&& attempt.outcome == Outcome.PENDING)
			.toList();
		for (Attempt attempt : pending) {
			DebugLog.line("IDENTITY", "MAX-CATCH-RESOLVE A" + attempt.id + " "
				+ critter.name() + "=" + catches);
			resolve(attempt, Outcome.CAUGHT, now);
		}
		int retired = 0;
		for (Logical logical : logicals.values()) {
			if (!critter.equals(logical.critter) || logical.life == Life.CAUGHT) continue;
			logical.life = Life.CAUGHT;
			logical.caughtAt = now;
			logical.activeAttempt = 0L;
			retired++;
		}
		unmatchedOutcomes.remove(critter);
		earlyBreakoutAcks.remove(critter);
		ArrayDeque<DeferredAttempt> deferred = deferredAttempts.remove(critter);
		if (deferred != null) {
			for (DeferredAttempt attempt : deferred) {
				effects.expired(attempt.id(), critter, null);
			}
		}
		// Project completion only after the authoritative attempts have settled.
		effects.completed(critter, catches);
		if (retired > 0) {
			DebugLog.line("IDENTITY", "MAX-CATCH " + critter.name() + "=" + catches
				+ " retired=" + retired + " logicals");
		}
		var trace = CaptureReplayTrace.current();
		if (trace != null) trace.total(critter, catches, now);
	}

	private static void prune(long now) {
		var attemptIterator = attempts.entrySet().iterator();
		while (attemptIterator.hasNext()) {
			Attempt attempt = attemptIterator.next().getValue();
			if (attempt.outcome != Outcome.PENDING
				|| now - attempt.startedAt <= ATTEMPT_AFTER_MILLIS) continue;
			Logical logical = logicals.get(attempt.logicalId);
			if (logical != null && logical.activeAttempt == attempt.id) {
				logical.activeAttempt = 0L;
				logical.life = logical.visible ? Life.LIVE : Life.LOST;
			}
			effects.expired(attempt.id, attempt.critter, attempt.targetEntityId);
			DebugLog.line("IDENTITY", "ATTEMPT-EXPIRE A" + attempt.id + " "
				+ attempt.critter.name() + " logical=L" + attempt.logicalId);
			attemptIterator.remove();
		}
		attempts.entrySet().removeIf(entry -> entry.getValue().outcome != Outcome.PENDING
			&& now - entry.getValue().resolvedAt > ATTEMPT_AFTER_MILLIS);
		attemptByCapsule.entrySet().removeIf(entry -> !attempts.containsKey(entry.getValue()));
		attemptByCaptureLabel.entrySet().removeIf(entry -> !attempts.containsKey(entry.getValue()));
		for (Logical logical : logicals.values()) {
			if (logical.activeAttempt != 0L) {
				Attempt attempt = attempts.get(logical.activeAttempt);
				if (attempt == null || now - attempt.startedAt > ATTEMPT_AFTER_MILLIS) {
					logical.activeAttempt = 0L;
					if (logical.life == Life.ATTEMPTING) logical.life = Life.LOST;
				}
			}
		}
		var logicalIterator = logicals.entrySet().iterator();
		while (logicalIterator.hasNext()) {
			Logical logical = logicalIterator.next().getValue();
			if (logical.life != Life.CAUGHT || logical.visible
				|| now - logical.seenAt <= LOST_AFTER_MILLIS) continue;
			logicalByEntity.remove(logical.entityId, logical.id);
			logicalByLabel.remove(logical.labelId, logical.id);
			logicalIterator.remove();
		}
		if (DebugLog.isEnabled()) auditInvariants();
	}

	/** A remembered/rapid-retry target's first observed body is not a second breakout. */
	private static boolean awaitingRetryBody(Attempt attempt, Logical logical, long now) {
		return attempt.awaitingFirstBody && logical.pity > 1
			&& now - attempt.startedAt <= DEFERRED_ATTEMPT_MILLIS;
	}

	/** A remembered/rapid-retry target's first observed body is not a second breakout. */
	private static boolean acceptFirstAttemptBody(Attempt attempt, Logical logical) {
		if (attempt == null || attempt.outcome != Outcome.PENDING || !attempt.awaitingFirstBody) return false;
		attempt.awaitingFirstBody = false;
		attempt.targetAbsent = false;
		DebugLog.line("IDENTITY", "FIRST-BODY A" + attempt.id + " " + logical.critter.name()
			+ " entity=" + shortId(logical.entityId) + " (established observation baseline)");
		return true;
	}

	private static void confirmReappearance(Logical logical, long now) {
		logical.life = Life.BREAKOUT;
		if (logical.activeAttempt == 0L) return;
		Attempt attempt = attempts.get(logical.activeAttempt);
		if (attempt != null && attempt.targetAbsent) attempt.reappearanceConfirmed = true;
		if (attempt == null) return;
		if (attempt.outcome == Outcome.BREAKOUT) {
			logical.activeAttempt = 0L;
			return;
		}
		if (attempt.outcome != Outcome.PENDING || !attempt.reappearanceConfirmed) return;

		ArrayDeque<OutcomeNotice> queued = unmatchedOutcomes.get(logical.critter);
		OutcomeNotice acknowledgement = null;
		if (queued != null) {
			for (var iterator = queued.iterator(); iterator.hasNext();) {
				OutcomeNotice notice = iterator.next();
				if (notice.outcome() != Outcome.BREAKOUT || notice.observedAt() < attempt.startedAt) continue;
				acknowledgement = notice;
				iterator.remove();
				break;
			}
			if (queued.isEmpty()) unmatchedOutcomes.remove(logical.critter);
		}
		if (acknowledgement != null) resolve(attempt, Outcome.BREAKOUT, now);
		else DebugLog.line("IDENTITY", "REAPPEAR-EVIDENCE A" + attempt.id + " "
			+ logical.critter.name() + " (awaiting outcome or retry)");
	}

	private static int pendingAttempts(Critter critter) {
		int count = 0;
		for (Attempt attempt : attempts.values()) {
			if (attempt.critter.equals(critter) && attempt.outcome == Outcome.PENDING) count++;
		}
		return count;
	}

	/** Development safety net: emit one concise line only when internal ownership is impossible. */
	private static void auditInvariants() {
		Set<UUID> entities = new HashSet<>();
		Set<UUID> labels = new HashSet<>();
		Set<Long> activeLogicals = new HashSet<>();
		for (Logical logical : logicals.values()) {
			if (logical.pity < 0) {
				invariant("pity:" + logical.id,
					"negative pity logical=L" + logical.id + " value=" + logical.pity);
			}
			if (logical.life != Life.CAUGHT && logical.entityId != null && !entities.add(logical.entityId)) {
				invariant("entity:" + logical.entityId,
					"entity owned twice id=" + shortId(logical.entityId));
			}
			if (logical.life != Life.CAUGHT && logical.labelId != null && !labels.add(logical.labelId)) {
				invariant("label:" + logical.labelId,
					"label owned twice id=" + shortId(logical.labelId));
			}
			if (logical.entityId != null
				&& !java.util.Objects.equals(logicalByEntity.get(logical.entityId), logical.id)) {
				invariant("entity-index:" + logical.id,
					"entity index mismatch logical=L" + logical.id + " id=" + shortId(logical.entityId));
			}
			if (logical.labelId != null
				&& !java.util.Objects.equals(logicalByLabel.get(logical.labelId), logical.id)) {
				invariant("label-index:" + logical.id,
					"label index mismatch logical=L" + logical.id + " id=" + shortId(logical.labelId));
			}
			if (logical.activeAttempt != 0L) {
				Attempt active = attempts.get(logical.activeAttempt);
				if (active == null || active.logicalId != logical.id
					|| !CaptureLifecycle.validLink(active.outcome == Outcome.PENDING,
						active.outcome == Outcome.BREAKOUT,
						logical.life == Life.BREAKOUT && !active.reappearanceConfirmed)) {
					invariant("active-link:" + logical.id,
						"invalid active attempt logical=L" + logical.id + " active=A"
							+ logical.activeAttempt);
				}
			}
		}
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING) continue;
			if (!activeLogicals.add(attempt.logicalId)) {
				invariant("pending:" + attempt.logicalId,
					"logical has multiple pending attempts logical=L" + attempt.logicalId);
			}
			Logical logical = logicals.get(attempt.logicalId);
			if (logical == null || logical.life == Life.CAUGHT) {
				invariant("orphan:" + attempt.id,
					"pending attempt without live logical attempt=A" + attempt.id);
			}
			if (logical != null && !logical.critter.equals(attempt.critter)) {
				invariant("species:" + attempt.id,
					"attempt/logical species mismatch attempt=A" + attempt.id);
			}
			if (logical != null && logical.activeAttempt != attempt.id) {
				invariant("link:" + attempt.id, "pending attempt/link mismatch attempt=A"
					+ attempt.id + " logical=L" + attempt.logicalId
					+ " active=A" + logical.activeAttempt);
			}
		}
	}

	private static void invariant(String key, String message) {
		if (loggedInvariantKeys.add(key)) DebugLog.line("INVARIANT", message);
	}

	private static boolean isCritterCapsule(Display.ItemDisplay display) {
		Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
		if (state == null) return false;
		ItemStack stack = state.itemStack();
		return !stack.isEmpty() && stack.getHoverName().getString().endsWith("Critter Capsule");
	}

	private static void resetForVisit(long newVisitEpoch) {
		MarkerRegistry.invalidate();
		int pending = 0;
		for (Attempt attempt : attempts.values()) if (attempt.outcome == Outcome.PENDING) pending++;
		int queued = unmatchedOutcomes.values().stream().mapToInt(ArrayDeque::size).sum();
		DebugLog.line("IDENTITY", "RESET visit=" + visitEpoch + " -> " + newVisitEpoch
			+ " logicals=" + logicals.size() + " attempts=" + attempts.size()
			+ " pending=" + pending + " queued=" + queued);
		visitEpoch = newVisitEpoch;
		logicals.clear();
		logicalByLabel.clear();
		logicalByEntity.clear();
		attempts.clear();
		capsulePaths.clear();
		attemptByCapsule.clear();
		attemptByCaptureLabel.clear();
		unmatchedOutcomes.clear();
		earlyBreakoutAcks.clear();
		deferredAttempts.clear();
		exhaustedSpecies.clear();
		loggedInvariantKeys.clear();
		recentInteraction = null;
		lastScan = 0L;
		CaptureReplayTrace.reset(newVisitEpoch, nextLogicalId, nextAttemptId);
	}

	private static Vec3 secondLast(ArrayDeque<Vec3> points) {
		if (points.size() < 2) return null;
		var iterator = points.descendingIterator();
		iterator.next();
		return iterator.next();
	}

	private static double segmentDistanceSq(Vec3 start, Vec3 end, Vec3 point) {
		Vec3 segment = end.subtract(start);
		double lengthSq = segment.lengthSqr();
		if (lengthSq <= 1.0e-9) return start.distanceToSqr(point);
		double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(segment) / lengthSq));
		return start.add(segment.scale(t)).distanceToSqr(point);
	}

	private static String shortId(UUID id) {
		return id == null ? "none" : id.toString().substring(0, 8);
	}

	private static String format(Vec3 position) {
		return "%.1f,%.1f,%.1f".formatted(position.x, position.y, position.z);
	}
}
