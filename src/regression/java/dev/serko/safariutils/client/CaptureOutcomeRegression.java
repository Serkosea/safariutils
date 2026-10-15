package dev.serko.safariutils.client;

/** Deterministic replay of the selection policy used by CritterState. */
public final class CaptureOutcomeRegression {
	public static int run() {
		int checks = 0;
		var empty = new CaptureOutcomeSelection(100);
		checks += expect(empty.select(false, 10_000, 750), 0, "no invented attempt");
		var group = new CaptureOutcomeSelection(100);
		group.observe(1, 10, true, false, false);
		group.observe(2, 20, true, false, false);
		checks += expect(group.select(false, 849, 750), 0, "ambiguous catch waits");
		checks += expect(group.select(false, 850, 750), 1, "catch fallback boundary");
		checks += expect(group.select(true, 5_099, 5_000), 0, "breakout waits independently");
		checks += expect(group.select(true, 5_100, 5_000), 1, "breakout fallback boundary");
		var evidence = new CaptureOutcomeSelection(100);
		evidence.observe(1, 10, true, false, false);
		evidence.observe(2, 20, true, true, true);
		checks += expect(evidence.select(false, 100, 750), 1, "confirmed breakout excludes stale absence from catches");
		checks += expect(evidence.select(true, 100, 5_000), 2, "visible breakout beats throw order");
		var retry = new CaptureOutcomeSelection(100);
		retry.observe(1, 10, false, true, true);
		retry.observe(2, 101, true, true, true);
		checks += expect(retry.select(false, 10_000, 750), 0, "old notice cannot consume future retry");
		checks += expect(retry.select(true, 10_000, 5_000), 0, "resolved attempt cannot resolve twice");
		var single = new CaptureOutcomeSelection(100);
		single.observe(3, 100, true, false, false);
		checks += expect(single.select(false, 100, 750), 3, "sole catch resolves immediately");
		checks += expect(single.select(true, 100, 5_000), 0, "sole breakout still awaits body evidence");
		var unordered = new CaptureOutcomeSelection(100);
		unordered.observe(3, 30, true, true, false);
		unordered.observe(2, 20, true, true, false);
		unordered.observe(1, 10, true, false, false);
		checks += expect(unordered.select(false, 100, 750), 2, "oldest absent wins independent of insertion");
		var tied = new CaptureOutcomeSelection(100);
		tied.observe(5, 10, true, false, false);
		tied.observe(6, 10, true, false, false);
		checks += expect(tied.select(false, 850, 750), 5, "same-tick attempts keep stable order");
		checks += expect(tied.select(false, 99, 750), 0, "backward clock does not force fallback");
		return checks;
	}

	private static int expect(long actual, long expected, String reason) {
		if (actual != expected) throw new AssertionError(reason + ": " + actual + " != " + expected);
		return 1;
	}
}
