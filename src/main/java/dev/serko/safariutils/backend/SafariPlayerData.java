package dev.serko.safariutils.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.serko.safariutils.api.SparklingPlayerLookup;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validated Worker collection data; never synthesizes or exposes raw Hypixel profiles. */
record SafariPlayerData(Set<String> species, int duplicates, Map<String, Long> tickets, long fetchedAt) {
	SafariPlayerData {
		species = Set.copyOf(species);
		tickets = Collections.unmodifiableMap(new LinkedHashMap<>(tickets));
	}

	SparklingPlayerLookup lookup(String name) {
		return new SparklingPlayerLookup(name, species, duplicates, tickets, fetchedAt);
	}

	static SafariPlayerData decode(JsonObject player) throws IOException {
		try {
			Set<String> species = new LinkedHashSet<>();
			int discovered = 0;
			JsonElement encoded = player.get("sparkling");
			if (encoded != null && !encoded.isJsonNull()) {
				for (JsonElement entry : encoded.getAsJsonArray()) {
					if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
						throw new IllegalArgumentException("Invalid species identifier");
					species.add(entry.getAsString().toUpperCase(Locale.ROOT));
					discovered++;
				}
			}
			int duplicates = Math.toIntExact(count(player, "duplicates"));
			if (discovered > species.size()) duplicates = discovered - species.size();
			JsonElement ticketValue = player.get("tickets");
			JsonObject source = ticketValue == null || ticketValue.isJsonNull() ? null : ticketValue.getAsJsonObject();
			Map<String, Long> tickets = new LinkedHashMap<>();
			tickets.put("Basic", count(source, "basic"));
			tickets.put("Economy", count(source, "economy"));
			tickets.put("Premium", count(source, "premium"));
			tickets.put("First Class", count(source, "firstClass"));
			long fetchedAt = BackendValues.integer(player, "fetchedAt");
			if (fetchedAt < 0) throw new IllegalArgumentException("Invalid collection timestamp");
			return new SafariPlayerData(species, duplicates, tickets, fetchedAt);
		} catch (RuntimeException malformed) {
			throw new IOException("SafariUtils API returned malformed collection data", malformed);
		}
	}

	private static long count(JsonObject source, String key) {
		JsonElement value = source == null ? null : source.get(key);
		if (value == null || value.isJsonNull()) return 0L;
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException("Invalid collection count");
		return Math.max(0L, value.getAsBigDecimal().longValueExact());
	}
}
