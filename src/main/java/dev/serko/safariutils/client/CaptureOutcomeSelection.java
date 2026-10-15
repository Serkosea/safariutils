package dev.serko.safariutils.client;

/** Pure, one-pass outcome attribution. Callers supply observations, never mutable world objects. */
final class CaptureOutcomeSelection {
	record Candidate(long id, long startedAt, boolean absent, boolean brokeOut) { }
	private final java.util.List<Candidate> candidates;
	private final long observedAt;
	private long oldestId;
	private long oldestAt = Long.MAX_VALUE;
	private long absentId;
	private long absentAt = Long.MAX_VALUE;
	private long breakoutId;
	private int eligible;
	private long catchId;
	private long catchAt = Long.MAX_VALUE;
	private final boolean excludeBreakoutsFromCatch;

	CaptureOutcomeSelection(long observedAt) {
		this(observedAt, false);
	}

	CaptureOutcomeSelection(long observedAt, boolean recordEvidence) {
		this(observedAt, recordEvidence, true);
	}

	/** Compatibility is only for replaying schema-1 traces, never live attribution. */
	CaptureOutcomeSelection(long observedAt, boolean recordEvidence, boolean excludeBreakoutsFromCatch) {
		this.observedAt = observedAt;
		this.excludeBreakoutsFromCatch = excludeBreakoutsFromCatch;
		candidates = recordEvidence ? new java.util.ArrayList<>() : null;
	}

	long observedAt() { return observedAt; }
	java.util.List<Candidate> candidates() { return candidates; }
	long directBreakout() { return breakoutId; }

	void observe(long id, long startedAt, boolean pending, boolean absent, boolean brokeOut) {
		if (!pending || startedAt > observedAt) return;
		if (candidates != null) candidates.add(new Candidate(id, startedAt, absent, brokeOut));
		if (oldestId == 0L || startedAt < oldestAt) {
			oldestId = id;
			oldestAt = startedAt;
		}
		if (brokeOut && breakoutId == 0L) breakoutId = id;
		if (brokeOut && excludeBreakoutsFromCatch) return;
		eligible++;
		if (catchId == 0L || startedAt < catchAt) {
			catchId = id;
			catchAt = startedAt;
		}
		if (absent && (absentId == 0L || startedAt < absentAt)) {
			absentId = id;
			absentAt = startedAt;
		}
	}

	long select(boolean breakout, long now, long fallbackMillis) {
		if (breakout) {
			if (breakoutId != 0L) return breakoutId;
		} else {
			if (absentId != 0L) return absentId;
			if (eligible == 1) return catchId;
		}
		return now - observedAt >= fallbackMillis ? (breakout ? oldestId : catchId) : 0L;
	}
}
