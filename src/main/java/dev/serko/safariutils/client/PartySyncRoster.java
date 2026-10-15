package dev.serko.safariutils.client;

import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import net.minecraft.client.Minecraft;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Adapts the shared immutable party snapshot for objective synchronization. */
final class PartySyncRoster {
	private PartySyncRoster() { }

	static List<Member> fullParty() {
		PartySnapshot snapshot = PartyState.snapshot();
		if (!snapshot.completeRoster() || snapshot.expectedPlayers() > 4) return List.of();
		List<Member> members = snapshot.members().stream()
			.filter(member -> !member.uuid().isEmpty())
			.map(member -> new Member(member.name(), member.uuid()))
			.sorted(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
		return members.size() == snapshot.expectedPlayers() ? members : List.of();
	}

	static List<Member> presentInSafari() {
		if (!SafariLocation.inside()) return List.of();
		PartySnapshot snapshot = PartyState.snapshot();
		List<Member> members = snapshot.presentInSafari().stream()
			.filter(member -> !member.uuid().isEmpty())
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
