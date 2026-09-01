package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks which ordinary critters have appeared and which are currently nearby. */
public final class DetectedCritters {

	private static final Set<Critter> everSeen = new HashSet<>();
	private static final Map<Critter, Integer> currentConcurrent = new HashMap<>();
	private static long lastScan = Long.MIN_VALUE;

	private DetectedCritters() {
	}

	/** Called every scan; records every species with a live sighting right now. */
	public static void tick() {
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;
		boolean safeMode = SafeMode.critterDetection();
		Map<Critter, Integer> concurrent = new HashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			boolean sparkling = SparklingWatch.isSparkling(sighting);
			boolean labelVisible = VisibilityCheck.canSeeVisibleName(sighting.label());
			boolean mobVisible = sighting.mob() != null && VisibilityCheck.canSee(sighting.mob());
			boolean hiddenSafe = SafeMode.hiddenCritter(sighting.critter(), sparkling);
			// Dormant hidden species do not visibly expose their label. In Safe Mode,
			// only seeing their actual body can reveal them; an internal label entity is
			// not information the player has. Ordinary critters may still use either a
			// visible label or body because pairing can legitimately fail for them.
			if (hiddenSafe ? !mobVisible : safeMode && !labelVisible && !mobVisible) continue;
			if (sparkling) continue;
			everSeen.add(sighting.critter());
			concurrent.merge(sighting.critter(), 1, Integer::sum);
		}
		// Replace rather than merge so absent species immediately read as zero.
		currentConcurrent.clear();
		currentConcurrent.putAll(concurrent);
	}

	/** Whether {@code critter} has had at least one live sighting this run, ever. */
	public static boolean everSeen(Critter critter) {
		return everSeen.contains(critter);
	}

	/**
	 * How many {@code critter} are concurrently visible this exact tick, or zero.
	 */
	public static int currentConcurrent(Critter critter) {
		return currentConcurrent.getOrDefault(critter, 0);
	}

	/** Nothing carries over between runs. */
	public static void reset() {
		everSeen.clear();
		currentConcurrent.clear();
	}
}
