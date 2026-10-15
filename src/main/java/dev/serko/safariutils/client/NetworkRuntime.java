package dev.serko.safariutils.client;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.io.InputStream;
import java.io.IOException;
import java.time.Duration;
import dev.serko.safariutils.concurrent.BoundedRequests;
import dev.serko.safariutils.concurrent.DeadlineInputStream;
import dev.serko.safariutils.concurrent.HttpBodies;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Supplier;

/**
 * Bounded process-wide runtime for SafariUtils' public HTTP work.
 *
 * <p>Features retain their own request cadence and caches, while this class owns
 * the expensive threads, reusable client, and deterministic shutdown boundary.
 */
public final class NetworkRuntime {
	private static final BoundedRequests WORKERS = new BoundedRequests("SafariUtils-Network", 3, 128);
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	/** Proofs and session tokens must never be forwarded to a redirect destination. */
	private static final HttpClient AUTHENTICATED_HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();
	private static volatile boolean stopping;
	private static final ScheduledThreadPoolExecutor DEADLINES = deadlines();

	private NetworkRuntime() { }

	public static HttpClient http() {
		return HTTP;
	}

	public static HttpClient authenticatedHttp() {
		return AUTHENTICATED_HTTP;
	}

	/** Completes overload and shutdown failures through the same asynchronous contract. */
	public static <T> CompletableFuture<T> submit(Supplier<T> task) {
		return WORKERS.submit(task);
	}

	public static boolean stopping() {
		return stopping;
	}

	@FunctionalInterface
	public interface BodyReader<T> {
		T read(int status, InputStream body) throws IOException;
	}

	/** Worker-only selective parsing with one deadline covering headers and body consumption. */
	public static <T> T read(HttpRequest request, int maximumBytes, Duration budget, BodyReader<T> reader)
			throws IOException, InterruptedException {
		if (stopping) throw new IOException("Network runtime stopped");
		long duration = budget.toNanos();
		if (duration <= 0) throw new IllegalArgumentException("Positive request budget required");
		Duration headerBudget = request.timeout().filter(value -> value.compareTo(budget) < 0).orElse(budget);
		request = HttpRequest.newBuilder(request, (name, value) -> true).timeout(headerBudget).build();
		long start = System.nanoTime();
		var response = HTTP.send(request, HttpBodies.stream(maximumBytes));
		try (var body = new DeadlineInputStream(response.body(), duration - (System.nanoTime() - start), DEADLINES)) {
			body.checkDeadline();
			T value = reader.read(response.statusCode(), body);
			body.checkDeadline();
			return value;
		}
	}

	private static ScheduledThreadPoolExecutor deadlines() {
		var scheduler = new ScheduledThreadPoolExecutor(1, task -> {
			Thread thread = new Thread(task, "SafariUtils-Network-Deadlines");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.setRemoveOnCancelPolicy(true);
		return scheduler;
	}

	/** Starts optional background work only while the client runtime is accepting it. */
	public static boolean execute(Runnable task) {
		if (task == null || stopping) return false;
		return WORKERS.execute(task);
	}

	public static void shutdown() {
		stopping = true;
		try { HTTP.shutdownNow(); }
		finally {
			try { AUTHENTICATED_HTTP.shutdownNow(); }
			finally {
				try { WORKERS.close(); }
				finally { DEADLINES.shutdownNow(); }
			}
		}
	}
}
