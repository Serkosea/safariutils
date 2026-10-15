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

		// Dense same-species groups: every prior pair stays exclusive even after bodies cross.
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
