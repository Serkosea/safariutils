package dev.serko.safariutils.client;

import dev.serko.safariutils.session.SessionManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Timed, opt-in party commands for trading Safari entry windows with trusted players. */
public final class TicketTrading {
	private static final long INVITE_AT_MILLIS = 20_000L;
	private static final long WARP_AT_MILLIS = 27_000L;
	private static final long LAST_WARP_AT_MILLIS = 29_500L;
	private static final long SPARKLING_INVITE_DEADLINE_MILLIS = 25_000L;
	private static final long COMMAND_COOLDOWN_MILLIS = 3_100L;
	private static final long ROSTER_CONFIRM_AT_MILLIS = 60_000L;
	private static final Pattern INVITE = Pattern.compile(
		"^(?:You have been invited to join\\s+)?(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16})(?:'s party| has invited you to join (?:their|his|her) party)!?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PARTY_JOIN = Pattern.compile(
		"^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) joined the party\\.?$",
		Pattern.CASE_INSENSITIVE);

	private static final Set<String> joinedTargets = new HashSet<>();
	private static final Set<String> confirmedRunMembers = new HashSet<>();
	private static final Set<String> activeTargets = new LinkedHashSet<>();
	private static final Set<String> invitedTargets = new LinkedHashSet<>();
	private static List<String> normalTargets = List.of();
	private static List<String> sparklingTargets = List.of();
	private static long connectionStartedAt;
	private static long lastHostCommandAt;
	private static boolean hostScheduled;
	private static boolean tradeRunActive;
	private static boolean initialInviteWindowOpened;
	private static boolean warpSent;
	private static boolean rosterMinuteChecked;
	private static boolean sparklingFoundThisVisit;
	private static String lastAccepted = "";
	private static long lastAcceptedAt;
	private static String pendingAcceptInviter = "";
	private static long pendingAcceptAt;

	private record ConfiguredPlayer(String name, boolean sparklingOnly) { }

	private TicketTrading() { }

	/** Snapshots the opt-in and trusted names only when a ticket actually starts a run. */
	public static void onRunStarted() {
		resetRunAutomation();
		if (!ConfigManager.get().sparkling.ticketTradingEnabled) return;
		long elapsed = SessionManager.ticketWindowElapsedMillis();
		if (elapsed < 0L || elapsed >= INVITE_AT_MILLIS) {
			OperationalLog.info("TICKET_TRADE", "Not scheduled: ticket accepted after invite deadline");
			return;
		}
		List<ConfiguredPlayer> players = configuredPlayerEntries();
		if (players.isEmpty()) return;
		normalTargets = players.stream().filter(player -> !player.sparklingOnly())
			.map(ConfiguredPlayer::name).toList();
		sparklingTargets = players.stream().filter(ConfiguredPlayer::sparklingOnly)
			.map(ConfiguredPlayer::name).toList();
		connectionStartedAt = System.currentTimeMillis() - elapsed;
		hostScheduled = true;
		tradeRunActive = true;
		OperationalLog.info("TICKET_TRADE", "Scheduled normal=" + normalTargets.size()
			+ " sparkling=" + sparklingTargets.size()
			+ " alreadyFound=" + sparklingFoundThisVisit);
	}

