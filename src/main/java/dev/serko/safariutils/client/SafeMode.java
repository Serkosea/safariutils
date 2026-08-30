package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.data.Critter;

/** Centralizes the master Safe Mode switch and its per-feature overrides. */
public final class SafeMode {
	private SafeMode() {
	}

	private static SafariConfig.AdvancedConfig config() {
		return ConfigManager.get().advanced;
	}

	private static boolean option(boolean enabled) {
		return BuildVersion.SAFE || (config().safeMode && enabled);
	}

	/** Whether the version-wide or user-controlled Safe Mode switch is active. */
	public static boolean active() { return BuildVersion.SAFE || config().safeMode; }

	public static boolean critterDetection() { return option(config().safeVisibleCritterDetection); }
	public static boolean nearbyCounts() { return option(config().safeHideNearbyCounts); }
	public static boolean conservativeAvailability() { return option(config().safeConservativeAvailability); }
	public static boolean conservativeCompletion() { return option(config().safeConservativeCompletion); }
	public static boolean sparklingCritters() { return option(config().safeSparklingCritters); }

	public static boolean critterHitboxes(boolean sparkling) {
		if (BuildVersion.SAFE) return true;
		return option(config().safeCritterHitboxes) && (!sparkling || config().safeSparklingCritters);
	}

	public static boolean hiddenCritter(Critter critter, boolean sparkling) {
		if (BuildVersion.SAFE) return isHiddenSpecies(critter);
		if (!config().safeMode || (sparkling && !config().safeSparklingCritters)) return false;
		return isHiddenSpeciesEnabled(critter);
	}

	private static boolean isHiddenSpecies(Critter critter) {
		return switch (critter.name()) {
			case "Hideyho", "Hideonwall", "Duplico", "Bloodbat", "Hideonfloor" -> true;
			default -> false;
		};
	}

	private static boolean isHiddenSpeciesEnabled(Critter critter) {
		return switch (critter.name()) {
			case "Hideyho" -> config().safeHideyho;
			case "Hideonwall" -> config().safeHideonwall;
			case "Duplico" -> config().safeDuplico;
			case "Bloodbat" -> config().safeBloodbat;
			case "Hideonfloor" -> config().safeHideonfloor;
			default -> false;
		};
	}

	public static boolean hiddenCritterCandidates(Critter critter) {
		return hiddenCritter(critter, false);
	}

	public static boolean hideyho() { return option(config().safeHideyho); }
	public static boolean floorDrops() { return option(config().safeFloorDrops); }
	public static boolean nests() { return option(config().safeBeeNests); }
	public static boolean mounds() { return option(config().safeRockmiteMounds); }
	public static boolean snoozleWalls() { return option(config().safeSnoozleWalls); }
	public static boolean troodonWalls() { return option(config().safeTroodonWalls); }
}
