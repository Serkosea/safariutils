package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * Privacy-safe probe for values that could identify one temporary Safari room.
 *
 * <p>Only short, domain-separated hashes are exposed. The lobby label and entity UUID
 * stay local so test reports cannot reveal the eventual room secret.
 */
public final class SafariRoomIdentityProbe {
	private static final String DOMAIN = "SURoom";
	private static final String PROTOCOL = "1";
	private static final double MANAGER_X = -51.5;
	private static final double MANAGER_Y = 68.0;
	private static final double MANAGER_Z = 21.5;
	private static final double MANAGER_RADIUS_SQUARED = 9.0;
	private static final int FINGERPRINT_BYTES = 6;

	public record Result(String roomFingerprint, String managerFingerprint,
		String combinedFingerprint, int managerCandidates, double managerDistance) {
	}

	private SafariRoomIdentityProbe() {
	}

	/** Returns no result until both the lobby label and Manager entity are available. */
	public static Result inspect() {
		if (!SafariLocation.inside()) return null;
		String lobby = SafariLocation.lobbyId();
		if (lobby == null || lobby.isBlank()) return null;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return null;
		var candidates = new ArrayList<Entity>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Player
				&& entity.distanceToSqr(MANAGER_X, MANAGER_Y, MANAGER_Z)
				<= MANAGER_RADIUS_SQUARED) candidates.add(entity);
		}
		candidates.sort(Comparator.comparingDouble(entity ->
			entity.distanceToSqr(MANAGER_X, MANAGER_Y, MANAGER_Z)));
		if (candidates.isEmpty()) return null;

		Entity manager = candidates.getFirst();
		String managerId = manager.getUUID().toString();
		return new Result(
			fingerprint(DOMAIN + "\u0000room\u0000" + PROTOCOL + "\u0000" + lobby),
			fingerprint(DOMAIN + "\u0000manager\u0000" + PROTOCOL + "\u0000" + managerId),
			fingerprint(DOMAIN + "\u0000key-candidate\u0000" + PROTOCOL + "\u0000"
				+ lobby + "\u0000" + managerId),
			candidates.size(),
			Math.sqrt(manager.distanceToSqr(MANAGER_X, MANAGER_Y, MANAGER_Z)));
	}

	private static String fingerprint(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().withUpperCase().formatHex(digest, 0, FINGERPRINT_BYTES);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
