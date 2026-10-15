package dev.serko.safariutils.client;

import java.util.UUID;
import static dev.serko.safariutils.client.CaptureLifecycle.Resolution.*;

/** Sequence fixtures exercising the same selection/transition/ownership policies as live captures. */
public final class CaptureLifecycleRegression {
	private static int checks;
	public static int run() {
		checks = 0;
		for (int flags = 0; flags < 64; flags++) for (long owner : new long[] {0, 7, 8}) {
			var input = new CaptureLifecycle.Input((flags & 1) != 0, (flags & 2) != 0, 7,
				(flags & 4) != 0, owner, (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0);
			var expected = !input.pending() || !input.terminal() ? IGNORE
				: !input.logicalExists() || owner != 7 || input.alreadyCaught() ? RETIRE
				: input.caught() ? CAUGHT : input.reappeared() ? BREAKOUT_READY : BREAKOUT_WAITING;
			check(CaptureLifecycle.resolve(input) == expected, "exhaustive terminal policy");
			var trace = CaptureTransitionTrace.encode(input, 9, expected);
			check(CaptureTransitionTrace.matches(trace), "terminal trace round trip");
			trace.addProperty("result", expected == CAUGHT ? RETIRE.name() : CAUGHT.name());
			check(!CaptureTransitionTrace.matches(trace), "changed terminal decision is detected");
		}
		var owners = new CaptureOwnership();
		UUID first = new UUID(0, 1), second = new UUID(0, 2);
		owners.claim(1, first, false);
		owners.claim(2, second, false);
		var selection = new CaptureOutcomeSelection(100);
		selection.observe(1, 10, true, true, true); // absent, then reappeared
		selection.observe(2, 20, true, true, false); // still inside its capsule
		check(selection.select(false, 100, 750) == 2, "catch cannot consume reappeared peer");
		check(selection.select(true, 100, 5000) == 1, "breakout retains direct evidence");
		check(CaptureLifecycle.resolve(true, true, 2, true, 2, false, true, false) == CAUGHT,
			"matched catch is terminal");
		owners.release(2);
		check(owners.owner(first) == 1 && owners.owner(second) == null, "peer marker survives catch cleanup");
		check(CaptureLifecycle.resolve(true, true, 1, true, 1, false, false, true) == BREAKOUT_READY,
			"observed breakout allows immediate retry");
		owners.claim(3, first, true); // retry starts before old acknowledgement/cleanup
		check(CaptureLifecycle.resolve(true, true, 1, true, 3, false, true, false) == RETIRE,
			"late old terminal result cannot alter new logical owner");
		owners.release(1);
		check(owners.owner(first) == 3, "old projection cleanup preserves retry ownership");
		check(CaptureLifecycle.resolve(false, true, 1, true, 3, false, false, true) == IGNORE,
			"duplicate acknowledgement has no terminal effects");
		check(CaptureLifecycle.resolve(true, true, 3, true, 3, false, false, false) == BREAKOUT_WAITING,
			"result before body retains reappearance anchor");
		check(CaptureLifecycle.validLink(false, true, true), "waiting anchor is a valid terminal link");
		check(!CaptureLifecycle.validLink(false, false, true), "caught result cannot retain active link");
		check(CaptureLifecycle.resolve(true, true, 3, false, 0, false, true, false) == RETIRE,
			"missing logical retires projection only");
		check(CaptureLifecycle.resolve(true, true, 3, true, 3, true, true, false) == RETIRE,
			"caught logical cannot be revived by stale result");
		check(CaptureLifecycle.resolve(true, false, 3, true, 3, false, false, false) == IGNORE,
			"non-terminal observation cannot resolve attempt");
		var allEscaped = new CaptureOutcomeSelection(100);
		allEscaped.observe(1, 10, true, true, true);
		allEscaped.observe(2, 20, true, true, true);
		check(allEscaped.select(false, 10000, 750) == 0, "elapsed fallback cannot override contradictory breakout evidence");
		owners.clear();
		check(owners.entries().isEmpty(), "visit reset clears presentation ownership");
		return checks;
	}
	private static void check(boolean valid, String reason) {
		if (!valid) throw new AssertionError(reason);
		checks++;
	}
}
