package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Exercises the complete production scanner without constructing a Minecraft world. */
public final class CritterScanRegression {
	public static int run() {
		int checks = 0;
		for (String label : List.of("Wumpa zZz", "Sparkling Wumpa zZz", "Wumpa", "Sparkling Wumpa")) {
			var sleeping = CritterScan.scan(List.of(sample(900, "armor_stand", label, 0),
				sample(901, "ravager", null, 0)), Map.of(), null).getFirst();
			check(sleeping.body() != null && sleeping.critter().name().equals("Wumpa")
				&& sleeping.sparkling() == label.startsWith("Sparkling "), "Wumpa sleeping/waking label lost"); checks++;
		}
		for (String label : List.of("Wumpa reward", "Doomspiral spawned!", "SparklingWumpa", "Player: Wumpa zZz")) {
			check(CritterScan.parseLabel(label) == null, "Unrelated label recognized"); checks++;
		}
		check(CritterScan.parseLabel("Doomspiral").critter().name().equals("Doomspiral"), "Spawn label missing"); checks++;
		var gazer = sample(1, "armor_stand", "Sparkling Gazer", 0);
		var wrong = sample(2, "bat", null, 0);
		var named = sample(3, "armor_stand", "Unrelated label", 0.1);
		var stand = sample(4, "armor_stand", null, 2.9);
		var matches = CritterScan.scan(List.of(gazer, wrong, named, stand), Map.of(), null);
		check(matches.size() == 1 && matches.getFirst().sparkling() && matches.getFirst().body().equals(stand), "Gazer selected unrelated body"); checks++;
		var failures = new ArrayList<CritterScan.Failure>();
		matches = CritterScan.scan(List.of(gazer, wrong, sample(4, "armor_stand", null, 3)), Map.of(), failures::add);
		check(matches.getFirst().body() == null && failures.size() == 1 && failures.getFirst().radius() == 3
			&& failures.getFirst().closest().equals(wrong), "Gazer radius/diagnostic changed"); checks++;
		var duplico = sample(5, "armor_stand", "Duplico", 0);
		var fish = sample(6, "silverfish", null, 0.1);
		var interaction = sample(7, "interaction", null, 3.9);
		check(CritterScan.scan(List.of(duplico, fish, interaction), Map.of(), null).getFirst().body().equals(interaction), "Duplico lost interaction preference"); checks++;
		check(CritterScan.scan(List.of(duplico, fish), Map.of(), null).getFirst().body().equals(fish), "Duplico lost fallback"); checks++;
		var hideyho = sample(8, "armor_stand", "Hideyho", 0);
		check(CritterScan.scan(List.of(hideyho, fish), Map.of(), null).getFirst().body().equals(hideyho), "Hideyho acquired neighbor"); checks++;
		var bat = sample(9, "armor_stand", "Bloodbat", -4);
		check(CritterScan.scan(List.of(bat, sample(10, "bat", null, 0)), Map.of(), null).getFirst().body() == null, "Outer radius became inclusive"); checks++;
		check(CritterScan.scan(List.of(bat, sample(10, "bat", null, -0.001)), Map.of(), null).getFirst().body() != null, "Negative cell boundary missed body"); checks++;
		check(CritterScan.scan(List.of(sample(11, "armor_stand", "Tepid Shard", 0)), Map.of(), null).isEmpty(), "Non-critter became label"); checks++;
		var first = sample(12, "bat", null, -3);
		var second = sample(13, "bat", null, -5);
		check(CritterScan.scan(List.of(bat, first, second), Map.of(), null).getFirst().body().equals(first), "Equal-distance tie lost source order"); checks++;
		check(CritterScan.scan(List.of(bat, first, second), Map.of(bat.id(), second.id()), null).getFirst().body().equals(second), "Retained ownership lost"); checks++;
		check(CritterScan.scan(List.of(bat, first), Map.of(bat.id(), second.id()), null).getFirst().body().equals(first), "Removed body retained"); checks++;

		// October 20: a Hideon capsule label acquired a nearby Duplico's carrier,
		// incorrectly turning a successful catch into a breakout and an orphaned pin.
		var capsuleLabel = new CritterScan.Sample(new UUID(12, 1), "armor_stand", "Hideonwall",
			-21.65625, 78.75, -56.65625);
		var dupLabel = new CritterScan.Sample(new UUID(12, 2), "armor_stand", "Duplico",
			-18.875, 78.65625, -59.15625);
		var dupBody = new CritterScan.Sample(new UUID(12, 3), "interaction", null,
			-18.875, 77.40625, -59.15625);
		var carrier = new CritterScan.Sample(new UUID(12, 4), "silverfish", null,
			-18.875, 77.40625, -59.15625);
		for (boolean reverse : List.of(false, true)) {
			for (boolean stalePair : List.of(false, true)) {
				var input = reverse ? List.of(carrier, capsuleLabel, dupBody, dupLabel)
					: List.of(dupLabel, dupBody, capsuleLabel, carrier);
				var prior = new HashMap<UUID, UUID>();
				prior.put(dupLabel.id(), dupBody.id());
				if (stalePair) prior.put(capsuleLabel.id(), carrier.id());
				var result = CritterScan.scan(input, prior, null);
				check(result.stream().filter(m -> m.label().equals(capsuleLabel))
					.allMatch(m -> m.body() == null), "capsule label stole Duplico carrier"); checks++;
				check(result.stream().filter(m -> m.label().equals(dupLabel))
					.allMatch(m -> dupBody.equals(m.body())), "Duplico lost canonical interaction"); checks++;
			}
		}
		var nearbyHideon = new CritterScan.Sample(new UUID(12, 5), "armor_stand", "Hideonfloor",
			carrier.x(), carrier.y() + .5, carrier.z());
		check(CritterScan.scan(List.of(dupLabel, dupBody, nearbyHideon, carrier), Map.of(), null)
			.stream().anyMatch(m -> m.label().equals(nearbyHideon) && carrier.equals(m.body())),
			"stronger independently labelled Hideon lost its body"); checks++;
		check(CritterScan.scan(List.of(dupLabel, carrier), Map.of(), null).getFirst().body().equals(carrier),
			"Duplico cannot switch to its carrier after interaction disappears"); checks++;
		failures.clear();
		CritterScan.scan(List.of(dupLabel, dupBody, capsuleLabel, carrier), Map.of(), failures::add);
		check(failures.stream().anyMatch(f -> f.label().equals(capsuleLabel) && f.companionBlocked()),
			"compound ownership rejection lacks diagnostics"); checks++;
		// Entity updates need not arrive together. Preserve the decision with a
		// carrier one interpolation step behind its moving interaction and label.
		for (int step = 0; step < 20; step++) {
			double x = carrier.x() + step * .1;
			var movingCarrier = new CritterScan.Sample(carrier.id(), carrier.type(), null,
				x, carrier.y(), carrier.z());
			var movingInteraction = new CritterScan.Sample(dupBody.id(), dupBody.type(), null,
				x + .4, carrier.y() + .2, carrier.z());
			var movingLabel = new CritterScan.Sample(dupLabel.id(), dupLabel.type(), dupLabel.name(),
				x + .4, carrier.y() + 1.25, carrier.z());
			check(CritterScan.scan(List.of(capsuleLabel, movingCarrier, movingLabel, movingInteraction),
				Map.of(dupLabel.id(), dupBody.id()), null).stream()
				.filter(m -> m.label().equals(capsuleLabel)).allMatch(m -> m.body() == null),
				"moving compound body became a false Hideon breakout"); checks++;
		}

		// October 28: a capsule's Duplico label borrowed the original body's
		// silverfish carrier while its interaction was already shrinking.
		var originalLabel = new CritterScan.Sample(new UUID(28, 1), "armor_stand", "Duplico", -12.4375, 71.25, -83.375);
		var originalBody = new CritterScan.Sample(new UUID(28, 2), "interaction", null, -12.4375, 70, -83.375);
		var originalCarrier = new CritterScan.Sample(new UUID(28, 3), "silverfish", null, -12.4375, 70, -83.375);
		var captureLabel = new CritterScan.Sample(new UUID(28, 4), "armor_stand", "Duplico", -9.625, 69.40625, -84);
		for (int seed = 0; seed < 24; seed++) {
			var batch = new ArrayList<>(List.of(originalLabel, originalBody, originalCarrier, captureLabel));
			java.util.Collections.shuffle(batch, new Random(seed));
			var result = CritterScan.scan(batch, Map.of(originalLabel.id(), originalBody.id(), captureLabel.id(), originalCarrier.id()), null);
			check(result.stream().anyMatch(m -> m.label().equals(captureLabel) && m.body() == null),
				"same-species capsule acquired compound carrier"); checks++;
			check(result.stream().anyMatch(m -> m.label().equals(originalLabel) && originalBody.equals(m.body())),
				"original Duplico lost interaction"); checks++;
		}
		check(CritterScan.scan(List.of(originalLabel, originalCarrier), Map.of(originalLabel.id(), originalBody.id()), null)
			.getFirst().body().equals(originalCarrier), "real Duplico carrier fallback lost"); checks++;

		// Dense same-species groups: every prior pair stays exclusive even after bodies cross.
		for (String name : List.of("Hideonfloor", "Hideonwall")) {
			var label = new CritterScan.Sample(new UUID(9, 1), "armor_stand", name, 0, 1.25, 0);
			var dormant = sample(90, "shulker", null, 0);
			var awake = sample(91, "silverfish", null, 0);
			check(CritterScan.scan(List.of(label, dormant), Map.of(), null).getFirst().body().equals(dormant),
				"concealed Hideon lost pairing"); checks++;
			check(CritterScan.scan(List.of(label, awake), Map.of(label.id(), dormant.id()), null).getFirst().body().equals(awake),
				"awakened Hideon became a bodyless capsule label"); checks++;
			check(CritterScan.scan(List.of(label, dormant), Map.of(label.id(), awake.id()), null).getFirst().body().equals(dormant),
				"concealed form cannot return"); checks++;
			var extraLabel = new CritterScan.Sample(new UUID(9, 2), "armor_stand", name, 0, .5, 0);
			for (boolean reverse : new boolean[] {false, true}) {
				var compound = CritterScan.scan(reverse ? List.of(extraLabel, awake, label, dormant)
					: List.of(label, dormant, extraLabel, awake), Map.of(extraLabel.id(), awake.id()), null);
				check(compound.size() == 1 && compound.getFirst().body().equals(dormant),
					"overlapping Hideon forms became two critters"); checks++;
			}
			var separate = new CritterScan.Sample(awake.id(), "silverfish", null, .25, 0, 0);
			var offset = new CritterScan.Sample(awake.id(), "silverfish", null, .46875, 0, -.4375);
			var offsetScan = CritterScan.scan(List.of(label, dormant, extraLabel, offset), Map.of(), null);
			check(offsetScan.size() == 1 && offsetScan.getFirst().body().equals(dormant),
				"fresh offset breakout carrier split the shell identity"); checks++;
			check(CritterScan.scan(List.of(label, dormant, extraLabel, offset),
				Map.of(offsetScan.getFirst().label().id(), dormant.id()), null).size() == 1,
				"canonical offset shell split on the next scan"); checks++;
			var otherLabel = new CritterScan.Sample(new UUID(9, 3), "armor_stand", name, -.3, .5, 0);
			var otherCarrier = sample(92, "silverfish", null, -.3);
			check(CritterScan.scan(List.of(label, dormant, extraLabel, offset, otherLabel, otherCarrier),
				Map.of(label.id(), dormant.id()), null).size() == 3,
				"ambiguous fresh carriers must not merge with a shell"); checks++;
			check(CritterScan.scan(List.of(label, dormant, extraLabel, separate),
				Map.of(extraLabel.id(), separate.id()), null).size() == 2,
				"nearby separate mobile Hideon was removed"); checks++;
		}

		Random random = new Random(20261014);
		for (int iteration = 0; iteration < 1000; iteration++) {
			var samples = new ArrayList<CritterScan.Sample>();
			var previous = new HashMap<UUID, UUID>();
			int count = 2 + random.nextInt(12);
			for (int i = 0; i < count; i++) {
				var label = sample(100 + i, "armor_stand", i % 2 == 0 ? "Bloodbat" : "Sparkling Bloodbat", random.nextDouble());
				var body = sample(200 + i, "bat", null, random.nextDouble());
				samples.add(label); samples.add(body); previous.put(label.id(), body.id());
			}
			matches = CritterScan.scan(samples, previous, null);
			check(matches.size() == count, "Label lost in dense group"); checks++;
			var owned = new HashSet<UUID>();
			for (int i = 0; i < count; i++) {
				var match = matches.get(i);
				check(match.label().equals(samples.get(i * 2)) && match.body().id().equals(previous.get(match.label().id()))
					&& owned.add(match.body().id()) && !match.conflict(), "Dense retained ownership/output order changed"); checks++;
			}
			var fresh = CritterScan.scan(samples, Map.of(), null);
			owned.clear();
			for (var match : fresh) {
				check(match.body() != null && owned.add(match.body().id()) && !match.conflict(), "Fresh scan shared a body"); checks++;
			}
		}
		return checks;
	}
	private static CritterScan.Sample sample(long id, String type, String name, double x) {
		return new CritterScan.Sample(new UUID(0, id), type, name, x, 0, 0);
	}
	private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
	private CritterScanRegression() { }
}
