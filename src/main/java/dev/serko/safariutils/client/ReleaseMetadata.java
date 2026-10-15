package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Validated release details, published as one immutable value. */
record ReleaseMetadata(String version, String title, String notes, String publishedAt) {
	static ReleaseMetadata parse(String json) {
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		String version = normalize(string(object, "tag_name"));
		if (numbers(version) == null) throw new IllegalArgumentException("Invalid release version");
		return new ReleaseMetadata(version, string(object, "name"), string(object, "body"),
			string(object, "published_at"));
	}

	boolean newerThan(String current) {
		int[] left = numbers(version), right = numbers(normalize(current));
		if (left == null || right == null) return false;
		for (int i = 0; i < left.length; i++) {
			if (left[i] != right[i]) return left[i] > right[i];
		}
		return false;
	}

	private static String string(JsonObject object, String key) {
		var value = object.get(key);
		if (value == null || value.isJsonNull()) return "";
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("Invalid release field: " + key);
		}
		return value.getAsString();
	}

	private static String normalize(String version) {
		String clean = version == null ? "" : version.trim();
		if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
		int build = clean.indexOf('+');
		if (build >= 0) clean = clean.substring(0, build);
		return clean.replaceFirst("-(?:extra|private|developer)$", "");
	}

	private static int[] numbers(String version) {
		String core = version.split("-", 2)[0];
		if (!core.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")) return null;
		String[] parts = core.split("\\.");
		int[] values = new int[3];
		try {
			for (int i = 0; i < parts.length; i++) values[i] = Integer.parseInt(parts[i]);
			return values;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
