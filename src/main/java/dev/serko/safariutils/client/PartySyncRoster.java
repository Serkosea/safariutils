package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Resolves complete party and in-instance rosters for objective synchronization. */
final class PartySyncRoster {
	private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static final Pattern RANK = Pattern.compile("\\[[^]]+\\]");
	private static Object cachedLevel;
	private static long cachedTick = Long.MIN_VALUE;
	private static final Map<String, String> knownIdsByName = new LinkedHashMap<>();
	private static List<Member> cachedFullParty;
	private static List<Member> cachedPresent;

	private PartySyncRoster() { }

	static List<Member> fullParty() {
		refreshTickCache();
		if (cachedFullParty == null) cachedFullParty = computeFullParty();
		return cachedFullParty;
	}

	private static List<Member> computeFullParty() {
		if (!PartyRosterWatch.known()) return List.of();
		int expected = PartyRosterWatch.expectedPlayers();
		if (expected < 1 || expected > 4) return List.of();
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return List.of();
		Map<String, String> idsByName = onlineIds();
		Map<String, Member> members = new LinkedHashMap<>();
		for (String line : PartyRosterWatch.rosterLines()) {
			int colon = line.indexOf(':');
			if (colon < 0) continue;
			String names = RANK.matcher(line.substring(colon + 1)).replaceAll(" ");
			for (String token : names.split("\\s+")) {
				if (!USERNAME.matcher(token).matches() || "None".equalsIgnoreCase(token)) continue;
				String lower = token.toLowerCase(Locale.ROOT);
				String uuid = idsByName.get(lower);
				if (uuid != null) members.putIfAbsent(lower, new Member(token, uuid));
			}
		}
		if (expected == 1 && members.isEmpty()) {
			String name = client.player.getGameProfile().name();
			members.put(name.toLowerCase(Locale.ROOT), new Member(name, localUuid()));
		}
		return members.size() == expected ? sorted(members.values()) : List.of();
	}

	static List<Member> presentInSafari() {
		refreshTickCache();
		if (cachedPresent == null) cachedPresent = computePresentInSafari();
		return cachedPresent;
	}

	private static List<Member> computePresentInSafari() {
		if (!SafariLocation.inside()) return List.of();
		int expected = SafariPartyWatch.joinedPlayers();
		if (expected < 1 || expected > 4) return List.of();
		Map<String, String> idsByName = onlineIds();
		List<Member> members = SafariPartyWatch.presentPlayerNames().stream()
			.map(name -> new Member(name, idsByName.get(name.toLowerCase(Locale.ROOT))))
			.filter(member -> member.uuid() != null)
			.sorted(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
		return members.size() == expected ? members : List.of();
	}

	private static Map<String, String> onlineIds() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return Map.of();
		knownIdsByName.put(client.player.getGameProfile().name().toLowerCase(Locale.ROOT), localUuid());
		if (client.player.connection != null) {
			for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
				knownIdsByName.put(info.getProfile().name().toLowerCase(Locale.ROOT),
					compact(info.getProfile().id().toString()));
			}
		}
		return Map.copyOf(knownIdsByName);
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

	private static void refreshTickCache() {
		Minecraft client = Minecraft.getInstance();
		long tick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
		if (client.level == cachedLevel && tick == cachedTick) return;
		cachedLevel = client.level;
		cachedTick = tick;
		cachedFullParty = null;
		cachedPresent = null;
	}

	private static List<Member> sorted(java.util.Collection<Member> members) {
		return members.stream().sorted(Comparator.comparing(Member::name,
			String.CASE_INSENSITIVE_ORDER)).toList();
	}

	record Member(String name, String uuid) { }
}
