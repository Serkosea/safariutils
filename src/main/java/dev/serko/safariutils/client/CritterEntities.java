package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.state.MonotonicTime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scans loaded critter labels once and shares the paired bodies with every tracker. */
public final class CritterEntities {

	/** Spawns do not appear fast enough to be worth scanning every tick. */
	private static final int SCAN_INTERVAL_TICKS = 5;
	/** All indexes belong to the same completed scan; entity references remain live. */
	private record Observations(List<Sighting> sightings, Map<Critter, List<Sighting>> byCritter,
			Map<UUID, Critter> bodyless, Map<UUID, Sighting> byEntity) { }
	private static final Observations EMPTY = new Observations(List.of(), Map.of(), Map.of(), Map.of());
	private static final ScanPublication<Observations> observations = new ScanPublication<>(EMPTY);
	private static int ticks;

	/** A critter label and its paired body. The body may be missing for unusual mobs. */
	public record Sighting(Critter critter, Entity label, Entity mob, boolean sparkling) {
		/** The entity to point at: the mob if there is one, otherwise the label itself. */
		public Entity body() {
			return mob != null ? mob : label;
		}
	}

	private CritterEntities() {
	}

	public static void tick() {
		// Ordinary exploration does not need a full entity-pairing sweep every tick.
		// During an unresolved capture, however, disappearance and uniform shrink are
		// ownership evidence, so temporarily sample each tick to eliminate avoidable
		// presentation latency without imposing that cost for the rest of the run.
		int interval = CritterState.captureTrackingActive() ? 1 : SCAN_INTERVAL_TICKS;
		if (++ticks < interval) return;
		ticks = 0;
		if (!DebugLog.isEnabled()) {
			lastPairFailureLogged.clear();
		} else if (!lastPairFailureLogged.isEmpty()) {
			long cutoff = MonotonicTime.nowMillis() - PAIR_FAILURE_LOG_INTERVAL_MILLIS * 2;
			lastPairFailureLogged.values().removeIf(time -> time < cutoff);
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !SafariLocation.inSafari()) {
			logDiff(List.of());
			observations.publish(EMPTY);
			ballCandidates.clear();
			ballCandidatePositions.clear();
			return;
		}
		List<Sighting> result = scan();
		logDiff(result);
		checkBallCandidates(client, result);
		Map<Critter, List<Sighting>> grouped = new HashMap<>();
		for (Sighting sighting : result) {
			grouped.computeIfAbsent(sighting.critter(), ignored -> new ArrayList<>()).add(sighting);
		}
		grouped.replaceAll((critter, entries) -> List.copyOf(entries));
		Map<UUID, Critter> nextBodylessLabels = new HashMap<>();
		Map<UUID, Sighting> nextSightingsByEntity = new HashMap<>(Math.max(16, result.size() * 2));
		for (Sighting sighting : result) {
			nextSightingsByEntity.put(sighting.label().getUUID(), sighting);
			if (sighting.mob() != null) nextSightingsByEntity.put(sighting.mob().getUUID(), sighting);
			if (sighting.mob() == null) {
				nextBodylessLabels.put(sighting.label().getUUID(), sighting.critter());
			}
		}
		observations.publish(new Observations(List.copyOf(result), Map.copyOf(grouped),
			Map.copyOf(nextBodylessLabels), Map.copyOf(nextSightingsByEntity)));
	}

	/** Correlates debug-only capsule candidates with nearby reappearing critters. */
	private static void checkBallCandidates(Minecraft client, List<Sighting> currentSightings) {
		if (!DebugLog.isEnabled()) {
			ballCandidates.clear();
			ballCandidatePositions.clear();
			return;
		}
		if (ballCandidates.isEmpty()) return;

		java.util.Set<java.util.UUID> stillPresent = new java.util.HashSet<>();
		for (Entity entity : WorldEntities.current()) {
			if (ballCandidates.containsKey(entity.getUUID())) stillPresent.add(entity.getUUID());
		}

		java.util.Iterator<java.util.Map.Entry<java.util.UUID, String>> it = ballCandidates.entrySet().iterator();
		while (it.hasNext()) {
			java.util.Map.Entry<java.util.UUID, String> entry = it.next();
			java.util.UUID ballId = entry.getKey();
			if (stillPresent.contains(ballId)) continue;

			String critterName = entry.getValue();
			net.minecraft.core.BlockPos lastPos = ballCandidatePositions.get(ballId);
			DebugLog.line("BALL", critterName + " watched id=" + shortId(ballId)
				+ " disappeared, last seen at " + pos(lastPos));

			for (Sighting sighting : currentSightings) {
				if (!critterName.equals(sighting.critter().name())) continue;
				if (sighting.mob() == null) continue;
				double distSq = sighting.mob().blockPosition().distSqr(lastPos);
				DebugLog.line("BALL", critterName + " candidate reappearance: id="
					+ shortId(sighting.mob().getUUID()) + " at " + pos(sighting.mob().blockPosition())
					+ " (" + String.format("%.1f", Math.sqrt(distSq)) + " blocks from where the ball was)");
			}

			it.remove();
			ballCandidatePositions.remove(ballId);
		}
	}

