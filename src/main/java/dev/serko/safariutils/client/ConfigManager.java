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

	private static SafariConfig load() {
		Path path = SafariPaths.settings();
		if (!Files.isRegularFile(path)) return new SafariConfig();
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			migrateBannerPlayback(root);
			SafariConfig loaded = GSON.fromJson(root, SafariConfig.class);
			if (loaded == null) loaded = new SafariConfig();
			if (loaded.sparkling.sparklingUniqueHitboxColours) {
				loaded.display.uniqueHitboxColours = true;
				loaded.sparkling.sparklingUniqueHitboxColours = false;
			}
			resetSessionDebugOptions(loaded.advanced);
			return loaded;
		} catch (RuntimeException | IOException malformed) {
			return new SafariConfig();
		}
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

	/** Output categories are diagnostic session state, not lasting preferences. */
	private static void resetSessionDebugOptions(SafariConfig.AdvancedConfig advanced) {
		advanced.debugLog = false;
		advanced.outputLogPreset = 1; // Custom: every individual option starts disabled below.
		for (var field : SafariConfig.AdvancedConfig.class.getFields()) {
			if (field.getType() != boolean.class || !field.getName().startsWith("log")) continue;
			try {
				field.setBoolean(advanced, false);
			} catch (IllegalAccessException ignored) {
			}
		}
	}

	/** Writes atomically so an interrupted save cannot destroy a working config. */
	public static synchronized void save() {
		if (config == null || TestingMode.savingSuspended()) return;
		Path path = SafariPaths.settings();
		try {
			AtomicFiles.writeString(path, GSON.toJson(config));
		} catch (IOException ignored) {
			// Settings remain live in memory; the next close/shutdown retries the save.
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
		} catch (ReflectiveOperationException ignored) {
		}
	}
}