	public static void tick() {
		long now = System.currentTimeMillis();
		if (pendingAcceptAt > 0L) {
			if (!ConfigManager.get().sparkling.ticketTradingEnabled) clearPendingAccept();
			else if (now >= pendingAcceptAt) {
				sendCommand("party accept " + pendingAcceptInviter);
				clearPendingAccept();
			}
		}
		if (!hostScheduled && !tradeRunActive) return;
		if (tradeRunActive && !rosterMinuteChecked
				&& SafariLocation.inside()
				&& now >= connectionStartedAt + ROSTER_CONFIRM_AT_MILLIS) {
			List<String> presentNames = SafariPartyWatch.presentPlayerNames();
			if (!presentNames.isEmpty()) rosterMinuteChecked = true;
			java.util.Map<String, String> present = new java.util.HashMap<>();
			for (String name : presentNames) {
				present.put(name.toLowerCase(Locale.ROOT), name);
			}
			for (String target : activeTargets) {
				if (present.containsKey(target)) {
					confirmRunMember(target, present.get(target), "present at 60s");
				}
			}
		}
		if (!hostScheduled) return;
		// Once warped, keep the promised cleanup armed even if HUD/location state briefly
		// resets during a transfer. Before warp, either condition safely cancels the plan.
		if ((!ConfigManager.get().sparkling.ticketTradingEnabled || !SafariLocation.inside())
				&& !warpSent) {
			resetRunAutomation();
			return;
		}
		long elapsed = now - connectionStartedAt;
		if (!initialInviteWindowOpened && elapsed >= INVITE_AT_MILLIS) {
			initialInviteWindowOpened = true;
		}
		if (initialInviteWindowOpened && !warpSent
				&& elapsed <= SPARKLING_INVITE_DEADLINE_MILLIS
				&& now - lastHostCommandAt >= COMMAND_COOLDOWN_MILLIS
				&& hasPendingInviteTargets()) {
			List<String> pending = pendingInviteTargets();
			if (!pending.isEmpty()) {
				sendCommand("party invite " + String.join(" ", pending));
				lastHostCommandAt = now;
				invitedTargets.addAll(pending);
				activeTargets.addAll(pending);
			}
		}
		boolean hasJoinedPlayer = !joinedTargets.isEmpty();
		// Always preserve the full scouting window: even a complete party waits until 27s.
		// A first late join between 27s and 29s is picked up on the next client tick.
		boolean warpReady = hasJoinedPlayer && now >= connectionStartedAt + WARP_AT_MILLIS
			&& now - lastHostCommandAt >= COMMAND_COOLDOWN_MILLIS;
		if (!warpSent && warpReady && now < connectionStartedAt + LAST_WARP_AT_MILLIS) {
			sendCommand("party warp");
			lastHostCommandAt = now;
			warpSent = true;
		}
		if (!warpSent && now >= connectionStartedAt + LAST_WARP_AT_MILLIS) {
			if (!invitedTargets.isEmpty()) sendCommand("party disband");
			resetRunAutomation();
		}
	}

	/** Qualifies Sparkling-only traders while a pre-ticket or active invite window remains. */
	public static void onSparklingDetected() {
		sparklingFoundThisVisit = true;
		OperationalLog.info("TICKET_TRADE", "Sparkling qualified this Safari visit");
	}

	/** Handles trusted invitations and observes the host party filling up. */
	public static void onChatMessage(String line) {
		if (line == null || line.isBlank()) return;
		Matcher joined = PARTY_JOIN.matcher(line);
		if (joined.matches() && hostScheduled) {
			String name = joined.group(1).toLowerCase(Locale.ROOT);
			if (activeTargets.contains(name)) joinedTargets.add(name);
		}
		if (!ConfigManager.get().sparkling.ticketTradingEnabled) return;
		Matcher invite = INVITE.matcher(line);
		if (!invite.matches()) return;
		String inviter = invite.group(1);
		String normalized = inviter.toLowerCase(Locale.ROOT);
		ConfiguredPlayer trusted = configuredPlayerEntries().stream()
			.filter(player -> player.name().equals(normalized)).findFirst().orElse(null);
		if (trusted == null) return;
		long now = System.currentTimeMillis();
		if (normalized.equals(lastAccepted) && now - lastAcceptedAt < 5_000L) return;
		lastAccepted = normalized;
		lastAcceptedAt = now;
		if (trusted.sparklingOnly()
				&& (!PartyRosterWatch.known() || PartyRosterWatch.inParty())) {
			sendCommand("party leave");
			pendingAcceptInviter = inviter;
			pendingAcceptAt = now + COMMAND_COOLDOWN_MILLIS;
			OperationalLog.info("TICKET_TRADE", "Leaving current party before Sparkling trade accept");
		} else {
			sendCommand("party accept " + inviter);
		}
	}

