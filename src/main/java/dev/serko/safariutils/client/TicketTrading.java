package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
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
	static final long INVITE_AT_MILLIS = 20_000L;
	static final long SPARKLING_BATCH_FROM_MILLIS = 17_000L;
	static final long WARP_AT_MILLIS = 27_000L;
	static final long LAST_WARP_AT_MILLIS = 29_500L;
	static final long SPARKLING_INVITE_DEADLINE_MILLIS = 25_000L;
	static final long COMMAND_COOLDOWN_MILLIS = 3_100L;
	static final long BACKUP_INVITE_DEADLINE_MILLIS =
		LAST_WARP_AT_MILLIS - COMMAND_COOLDOWN_MILLIS - 100L;
	static final long BACKUP_INVITE_DELAY_MILLIS = 3_000L;
	static final long INVITE_RESPONSE_WINDOW_MILLIS = 5_000L;
	static final long GUEST_LEAVE_SETTLE_MILLIS = 1_100L;
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
	private static final Set<String> failedPrimaryTargets = new LinkedHashSet<>();
	private static List<String> normalTargets = List.of();
	private static List<ConfiguredPlayer> sparklingTargets = List.of();
	private static List<ConfiguredPlayer> backupTargets = List.of();
	private static List<String> lastPrimaryInviteBatch = List.of();
	private static long connectionStartedAt;
	private static long lastHostCommandAt;
	private static long inviteResponseUntil;
	private static long backupInviteAt;
	private static int backupReplacementsSent;
	private static boolean hostQualificationPending;
	private static boolean hostOwnedRun;
	private static boolean hostScheduled;
	private static boolean tradeRunActive;
	private static boolean warpSent;
	private static boolean rosterMinuteChecked;
	private static long sparklingFoundThisVisit;
	private static String lastAccepted = "";
	private static long lastAcceptedAt;
	private static String pendingAcceptInviter = "";
	private static long pendingAcceptAt;
	private static boolean testMode;

	private record ConfiguredPlayer(String name, boolean sparklingOnly, long sparklingCritters) { }

	private TicketTrading() { }

	/** Snapshots trusted names, then waits for current-instance proof that this client is host. */
	public static void onRunStarted() {
		resetRunAutomation();
		if (testMode) return;
		if (!ConfigManager.get().sparkling.ticketTradingEnabled) return;
		long elapsed = SessionManager.ticketWindowElapsedMillis();
		if (elapsed < 0L || elapsed >= INVITE_AT_MILLIS) {
			OperationalLog.info("TICKET_TRADE", "Not scheduled: ticket accepted after invite deadline");
			return;
		}
		List<ConfiguredPlayer> players = configuredPlayerEntries(0, TicketTradingProfiles.ACTIVE_SLOTS);
		if (players.isEmpty()) return;
		normalTargets = players.stream().filter(player -> !player.sparklingOnly())
			.map(ConfiguredPlayer::name).toList();
		sparklingTargets = players.stream().filter(ConfiguredPlayer::sparklingOnly).toList();
		backupTargets = configuredPlayerEntries(TicketTradingProfiles.ACTIVE_SLOTS,
			TicketTradingProfiles.TOTAL_SLOTS);
		connectionStartedAt = System.currentTimeMillis() - elapsed;
		hostQualificationPending = true;
		OperationalLog.info("TICKET_TRADE", "Waiting for host authority normal=" + normalTargets.size()
			+ " sparkling=" + sparklingTargets.size()
			+ " backups=" + backupTargets.size()
			+ " alreadyFound=" + Long.bitCount(sparklingFoundThisVisit));
	}

	public static void tick() {
		if (!HypixelConnection.active()) {
			onDisconnect();
			return;
		}
		if (testMode) return;
		long now = System.currentTimeMillis();
		if (pendingAcceptAt > 0L) {
			if (!ConfigManager.get().sparkling.ticketTradingEnabled) clearPendingAccept();
			else if (now >= pendingAcceptAt) {
				sendCommand("party accept " + pendingAcceptInviter);
				clearPendingAccept();
			}
		}
		qualifyHost(now);
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
		boolean commandReady = now - lastHostCommandAt >= COMMAND_COOLDOWN_MILLIS;
		if (!warpSent && commandReady && elapsed <= SPARKLING_INVITE_DEADLINE_MILLIS) {
			List<String> pending = elapsed < SPARKLING_BATCH_FROM_MILLIS
				? pendingSparklingTargets() : elapsed >= INVITE_AT_MILLIS
					? pendingPrimaryTargets() : List.of();
			if (!pending.isEmpty()) sendInviteBatch(pending, true, now);
		}
		int replacementsNeeded = failedPrimaryTargets.size() - backupReplacementsSent;
		if (!warpSent && replacementsNeeded > 0 && backupInviteAt > 0L
				&& elapsed >= INVITE_AT_MILLIS && now >= backupInviteAt
				&& elapsed <= BACKUP_INVITE_DEADLINE_MILLIS
				&& now - lastHostCommandAt >= COMMAND_COOLDOWN_MILLIS) {
			List<String> backups = pendingBackupTargets(replacementsNeeded);
			if (!backups.isEmpty()) {
				sendInviteBatch(backups, false, now);
				backupReplacementsSent += backups.size();
			}
			backupInviteAt = 0L;
		}
		if (backupInviteAt > 0L && elapsed > BACKUP_INVITE_DEADLINE_MILLIS) {
			backupInviteAt = 0L;
			OperationalLog.info("TICKET_TRADE", "Skipped backup invite: no safe warp window remained");
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
	public static void onSparklingDetected(Critter critter) {
		if (testMode) return;
		long bit = Critters.selectionMask(critter);
		if (bit == 0L || (sparklingFoundThisVisit & bit) != 0L) return;
		sparklingFoundThisVisit |= bit;
		OperationalLog.info("TICKET_TRADE", "Sparkling qualified this Safari visit species="
			+ critter.name());
	}

	/** Handles trusted invitations and observes the host party filling up. */
	public static void onChatMessage(String line) {
		if (testMode) return;
		if (line == null || line.isBlank()) return;
		long now = System.currentTimeMillis();
		if (hostScheduled && now <= inviteResponseUntil && isOfflineInviteFailure(line)) {
			recordOfflinePrimaryInvite(line, now);
		}
		Matcher joined = PARTY_JOIN.matcher(line);
		if (joined.matches() && hostScheduled) {
			String name = joined.group(1).toLowerCase(Locale.ROOT);
			if (activeTargets.contains(name)) {
				joinedTargets.add(name);
				lastPrimaryInviteBatch = lastPrimaryInviteBatch.stream()
					.filter(target -> !target.equals(name)).toList();
			}
		}
		if (!ConfigManager.get().sparkling.ticketTradingEnabled) return;
		Matcher invite = INVITE.matcher(line);
		if (!invite.matches()) return;
		String inviter = invite.group(1);
		String normalized = inviter.toLowerCase(Locale.ROOT);
		ConfiguredPlayer trusted = configuredPlayerEntries(0, TicketTradingProfiles.TOTAL_SLOTS).stream()
			.filter(player -> player.name().equals(normalized)).findFirst().orElse(null);
		if (trusted == null) return;
		if (normalized.equals(lastAccepted) && now - lastAcceptedAt < 5_000L) return;
		lastAccepted = normalized;
		lastAcceptedAt = now;
		if (trusted.sparklingOnly()
				&& (!PartyRosterWatch.known() || PartyRosterWatch.inParty())) {
			// Leave and accept share Hypixel's short command cooldown. Keep the
			// automatic roster refresh from occupying either side of that window.
			PartyRosterWatch.deferRefreshUntil(now + GUEST_LEAVE_SETTLE_MILLIS * 2L);
			sendCommand("party leave");
			pendingAcceptInviter = inviter;
			pendingAcceptAt = now + GUEST_LEAVE_SETTLE_MILLIS;
			OperationalLog.info("TICKET_TRADE", "Leaving current party before Sparkling trade accept");
		} else {
			sendCommand("party accept " + inviter);
		}
	}

	/** A loot share proves that an invited trader participated, even if they leave early. */
	public static void onSharedCatch(String catcher) {
		if (testMode) return;
		String normalized = normalizeName(catcher);
		if (tradeRunActive && activeTargets.contains(normalized)) {
			confirmRunMember(normalized, catcher, "loot share");
		}
	}

	/** Leaving the host's ticketed instance closes a successfully warped trading party. */
	public static void onConnectionJoin() {
		if (testMode) {
			resetRunAutomation();
			clearPendingAccept();
			sparklingFoundThisVisit = 0L;
			return;
		}
		if (hostOwnedRun && tradeRunActive && warpSent) sendCommand("party disband");
		resetRunAutomation();
		sparklingFoundThisVisit = 0L;
	}

	public static void onDisconnect() {
		resetRunAutomation();
		clearPendingAccept();
		sparklingFoundThisVisit = 0L;
	}

	public static List<String> configuredPlayers() {
		return configuredPlayerEntries(0, TicketTradingProfiles.TOTAL_SLOTS).stream()
			.map(ConfiguredPlayer::name).toList();
	}

	static void beginTestMode() {
		if (!BuildVersion.DEVELOPER) return;
		resetRunAutomation();
		clearPendingAccept();
		testMode = true;
	}

	static void endTestMode() {
		if (!testMode) return;
		testMode = false;
		resetRunAutomation();
		clearPendingAccept();
	}

	private static List<ConfiguredPlayer> configuredPlayerEntries(int from, int to) {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		TicketTradingProfiles.sanitize(config);
		List<ConfiguredPlayer> result = new ArrayList<>(Math.max(0, to - from));
		Set<String> seen = new HashSet<>();
		String self = Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT);
		for (int index = from; index < to; index++) {
			SafariConfig.SparklingConfig.TicketTraderProfile profile =
				TicketTradingProfiles.slot(config, index);
			if (profile == null) continue;
			String normalized = normalizeName(profile.username);
			if (!normalized.isEmpty() && !normalized.equals(self) && seen.add(normalized)) {
				result.add(new ConfiguredPlayer(normalized, profile.sparklingOnly,
					profile.sparklingCritters & Critters.allSelectionMask()));
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
		if (!HypixelConnection.active() || client.getConnection() == null) return;
		client.getConnection().sendCommand(command);
		OperationalLog.info("TICKET_TRADE", "Sent /" + command.split(" ")[0] + " "
			+ command.split(" ")[1]);
	}

	private static void confirmRunMember(String normalized, String displayName, String reason) {
		if (!confirmedRunMembers.add(normalized)) return;
		SessionManager.onTicketTradingMemberConfirmed(displayName);
		OperationalLog.info("TICKET_TRADE", "Confirmed traded run member via " + reason);
	}

	/** Mutating host commands fail closed until a fresh roster proves local leadership. */
	private static void qualifyHost(long now) {
		if (!hostQualificationPending) return;
		long elapsed = now - connectionStartedAt;
		if (elapsed > SPARKLING_INVITE_DEADLINE_MILLIS) {
			OperationalLog.info("TICKET_TRADE", "Not scheduled: host authority was not confirmed in time");
			resetRunAutomation();
			return;
		}
		if (!PartyRosterWatch.known()
				|| PartyRosterWatch.rosterCapturedAt() < connectionStartedAt) return;
		hostQualificationPending = false;
		if (!PartyRosterWatch.localPlayerIsLeader()) {
			OperationalLog.info("TICKET_TRADE", "Guest role confirmed; host automation not armed");
			resetRunAutomation();
			return;
		}
		hostOwnedRun = true;
		hostScheduled = true;
		tradeRunActive = true;
		OperationalLog.info("TICKET_TRADE", "Host authority confirmed; scheduled normal="
			+ normalTargets.size() + " sparkling=" + sparklingTargets.size()
			+ " backups=" + backupTargets.size()
			+ " alreadyFound=" + Long.bitCount(sparklingFoundThisVisit));
	}

	private static void resetRunAutomation() {
		joinedTargets.clear();
		hostScheduled = false;
		warpSent = false;
		hostQualificationPending = false;
		hostOwnedRun = false;
		confirmedRunMembers.clear();
		activeTargets.clear();
		invitedTargets.clear();
		failedPrimaryTargets.clear();
		normalTargets = List.of();
		sparklingTargets = List.of();
		backupTargets = List.of();
		lastPrimaryInviteBatch = List.of();
		connectionStartedAt = 0L;
		lastHostCommandAt = 0L;
		inviteResponseUntil = 0L;
		backupInviteAt = 0L;
		backupReplacementsSent = 0;
		tradeRunActive = false;
		rosterMinuteChecked = false;
	}

	private static List<String> pendingPrimaryTargets() {
		List<String> pending = new ArrayList<>(3);
		for (String target : normalTargets) {
			if (!invitedTargets.contains(target)) pending.add(target);
		}
		if (sparklingFoundThisVisit != 0L) {
			for (ConfiguredPlayer target : sparklingTargets) {
				if ((target.sparklingCritters() & sparklingFoundThisVisit) != 0L
						&& !invitedTargets.contains(target.name())) pending.add(target.name());
			}
		}
		return pending;
	}

	private static List<String> pendingSparklingTargets() {
		List<String> pending = new ArrayList<>(3);
		if (sparklingFoundThisVisit == 0L) return pending;
		for (ConfiguredPlayer target : sparklingTargets) {
			if ((target.sparklingCritters() & sparklingFoundThisVisit) != 0L
					&& !invitedTargets.contains(target.name())) pending.add(target.name());
		}
		return pending;
	}

	private static List<String> pendingBackupTargets(int limit) {
		List<String> pending = new ArrayList<>(Math.min(limit, backupTargets.size()));
		for (ConfiguredPlayer target : backupTargets) {
			if (pending.size() >= limit) break;
			if (invitedTargets.contains(target.name())) continue;
			if (target.sparklingOnly()
					&& (target.sparklingCritters() & sparklingFoundThisVisit) == 0L) continue;
			pending.add(target.name());
		}
		return pending;
	}

	private static void sendInviteBatch(List<String> targets, boolean primary, long now) {
		sendCommand("party invite " + String.join(" ", targets));
		lastHostCommandAt = now;
		invitedTargets.addAll(targets);
		activeTargets.addAll(targets);
		if (primary) {
			lastPrimaryInviteBatch = List.copyOf(targets);
			inviteResponseUntil = now + INVITE_RESPONSE_WINDOW_MILLIS;
		}
	}

	private static boolean isOfflineInviteFailure(String line) {
		String lower = line.toLowerCase(Locale.ROOT);
		return lower.contains("couldn't find a player with that name")
			|| lower.contains("could not find a player with that name")
			|| lower.contains("cannot find a player with that name")
			|| lower.contains("is not online") || lower.contains("is offline");
	}

	private static void recordOfflinePrimaryInvite(String line, long now) {
		String lower = line.toLowerCase(Locale.ROOT);
		String failed = null;
		for (String target : lastPrimaryInviteBatch) {
			if (lower.matches(".*\\b" + Pattern.quote(target) + "\\b.*")) {
				failed = target;
				break;
			}
		}
		if (failed == null) {
			for (String target : lastPrimaryInviteBatch) {
				if (!joinedTargets.contains(target) && !failedPrimaryTargets.contains(target)) {
					failed = target;
					break;
				}
			}
		}
		if (failed == null || !failedPrimaryTargets.add(failed)) return;
		String failedTarget = failed;
		lastPrimaryInviteBatch = lastPrimaryInviteBatch.stream()
			.filter(target -> !target.equals(failedTarget)).toList();
		if (backupInviteAt == 0L) backupInviteAt = now + BACKUP_INVITE_DELAY_MILLIS;
		OperationalLog.info("TICKET_TRADE", "Primary invite unavailable; queued backup replacement");
	}

	private static void clearPendingAccept() {
		pendingAcceptInviter = "";
		pendingAcceptAt = 0L;
	}
}
