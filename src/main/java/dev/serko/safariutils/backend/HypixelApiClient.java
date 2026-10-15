package dev.serko.safariutils.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.client.NetworkRuntime;
import dev.serko.safariutils.client.MinecraftProfiles;
import dev.serko.safariutils.client.CanonicalPlayerNames;
import dev.serko.safariutils.concurrent.HttpBodies;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Key-free client for derived SafariUtils data; upstream Hypixel credentials remain server-side. */
final class HypixelApiClient {
	private static final int BACKEND_SCHEMA = 1;
	private static final URI PLAYERS = BackendEndpoints.PLAYERS;
	private static final long MANUAL_REQUEST_INTERVAL_MILLIS = 10_000L;
	private static final long LOCAL_CACHE_MILLIS = 5 * 60_000L;
	private static final int PLAYER_CACHE_LIMIT = 256;

	private final Map<String, CachedPlayer> players = new HashMap<>();
	private volatile long lastManualProfileRequestAt;

	private record CachedPlayer(SafariPlayerData collection, RankSnapshot rank,
			long cachedAt) { }
	record RankSnapshot(int colour, int permanentColour, int permanentLevel, boolean temporary,
		long fetchedAt) {
		static RankSnapshot decode(JsonObject player) {
			int colour = BackendValues.colour(player, "rankColour");
			int permanent = player.has("permanentRankColour")
				? BackendValues.colour(player, "permanentRankColour") : colour == 0xFFFFAA00 ? -1 : colour;
			long level = player.has("permanentRankLevel") ? BackendValues.integer(player, "permanentRankLevel") : -1;
			if (level < -1 || level > 4) throw new IllegalArgumentException("Invalid permanent rank level");
			boolean temporary = player.has("temporaryRank")
				? BackendValues.bool(player, "temporaryRank") : colour == 0xFFFFAA00;
			long fetchedAt = BackendValues.integer(player, "rankFetchedAt");
			if (fetchedAt < 0) throw new IllegalArgumentException("Invalid rank timestamp");
			return new RankSnapshot(colour, permanent, (int) level, temporary, fetchedAt);
		}
	}
	record ProfileIdentity(String uuid, String name) { }

	SafariPlayerData collectionManual(String uuid) throws IOException, InterruptedException {
		return collection(uuid, true);
	}

	SafariPlayerData collectionAutomatic(String uuid) throws IOException, InterruptedException {
		return collection(uuid, false);
	}

	private SafariPlayerData collection(String uuid, boolean manual) throws IOException, InterruptedException {
		String compact = compactUuid(uuid);
		CachedPlayer cached = fresh(compact);
		if (cached != null) return cached.collection();
		if (manual) waitForManualRequestSlot();
		fetchPlayers(List.of(compact));
		if (manual) lastManualProfileRequestAt = System.currentTimeMillis();
		return required(compact).collection();
	}

	/** Primes an entire party through one backend request. */
	void prefetch(Collection<String> uuids) throws IOException, InterruptedException {
		List<String> missing = uuids.stream().map(HypixelApiClient::compactUuid)
			.distinct().filter(uuid -> fresh(uuid) == null).toList();
		if (!missing.isEmpty()) fetchPlayers(missing);
	}

	private void waitForManualRequestSlot() throws InterruptedException {
		long remaining = MANUAL_REQUEST_INTERVAL_MILLIS
			- (System.currentTimeMillis() - lastManualProfileRequestAt);
		if (remaining > 0) Thread.sleep(remaining);
	}

	long nextProfileRequestAt() {
		return lastManualProfileRequestAt + MANUAL_REQUEST_INTERVAL_MILLIS;
	}

	RankSnapshot rank(String uuid) throws IOException, InterruptedException {
		String compact = compactUuid(uuid);
		CachedPlayer cached = fresh(compact);
		if (cached == null) {
			fetchPlayers(List.of(compact));
			cached = required(compact);
		}
		return cached.rank();
	}

