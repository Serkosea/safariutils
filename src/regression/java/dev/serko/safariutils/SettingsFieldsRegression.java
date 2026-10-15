package dev.serko.safariutils;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import dev.serko.safariutils.io.SettingsFields;

public final class SettingsFieldsRegression {
	public enum Choice { FIRST, SECOND }
	public static final class Entry {
		@Expose public String name = "entry";
		@Expose public Choice choice = Choice.FIRST;
	}
	public static final class Settings {
		@Expose public int count = 12;
		@Expose public boolean enabled = true;
		@Expose public String name = "default";
		@Expose public Choice choice = Choice.FIRST;
		@Expose public java.util.List<Entry> entries = new java.util.ArrayList<>();
		public String transientValue = "session";
	}

	static int run() {
		var gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		var source = JsonParser.parseString("{\"count\":{},\"enabled\":false,\"name\":\"saved\",\"choice\":\"SECOND\",\"future\":42}").getAsJsonObject();
		String before = source.toString();
		var result = SettingsFields.decode(source, gson, Settings::new);
		check(result.rejected() == 1 && result.value().count == 12, "bad field isolated");
		check(!result.value().enabled && result.value().name.equals("saved") && result.value().choice == Choice.SECOND, "neighbors survive");
		check(source.toString().equals(before), "source preserved including unknown fields");
		int checks = 3;
		for (String invalid : new String[] {"1.5", "2147483648", "[1]", "null", "true"}) {
			var decoded = SettingsFields.decode(JsonParser.parseString("{\"count\":" + invalid + "}").getAsJsonObject(), gson, Settings::new);
			check(decoded.rejected() == 1 && decoded.value().count == 12, "invalid integer rejected"); checks++;
		}
		var legacy = SettingsFields.decode(JsonParser.parseString("{\"count\":\"25\"}").getAsJsonObject(), gson, Settings::new);
		check(legacy.rejected() == 0 && legacy.value().count == 25, "legacy numeric string accepted"); checks++;
		for (String invalid : new String[] {"{\"enabled\":\"false\"}", "{\"choice\":\"FUTURE\"}", "{\"name\":123}"}) {
			check(SettingsFields.decode(JsonParser.parseString(invalid).getAsJsonObject(), gson, Settings::new).rejected() == 1, "coercion cannot silently replace setting"); checks++;
		}
		var ignored = SettingsFields.decode(JsonParser.parseString("{\"transientValue\":\"old\"}").getAsJsonObject(), gson, Settings::new);
		check(ignored.rejected() == 0 && ignored.value().transientValue.equals("session"), "unexposed state ignored"); checks++;
		check(SettingsFields.decode(new com.google.gson.JsonObject(), gson, Settings::new).rejected() == 0, "missing settings use defaults"); checks++;
		for (String invalid : new String[] {"null", "{\"choice\":\"FUTURE\"}",
			"{\"name\":123}", "{\"name\":\"saved\",\"future\":true}"}) {
			var nested = SettingsFields.decode(JsonParser.parseString("{\"enabled\":false,\"entries\":[" + invalid + "]}")
				.getAsJsonObject(), gson, Settings::new);
			check(nested.rejected() == 1 && !nested.value().enabled && nested.value().entries.isEmpty(),
				"unsupported nested data protects source without discarding neighboring settings"); checks++;
		}
		var nested = SettingsFields.decode(JsonParser.parseString("{\"entries\":[{\"name\":\"saved\",\"choice\":\"SECOND\"},{}]}")
			.getAsJsonObject(), gson, Settings::new);
		check(nested.rejected() == 0 && nested.value().entries.size() == 2
			&& nested.value().entries.getFirst().choice == Choice.SECOND
			&& nested.value().entries.getLast().choice == Choice.FIRST,
			"valid nested settings and missing-field defaults survive"); checks++;
		return checks;
	}

	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
