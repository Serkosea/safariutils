package dev.serko.safariutils.session;

import dev.serko.safariutils.data.Critter;

import java.util.HashSet;
import java.util.Set;

/**
 * Defines whether one catch or the full known spawn quota completes a species.
 * Kept outside config so run logic does not depend on the settings UI.
 */
public final class TrackingMode {

	private static boolean uniqueOnly;
	private static boolean countSpawns = true;
	private static final Set<Critter> unavailable = new HashSet<>();

	private TrackingMode() {
	}

	public static void setUniqueOnly(boolean value) {
		uniqueOnly = value;
	}

	public static boolean uniqueOnly() {
		return uniqueOnly;
	}

	public static void setCountSpawns(boolean value) {
		countSpawns = value;
	}

	/** Whether spawns seen in the world are used as the target for unquotaed species. */
	public static boolean countSpawns() {
		return countSpawns;
	}

	/**
	 * Marks the species this run can no longer produce, so they stop being reported as
	 * outstanding. Snoozle comes from the breakable Cavern walls: once every wall is
	 * confirmed broken without one appearing, none can.
	 */
	public static boolean setUnavailable(Set<Critter> species) {
		if (unavailable.equals(species)) return false;
		unavailable.clear();
		unavailable.addAll(species);
		return true;
	}

	/** True when the run cannot produce {@code critter} any more. */
	public static boolean isUnavailable(Critter critter) {
		return unavailable.contains(critter);
	}

	/** How many of {@code critter} are needed before it counts as done. */
	public static int required(Critter critter) {
		if (uniqueOnly || !critter.hasQuota()) return 1;
		return critter.spawnQuota();
	}
}
