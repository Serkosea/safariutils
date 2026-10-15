package dev.serko.safariutils.io;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared safety primitives for versioned JSON stores. */
public final class PersistentJson {
	private PersistentJson() { }

	/** Recursively overlays known fields while retaining unknown future fields. */
	public static JsonObject merge(JsonObject source, JsonObject known) {
		JsonObject merged = source == null ? new JsonObject() : source.deepCopy();
		for (var entry : known.entrySet()) {
			if (entry.getValue().isJsonObject() && merged.has(entry.getKey())
					&& merged.get(entry.getKey()).isJsonObject()) {
				merged.add(entry.getKey(), merge(merged.getAsJsonObject(entry.getKey()),
					entry.getValue().getAsJsonObject()));
			} else {
				merged.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		return merged;
	}

	/** Replaces known top-level values while retaining unknown record fields. */
	public static JsonObject mergeRecord(JsonObject source, JsonObject known) {
		JsonObject merged = source == null ? new JsonObject() : source.deepCopy();
		for (var entry : known.entrySet()) {
			merged.add(entry.getKey(), entry.getValue().deepCopy());
		}
		return merged;
	}

	/** Missing means legacy; unrecognized versions are read-only, never silently migrated. */
	public static int schema(JsonObject root, String key) {
		if (root == null || !root.has(key)) return 0;
		try {
			if (!root.get(key).isJsonPrimitive()) return Integer.MAX_VALUE;
			int schema = root.get(key).getAsBigDecimal().intValueExact();
			return schema < 0 ? Integer.MAX_VALUE : schema;
		} catch (RuntimeException invalid) {
			return Integer.MAX_VALUE;
		}
	}

	/** Imports must not normalize a document whose schema this build cannot understand. */
	public static void requireSupportedSchema(JsonObject root, String key, int supported) {
		if (schema(root, key) > supported) {
			throw new IllegalArgumentException("Settings use an unsupported or invalid schema version");
		}
	}

	/** Keeps one immutable source copy before a schema upgrade. */
	public static Path backupBeforeUpgrade(Path source, Path directory, String stem,
			int targetSchema) throws IOException {
		if (source == null || !Files.isRegularFile(source)) return null;
		Path backup = directory.resolve(stem + "-before-schema-" + targetSchema + ".json");
		AtomicFiles.copyOnce(source, backup);
		return backup;
	}

	/**
	 * Keeps one immutable copy of user data before each SafariUtils release can
	 * normalize or rewrite it. The copy is created once and never replaced.
	 */
	public static Path backupBeforeVersion(Path source, Path directory, String stem,
			String version) throws IOException {
		if (source == null || !Files.isRegularFile(source)) return null;
		String safeVersion = version == null ? "unknown"
			: version.replaceAll("[^A-Za-z0-9._-]", "_");
		Path backup = directory.resolve(stem + "-before-v" + safeVersion + ".json");
		AtomicFiles.copyOnce(source, backup);
		return backup;
	}
}
