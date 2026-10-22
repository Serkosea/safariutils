package dev.serko.safariutils.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.client.NetworkRuntime;
import dev.serko.safariutils.concurrent.HttpBodies;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.StructuredDiagnostics;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** One single-flight account session shared by lookups, market and party sync. Worker-only. */
final class RemoteSession {
	private static final long REFRESH_MARGIN_NANOS = Duration.ofMinutes(2).toNanos();
	private static final RemoteSession INSTANCE = new RemoteSession();
	private String token, ownerUuid, failedUuid;
	private long expiresAtNanos, retryAfterNanos;
	private RemoteSession() { }
	static RemoteSession instance() { return INSTANCE; }

	synchronized String token() throws IOException, InterruptedException {
		Minecraft minecraft = Minecraft.getInstance();
		var user = minecraft.getUser();
		String uuid = user.getProfileId().toString().replace("-", "").toLowerCase(Locale.ROOT);
		if (NetworkRuntime.stopping()) throw new IOException("Network runtime stopped");
		if (uuid.equals(ownerUuid) && token != null && expiresAtNanos - System.nanoTime() > REFRESH_MARGIN_NANOS) return token;
		if (uuid.equals(failedUuid) && System.nanoTime() - retryAfterNanos < 0)
			throw new IOException("SafariUtils service is temporarily unavailable; retry shortly");
		token = null;
		ownerUuid = null;
		var connection = minecraft.getConnection();
		if (connection == null) throw new IOException("Join a Minecraft server before requesting API data");
		String stage = "session";
		try {
			JsonObject request = new JsonObject();
			request.addProperty("uuid", uuid);
			long sessionStarted = System.nanoTime();
			// Claimed identity for service budgets, not proof of account ownership.
			JsonObject session = post(BackendEndpoints.UUID_SESSION, request);
			if (minecraft.getUser() != user || minecraft.getConnection() != connection || NetworkRuntime.stopping())
				throw new IOException("Minecraft context changed during authentication");
			try {
				String receivedToken = session.get("token").getAsString();
				int lifetime = session.get("expiresInSeconds").getAsInt();
				if (receivedToken.isBlank() || receivedToken.length() > 4096
					|| lifetime <= 120 || lifetime > 900) throw new IllegalArgumentException();
				token = receivedToken;
				expiresAtNanos = sessionStarted + Duration.ofSeconds(lifetime).toNanos();
				ownerUuid = uuid;
			} catch (RuntimeException invalid) { throw new IOException("SafariUtils authentication returned invalid session data"); }
			failedUuid = null;
			DebugLog.line("SYNC_NET", "UUID service session established");
			StructuredDiagnostics.decision("network-auth", "established", "method=uuid-claim");
			return token;
		} catch (IOException error) {
			failedUuid = uuid;
			retryAfterNanos = System.nanoTime() + Duration.ofSeconds(30).toNanos();
			StructuredDiagnostics.decision("network-auth", "failed", "stage=" + stage);
			throw error;
		}
	}

	synchronized void invalidate(String rejectedToken) {
		if (!Objects.equals(token, rejectedToken)) return;
		token = null;
		ownerUuid = null;
		expiresAtNanos = 0L;
	}

	private static HttpResponse<String> send(URI uri, JsonObject body) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/json").header("User-Agent", "SafariUtils")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		return NetworkRuntime.authenticatedHttp().send(request, HttpBodies.utf8(32 * 1024));
	}

	private static JsonObject post(URI uri, JsonObject body) throws IOException, InterruptedException {
		HttpResponse<String> response = send(uri, body);
		if (response.statusCode() / 100 != 2) throw backendError(response);
		try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
		catch (RuntimeException malformed) { throw new IOException("SafariUtils authentication returned malformed data"); }
	}

	static IOException backendError(HttpResponse<String> response) {
		try {
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("message")) return new IOException(root.get("message").getAsString());
		} catch (RuntimeException ignored) { }
		return new IOException("SafariUtils service request failed with HTTP " + response.statusCode());
	}
}