	/** Logs label appearance and body re-pairing changes while output logging is enabled. */
	private static void logDiff(List<Sighting> next) {
		if (!DebugLog.isEnabled()) {
			previous = next;
			return;
		}

		java.util.Map<java.util.UUID, Sighting> prevByLabel = new java.util.HashMap<>();
		for (Sighting s : previous) prevByLabel.put(s.label().getUUID(), s);
		java.util.Set<java.util.UUID> nextLabelIds = new java.util.HashSet<>();

		for (Sighting s : next) {
			java.util.UUID labelId = s.label().getUUID();
			nextLabelIds.add(labelId);
			Sighting was = prevByLabel.get(labelId);

			if (was == null) {
				DebugLog.line("SIGHTING+", s.critter().name() + " label=" + shortId(labelId)
					+ " mob=" + shortId(mobId(s)) + mobInfo(s.mob()) + " pos=" + pos(s.body()));

				// Capture capsules keep the critter label but have no paired mob. Log
				// their player-relative position to help trace entity ID changes.
				if (mobId(s) == null) {
					Minecraft client = Minecraft.getInstance();
					if (client.player != null) {
						Vec3 toBall = s.body().position().subtract(client.player.position());
						String labelText = s.label().hasCustomName() ? s.label().getCustomName().getString() : "?";
						DebugLog.line("BALL", s.critter().name() + " label=" + shortId(labelId)
							+ " labelText=\"" + labelText + "\" pos=" + pos(s.body()) + " offsetFromPlayer="
							+ String.format("%.1f,%.1f,%.1f", toBall.x, toBall.y, toBall.z) + " distFromPlayer="
							+ String.format("%.1f", toBall.length()));
					}

					// Scan every nearby entity because the capsule may use a type that
					// normal critter pairing intentionally ignores.
					if (client.level != null) {
						double ballScanRadiusSq = 5.0 * 5.0;
						StringBuilder nearby = new StringBuilder();
						Entity likelyBall = null;
						double likelyBallDistSq = Double.MAX_VALUE;
						for (Entity candidate : WorldEntities.current()) {
							if (candidate == s.label()) continue;
							double distSq = candidate.position().distanceToSqr(s.body().position());
							if (distSq > ballScanRadiusSq) continue;
							if (!nearby.isEmpty()) nearby.append(", ");
							nearby.append(candidate.getType()).append('@')
								.append(String.format("%.1f", Math.sqrt(distSq)));

							// Item displays are the best capsule candidate seen so far.
							if (EntityTypeIds.is(candidate, "item_display") && distSq < likelyBallDistSq) {
								likelyBall = candidate;
								likelyBallDistSq = distSq;
							}
						}
						DebugLog.line("BALL", s.critter().name() + " label=" + shortId(labelId)
							+ " nearby=[" + (nearby.isEmpty() ? "nothing within 5 blocks" : nearby) + "]");

						if (likelyBall != null) {
							ballCandidates.put(likelyBall.getUUID(), s.critter().name());
							ballCandidatePositions.put(likelyBall.getUUID(), likelyBall.blockPosition());
							DebugLog.line("BALL", s.critter().name() + " label=" + shortId(labelId)
								+ " watching id=" + shortId(likelyBall.getUUID())
								+ " (nearest item_display) for when it disappears");
						}
					}
				}
				continue;
			}

			java.util.UUID wasMobId = mobId(was);
			java.util.UUID nowMobId = mobId(s);
			if (!java.util.Objects.equals(wasMobId, nowMobId)) {
				DebugLog.line("SIGHTING~", s.critter().name() + " label=" + shortId(labelId)
					+ " mob " + shortId(wasMobId) + " -> " + shortId(nowMobId) + mobInfo(s.mob())
					+ " pos=" + pos(s.body()));
			}
		}

		for (Sighting s : previous) {
			if (nextLabelIds.contains(s.label().getUUID())) continue;
			DebugLog.line("SIGHTING-", s.critter().name() + " label=" + shortId(s.label().getUUID())
				+ " lastMob=" + shortId(mobId(s)) + " lastPos=" + pos(s.body()));
		}

		previous = next;
	}

	/** Adds the paired mob's exact type and hitbox size to diagnostic output. */
	private static String mobInfo(Entity mob) {
		if (mob == null) return "";
		return " type=" + mob.getType() + " w=" + String.format("%.2f", mob.getBbWidth())
			+ " h=" + String.format("%.2f", mob.getBbHeight());
	}

	private static java.util.UUID mobId(Sighting sighting) {
		return sighting.mob() == null ? null : sighting.mob().getUUID();
	}

	private static String shortId(java.util.UUID id) {
		return id == null ? "none" : id.toString().substring(0, 8);
	}

	private static String pos(Entity entity) {
		var p = entity.blockPosition();
		return p.getX() + "," + p.getY() + "," + p.getZ();
	}

