package dev.serko.safariutils.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.client.NetworkRuntime;
import dev.serko.safariutils.concurrent.HttpBodies;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.StructuredDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Owns a short-lived SafariUtils backend session proven by Minecraft's profile key.
 * The private key is used only by {@link Signature} and never leaves this process.
 */
final class RemoteSession {
	private static final URI CHALLENGE = BackendEndpoints.CHALLENGE;
	private static final URI SESSION = BackendEndpoints.SESSION;
	private static final long REFRESH_MARGIN_MILLIS = 2 * 60_000L;
	private static final RemoteSession INSTANCE = new RemoteSession();

	private String token;
	private String ownerUuid;
	private long expiresAt;

	private RemoteSession() { }

	static RemoteSession instance() {
		return INSTANCE;
	}

	synchronized String token() throws IOException, InterruptedException {
		long now = System.currentTimeMillis();
		Minecraft minecraft = Minecraft.getInstance();
		String uuid = minecraft.getUser().getProfileId().toString().replace("-", "")
			.toLowerCase(Locale.ROOT);
		if (uuid.equals(ownerUuid) && token != null && now + REFRESH_MARGIN_MILLIS < expiresAt) return token;
		token = null;
		ownerUuid = null;
		ProfileKeyPair pair;
		try {
			pair = minecraft.getProfileKeyPairManager().prepareKeyPair().get(15, TimeUnit.SECONDS)
				.orElseThrow(() -> new IOException("Minecraft profile authentication is unavailable"));
		} catch (ExecutionException | TimeoutException unavailable) {
			throw new IOException("Minecraft profile authentication is unavailable", unavailable);
		}

		JsonObject challengeRequest = new JsonObject();
		challengeRequest.addProperty("uuid", uuid);
		JsonObject challenge = post(CHALLENGE, challengeRequest);
		String challengeId = challenge.get("challengeId").getAsString();
		String nonce = challenge.get("challenge").getAsString();
		byte[] proof = sign(pair, ("SafariUtils-Auth-v1\n" + challengeId + "\n" + nonce)
			.getBytes(StandardCharsets.UTF_8));
		var certificate = pair.publicKey().data();
		boolean certificateValid = validatesLocally(minecraft, certificate);
		boolean proofValid = verifiesProof(certificate, proof,
			("SafariUtils-Auth-v1\n" + challengeId + "\n" + nonce)
				.getBytes(StandardCharsets.UTF_8));
		DebugLog.line("SYNC_NET", "local profile proof certificate=" + certificateValid
			+ " signature=" + proofValid);
		StructuredDiagnostics.decision("network-auth", "local-proof",
			"certificate=" + certificateValid + ",signature=" + proofValid);

		JsonObject sessionRequest = new JsonObject();
		sessionRequest.addProperty("challengeId", challengeId);
		sessionRequest.addProperty("uuid", uuid);
		sessionRequest.addProperty("expiresAt", certificate.expiresAt().toEpochMilli());
		sessionRequest.addProperty("publicKey", Base64.getEncoder()
			.encodeToString(certificate.key().getEncoded()));
		sessionRequest.addProperty("keySignature", Base64.getEncoder()
			.encodeToString(certificate.keySignature()));
		sessionRequest.addProperty("proof", Base64.getEncoder().encodeToString(proof));
		JsonObject session = post(SESSION, sessionRequest);
		if (!uuid.equals(minecraft.getUser().getProfileId().toString().replace("-", "")
				.toLowerCase(Locale.ROOT))) throw new IOException("Minecraft account changed during authentication");
		try {
			String receivedToken = session.get("token").getAsString();
			long receivedExpiry = session.get("expiresAt").getAsLong();
			if (receivedToken.isBlank() || receivedToken.length() > 4096
					|| receivedExpiry <= System.currentTimeMillis() + REFRESH_MARGIN_MILLIS) {
				throw new IOException("SafariUtils authentication returned an invalid session");
			}
			token = receivedToken;
			expiresAt = receivedExpiry;
			ownerUuid = uuid;
		} catch (RuntimeException malformed) {
			throw new IOException("SafariUtils authentication returned malformed session data", malformed);
		}
		DebugLog.line("SYNC_NET", "minecraft profile authenticated tokenMinutes="
			+ Math.max(0L, (expiresAt - System.currentTimeMillis()) / 60_000L));
		StructuredDiagnostics.decision("network-auth", "established",
			"ttlMinutes=" + Math.max(0L, (expiresAt - System.currentTimeMillis()) / 60_000L));
		return token;
	}

	synchronized void invalidate(String rejectedToken) {
		if (!Objects.equals(token, rejectedToken)) return;
		token = null;
		ownerUuid = null;
		expiresAt = 0L;
	}

	private static byte[] sign(ProfileKeyPair pair, byte[] payload) throws IOException {
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(pair.privateKey());
			signature.update(payload);
			return signature.sign();
		} catch (GeneralSecurityException error) {
			throw new IOException("Could not prove Minecraft profile ownership", error);
		}
	}

	private static boolean validatesLocally(Minecraft minecraft,
			ProfilePublicKey.Data certificate) {
		try {
			ProfilePublicKey.createValidated(minecraft.services().profileKeySignatureValidator(),
				minecraft.getUser().getProfileId(), certificate);
			return true;
		} catch (ProfilePublicKey.ValidationException | RuntimeException invalid) {
			return false;
		}
	}

	private static boolean verifiesProof(ProfilePublicKey.Data certificate, byte[] proof,
			byte[] payload) {
		try {
			Signature verifier = Signature.getInstance("SHA256withRSA");
			verifier.initVerify(certificate.key());
			verifier.update(payload);
			return verifier.verify(proof);
		} catch (GeneralSecurityException invalid) {
			return false;
		}
	}

	private static JsonObject post(URI uri, JsonObject body)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/json")
			.header("User-Agent", "SafariUtils-Developer")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		HttpResponse<String> response = NetworkRuntime.authenticatedHttp().send(builder.build(),
			HttpBodies.utf8(32 * 1024));
		if (response.statusCode() / 100 != 2) throw backendError(response);
		try {
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (RuntimeException malformed) {
			throw new IOException("SafariUtils authentication returned malformed data", malformed);
		}
	}

	static IOException backendError(HttpResponse<String> response) {
		try {
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("message")) return new IOException(root.get("message").getAsString());
		} catch (RuntimeException ignored) { }
		return new IOException("SafariUtils service request failed with HTTP " + response.statusCode());
	}
}
