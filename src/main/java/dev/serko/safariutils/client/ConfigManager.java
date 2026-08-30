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

	/** Output categories are diagnostic session state, not lasting preferences. */
	private static void resetSessionDebugOptions(SafariConfig.AdvancedConfig advanced) {
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
		if (config == null) return;
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
