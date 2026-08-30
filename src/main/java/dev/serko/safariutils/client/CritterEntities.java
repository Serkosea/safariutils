package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Scans loaded critter labels once and shares the paired bodies with every tracker. */
public final class CritterEntities {

	/** Spawns do not appear fast enough to be worth scanning every tick. */
	private static final int SCAN_INTERVAL_TICKS = 5;
	/**
	 * Maximum label-to-body distance. Doomspiral and Mantis Shrimp can exceed the old
	 * three-block limit because of their size and movement.
	 */
	private static final double LABEL_TO_MOB_RADIUS = 4.0;
	/** The word a rare variant is assumed to carry in its name. */
	private static final String SPARKLING = "Sparkling";

	private static List<Sighting> sightings = List.of();
	private static long scannedAt;
	private static int ticks;

	/** A critter label and its paired body. The body may be missing for unusual mobs. */
	public record Sighting(Critter critter, Entity label, Entity mob, boolean sparkling) {
		/** The entity to point at: the mob if there is one, otherwise the label itself. */
		public Entity body() {
			return mob != null ? mob : label;
		}
	}

	/** A named label waiting to be paired with its body. */
	private record Label(Critter critter, Entity entity, boolean sparkling) {
	}

	private CritterEntities() {
	}

	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		Minecraft client = Minecraft.getInstance();
		scannedAt = System.currentTimeMillis();
		if (client.level == null || !SafariLocation.inSafari()) {
			logDiff(List.of());
			sightings = List.of();
			ballCandidates.clear();
			ballCandidatePositions.clear();
			return;
		}
		List<Sighting> result = scan(client);
		logDiff(result);
		checkBallCandidates(client, result);
		sightings = result;
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
		for (Entity entity : client.level.entitiesForRendering()) {
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
						for (Entity candidate : client.level.entitiesForRendering()) {
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
		return sightings;
	}

	/**
	 * When the list was last rebuilt.
	 *
	 * <p>Between sweeps {@link #all()} returns the previous answer, so anything keeping
	 * its own timestamps has to key off this rather than off the tick it read them on.
	 */
	public static long scannedAt() {
		return scannedAt;
	}

	private static List<Sighting> scan(Minecraft client) {
		List<Label> labels = new ArrayList<>();
		List<Entity> candidates = new ArrayList<>();
		List<Entity> interactions = new ArrayList<>();
		// Gazer is the only critter whose body is an unnamed armour stand.
		List<Entity> unnamedArmorStands = new ArrayList<>();

		for (Entity entity : client.level.entitiesForRendering()) {
			// The name identifies a label, not the entity type: most are armour stands
			// but a Hideyho arrives as a player.
			String name = entity.hasCustomName()
				? SafariLocation.strip(entity.getCustomName().getString()) : null;
			Critter named = name == null ? null : Critters.byName(name);
			boolean rare = false;

			// A sparkling one is assumed to be named for it. Only this one prefix is
			// tolerated, rather than searching the label for any species name: an armour
			// stand reading "Tepid Shard" must not count as a Tepid.
			if (named == null && name != null && startsWithSparkling(name)) {
				named = Critters.byName(name.substring(SPARKLING.length()).trim());
				rare = named != null;
			}

			if (named != null) {
				labels.add(new Label(named, entity, rare));
			} else if (EntityTypeIds.is(entity, "interaction")) {
				interactions.add(entity);
			} else if (isMobLike(entity)) {
				candidates.add(entity);
			} else if (EntityTypeIds.is(entity, "armor_stand") && !entity.hasCustomName()) {
				unnamedArmorStands.add(entity);
			}
		}

		List<Sighting> result = new ArrayList<>(labels.size());
		for (Label label : labels) {
			result.add(new Sighting(label.critter(), label.entity(),
				nearest(candidates, interactions, label.entity(), label.critter(), unnamedArmorStands),
				label.sparkling()));
		}
		return result;
	}

	private static boolean startsWithSparkling(String name) {
		return name.length() > SPARKLING.length()
			&& name.regionMatches(true, 0, SPARKLING, 0, SPARKLING.length());
	}

	/** Excludes the scaffolding entities Hypixel builds its props out of. */
	private static boolean isMobLike(Entity entity) {
		EntityType<?> type = entity.getType();
		return !EntityTypeIds.is(type, "armor_stand")
			&& !EntityTypeIds.is(type, "interaction")
			&& !EntityTypeIds.is(type, "item_display")
			&& !EntityTypeIds.is(type, "block_display")
			&& !EntityTypeIds.is(type, "text_display")
			&& !EntityTypeIds.is(type, "player")
			&& !EntityTypeIds.is(type, "item")
			// Props and projectiles near a label are never its critter body.
			&& !EntityTypeIds.is(type, "painting")
			&& !EntityTypeIds.is(type, "item_frame")
			&& !EntityTypeIds.is(type, "glow_item_frame")
			&& !EntityTypeIds.is(type, "leash_knot")
			&& !EntityTypeIds.is(type, "experience_orb")
			&& !EntityTypeIds.is(type, "end_crystal")
			&& !EntityTypeIds.is(type, "falling_block")
			&& !EntityTypeIds.is(type, "lightning_bolt")
			&& !EntityTypeIds.is(type, "marker")
			&& !EntityTypeIds.is(type, "arrow")
			&& !EntityTypeIds.is(type, "spectral_arrow")
			&& !EntityTypeIds.is(type, "trident")
			&& !EntityTypeIds.is(type, "snowball")
			&& !EntityTypeIds.is(type, "egg")
			&& !EntityTypeIds.is(type, "ender_pearl")
			&& !EntityTypeIds.is(type, "firework_rocket")
			&& !EntityTypeIds.is(type, "tnt")
			&& !EntityTypeIds.is(type, "fishing_bobber");
	}

	/** Last time a given label's failed pairing was logged, so it is not re-logged every scan. */
	private static final Map<java.util.UUID, Long> lastPairFailureLogged = new java.util.HashMap<>();
	/** How often the same still-unpaired label is worth logging again. */
	private static final long PAIR_FAILURE_LOG_INTERVAL_MILLIS = 5_000;

	/** How close Gazer's own body (an unnamed armour stand) sits to its label — confirmed consistently ~2 blocks. */
	private static final double GAZER_BODY_RADIUS = 3.0;

	/** Returns the nearest qualifying body and logs throttled pairing diagnostics. */
	private static Entity nearest(List<Entity> candidates, List<Entity> interactions,
								   Entity label, Critter critter,
								   List<Entity> unnamedArmorStands) {
		Entity best = null;
		double bestSq = LABEL_TO_MOB_RADIUS * LABEL_TO_MOB_RADIUS;

		Entity closestAnyDistance = null;
		double closestAnyDistanceSq = Double.MAX_VALUE;
		boolean diagnostics = DebugLog.isEnabled();

		// Duplico's persistent body is an interaction entity; its armor stand is only
		// the name label. Resolve that body independently so an unrelated nearby mob
		// can never win merely by being a little closer to the label.
		if ("Duplico".equals(critter.name())) {
			Entity interactionBest = null;
			double interactionBestSq = LABEL_TO_MOB_RADIUS * LABEL_TO_MOB_RADIUS;
			for (Entity interaction : interactions) {
				double distanceSq = interaction.position().distanceToSqr(label.position());
				if (distanceSq >= interactionBestSq) continue;
				interactionBestSq = distanceSq;
				interactionBest = interaction;
			}
			if (interactionBest != null) return interactionBest;
		}

		for (Entity candidate : candidates) {
			double distanceSq = candidate.position().distanceToSqr(label.position());
			if (diagnostics && distanceSq < closestAnyDistanceSq) {
				closestAnyDistanceSq = distanceSq;
				closestAnyDistance = candidate;
			}
			if (distanceSq >= bestSq) continue;
			bestSq = distanceSq;
			best = candidate;
		}

		// Gazer's body is an unnamed armour stand about two blocks from its label.
		// Keep this fallback narrow because other armour stands are usually labels.
		if (best == null && "Gazer".equals(critter.name())) {
			double gazerBestSq = GAZER_BODY_RADIUS * GAZER_BODY_RADIUS;
			for (Entity candidate : unnamedArmorStands) {
				double distanceSq = candidate.position().distanceToSqr(label.position());
				if (distanceSq >= gazerBestSq) continue;
				gazerBestSq = distanceSq;
				best = candidate;
			}
		}

		if (best == null && diagnostics) {
			long now = System.currentTimeMillis();
			java.util.UUID labelId = label.getUUID();
			Long lastLogged = lastPairFailureLogged.get(labelId);
			if (lastLogged == null || now - lastLogged >= PAIR_FAILURE_LOG_INTERVAL_MILLIS) {
				lastPairFailureLogged.put(labelId, now);
				String labelName = label.hasCustomName() ? label.getCustomName().getString() : "?";
				if (closestAnyDistance == null) {
					DebugLog.line("PAIR", "\"" + labelName + "\" no candidates in entitiesForRendering() at all");
				} else {
					DebugLog.line("PAIR", "\"" + labelName + "\" closest candidate is " + closestAnyDistance.getType()
						+ " at " + String.format("%.2f", Math.sqrt(closestAnyDistanceSq))
						+ " blocks (radius is " + LABEL_TO_MOB_RADIUS + ")");
				}
			}
		}

		return best;
	}
}
