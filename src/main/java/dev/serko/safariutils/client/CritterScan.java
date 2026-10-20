package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Full label/body selection over one immutable world observation batch. No game/client access. */
final class CritterScan {
	static final double BODY_RADIUS = 4.0;
	static final double GAZER_RADIUS = 3.0;
	private static final String SPARKLING = "Sparkling";
	record Sample(UUID id, String type, String name, double x, double y, double z) { }
	record Match(Critter critter, Sample label, Sample body, boolean sparkling, boolean conflict) { }
	record Failure(Sample label, Sample closest, double distanceSq, double radius, boolean companionBlocked) { }
	private record Label(Critter critter, Sample sample, boolean sparkling) { }

	static List<Match> scan(List<Sample> samples, Map<UUID, UUID> previous, Consumer<Failure> failures) {
		List<Label> labels = new ArrayList<>();
		List<Sample> bodies = new ArrayList<>(), interactions = new ArrayList<>(), stands = new ArrayList<>();
		for (Sample sample : samples) {
			String name = sample.name();
			Critter critter = name == null ? null : Critters.byName(name);
			boolean sparkling = false;
			if (critter == null && name != null && name.length() > SPARKLING.length()
				&& name.regionMatches(true, 0, SPARKLING, 0, SPARKLING.length())) {
				critter = Critters.byName(name.substring(SPARKLING.length()).trim());
				sparkling = critter != null;
			}
			if (critter != null) labels.add(new Label(critter, sample, sparkling));
			else if (sample.type().equals("interaction")) interactions.add(sample);
			else if (CritterBodyTypes.generic(sample.type())) bodies.add(sample);
			else if (sample.type().equals("armor_stand") && name == null) stands.add(sample);
		}
		Pools pools = new Pools(bodies, interactions, stands);
		// A Duplico's interaction and silverfish carrier represent one critter.
		// Reserve compound-body evidence before generic labels (including capsule
		// labels) compete for it. Otherwise one physical critter can acquire two species.
		var duplicos = labels.stream().filter(label -> label.critter().name().equals("Duplico")).toList();
		for (var pair : ScanPairing.assign(duplicos,
				label -> rank(label, pools, previous.get(label.sample().id())),
				(label, claimed) -> resolve(label, pools, claimed, previous.get(label.sample().id())),
				Sample::id)) {
			if (pair.body() == null || !pair.body().type().equals("interaction")) continue;
			for (Sample carrier : pools.bodies.near(pair.body())) {
				if (!carrier.type().equals("silverfish") || distance(carrier, pair.body()) > 2.25) continue;
				pools.companions.computeIfAbsent(carrier.id(), ignored -> new ArrayList<>()).add(pair.label());
			}
		}
		List<ScanPairing.Pair<Label, Sample>> pairs = ScanPairing.assign(labels,
			label -> rank(label, pools, previous.get(label.sample().id())),
			(label, claimed) -> {
				if (label.critter().name().equals("Hideyho")) return label.sample();
				Sample body = resolve(label, pools, claimed, previous.get(label.sample().id()));
				if (body == null && failures != null) {
					Sample closest = null;
					double distance = Double.MAX_VALUE;
					for (Sample candidate : bodies) {
						if (claimed.contains(candidate.id())) continue;
						double candidateDistance = distance(candidate, label.sample());
						if (candidateDistance < distance) { closest = candidate; distance = candidateDistance; }
					}
					failures.accept(new Failure(label.sample(), closest, distance, radius(label),
						closest != null && CritterBodyTypes.accepts(label.critter(), closest.type())
							&& !pools.eligible(label, closest)));
				}
				return body;
			}, Sample::id);
		List<Match> result = new ArrayList<>(pairs.size());
		for (var pair : pairs) {
			Label label = pair.label();
			result.add(new Match(label.critter(), label.sample(), pair.body(), label.sparkling(), pair.ownershipConflict()));
		}
		collapseConcealedCarriers(result, Set.copyOf(previous.values()));
		return List.copyOf(result);
	}

	/**
	 * A Hideon may expose both its shulker and mobile carrier with separate labels
	 * during a form transition. Publish one canonical body before identity/rendering
	 * consumers see the batch. Exact co-location or an unclaimed carrier inside one
	 * shell footprint is compound evidence; retained mobile peers remain independent.
	 */
	private static void collapseConcealedCarriers(List<Match> matches, Set<UUID> priorBodies) {
		for (int index = matches.size() - 1; index >= 0; index--) {
			Match carrier = matches.get(index);
			if (carrier.body() == null || !carrier.body().type().equals("silverfish")
				|| !(carrier.critter().name().equals("Hideonwall") || carrier.critter().name().equals("Hideonfloor"))) continue;
			for (int other = 0; other < matches.size(); other++) {
				Match shell = matches.get(other);
				if (!shell.critter().equals(carrier.critter()) || shell.body() == null
					|| !shell.body().type().equals("shulker")
					|| !compoundForms(shell, carrier, priorBodies)) continue;
				// Offset fresh forms must be mutually unique; never choose between peers.
				if (distance(shell.body(), carrier.body()) > 1.0 / (64 * 64)) {
					int shells = 0, carriers = 0;
					for (Match candidate : matches) {
						if (compoundForms(candidate, carrier, priorBodies)) shells++;
						if (compoundForms(shell, candidate, priorBodies)) carriers++;
					}
					if (shells != 1 || carriers != 1) continue;
				}
				if (carrier.sparkling() && !shell.sparkling()) {
					matches.set(other, new Match(shell.critter(), shell.label(), shell.body(), true, shell.conflict()));
				}
				matches.remove(index);
				break;
			}
		}
	}

