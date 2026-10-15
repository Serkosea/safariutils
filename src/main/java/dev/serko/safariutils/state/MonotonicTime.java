package dev.serko.safariutils.state;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Process-relative milliseconds for transient state; never persist or interpret as a date. */
public final class MonotonicTime {
	private static final MonotonicTime PROCESS = new MonotonicTime(System::nanoTime);
	private final LongSupplier nanos;
	private final long origin;

	public MonotonicTime(LongSupplier nanos) {
		this.nanos = Objects.requireNonNull(nanos);
		origin = nanos.getAsLong();
	}

	/** Positive values preserve zero as an unset timestamp in existing state models. */
	public long millis() {
		return 1L + (nanos.getAsLong() - origin) / 1_000_000L;
	}

	public static long nowMillis() { return PROCESS.millis(); }
}
