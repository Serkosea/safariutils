package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
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
	static final long INVITE_RETRY_DELAY_MILLIS = 3_000L;
	static final long INVITE_RESPONSE_WINDOW_MILLIS = 5_000L;
	static final long GUEST_LEAVE_SETTLE_MILLIS = 1_100L;
	private static final long KICK_INVITE_LOCKOUT_MILLIS = 45_000L;
	private static final long REMINDER_INTERVAL_MILLIS = 8_000L;
	private static final long INBOUND_LOBBY_WAIT_MILLIS = 5_000L;
	private static final long DIRECT_ACCEPT_WINDOW_MILLIS = 3_000L;
	private static final long ACCEPT_DEDUPLICATION_MILLIS = 5_000L;
	private static final long TICKETED_NOTICE_MILLIS = 4_000L;
	private static final String SPARKLING_PROTOCOL = "SUT1";
	private static final long ROSTER_CONFIRM_AT_MILLIS = 60_000L;
	private static final Pattern INVITE = Pattern.compile(
		"^(?:You have been invited to join\\s+)?(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16})(?:'s party| has invited you to join (?:their|his|her) party)!?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PARTY_JOIN = Pattern.compile(
		"^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) joined the party\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PARTY_PROTOCOL = Pattern.compile(
		"^Party > (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}):\\s+(SUT1)([FC])([0-9A-Fa-f]{2})([A-Za-z0-9_-]{12})$",
		Pattern.CASE_INSENSITIVE);

	private static final Set<String> joinedTargets = new HashSet<>();
	private static final Set<String> confirmedRunMembers = new HashSet<>();
	private static final Set<String> activeTargets = new LinkedHashSet<>();
	private static final Set<String> invitedTargets = new LinkedHashSet<>();
	private static final Set<String> unavailableTargets = new LinkedHashSet<>();
	private static final Set<String> pendingResponses = new LinkedHashSet<>();
	/** Party members present before trading invites; these players are never cleanup targets. */
	private static final Set<String> baselineMembers = new LinkedHashSet<>();
	private static List<ConfiguredPlayer> inviteQueue = List.of();
	private static long connectionStartedAt;
	private static long lastHostCommandAt;
	private static long inviteResponseUntil;
	private static long nextInviteAt;
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
	private static String directAcceptInviter = "";
	private static long directAcceptAt;
	private static long guestInviteBlockedUntil;
	private static long nextReminderAt;
	private static long remotelyFound;
	private static long sentFound;
	private static long sentCleared;
	private static long pendingFound;
	private static long pendingCleared;
	private static final dev.serko.safariutils.state.DeferredObservations<InboundProtocol> pendingInbound =
		new dev.serko.safariutils.state.DeferredObservations<>(128, INBOUND_LOBBY_WAIT_MILLIS * 1_000_000L);
	private static boolean enabledLastTick;
	private static boolean testMode;
	private static int baselinePartySize = 1;
	private static final List<String> pendingCleanupGuests = new ArrayList<>();
	private static boolean pendingCleanupDisband;
	private static long cleanupGeneration = -1L;
	private static long cleanupDeadline;
	private static long nextCleanupAt;

	private record ConfiguredPlayer(String name, TicketTradingProfiles.HostMode hostMode,
		TicketTradingProfiles.GuestMode guestMode, long sparklingCritters) {
		boolean sparklingOnly() { return hostMode == TicketTradingProfiles.HostMode.SPARKLING; }
	}
	private record InboundProtocol(String sender, char event, int species, String fingerprint) { }

	private TicketTrading() { }

	public static void sanitizeSettings(SafariConfig.SparklingConfig config) {
		TicketTradingProfiles.sanitize(config);
	}

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
		inviteQueue = configuredHostQueue();
		if (inviteQueue.isEmpty()) return;
		connectionStartedAt = System.currentTimeMillis() - elapsed;
		hostQualificationPending = true;
		OperationalLog.info("TICKET_TRADE", "Waiting for host authority queue=" + inviteQueue.size()
			+ " alreadyFound=" + Long.bitCount(sparklingFoundThisVisit));
	}

	public static void tick() {
		if (!HypixelConnection.active()) {
			onDisconnect();
			return;
		}
		if (testMode) return;
		long now = System.currentTimeMillis();
		boolean enabled = ConfigManager.get().sparkling.ticketTradingEnabled;
		if (!enabled) {
			if (enabledLastTick) {
				clearProtocolVisitState();
				sparklingFoundThisVisit = 0L;
			}
			enabledLastTick = false;
		} else {
			enabledLastTick = true;
		}
		if (pendingAcceptAt > 0L) {
			if (!enabled || now < guestInviteBlockedUntil) clearPendingAccept();
			else if (now >= pendingAcceptAt) {
				if (!TicketGuestPolicy.pending(enabled,
						configuredGuest(normalizeName(pendingAcceptInviter)) != null,
						now < guestInviteBlockedUntil)) clearPendingAccept();
				else if (sendCommand("party accept " + pendingAcceptInviter,
						CommandCoordinator.Category.TICKET_GUEST,
						CommandCoordinator.Cooldown.PARTY_MEMBERSHIP, false, false)) {
					clearPendingAccept();
				}
			}
		}
		processPendingCleanup(now);
		processPendingInbound();
		updateRemoteReminder(now);
		flushSparklingProtocol();
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
		if (!pendingResponses.isEmpty() && now >= inviteResponseUntil) {
			unavailableTargets.addAll(pendingResponses);
			OperationalLog.info("TICKET_TRADE", "Invite wave timed out count="
				+ pendingResponses.size() + "; continuing ordered queue");
			pendingResponses.clear();
			nextInviteAt = Math.max(nextInviteAt, now);
		}
		boolean commandReady = now - lastHostCommandAt >= COMMAND_COOLDOWN_MILLIS;
		boolean inviteWindow = elapsed <= SPARKLING_INVITE_DEADLINE_MILLIS;
		boolean earlySparklingWindow = elapsed < SPARKLING_BATCH_FROM_MILLIS;
		boolean normalWindow = elapsed >= INVITE_AT_MILLIS;
		if (!warpSent && inviteWindow && commandReady && pendingResponses.isEmpty()
				&& now >= nextInviteAt && (earlySparklingWindow || normalWindow)) {
			List<String> pending = pendingQueueTargets(earlySparklingWindow);
			if (!pending.isEmpty()) sendInviteBatch(pending, now);
		}
		boolean hasJoinedPlayer = !joinedTargets.isEmpty();
		// Always preserve the full scouting window: even a complete party waits until 27s.
		// A first late join between 27s and 29s is picked up on the next client tick.
		boolean warpReady = hasJoinedPlayer && now >= connectionStartedAt + WARP_AT_MILLIS
			&& now - lastHostCommandAt >= COMMAND_COOLDOWN_MILLIS;
		if (!warpSent && warpReady && now < connectionStartedAt + LAST_WARP_AT_MILLIS) {
			if (sendCommand("party warp", CommandCoordinator.Category.TICKET_HOST,
					CommandCoordinator.Cooldown.PARTY_ACTION, true, true)) {
				lastHostCommandAt = now;
				warpSent = true;
			}
		}
		if (!warpSent && now >= connectionStartedAt + LAST_WARP_AT_MILLIS) {
			if (!joinedTargets.isEmpty()) armCleanup();
			else if (baselinePartySize == 1 && !invitedTargets.isEmpty()) {
				sendCommand("party disband", CommandCoordinator.Category.TICKET_CLEANUP,
					CommandCoordinator.Cooldown.PARTY_CLEANUP, true, false);
			}
			resetRunAutomation();
		}
	}

	/** Qualifies Sparkling-only traders while a pre-ticket or active invite window remains. */
	public static void onSparklingDetected(Critter critter) {
		if (testMode || !ConfigManager.get().sparkling.ticketTradingEnabled) return;
		long bit = Critters.selectionMask(critter);
		if (bit == 0L || (sparklingFoundThisVisit & bit) != 0L) return;
		sparklingFoundThisVisit |= bit;
		pendingFound |= bit;
		pendingCleared &= ~bit;
		OperationalLog.info("TICKET_TRADE", "Sparkling qualified this Safari visit species="
			+ critter.name());
	}

	/** Stops remote reminders and informs current ticket-trading party members. */
	public static void onSparklingCaught(Critter critter) {
		if (testMode || critter == null) return;
		long bit = Critters.selectionMask(critter);
		if (bit == 0L) return;
		remotelyFound &= ~bit;
		if ((sparklingFoundThisVisit & bit) != 0L) {
			sparklingFoundThisVisit &= ~bit;
			pendingFound &= ~bit;
			if ((sentFound & bit) != 0L) pendingCleared |= bit;
			else pendingCleared &= ~bit;
		}
		refreshRemoteReminder(false);
	}

	/** Handles trusted invitations and observes the host party filling up. */
	public static void onChatMessage(String line) {
		if (testMode) return;
		if (line == null || line.isBlank()) return;
		long now = System.currentTimeMillis();
		String lower = line.toLowerCase(Locale.ROOT);
		if (ConfigManager.get().sparkling.ticketTradingEnabled
				&& (lower.contains("you were kicked while joining that server")
				|| lower.contains("a kick occurred in your connection")
				|| lower.contains("something went wrong! (warp timeout)"))) {
			boolean newlyBlocked = now >= guestInviteBlockedUntil;
			guestInviteBlockedUntil = Math.max(guestInviteBlockedUntil,
				now + KICK_INVITE_LOCKOUT_MILLIS);
			clearPendingAccept();
			if (newlyBlocked) {
				OperationalLog.info("TICKET_TRADE", "Guest invites paused for 45s after failed server join");
			}
			return;
		}
		boolean partyRejection = lower.contains("already in a party")
			|| lower.contains("must leave your current party");
		ConfiguredPlayer fallbackGuest = partyRejection && !directAcceptInviter.isEmpty()
			&& now - directAcceptAt <= DIRECT_ACCEPT_WINDOW_MILLIS
			? configuredGuest(normalizeName(directAcceptInviter)) : null;
		if (!directAcceptInviter.isEmpty() && TicketGuestPolicy.fallback(
				ConfigManager.get().sparkling.ticketTradingEnabled,
				fallbackGuest != null && fallbackGuest.guestMode() == TicketTradingProfiles.GuestMode.SWITCH,
				now < guestInviteBlockedUntil, now - directAcceptAt, DIRECT_ACCEPT_WINDOW_MILLIS)
				&& partyRejection) {
			PartyRosterWatch.deferRefreshUntil(now + GUEST_LEAVE_SETTLE_MILLIS * 2L);
			if (!sendCommand("party leave", CommandCoordinator.Category.TICKET_GUEST,
					CommandCoordinator.Cooldown.PARTY_MEMBERSHIP, false, false)) return;
			pendingAcceptInviter = directAcceptInviter;
			pendingAcceptAt = now + GUEST_LEAVE_SETTLE_MILLIS;
			directAcceptInviter = "";
			directAcceptAt = 0L;
			OperationalLog.info("TICKET_TRADE", "Direct accept found an existing party; leaving and retrying");
		}
		if (lower.startsWith("you have joined ") && lower.endsWith("'s party!")) {
			directAcceptInviter = "";
			directAcceptAt = 0L;
		}
		if (hostScheduled && now <= inviteResponseUntil && isOfflineInviteFailure(line)) {
			recordOfflinePrimaryInvite(line, now);
		}
		Matcher joined = PARTY_JOIN.matcher(line);
		if (joined.matches() && hostScheduled) {
			String name = joined.group(1).toLowerCase(Locale.ROOT);
			if (activeTargets.contains(name)) {
				joinedTargets.add(name);
				pendingResponses.remove(name);
			}
		}
		if (!ConfigManager.get().sparkling.ticketTradingEnabled) return;
		Matcher invite = INVITE.matcher(line);
		if (!invite.matches()) return;
		String inviter = invite.group(1);
		String normalized = inviter.toLowerCase(Locale.ROOT);
		ConfiguredPlayer trusted = configuredGuest(normalized);
		if (trusted == null) return;
		if (now < guestInviteBlockedUntil) {
			OperationalLog.info("TICKET_TRADE", "Ignored trusted invite during post-kick lockout");
			return;
		}
		boolean switchParty = trusted.guestMode() == TicketTradingProfiles.GuestMode.SWITCH;
		TicketGuestPolicy.Action action = TicketGuestPolicy.invite(true, true, false,
			normalized.equals(lastAccepted) && now - lastAcceptedAt < ACCEPT_DEDUPLICATION_MILLIS,
			switchParty, PartyState.snapshot().inParty());
		if (action == TicketGuestPolicy.Action.IGNORE) return;
		if (action == TicketGuestPolicy.Action.LEAVE_THEN_ACCEPT) {
			// Leave and accept share Hypixel's short command cooldown. Keep the
			// automatic roster refresh from occupying either side of that window.
			PartyRosterWatch.deferRefreshUntil(now + GUEST_LEAVE_SETTLE_MILLIS * 2L);
			if (!sendCommand("party leave", CommandCoordinator.Category.TICKET_GUEST,
					CommandCoordinator.Cooldown.PARTY_MEMBERSHIP, false, true)) return;
			pendingAcceptInviter = inviter;
			pendingAcceptAt = now + GUEST_LEAVE_SETTLE_MILLIS;
			OperationalLog.info("TICKET_TRADE", "Leaving current party before Sparkling trade accept");
		} else {
			if (!sendCommand("party accept " + inviter,
					CommandCoordinator.Category.TICKET_GUEST,
					CommandCoordinator.Cooldown.PARTY_MEMBERSHIP, false, false)) return;
			directAcceptInviter = switchParty ? inviter : "";
			directAcceptAt = switchParty ? now : 0L;
		}
		// A denied command must not consume the invite deduplication window.
		lastAccepted = normalized;
		lastAcceptedAt = now;
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
			clearProtocolVisitState();
			return;
		}
		if (hostOwnedRun && tradeRunActive && warpSent) armCleanup();
		resetRunAutomation();
		sparklingFoundThisVisit = 0L;
		clearProtocolVisitState();
	}

	public static void onDisconnect() {
		// A Hypixel server transfer emits a disconnect before the destination join.
		// Preserve a warped host operation so onConnectionJoin can perform its promised
		// cleanup; a confirmed non-Hypixel destination clears it through the method below.
		if (hostOwnedRun && tradeRunActive && warpSent) return;
		resetRunAutomation();
		clearPendingAccept();
		sparklingFoundThisVisit = 0L;
		clearProtocolVisitState();
	}

	public static void onConfirmedNonHypixel() {
		resetRunAutomation();
		clearPendingAccept();
		clearCleanup("left Hypixel");
		sparklingFoundThisVisit = 0L;
		clearProtocolVisitState();
	}

	public static List<String> configuredPlayers() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		TicketTradingProfiles.sanitize(config);
		return config.ticketTradingProfiles.stream()
			.filter(profile -> TicketTradingProfiles.queued(config, profile)
				|| TicketTradingProfiles.guestEnabled(config, profile))
			.map(profile -> normalizeName(profile.username)).filter(name -> !name.isEmpty())
			.distinct().toList();
	}

	static void beginTestMode() {
		if (!BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)) return;
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

	private static List<ConfiguredPlayer> configuredHostQueue() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		TicketTradingProfiles.sanitize(config);
		List<ConfiguredPlayer> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		String self = Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT);
		for (SafariConfig.SparklingConfig.TicketTraderProfile profile
				: TicketTradingProfiles.hostQueue(config)) {
			String normalized = normalizeName(profile.username);
			if (!normalized.isEmpty() && !normalized.equals(self) && seen.add(normalized)) {
				result.add(new ConfiguredPlayer(normalized,
					TicketTradingProfiles.hostMode(profile), TicketTradingProfiles.guestMode(profile),
					profile.sparklingCritters & Critters.allSelectionMask()));
			}
		}
		return List.copyOf(result);
	}

	private static ConfiguredPlayer configuredGuest(String normalized) {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		TicketTradingProfiles.sanitize(config);
		SafariConfig.SparklingConfig.TicketTraderProfile profile =
			TicketTradingProfiles.find(config, normalized);
		if (profile == null || !TicketTradingProfiles.guestEnabled(config, profile)) return null;
		return new ConfiguredPlayer(normalized, TicketTradingProfiles.hostMode(profile),
			TicketTradingProfiles.guestMode(profile),
			profile.sparklingCritters & Critters.allSelectionMask());
	}

	static String normalizeName(String value) {
		if (value == null) return "";
		String trimmed = value.trim();
		return trimmed.matches("[A-Za-z0-9_]{1,16}") ? trimmed.toLowerCase(Locale.ROOT) : "";
	}

	/** Parses and hides valid internal party-Sparkling notices before player-chat filtering. */
	public static boolean allowMessage(Component message, boolean overlay) {
		if (overlay || message == null || !HypixelConnection.active()
				|| !ConfigManager.get().sparkling.ticketTradingEnabled) {
			return true;
		}
		String line = ChatParser.clean(message.getString());
		Matcher protocol = PARTY_PROTOCOL.matcher(line);
		if (!protocol.matches()) return true;
		String sender = protocol.group(1);
		char event = Character.toUpperCase(protocol.group(3).charAt(0));
		int species = Integer.parseInt(protocol.group(4), 16);
		if (species < 0 || species >= Critters.selectionOrder().size()) return true;
		InboundProtocol inbound = new InboundProtocol(sender.toLowerCase(Locale.ROOT), event, species,
			protocol.group(5));
		if (!acceptInbound(inbound)) {
			boolean added = pendingInbound.offer(inbound);
			StructuredDiagnostics.decision("TICKET/SIGNAL", added ? "deferred" : "duplicate",
				"pending=" + pendingInbound.size());
		}
		return false;
	}

	/** Ticket-trading hosts wait for their invited roster before freezing API state. */
	public static boolean deferPartyApiRoster() {
		if (!ConfigManager.get().sparkling.ticketTradingEnabled) return false;
		if (SessionManager.current() == null
				&& SessionManager.ticketWindowRemainingMillis() > 0L) return true;
		return hostQualificationPending || hostScheduled && !warpSent;
	}

	private static void processPendingInbound() {
		pendingInbound.drain(TicketTrading::acceptInbound);
	}

	private static boolean acceptInbound(InboundProtocol inbound) {
		String lobby = SafariLocation.lobbyId();
		if (lobby == null || lobby.isBlank()) return false;
		String expected = fingerprint(inbound.sender(), lobby, inbound.event(), inbound.species());
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
				inbound.fingerprint().getBytes(StandardCharsets.US_ASCII))) {
			DebugLog.line("TICKET_SIGNAL", "rejected sender=" + inbound.sender()
				+ " reason=fingerprint");
			return true;
		}
		String self = Minecraft.getInstance().getUser().getName();
		if (inbound.sender().equalsIgnoreCase(self)) return true;
		long bit = 1L << inbound.species();
		Critter critter = Critters.selectionOrder().get(inbound.species());
		if (inbound.event() == 'C') {
			remotelyFound &= ~bit;
			DebugLog.line("TICKET_SIGNAL", "cleared species=" + critter.name()
				+ " sender=" + inbound.sender());
			refreshRemoteReminder(false);
			return true;
		}
		if (SessionManager.current() != null) {
			EncounterAlerts.showTicketTradingSparklingReminder(critter.name(),
				TICKETED_NOTICE_MILLIS, false, true);
			DebugLog.line("TICKET_SIGNAL", "noticed species=" + critter.name()
				+ " sender=" + inbound.sender() + " ticketed=true");
			return true;
		}
		if ((remotelyFound & bit) != 0L) return true;
		remotelyFound |= bit;
		nextReminderAt = System.currentTimeMillis() + REMINDER_INTERVAL_MILLIS;
		DebugLog.line("TICKET_SIGNAL", "noticed species=" + critter.name()
			+ " sender=" + inbound.sender() + " ticketed=false");
		refreshRemoteReminder(true);
		return true;
	}

	private static void updateRemoteReminder(long now) {
		if (remotelyFound == 0L) return;
		long remaining = SessionManager.ticketWindowRemainingMillis();
		if (SessionManager.current() != null || remaining <= 0L || !SafariLocation.inside()) {
			clearRemoteSparklingState();
			return;
		}
		if (now >= nextReminderAt) {
			EncounterAlerts.playTicketTradingSparklingSound();
			nextReminderAt = now + REMINDER_INTERVAL_MILLIS;
			DebugLog.line("TICKET_SIGNAL", "guest reminder repeated remaining=" + remaining + "ms");
		}
	}

	private static void refreshRemoteReminder(boolean playSound) {
		if (remotelyFound == 0L) {
			EncounterAlerts.clearTicketTradingSparklingReminder();
			return;
		}
		long remaining = SessionManager.ticketWindowRemainingMillis();
		if (remaining <= 0L) {
			clearRemoteSparklingState();
			return;
		}
		String names = java.util.stream.IntStream.range(0, Critters.selectionOrder().size())
			.filter(index -> (remotelyFound & (1L << index)) != 0L)
			.mapToObj(index -> Critters.selectionOrder().get(index).name())
			.collect(java.util.stream.Collectors.joining(", "));
		EncounterAlerts.showTicketTradingSparklingReminder(names,
			remaining, true, playSound);
	}

	private static void flushSparklingProtocol() {
		if ((pendingFound | pendingCleared) == 0L) return;
		if (!ConfigManager.get().sparkling.ticketTradingEnabled || !PartyState.snapshot().inParty()) return;
		if (hostScheduled && !warpSent) return;
		String lobby = SafariLocation.lobbyId();
		if (lobby == null || lobby.isBlank()) return;
		String self = Minecraft.getInstance().getUser().getName();
		long found = pendingFound & ~sentFound;
		long cleared = pendingCleared & sentFound & ~sentCleared;
		for (int index = 0; index < Critters.selectionOrder().size(); index++) {
			long bit = 1L << index;
			if ((found & bit) != 0L) {
				ChatQueue.enqueueVisitScopedVerifiedPartyFirst("pc " + protocol(self, lobby, 'F', index));
				sentFound |= bit;
				pendingFound &= ~bit;
				DebugLog.line("TICKET_SIGNAL", "queued found species="
					+ Critters.selectionOrder().get(index).name());
			}
			if ((cleared & bit) != 0L) {
				ChatQueue.enqueueVisitScopedVerifiedPartyFirst("pc " + protocol(self, lobby, 'C', index));
				sentCleared |= bit;
				pendingCleared &= ~bit;
				DebugLog.line("TICKET_SIGNAL", "queued clear species="
					+ Critters.selectionOrder().get(index).name());
			}
		}
	}

	private static String protocol(String sender, String lobby, char event, int species) {
		String hex = "%02X".formatted(species);
		return SPARKLING_PROTOCOL + event + hex + fingerprint(sender, lobby, event, species);
	}

	private static String fingerprint(String sender, String lobby, char event, int species) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((SPARKLING_PROTOCOL + '|' + sender.toLowerCase(Locale.ROOT)
				+ '|' + lobby.toLowerCase(Locale.ROOT) + '|' + event + '|' + species)
				.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 12);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static void clearRemoteSparklingState() {
		remotelyFound = 0L;
		nextReminderAt = 0L;
		pendingInbound.clear();
		EncounterAlerts.clearTicketTradingSparklingReminder();
	}

	private static void clearProtocolVisitState() {
		pendingFound = 0L;
		pendingCleared = 0L;
		sentFound = 0L;
		sentCleared = 0L;
		clearRemoteSparklingState();
	}

	private static boolean sendCommand(String command, CommandCoordinator.Category category,
			CommandCoordinator.Cooldown cooldown, boolean requireLeader, boolean requireParty) {
		var request = CommandCoordinator.request(command, category, cooldown,
			CommandCoordinator.Scope.CONNECTION, requireLeader, requireParty);
		if (!CommandCoordinator.trySend(request)) return false;
		String[] pieces = command.split(" ");
		OperationalLog.info("TICKET_TRADE", "Sent /" + pieces[0]
			+ (pieces.length > 1 ? " " + pieces[1] : ""));
		return true;
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
		PartySnapshot party = PartyState.snapshot();
		if (!party.known() || party.capturedAtMillis() < connectionStartedAt) return;
		hostQualificationPending = false;
		if (!party.localLeader()) {
			OperationalLog.info("TICKET_TRADE", "Guest role confirmed; host automation not armed");
			resetRunAutomation();
			return;
		}
		freezeBaselineParty();
		if (baselinePartySize >= 4) {
			OperationalLog.info("TICKET_TRADE",
				"Host automation disabled for this visit: party was already full");
			resetRunAutomation();
			return;
		}
		hostOwnedRun = true;
		hostScheduled = true;
		tradeRunActive = true;
		OperationalLog.info("TICKET_TRADE", "Host authority confirmed; scheduled queue="
			+ inviteQueue.size()
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
		unavailableTargets.clear();
		pendingResponses.clear();
		baselineMembers.clear();
		baselinePartySize = 1;
		inviteQueue = List.of();
		connectionStartedAt = 0L;
		lastHostCommandAt = 0L;
		inviteResponseUntil = 0L;
		nextInviteAt = 0L;
		tradeRunActive = false;
		rosterMinuteChecked = false;
	}

	private static List<String> pendingQueueTargets(boolean sparklingOnlyWave) {
		return TicketInvitePolicy.select(inviteQueue, guestCapacityRemaining(), baselineMembers,
			invitedTargets, sparklingOnlyWave, ConfiguredPlayer::name, ConfiguredPlayer::sparklingOnly,
			target -> (target.sparklingCritters() & sparklingFoundThisVisit) != 0L)
			.stream().map(ConfiguredPlayer::name).toList();
	}

	private static void sendInviteBatch(List<String> targets, long now) {
		if (targets.isEmpty() || !sendCommand("party invite " + String.join(" ", targets),
				CommandCoordinator.Category.TICKET_HOST,
				CommandCoordinator.Cooldown.PARTY_ACTION, true, false)) return;
		lastHostCommandAt = now;
		invitedTargets.addAll(targets);
		activeTargets.addAll(targets);
		pendingResponses.clear();
		pendingResponses.addAll(targets);
		inviteResponseUntil = now + INVITE_RESPONSE_WINDOW_MILLIS;
		nextInviteAt = now + INVITE_RETRY_DELAY_MILLIS;
		OperationalLog.info("TICKET_TRADE", "Invited ordered queue wave count=" + targets.size());
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
		for (String target : pendingResponses) {
			if (lower.matches(".*\\b" + Pattern.quote(target) + "\\b.*")) {
				failed = target;
				break;
			}
		}
		if (failed == null) {
			for (String target : pendingResponses) {
				if (!joinedTargets.contains(target) && !unavailableTargets.contains(target)) {
					failed = target;
					break;
				}
			}
		}
		if (failed == null || !unavailableTargets.add(failed)) return;
		pendingResponses.remove(failed);
		nextInviteAt = Math.max(nextInviteAt, now + INVITE_RETRY_DELAY_MILLIS);
		OperationalLog.info("TICKET_TRADE", "Invite unavailable; continuing ordered queue");
	}

	private static void clearPendingAccept() {
		pendingAcceptInviter = "";
		pendingAcceptAt = 0L;
		directAcceptInviter = "";
		directAcceptAt = 0L;
	}

	private static void freezeBaselineParty() {
		baselineMembers.clear();
		var snapshot = dev.serko.safariutils.state.PartyState.snapshot();
		for (var member : snapshot.members()) {
			String normalized = normalizeName(member.name());
			if (!normalized.isEmpty()) baselineMembers.add(normalized);
		}
		baselineMembers.add(Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT));
		baselinePartySize = Math.max(1, snapshot.expectedPlayers());
		inviteQueue = inviteQueue.stream()
			.filter(player -> !baselineMembers.contains(player.name())).toList();
		OperationalLog.info("TICKET_TRADE", "Baseline party frozen size=" + baselinePartySize
			+ " guestCapacity=" + Math.max(0, 4 - baselinePartySize));
	}

	private static int guestCapacityRemaining() {
		return TicketInvitePolicy.capacity(baselinePartySize, joinedTargets.size());
	}

	private static void armCleanup() {
		pendingCleanupGuests.clear();
		for (String joined : joinedTargets) {
			if (!baselineMembers.contains(joined)) pendingCleanupGuests.add(joined);
		}
		pendingCleanupDisband = baselinePartySize <= 1 && !pendingCleanupGuests.isEmpty();
		if (!pendingCleanupDisband && pendingCleanupGuests.isEmpty()) return;
		cleanupGeneration = -1L;
		cleanupDeadline = System.currentTimeMillis() + 15_000L;
		nextCleanupAt = System.currentTimeMillis();
		OperationalLog.info("TICKET_TRADE", pendingCleanupDisband
			? "Armed host party disband after leaving Safari"
			: "Armed guest-only cleanup count=" + pendingCleanupGuests.size());
	}

	private static void processPendingCleanup(long now) {
		if (!pendingCleanupDisband && pendingCleanupGuests.isEmpty()) return;
		if (now > cleanupDeadline) {
			clearCleanup("expired");
			return;
		}
		PartySnapshot party = PartyState.snapshot();
		if (!party.known() || !party.localLeader()) return;
		if (cleanupGeneration < 0L) cleanupGeneration = party.generation();
		if (cleanupGeneration != party.generation()) {
			clearCleanup("party changed");
			return;
		}
		if (now < nextCleanupAt) return;
		if (pendingCleanupDisband) {
			if (sendCommand("party disband", CommandCoordinator.Category.TICKET_CLEANUP,
					CommandCoordinator.Cooldown.PARTY_CLEANUP, true, true)) {
				clearCleanup("disbanded");
			}
			return;
		}
		String target = pendingCleanupGuests.getFirst();
		if (sendCommand("party kick " + target, CommandCoordinator.Category.TICKET_CLEANUP,
				CommandCoordinator.Cooldown.PARTY_CLEANUP, true, true)) {
			pendingCleanupGuests.removeFirst();
			nextCleanupAt = now + 1_000L;
			if (pendingCleanupGuests.isEmpty()) clearCleanup("guests kicked");
		}
	}

	private static void clearCleanup(String reason) {
		if (pendingCleanupDisband || !pendingCleanupGuests.isEmpty()) {
			OperationalLog.info("TICKET_TRADE", "Cleanup finished: " + reason);
		}
		pendingCleanupGuests.clear();
		pendingCleanupDisband = false;
		cleanupGeneration = -1L;
		cleanupDeadline = 0L;
		nextCleanupAt = 0L;
	}
}
