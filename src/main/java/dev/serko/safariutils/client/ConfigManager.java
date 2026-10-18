package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.io.PersistentJson;
import dev.serko.safariutils.io.ImportBackup;
import dev.serko.safariutils.io.RecoverySnapshot;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns the dependency-free settings model, migration, persistence, and screen. */
public final class ConfigManager {
	/** Append one migration step whenever persisted setting structure changes. */
	public static final int CURRENT_SETTINGS_SCHEMA = 5;
	private static final Gson GSON = new GsonBuilder()
		.excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
	private static SafariConfig config;
	/** Migrated source tree retained so unknown and edition-specific keys survive saves. */
	private static JsonObject preservedSettings;
	private static Screen ourScreen;
	private static boolean wasOpen;
	/** Invalidates render-derived caches when a live setting changes between game ticks. */
	private static long revision;
	/** Never replace an unreadable source unless it was first preserved successfully. */
	private static boolean writesBlocked;
	/** A failed release-boundary snapshot is retried before any later write. */
	private static boolean pendingVersionBackup;
	private static final RecoverySnapshot RECOVERY = new RecoverySnapshot("safariutils-settings-working");

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

	static JsonObject settingsJson() {
		return mergedSettingsJson(get());
	}

	/** Parses and migrates an imported copy without changing live settings. */
	static SafariConfig validateImported(JsonObject root) {
		PersistentJson.requireSupportedSchema(root, "settingsSchemaVersion", CURRENT_SETTINGS_SCHEMA);
		SafariConfig imported = parseSettings(root);
		if (imported == null) throw new IllegalArgumentException("Settings were empty");
		return imported;
	}

	/** Replaces settings only after the transfer layer has validated and backed them up. */
	static synchronized Path importSettings(JsonObject root) throws IOException {
		PersistentJson.requireSupportedSchema(root, "settingsSchemaVersion", CURRENT_SETTINGS_SCHEMA);
		SafariConfig imported = parseSettings(root);
		if (imported == null) throw new IllegalArgumentException("Settings were empty");
		JsonObject merged = mergedSettingsJson(imported, root);
		Path backup = ImportBackup.preserve(SafariPaths.settings(), SafariPaths.settingsBackups(),
			GSON.toJson(settingsJson()));
		AtomicFiles.writeString(SafariPaths.settings(), GSON.toJson(merged));
		preservedSettings = merged;
		config = imported;
		OperationalLog.settingsChanged();
		writesBlocked = false;
		pendingVersionBackup = false;
		RECOVERY.reset();
		revision++;
		return backup;
	}

	public static long revision() {
		return revision;
	}

	/** Read-only, non-loading probe used by logging during config initialization. */
	public static boolean automaticLoggingEnabled() {
		return config != null && config.advanced.enableAutomaticLogging;
	}

	private static SafariConfig load() {
		Path path = SafariPaths.settings();
		preservedSettings = null;
		writesBlocked = false;
		pendingVersionBackup = false;
		RECOVERY.reset();
		if (!Files.isRegularFile(path)) return new SafariConfig();
		try {
			String saved = Files.readString(path);
			try {
				backupBeforeVersionLoad(path);
			} catch (IOException backupFailed) {
				// A backup problem must never prevent a valid settings file from loading.
				// It does, however, prevent this version from rewriting it until a later
				// save can successfully preserve the untouched source.
				pendingVersionBackup = true;
				OperationalLog.error("CONFIG/BACKUP", backupFailed);
			}
			JsonObject root = JsonParser.parseString(saved).getAsJsonObject();
			if (PersistentJson.schema(root, "settingsSchemaVersion") > CURRENT_SETTINGS_SCHEMA) {
				writesBlocked = true;
				OperationalLog.info("CONFIG/FUTURE_SCHEMA",
					"Settings schema is newer or unrecognized; using them read-only");
			}
			SafariConfig loaded = parseSettings(root);
			if (loaded != null) preservedSettings = root.deepCopy();
			return loaded == null ? new SafariConfig() : loaded;
		} catch (RuntimeException | IOException malformed) {
			OperationalLog.error("CONFIG/LOAD", malformed);
			boolean preserved = false;
			try {
				Path backup = SafariPaths.quarantineUnreadableJson(path,
					SafariPaths.settingsBackups(), "safariutils-settings");
				preserved = backup != null;
				if (backup != null) OperationalLog.info("CONFIG/RECOVERY",
					"Preserved unreadable settings at " + backup.toAbsolutePath());
			} catch (IOException backupFailed) {
				OperationalLog.error("CONFIG/RECOVERY", backupFailed);
			}
			writesBlocked |= !preserved && Files.exists(path);
			return new SafariConfig();
		}
	}

