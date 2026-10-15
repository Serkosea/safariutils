package dev.serko.safariutils.backend;

import dev.serko.safariutils.client.CanonicalPlayerNames;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.client.SafariPartyWatch;
import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import net.minecraft.client.Minecraft;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** UUID-backed owner identity and adapters over the shared party snapshot. */
final class BackendPartyRoster {
	private static final Set<String> ALLOWED_UUIDS = Set.of(
		"bb28716d96ae15a537ff3d2e6de2b29c8d98f94f063d93778d9c9f9fb5558041");

	private BackendPartyRoster() { }

	static boolean allowed(String uuid) {
		try {
			byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
				.digest(compact(uuid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return ALLOWED_UUIDS.contains(java.util.HexFormat.of().formatHex(bytes));
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	/** Complete confirmed party; missing UUIDs are resolved by the API workflow. */
	static List<Member> apiParty(PartySnapshot snapshot) {
		if (!snapshot.completeRoster() || snapshot.expectedPlayers() > 4) return List.of();
		List<Member> members = snapshot.members().stream().map(member -> {
			CanonicalPlayerNames.rememberProfile(member.name(), member.uuid());
			return new Member(member.name(), member.uuid());
		}).sorted(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER)).toList();
		return members.size() == snapshot.expectedPlayers() ? members : List.of();
	}

	/** Players currently confirmed in this Safari instance. */
	static List<Member> presentInSafari() {
		if (!SafariLocation.inside()) return List.of();
		PartySnapshot snapshot = PartyState.snapshot();
		List<Member> members = snapshot.presentInSafari().stream()
			.map(member -> new Member(member.name(), member.uuid()))
			.sorted(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
		return members.size() == SafariPartyWatch.joinedPlayers() ? members : List.of();
	}

	static String localUuid() {
		var player = Minecraft.getInstance().player;
		return player == null ? "" : compact(player.getGameProfile().id().toString());
	}

	static String localName() {
		var player = Minecraft.getInstance().player;
		return player == null ? "" : player.getGameProfile().name();
	}

	static String compact(String value) {
		return value == null ? "" : value.replace("-", "").toLowerCase(Locale.ROOT);
	}

	record Member(String name, String uuid) { }
}
