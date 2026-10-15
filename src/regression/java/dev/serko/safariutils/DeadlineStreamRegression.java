package dev.serko.safariutils;

import dev.serko.safariutils.concurrent.DeadlineInputStream;
import dev.serko.safariutils.concurrent.HttpBodies;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

final class DeadlineStreamRegression {
	static int run() throws Exception {
		var scheduler = new ScheduledThreadPoolExecutor(1);
		scheduler.setRemoveOnCancelPolicy(true);
		try {
			try (var stream = new DeadlineInputStream(new ByteArrayInputStream(new byte[]{1,2,3}),
					TimeUnit.SECONDS.toNanos(10), scheduler)) {
				check(stream.read() == 1 && stream.readAllBytes().length == 2, "stream parsed incrementally");
			}
			check(scheduler.getQueue().isEmpty(), "normal close removes watchdog");
			CountDownLatch closed = new CountDownLatch(1);
			InputStream stalled = new InputStream() {
				@Override public int read() throws IOException {
					try {
						if (!closed.await(5, TimeUnit.SECONDS)) throw new AssertionError("watchdog failed to unblock reader");
						return -1;
					} catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
				}
				@Override public void close() { closed.countDown(); }
			};
			boolean timedOut = false;
			try (var stream = new DeadlineInputStream(stalled, TimeUnit.MILLISECONDS.toNanos(50), scheduler)) {
				stream.read();
			} catch (java.net.http.HttpTimeoutException expected) { timedOut = true; }
			check(timedOut && closed.getCount() == 0, "stalled body closed and never treated as valid EOF");
			boolean expired = false;
			try (var stream = new DeadlineInputStream(new ByteArrayInputStream(new byte[]{1}), 0, scheduler)) {
				stream.read();
			} catch (java.net.http.HttpTimeoutException expected) { expired = true; }
			check(expired, "header work consuming entire budget prevents body parsing");
			var subscriber = HttpBodies.stream(3).apply(null);
			AtomicBoolean cancelled = new AtomicBoolean();
			subscriber.onSubscribe(new Flow.Subscription() {
				public void request(long n) { }
				public void cancel() { cancelled.set(true); }
			});
			try (var stream = subscriber.getBody().toCompletableFuture().join()) {
				subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2,3,4})));
				boolean rejected = false;
				try { stream.read(); } catch (IOException expected) { rejected = true; }
				check(rejected && cancelled.get(), "oversized streamed body cancels upstream and fails reader");
			}
		} finally { scheduler.shutdownNow(); }
		AtomicBoolean rejectedBodyClosed = new AtomicBoolean();
		try {
			new DeadlineInputStream(new ByteArrayInputStream(new byte[0]) {
				@Override public void close() { rejectedBodyClosed.set(true); }
			}, TimeUnit.SECONDS.toNanos(10), scheduler);
			throw new AssertionError("closed scheduler accepted work");
		} catch (java.util.concurrent.RejectedExecutionException expected) {
			check(rejectedBodyClosed.get(), "shutdown race closes newly arrived response body");
		}
		return 6;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
