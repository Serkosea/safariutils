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
	private static final Map<Critter, Integer> currentDetected = new HashMap<>();
	private static final Map<Critter, Integer> currentVisible = new HashMap<>();
	private static long lastScan = Long.MIN_VALUE;

	private DetectedCritters() {
	}

	/** Called every scan; records every species with a live sighting right now. */
	public static void tick() {
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;
		Map<Critter, Integer> detected = new HashMap<>();
		Map<Critter, Integer> visible = new HashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			boolean sparkling = SparklingWatch.isSparkling(sighting);
			if (sparkling) continue;
			boolean labelVisible = VisibilityCheck.canSeeVisibleName(sighting.label());
			boolean mobVisible = sighting.mob() != null && VisibilityCheck.canSee(sighting.mob());
			boolean visuallyKnown = SafeMode.hiddenSpecies(sighting.critter())
				? mobVisible : labelVisible || mobVisible;
			everDetected.add(sighting.critter());
			detected.merge(sighting.critter(), 1, Integer::sum);
			if (visuallyKnown) {
				everVisible.add(sighting.critter());
				visible.merge(sighting.critter(), 1, Integer::sum);
			}
		}
		currentDetected.clear();
		currentDetected.putAll(detected);
		currentVisible.clear();
		currentVisible.putAll(visible);
	}

	/** Whether {@code critter} has had at least one live sighting this run, ever. */
	public static boolean everSeen(Critter critter) {
		return (SafeMode.critterDetection() ? everVisible : everDetected).contains(critter);
	}

	/**
	 * How many {@code critter} are concurrently visible this exact tick, or zero.
	 */
	public static int currentConcurrent(Critter critter) {
		return (SafeMode.nearbyCounts() ? currentVisible : currentDetected)
			.getOrDefault(critter, 0);
	}

	/** Nothing carries over between runs. */
	public static void reset() {
		everDetected.clear();
		everVisible.clear();
		currentDetected.clear();
		currentVisible.clear();
	}
}