	/** A loot share proves that an invited trader participated, even if they leave early. */
	public static void onSharedCatch(String catcher) {
		String normalized = normalizeName(catcher);
		if (tradeRunActive && activeTargets.contains(normalized)) {
			confirmRunMember(normalized, catcher, "loot share");
		}
	}

	/** Leaving the host's ticketed instance closes a successfully warped trading party. */
	public static void onConnectionJoin() {
		if (tradeRunActive && warpSent) sendCommand("party disband");
		resetRunAutomation();
		sparklingFoundThisVisit = false;
	}

	public static void onDisconnect() {
		resetRunAutomation();
		clearPendingAccept();
		sparklingFoundThisVisit = false;
	}

	public static List<String> configuredPlayers() {
		return configuredPlayerEntries().stream().map(ConfiguredPlayer::name).toList();
	}

	private static List<ConfiguredPlayer> configuredPlayerEntries() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		String[] values = {config.ticketTradingPlayer1, config.ticketTradingPlayer2,
			config.ticketTradingPlayer3};
		boolean[] sparkling = {config.ticketTradingSparkling1, config.ticketTradingSparkling2,
			config.ticketTradingSparkling3};
		List<ConfiguredPlayer> result = new ArrayList<>(3);
		Set<String> seen = new HashSet<>();
		String self = Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT);
		for (int index = 0; index < values.length; index++) {
			String normalized = normalizeName(values[index]);
			if (!normalized.isEmpty() && !normalized.equals(self) && seen.add(normalized)) {
				result.add(new ConfiguredPlayer(normalized, sparkling[index]));
			}
		}
		return List.copyOf(result);
	}

	static String normalizeName(String value) {
		if (value == null) return "";
		String trimmed = value.trim();
		return trimmed.matches("[A-Za-z0-9_]{1,16}") ? trimmed.toLowerCase(Locale.ROOT) : "";
	}

	private static void sendCommand(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return;
		client.getConnection().sendCommand(command);
		OperationalLog.info("TICKET_TRADE", "Sent /" + command.split(" ")[0] + " "
			+ command.split(" ")[1]);
	}

	private static void confirmRunMember(String normalized, String displayName, String reason) {
		if (!confirmedRunMembers.add(normalized)) return;
		SessionManager.onTicketTradingMemberConfirmed(displayName);
		OperationalLog.info("TICKET_TRADE", "Confirmed traded run member via " + reason);
	}

	private static void cancelAutomation() {
		joinedTargets.clear();
		hostScheduled = false;
		initialInviteWindowOpened = false;
		warpSent = false;
	}

	private static void resetRunAutomation() {
		cancelAutomation();
		confirmedRunMembers.clear();
		activeTargets.clear();
		invitedTargets.clear();
		normalTargets = List.of();
		sparklingTargets = List.of();
		connectionStartedAt = 0L;
		lastHostCommandAt = 0L;
		tradeRunActive = false;
		rosterMinuteChecked = false;
	}

	private static List<String> pendingInviteTargets() {
		List<String> pending = new ArrayList<>(3);
		for (String target : normalTargets) {
			if (!invitedTargets.contains(target)) pending.add(target);
		}
		if (sparklingFoundThisVisit) {
			for (String target : sparklingTargets) {
				if (!invitedTargets.contains(target)) pending.add(target);
			}
		}
		return pending;
	}

	private static boolean hasPendingInviteTargets() {
		for (String target : normalTargets) {
			if (!invitedTargets.contains(target)) return true;
		}
		if (!sparklingFoundThisVisit) return false;
		for (String target : sparklingTargets) {
			if (!invitedTargets.contains(target)) return true;
		}
		return false;
	}

	private static void clearPendingAccept() {
		pendingAcceptInviter = "";
		pendingAcceptAt = 0L;
	}
}
