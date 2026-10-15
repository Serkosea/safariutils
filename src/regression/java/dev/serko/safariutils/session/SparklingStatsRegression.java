package dev.serko.safariutils.session;

import com.google.gson.JsonParser;

/** Production decoder fixtures; never loads or changes the installed collection. */
public final class SparklingStatsRegression {
	public static int run() {
		int checks = 0;
		var legacy = decode("{\"species\":{\"Cavernfish\":3,\"FutureSpecies\":2},\"rainbowFeathers\":7}");
		check(legacy.rejected() == 0 && legacy.data().species.get("Cavernfish") == 3, "legacy counters survive"); checks++;
		check(legacy.data().species.get("FutureSpecies") == 2, "unknown species counter retained"); checks++;
		check(legacy.data().rainbowFeathers == 7 && legacy.data().importedDuplicates == -1, "missing fields keep defaults"); checks++;
		for (String invalid : java.util.List.of("null", "true", "{}", "[1]", "1.5", "-1", "2147483648", "\"bad\"")) {
			var result = decode("{\"species\":{\"Good\":4,\"Bad\":" + invalid + "}}");
			check(result.rejected() == 1 && result.data().species.size() == 1
				&& result.data().species.get("Good") == 4, "bad entry cannot discard its readable neighbor"); checks++;
		}
		var fields = decode("{\"rainbowFeathers\":1.5,\"importedDuplicates\":-1,\"apiComparisonNotBefore\":9223372036854775808}");
		check(fields.rejected() == 2 && fields.data().importedDuplicates == -1, "scalar validation and sentinel"); checks++;
		var numericString = decode("{\"species\":{\"Good\":\"5\"},\"apiComparisonNotBefore\":9223372036854775807}");
		check(numericString.rejected() == 0 && numericString.data().species.get("Good") == 5
			&& numericString.data().apiComparisonNotBefore == Long.MAX_VALUE, "exact numeric legacy strings and long boundary"); checks++;
		var root = JsonParser.parseString("{\"schemaVersion\":999,\"species\":null,\"future\":{\"x\":7}}").getAsJsonObject();
		var before = root.deepCopy();
		check(SparklingStats.decodeStore(root).rejected() == 1 && root.equals(before), "decoder never mutates source including future fields"); checks++;
		return checks;
	}
	private static SparklingStats.Decoded decode(String json) {
		return SparklingStats.decodeStore(JsonParser.parseString(json).getAsJsonObject());
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
