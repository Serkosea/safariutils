package dev.serko.safariutils.client;

import java.util.UUID;

/** Exact static-marker ownership, independent of Minecraft world state. */
public final class CaptureResolutionsRegression {
	private static int checks;
	public static int run() {
		checks = 0;
		var index = new CaptureResolutions<String, Integer>();
		UUID body = new UUID(0, 1), corrected = new UUID(0, 2);
		check(index.start(1, "hideon", body, 10, 0), "first attempt registered");
		check(index.start(2, "hideon", body, 20, 100), "overlapping provisional body retained by exact ID");
		check(!index.start(2, "hideon", corrected, 99, 900), "duplicate start cannot change owner or age");
		check(index.correct(2, "hideon", body, corrected, 30), "second attempt corrected directly");
		check(index.get(1, "hideon").id().equals(body), "correction does not pick oldest body match");
		check(index.get(2, "hideon").pos() == 30 && index.get(2, "hideon").millis() == 100,
			"correction updates location without resetting expiry");
		check(!index.correct(2, "hideon", body, corrected, 40), "stale old-body correction rejected");
		check(index.remove(2, "other") == null, "wrong species cannot remove attempt");
		check(index.remove(1, "hideon") != null && index.hasBody(corrected), "out-of-order outcome preserves peer");
		check(index.remove(1, "hideon") == null, "duplicate resolution has no effects");
		index.start(3, "hideon", corrected, 30, 200);
		index.remove(2, "hideon");
		check(index.hasBody(corrected), "old cleanup retains retry suppression");
		index.start(4, "other", body, 40, 0); // older timestamp inserted later
		check(index.expire(1001, 1000).size() == 1 && index.hasBody(corrected), "expiry examines every attempt independently");
		check(index.expire(1200, 1000).isEmpty(), "expiry retains exact boundary");
		check(index.removeSpecies("hideon").size() == 1 && !index.hasBody(corrected), "species exhaustion removes only matching owners");
		index.start(5, "other", null, 50, 2000);
		check(index.hasSpecies("other") && !index.hasBody(null), "position-only attempt supported");
		check(index.hasPosition("other", 50, Integer::equals), "position-only anchor matches its perch");
		check(!index.hasPosition("other", 60, Integer::equals), "same species does not match distant perch");
		check(!index.hasPosition("hideon", 50, Integer::equals), "same position does not match other species");
		check(!index.hasPosition("other", null, Integer::equals), "unknown candidate has no spatial evidence");
		index.start(6, "other", body, null, 2000);
		check(!index.hasPosition("other", 60, Integer::equals), "missing anchor does not match arbitrary perch");
		check(index.correct(5, "other", null, corrected, 55), "position-only anchor can be corrected");
		check(!index.hasPosition("other", 50, Integer::equals)
			&& index.hasPosition("other", 55, Integer::equals), "correction replaces spatial evidence");
		check(index.hasPosition("other", 56, (a, b) -> Math.abs(a - b) <= 1), "caller supplies existing spatial tolerance");
		index.remove(5, "other");
		check(!index.hasPosition("other", 55, Integer::equals), "resolved attempt releases spatial evidence");
		index.clear();
		check(!index.hasSpecies("other") && index.get(5, "other") == null, "visit reset clears registry");
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
		checks++;
	}
}
