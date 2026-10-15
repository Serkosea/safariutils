package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class DiagnosticQueueRegression {
	public static int run() throws Exception {
		var queue = new DiagnosticQueue<Integer>(2);
		check(queue.offer(1) && queue.offer(2) && !queue.offer(3), "bounded offer");
		check(queue.size() == 2 && queue.takeDropped() == 1 && queue.takeDropped() == 0, "single authoritative size and drop count");
		check(queue.poll() == 1 && queue.poll() == 2 && queue.poll() == null && queue.size() == 0, "FIFO draining");
		queue.offer(4);
		queue.close();
		check(!queue.offer(5) && queue.poll() == 4 && queue.dropped() == 0, "close rejects late producers without losing retained record");
		queue.clear();
		check(!queue.offer(6) && queue.size() == 0, "clear cannot reopen shutdown queue");
		var concurrent = new DiagnosticQueue<Integer>(64);
		var accepted = new AtomicInteger();
		var threads = new ArrayList<Thread>();
		for (int worker = 0; worker < 4; worker++) {
			threads.add(Thread.ofPlatform().start(() -> {
				for (int i = 0; i < 10_000; i++) if (concurrent.offer(i)) accepted.incrementAndGet();
			}));
		}
		for (Thread thread : threads) thread.join();
		check(accepted.get() == 64 && concurrent.size() == 64 && concurrent.dropped() == 39_936,
			"concurrent burst neither overfills nor loses overflow accounting");
		concurrent.clear();
		check(concurrent.size() == 0 && concurrent.dropped() == 0, "disabled flush clears state together");
		var repeats = new DiagnosticRepeats(2, 1000);
		repeats.accept("test", "repeat", 1);
		repeats.accept("test", "repeat", 2);
		check(repeats.drain().getFirst().count() == 1 && repeats.size() == 0 && repeats.drain().isEmpty(),
			"shutdown preserves unexpired repeat summary exactly once");
		return 8;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
