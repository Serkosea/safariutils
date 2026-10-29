package dev.serko.safariutils.client;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

public final class SettingsTransferRegression {
	public static int run() throws Exception {
		int checks = 0;
        for (boolean enabled : new boolean[] {true, false}) {
            var root = new com.google.gson.JsonObject(); root.addProperty("settingsSchemaVersion", 8);
            var display = new com.google.gson.JsonObject(); root.add("display", display);
            display.addProperty("floorDrops", enabled);
            var migrated = ConfigManager.validateImported(root);
            check(migrated.display.floorDropLoot == enabled && migrated.display.floorDropQuestItems == enabled,
                "floor toggle preference lost during split"); checks++;
            display.addProperty("floorDropQuestItems", !enabled);
            check(ConfigManager.validateImported(root).display.floorDropQuestItems == !enabled,
                "explicit split preference overwritten"); checks++;
        }
		var markers = SafariConfig.SparklingConfig.class.getField("sparklingAlwaysActiveWaypoints")
			.getAnnotation(SettingMultiChoice.class);
		check(java.util.Arrays.equals(markers.bits(), new int[] {0, 9, 14, 15, 1, 2, 8, 3, 10, 4, 5, 12, 13, 6, 11, 16, 17, 7}),
			"pedestal insertion changed saved objective bits"); checks++;
		for (int mask : new int[] {0, 1, 128, 255, 256, 511}) {
			var imported = ConfigManager.validateImported(com.google.gson.JsonParser.parseString(
				"{\"sparkling\":{\"sparklingAlwaysActiveWaypoints\":" + mask + "}}") .getAsJsonObject());
			int expanded = mask | ((mask & 1) != 0 ? 1 << 9 : 0)
                | ((mask & 8) != 0 ? 1 << 10 : 0) | ((mask & 64) != 0 ? 1 << 11 : 0)
                | ((mask & 32) != 0 ? (1 << 12) | (1 << 13) : 0);
            if ((expanded & (1 << 9)) != 0) expanded |= (1 << 14) | (1 << 15);
            if ((expanded & (1 << 11)) != 0) expanded |= (1 << 16) | (1 << 17);
            check(imported.sparkling.sparklingAlwaysActiveWaypoints == expanded, "objective marker selection lost on import"); checks++;
		}
        for (int choices = 0; choices < 32; choices++) {
            var root = new com.google.gson.JsonObject();
            root.addProperty("settingsSchemaVersion", 7);
            var advanced = new com.google.gson.JsonObject(); root.add("advanced", advanced);
            String[] fields = {"safeFloorDrops", "safeBeeNests", "safeRockmiteMounds", "safeSnoozleWalls", "safeTroodonWalls"};
            for (int i = 0; i < fields.length; i++) advanced.addProperty(fields[i], (choices & (1 << i)) != 0);
            advanced.addProperty("safeFloorDropContents", true); advanced.addProperty("safeScrappyFood", true);
            var migrated = ConfigManager.validateImported(root);
            check(migrated.advanced.safeWorldObjectives == (choices != 0), "merged restrictions lost an enabled choice"); checks++;
            check(!migrated.advanced.safePredictedInformation, "new prediction control did not default off"); checks++;
        }
        for (int bit : new int[] {9, 11, 14, 15, 16, 17}) {
            var root = new com.google.gson.JsonObject(); root.addProperty("settingsSchemaVersion", ConfigManager.CURRENT_SETTINGS_SCHEMA);
            var sparkling = new com.google.gson.JsonObject(); root.add("sparkling", sparkling);
            sparkling.addProperty("sparklingAlwaysActiveWaypoints", 1 << bit);
            check(ConfigManager.validateImported(root).sparkling.sparklingAlwaysActiveWaypoints == (1 << bit),
                "current-schema item selection expanded unexpectedly"); checks++;
        }
		for (String format : new String[] {"2.5", "\"2\"", "true", "null", "[]", "{}", "4294967298", "3"}) {
			check(!SettingsTransfer.preview(encode("{\"f\":" + format + ",\"s\":{}}", false)).valid(),
				"invalid format rejected: " + format);
			checks++;
		}
		check(!SettingsTransfer.preview("SU2-invalid!").valid(), "invalid base64 rejected"); checks++;
		check(!SettingsTransfer.preview(encode("not json", false)).valid(), "invalid JSON rejected"); checks++;
		check(!SettingsTransfer.preview(encode("{\"format\":1.5,\"settings\":{}}", true)).valid(),
			"legacy fractional format rejected"); checks++;
		check(!SettingsTransfer.preview(encode(" ".repeat(4_000_001), false)).valid(),
			"compressed expansion bounded"); checks++;
		check(!SettingsTransfer.preview("SU2-" + "a".repeat(2_004_097)).valid(), "input bounded"); checks++;
		check(!SettingsTransfer.apply(null).success(), "missing preview cannot import"); checks++;
		return checks;
	}
	private static String encode(String text, boolean legacy) throws Exception {
		var bytes = new ByteArrayOutputStream();
		var deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
		try {
			try (var stream = legacy ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes, deflater)) {
				stream.write(text.getBytes(StandardCharsets.UTF_8));
			}
			return (legacy ? "SU1-" : "SU2-") + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} finally { deflater.end(); }
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
