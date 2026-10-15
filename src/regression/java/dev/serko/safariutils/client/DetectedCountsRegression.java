package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.SafariBiome;
import java.util.HashMap;
import java.util.Map;

public final class DetectedCountsRegression {
	public static int run() {
		var critter = new Critter("Test", SafariBiome.values()[0], Critter.Rarity.COMMON, 3);
		var detected = new HashMap<Critter, Integer>();
		detected.put(critter, 2);
		var value = new DetectedCritters.Counts(7, 11, detected, Map.of(critter, 1));
		detected.clear();
		check(value.detected().get(critter) == 2 && value.visible().get(critter) == 1,
			"published detection and visible counts are independent frozen inputs");
		try { value.detected().clear(); throw new AssertionError("mutable publication"); }
		catch (UnsupportedOperationException expected) { }
		var publication = new ScanPublication<>(value);
		var held = publication.current();
		publication.publish(new DetectedCritters.Counts(7, 12, Map.of(critter, 2), Map.of()));
		check(publication.current().revision() != held.revision()
			&& publication.current().value().scan() == held.value().scan(), "configuration can publish on same entity scan");
		check(held.value().visible().get(critter) == 1, "old publication stays stable");
		try {
			publication.publish(new DetectedCritters.Counts(8, 12, null, Map.of()));
			throw new AssertionError("invalid tally published");
		} catch (NullPointerException expected) { }
		check(publication.current().value().scan() == 7, "failed tally cannot advance consumed source scan");
		publication.publish(new DetectedCritters.Counts(Long.MIN_VALUE, Long.MIN_VALUE, Map.of(), Map.of()));
		check(publication.current().value().detected().isEmpty() && publication.current().revision() == 2,
			"reset publishes empty counts as new revision");
		return 6;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
