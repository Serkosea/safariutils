package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks which ordinary critters have appeared and which are currently nearby. */
public final class DetectedCritters {

	private static final Set<Critter> everDetected = new HashSet<>();
	private static final Set<Critter> everVisible = new HashSet<>();
	record Counts(long scan, long configRevision, Map<Critter, Integer> detected,
			Map<Critter, Integer> visible) {
		Counts {
			detected = Map.copyOf(detected);
			visible = Map.copyOf(visible);
		}
	}
	private static final Counts EMPTY = new Counts(Long.MIN_VALUE, Long.MIN_VALUE, Map.of(), Map.of());
	private static final ScanPublication<Counts> counts = new ScanPublication<>(EMPTY);

	private DetectedCritters() {
	}

	/** Called every scan; records every species with a live sighting right now. */
	public static void tick() {
		long scan = CritterEntities.scanRevision();
		long configRevision = ConfigManager.revision();
		Counts previous = counts.current().value();
		if (scan == previous.scan() && configRevision == previous.configRevision()) return;
		Map<Critter, Integer> detected = new HashMap<>();
		Map<Critter, Integer> visible = new HashMap<>();
		boolean trackVisibility = SafeMode.critterDetection() || SafeMode.nearbyCounts();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (CritterEntities.isCaptureScaffolding(sighting)) continue;
			if (sighting.mob() != null && StillCritters.isResolved(sighting.mob().getUUID())) continue;
			boolean sparkling = SparklingWatch.isSparkling(sighting);
			if (sparkling) continue;
			detected.merge(sighting.critter(), 1, Integer::sum);
			if (trackVisibility) {
				boolean labelVisible = VisibilityCheck.canSeeVisibleName(sighting.label());
				boolean mobVisible = sighting.mob() != null && VisibilityCheck.canSee(sighting.mob());
				boolean visuallyKnown = SafeMode.hiddenSpecies(sighting.critter())
					? mobVisible : labelVisible || mobVisible;
				if (!visuallyKnown) continue;
				visible.merge(sighting.critter(), 1, Integer::sum);
			}
		}
		Counts complete = new Counts(scan, configRevision, detected, visible);
		everDetected.addAll(complete.detected().keySet());
		everVisible.addAll(complete.visible().keySet());
		counts.publish(complete);
	}

	/** Whether {@code critter} has had at least one live sighting this run, ever. */
	public static boolean everSeen(Critter critter) {
		return (SafeMode.critterDetection() ? everVisible : everDetected).contains(critter);
	}

	/**
	 * How many {@code critter} are concurrently visible this exact tick, or zero.
	 */
	public static int currentConcurrent(Critter critter) {
		Counts current = counts.current().value();
		return (SafeMode.nearbyCounts() ? current.visible() : current.detected())
			.getOrDefault(critter, 0);
	}

	/** Shared ordinary-critter tally from the latest entity scan. */
	static ScanPublication.Snapshot<Counts> snapshot() {
		return counts.current();
	}

	/** Nothing carries over between runs. */
	public static void reset() {
		everDetected.clear();
		everVisible.clear();
		counts.publish(EMPTY);
	}
}
