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
		return !BuildVersion.has(BuildVersion.Capability.SAFE_MODE_CONTROLS) || enabled;
	}

	/** Whether any visibility-based protection is active. */
	public static boolean active() {
		SafariConfig.AdvancedConfig config = config();
        return !BuildVersion.has(BuildVersion.Capability.SAFE_MODE_CONTROLS)
            || config.safeDetectionHud || config.safeCritterMarkers || config.safeSparklingCritters
            || config.safeWorldObjectives || config.safePredictedInformation;
	}

	public static boolean critterDetection() { return option(config().safeDetectionHud); }
	public static boolean nearbyCounts() { return option(config().safeDetectionHud); }
	public static boolean conservativeAvailability() { return option(config().safeDetectionHud); }
	public static boolean conservativeCompletion() { return option(config().safeDetectionHud); }
	public static boolean sparklingCritters() { return option(config().safeSparklingCritters); }

	public static boolean critterHitboxes(boolean sparkling) {
		if (!BuildVersion.has(BuildVersion.Capability.SAFE_MODE_CONTROLS)) return true;
		return option(config().safeCritterMarkers) && (!sparkling || config().safeSparklingCritters);
	}

	public static boolean hiddenCritter(Critter critter, boolean sparkling) {
		if (!BuildVersion.has(BuildVersion.Capability.SAFE_MODE_CONTROLS)) return hiddenSpecies(critter);
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
        return hiddenSpecies(critter) && config().safeCritterMarkers;
    }

	public static boolean hiddenCritterCandidates(Critter critter) {
		return hiddenCritter(critter, false);
	}

	public static boolean hideyho() { return option(config().safeCritterMarkers); }
	public static boolean floorDropContents() { return option(config().safePredictedInformation); }
	public static boolean scrappyFood() { return option(config().safePredictedInformation); }
	public static boolean floorDrops() { return option(config().safeWorldObjectives); }
	public static boolean nests() { return option(config().safeWorldObjectives); }
	public static boolean mounds() { return option(config().safeWorldObjectives); }
	public static boolean snoozleWalls() { return option(config().safeWorldObjectives); }
	public static boolean troodonWalls() { return option(config().safeWorldObjectives); }
}
