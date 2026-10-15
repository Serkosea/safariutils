package dev.serko.safariutils.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Arrays;

/** Bounded pre-mutation assignment inputs; never serializes entities or player information. */
final class CapturePairingTrace {
	private static final int MAX_DIMENSION = 64;

	static JsonObject encode(String stage, double[][] distances, double maximum, double unmatched,
			int[] assignment) {
		if (distances.length > MAX_DIMENSION) return null;
		JsonArray rows = new JsonArray();
		for (double[] row : distances) {
			if (row.length > MAX_DIMENSION) return null;
			JsonArray values = new JsonArray();
			for (double distance : row) {
				if (Double.isFinite(distance)) values.add(distance);
				else values.add(JsonNull.INSTANCE);
			}
			rows.add(values);
		}
		JsonObject json = new JsonObject();
		json.addProperty("k", "cp");
		json.addProperty("schema", 1);
		json.addProperty("stage", stage);
		json.add("costs", rows);
		if (Double.isFinite(maximum)) json.addProperty("maximum", maximum);
		else json.add("maximum", JsonNull.INSTANCE);
		json.addProperty("unmatched", unmatched);
		JsonArray result = new JsonArray();
		for (int index : assignment) result.add(index);
		json.add("result", result);
		return json;
	}

	/** Optional context maps matrix axes back to critter bodies, never player identities. */
	static void addIdentities(JsonObject json, java.util.List<String> bodies, java.util.List<Long> logicals) {
		JsonArray bodyIds = new JsonArray(), logicalIds = new JsonArray();
		bodies.forEach(bodyIds::add);
		logicals.forEach(logicalIds::add);
		json.add("bodies", bodyIds);
		json.add("logicals", logicalIds);
	}

	static boolean matches(JsonObject json) {
		if (integer(json.get("schema")) != 1) return false;
		JsonArray rows = json.getAsJsonArray("costs");
		if (rows.size() > MAX_DIMENSION) throw new IllegalArgumentException("Pairing trace too large");
		double[][] costs = new double[rows.size()][];
		for (int row = 0; row < costs.length; row++) {
			JsonArray values = rows.get(row).getAsJsonArray();
			if (values.size() > MAX_DIMENSION) throw new IllegalArgumentException("Pairing trace too large");
			costs[row] = new double[values.size()];
			for (int column = 0; column < values.size(); column++) {
				costs[row][column] = values.get(column).isJsonNull()
					? Double.POSITIVE_INFINITY : number(values.get(column));
			}
		}
		if (json.has("bodies") || json.has("logicals")) {
			JsonArray bodies = json.getAsJsonArray("bodies"), logicals = json.getAsJsonArray("logicals");
			if (bodies == null || logicals == null || bodies.size() != costs.length
					|| costs.length > 0 && logicals.size() != costs[0].length) {
				throw new IllegalArgumentException("Pairing identity dimensions");
			}
			var seenBodies = new java.util.HashSet<java.util.UUID>();
			for (var body : bodies) {
				if (!seenBodies.add(java.util.UUID.fromString(body.getAsString()))) {
					throw new IllegalArgumentException("Repeated pairing body");
				}
			}
			var seenLogicals = new java.util.HashSet<Long>();
			for (var logical : logicals) {
				if (!logical.isJsonPrimitive() || !logical.getAsJsonPrimitive().isNumber()) {
					throw new IllegalArgumentException("Expected logical identifier");
				}
				long id = logical.getAsBigDecimal().longValueExact();
				if (id <= 0 || !seenLogicals.add(id)) throw new IllegalArgumentException("Invalid logical identifier");
			}
		}
		double maximum = json.get("maximum").isJsonNull() ? Double.POSITIVE_INFINITY : number(json.get("maximum"));
		int[] actual = CapturePairing.match(costs, maximum, number(json.get("unmatched")));
		JsonArray recorded = json.getAsJsonArray("result");
		if (recorded.size() != actual.length) return false;
		int[] expected = new int[recorded.size()];
		for (int row = 0; row < expected.length; row++) expected[row] = integer(recorded.get(row));
		return Arrays.equals(actual, expected);
	}

	private static int integer(com.google.gson.JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
		return value.getAsBigDecimal().intValueExact();
	}
	private static double number(com.google.gson.JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected number");
		double number = value.getAsDouble();
		if (!Double.isFinite(number)) throw new IllegalArgumentException("Nonfinite number");
		return number;
	}
	private CapturePairingTrace() { }
}
