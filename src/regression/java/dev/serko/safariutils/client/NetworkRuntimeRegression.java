package dev.serko.safariutils.client;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Loopback-only transport smoke test; never contacts Minecraft, Hypixel or production services. */
public final class NetworkRuntimeRegression {
	public static int run() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch bodyStarted = new CountDownLatch(1);
		var redirected = new java.util.concurrent.atomic.AtomicInteger();
		server.createContext("/redirect", exchange -> {
			try (exchange) {
				exchange.getResponseHeaders().add("Location", "/destination");
				exchange.sendResponseHeaders(307, -1);
			}
		});
		server.createContext("/destination", exchange -> {
			try (exchange) {
				redirected.incrementAndGet();
				exchange.sendResponseHeaders(200, -1);
			}
		});
		server.createContext("/ok", exchange -> {
			try (exchange) {
				exchange.sendResponseHeaders(200, 2);
				exchange.getResponseBody().write(new byte[]{1, 2});
			}
		});
		server.createContext("/stall", exchange -> {
			try (exchange) {
				exchange.sendResponseHeaders(200, 0);
				exchange.getResponseBody().write(1);
				exchange.getResponseBody().flush();
				bodyStarted.countDown();
				try { release.await(5, TimeUnit.SECONDS); }
				catch (InterruptedException error) { Thread.currentThread().interrupt(); }
			}
		});
		server.start();
		String root = "http://127.0.0.1:" + server.getAddress().getPort();
		try {
			var redirect = HttpRequest.newBuilder(URI.create(root + "/redirect"))
				.timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer regression-only")
				.POST(HttpRequest.BodyPublishers.ofString("test-proof")).build();
			check(NetworkRuntime.authenticatedHttp().send(redirect,
				java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 307
				&& redirected.get() == 0, "authenticated request never forwards proof or token through redirects");
			var ok = HttpRequest.newBuilder(URI.create(root + "/ok")).GET().build();
			check(NetworkRuntime.read(ok, 8, Duration.ofSeconds(5), (status, body) -> body.readAllBytes()).length == 2,
				"bounded streaming accepts normal HTTP response");
			var stall = HttpRequest.newBuilder(URI.create(root + "/stall")).GET().build();
			boolean timedOut = false;
			try {
				NetworkRuntime.read(stall, 8, Duration.ofMillis(300), (status, body) -> body.readAllBytes());
			} catch (IOException expected) { timedOut = true; }
			finally { release.countDown(); }
			check(timedOut && bodyStarted.getCount() == 0, "deadline terminates an actual stalled HTTP body after headers");
			check(NetworkRuntime.read(ok, 8, Duration.ofSeconds(5), (status, body) -> body.readAllBytes()).length == 2,
				"shared transport remains usable after timeout");
		} finally {
			release.countDown();
			server.stop(0);
			NetworkRuntime.shutdown();
		}
		return 4;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
