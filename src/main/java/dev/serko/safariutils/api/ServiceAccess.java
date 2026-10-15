package dev.serko.safariutils.api;

import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Failure boundary for optional services, including broken service descriptors or linkage. */
public final class ServiceAccess<T> {
	private final Optional<T> provider;
	private final BiConsumer<String, Throwable> errors;

	public ServiceAccess(Supplier<Optional<T>> loader, BiConsumer<String, Throwable> errors) {
		this.errors = java.util.Objects.requireNonNull(errors);
		Optional<T> loaded;
		try { loaded = java.util.Objects.requireNonNull(loader.get()); }
		catch (RuntimeException | LinkageError | ServiceConfigurationError failure) {
			errors.accept("load", failure);
			loaded = Optional.empty();
		}
		provider = loaded;
	}

	static <T> ServiceAccess<T> load(Class<T> type, String category) {
		return load(type, category, true);
	}

	static <T> ServiceAccess<T> load(Class<T> type, String category, boolean enabled) {
		return new ServiceAccess<>(() -> enabled ? ServiceLoader.load(type).findFirst() : Optional.empty(),
			(operation, error) -> dev.serko.safariutils.client.OperationalLog.error(category + "/" + operation, error));
	}

	public boolean available() { return provider.isPresent(); }

	public <R> R read(String operation, Function<T, R> call, R fallback) {
		if (provider.isEmpty()) return fallback;
		try {
			R result = call.apply(provider.get());
			return result == null ? fallback : result;
		} catch (RuntimeException | LinkageError | ServiceConfigurationError failure) {
			errors.accept(operation, failure);
			return fallback;
		}
	}

	public void run(String operation, Consumer<T> call) {
		read(operation, service -> { call.accept(service); return true; }, false);
	}

	public <R> CompletableFuture<R> request(String operation, Function<T, CompletableFuture<R>> call) {
		if (provider.isEmpty()) return CompletableFuture.failedFuture(new IllegalStateException("Service unavailable"));
		try {
			return java.util.Objects.requireNonNull(call.apply(provider.get()))
				.thenApply(java.util.Objects::requireNonNull)
				.whenComplete((value, failure) -> {
					if (failure == null) return;
					Throwable cause = failure;
					while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
					if (!(cause instanceof CancellationException)) errors.accept(operation, cause);
				});
		}
		catch (RuntimeException | LinkageError | ServiceConfigurationError failure) {
			errors.accept(operation, failure);
			return CompletableFuture.failedFuture(failure);
		}
	}
}
