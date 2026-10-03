package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** Cached, API-key-free Minecraft username capitalization. */
public final class CanonicalPlayerNames {
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final int CACHE_LIMIT = 512;
	private static final String LOOKUP =
		"https://api.minecraftservices.com/minecraft/profile/lookup/name/";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8)).build();
	private static final ExecutorService REQUESTS = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "SafariUtils-Minecraft-Profiles");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
	private static final Map<String, String> UUIDS = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<String>> PENDING = new ConcurrentHashMap<>();
	private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();

	private CanonicalPlayerNames() { }

	static String display(String value) {
		String valid = valid(value);
		if (valid.isEmpty()) return "";
		return NAMES.getOrDefault(normalize(valid), valid);
	}

	/** Records capitalization supplied by Minecraft or another authoritative profile response. */
	static String remember(String value) {
		String valid = valid(value);
		if (!valid.isEmpty()) {
			String key = normalize(valid);
			if (!NAMES.containsKey(key) && NAMES.size() >= CACHE_LIMIT) evictOne();
			NAMES.put(key, valid);
		}
		return valid;
	}

	/** Caches an authoritative player profile already observed elsewhere in the mod. */
	public static String rememberProfile(String value, String uuid) {
		String canonical = remember(value);
		if (!canonical.isEmpty() && uuid != null && !uuid.isBlank()) {
			UUIDS.put(normalize(canonical), uuid.replace("-", "").toLowerCase(Locale.ROOT));
		}
		return canonical;
	}

	static String uuid(String value) {
		return UUIDS.getOrDefault(normalize(valid(value)), "");
	}

	/** Resolves from the live player list first, then Minecraft's unauthenticated profile service. */
	static CompletableFuture<String> resolve(String value) {
		String valid = valid(value);
		if (valid.isEmpty()) {
			return CompletableFuture.failedFuture(new IOException("Invalid Minecraft username"));
		}
		String online = onlineName(valid);
		if (online != null) return CompletableFuture.completedFuture(remember(online));
		String key = normalize(valid);
		String cached = NAMES.get(key);
		if (cached != null) return CompletableFuture.completedFuture(cached);
		if (System.currentTimeMillis() < RETRY_AFTER.getOrDefault(key, 0L)) {
			return CompletableFuture.failedFuture(new IOException("Minecraft profile lookup unavailable"));
		}
		return PENDING.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(LOOKUP + valid))
					.timeout(Duration.ofSeconds(10)).GET().build();
				HttpResponse<String> response = HTTP.send(request,
					HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 404) {
					throw new IOException("Minecraft player not found");
				}
				if (response.statusCode() / 100 != 2) {
					throw new IOException("Minecraft profile lookup failed with HTTP "
						+ response.statusCode());
				}
				JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
				if (!profile.has("name") || !profile.has("id")) {
					throw new IOException("Minecraft player not found");
				}
				String canonical = remember(profile.get("name").getAsString());
				if (canonical.isEmpty()) throw new IOException("Invalid Minecraft profile response");
				UUIDS.put(normalize(canonical), profile.get("id").getAsString()
					.replace("-", "").toLowerCase(Locale.ROOT));
				return canonical;
			} catch (IOException error) {
				throw new CompletionException(error);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new CompletionException(new IOException("Minecraft profile lookup interrupted", error));
			}
		}, REQUESTS).whenComplete((result, error) -> {
			PENDING.remove(key);
			if (error == null) RETRY_AFTER.remove(key);
			else RETRY_AFTER.put(key, System.currentTimeMillis() + 5 * 60_000L);
		}));
	}

	private static String onlineName(String wanted) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.getGameProfile().name().equalsIgnoreCase(wanted)) {
			return rememberOnline(client.player.getGameProfile().name(),
				client.player.getGameProfile().id().toString());
		}
		if (client.player == null || client.player.connection == null) return null;
		for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (name.equalsIgnoreCase(wanted)) {
				return rememberOnline(name, info.getProfile().id().toString());
			}
		}
		return null;
	}

	private static String rememberOnline(String name, String uuid) {
		return rememberProfile(name, uuid);
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}

	private static String valid(String value) {
		if (value == null) return "";
		String trimmed = value.trim();
		return USERNAME.matcher(trimmed).matches() ? trimmed : "";
	}

	private static void evictOne() {
		var iterator = NAMES.keySet().iterator();
		if (!iterator.hasNext()) return;
		String key = iterator.next();
		NAMES.remove(key);
		UUIDS.remove(key);
		RETRY_AFTER.remove(key);
	}
}
