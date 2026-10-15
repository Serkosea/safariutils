package dev.serko.safariutils.concurrent;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closes a stalled HTTP body at its total deadline; normal close cancels the watchdog. */
public final class DeadlineInputStream extends FilterInputStream {
	private final AtomicBoolean closed = new AtomicBoolean();
	private final long started = System.nanoTime();
	private final long budgetNanos;
	private final ScheduledFuture<?> timeout;
	private volatile boolean expired;

	public DeadlineInputStream(InputStream input, long remainingNanos, ScheduledExecutorService scheduler)
			throws IOException {
		super(input);
		budgetNanos = Math.max(0, remainingNanos);
		try {
			timeout = scheduler.schedule(() -> {
				if (!closed.compareAndSet(false, true)) return;
				expired = true;
				try { in.close(); } catch (IOException ignored) { }
			}, budgetNanos, TimeUnit.NANOSECONDS);
		} catch (RuntimeException failure) {
			try { input.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
			throw failure;
		}
	}

	public void checkDeadline() throws HttpTimeoutException {
		if (expired || System.nanoTime() - started >= budgetNanos) {
			throw new HttpTimeoutException("Response body deadline exceeded");
		}
	}

	@Override public int read() throws IOException {
		checkDeadline();
		int value = in.read();
		checkDeadline();
		return value;
	}

	@Override public int read(byte[] buffer, int offset, int length) throws IOException {
		checkDeadline();
		int count = in.read(buffer, offset, length);
		checkDeadline();
		return count;
	}

	@Override public void close() throws IOException {
		timeout.cancel(false);
		if (closed.compareAndSet(false, true)) in.close();
	}

	@Override public long skip(long count) throws IOException {
		checkDeadline();
		long skipped = in.skip(count);
		checkDeadline();
		return skipped;
	}
}
