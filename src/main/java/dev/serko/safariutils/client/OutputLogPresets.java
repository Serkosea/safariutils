package dev.serko.safariutils.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Applies coherent groups of temporary diagnostics without changing lasting preferences. */
final class OutputLogPresets {
	private static final int ALL = 0;
	private static final int CUSTOM = 1;
	private static final int JOIN_TIMING = 2;
	private static final int SAFARI_RUN_RESEARCH = 3;
	private static final int PARTY_AND_SYNC = 4;
	private static final int RUN_LIFECYCLE = 5;
	private static final int OBJECTIVES_AND_INVENTORY = 6;
	private static final int CONTESTS_AND_HUD = 7;
	private static final int SPARKLING_RESEARCH = 8;
	private static final int STATIC_LOCATIONS = 9;
	private static final int ENTITY_AND_CATCH_TRACKING = 10;
	private static final int SERVER_PACKETS = 11;
	private static final int LAST_PRESET = SERVER_PACKETS;
	private static final List<LogOption> LOG_OPTIONS = discoverLogOptions();

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
		for (int preset = ALL; preset <= LAST_PRESET; preset++) {
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
			case JOIN_TIMING -> joinTiming(config);
			case SAFARI_RUN_RESEARCH -> safariRunResearch(config);
			case PARTY_AND_SYNC -> partyAndSync(config);
			case RUN_LIFECYCLE -> runLifecycle(config);
			case OBJECTIVES_AND_INVENTORY -> objectivesAndInventory(config);
			case CONTESTS_AND_HUD -> contestsAndHud(config);
			case SPARKLING_RESEARCH -> sparklingResearch(config);
			case STATIC_LOCATIONS -> staticLocations(config);
			case ENTITY_AND_CATCH_TRACKING -> entityAndCatchTracking(config);
			case SERVER_PACKETS -> serverPackets(config);
			default -> {
				config.outputLogPreset = CUSTOM;
				return;
			}
		}
		config.outputLogOptionsAccordion = true;
	}

	/** Exact labels shown below the picker, derived from the actual enabled fields. */
	static List<String> enabledOptionNames() {
		SafariConfig.AdvancedConfig config = ConfigManager.get().advanced;
		List<String> enabled = new ArrayList<>();
		for (LogOption option : LOG_OPTIONS) {
			try {
				if (option.field().getBoolean(config)) enabled.add(option.label());
			} catch (IllegalAccessException ignored) {
			}
		}
		return List.copyOf(enabled);
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
		config.outputServerPacketsAccordion = true;
	}

	private static void joinTiming(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logActivation = true;
		config.logScoreboard = true;
		config.logPacketTransitions = true;
		config.logPacketHud = true;
		config.logPacketWorld = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	/** Broad solo/party run evidence without noisy packet streams or private payloads. */
	private static void safariRunResearch(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logPartyRoster = true;
		config.logPartyTiming = true;
		config.logPartySync = true;
		config.logPartyApi = true;
		config.logTabList = true;
		config.logScoreboard = true;
		config.logInventory = true;
		config.logInterfaces = true;
		config.logChat = true;
		config.logRun = true;
		config.logObjectives = true;
		config.logActivation = true;
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
		config.logSparkling = true;
		config.logPacketTransitions = true;
		config.logPacketEntities = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	private static void partyAndSync(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logPartyRoster = true;
		config.logPartyTiming = true;
		config.logPartySync = true;
		config.logPartyApi = true;
		config.logInterfaces = true;
		config.logInventory = true;
		config.logTabList = true;
		config.logScoreboard = true;
		config.logPacketTransitions = true;
		config.logPacketHud = true;
		config.logPacketInventory = true;
		config.logPacketChannels = true;
		config.outputSessionDataAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	private static void runLifecycle(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logInventory = true;
		config.logInterfaces = true;
		config.logChat = true;
		config.logRun = true;
		config.logObjectives = true;
		config.logActivation = true;
		config.logHeadstart = true;
		config.logPacketTransitions = true;
		config.logPacketInventory = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	private static void objectivesAndInventory(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logInventory = true;
		config.logInterfaces = true;
		config.logRun = true;
		config.logObjectives = true;
		config.logHeadstart = true;
		config.logFloor = true;
		config.logNest = true;
		config.logStaticWaypoints = true;
		config.logPacketInventory = true;
		config.logPacketEntities = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	private static void contestsAndHud(SafariConfig.AdvancedConfig config) {
		config.logRaw = true;
		config.logLocation = true;
		config.logTabList = true;
		config.logScoreboard = true;
		config.logPacketHud = true;
		config.logPacketWorld = true;
		config.outputSessionDataAccordion = true;
		config.outputServerPacketsAccordion = true;
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
		config.logPacketEntities = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	private static void staticLocations(SafariConfig.AdvancedConfig config) {
		config.logLocation = true;
		config.logSighting = true;
		config.logPair = true;
		config.logStill = true;
		config.logHideyho = true;
		config.logStaticWaypoints = true;
		config.logFloor = true;
		config.logNest = true;
		config.outputSessionDataAccordion = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
	}

	private static void entityAndCatchTracking(SafariConfig.AdvancedConfig config) {
		config.logChat = true;
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
		config.logPacketEntities = true;
		config.outputCritterDetectionAccordion = true;
		config.outputWorldTrackingAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	private static void serverPackets(SafariConfig.AdvancedConfig config) {
		config.logLocation = true;
		config.logPacketTransitions = true;
		config.logPacketHud = true;
		config.logPacketInventory = true;
		config.logPacketEntities = true;
		config.logPacketWorld = true;
		config.logPacketChannels = true;
		config.outputSessionDataAccordion = true;
		config.outputServerPacketsAccordion = true;
	}

	private static List<LogOption> discoverLogOptions() {
		List<LogOption> options = new ArrayList<>();
		for (Field field : SafariConfig.AdvancedConfig.class.getFields()) {
			if (field.getType() != boolean.class || !field.getName().startsWith("log")) continue;
			SettingInfo info = field.getAnnotation(SettingInfo.class);
			options.add(new LogOption(field, info == null ? field.getName() : info.name()));
		}
		return List.copyOf(options);
	}

	private record LogOption(Field field, String label) { }
}
