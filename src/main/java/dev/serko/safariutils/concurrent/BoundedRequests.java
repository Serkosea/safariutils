package dev.serko.safariutils.concurrent;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Owns bounded background work and completes every caller, including on shutdown. */
public final class BoundedRequests implements AutoCloseable {
	private final ThreadPoolExecutor executor;
	private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
	private volatile boolean closed;

	public BoundedRequests(String name, int threads, int queueCapacity) {
		executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(queueCapacity), task -> {
				Thread thread = new Thread(task, name);
				thread.setDaemon(true);
				return thread;
			});
	}

	public <T> CompletableFuture<T> submit(Supplier<T> task) {
		java.util.Objects.requireNonNull(task);
		CompletableFuture<T> result = new CompletableFuture<>();
		Runnable work = () -> {
			if (result.isDone()) return;
			try { result.complete(task.get()); }
			catch (Throwable error) { result.completeExceptionally(error); }
		};
		pending.add(result);
		result.whenComplete((value, error) -> {
			pending.remove(result);
			// Cancelled queued work must release admission capacity immediately. Running
			// work is not interrupted here: shared subscribers own independent futures.
			executor.remove(work);
		});
		try {
			if (closed) throw new RejectedExecutionException("Request runtime is closed");
			executor.execute(work);
			if (result.isDone()) executor.remove(work);
		} catch (RejectedExecutionException unavailable) {
			result.completeExceptionally(unavailable);
		}
		return result;
	}

	public boolean execute(Runnable task) {
		if (closed) return false;
		try {
			executor.execute(task);
			return true;
		} catch (RejectedExecutionException unavailable) {
			return false;
		}
	}

	@Override
	public void close() {
		closed = true;
		executor.shutdownNow();
		for (CompletableFuture<?> result : pending) {
			result.completeExceptionally(new RejectedExecutionException("Request runtime is closed"));
		}
	}
}
