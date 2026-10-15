package dev.serko.safariutils.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.Optional;

/** Applies results in the owner executor, where generation changes must also be serialized. */
public final class GenerationDispatch {
	private GenerationDispatch() { }

	public static CompletableFuture<Boolean> submit(Executor owner, LongSupplier generation,
			long expected, Runnable action) {
		return dispatch(owner, () -> generation.getAsLong() == expected, () -> {
			action.run();
			return true;
		}, false);
	}

	/** Value-producing application with the same cancellation, failure and owner-thread guard. */
	public static <T> CompletableFuture<Optional<T>> call(Executor owner, BooleanSupplier current,
			Supplier<T> action) {
		return dispatch(owner, current, () -> Optional.of(action.get()), Optional.empty());
	}

	private static <T> CompletableFuture<T> dispatch(Executor owner, BooleanSupplier current,
			Supplier<T> action, T stale) {
		CompletableFuture<T> result = new CompletableFuture<>();
		try {
			owner.execute(() -> {
				try {
					if (result.isDone()) return;
					if (!current.getAsBoolean()) {
						result.complete(stale);
						return;
					}
					result.complete(action.get());
				} catch (Throwable failure) {
					result.completeExceptionally(failure);
				}
			});
		} catch (RuntimeException rejected) {
			result.completeExceptionally(rejected);
		}
		return result;
	}
}
