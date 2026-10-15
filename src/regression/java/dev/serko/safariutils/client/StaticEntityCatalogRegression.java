package dev.serko.safariutils.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class StaticEntityCatalogRegression {
	public static int run() throws Exception {
		int checks = 0;
		var root = JsonParser.parseString("{\"schema\":9,\"positions\":{\"Hideonfloor\":[\"1,2,3\",null,\"4.5,5.5,6.5\"],\"Broken\":null,\"Future\":[\"7,8,9\"]},\"extra\":true}").getAsJsonObject();
		var before = root.deepCopy();
		var decoded = StaticEntityCatalog.decodeStore(root);
		check(decoded.rejected() == 2, "malformed entries counted independently"); checks++;
		check(decoded.data().positions.get("Hideonfloor").size() == 2, "valid neighbors retained"); checks++;
		check(decoded.data().positions.containsKey("Future"), "decoder retains unknown species before domain policy"); checks++;
		check(root.equals(before), "source unchanged"); checks++;
		for (String bad : java.util.List.of("NaN,2,3", "Infinity,2,3", "2147483648,2,3", "1.25,2,3", "1,2")) {
			var invalid = JsonParser.parseString("{\"positions\":{\"Hideonfloor\":[\"" + bad + "\"]}}").getAsJsonObject();
			check(StaticEntityCatalog.decodeStore(invalid).rejected() == 1, "invalid coordinate rejected"); checks++;
		}
		var future = StaticEntityCatalog.decodeStore(JsonParser.parseString("{\"schema\":99,\"positions\":[]}").getAsJsonObject());
		check(future.data().schema == 99 && future.rejected() == 1, "future schema retained for protection"); checks++;
		try (var stream = StaticEntityCatalog.class.getResourceAsStream("/assets/safariutils/static-entities.json")) {
			if (stream == null) throw new AssertionError("bundled catalog missing");
			var shipped = StaticEntityCatalog.decodeStore(JsonParser.parseReader(new InputStreamReader(stream,
				StandardCharsets.UTF_8)).getAsJsonObject());
			check(shipped.rejected() == 0 && !shipped.data().positions.isEmpty(), "actual bundled catalog decodes without loss"); checks++;
		}
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
