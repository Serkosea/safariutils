package dev.serko.safariutils.data;

import java.util.Map;

/** Expected full-party population bounds for one Safari run. */
public final class CritterSpawnRanges {

	public record Range(int minimum, int maximum, boolean estimated) {
		public Range {
			if (minimum < 0 || maximum < minimum) throw new IllegalArgumentException("Invalid spawn range");
		}

		public boolean exact() {
			return minimum == maximum;
		}
	}

	private static final Map<String, Range> BY_NAME = Map.ofEntries(
		// Cavern
		entry("Cavernfish", 4, 8), entry("Flitter", 6, 8), entry("Shyworm", 4, 8),
		entry("Driftling", 3, 6), entry("Chuckwalla", 2, 4), estimated("Rockmite", 0, 12),
		entry("Scrappy", 3, 3), entry("Snoozle", 0, 5), entry("Gemzie", 3, 3),
		// Forest
		entry("Foxtrot", 6, 8), estimated("Bluebird", 0, 12), entry("Honeybug", 3, 6),
		entry("Treefrog", 3, 6), entry("Woodchucker", 3, 6), entry("Fluffling", 1, 3),
		entry("Hideonfloor", 1, 3), estimated("Parakeet", 0, 12), estimated("Macaw", 0, 16),
		// Haunted
		entry("Areita", 3, 6), entry("Bloodbat", 3, 6), entry("Duplico", 2, 4),
		entry("Gazer", 4, 4), entry("Litterbug", 4, 8), entry("Solsnatcher", 4, 8),
		estimated("Gimmiegold", 3, 10), entry("Hideonwall", 2, 4), entry("Hideyho", 1, 1),
		entry("Doomspiral", 1, 1),
		// Icy
		entry("Strongarm", 6, 8), entry("Tepid", 6, 8), entry("Polaris", 2, 4),
		entry("Shuddersquid", 3, 6), entry("Billygoat", 2, 4), entry("Mantis Shrimp", 3, 6),
		entry("Nozzlenose", 2, 4), entry("Troodon", 3, 3), entry("Wumpa", 1, 1)
	);

	private CritterSpawnRanges() { }

	public static Range forCritter(Critter critter) {
		return critter == null ? null : BY_NAME.get(critter.name());
	}

	public static int maximum(Critter critter) {
		Range range = forCritter(critter);
		return range == null ? Integer.MAX_VALUE : range.maximum();
	}

	private static Map.Entry<String, Range> entry(String name, int minimum, int maximum) {
		return Map.entry(name, new Range(minimum, maximum, false));
	}

	private static Map.Entry<String, Range> estimated(String name, int minimum, int maximum) {
		return Map.entry(name, new Range(minimum, maximum, true));
	}
}
