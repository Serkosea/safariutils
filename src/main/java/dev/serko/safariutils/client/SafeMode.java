package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.data.Critter;

/** Centralizes the build-wide Safe Mode requirement and per-feature controls. */
public final class SafeMode {
	private SafeMode() {
	}

	private static SafariConfig.AdvancedConfig config() {
		return ConfigManager.get().advanced;
	}

	private static boolean option(boolean enabled) {
		return BuildVersion.SAFE || enabled;
	}

	/** Whether any visibility-based protection is active. */
	public static boolean active() {
		SafariConfig.AdvancedConfig config = config();
		return BuildVersion.SAFE || config.safeVisibleCritterDetection
			|| config.safeHideNearbyCounts || config.safeConservativeAvailability
			|| config.safeConservativeCompletion || config.safeCritterHitboxes
			|| config.safeSparklingCritters || config.safeHideyho || config.safeHideonwall
			|| config.safeDuplico || config.safeBloodbat || config.safeHideonfloor
			|| config.safeFloorDrops || config.safeBeeNests || config.safeRockmiteMounds
			|| config.safeSnoozleWalls || config.safeTroodonWalls;
	}

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
		if (BuildVersion.SAFE) return hiddenSpecies(critter);
		if (sparkling && !config().safeSparklingCritters) return false;
		return isHiddenSpeciesEnabled(critter);
	}

	/** Species whose loaded entities can reveal information the player has not seen. */
	static boolean hiddenSpecies(Critter critter) {
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
