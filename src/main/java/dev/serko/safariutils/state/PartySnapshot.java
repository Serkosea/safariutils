package dev.serko.safariutils.state;

import java.util.List;
import java.util.Locale;

/** Immutable authoritative view of party membership and current Safari attendance. */
public record PartySnapshot(long generation, long capturedAtMillis, Status status,
		boolean localLeader, int expectedPlayers, List<Member> members,
		long attendanceCapturedAtMillis, List<Member> presentInSafari) {
	public enum Status { UNKNOWN, SOLO, PARTY, TRANSITIONING }

	public record Member(String name, String uuid) {
		public Member {
			name = name == null ? "" : name;
			uuid = uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
		}

		public String key() {
			return !uuid.isEmpty() ? uuid : name.toLowerCase(Locale.ROOT);
		}
	}

	public PartySnapshot {
		status = status == null ? Status.UNKNOWN : status;
		expectedPlayers = Math.max(1, expectedPlayers);
		members = members == null ? List.of() : List.copyOf(members);
		presentInSafari = presentInSafari == null ? List.of() : List.copyOf(presentInSafari);
	}

	public boolean known() {
		return status == Status.SOLO || status == Status.PARTY;
	}

	public boolean inParty() {
		return known() && expectedPlayers > 1;
	}

	public boolean completeRoster() {
		return known() && members.size() == expectedPlayers;
	}

	public boolean containsName(String name) {
		if (name == null || name.isBlank()) return false;
		return known() && members.stream().anyMatch(member -> member.name().equalsIgnoreCase(name));
	}
}
