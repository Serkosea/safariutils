package dev.serko.safariutils;

import dev.serko.safariutils.io.OrderedSnapshotWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** No user files: exercises production commit ordering with delayed and failing writers. */
final class OrderedSnapshotRegression {
	static int run() throws Exception {
		OrderedSnapshotWriter writer = new OrderedSnapshotWriter();
		AtomicInteger value = new AtomicInteger();
		check(writer.write(0, () -> value.set(1)), "initial revision commits");
		check(!writer.write(0, () -> value.set(2)) && value.get() == 1, "duplicate revision cannot rewrite");
		try {
			writer.write(2, () -> { throw new IOException("fixture"); });
			throw new AssertionError("failed write accepted");
		} catch (IOException expected) { }
		check(writer.write(2, () -> value.set(2)), "failed revision remains retryable");
		check(!writer.write(1, () -> value.set(1)) && value.get() == 2, "stale revision cannot roll back data");
		CountDownLatch captured = new CountDownLatch(1), release = new CountDownLatch(1);
		var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			var old = pool.submit(() -> {
				captured.countDown();
				if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("fixture timed out");
				return writer.write(3, () -> value.set(3));
			});
			check(captured.await(5, TimeUnit.SECONDS), "background snapshot captured");
			check(writer.write(4, () -> value.set(4)), "shutdown snapshot commits ahead of delayed encode");
			release.countDown();
			check(!old.get(5, TimeUnit.SECONDS) && value.get() == 4, "late worker cannot overwrite shutdown save");
		} finally { release.countDown(); pool.shutdownNow(); }
		return 7;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