	/** Minecraft supplies UUID resolution and canonical capitalization without Hypixel. */
	ProfileIdentity identity(String username) throws IOException, InterruptedException {
		try {
			MinecraftProfiles.Profile profile = MinecraftProfiles.resolve(username).get(15, TimeUnit.SECONDS);
			CanonicalPlayerNames.rememberProfile(profile.name(), profile.uuid());
			return new ProfileIdentity(profile.uuid(), profile.name());
		} catch (ExecutionException error) {
			Throwable cause = error.getCause();
			while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
				cause = cause.getCause();
			}
			if (cause instanceof IOException io) throw io;
			throw new IOException("Minecraft profile lookup unavailable", cause);
		} catch (TimeoutException timeout) {
			throw new IOException("Minecraft profile lookup timed out", timeout);
		}
	}

	private void fetchPlayers(List<String> uuids) throws IOException, InterruptedException {
		if (uuids.isEmpty() || uuids.size() > 4) throw new IOException("Invalid backend player batch");
		JsonObject body = new JsonObject();
		JsonArray requested = new JsonArray();
		uuids.forEach(requested::add);
		body.add("uuids", requested);
		String token = RemoteSession.instance().token();
		HttpRequest request = HttpRequest.newBuilder(PLAYERS)
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/json")
			.header("User-Agent", "SafariUtils-Developer")
			.header("Authorization", "Bearer " + token)
			.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		HttpResponse<String> response = NetworkRuntime.authenticatedHttp().send(request,
			HttpBodies.utf8(128 * 1024));
		if (response.statusCode() == 401) RemoteSession.instance().invalidate(token);
		if (response.statusCode() / 100 != 2) throw backendError(response);
		try {
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (BackendValues.integer(root, "schema") != BACKEND_SCHEMA) {
				throw new IOException("SafariUtils API uses an unsupported response schema");
			}
			JsonArray result = root.getAsJsonArray("players");
			if (result == null || result.size() != uuids.size()) {
				throw new IOException("SafariUtils API returned an incomplete player batch");
			}
			Set<String> requestedUuids = new HashSet<>(uuids);
			Set<String> received = new HashSet<>();
			Map<String, CachedPlayer> validated = new HashMap<>();
			long cachedAt = System.currentTimeMillis();
			for (JsonElement element : result) {
				JsonObject player = element.getAsJsonObject();
				String uuid = compactUuid(player.get("uuid").getAsString());
				if (!requestedUuids.contains(uuid) || !received.add(uuid)) {
					throw new IOException("SafariUtils API returned an unexpected player batch");
				}
				validated.put(uuid, derivedPlayer(player, cachedAt));
			}
			// Commit only a fully validated batch; a malformed later player must not
			// partially replace the party's previously usable cached data.
			players.putAll(validated);
			trimOldestPlayer();
		} catch (IOException error) {
			throw error;
		} catch (RuntimeException malformed) {
			throw new IOException("SafariUtils API returned malformed player data", malformed);
		}
	}

	private void trimOldestPlayer() {
		while (players.size() > PLAYER_CACHE_LIMIT) {
			String oldest = players.entrySet().stream()
				.min(java.util.Comparator.comparingLong(entry -> entry.getValue().cachedAt()))
				.map(Map.Entry::getKey).orElse(null);
			if (oldest == null) return;
			players.remove(oldest);
		}
	}

	private static CachedPlayer derivedPlayer(JsonObject player, long cachedAt)
			throws IOException {
		SafariPlayerData collection = SafariPlayerData.decode(player);

		return new CachedPlayer(collection, RankSnapshot.decode(player), cachedAt);
	}


	private static IOException backendError(HttpResponse<String> response) {
		try {
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("message")) return new IOException(root.get("message").getAsString());
		} catch (RuntimeException ignored) {
			// Fall through to the stable HTTP message.
		}
		return new IOException("SafariUtils API request failed with HTTP " + response.statusCode());
	}

	private CachedPlayer fresh(String uuid) {
		CachedPlayer cached = players.get(uuid);
		return cached != null && System.currentTimeMillis() - cached.cachedAt() < LOCAL_CACHE_MILLIS
			? cached : null;
	}

	private CachedPlayer required(String uuid) throws IOException {
		CachedPlayer cached = players.get(uuid);
		if (cached == null) throw new IOException("SafariUtils API omitted requested player");
		return cached;
	}

	private static String compactUuid(String uuid) {
		return uuid.replace("-", "").toLowerCase(Locale.ROOT);
	}
}
