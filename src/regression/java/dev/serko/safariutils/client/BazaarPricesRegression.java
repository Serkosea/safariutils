package dev.serko.safariutils.client;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class BazaarPricesRegression {
	public static int run() throws Exception {
		var prices = parse("{\"success\":true,\"products\":{\"IGNORED\":{\"other\":[1,2,3]},"
			+ "\"ESSENCE_SAFARI\":{\"sell_summary\":[{\"pricePerUnit\":3},{\"pricePerUnit\":5}],"
			+ "\"buy_summary\":[{\"pricePerUnit\":9},{\"pricePerUnit\":7}]}}}");
		check(prices.size() == 1, "unrelated products skipped");
		check(prices.get("ESSENCE_SAFARI").instantSell() == 5, "highest buy order retained");
		check(prices.get("ESSENCE_SAFARI").sellOffer() == 7, "lowest sell offer retained");
		var empty = parse("{\"products\":{\"RAINBOW_FEATHER\":{\"sell_summary\":[],\"buy_summary\":[]}}}");
		check(empty.get("RAINBOW_FEATHER").instantSell() == 0, "empty order book remains zero");
		for (String bad : new String[]{"{\"success\":false,\"products\":{}}", "{\"products\":{", "{} {}",
			"{\"products\":{\"ESSENCE_SAFARI\":{\"sell_summary\":[{\"pricePerUnit\":\"NaN\"}]}}}",
			"{\"products\":{\"ESSENCE_SAFARI\":{\"sell_summary\":[{\"pricePerUnit\":1e999}]}}}"}) {
			boolean rejected = false;
			try { parse(bad); } catch (Exception expected) { rejected = true; }
			check(rejected, "bad response cannot become a price update");
		}
		return 9;
	}
	private static java.util.Map<String, BazaarPrices.Price> parse(String json) throws Exception {
		return BazaarPrices.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
