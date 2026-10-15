package dev.serko.safariutils.client;

public final class DiagnosticsRegression {
	public static int run() {
		int checks = 0;
		var repeats = new DiagnosticRepeats(2, 10);
		checks += check(repeats.accept("a", "first", 0).emit(), "first diagnostic emitted");
		checks += check(!repeats.accept("a", "first", 1).emit(), "repeat coalesced");
		repeats.accept("a", "second", 2);
		var eviction = repeats.accept("a", "third", 3);
		checks += check(eviction.summaries().size() == 1 && eviction.summaries().getFirst().count() == 1,
			"eviction preserves repeat count");
		for (int i = 0; i < 10_000; i++) repeats.accept("unique", Integer.toString(i), 4);
		checks += check(repeats.size() == 2, "unique-message burst cannot exceed bound");
		repeats.clear();
		repeats.accept("a", "repeat", 10);
		repeats.accept("a", "repeat", 11);
		checks += check(repeats.expire(21).isEmpty(), "inclusive repeat window");
		checks += check(repeats.expire(22).getFirst().count() == 1 && repeats.size() == 0, "expiry summarizes and releases");
		repeats.accept("a", "clock", 100);
		checks += check(repeats.accept("a", "clock", 90).emit(), "backward clock cannot suppress indefinitely");
		var selection = new CaptureOutcomeSelection(100, true);
		selection.observe(1, 10, true, false, false);
		selection.observe(2, 20, true, true, true);
		selection.observe(3, 101, true, true, true);
		selection.observe(4, 0, false, true, true);
		checks += check(selection.candidates().size() == 2, "only eligible decision inputs logged");
		var trace = CaptureDecisionTrace.encode(selection, false, false, 100, 750, 1);
		checks += check(CaptureDecisionTrace.matches(trace), "catch decision replays");
		checks += check(CaptureDecisionTrace.matches(CaptureDecisionTrace.encode(selection, true, true, 100, 0, 2)),
			"direct breakout decision replays");
		trace.addProperty("schema", 1);
		trace.addProperty("chosen", 2);
		checks += check(CaptureDecisionTrace.matches(trace), "legacy policy trace remains replayable");
		trace.addProperty("schema", 2);
		checks += check(!CaptureDecisionTrace.matches(trace), "wrong selected attempt is detected");
		trace.addProperty("schema", 999);
		checks += check(!CaptureDecisionTrace.matches(trace), "future schema not silently accepted");
		checks += check(new CaptureOutcomeSelection(100).candidates() == null, "normal selection does not allocate trace list");
		return checks;
	}

	private static int check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
		return 1;
	}
}
