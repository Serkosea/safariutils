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
	record Failure(Sample label, Sample closest, double distanceSq, double radius) { }
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
					failures.accept(new Failure(label.sample(), closest, distance, radius(label)));
				}
				return body;
			}, Sample::id);
		List<Match> result = new ArrayList<>(pairs.size());
		for (var pair : pairs) {
			Label label = pair.label();
			result.add(new Match(label.critter(), label.sample(), pair.body(), label.sparkling(), pair.ownershipConflict()));
		}
		return List.copyOf(result);
	}

	private static ScanPairing.Rank rank(Label label, Pools pools, UUID previous) {
		if (retained(label, pools, Set.of(), previous) != null) return new ScanPairing.Rank(0, 0);
		double nearest = radius(label) * radius(label);
		for (Sample candidate : pools.primary(label)) {
			if (CritterBodyTypes.accepts(label.critter(), candidate.type())) {
				nearest = Math.min(nearest, distance(candidate, label.sample()));
			}
		}
		return new ScanPairing.Rank(CritterBodyTypes.known(label.critter()) ? 1 : 2, nearest);
	}

	private static Sample resolve(Label label, Pools pools, Set<UUID> claimed, UUID previous) {
		Sample retained = retained(label, pools, claimed, previous);
		if (retained != null) return retained;
		if (label.critter().name().equals("Duplico")) {
			Sample interaction = nearest(label, pools.interactions.near(label.sample()), claimed, BODY_RADIUS);
			if (interaction != null) return interaction;
		}
		Sample body = nearest(label, pools.bodies.near(label.sample()), claimed, BODY_RADIUS);
		if (body == null && label.critter().name().equals("Gazer")) {
			body = nearest(label, pools.stands.near(label.sample()), claimed, GAZER_RADIUS);
		}
		return body;
	}

	private static Sample retained(Label label, Pools pools, Set<UUID> claimed, UUID previous) {
		if (previous == null || claimed.contains(previous)) return null;
		for (Sample candidate : pools.primary(label)) {
			if (candidate.id().equals(previous)) {
				return distance(candidate, label.sample()) < radius(label) * radius(label)
					&& CritterBodyTypes.accepts(label.critter(), candidate.type()) ? candidate : null;
			}
		}
		return null;
	}

	private static Sample nearest(Label label, List<Sample> candidates, Set<UUID> claimed, double radius) {
		Sample best = null;
		double bestDistance = radius * radius;
		for (Sample candidate : candidates) {
			if (claimed.contains(candidate.id()) || !CritterBodyTypes.accepts(label.critter(), candidate.type())) continue;
			double distance = distance(candidate, label.sample());
			if (distance < bestDistance) { best = candidate; bestDistance = distance; }
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
