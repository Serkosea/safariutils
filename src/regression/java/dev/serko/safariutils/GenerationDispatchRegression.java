package dev.serko.safariutils;

import dev.serko.safariutils.concurrent.GenerationDispatch;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class GenerationDispatchRegression {
	static int run() {
		var queue = new ArrayDeque<Runnable>();
		var generation = new AtomicLong(1);
		var applied = new AtomicInteger();
		var first = GenerationDispatch.submit(queue::add, generation::get, 1, applied::incrementAndGet);
		check(!first.isDone() && applied.get() == 0, "delivery waits for owner");
		generation.incrementAndGet();
		queue.remove().run();
		check(!first.join() && applied.get() == 0, "queued stale generation discarded");
		var current = GenerationDispatch.submit(queue::add, generation::get, 2, applied::incrementAndGet);
		queue.remove().run();
		check(current.join() && applied.get() == 1, "current owner applies once");
		var failed = GenerationDispatch.submit(queue::add, generation::get, 2, () -> { throw new IllegalStateException(); });
		queue.remove().run();
		check(failed.isCompletedExceptionally(), "action failure completes delivery");
		var rejected = GenerationDispatch.submit(task -> { throw new RejectedExecutionException(); }, generation::get, 2, applied::incrementAndGet);
		check(rejected.isCompletedExceptionally() && applied.get() == 1, "closed executor cannot strand delivery");
		var cancelled = GenerationDispatch.submit(queue::add, generation::get, 2, applied::incrementAndGet);
		cancelled.cancel(false);
		queue.remove().run();
		check(applied.get() == 1, "cancelled delivery does not apply");
		var restarted = GenerationDispatch.submit(Runnable::run, generation::get, 2, generation::incrementAndGet);
		check(restarted.join() && generation.get() == 3, "action can advance its lifecycle");
		var late = GenerationDispatch.submit(Runnable::run, generation::get, 2, applied::incrementAndGet);
		check(!late.join() && applied.get() == 1, "post-restart callbacks discarded");
		var value = GenerationDispatch.call(queue::add, () -> generation.get() == 3, () -> 42);
		queue.remove().run();
		check(value.join().orElseThrow() == 42, "value returned from owner thread");
		var staleValue = GenerationDispatch.call(queue::add, () -> generation.get() == 3, () -> {
			throw new AssertionError("stale action ran");
		});
		generation.incrementAndGet();
		queue.remove().run();
		check(staleValue.join().isEmpty(), "value guard checked at delivery rather than submission");
		var throwingValue = GenerationDispatch.call(Runnable::run, () -> true, () -> {
			throw new IllegalStateException();
		});
		check(throwingValue.isCompletedExceptionally(), "value application cannot strand its future");
		var cancelledValue = GenerationDispatch.call(queue::add, () -> true, applied::incrementAndGet);
		cancelledValue.cancel(false);
		queue.remove().run();
		check(applied.get() == 1, "cancelled value application has no effects");
		var rejectedValue = GenerationDispatch.call(task -> { throw new RejectedExecutionException(); },
			() -> true, () -> 42);
		check(rejectedValue.isCompletedExceptionally(), "rejected value delivery completes exceptionally");
		var failedGuard = GenerationDispatch.call(Runnable::run, () -> { throw new IllegalStateException(); }, () -> 42);
		check(failedGuard.isCompletedExceptionally(), "guard failure completes rather than escaping owner thread");
		return 14;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
