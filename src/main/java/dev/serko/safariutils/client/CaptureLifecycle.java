package dev.serko.safariutils.client;

/** Pure terminal-transition policy. World/projection side effects stay in CritterState. */
final class CaptureLifecycle {
	enum Resolution { IGNORE, RETIRE, CAUGHT, BREAKOUT_READY, BREAKOUT_WAITING }
	record Input(boolean pending, boolean terminal, long attemptId, boolean logicalExists,
		long owner, boolean alreadyCaught, boolean caught, boolean reappeared) { }

	static Resolution resolve(Input input) {
		return resolve(input.pending(), input.terminal(), input.attemptId(), input.logicalExists(),
			input.owner(), input.alreadyCaught(), input.caught(), input.reappeared());
	}

	static Resolution resolve(boolean pending, boolean terminal, long attemptId,
			boolean logicalExists, long owner, boolean alreadyCaught, boolean caught,
			boolean reappeared) {
		if (!pending || !terminal) return Resolution.IGNORE;
		if (!logicalExists || owner != attemptId || alreadyCaught) return Resolution.RETIRE;
		if (caught) return Resolution.CAUGHT;
		return reappeared ? Resolution.BREAKOUT_READY : Resolution.BREAKOUT_WAITING;
	}

	static boolean validLink(boolean pending, boolean breakout, boolean waitingForBody) {
		return pending || breakout && waitingForBody;
	}

	private CaptureLifecycle() { }
}
