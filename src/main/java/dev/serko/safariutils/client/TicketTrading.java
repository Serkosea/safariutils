package dev.serko.safariutils.client;

import dev.serko.safariutils.session.SessionManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Timed, opt-in party commands for trading Safari entry windows with trusted players. */
public final class TicketTrading {
	private static final long INVITE_AT_MILLIS = 20_000L;
	private static final long WARP_AT_MILLIS = 27_000L;
	private static final long LAST_WARP_AT_MILLIS = 29_000L;
	private static final long ROSTER_CONFIRM_AT_MILLIS = 60_000L;
	private static final Pattern INVITE = Pattern.compile(
		"^(?:You have been invited to join\\s+)?(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16})(?:'s party| has invited you to join (?:their|his|her) party)!?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PARTY_JOIN = Pattern.compile(
		"^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) joined the party\\.?$",
		Pattern.CASE_INSENSITIVE);

	private static final Set<String> joinedTargets = new HashSet<>();
	private static final Set<String> confirmedRunMembers = new HashSet<>();
	private static List<String> activeTargets = List.of();
	private static long connectionStartedAt;
	private static boolean hostScheduled;
	private static boolean tradeRunActive;
	private static boolean invitesSent;
	private static boolean warpSent;
	private static boolean rosterMinuteChecked;
	private static String lastAccepted = "";
	private static long lastAcceptedAt;

	private TicketTrading() { }

	/** Snapshots the opt-in and trusted names only when a ticket actually starts a run. */
	public static void onRunStarted() {
		resetAll();
		if (!ConfigManager.get().sparkling.ticketTradingEnabled) return;
		long elapsed = SessionManager.ticketWindowElapsedMillis();
		if (elapsed < 0L || elapsed >= INVITE_AT_MILLIS) {
			OperationalLog.info("TICKET_TRADE", "Not scheduled: ticket accepted after invite deadline");
			return;
		}
		activeTargets = configuredPlayers();
		if (activeTargets.isEmpty()) return;
		connectionStartedAt = System.currentTimeMillis() - elapsed;
		hostScheduled = true;
		tradeRunActive = true;
		OperationalLog.info("TICKET_TRADE", "Scheduled for " + activeTargets.size() + " trusted player(s)");
	}

	public static void tick() {
		if (!hostScheduled && !tradeRunActive) return;
		long now = System.currentTimeMillis();
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
			resetAll();
			return;
		}
		if (!invitesSent && now >= connectionStartedAt + INVITE_AT_MILLIS) {
			sendCommand("party invite " + String.join(" ", activeTargets));
			invitesSent = true;
		}
		boolean hasJoinedPlayer = !joinedTargets.isEmpty();
		// Always preserve the full scouting window: even a complete party waits until 27s.
		// A first late join between 27s and 29s is picked up on the next client tick.
		boolean warpReady = hasJoinedPlayer && now >= connectionStartedAt + WARP_AT_MILLIS;
		if (!warpSent && warpReady && now < connectionStartedAt + LAST_WARP_AT_MILLIS) {
			sendCommand("party warp");
			warpSent = true;
		}
		if (!warpSent && now >= connectionStartedAt + LAST_WARP_AT_MILLIS) {
			sendCommand("party disband");
			resetAll();
		}
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
		if (!configuredPlayers().contains(normalized)) return;
		long now = System.currentTimeMillis();
		if (normalized.equals(lastAccepted) && now - lastAcceptedAt < 5_000L) return;
		lastAccepted = normalized;
		lastAcceptedAt = now;
		sendCommand("party accept " + inviter);
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
		resetAll();
	}

	public static void onDisconnect() {
		resetAll();
	}

	public static List<String> configuredPlayers() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		String[] values = {config.ticketTradingPlayer1, config.ticketTradingPlayer2,
			config.ticketTradingPlayer3};
		List<String> result = new ArrayList<>(3);
		Set<String> seen = new HashSet<>();
		String self = Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT);
		for (String value : values) {
			String normalized = normalizeName(value);
			if (!normalized.isEmpty() && !normalized.equals(self) && seen.add(normalized)) {
				result.add(normalized);
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
		invitesSent = false;
		warpSent = false;
	}

	private static void resetAll() {
		cancelAutomation();
		confirmedRunMembers.clear();
		activeTargets = List.of();
		connectionStartedAt = 0L;
		tradeRunActive = false;
		rosterMinuteChecked = false;
	}
}
