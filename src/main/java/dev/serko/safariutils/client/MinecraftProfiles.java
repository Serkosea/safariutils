package dev.serko.safariutils.client;

import com.google.gson.JsonParser;
import dev.serko.safariutils.concurrent.HttpBodies;
import dev.serko.safariutils.concurrent.SingleFlight;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/** One bounded, key-free profile resolver shared by UI and authenticated lookup workflows. */
public final class MinecraftProfiles {
	private static final int LIMIT = 512;
	private static final long FRESH_NANOS = Duration.ofMinutes(5).toNanos();
	private static final long RETRY_NANOS = Duration.ofSeconds(30).toNanos();
	private static final String LOOKUP = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
	private static final Map<String, Cached> CACHE = new HashMap<>();
	private static final Map<String, Failure> FAILURES = new HashMap<>();
	private static final ConcurrentHashMap<String, CompletableFuture<Profile>> PENDING = new ConcurrentHashMap<>();

	public record Profile(String uuid, String name) { }
	private record Cached(Profile profile, long observedNanos) { }
	private record Failure(long atNanos, Throwable cause) { }

	private MinecraftProfiles() { }

	public static synchronized void remember(String name, String uuid) {
		if (name == null || !name.matches("[A-Za-z0-9_]{1,16}") || uuid == null) return;
		String compact = uuid.replace("-", "").toLowerCase(Locale.ROOT);
		if (!compact.matches("[0-9a-f]{32}")) return;
		String key = name.toLowerCase(Locale.ROOT);
		CACHE.put(key, new Cached(new Profile(compact, name), System.nanoTime()));
		FAILURES.remove(key);
		while (CACHE.size() > LIMIT) {
			String oldest = CACHE.entrySet().stream()
				.min(java.util.Comparator.comparingLong(entry -> entry.getValue().observedNanos()))
				.orElseThrow().getKey();
			CACHE.remove(oldest);
		}
	}

	public static CompletableFuture<Profile> resolve(String name) {
		if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
			return CompletableFuture.failedFuture(new IOException("Invalid Minecraft username"));
		}
		String key = name.toLowerCase(Locale.ROOT);
		synchronized (MinecraftProfiles.class) {
			Cached cached = CACHE.get(key);
			long now = System.nanoTime();
			if (cached != null && now - cached.observedNanos() < FRESH_NANOS) {
				return CompletableFuture.completedFuture(cached.profile());
			}
			Failure failure = FAILURES.get(key);
			if (failure != null && now - failure.atNanos() < RETRY_NANOS) {
				return CompletableFuture.failedFuture(failure.cause());
			}
		}
		return SingleFlight.request(PENDING, key, () -> NetworkRuntime.submit(() -> fetch(name))
			.whenComplete((profile, error) -> {
				if (error == null) remember(profile.name(), profile.uuid());
				else synchronized (MinecraftProfiles.class) {
					if (FAILURES.size() >= LIMIT) FAILURES.remove(FAILURES.keySet().iterator().next());
					FAILURES.put(key, new Failure(System.nanoTime(), error));
				}
			}));
	}

	private static Profile fetch(String name) {
		try {
			var request = HttpRequest.newBuilder(URI.create(LOOKUP + name))
				.timeout(Duration.ofSeconds(10)).GET().build();
			var response = NetworkRuntime.http().send(request, HttpBodies.utf8(16 * 1024));
			if (response.statusCode() == 204 || response.statusCode() == 404) {
				throw new IOException("Minecraft player not found: " + name);
			}
			if (response.statusCode() / 100 != 2) {
				throw new IOException("Minecraft profile lookup failed with HTTP " + response.statusCode());
			}
			var root = JsonParser.parseString(response.body()).getAsJsonObject();
			String canonical = root.get("name").getAsString();
			String uuid = root.get("id").getAsString().replace("-", "").toLowerCase(Locale.ROOT);
			if (!canonical.matches("[A-Za-z0-9_]{1,16}") || !uuid.matches("[0-9a-f]{32}")) {
				throw new IOException("Invalid Minecraft profile response");
			}
			return new Profile(uuid, canonical);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new CompletionException(new IOException("Minecraft profile lookup interrupted", error));
		} catch (IOException error) {
			throw new CompletionException(error);
		} catch (RuntimeException error) {
			throw new CompletionException(new IOException("Malformed Minecraft profile response", error));
		}
	}
}
