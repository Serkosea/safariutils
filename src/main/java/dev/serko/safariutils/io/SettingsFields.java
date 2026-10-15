package dev.serko.safariutils.io;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Supplier;

/** Isolated decoding for public @Expose settings; callers retain the untouched source. */
public final class SettingsFields {
	private SettingsFields() { }

	public record Decoded<T>(T value, int rejected) { }

	public static <T> Decoded<T> decode(JsonObject source, Gson gson, Supplier<T> defaults) {
		T value = defaults.get();
		int rejected = 0;
		for (var field : value.getClass().getFields()) {
			Expose exposed = field.getAnnotation(Expose.class);
			if (exposed == null || !exposed.deserialize() || Modifier.isStatic(field.getModifiers())
					|| Modifier.isFinal(field.getModifiers()) || !source.has(field.getName())) continue;
			JsonElement encoded = source.get(field.getName());
			try {
				Object decoded;
				if (encoded.isJsonNull()) throw new IllegalArgumentException();
				if (field.getType() == int.class) {
					if (!encoded.isJsonPrimitive()) throw new IllegalArgumentException();
					decoded = encoded.getAsBigDecimal().intValueExact();
				} else if (field.getType() == boolean.class) {
					if (!encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive().isBoolean())
						throw new IllegalArgumentException();
					decoded = encoded.getAsBoolean();
				} else {
					if ((field.getType() == String.class || field.getType().isEnum())
							&& (!encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive().isString()))
						throw new IllegalArgumentException();
					validateNested(encoded, field.getGenericType(), gson, 0);
					decoded = gson.fromJson(encoded, field.getGenericType());
					if (decoded == null) throw new IllegalArgumentException();
				}
				field.set(value, decoded);
			} catch (IllegalAccessException error) {
				throw new IllegalStateException("Settings field is inaccessible", error);
			} catch (RuntimeException invalid) {
				// Never log field values: settings may contain credentials.
				rejected++;
			}
		}
		return new Decoded<>(value, rejected);
	}

	/** Nested collections must not silently lose malformed or future entries during sanitizing. */
	private static void validateNested(JsonElement encoded, Type type, Gson gson, int depth) {
		if (depth > 32 || encoded.isJsonNull()) throw new IllegalArgumentException();
		if (type instanceof ParameterizedType parameterized
				&& parameterized.getRawType() instanceof Class<?> raw
				&& Collection.class.isAssignableFrom(raw)) {
			if (!encoded.isJsonArray()) throw new IllegalArgumentException();
			for (JsonElement element : encoded.getAsJsonArray()) {
				validateNested(element, parameterized.getActualTypeArguments()[0], gson, depth + 1);
			}
			return;
		}
		if (!(type instanceof Class<?> raw)) return;
		if (raw == String.class || raw.isEnum()) {
			if (!encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive().isString()
					|| raw.isEnum() && gson.fromJson(encoded, raw) == null) throw new IllegalArgumentException();
			return;
		}
		if (raw == boolean.class || raw == Boolean.class) {
			if (!encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive().isBoolean())
				throw new IllegalArgumentException();
			return;
		}
		if (raw == int.class || raw == Integer.class) {
			if (!encoded.isJsonPrimitive()) throw new IllegalArgumentException();
			encoded.getAsBigDecimal().intValueExact();
			return;
		}
		var fields = java.util.Arrays.stream(raw.getFields()).filter(field -> {
			Expose expose = field.getAnnotation(Expose.class);
			return expose != null && expose.deserialize() && !Modifier.isStatic(field.getModifiers())
				&& !Modifier.isFinal(field.getModifiers());
		}).toList();
		if (fields.isEmpty()) return;
		if (!encoded.isJsonObject()) throw new IllegalArgumentException();
		JsonObject object = encoded.getAsJsonObject();
		var known = new HashSet<String>();
		for (var field : fields) {
			known.add(field.getName());
			if (object.has(field.getName())) validateNested(object.get(field.getName()), field.getGenericType(), gson, depth + 1);
		}
		// Root unknown fields are merged on save; list rows are replaced, not identity-merged.
		if (!known.containsAll(object.keySet())) throw new IllegalArgumentException();
	}
}
