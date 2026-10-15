package dev.serko.safariutils.backend;

import com.google.gson.JsonParser;
import java.util.List;

public final class BackendValuesRegression {
	public static int run() {
		int checks = 0;
		for (long colour : new long[]{0xff55ffffL, (int) 0xff55ffffL}) {
			var source = JsonParser.parseString("{\"rankColour\":" + colour
				+ ",\"permanentRankColour\":" + colour
				+ ",\"permanentRankLevel\":4,\"temporaryRank\":false,\"rankFetchedAt\":123}").getAsJsonObject();
			var rank = HypixelApiClient.RankSnapshot.decode(source);
			check(rank.colour() == 0xff55ffff && rank.permanentColour() == rank.colour()
				&& rank.permanentLevel() == 4 && !rank.temporary() && rank.fetchedAt() == 123, "ARGB forms agree");
			checks++;
		}
		var legacy = JsonParser.parseString("{\"rankColour\":4294945280,\"rankFetchedAt\":1}").getAsJsonObject();
		var rank = HypixelApiClient.RankSnapshot.decode(legacy);
		check(rank.temporary() && rank.permanentColour() == -1 && rank.permanentLevel() == -1,
			"legacy temporary rank never invents a permanent rank"); checks++;
		for (String field : List.of("\"rankColour\":1.5", "\"rankColour\":4294967296",
			"\"rankColour\":-2147483649", "\"rankColour\":\"42\"", "\"rankColour\":null",
			"\"permanentRankLevel\":5", "\"permanentRankLevel\":-2", "\"permanentRankLevel\":1.5",
			"\"temporaryRank\":\"false\"", "\"temporaryRank\":1", "\"rankFetchedAt\":-1",
			"\"rankFetchedAt\":1.5", "\"rankFetchedAt\":9223372036854775808")) {
			var source = legacy.deepCopy();
			var invalid = JsonParser.parseString("{" + field + "}").getAsJsonObject();
			invalid.entrySet().forEach(entry -> source.add(entry.getKey(), entry.getValue()));
			try { HypixelApiClient.RankSnapshot.decode(source); throw new AssertionError("Accepted " + field); }
			catch (IllegalArgumentException | ArithmeticException expected) { checks++; }
		}
		for (String value : List.of("1.5", "\"1\"", "null", "true", "9223372036854775808")) {
			try { BackendValues.integer(JsonParser.parseString("{\"schema\":" + value + "}").getAsJsonObject(), "schema");
				throw new AssertionError("Invalid schema accepted"); }
			catch (IllegalArgumentException | ArithmeticException expected) { checks++; }
		}
		return checks;
	}
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