	private static String pos(net.minecraft.core.BlockPos p) {
		return p.getX() + "," + p.getY() + "," + p.getZ();
	}

	private static List<Sighting> previous = List.of();
	/** Likely capture capsules being watched for a nearby critter reappearance. */
	private static final java.util.Map<java.util.UUID, String> ballCandidates = new java.util.HashMap<>();
	private static final java.util.Map<java.util.UUID, net.minecraft.core.BlockPos> ballCandidatePositions
		= new java.util.HashMap<>();

	/** Every critter currently labelled in the world. Never null; empty outside the Safari. */
	public static List<Sighting> all() {
		return observations.current().value().sightings();
	}

	/** Current sightings of one species from the same immutable shared-scan snapshot. */
	public static List<Sighting> all(Critter critter) {
		return critter == null ? List.of() : observations.current().value().byCritter().getOrDefault(critter, List.of());
	}

	/** Immutable species index already constructed by the shared scan. */
	static Map<Critter, List<Sighting>> byCritter() {
		return observations.current().value().byCritter();
	}

	/** Identity of the completed scan, independent of wall-clock time and world resets. */
	public static long scanRevision() {
		return observations.current().revision();
	}

	/** Drops entity references immediately when the client changes worlds. */
	public static void onWorldChange() {
		observations.publish(EMPTY);
		previous = List.of();
		ticks = 0;
		ballCandidates.clear();
		ballCandidatePositions.clear();
		lastPairFailureLogged.clear();
	}

	private static List<Sighting> scan() {
		List<CritterScan.Sample> samples = new ArrayList<>();
		for (Entity entity : WorldEntities.current()) {
			String name = entity.hasCustomName() ? SafariLocation.strip(entity.getCustomName().getString()) : null;
			samples.add(new CritterScan.Sample(entity.getUUID(), EntityTypeIds.path(entity), name,
				entity.getX(), entity.getY(), entity.getZ()));
		}
		Map<UUID, UUID> previousBodies = new HashMap<>();
		for (Sighting sighting : previous) {
			if (sighting.mob() != null) previousBodies.put(sighting.label().getUUID(), sighting.mob().getUUID());
		}
		List<CritterScan.Match> matches = CritterScan.scan(samples, previousBodies,
			DebugLog.isEnabled() ? CritterEntities::logPairFailure : null);
		List<Sighting> result = new ArrayList<>(matches.size());
		for (var match : matches) {
			if (match.conflict()) {
				StructuredDiagnostics.decision("SCAN/PAIRING", "ownership-conflict",
					"label=" + shortId(match.label().id()) + " species=" + match.critter().name());
			}
			result.add(new Sighting(match.critter(), WorldEntities.find(match.label().id()),
				match.body() == null ? null : WorldEntities.find(match.body().id()), match.sparkling()));
		}
		return result;
	}

	private static final Map<UUID, Long> lastPairFailureLogged = new HashMap<>();
	private static final long PAIR_FAILURE_LOG_INTERVAL_MILLIS = 5_000;

	private static void logPairFailure(CritterScan.Failure failure) {
		long now = MonotonicTime.nowMillis();
		Long last = lastPairFailureLogged.get(failure.label().id());
		if (last != null && now - last < PAIR_FAILURE_LOG_INTERVAL_MILLIS) return;
		lastPairFailureLogged.put(failure.label().id(), now);
		DebugLog.line("PAIR", "\"" + failure.label().name() + "\" "
			+ (failure.closest() == null ? "no generic body candidates"
				: "closest generic candidate is " + failure.closest().type() + " at "
					+ String.format("%.2f", Math.sqrt(failure.distanceSq())) + " blocks")
			+ " (radius is " + failure.radius() + ")"
			+ (failure.companionBlocked() ? " rejected=other-label-compound-body" : ""));
	}

	/** Whether this entity is a positively identified body rather than a proximity guess. */
	static boolean isVerifiedBody(Critter critter, Entity candidate) {
		return verifiedBodyType(critter, EntityTypeIds.path(candidate));
	}

	static boolean verifiedBodyType(Critter critter, String type) { return CritterBodyTypes.verified(critter, type); }
	static boolean acceptsBodyType(Critter critter, String type) { return CritterBodyTypes.accepts(critter, type); }
	static boolean isMobBodyType(String type) { return CritterBodyTypes.generic(type); }

	/** The bodyless label's species, used to hide capsule nametags during capture. */
	static Critter bodylessLabelCritter(UUID labelId) {
		return observations.current().value().bodyless().get(labelId);
	}

	/** The current paired sighting represented by either this label or body id. */
	static Sighting sightingFor(UUID entityId) {
		return observations.current().value().byEntity().get(entityId);
	}

	/** Whether this sighting is temporary capsule/capture scaffolding, not a live critter. */
	static boolean isCaptureScaffolding(Sighting sighting) {
		return sighting.mob() == null
			? RecatchSpots.captureInProgress(sighting.critter())
			: RecatchSpots.isCaptureArtifact(sighting.critter(), sighting.mob());
	}

}
