package dev.serko.safariutils.client;

import com.google.gson.JsonParser;

public final class PlayerIdentityStoreRegression {
	public static int run() {
		int checks = 0;
		String id = "00000000000000000000000000000001";
		String next = "00000000000000000000000000000002";
		var root = JsonParser.parseString("{\"players\":{\"" + id
			+ "\":{\"uuid\":null,\"name\":\"CachedPlayer\",\"permanentRankColour\":-11141121,\"permanentRankLevel\":4},"
			+ "\"broken\":null,\"" + next + "\":{\"name\":\"LaterPlayer\"}}}").getAsJsonObject();
		var original = root.deepCopy();
		var decoded = PlayerIdentityStore.decodeStore(root);
		check(decoded.entries().size() == 2 && decoded.rejected() == 1, "malformed row does not stop subsequent identities"); checks++;
		check(decoded.entries().get(id).permanentRankColour == 0xFF55FFFF
			&& decoded.entries().get(id).permanentRankLevel == 4, "null UUID fallback retains signed permanent rank"); checks++;
		check(root.equals(original), "decoder preserves raw source"); checks++;
		for (String invalid : java.util.List.of("1.5", "4294967295", "null", "[1]", "true", "{}")) {
			var input = JsonParser.parseString("{\"players\":{\"" + id + "\":{\"name\":\"Player\",\"rankColour\":"
				+ invalid + ",\"permanentRankColour\":-11141121,\"permanentRankLevel\":4}}}").getAsJsonObject();
			var result = PlayerIdentityStore.decodeStore(input);
			check(result.rejected() == 1 && result.entries().get(id).rankColour == -1
				&& result.entries().get(id).permanentRankColour == 0xFF55FFFF, "invalid current rank preserves permanent fallback"); checks++;
		}
		var invalidPlayers = PlayerIdentityStore.decodeStore(JsonParser.parseString("{\"players\":[]}").getAsJsonObject());
		check(invalidPlayers.rejected() == 1, "invalid container protects source"); checks++;
		var duplicate = JsonParser.parseString("{\"players\":{\"old\":{\"uuid\":\"" + id
			+ "\",\"name\":\"OldName\",\"updatedAt\":1},\"new\":{\"uuid\":\"" + id
			+ "\",\"name\":\"NewName\",\"updatedAt\":2}}}").getAsJsonObject();
		var merged = PlayerIdentityStore.decodeStore(duplicate);
		check(merged.rejected() == 1 && merged.entries().size() == 1
			&& merged.entries().get(id).name.equals("NewName"), "duplicate UUID prefers newest while protecting source"); checks++;
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
