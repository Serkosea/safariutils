package dev.serko.safariutils.state;

/** Immutable animation clock. Inputs are monotonic nanoseconds, never wall-clock timestamps. */
public record LinearTimeline(long startedAtNanos, long durationNanos) {
	public LinearTimeline {
		if (durationNanos <= 0L) throw new IllegalArgumentException("Animation duration must be positive");
	}

	public double progress(long frameNanos) {
		long elapsed = frameNanos - startedAtNanos;
		if (elapsed <= 0L) return 0.0;
		if (elapsed >= durationNanos) return 1.0;
		return (double) elapsed / durationNanos;
	}
}
