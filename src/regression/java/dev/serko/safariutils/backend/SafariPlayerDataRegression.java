package dev.serko.safariutils.backend;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class SafariPlayerDataRegression {
	public static int run() throws Exception {
		int checks = 0;
		var source = JsonParser.parseString("""
			{"sparkling":["bloodbat","GAZER"],"duplicates":7,"tickets":{"basic":10,"firstClass":2},"fetchedAt":1234}
			""").getAsJsonObject();
		var data = SafariPlayerData.decode(source);
		check(data.species().equals(Set.of("BLOODBAT", "GAZER")) && data.duplicates() == 7,
			"species and duplicate count survive typed decoding"); checks++;
		check(List.copyOf(data.tickets().keySet()).equals(List.of("Basic", "Economy", "Premium", "First Class"))
			&& data.tickets().get("Basic") == 10 && data.tickets().get("Premium") == 0,
			"ticket order and missing-zero defaults retained"); checks++;
		check(data.lookup("Example").fetchedAt() == 1234 && data.lookup("Example").username().equals("Example"),
			"lookup keeps source timestamp and canonical name"); checks++;
		source.getAsJsonArray("sparkling").add("OTHER");
		source.getAsJsonObject("tickets").addProperty("basic", 99);
		check(data.species().size() == 2 && data.tickets().get("Basic") == 10,
			"cached data cannot change through source JSON mutation"); checks++;
		try { data.tickets().put("Basic", 3L); throw new AssertionError("mutable tickets"); }
		catch (UnsupportedOperationException expected) { checks++; }
		try { data.species().clear(); throw new AssertionError("mutable species"); }
		catch (UnsupportedOperationException expected) { checks++; }
		var empty = SafariPlayerData.decode(JsonParser.parseString("{\"fetchedAt\":1}").getAsJsonObject());
		check(empty.species().isEmpty() && empty.duplicates() == 0 && empty.tickets().values().stream().allMatch(n -> n == 0),
			"never-owned collections retain zero defaults"); checks++;
		var repeated = SafariPlayerData.decode(JsonParser.parseString("{\"sparkling\":[\"bat\",\"BAT\"],\"duplicates\":9,\"fetchedAt\":1}").getAsJsonObject());
		check(repeated.duplicates() == 1, "legacy repeated discovery evidence keeps existing precedence"); checks++;
		for (String invalid : List.of("{\"tickets\":{\"basic\":1.5},\"fetchedAt\":1}",
			"{\"tickets\":{\"basic\":9223372036854775808},\"fetchedAt\":1}",
			"{\"sparkling\":[{}],\"fetchedAt\":1}", "{\"sparkling\":{},\"fetchedAt\":1}",
			"{\"duplicates\":2147483648,\"fetchedAt\":1}", "{\"fetchedAt\":1.5}", "{}")) {
			try { SafariPlayerData.decode(JsonParser.parseString(invalid).getAsJsonObject());
				throw new AssertionError("malformed collection accepted"); }
			catch (IOException expected) { checks++; }
		}
		return checks;
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