	/**
	 * Keeps one untouched settings snapshot per SafariUtils version before that
	 * version can migrate, normalize, or save the file. Existing snapshots are
	 * never replaced, so a bad update remains recoverable even after relaunching.
	 */
	private static void backupBeforeVersionLoad(Path source) throws IOException {
		PersistentJson.backupBeforeVersion(source, SafariPaths.settingsBackups(),
			"safariutils-settings", BuildVersion.releaseVersion());
	}

	private static SafariConfig parseSettings(JsonObject root) {
		migrateSettings(root);
		SafariConfig loaded = GSON.fromJson(root, SafariConfig.class);
		if (loaded == null) return null;
		normalizeCategories(loaded);
		TicketTrading.sanitizeSettings(loaded.sparkling);
		normalizeSavedAlertPresets(loaded.sparkling);
		if (loaded.sparkling.sparklingUniqueHitboxColours) {
			loaded.display.uniqueHitboxColours = true;
			loaded.sparkling.sparklingUniqueHitboxColours = false;
		}
		resetSessionOptions(loaded);
		return loaded;
	}

	/**
	 * Runs ordered, one-time migrations. Existing unversioned files are schema
	 * zero; future migrations append another case and increment the current
	 * version without changing or re-running earlier steps.
	 */
	private static void migrateSettings(JsonObject root) {
		int version = PersistentJson.schema(root, "settingsSchemaVersion");
		if (version > CURRENT_SETTINGS_SCHEMA) return;
		while (version < CURRENT_SETTINGS_SCHEMA) {
			switch (version) {
				case 0 -> migrateLegacySettings(root);
				case 1 -> migrateAutomaticPartySync(root);
				case 2 -> migrateTicketTradingRoles(root);
				case 3 -> migrateTicketTradingGuestMembership(root);
				case 4 -> migrateAutomaticLogging(root);
				default -> throw new IllegalStateException("Missing settings migration from schema " + version);
			}
			version++;
			root.addProperty("settingsSchemaVersion", version);
		}
	}

	private static void migrateAutomaticLogging(JsonObject root) {
		if (!root.has("advanced") || !root.get("advanced").isJsonObject()) return;
		JsonObject advanced = root.getAsJsonObject("advanced");
		var old = advanced.remove("disableAutomaticLogging");
		if (!advanced.has("enableAutomaticLogging") && old != null && old.isJsonPrimitive()
			&& old.getAsJsonPrimitive().isBoolean()) {
			advanced.addProperty("enableAutomaticLogging", !old.getAsBoolean());
		}
	}

	/** Consolidates every pre-schema migration into the schema-zero upgrade. */
	private static void migrateLegacySettings(JsonObject root) {
		migrateBannerPlayback(root);
		migrateSparklingCatchIntensity(root);
		migrateSafeModeOptions(root);
		migrateTicketTradingProfiles(root);
	}

	/** Removes the retired opt-in flag now that synchronization is automatic and network-only. */
	private static void migrateAutomaticPartySync(JsonObject root) {
		if (root.has("advanced") && root.get("advanced").isJsonObject()) {
			root.getAsJsonObject("advanced").remove("enablePartySync");
		}
	}

	/**
	 * Overlays every known value onto the migrated source tree while retaining
	 * unknown keys. This prevents settings from newer versions or other editions
	 * from disappearing merely because this build does not currently expose them.
	 */
	private static JsonObject mergedSettingsJson(SafariConfig value) {
		return mergedSettingsJson(value, preservedSettings);
	}

