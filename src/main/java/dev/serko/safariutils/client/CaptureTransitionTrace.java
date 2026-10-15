package dev.serko.safariutils.client;

import com.google.gson.JsonObject;

/** Terminal policy inputs captured before mutation; IDs are local to the diagnostic session. */
final class CaptureTransitionTrace {
	static JsonObject encode(CaptureLifecycle.Input input, long logicalId,
			CaptureLifecycle.Resolution result) {
		JsonObject json = new JsonObject();
		json.addProperty("k", "ct");
		json.addProperty("schema", 1);
		json.addProperty("attempt", input.attemptId());
		json.addProperty("logical", logicalId);
		json.addProperty("owner", input.owner());
		json.addProperty("pending", input.pending());
		json.addProperty("terminal", input.terminal());
		json.addProperty("exists", input.logicalExists());
		json.addProperty("alreadyCaught", input.alreadyCaught());
		json.addProperty("caught", input.caught());
		json.addProperty("reappeared", input.reappeared());
		json.addProperty("result", result.name());
		return json;
	}

	static boolean matches(JsonObject json) {
		if (integer(json, "schema") != 1) return false;
		var input = new CaptureLifecycle.Input(flag(json, "pending"), flag(json, "terminal"),
			integer(json, "attempt"), flag(json, "exists"), integer(json, "owner"),
			flag(json, "alreadyCaught"), flag(json, "caught"), flag(json, "reappeared"));
		integer(json, "logical");
		return CaptureLifecycle.resolve(input).name().equals(json.get("result").getAsString());
	}

	private static long integer(JsonObject json, String key) {
		var value = json.getAsJsonPrimitive(key);
		if (!value.isNumber()) throw new IllegalArgumentException("Expected numeric " + key);
		return value.getAsBigDecimal().longValueExact();
	}

	private static boolean flag(JsonObject json, String key) {
		var value = json.getAsJsonPrimitive(key);
		if (!value.isBoolean()) throw new IllegalArgumentException("Expected boolean " + key);
		return value.getAsBoolean();
	}

	private CaptureTransitionTrace() { }
}
