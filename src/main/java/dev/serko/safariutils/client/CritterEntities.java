package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
	/** Body types repeatedly verified in research logs; unlisted species retain generic pairing. */
	private static final Map<String, Set<String>> EXPECTED_BODY_TYPES = Map.ofEntries(
		Map.entry("Foxtrot", Set.of("fox")),
		Map.entry("Bluebird", Set.of("parrot")),
		Map.entry("Honeybug", Set.of("bee")),
		Map.entry("Treefrog", Set.of("frog")),
		Map.entry("Woodchucker", Set.of("creaking")),
		Map.entry("Fluffling", Set.of("panda")),
		Map.entry("Hideonfloor", Set.of("shulker", "silverfish")),
		Map.entry("Parakeet", Set.of("parrot")),
		Map.entry("Macaw", Set.of("parrot")),
		Map.entry("Cavernfish", Set.of("tropical_fish")),
		Map.entry("Flitter", Set.of("bat")),
		Map.entry("Shyworm", Set.of("slime", "zombie")),
		Map.entry("Driftling", Set.of("silverfish")),
		Map.entry("Chuckwalla", Set.of("silverfish")),
		Map.entry("Rockmite", Set.of("silverfish")),
		Map.entry("Scrappy", Set.of("armadillo")),
		Map.entry("Mantis Shrimp", Set.of("tropical_fish")),
		Map.entry("Nozzlenose", Set.of("dolphin")),
		Map.entry("Strongarm", Set.of("snow_golem")),
		Map.entry("Areita", Set.of("cave_spider")),
		Map.entry("Bloodbat", Set.of("bat")),
		Map.entry("Litterbug", Set.of("endermite")),
		Map.entry("Solsnatcher", Set.of("phantom")),
		Map.entry("Hideonwall", Set.of("shulker", "silverfish")),
		Map.entry("Duplico", Set.of("interaction", "silverfish")),
		Map.entry("Doomspiral", Set.of("warden"))
	);

	private static List<Sighting> sightings = List.of();
	private static Map<UUID, Critter> bodylessLabels = Map.of();
	/** Constant-time reverse lookup shared by render hooks for this scan snapshot. */
	private static Map<UUID, Sighting> sightingsByEntity = Map.of();
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
		if (!DebugLog.isEnabled()) {
			lastPairFailureLogged.clear();
		} else if (!lastPairFailureLogged.isEmpty()) {
			long cutoff = System.currentTimeMillis() - PAIR_FAILURE_LOG_INTERVAL_MILLIS * 2;
			lastPairFailureLogged.values().removeIf(time -> time < cutoff);
		}

		Minecraft client = Minecraft.getInstance();
		scannedAt = System.currentTimeMillis();
		if (client.level == null || !SafariLocation.inSafari()) {
			logDiff(List.of());
			sightings = List.of();
			bodylessLabels = Map.of();
			sightingsByEntity = Map.of();
			ballCandidates.clear();
			ballCandidatePositions.clear();
			return;
		}
		List<Sighting> result = scan(client);
		logDiff(result);
		checkBallCandidates(client, result);
		sightings = result;
		Map<UUID, Critter> nextBodylessLabels = new HashMap<>();
		Map<UUID, Sighting> nextSightingsByEntity = new HashMap<>(Math.max(16, result.size() * 2));
		for (Sighting sighting : result) {
			nextSightingsByEntity.put(sighting.label().getUUID(), sighting);
			if (sighting.mob() != null) nextSightingsByEntity.put(sighting.mob().getUUID(), sighting);
			if (sighting.mob() == null) {
				nextBodylessLabels.put(sighting.label().getUUID(), sighting.critter());
			}
		}
		bodylessLabels = Map.copyOf(nextBodylessLabels);
		sightingsByEntity = Map.copyOf(nextSightingsByEntity);
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
		// Gazer is the only critter whose body is an unnamed armor stand.
		List<Entity> unnamedArmorStands = new ArrayList<>();

		for (Entity entity : WorldEntities.current()) {
			// The name identifies a label, not the entity type: most are armor stands
			// but a Hideyho arrives as a player.
			String name = entity.hasCustomName()
				? SafariLocation.strip(entity.getCustomName().getString()) : null;
			Critter named = name == null ? null : Critters.byName(name);
			boolean rare = false;

			// A sparkling one is assumed to be named for it. Only this one prefix is
			// tolerated, rather than searching the label for any species name: an armor
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

		EntityGrid candidateGrid = new EntityGrid(candidates);
		EntityGrid interactionGrid = new EntityGrid(interactions);
		EntityGrid armorStandGrid = new EntityGrid(unnamedArmorStands);

		Map<UUID, UUID> previousBodies = new HashMap<>();
		for (Sighting sighting : previous) {
			if (sighting.mob() != null) {
				previousBodies.put(sighting.label().getUUID(), sighting.mob().getUUID());
			}
		}
		Set<UUID> claimedBodies = new java.util.HashSet<>();
		Map<UUID, Sighting> pairedByLabel = new HashMap<>();
		List<Label> pairingOrder = new ArrayList<>(labels);
		// Existing pairs stay stable. New labels then compete by actual proximity, so
		// a capsule label cannot steal a newly spawned nearby critter's body.
		Map<UUID, PairingRank> pairingRanks = new HashMap<>();
		for (Label label : pairingOrder) {
			pairingRanks.put(label.entity().getUUID(), pairingRank(label, candidateGrid,
				interactionGrid, armorStandGrid, previousBodies.get(label.entity().getUUID())));
		}
		pairingOrder.sort(Comparator.comparing(
			label -> pairingRanks.get(label.entity().getUUID())));
		for (Label label : pairingOrder) {
			Entity body = nearest(candidates, candidateGrid, interactionGrid,
				label.entity(), label.critter(), armorStandGrid, claimedBodies,
				previousBodies.get(label.entity().getUUID()));
			if (body != null) claimedBodies.add(body.getUUID());
			pairedByLabel.put(label.entity().getUUID(),
				new Sighting(label.critter(), label.entity(), body, label.sparkling()));
		}

		List<Sighting> result = new ArrayList<>(labels.size());
		for (Label label : labels) result.add(pairedByLabel.get(label.entity().getUUID()));
		return result;
	}

	private static int pairingPriority(Label label) {
		String name = label.critter().name();
		return EXPECTED_BODY_TYPES.containsKey(name) || "Duplico".equals(name) || "Gazer".equals(name) ? 0 : 1;
	}

	private static PairingRank pairingRank(Label label, EntityGrid candidateGrid,
			EntityGrid interactionGrid, EntityGrid armorStandGrid, UUID previousBodyId) {
		if (retainedBody(candidateGrid, interactionGrid, armorStandGrid, label.entity(),
			label.critter(), Set.of(), previousBodyId) != null) {
			return new PairingRank(0, 0.0);
		}
		List<Entity> pool;
		if ("Duplico".equals(label.critter().name())) pool = interactionGrid.near(label.entity());
		else if ("Gazer".equals(label.critter().name())) pool = armorStandGrid.near(label.entity());
		else pool = candidateGrid.near(label.entity());
		double nearestSq = LABEL_TO_MOB_RADIUS * LABEL_TO_MOB_RADIUS;
		for (Entity candidate : pool) {
			if (!isExpectedBody(label.critter(), candidate)) continue;
			nearestSq = Math.min(nearestSq, distanceSquared(candidate, label.entity()));
		}
		return new PairingRank(1 + pairingPriority(label), nearestSq);
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
			// Hypixel's floating Icy props use tiny happy ghasts near Nozzlenose labels.
			&& !EntityTypeIds.is(type, "happy_ghast")
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

	/** How close Gazer's own body (an unnamed armor stand) sits to its label — confirmed consistently ~2 blocks. */
	private static final double GAZER_BODY_RADIUS = 3.0;

	/** Returns the nearest qualifying body and logs throttled pairing diagnostics. */
	private static Entity nearest(List<Entity> candidates, EntityGrid candidateGrid,
					EntityGrid interactionGrid,
					Entity label, Critter critter, EntityGrid armorStandGrid,
					Set<UUID> claimedBodies, UUID previousBodyId) {
		Entity best = null;
		double bestSq = LABEL_TO_MOB_RADIUS * LABEL_TO_MOB_RADIUS;

		Entity closestAnyDistance = null;
		double closestAnyDistanceSq = Double.MAX_VALUE;
		boolean diagnostics = DebugLog.isEnabled();
		Entity retained = retainedBody(candidateGrid, interactionGrid, armorStandGrid,
			label, critter, claimedBodies, previousBodyId);
		if (retained != null) return retained;

		// Duplico's persistent body is an interaction entity; its armor stand is only
		// the name label. Resolve that body independently so an unrelated nearby mob
		// can never win merely by being a little closer to the label.
		if ("Duplico".equals(critter.name())) {
			Entity interactionBest = null;
			double interactionBestSq = LABEL_TO_MOB_RADIUS * LABEL_TO_MOB_RADIUS;
			for (Entity interaction : interactionGrid.near(label)) {
				if (claimedBodies.contains(interaction.getUUID())) continue;
				double distanceSq = distanceSquared(interaction, label);
				if (distanceSq >= interactionBestSq) continue;
				interactionBestSq = distanceSq;
				interactionBest = interaction;
			}
			if (interactionBest != null) return interactionBest;
		}

		List<Entity> nearbyCandidates = candidateGrid.near(label);
		for (Entity candidate : nearbyCandidates) {
			if (claimedBodies.contains(candidate.getUUID())) continue;
			double distanceSq = distanceSquared(candidate, label);
			if (diagnostics && distanceSq < closestAnyDistanceSq) {
				closestAnyDistanceSq = distanceSq;
				closestAnyDistance = candidate;
			}
			if (!isExpectedBody(critter, candidate)) continue;
			if (distanceSq >= bestSq) continue;
			bestSq = distanceSq;
			best = candidate;
		}
		// Debug diagnostics report the genuinely closest candidate even when it lies
		// outside the pairing grid's radius.
		if (diagnostics && best == null) {
			for (Entity candidate : candidates) {
				if (claimedBodies.contains(candidate.getUUID())) continue;
				double distanceSq = distanceSquared(candidate, label);
				if (distanceSq >= closestAnyDistanceSq) continue;
				closestAnyDistanceSq = distanceSq;
				closestAnyDistance = candidate;
			}
		}

		// Gazer's body is an unnamed armor stand about two blocks from its label.
		// Keep this fallback narrow because other armor stands are usually labels.
		if (best == null && "Gazer".equals(critter.name())) {
			double gazerBestSq = GAZER_BODY_RADIUS * GAZER_BODY_RADIUS;
			for (Entity candidate : armorStandGrid.near(label)) {
				if (claimedBodies.contains(candidate.getUUID())) continue;
				double distanceSq = distanceSquared(candidate, label);
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

	/** Keeps an established label/body relationship stable while both entities remain valid. */
	private static Entity retainedBody(EntityGrid candidateGrid, EntityGrid interactionGrid,
					EntityGrid armorStandGrid, Entity label, Critter critter,
					Set<UUID> claimedBodies, UUID previousBodyId) {
		if (previousBodyId == null || claimedBodies.contains(previousBodyId)) return null;
		List<Entity> pool;
		if ("Duplico".equals(critter.name())) pool = interactionGrid.near(label);
		else if ("Gazer".equals(critter.name())) pool = armorStandGrid.near(label);
		else pool = candidateGrid.near(label);
		double maxSq = ("Gazer".equals(critter.name()) ? GAZER_BODY_RADIUS : LABEL_TO_MOB_RADIUS);
		maxSq *= maxSq;
		for (Entity candidate : pool) {
			if (!candidate.getUUID().equals(previousBodyId)) continue;
			if (distanceSquared(candidate, label) >= maxSq) return null;
			if (!"Duplico".equals(critter.name()) && !"Gazer".equals(critter.name())
				&& !isExpectedBody(critter, candidate)) return null;
			return candidate;
		}
		return null;
	}

	/** Whether this entity is a positively identified body rather than a proximity guess. */
	static boolean isVerifiedBody(Critter critter, Entity candidate) {
		if ("Duplico".equals(critter.name())) {
			return EntityTypeIds.is(candidate, "interaction") || EntityTypeIds.is(candidate, "silverfish");
		}
		if ("Gazer".equals(critter.name())) return EntityTypeIds.is(candidate, "armor_stand");
		Set<String> expected = EXPECTED_BODY_TYPES.get(critter.name());
		return expected != null && expected.contains(EntityTypeIds.path(candidate));
	}

	/** The bodyless label's species, used to hide capsule nametags during capture. */
	static Critter bodylessLabelCritter(UUID labelId) {
		return bodylessLabels.get(labelId);
	}

	/** The current paired sighting represented by either this label or body id. */
	static Sighting sightingFor(UUID entityId) {
		return sightingsByEntity.get(entityId);
	}

	/** Whether this sighting is temporary capsule/capture scaffolding, not a live critter. */
	static boolean isCaptureScaffolding(Sighting sighting) {
		return sighting.mob() == null
			? RecatchSpots.captureInProgress(sighting.critter())
			: RecatchSpots.isCaptureArtifact(sighting.critter(), sighting.mob());
	}

	/** Prevents nearby wildlife and capture helpers from stealing verified critter labels. */
	private static boolean isExpectedBody(Critter critter, Entity candidate) {
		Set<String> expected = EXPECTED_BODY_TYPES.get(critter.name());
		return expected == null || expected.contains(EntityTypeIds.path(candidate));
	}

	private static double distanceSquared(Entity first, Entity second) {
		double dx = first.getX() - second.getX();
		double dy = first.getY() - second.getY();
		double dz = first.getZ() - second.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	/** Four-block buckets keep pairing local while retaining source-list tie ordering. */
	private static final class EntityGrid {
		private static final double CELL_SIZE = LABEL_TO_MOB_RADIUS;
		private final Map<Cell, List<IndexedEntity>> cells = new HashMap<>();

		private EntityGrid(List<Entity> entities) {
			for (int i = 0; i < entities.size(); i++) {
				Entity entity = entities.get(i);
				cells.computeIfAbsent(cell(entity), ignored -> new ArrayList<>())
					.add(new IndexedEntity(i, entity));
			}
		}

		private List<Entity> near(Entity anchor) {
			Cell centre = cell(anchor);
			List<IndexedEntity> found = new ArrayList<>();
			for (int x = centre.x() - 1; x <= centre.x() + 1; x++) {
				for (int y = centre.y() - 1; y <= centre.y() + 1; y++) {
					for (int z = centre.z() - 1; z <= centre.z() + 1; z++) {
						List<IndexedEntity> bucket = cells.get(new Cell(x, y, z));
						if (bucket != null) found.addAll(bucket);
					}
				}
			}
			found.sort(Comparator.comparingInt(IndexedEntity::index));
			List<Entity> result = new ArrayList<>(found.size());
			for (IndexedEntity indexed : found) result.add(indexed.entity());
			return result;
		}

		private static Cell cell(Entity entity) {
			return new Cell((int) Math.floor(entity.getX() / CELL_SIZE),
				(int) Math.floor(entity.getY() / CELL_SIZE),
				(int) Math.floor(entity.getZ() / CELL_SIZE));
		}
	}

	private record Cell(int x, int y, int z) { }
	private record IndexedEntity(int index, Entity entity) { }
	private record PairingRank(int priority, double distanceSq) implements Comparable<PairingRank> {
		@Override
		public int compareTo(PairingRank other) {
			int compared = Integer.compare(priority, other.priority);
			return compared != 0 ? compared : Double.compare(distanceSq, other.distanceSq);
		}
	}
}