	private static JsonObject mergedSettingsJson(SafariConfig value, JsonObject source) {
		return PersistentJson.merge(source, GSON.toJsonTree(value).getAsJsonObject());
	}

	/** Explicit JSON nulls must behave like absent categories, not poison later reads. */
	private static void normalizeCategories(SafariConfig loaded) {
		fillNullFields(loaded, new SafariConfig());
		normalizeAnnotatedValues(loaded);
	}

	/** Keeps hand-edited/imported picker, mask, and slider values inside declared bounds. */
	private static void normalizeAnnotatedValues(Object target) {
		for (var field : target.getClass().getFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
				|| field.getAnnotation(com.google.gson.annotations.Expose.class) == null) continue;
			try {
				Object value = field.get(target);
				if (value != null && field.getType().getEnclosingClass() == SafariConfig.class) {
					normalizeAnnotatedValues(value);
				}
				SettingChoice choice = field.getAnnotation(SettingChoice.class);
				if (choice != null && field.getType() == int.class && choiceCount(field, choice) > 0) {
					int choices = choiceCount(field, choice);
					field.setInt(target, Math.clamp(field.getInt(target), 0, choices - 1));
				}
				SettingRange range = field.getAnnotation(SettingRange.class);
				if (range != null) clampRange(target, field, range);
				SettingMultiChoice multi = field.getAnnotation(SettingMultiChoice.class);
				if (multi != null) clampMask(target, field, multi);
			} catch (IllegalAccessException impossible) {
				throw new IllegalStateException("Could not validate settings field " + field.getName(),
					impossible);
			}
		}
	}

	private static int choiceCount(Field field, SettingChoice choice) {
		return AlertSounds.isChoiceSetting(field.getName())
			? AlertSounds.choiceCount() : choice.values().length;
	}

	/**
	 * Startup invariant covering every settings control. It catches a missing
	 * {@code @Expose}, an incorrect picker bound, or a migration/sanitizer that
	 * changes current-schema values before a release can silently rewrite users'
	 * files.
	 */
	public static void verifyPersistenceModel() {
		SafariConfig probe = new SafariConfig();
		exercisePersistedSettings(probe, "settings");
		JsonObject expected = GSON.toJsonTree(probe).getAsJsonObject();
		SafariConfig decoded = parseSettings(expected.deepCopy());
		JsonObject actual = GSON.toJsonTree(decoded).getAsJsonObject();
		if (!expected.equals(actual)) {
			throw new IllegalStateException("Settings persistence changed current values at "
				+ firstDifference(expected, actual, "settings"));
		}

		// Exercise the real update path: unversioned settings gain a schema marker,
		// while unknown top-level and nested data must survive untouched.
		JsonObject legacy = expected.deepCopy();
		legacy.remove("settingsSchemaVersion");
		legacy.addProperty("futureTopLevelSetting", "preserve-me");
		legacy.getAsJsonObject("display").addProperty("futureDisplaySetting", 73);
		SafariConfig migrated = parseSettings(legacy);
		JsonObject migratedKnown = GSON.toJsonTree(migrated).getAsJsonObject();
		if (!expected.equals(migratedKnown)) {
			throw new IllegalStateException("Legacy settings migration changed a value at "
				+ firstDifference(expected, migratedKnown, "settings"));
		}
		JsonObject merged = mergedSettingsJson(migrated, legacy);
		if (!"preserve-me".equals(merged.get("futureTopLevelSetting").getAsString())
				|| merged.getAsJsonObject("display").get("futureDisplaySetting").getAsInt() != 73) {
			throw new IllegalStateException("Unknown settings were not preserved across save");
		}
	}

	private static void exercisePersistedSettings(Object target, String path) {
		for (Field field : target.getClass().getFields()) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			boolean setting = field.isAnnotationPresent(SettingToggle.class)
				|| field.isAnnotationPresent(SettingChoice.class)
				|| field.isAnnotationPresent(SettingRange.class)
				|| field.isAnnotationPresent(SettingMultiChoice.class)
				|| field.isAnnotationPresent(SettingText.class)
				|| field.isAnnotationPresent(SettingColor.class);
			boolean exposed = field.isAnnotationPresent(com.google.gson.annotations.Expose.class);
			boolean sessionOnly = Modifier.isTransient(field.getModifiers());
			if (setting && !exposed && !sessionOnly) {
				throw new IllegalStateException("Settings control is not persisted: "
					+ path + "." + field.getName());
			}
			if (!exposed) continue;
			try {
				Object value = field.get(target);
				if (value != null && field.getType().getEnclosingClass() == SafariConfig.class) {
					exercisePersistedSettings(value, path + "." + field.getName());
					continue;
				}
				if (field.isAnnotationPresent(SettingToggle.class) && field.getType() == boolean.class) {
					field.setBoolean(target, !field.getBoolean(target));
				}
				SettingChoice choice = field.getAnnotation(SettingChoice.class);
				if (choice != null && field.getType() == int.class && choiceCount(field, choice) > 0) {
					field.setInt(target, choiceCount(field, choice) - 1);
				}
				SettingRange range = field.getAnnotation(SettingRange.class);
				if (range != null) setRangeProbe(target, field, range);
				SettingMultiChoice multi = field.getAnnotation(SettingMultiChoice.class);
				if (multi != null) setMaskProbe(target, field, multi);
				if (field.isAnnotationPresent(SettingText.class) && field.getType() == String.class) {
					field.set(target, "Persistence <CRITTER> <BIOME> Test");
				}
				if (field.isAnnotationPresent(SettingColor.class) && field.getType() == String.class) {
					field.set(target, "255:17:34:51:68");
				}
			} catch (IllegalAccessException impossible) {
				throw new IllegalStateException("Could not exercise settings field "
					+ path + "." + field.getName(), impossible);
			}
		}
	}

	private static void setRangeProbe(Object target, Field field, SettingRange range)
			throws IllegalAccessException {
		if (field.getType() == int.class) field.setInt(target, Math.round(range.maxValue()));
		else if (field.getType() == float.class) field.setFloat(target, range.maxValue());
		else if (field.getType() == double.class) field.setDouble(target, range.maxValue());
	}

	private static void setMaskProbe(Object target, Field field, SettingMultiChoice multi)
			throws IllegalAccessException {
		int[] bits = multi.bits();
		int count = multi.critters() ? dev.serko.safariutils.data.Critters.total()
			: bits.length > 0 ? bits.length : multi.values().length;
		long mask = 0L;
		for (int index = 0; index < count; index++) {
			int bit = bits.length > index ? bits[index] : index;
			if (bit >= 0 && bit < Long.SIZE) mask |= 1L << bit;
		}
		if (field.getType() == long.class) field.setLong(target, mask);
		else if (field.getType() == int.class) field.setInt(target, (int) mask);
	}

	private static String firstDifference(com.google.gson.JsonElement expected,
			com.google.gson.JsonElement actual, String path) {
		if (java.util.Objects.equals(expected, actual)) return path;
		if (expected == null || actual == null || expected.getClass() != actual.getClass()) return path;
		if (expected.isJsonObject()) {
			java.util.Set<String> keys = new java.util.TreeSet<>();
			keys.addAll(expected.getAsJsonObject().keySet());
			keys.addAll(actual.getAsJsonObject().keySet());
			for (String key : keys) {
				var left = expected.getAsJsonObject().get(key);
				var right = actual.getAsJsonObject().get(key);
				if (!java.util.Objects.equals(left, right)) {
					return firstDifference(left, right, path + "." + key);
				}
			}
		}
		if (expected.isJsonArray()) {
			int size = Math.max(expected.getAsJsonArray().size(), actual.getAsJsonArray().size());
			for (int index = 0; index < size; index++) {
				var left = index < expected.getAsJsonArray().size()
					? expected.getAsJsonArray().get(index) : null;
				var right = index < actual.getAsJsonArray().size()
					? actual.getAsJsonArray().get(index) : null;
				if (!java.util.Objects.equals(left, right)) {
					return firstDifference(left, right, path + "[" + index + "]");
				}
			}
		}
		return path;
	}

	private static void clampRange(Object target, java.lang.reflect.Field field, SettingRange range)
			throws IllegalAccessException {
		if (field.getType() == int.class) {
			field.setInt(target, Math.clamp(field.getInt(target),
				Math.round(range.minValue()), Math.round(range.maxValue())));
		} else if (field.getType() == float.class) {
			field.setFloat(target, Math.clamp(field.getFloat(target), range.minValue(), range.maxValue()));
		} else if (field.getType() == double.class) {
			field.setDouble(target, Math.clamp(field.getDouble(target),
				(double) range.minValue(), (double) range.maxValue()));
		}
	}

	private static void clampMask(Object target, java.lang.reflect.Field field, SettingMultiChoice multi)
			throws IllegalAccessException {
		int[] declaredBits = multi.bits();
		int count = multi.critters() ? dev.serko.safariutils.data.Critters.total()
			: declaredBits.length > 0 ? declaredBits.length : multi.values().length;
		if (count <= 0) return;
		long allowed = 0L;
		for (int index = 0; index < count; index++) {
			int bit = declaredBits.length > index ? declaredBits[index] : index;
			if (bit >= 0 && bit < Long.SIZE) allowed |= 1L << bit;
		}
		if (field.getType() == long.class) field.setLong(target, field.getLong(target) & allowed);
		else if (field.getType() == int.class) field.setInt(target,
			(int) (Integer.toUnsignedLong(field.getInt(target)) & allowed));
	}

	/** Restores defaults for explicit JSON nulls at every persisted config level. */
	private static void fillNullFields(Object target, Object defaults) {
		for (var field : target.getClass().getFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
				|| field.getAnnotation(com.google.gson.annotations.Expose.class) == null) continue;
			try {
				Object value = field.get(target);
				Object fallback = field.get(defaults);
				if (value == null) {
					field.set(target, fallback);
					continue;
				}
				if (field.getType().getEnclosingClass() == SafariConfig.class) {
					fillNullFields(value, fallback);
				}
			} catch (IllegalAccessException impossible) {
				throw new IllegalStateException("Could not normalize settings field " + field.getName(),
					impossible);
			}
		}
	}

	private static void normalizeSavedAlertPresets(SafariConfig.SparklingConfig sparkling) {
		sparkling.customAlertSavedPresets.removeIf(java.util.Objects::isNull);
		for (SafariConfig.SavedAlertPreset preset : sparkling.customAlertSavedPresets) {
			if (preset.name == null || preset.name.isBlank()) preset.name = "Custom Alert";
			if (preset.recipe == null) preset.recipe = "";
		}
	}

	/** Converts the former three inline names into reusable profiles without changing behavior. */
	private static void migrateTicketTradingProfiles(JsonObject root) {
		if (!root.has("sparkling") || !root.get("sparkling").isJsonObject()) return;
		JsonObject sparkling = root.getAsJsonObject("sparkling");
		if (sparkling.has("ticketTradingProfiles")) return;
		JsonArray profiles = new JsonArray();
		java.util.Set<String> added = new java.util.HashSet<>();
		for (int index = 1; index <= 3; index++) {
			String playerKey = "ticketTradingPlayer" + index;
			if (!sparkling.has(playerKey)) continue;
			String username = sparkling.get(playerKey).getAsString().trim();
			if (!username.matches("[A-Za-z0-9_]{1,16}")) continue;
			String normalized = username.toLowerCase(java.util.Locale.ROOT);
			if (added.add(normalized)) {
				JsonObject profile = new JsonObject();
				profile.addProperty("username", username);
				String sparklingKey = "ticketTradingSparkling" + index;
				profile.addProperty("sparklingOnly", sparkling.has(sparklingKey)
					&& sparkling.get(sparklingKey).getAsBoolean());
				profile.addProperty("sparklingCritters",
					dev.serko.safariutils.data.Critters.allSelectionMask());
				profiles.add(profile);
			}
			sparkling.addProperty("ticketTradingSlot" + index, username);
		}
		sparkling.add("ticketTradingProfiles", profiles);
	}

	/**
	 * Converts the former active/backup slots and combined Sparkling flag into
	 * independent host and guest roles. This must operate on JSON before model
	 * validation so loading current-schema settings is free of side effects.
	 */
	private static void migrateTicketTradingRoles(JsonObject root) {
		if (!root.has("sparkling") || !root.get("sparkling").isJsonObject()) return;
		JsonObject sparkling = root.getAsJsonObject("sparkling");
		int roleVersion = 0;
		if (sparkling.has("ticketTradingRolesVersion")) {
			try {
				roleVersion = Math.max(0, sparkling.get("ticketTradingRolesVersion").getAsInt());
			} catch (RuntimeException ignored) {
				roleVersion = 0;
			}
		}
		if (roleVersion >= 1) return;

		JsonArray queue = sparkling.has("ticketTradingHostQueue")
			&& sparkling.get("ticketTradingHostQueue").isJsonArray()
			? sparkling.getAsJsonArray("ticketTradingHostQueue") : new JsonArray();
		java.util.Set<String> assigned = new java.util.LinkedHashSet<>();
		JsonArray normalizedQueue = new JsonArray();
		for (var entry : queue) {
			if (!entry.isJsonPrimitive()) continue;
			String username = entry.getAsString().trim();
			if (username.matches("[A-Za-z0-9_]{1,16}")
					&& assigned.add(username.toLowerCase(java.util.Locale.ROOT))) {
				normalizedQueue.add(username);
			}
		}
		for (String key : new String[] {
			"ticketTradingSlot1", "ticketTradingSlot2", "ticketTradingSlot3",
			"ticketTradingBackupSlot1", "ticketTradingBackupSlot2", "ticketTradingBackupSlot3"
		}) {
			if (!sparkling.has(key) || !sparkling.get(key).isJsonPrimitive()) continue;
			String username = sparkling.get(key).getAsString().trim();
			if (username.matches("[A-Za-z0-9_]{1,16}")
					&& assigned.add(username.toLowerCase(java.util.Locale.ROOT))) {
				normalizedQueue.add(username);
			}
			sparkling.addProperty(key, "");
		}
		sparkling.add("ticketTradingHostQueue", normalizedQueue);

		if (sparkling.has("ticketTradingProfiles")
				&& sparkling.get("ticketTradingProfiles").isJsonArray()) {
			for (var entry : sparkling.getAsJsonArray("ticketTradingProfiles")) {
				if (!entry.isJsonObject()) continue;
				JsonObject profile = entry.getAsJsonObject();
				String username = profile.has("username") && profile.get("username").isJsonPrimitive()
					? profile.get("username").getAsString().trim() : "";
				boolean sparklingOnly = false;
				try {
					sparklingOnly = profile.has("sparklingOnly")
						&& profile.get("sparklingOnly").getAsBoolean();
				} catch (RuntimeException ignored) {
					// Invalid hand-edited values retain the safe ordinary-player behavior.
				}
				profile.addProperty("hostInviteMode", sparklingOnly ? "SPARKLING" : "EVERY_RUN");
				boolean active = assigned.contains(username.toLowerCase(java.util.Locale.ROOT));
				profile.addProperty("guestInviteMode", active
					? (sparklingOnly ? "SWITCH" : "JOIN") : "OFF");
			}
		}
		sparkling.addProperty("ticketTradingRolesVersion", 1);
	}

	/** Separates Guest activation from its remembered Join/Leave & Join behavior. */
	private static void migrateTicketTradingGuestMembership(JsonObject root) {
		if (!root.has("sparkling") || !root.get("sparkling").isJsonObject()) return;
		JsonObject sparkling = root.getAsJsonObject("sparkling");
		int roleVersion = 0;
		try {
			if (sparkling.has("ticketTradingRolesVersion")) {
				roleVersion = sparkling.get("ticketTradingRolesVersion").getAsInt();
			}
		} catch (RuntimeException ignored) {
			roleVersion = 0;
		}
		if (roleVersion >= TicketTradingProfiles.ROLE_SCHEMA
				&& sparkling.has("ticketTradingGuestPlayers")
				&& sparkling.get("ticketTradingGuestPlayers").isJsonArray()) return;
		JsonArray guests = new JsonArray();
		java.util.Set<String> added = new java.util.HashSet<>();
		if (sparkling.has("ticketTradingGuestPlayers")
				&& sparkling.get("ticketTradingGuestPlayers").isJsonArray()) {
			for (var entry : sparkling.getAsJsonArray("ticketTradingGuestPlayers")) {
				if (!entry.isJsonPrimitive()) continue;
				String username = entry.getAsString().trim();
				if (username.matches("[A-Za-z0-9_]{1,16}")
						&& added.add(username.toLowerCase(java.util.Locale.ROOT))) guests.add(username);
			}
		}
		if (sparkling.has("ticketTradingProfiles")
				&& sparkling.get("ticketTradingProfiles").isJsonArray()) {
			for (var entry : sparkling.getAsJsonArray("ticketTradingProfiles")) {
				if (!entry.isJsonObject()) continue;
				JsonObject profile = entry.getAsJsonObject();
				String username = profile.has("username") && profile.get("username").isJsonPrimitive()
					? profile.get("username").getAsString().trim() : "";
				String mode = profile.has("guestInviteMode") && profile.get("guestInviteMode").isJsonPrimitive()
					? profile.get("guestInviteMode").getAsString() : "OFF";
				if (!mode.equals("OFF") && username.matches("[A-Za-z0-9_]{1,16}")
						&& added.add(username.toLowerCase(java.util.Locale.ROOT))) guests.add(username);
				if (!mode.equals("SWITCH")) profile.addProperty("guestInviteMode", "JOIN");
			}
		}
		sparkling.add("ticketTradingGuestPlayers", guests);
		sparkling.addProperty("ticketTradingRolesVersion", TicketTradingProfiles.ROLE_SCHEMA);
	}

	/** Replaces the old master switch while preserving its disabled behavior once. */
	private static void migrateSafeModeOptions(JsonObject root) {
		if (!root.has("advanced") || !root.get("advanced").isJsonObject()) return;
		JsonObject advanced = root.getAsJsonObject("advanced");
		if (advanced.has("safeModeOptionsVersion")) return;
		boolean masterEnabled = !advanced.has("safeMode") || advanced.get("safeMode").getAsBoolean();
		if (!masterEnabled) {
			for (String option : new String[] {
				"safeVisibleCritterDetection", "safeHideNearbyCounts",
				"safeConservativeAvailability", "safeConservativeCompletion",
				"safeCritterHitboxes", "safeSparklingCritters", "safeHideyho",
				"safeHideonwall", "safeDuplico", "safeBloodbat", "safeHideonfloor",
				"safeFloorDrops", "safeBeeNests", "safeRockmiteMounds",
				"safeSnoozleWalls", "safeTroodonWalls"
			}) advanced.addProperty(option, false);
		}
		advanced.remove("safeMode");
		advanced.addProperty("safeModeOptionsVersion", 1);
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

	/** Security-sensitive automation remains opt-in for every Minecraft launch. */
	private static void resetSessionOptions(SafariConfig loaded) {
		loaded.sparkling.ticketTradingEnabled = false;
	}

	/** Writes atomically so an interrupted save cannot destroy a working config. */
	public static synchronized void save() {
		if (config == null) return;
		OperationalLog.settingsChanged();
		revision++;
		Path path = SafariPaths.settings();
		try {
			if (writesBlocked) {
				RECOVERY.write(SafariPaths.settingsBackups(), GSON.toJson(mergedSettingsJson(config)));
				OperationalLog.info("CONFIG/SAVE_PROTECTED",
					"Source retained; working settings saved to recovery snapshot");
				return;
			}
			if (pendingVersionBackup && Files.isRegularFile(path)) {
				backupBeforeVersionLoad(path);
				pendingVersionBackup = false;
			}
			JsonObject merged = mergedSettingsJson(config);
			AtomicFiles.writeString(path, GSON.toJson(merged));
			preservedSettings = merged;
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
			if (preservedSettings != null) preservedSettings.remove(categoryField);
			save();
		} catch (ReflectiveOperationException failed) {
			OperationalLog.error("CONFIG/RESET", failed);
		}
	}
}
