package dev.serko.safariutils.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Exact wire values. Unlike settings migrations, backend responses never coerce strings. */
final class BackendValues {
	private BackendValues() { }

	static long integer(JsonObject source, String key) {
		JsonElement value = source.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException("Invalid numeric field: " + key);
		return value.getAsBigDecimal().longValueExact();
	}

	static int colour(JsonObject source, String key) {
		long value = integer(source, key);
		// Java emits signed ARGB; JavaScript emits unsigned ARGB. Both represent the same bits.
		if (value < Integer.MIN_VALUE || value > 0xffff_ffffL)
			throw new IllegalArgumentException("Invalid rank colour");
		return (int) value;
	}

	static boolean bool(JsonObject source, String key) {
		JsonElement value = source.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
			throw new IllegalArgumentException("Invalid boolean field: " + key);
		return value.getAsBoolean();
	}
}
