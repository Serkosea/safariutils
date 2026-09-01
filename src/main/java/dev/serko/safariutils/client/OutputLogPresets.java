package dev.serko.safariutils.client;

import java.lang.reflect.Field;

/** Applies coherent groups of temporary diagnostics without changing lasting preferences. */
final class OutputLogPresets {
	private static final int ALL = 0;
	private static final int CUSTOM = 1;
	private static final int PARTY_AND_SERVER = 2;
	private static final int RUN_LIFECYCLE = 3;
	private static final int SPARKLING_RESEARCH = 4;
	private static final int STATIC_LOCATIONS = 5;
	private static final int WORLD_TRACKING = 6;

	private OutputLogPresets() {
	}

	static void apply(int preset) {
		if (preset == CUSTOM) return;
		SafariConfig.AdvancedConfig config = ConfigManager.get().advanced;
		applyTo(config, preset);
	}

	/** Keeps the picker honest when individual diagnostic options are edited. */
	static int syncSelection() {
		SafariConfig.AdvancedConfig config = ConfigManager.get().advanced;
		for (int preset = ALL; preset <= WORLD_TRACKING; preset++) {
			if (preset == CUSTOM) continue;
			SafariConfig.AdvancedConfig expected = new SafariConfig.AdvancedConfig();
			applyTo(expected, preset);
			if (sameOptions(config, expected)) {
				config.outputLogPreset = preset;
				return preset;
			}
		}
		config.outputLogPreset = CUSTOM;
		return CUSTOM;
	}

	private static void applyTo(SafariConfig.AdvancedConfig config, int preset) {
		clearLogOptions(config);

		switch (preset) {
			case ALL -> enableEveryLogOption(config);
			case PARTY_AND_SERVER -> partyAndServer(config);
			case RUN_LIFECYCLE -> runLifecycle(config);
			case SPARKLING_RESEARCH -> sparklingResearch(config);
			case STATIC_LOCATIONS -> staticLocations(config);
			case WORLD_TRACKING -> worldTracking(config);
			default -> {
				config.outputLogPreset = CUSTOM;
				return;
			}
		}
		config.outputLogOptionsAccordion = true;
	}

	private static boolean sameOptions(SafariConfig.AdvancedConfig current,
			SafariConfig.AdvancedConfig expected) {
		for (Field field : SafariConfig.AdvancedConfig.class.getFields()) {
			if (field.getType() != boolean.class || !field.getName().startsWith("log")) continue;
			try {
				if (field.getBoolean(current) != field.getBoolean(expected)) return false;
			} catch (IllegalAccessException ignored) {
				return false;
			}
		}
		return true;
	}

	private static void clearLogOptions(SafariConfig.AdvancedConfig config) {
		for (Field field : SafariConfig.AdvancedConfig.class.getFields()) {
			if (field.getType() != boolean.class || !field.getName().startsWith("log")) continue;
			try {
				field.setBoolean(config, false);
			} catch (IllegalAccessException ignored) {
			}
		}
	}

	private static void enableEveryLogOption(SafariConfig.AdvancedConfig config) {
		for (Field field : SafariConfig.AdvancedConfig.class.getFields()) {
			if (field.getType() != boolean.class || !field.getName().startsWith("log")) continue;
			try {
				field.setBoolean(config, true);
			} catch (IllegalAccessException ignored) {
			}
		}
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
	}

	private static void partyAndServer(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logPartyRoster = true;
		config.logTabList = true;
		config.logScoreboard = true;
		config.outputSessionDataAccordion = true;
	}

	private static void runLifecycle(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logInventory = true;
		config.logChat = true;
		config.logRun = true;
		config.logActivation = true;
		config.logHeadstart = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
	}

	private static void sparklingResearch(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logChat = true;
		config.logRun = true;
		config.logActivation = true;
		config.logSighting = true;
		config.logPair = true;
		config.logStaticWaypoints = true;
		config.logParticles = true;
		config.logSparkling = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
	}

	private static void staticLocations(SafariConfig.AdvancedConfig config) {
		config.logLocation = true;
		config.logSighting = true;
		config.logPair = true;
		config.logStaticWaypoints = true;
		config.logFloor = true;
		config.logNest = true;
		config.outputSessionDataAccordion = true;
		config.outputWorldTrackingAccordion = true;
	}

	private static void worldTracking(SafariConfig.AdvancedConfig config) {
		config.logSighting = true;
		config.logNearby = true;
		config.logPair = true;
		config.logBall = true;
		config.logRecatch = true;
		config.logDraw = true;
		config.logStill = true;
		config.logWall = true;
		config.logFloor = true;
		config.logHideyho = true;
		config.logHeadstart = true;
		config.logNest = true;
		config.logCritterCounts = true;
		config.logStaticWaypoints = true;
		config.logParticles = true;
		config.logSparkling = true;
		config.outputWorldTrackingAccordion = true;
	}
}
