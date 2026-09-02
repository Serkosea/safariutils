package dev.serko.safariutils.client;

import java.util.regex.Pattern;

/** Reads and stabilizes Hypixel's four-player Safari count for the current instance. */
public final class SafariPartyWatch {
	private static final Pattern PLAYER_COUNT = Pattern.compile("^Players \\((\\d+)\\)$",
		Pattern.CASE_INSENSITIVE);
	private static final long INSTANCE_GRACE_MILLIS = 500L;
	private static final long ROSTER_STABLE_MILLIS = 350L;
	/** Extra certainty before persistent entity-location learning treats a run as solo. */
	private static final long SOLO_LEARNING_STABLE_MILLIS = 3_000L;
	private static String lobbyId;
	private static int joinedPlayers;
	private static int candidatePlayers = -1;
	private static long instanceObservedAt;
	private static long candidateSince;
	private static boolean fullPartyAnnounced;
	private static boolean otherPlayerSeenThisInstance;
	private static boolean graceLogged;

	private SafariPartyWatch() {
	}

	public static void tick() {
		if (!SafariLocation.inside()) {
			reset();
			return;
		}

		long now = System.currentTimeMillis();
		String currentLobby = SafariLocation.lobbyId();
		if (instanceObservedAt == 0) {
			lobbyId = currentLobby;
			beginInstance(now);
		} else if (lobbyId == null && currentLobby != null) {
			lobbyId = currentLobby;
		} else if (currentLobby != null && !currentLobby.equals(lobbyId)) {
			lobbyId = currentLobby;
			beginInstance(now);
		}

		// Minecraft briefly leaves the previous island's tab data installed after the
		// new Safari area appears. Treat only the local player as present until that
		// transition window passes, then require Players (N) to stop changing before
		// exposing it or announcing 4/4.
		if (now - instanceObservedAt < INSTANCE_GRACE_MILLIS) return;
		if (!graceLogged) {
			graceLogged = true;
			DebugLog.line("PARTYTIME", "grace complete after " + (now - instanceObservedAt)
				+ "ms lobby=" + lobbyId);
		}
		int observed = tabListPlayerCount();
		if (observed != candidatePlayers) {
			DebugLog.line("PARTYTIME", "candidate " + candidatePlayers + " -> " + observed
				+ " at +" + (now - instanceObservedAt) + "ms source=" + playerCountLine());
			candidatePlayers = observed;
			candidateSince = now;
			return;
		}
		if (now - candidateSince < ROSTER_STABLE_MILLIS) return;

		if (joinedPlayers != observed) {
			DebugLog.line("PARTYTIME", "accepted " + observed + "/4 after "
				+ (now - candidateSince) + "ms stable at +" + (now - instanceObservedAt) + "ms");
			joinedPlayers = observed;
		}
		if (observed > 1) otherPlayerSeenThisInstance = true;
		// Manager activation must not suppress this: a player can turn in their ticket
		// before the tab-list roster has remained stable long enough to announce it.
		int expected = PartyRosterWatch.expectedPlayers();
		if (PartyRosterWatch.known() && expected > 1 && joinedPlayers >= expected && !fullPartyAnnounced) {
			fullPartyAnnounced = true;
			DebugLog.line("PARTYTIME", "announced " + expected + "/" + expected + " at +"
				+ (now - instanceObservedAt) + "ms");
			EncounterAlerts.fireFullPartyJoined(expected);
		}
	}

	public static int joinedPlayers() {
		return Math.clamp(joinedPlayers, 0, 4);
	}

	/** Records the attendance state used by the Manager click guard. */
	public static void onEntityUse(net.minecraft.world.entity.Entity entity) {
		if (!SafariLocation.inside() || entity == null) return;
		DebugLog.line("PARTYTIME", "use entity type=" + EntityTypeIds.key(entity)
			+ " name=\"" + entity.getName().getString() + "\" at="
			+ entity.blockPosition().toShortString() + " accepted=" + joinedPlayers
			+ " candidate=" + candidatePlayers + " stableFor="
			+ (candidateSince == 0 ? -1 : System.currentTimeMillis() - candidateSince) + "ms");
	}

	/**
	 * True only after the current instance has reported one player continuously for a
	 * few seconds. The longer window prevents a partially loaded party roster from
	 * contaminating persistent initial-spawn data.
	 */
	public static boolean confirmedSoloForLearning() {
		return !otherPlayerSeenThisInstance && joinedPlayers == 1
			&& candidatePlayers == 1 && candidateSince > 0
			&& System.currentTimeMillis() - candidateSince >= SOLO_LEARNING_STABLE_MILLIS;
	}

	private static int tabListPlayerCount() {
		for (String entry : SafariLocation.tabListEntries()) {
			var matcher = PLAYER_COUNT.matcher(entry.trim());
			if (!matcher.matches()) continue;
			int count = Integer.parseInt(matcher.group(1));
			// Counts above four can only belong to the previous public lobby. The
			// current private Safari always contains the local player, so fail to 1/4.
			return count >= 1 && count <= 4 ? count : 1;
		}
		return 1;
	}

	private static String playerCountLine() {
		for (String entry : SafariLocation.tabListEntries()) {
			if (PLAYER_COUNT.matcher(entry.trim()).matches()) return '"' + entry.trim() + '"';
		}
		return "missing";
	}

	private static void beginInstance(long now) {
		// Lobby entry, not Manager activation, is the lifetime boundary for detected
		// Sparklings. This preserves anything found during the pre-run ticket window.
		SparklingWatch.reset();
		joinedPlayers = 1;
		candidatePlayers = -1;
		candidateSince = 0;
		instanceObservedAt = now;
		fullPartyAnnounced = false;
		otherPlayerSeenThisInstance = false;
		graceLogged = false;
		DebugLog.line("PARTYTIME", "instance begin lobby=" + lobbyId);
	}

	private static void reset() {
		lobbyId = null;
		joinedPlayers = 0;
		candidatePlayers = -1;
		candidateSince = 0;
		instanceObservedAt = 0;
		fullPartyAnnounced = false;
		otherPlayerSeenThisInstance = false;
		graceLogged = false;
	}
}
