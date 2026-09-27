package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.io.AtomicFiles;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns the dependency-free settings model, migration, persistence, and screen. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder()
		.excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
	private static SafariConfig config;
	private static Screen ourScreen;
	private static boolean wasOpen;
	/** Invalidates render-derived caches when a live setting changes between game ticks. */
	private static long revision;

	private ConfigManager() {
	}

	public static void tick() {
		boolean open = ourScreen != null && ClientCompat.screen() == ourScreen;
		if (wasOpen && !open) save();
		wasOpen = open;
	}

	public static SafariConfig get() {
		if (config == null) config = load();
		return config;
	}

	public static long revision() {
		return revision;
	}

	/** Read-only, non-loading probe used by logging during config initialization. */
	public static boolean automaticLoggingDisabled() {
		return config != null && config.advanced.disableAutomaticLogging;
	}

	private static SafariConfig load() {
		Path path = SafariPaths.settings();
		if (!Files.isRegularFile(path)) return new SafariConfig();
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			migrateBannerPlayback(root);
			migrateSparklingCatchIntensity(root);
			SafariConfig loaded = GSON.fromJson(root, SafariConfig.class);
			if (loaded == null) loaded = new SafariConfig();
			if (loaded.sparkling.sparklingUniqueHitboxColours) {
				loaded.display.uniqueHitboxColours = true;
				loaded.sparkling.sparklingUniqueHitboxColours = false;
			}
			resetSessionOptions(loaded);
			return loaded;
		} catch (RuntimeException | IOException malformed) {
			OperationalLog.error("CONFIG/LOAD", malformed);
			return new SafariConfig();
		}
	}

	/** Replaces the former on/off celebration with an always-enabled intensity picker. */
	private static void migrateSparklingCatchIntensity(JsonObject root) {
		if (!root.has("sparkling") || !root.get("sparkling").isJsonObject()) return;
		JsonObject sparkling = root.getAsJsonObject("sparkling");
		if (!sparkling.has("specialSparklingIntensity")) {
			// The old disabled state was the original gentle celebration; the warned
			// enabled state maps to the first of the new intense choices.
			boolean intense = sparkling.has("specialSparklingCatch")
				&& sparkling.get("specialSparklingCatch").getAsBoolean();
			sparkling.addProperty("specialSparklingIntensity", intense ? 1 : 0);
		}
		int presetVersion = sparkling.has("sparklingAlertPresetVersion")
			? sparkling.get("sparklingAlertPresetVersion").getAsInt() : 0;
		if (presetVersion == 0) {
			// The first custom-editor test build used slot 18 for Custom. Preserve it
			// when loading that schema after the preset sequence expands.
			if (sparkling.has("customAlertHorizonFlares")
					&& sparkling.get("specialSparklingIntensity").getAsInt() == 18) {
				sparkling.addProperty("specialSparklingIntensity", SparklingAlertStyle.CUSTOM_INDEX);
			}
		} else if (presetVersion == 2
				&& sparkling.get("specialSparklingIntensity").getAsInt() == 28) {
			// Version two placed Custom immediately after its 28 presets.
			sparkling.addProperty("specialSparklingIntensity", SparklingAlertStyle.CUSTOM_INDEX);
		} else if (presetVersion == 3
				&& sparkling.get("specialSparklingIntensity").getAsInt() == 36) {
			// Version three placed Custom immediately after its 36 presets.
			sparkling.addProperty("specialSparklingIntensity", SparklingAlertStyle.CUSTOM_INDEX);
		}
		if (presetVersion < 5 && sparkling.has("customAlertSoundTheme")
				&& sparkling.get("customAlertSoundTheme").getAsInt() == 5) {
			// Apotheosis moved from the sixth slot to the final slot when two longer
			// scores were added; preserve existing custom-alert sound choices.
			sparkling.addProperty("customAlertSoundTheme", 7);
		}
		if (presetVersion < 5 && sparkling.has("customAlertDuration")) {
			int oldLevel = Math.clamp(sparkling.get("customAlertDuration").getAsInt(), 0, 7);
			int[] oldDurations = {5, 6, 7, 8, 10, 12, 15, 20};
			sparkling.addProperty("customAlertDuration", oldDurations[oldLevel]);
		}
		if (presetVersion < 9 && sparkling.has("customAlertSong")) {
			int oldSong = sparkling.get("customAlertSong").getAsInt();
			// Preserve Off and map the former eighteen-song range proportionally onto
			// the cleaner seven-composition set.
			sparkling.addProperty("customAlertSong", oldSong >= 18 ? 7
				: Math.clamp(Math.round(oldSong * 6f / 17f), 0, 6));
		}
		sparkling.addProperty("sparklingAlertPresetVersion", 9);
		sparkling.remove("specialSparklingCatch");
	}

	/** Converts the old two-toggle setup once; later saves contain only the picker. */
	private static void migrateBannerPlayback(JsonObject root) {
		String[][] settings = {
			{"alerts", "fullPartyJoinedAlert", "fullPartyJoinedSound", "fullPartyJoinedSoundMode", "3"},
			{"alerts", "hotspotAlert", "hotspotSound", "hotspotSoundMode", "3"},
			{"alerts", "floorDropsDoneAlert", "floorDropsDoneSound", "floorDropsDoneSoundMode", "3"},
			{"alerts", "biomeUniquesDoneAlert", "biomeUniquesDoneSound", "biomeUniquesDoneSoundMode", "3"},
			{"alerts", "allButMacawDoneAlert", "allButMacawDoneSound", "allButMacawDoneSoundMode", "3"},
			{"alerts", "allUniquesDoneAlert", "allUniquesDoneSound", "allUniquesDoneSoundMode", "3"},
			{"alerts", "gemzieReadyAlert", "gemzieReadySound", "gemzieReadySoundMode", "3"},
			{"alerts", "gemzieDoneAlert", "gemzieDoneSound", "gemzieDoneSoundMode", "3"},
			{"alerts", "wumpaReadyAlert", "wumpaReadySound", "wumpaReadySoundMode", "3"},
			{"alerts", "wumpaStartedAlert", "wumpaStartedSound", "wumpaStartedSoundMode", "3"},
			{"alerts", "wumpaDoneAlert", "wumpaDoneSound", "wumpaDoneSoundMode", "3"},
			{"alerts", "doomspiralReadyAlert", "doomspiralReadySound", "doomspiralReadySoundMode", "3"},
			{"alerts", "doomspiralStartedAlert", "doomspiralStartedSound", "doomspiralStartedSoundMode", "3"},
			{"alerts", "doomspiralDoneAlert", "doomspiralDoneSound", "doomspiralDoneSoundMode", "3"},
			{"alerts", "hideyhoAlert", "hideyhoSound", "hideyhoSoundMode", "3"},
			{"alerts", "macawAlert", "macawSound", "macawSoundMode", "3"},
			{"alerts", "birdfeederAlert", "birdfeederSound", "birdfeederSoundMode", "1"},
			{"alerts", "feedGoneAlert", "feedGoneSound", "feedGoneSoundMode", "3"},
			{"alerts", "contestStartAlert", "contestStartSound", "contestStartSoundMode", "3"},
			{"alerts", "contestFiveMinuteAlert", "contestFiveMinuteSound", "contestFiveMinuteSoundMode", "3"},
			{"alerts", "contestOneMinuteAlert", "contestOneMinuteSound", "contestOneMinuteSoundMode", "3"},
			{"alerts", "contestEndedAlert", "contestEndedSound", "contestEndedSoundMode", "3"},
			{"alerts", "contestTicketEarnedAlert", "contestTicketEarnedSound", "contestTicketEarnedSoundMode", "3"},
			{"sparkling", "sparklingBannerAlert", "sparklingBannerSound", "sparklingBannerSoundMode", "3"}
		};
		for (String[] setting : settings) {
			if (!root.has(setting[0]) || !root.get(setting[0]).isJsonObject()) continue;
			JsonObject category = root.getAsJsonObject(setting[0]);
			if (!category.has(setting[3])) {
				int defaults = Integer.parseInt(setting[4]);
				boolean enabled = category.has(setting[1])
					? category.get(setting[1]).getAsBoolean() : defaults != 0;
				// Older configs also had a hidden master switch for each encounter.
				for (String boss : new String[]{"gemzie", "wumpa", "doomspiral"}) {
					if (setting[1].startsWith(boss) && category.has(boss + "Alert")) {
						enabled &= category.get(boss + "Alert").getAsBoolean();
					}
				}
				boolean sound = category.has(setting[2])
					? category.get(setting[2]).getAsBoolean() : (defaults & 2) != 0;
				category.addProperty(setting[3], enabled ? (sound ? 3 : 1) : 0);
			}
			category.remove(setting[1]);
			category.remove(setting[2]);
		}
	}

	/** Party sync must be explicitly acknowledged again after every launch. */
	private static void resetSessionOptions(SafariConfig loaded) {
		loaded.advanced.enablePartySync = false;
		loaded.sparkling.ticketTradingEnabled = false;
	}

	/** Writes atomically so an interrupted save cannot destroy a working config. */
	public static synchronized void save() {
		if (config == null) return;
		revision++;
		Path path = SafariPaths.settings();
		try {
			AtomicFiles.writeString(path, GSON.toJson(config));
		} catch (IOException failed) {
			// Settings remain live in memory; the next close/shutdown retries the save.
			OperationalLog.error("CONFIG/SAVE", failed);
		}
	}

	public static Screen createScreen(Screen parent) {
		save();
		ourScreen = new SafariSettingsScreen(parent);
		return ourScreen;
	}

	static void resetSettingCategory(String categoryField) {
		try {
			var field = SafariConfig.class.getField(categoryField);
			field.set(get(), field.getType().getDeclaredConstructor().newInstance());
			save();
		} catch (ReflectiveOperationException failed) {
			OperationalLog.error("CONFIG/RESET", failed);
		}
	}
}
