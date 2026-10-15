package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SharedSparklingProviders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Cached, API-key-free Minecraft username capitalization. */
public final class CanonicalPlayerNames {
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final int CACHE_LIMIT = 512;
	private static final Map<String, String> NAMES = new ConcurrentHashMap<>();

	private CanonicalPlayerNames() { }

	static String display(String value) {
		String valid = valid(value);
		if (valid.isEmpty()) return "";
		String memory = NAMES.get(normalize(valid));
		if (memory != null) return memory;
		String persisted = PlayerIdentityStore.canonicalName(valid);
		return persisted.isEmpty() ? valid : persisted;
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
			String compact = uuid.replace("-", "").toLowerCase(Locale.ROOT);
			MinecraftProfiles.remember(canonical, compact);
			PlayerIdentityStore.remember(canonical, compact);
			SharedSparklingProviders.rememberIdentity(compact, canonical);
		}
		return canonical;
	}

	public static String uuid(String value) {
		return PlayerIdentityStore.uuid(value);
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
		if (cached == null) cached = PlayerIdentityStore.canonicalName(valid);
		if (cached != null && !cached.isEmpty()) {
			remember(cached);
			return CompletableFuture.completedFuture(cached);
		}
		return MinecraftProfiles.resolve(valid)
			.thenApply(profile -> rememberProfile(profile.name(), profile.uuid()));
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
	}
}
