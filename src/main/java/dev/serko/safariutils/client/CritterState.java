package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.CritterSpawnRanges;
import dev.serko.safariutils.parse.CritterEvent;
import dev.serko.safariutils.state.EvidenceLevel;
import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariRuntimeState;
import net.minecraft.core.BlockPos;
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
	private static final long OUTCOME_AFTER_MILLIS = 10_000L;
	private static final long CAUGHT_BODY_CONTRADICTION_MILLIS = 1_500L;
	private static final int CAPSULE_PATH_LIMIT = 16;
	private static final double REAPPEAR_DISTANCE_SQ = 4.0 * 4.0;
	private static final double RETRY_COALESCE_DISTANCE_SQ = 2.0 * 2.0;
	private static final double CAPSULE_ASSIGN_DISTANCE_SQ = 12.0 * 12.0;
	private static final double CAPTURE_LABEL_DISTANCE_SQ = 10.0 * 10.0;
	private static final double UNMATCHED_REAPPEAR_COST = REAPPEAR_DISTANCE_SQ + 1.0;
	private static final double FORBIDDEN_REAPPEAR_COST = 1_000_000.0;
	private static final double RAY_DISTANCE = 64.0;
	private static final long INTERACTION_TARGET_MILLIS = 1_500L;
	private static final double INTERACTION_TARGET_DISTANCE_SQ = 2.5 * 2.5;
	/** Covers server ordering where the old body vanishes just before its ATTEMPT chat line. */
	private static final long RECENT_TARGET_MILLIS = 4_000L;
	/** Covers rapid retries whose ATTEMPT line precedes the replacement-body scan. */
	private static final long DEFERRED_ATTEMPT_MILLIS = 1_500L;
	/** Impact evidence may repair an aimed-at body chosen incorrectly in a tight group. */
	private static final double IMPACT_REASSIGN_DISTANCE_SQ = 8.0 * 8.0;
	private static final double IMPACT_REASSIGN_MARGIN_SQ = 1.0;

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

	public record AttemptView(long id, long logicalId, Critter critter, UUID targetEntityId,
			UUID capsuleEntityId, Vec3 anchor, Vec3 impact, Outcome outcome, long startedAt) { }

	private static final class Logical {
		private final long id;
		private final Critter critter;
		private UUID labelId;
		private UUID entityId;
		private AABB box;
		private boolean sparkling;
		private int pity;
		private Life life = Life.LIVE;
		private EvidenceLevel evidence = EvidenceLevel.VISIBLE;
		private final long createdAt;
		private long seenAt;
		private long caughtAt;
		private long activeAttempt;
		private boolean visible = true;

		private Logical(long id, Critter critter, UUID labelId, UUID entityId, AABB box,
				boolean sparkling, long seenAt) {
			this.id = id;
			this.critter = critter;
			this.labelId = labelId;
			this.entityId = entityId;
			this.box = box;
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
		private UUID capsuleEntityId;
		private UUID captureLabelId;
		private Vec3 impact;
		private boolean targetAbsent;
		private boolean reappearanceConfirmed;
		private Outcome outcome = Outcome.PENDING;
		private long resolvedAt;

		private Attempt(long id, Logical target, long startedAt) {
			this.id = id;
			this.logicalId = target.id;
			this.critter = target.critter;
			this.targetEntityId = target.entityId;
			this.anchor = target.box.getCenter();
			this.startedAt = startedAt;
		}
	}

	private static final class CapsulePath {
		private final UUID id;
		private final ArrayDeque<Vec3> points = new ArrayDeque<>(CAPSULE_PATH_LIMIT);
		private long firstSeenAt;
		private long seenAt;

		private CapsulePath(UUID id) {
			this.id = id;
		}

		private void add(Vec3 point, long now) {
			if (firstSeenAt == 0L) firstSeenAt = now;
			if (points.isEmpty() || points.peekLast().distanceToSqr(point) > 1.0e-6) {
				points.addLast(point);
				while (points.size() > CAPSULE_PATH_LIMIT) points.removeFirst();
			}
			seenAt = now;
		}
	}

	private record OutcomeNotice(Outcome outcome, long observedAt) { }
	private record RecentInteraction(UUID entityId, Vec3 position, long observedAt) { }
	private record DeferredAttempt(Critter critter, long observedAt, boolean masterful) { }

	private static final Map<Long, Logical> logicals = new LinkedHashMap<>();
	private static final Map<UUID, Long> logicalByLabel = new HashMap<>();
	private static final Map<UUID, Long> logicalByEntity = new HashMap<>();
	private static final Map<Long, Attempt> attempts = new LinkedHashMap<>();
	private static final Map<UUID, CapsulePath> capsulePaths = new HashMap<>();
	private static final Map<UUID, Long> attemptByCapsule = new HashMap<>();
	private static final Map<UUID, Long> attemptByCaptureLabel = new HashMap<>();
	private static final Map<Critter, ArrayDeque<OutcomeNotice>> unmatchedOutcomes = new HashMap<>();
	/** Breakouts resolved from visible reappearance before their delayed chat acknowledgement. */
	private static final Map<Critter, ArrayDeque<Long>> earlyBreakoutAcks = new HashMap<>();
	private static final Set<Critter> exhaustedSpecies = new HashSet<>();
	private static long nextLogicalId;
	private static long nextAttemptId;
	private static long lastScan;
	private static long visitEpoch;
	private static final Set<String> loggedInvariantKeys = new HashSet<>();
	private static RecentInteraction recentInteraction;
	private static final Map<Critter, ArrayDeque<DeferredAttempt>> deferredAttempts = new HashMap<>();

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
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;
		long now = System.currentTimeMillis();
		observeSightings(now);
		observeCapsules(now);
		observeCaptureLabels(now);
		correctTargetsFromImpact(now);
		reconcile(now);
		prune(now);
	}

	public static void onCritterEvent(CritterEvent event, String rawLine) {
		if (event == null || event.critter() == null) return;
		long now = System.currentTimeMillis();
		switch (event.type()) {
			case ATTEMPT -> {
				SparklingWatch.onCaptureInteraction(event.critter());
				RecatchSpots.onCaptureStarted(event.critter());
				beginAttempt(event.critter(), now,
					rawLine != null && rawLine.contains("Masterful Critter Capsule"));
			}
			case FAILED -> {
				SparklingWatch.onCaptureInteraction(event.critter());
				queueOutcome(event.critter(), Outcome.BREAKOUT, now);
			}
			case OWN_CATCH -> queueOutcome(event.critter(), Outcome.CAUGHT, now);
			case SHARED_CATCH, ENTERED_SAFARI -> {
			}
		}
		reconcile(now);
	}

	/** Retains the exact entity the player interacted with until the server ATTEMPT line arrives. */
	public static void onEntityInteraction(Entity entity) {
		if (entity == null) return;
		recentInteraction = new RecentInteraction(entity.getUUID(), entity.getBoundingBox().getCenter(),
			System.currentTimeMillis());
	}

	public static List<LogicalView> logicalCritters() {
		List<LogicalView> result = new ArrayList<>(logicals.size());
		for (Logical logical : logicals.values()) {
			result.add(new LogicalView(logical.id, logical.critter, logical.entityId, logical.box,
				logical.sparkling, logical.pity, logical.life, logical.evidence));
		}
		return List.copyOf(result);
	}

	public static List<AttemptView> activeAttempts() {
		List<AttemptView> result = new ArrayList<>();
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING) continue;
			result.add(view(attempt));
		}
		return List.copyOf(result);
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
		Set<UUID> live = new HashSet<>();
		List<CritterEntities.Sighting> fresh = new ArrayList<>();
		for (Logical logical : logicals.values()) logical.visible = false;
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			// Hideyho has a dedicated phase/location solver and no capsule pity lifecycle.
			if ("Hideyho".equals(sighting.critter().name())) continue;
			if (exhaustedSpecies.contains(sighting.critter())) continue;
			Entity body = sighting.mob();
			if (body == null || CritterEntities.isCaptureScaffolding(sighting)) continue;
			UUID entityId = body.getUUID();
			live.add(entityId);
			UUID labelId = sighting.label().getUUID();
			Long entityLogicalId = logicalByEntity.get(entityId);
			Long labelLogicalId = logicalByLabel.get(labelId);
			Long logicalId = entityLogicalId != null ? entityLogicalId : labelLogicalId;
			if (entityLogicalId != null && labelLogicalId != null
				&& !entityLogicalId.equals(labelLogicalId)) {
				DebugLog.line("IDENTITY", "LABEL-REBOUND " + sighting.critter().name()
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
			boolean reappeared = active != null && active.targetAbsent
				&& (logical.life == Life.ATTEMPTING || logical.life == Life.BREAKOUT);
			if (!entityId.equals(logical.entityId)) {
				logicalByEntity.remove(logical.entityId);
				logical.entityId = entityId;
				logicalByEntity.put(entityId, logical.id);
			}
			if (!labelId.equals(logical.labelId)) {
				logicalByLabel.remove(logical.labelId);
				logical.labelId = labelId;
				logicalByLabel.put(labelId, logical.id);
			}
			logical.box = body.getBoundingBox();
			logical.sparkling |= SparklingWatch.isSparkling(sighting);
			logical.seenAt = now;
			logical.visible = true;
			if (reappeared) confirmReappearance(logical, now);
			if (logical.life == Life.CAUGHT
				&& now - logical.caughtAt >= CAUGHT_BODY_CONTRADICTION_MILLIS) {
				DebugLog.line("IDENTITY", "CAUGHT-CONTRADICTION " + logical.critter.name()
					+ " logical=L" + logical.id + " entity=" + shortId(logical.entityId)
					+ " remained visible; restoring LIVE");
				logical.life = Life.LIVE;
				logical.evidence = EvidenceLevel.VISIBLE;
				RecatchSpots.onCaughtBodyRestored(logical.entityId);
			}
			if (logical.life == Life.LOST) logical.life = Life.LIVE;
		}

		matchFreshBodies(fresh, now);
		bindDeferredAttempts(now);
		for (Logical logical : logicals.values()) {
			if (logical.activeAttempt != 0L && logical.entityId != null
				&& !live.contains(logical.entityId)) {
				Attempt attempt = attempts.get(logical.activeAttempt);
				if (attempt != null && attempt.outcome != Outcome.CAUGHT) attempt.targetAbsent = true;
			}
			if (logical.entityId != null && !live.contains(logical.entityId)
				&& logical.life == Life.LIVE && now - logical.seenAt > LOST_AFTER_MILLIS) {
				logical.life = Life.LOST;
			}
		}
	}

	/** Globally minimizes each same-species batch so one body cannot satisfy two breakouts. */
	private static void matchFreshBodies(List<CritterEntities.Sighting> fresh, long now) {
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
					return attempt != null && attempt.outcome != Outcome.CAUGHT;
				})
				.toList();
			if (candidates.isEmpty()) continue;
			List<Integer> rows = speciesRows.getValue();
			double[][] costs = new double[rows.size()][candidates.size() + rows.size()];
			for (int row = 0; row < rows.size(); row++) {
				Vec3 body = fresh.get(rows.get(row)).mob().getBoundingBox().getCenter();
				for (int column = 0; column < candidates.size(); column++) {
					Logical logical = candidates.get(column);
					Attempt attempt = attempts.get(logical.activeAttempt);
					Vec3 anchor = attempt.impact == null ? attempt.anchor : attempt.impact;
					double distance = anchor.distanceToSqr(body);
					costs[row][column] = singleton || distance <= REAPPEAR_DISTANCE_SQ
						? distance : FORBIDDEN_REAPPEAR_COST;
				}
				for (int column = candidates.size(); column < costs[row].length; column++) {
					costs[row][column] = singleton
						? FORBIDDEN_REAPPEAR_COST - 1.0 : UNMATCHED_REAPPEAR_COST;
				}
			}
			int[] assignment = MinimumCostAssignment.solve(costs);
			int matched = 0;
			for (int row = 0; row < assignment.length; row++) {
				int column = assignment[row];
				if (column < 0 || column >= candidates.size()
					|| !singleton && costs[row][column] > REAPPEAR_DISTANCE_SQ) continue;
				int freshIndex = rows.get(row);
				claimedBodies.add(freshIndex);
				matched++;
				reassignBody(candidates.get(column), fresh.get(freshIndex), costs[row][column], now);
			}
			if (rows.size() > 1 || candidates.size() > 1) {
				DebugLog.line("IDENTITY", "GROUP " + speciesRows.getKey().name()
					+ " bodies=" + rows.size() + " candidates=" + candidates.size()
					+ " matched=" + matched);
			}
		}
		for (int index = 0; index < fresh.size(); index++) {
			if (claimedBodies.contains(index)) continue;
			CritterEntities.Sighting sighting = fresh.get(index);
			Entity body = sighting.mob();
			Logical logical = new Logical(++nextLogicalId, sighting.critter(), sighting.label().getUUID(), body.getUUID(),
				body.getBoundingBox(), SparklingWatch.isSparkling(sighting), now);
			logicals.put(logical.id, logical);
			logicalByLabel.put(logical.labelId, logical.id);
			logicalByEntity.put(logical.entityId, logical.id);
		}
	}

	private static void reassignBody(Logical logical, CritterEntities.Sighting sighting,
			double distanceSq, long now) {
		UUID oldId = logical.entityId;
		logical.entityId = sighting.mob().getUUID();
		logicalByLabel.remove(logical.labelId);
		logical.labelId = sighting.label().getUUID();
		logicalByLabel.put(logical.labelId, logical.id);
		logical.box = sighting.mob().getBoundingBox();
		logical.sparkling |= SparklingWatch.isSparkling(sighting);
		logical.seenAt = now;
		logical.visible = true;
		logical.life = Life.BREAKOUT;
		logical.evidence = EvidenceLevel.VISIBLE;
		logicalByEntity.remove(oldId);
		logicalByEntity.put(logical.entityId, logical.id);
		confirmReappearance(logical, now);
		DebugLog.line("IDENTITY", "REAPPEAR " + logical.critter.name() + " logical=L" + logical.id
			+ " entity " + shortId(oldId) + " -> " + shortId(logical.entityId)
			+ " distance=" + "%.2f".formatted(Math.sqrt(distanceSq)));
	}

	private static void beginAttempt(Critter critter, long now, boolean masterful) {
		long scan = CritterEntities.scannedAt();
		if (scan != lastScan) {
			lastScan = scan;
			observeSightings(now);
		}
		Player player = Minecraft.getInstance().player;
		Logical best = null;
		double bestScore = Double.MAX_VALUE;
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
				DebugLog.line("IDENTITY", "INTERACTION-TARGET " + critter.name()
					+ " entity=" + shortId(direct.entityId));
			}
		}
		for (Logical logical : logicals.values()) {
			if (bestScore < 0.0) break;
			if (!logical.critter.equals(critter) || logical.life == Life.CAUGHT
				|| !logical.visible && now - logical.seenAt > RECENT_TARGET_MILLIS) continue;
			if (logical.activeAttempt != 0L) {
				Attempt active = attempts.get(logical.activeAttempt);
				// An invisible breakout must first rejoin its prior attempt. Replacing the
				// active pointer here would make that reappearance resolve the new retry.
				if (active != null && (active.outcome == Outcome.PENDING || !logical.visible)) continue;
			}
			double score = targetScore(player, logical.box);
			if (score >= bestScore) continue;
			best = logical;
			bestScore = score;
		}
		if (best == null) {
			Logical retry = retryTarget(critter, now);
			if (retry != null) {
				Attempt previous = attempts.get(retry.activeAttempt);
				if (previous != null && consumeQueuedBreakout(critter, previous.startedAt)) {
					previous.reappearanceConfirmed = true;
					resolve(previous, Outcome.BREAKOUT, now);
					DebugLog.line("IDENTITY", "RETRY-EVIDENCE A" + previous.id + " "
						+ critter.name() + " logical=L" + retry.id
						+ " (new throw confirms breakout)");
					createAttempt(retry, now, false, masterful);
					return;
				}
			}
			StillCritters.AttemptTarget remembered = StillCritters.attemptTarget(critter);
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
					createAttempt(best, now, false, masterful);
					return;
				}
			}
			deferredAttempts.computeIfAbsent(critter, ignored -> new ArrayDeque<>())
				.addLast(new DeferredAttempt(critter, now, masterful));
			DebugLog.line("IDENTITY", "ATTEMPT-MISSED " + critter.name()
				+ " logicals=" + logicals.size() + " sightings=" + CritterEntities.all().size()
				+ " (deferred)");
			return;
		}
		createAttempt(coalesceRetryIdentity(best, now), now, false, masterful);
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
			&& hasQueuedBreakout(critter, attempt.startedAt);
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

	private static Attempt createAttempt(Logical target, long now, boolean deferred,
			boolean masterful) {
		recentInteraction = null;
		Attempt attempt = new Attempt(++nextAttemptId, target, now);
		attempts.put(attempt.id, attempt);
		target.activeAttempt = attempt.id;
		target.pity++;
		target.life = Life.ATTEMPTING;
		target.evidence = EvidenceLevel.INTERACTION;
		DebugLog.line("IDENTITY", (deferred ? "DEFERRED-ATTEMPT A" : "ATTEMPT A")
			+ attempt.id + " " + target.critter.name() + " logical=L" + target.id
			+ " entity=" + shortId(target.entityId) + " pity=" + target.pity);
		RecatchSpots.onIdentityAttempt(attempt.id, target.critter, target.entityId,
			target.box, target.sparkling, target.pity, masterful);
		StillCritters.onIdentityAttempt(attempt.id, target.critter, target.entityId,
			target.box);
		return attempt;
	}

	/** Binds rapid retries as soon as their replacement body becomes visible. */
	private static void bindDeferredAttempts(long now) {
		Player player = Minecraft.getInstance().player;
		for (var entryIterator = deferredAttempts.entrySet().iterator(); entryIterator.hasNext();) {
			Map.Entry<Critter, ArrayDeque<DeferredAttempt>> entry = entryIterator.next();
			ArrayDeque<DeferredAttempt> queue = entry.getValue();
			while (!queue.isEmpty() && now - queue.peekFirst().observedAt() > DEFERRED_ATTEMPT_MILLIS) {
				DebugLog.line("IDENTITY", "DEFERRED-EXPIRE " + entry.getKey().name());
				queue.removeFirst();
			}
			while (!queue.isEmpty()) {
				Logical target = logicals.values().stream()
					.filter(logical -> logical.critter.equals(entry.getKey()) && logical.visible
						&& logical.life != Life.CAUGHT && logical.activeAttempt == 0L)
					.min(java.util.Comparator.comparingDouble(logical -> targetScore(player, logical.box)))
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
				createAttempt(target, deferred.observedAt(), true, deferred.masterful());
			}
			if (queue.isEmpty()) entryIterator.remove();
		}
	}

	private static double targetScore(Player player, AABB box) {
		if (player == null) return Double.MAX_VALUE;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(RAY_DISTANCE));
		var intersection = box.inflate(0.15).clip(eye, end);
		if (intersection.isPresent()) return eye.distanceTo(intersection.get()) * 1.0e-3;
		Vec3 toBox = box.getCenter().subtract(eye);
		double along = Math.max(0.0, toBox.dot(look));
		return toBox.subtract(look.scale(along)).length() + (along <= 0.0 ? 1000.0 : 0.0);
	}

	private static void observeCapsules(long now) {
		Set<UUID> present = new HashSet<>();
		for (Entity entity : WorldEntities.current()) {
			if (!(entity instanceof Display.ItemDisplay display) || !isCritterCapsule(display)) continue;
			present.add(entity.getUUID());
			CapsulePath path = capsulePaths.computeIfAbsent(entity.getUUID(), CapsulePath::new);
			path.add(entity.position(), now);
			if (path.points.size() < 2) continue;
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
				if (assigned.outcome == Outcome.PENDING) assigned.impact = path.points.peekLast();
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
		if (previous == null || latest == null) return;
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING || attempt.capsuleEntityId != null
				|| now - attempt.startedAt > CAPSULE_AFTER_MILLIS
				|| path.firstSeenAt + 250L < attempt.startedAt) continue;
			Logical logical = logicals.get(attempt.logicalId);
			AABB target = logical == null ? null : logical.box;
			boolean intersects = target != null && target.inflate(0.4).clip(previous, latest).isPresent();
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
		DebugLog.line("IDENTITY", "CAPSULE A" + best.id + " entity=" + shortId(path.id)
			+ " mode=" + (bestIntersects ? "INTERSECT" : "NEAR")
			+ " distance=" + "%.2f".formatted(Math.sqrt(bestDistanceSq))
			+ " points=" + path.points.size());
	}

	private static void observeCaptureLabels(long now) {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (sighting.mob() != null) continue;
			UUID labelId = sighting.label().getUUID();
			Long assignedId = attemptByCaptureLabel.get(labelId);
			if (assignedId != null) {
				Attempt assigned = attempts.get(assignedId);
				if (assigned != null) {
					if (assigned.outcome == Outcome.PENDING) assigned.impact = sighting.label().position();
					continue;
				}
				attemptByCaptureLabel.remove(labelId);
			}
			Attempt best = null;
			double bestDistance = Double.MAX_VALUE;
			Vec3 position = sighting.label().position();
			for (Attempt attempt : attempts.values()) {
				if (!attempt.critter.equals(sighting.critter()) || attempt.outcome != Outcome.PENDING
					|| attempt.captureLabelId != null
					|| now - attempt.startedAt > ATTEMPT_AFTER_MILLIS) continue;
				Vec3 anchor = attempt.impact == null ? attempt.anchor : attempt.impact;
				double distance = anchor.distanceToSqr(position);
				if (distance > CAPTURE_LABEL_DISTANCE_SQ) continue;
				if (distance >= bestDistance) continue;
				bestDistance = distance;
				best = attempt;
			}
			if (best != null) {
				best.impact = position;
				best.captureLabelId = labelId;
				attemptByCaptureLabel.put(labelId, best.id);
				DebugLog.line("IDENTITY", "IMPACT A" + best.id + " label=" + shortId(labelId)
					+ " at=" + format(position));
			}
		}
	}

	/**
	 * Repairs a provisional aim choice when the capsule impact is much closer to a
	 * different same-species body that just disappeared. This is the physical evidence
	 * available for tightly grouped critters such as Bloodbats and Cavernfish.
	 */
	private static void correctTargetsFromImpact(long now) {
		for (Attempt attempt : attempts.values()) {
			if (attempt.outcome != Outcome.PENDING || attempt.impact == null) continue;
			Logical current = logicals.get(attempt.logicalId);
			if (current == null || !current.visible) continue;
			double currentDistance = current.box.getCenter().distanceToSqr(attempt.impact);
			Logical replacement = null;
			double replacementDistance = IMPACT_REASSIGN_DISTANCE_SQ;
			for (Logical candidate : logicals.values()) {
				if (candidate.id == current.id || !candidate.critter.equals(attempt.critter)
					|| candidate.visible || candidate.life == Life.CAUGHT
					|| candidate.activeAttempt != 0L
					|| now - candidate.seenAt > RECENT_TARGET_MILLIS) continue;
				double distance = candidate.box.getCenter().distanceToSqr(attempt.impact);
				if (distance >= replacementDistance) continue;
				replacement = candidate;
				replacementDistance = distance;
			}
			if (replacement == null
				|| replacementDistance + IMPACT_REASSIGN_MARGIN_SQ >= currentDistance) continue;

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
			attempt.targetAbsent = true;
			RecatchSpots.correctIdentityTarget(attempt.id, attempt.critter, oldId,
				replacement.entityId, replacement.box, replacement.sparkling, replacement.pity);
			StillCritters.correctResolvingTarget(attempt.critter, oldId, replacement.entityId,
				BlockPos.containing(replacement.box.getCenter()));
			DebugLog.line("IDENTITY", "IMPACT-RETARGET A" + attempt.id + " "
				+ attempt.critter.name() + " " + shortId(oldId) + " -> "
				+ shortId(replacement.entityId) + " impactDistance="
				+ "%.2f".formatted(Math.sqrt(replacementDistance)));
		}
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
					Attempt match = visibleBreakout(entry.getKey(), notice);
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
					DebugLog.line("IDENTITY", "MATCH A" + match.id + " CAUGHT evidence="
						+ (absent ? "body-absent" : "timeout")
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

	private static Attempt visibleBreakout(Critter critter, OutcomeNotice notice) {
		for (Attempt attempt : attempts.values()) {
			if (!attempt.critter.equals(critter) || attempt.outcome != Outcome.PENDING
				|| attempt.startedAt > notice.observedAt()) continue;
			Logical logical = logicals.get(attempt.logicalId);
			if (logical != null && logical.life == Life.BREAKOUT) return attempt;
		}
		return null;
	}

	private static Attempt bestPending(Critter critter, OutcomeNotice notice, long now) {
		Attempt oldest = null;
		Attempt oldestAbsent = null;
		for (Attempt attempt : attempts.values()) {
			if (!attempt.critter.equals(critter) || attempt.outcome != Outcome.PENDING
				|| attempt.startedAt > notice.observedAt()) continue;
			Logical logical = logicals.get(attempt.logicalId);
			// A visibly reappeared body is direct evidence for a breakout. Prefer it over
			// throw order when several same-species capsules resolve unpredictably.
			if (notice.outcome() == Outcome.BREAKOUT && logical != null && logical.life == Life.BREAKOUT) {
				return attempt;
			}
			if (notice.outcome() == Outcome.CAUGHT
				&& (attempt.targetAbsent || logical == null || !logical.visible)
				&& (oldestAbsent == null || attempt.startedAt < oldestAbsent.startedAt)) {
				oldestAbsent = attempt;
			}
			if (oldest == null || attempt.startedAt < oldest.startedAt) oldest = attempt;
		}
		if (oldest == null) return null;
		if (notice.outcome() == Outcome.BREAKOUT) {
			return now - notice.observedAt() >= BREAKOUT_FALLBACK_MILLIS ? oldest : null;
		}
		// A disappeared target is direct catch evidence and beats throw order. If every
		// candidate remains visible, wait long enough to avoid clearing the wrong peer.
		if (oldestAbsent != null) return oldestAbsent;
		return null;
	}

	private static void resolve(Attempt attempt, Outcome outcome, long now) {
		attempt.outcome = outcome;
		attempt.resolvedAt = now;
		Logical logical = logicals.get(attempt.logicalId);
		if (logical == null) return;
		if (outcome == Outcome.CAUGHT) {
			logical.activeAttempt = 0L;
			logical.life = Life.CAUGHT;
			logical.caughtAt = now;
		} else {
			boolean confirmed = attempt.reappearanceConfirmed || logical.life == Life.BREAKOUT;
			logical.life = Life.BREAKOUT;
			// Keep the attempt as the reappearance anchor when the server result wins
			// the race. A later body can still inherit this logical identity and pity.
			if (confirmed) logical.activeAttempt = 0L;
		}
		DebugLog.line("IDENTITY", "RESOLVE A" + attempt.id + " " + outcome
			+ " logical=L" + logical.id + " entity=" + shortId(logical.entityId));
		RecatchSpots.onIdentityResolved(attempt.id, logical.critter, attempt.targetEntityId,
			logical.entityId, logical.box, logical.sparkling, logical.pity, outcome, logical.visible);
		StillCritters.onIdentityResolved(attempt.id, logical.critter, attempt.targetEntityId,
			logical.entityId, logical.box, outcome);
	}

	/** Retires remaining identity state once the run confirms the species exhausted. */
	public static void onConfirmedCatchTotal(Critter critter, int catches) {
		if (critter == null || catches < CritterSpawnRanges.maximum(critter)) return;
		exhaustedSpecies.add(critter);
		long now = System.currentTimeMillis();
		int retired = 0;
		for (Logical logical : logicals.values()) {
			if (!critter.equals(logical.critter) || logical.life == Life.CAUGHT) continue;
			logical.life = Life.CAUGHT;
			logical.caughtAt = now;
			logical.activeAttempt = 0L;
			retired++;
		}
		for (Attempt attempt : attempts.values()) {
			if (!critter.equals(attempt.critter) || attempt.outcome != Outcome.PENDING) continue;
			attempt.outcome = Outcome.CAUGHT;
			attempt.resolvedAt = now;
		}
		unmatchedOutcomes.remove(critter);
		if (retired > 0) {
			DebugLog.line("IDENTITY", "MAX-CATCH " + critter.name() + "=" + catches
				+ " retired=" + retired + " logicals");
		}
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
			RecatchSpots.onIdentityExpired(attempt.id, attempt.critter, attempt.targetEntityId);
			StillCritters.onIdentityExpired(attempt.id, attempt.critter, attempt.targetEntityId);
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
		resolve(attempt, Outcome.BREAKOUT, now);
		if (acknowledgement == null) {
			earlyBreakoutAcks.computeIfAbsent(logical.critter, ignored -> new ArrayDeque<>()).addLast(now);
			DebugLog.line("IDENTITY", "EARLY-BREAKOUT A" + attempt.id + " " + logical.critter.name()
				+ " (awaiting chat acknowledgement)");
		}
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
		Set<Long> activeLogicals = new HashSet<>();
		for (Logical logical : logicals.values()) {
			if (logical.life != Life.CAUGHT && logical.entityId != null && !entities.add(logical.entityId)) {
				invariant("entity:" + logical.entityId,
					"entity owned twice id=" + shortId(logical.entityId));
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
