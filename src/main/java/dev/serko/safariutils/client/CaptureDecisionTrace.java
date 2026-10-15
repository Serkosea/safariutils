package dev.serko.safariutils.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Versioned outcome-policy evidence; contains no names, UUIDs, credentials or chat text. */
final class CaptureDecisionTrace {
	static JsonObject encode(CaptureOutcomeSelection selection, boolean breakout, boolean direct,
			long now, long fallback, long chosen) {
		JsonObject json = new JsonObject();
		json.addProperty("k", "co");
		json.addProperty("schema", 2);
		json.addProperty("clock", "process-monotonic-ms");
		json.addProperty("observed", selection.observedAt());
		json.addProperty("evaluated", now);
		json.addProperty("breakout", breakout);
		json.addProperty("direct", direct);
		json.addProperty("fallback", fallback);
		json.addProperty("chosen", chosen);
		JsonArray candidates = new JsonArray();
		for (var candidate : selection.candidates()) {
			JsonArray row = new JsonArray();
			row.add(candidate.id());
			row.add(candidate.startedAt());
			row.add(candidate.absent());
			row.add(candidate.brokeOut());
			candidates.add(row);
		}
		json.add("candidates", candidates);
		return json;
	}

	static boolean matches(JsonObject json) {
		int schema = json.get("schema").getAsInt();
		if (schema != 1 && schema != 2) return false;
		var selection = new CaptureOutcomeSelection(json.get("observed").getAsLong(), false, schema >= 2);
		for (var value : json.getAsJsonArray("candidates")) {
			JsonArray row = value.getAsJsonArray();
			selection.observe(row.get(0).getAsLong(), row.get(1).getAsLong(), true,
				row.get(2).getAsBoolean(), row.get(3).getAsBoolean());
		}
		long chosen = json.get("direct").getAsBoolean() ? selection.directBreakout()
			: selection.select(json.get("breakout").getAsBoolean(),
				json.get("evaluated").getAsLong(), json.get("fallback").getAsLong());
		return chosen == json.get("chosen").getAsLong();
	}
}
