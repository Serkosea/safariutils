package dev.serko.safariutils;

import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.session.SparklingStats;
import java.util.Set;

/** Exercises the actual ledger without a persistence path or Minecraft client. */
public final class SparklingImportRegression {
	public static int run() {
		var critter = Critters.byName("Woodchucker");
		SparklingStats.set(critter, 75);
		check(!SparklingStats.apiCollectionAddsInformation(Set.of("WOODCHUCKER"), 72), "stale API");
		SparklingStats.importApiCollection(Set.of("WOODCHUCKER"), 72);
		check(SparklingStats.importedDuplicates() == 74, "stale import cannot reduce local total");
		SparklingStats.importApiCollection(Set.of("WOODCHUCKER"), 80);
		SparklingStats.importApiCollection(Set.of("WOODCHUCKER"), 72);
		check(SparklingStats.importedDuplicates() == 80, "out-of-order import");
		check(!SparklingStats.apiCollectionAddsInformation(Set.of("WOODCHUCKER"), 80), "duplicate result");
		SparklingStats.recordSparkling(critter);
		check(SparklingStats.importedDuplicates() == 81, "catch advances aggregate");
		check(SparklingStats.apiCollectionAddsInformation(Set.of("HIDEONWALL"), 72), "missing unique");
		SparklingStats.importApiCollection(Set.of("HIDEONWALL"), 72);
		check(SparklingStats.count(Critters.byName("Hideonwall")) == 1, "unique added");
		check(SparklingStats.importedDuplicates() == 81, "unique import preserves aggregate");
		return 8;
	}
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