	private static boolean compoundForms(Match shell, Match carrier, Set<UUID> priorBodies) {
		if (shell.body() == null || carrier.body() == null || !shell.critter().equals(carrier.critter())
			|| !shell.body().type().equals("shulker") || !carrier.body().type().equals("silverfish")) return false;
		if (distance(shell.body(), carrier.body()) <= 1.0 / (64 * 64)) return true;
		// A breakout's new shell snaps to its block while its new carrier can retain
		// the pre-capture offset inside that footprint. Existing mobile peers stay distinct.
		return !priorBodies.contains(carrier.body().id())
			&& Math.abs(shell.body().y() - carrier.body().y()) <= 1.0 / 64
			&& Math.abs(shell.body().x() - carrier.body().x()) < .5
			&& Math.abs(shell.body().z() - carrier.body().z()) < .5;
	}

	private static ScanPairing.Rank rank(Label label, Pools pools, UUID previous) {
		if (retained(label, pools, Set.of(), previous) != null) return new ScanPairing.Rank(0, 0);
		double nearest = radius(label) * radius(label);
		for (Sample candidate : pools.primary(label)) {
			if (pools.eligible(label, candidate)) {
				nearest = Math.min(nearest, distance(candidate, label.sample()));
			}
		}
		return new ScanPairing.Rank(CritterBodyTypes.known(label.critter()) ? 1 : 2, nearest);
	}

	private static Sample resolve(Label label, Pools pools, Set<UUID> claimed, UUID previous) {
		Sample concealed = concealedHideon(label, pools, claimed);
		if (concealed != null) return concealed;
		Sample retained = retained(label, pools, claimed, previous);
		if (retained != null) return retained;
		if (label.critter().name().equals("Duplico")) {
			Sample interaction = nearest(label, pools, pools.interactions.near(label.sample()), claimed, BODY_RADIUS);
			if (interaction != null) return interaction;
		}
		Sample body = nearest(label, pools, pools.bodies.near(label.sample()), claimed, BODY_RADIUS);
		if (body == null && label.critter().name().equals("Gazer")) {
			body = nearest(label, pools, pools.stands.near(label.sample()), claimed, GAZER_RADIUS);
		}
		return body;
	}

	private static Sample retained(Label label, Pools pools, Set<UUID> claimed, UUID previous) {
		if (previous == null || claimed.contains(previous)) return null;
		Sample concealed = concealedHideon(label, pools, claimed);
		if (concealed != null && !concealed.id().equals(previous)) return null;
		for (Sample candidate : pools.primary(label)) {
			if (candidate.id().equals(previous)) {
				return distance(candidate, label.sample()) < radius(label) * radius(label)
					&& pools.eligible(label, candidate) ? candidate : null;
			}
		}
		return null;
	}

	private static Sample nearest(Label label, Pools pools, List<Sample> candidates, Set<UUID> claimed, double radius) {
		Sample best = null;
		double bestDistance = radius * radius;
		for (Sample candidate : candidates) {
			if (claimed.contains(candidate.id()) || !pools.eligible(label, candidate)) continue;
			double distance = distance(candidate, label.sample());
			if (distance < bestDistance) { best = candidate; bestDistance = distance; }
		}
		return best;
	}
	/** A directly aligned concealed body wins over its overlapping mobile carrier. */
	private static Sample concealedHideon(Label label, Pools pools, Set<UUID> claimed) {
		if (!label.critter().name().equals("Hideonfloor") && !label.critter().name().equals("Hideonwall")) return null;
		Sample best = null;
		double bestDistance = BODY_RADIUS * BODY_RADIUS;
		for (Sample body : pools.bodies.near(label.sample())) {
			if (!body.type().equals("shulker") || claimed.contains(body.id())) continue;
			double dx = body.x() - label.sample().x(), dz = body.z() - label.sample().z();
			if (dx * dx + dz * dz > 0.25) continue;
			double distance = distance(body, label.sample());
			if (distance < bestDistance) { best = body; bestDistance = distance; }
		}
		return best;
	}
	private static double radius(Label label) { return label.critter().name().equals("Gazer") ? GAZER_RADIUS : BODY_RADIUS; }
	private static double distance(Sample a, Sample b) {
		double x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
		return x * x + y * y + z * z;
	}
	private static final class Grid {
		private final NearbyIndex<Sample> index;
		Grid(List<Sample> values) { index = new NearbyIndex<>(values, BODY_RADIUS, Sample::x, Sample::y, Sample::z); }
		List<Sample> near(Sample value) { return index.near(value.x(), value.y(), value.z()); }
	}
	private static final class Pools {
		private final Grid bodies, interactions, stands;
		private final Map<UUID, List<Label>> companions = new java.util.HashMap<>();
		boolean eligible(Label label, Sample body) {
			if (!CritterBodyTypes.accepts(label.critter(), body.type())) return false;
			var owners = companions.get(body.id());
			if (owners == null) return true;
			for (Label owner : owners) {
				// A closer independently labelled critter stays independent. Equal
				// evidence is ambiguous and must not fabricate a cross-species body.
				if (!owner.critter().equals(label.critter())
					&& distance(body, owner.sample()) <= distance(body, label.sample())) return false;
			}
			return true;
		}
		Pools(List<Sample> bodies, List<Sample> interactions, List<Sample> stands) {
			this.bodies = new Grid(bodies); this.interactions = new Grid(interactions); this.stands = new Grid(stands);
		}
		List<Sample> primary(Label label) {
			return switch (label.critter().name()) {
				case "Duplico" -> interactions.near(label.sample());
				case "Gazer" -> stands.near(label.sample());
				default -> bodies.near(label.sample());
			};
		}
	}
	private CritterScan() { }
}
