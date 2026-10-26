package dev.serko.safariutils.session;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.AlertText;
import dev.serko.safariutils.client.EncounterAlerts;
import dev.serko.safariutils.client.FloorDrops;
import dev.serko.safariutils.client.MoundSpotter;
import dev.serko.safariutils.client.CritterCountLog;
import dev.serko.safariutils.client.CritterState;
import dev.serko.safariutils.client.CritterSpotter;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.HideyhoSolver;
import dev.serko.safariutils.client.StillCritters;
import dev.serko.safariutils.client.HotspotWatch;
import dev.serko.safariutils.client.JoinWindowDiagnostics;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.client.BirdfeederWatch;
import dev.serko.safariutils.client.ShiningCoinWatch;
import dev.serko.safariutils.client.SparklingWatch;
import dev.serko.safariutils.client.SparklingMode;
import dev.serko.safariutils.client.SafariObjectives;
import dev.serko.safariutils.client.NestTracker;
import dev.serko.safariutils.client.RecatchSpots;
import dev.serko.safariutils.state.SafariRuntimeState;
import dev.serko.safariutils.client.DetectedCritters;
import dev.serko.safariutils.client.StartingItemsWatch;
import dev.serko.safariutils.client.SafariPartyWatch;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.client.TicketTrading;
import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.client.WallTracker;
import dev.serko.safariutils.client.SafeMode;
import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.CritterSpawnRanges;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/** Starts, updates, finishes, and saves Safari runs. */
public final class SessionManager {

	private static final String LEADER_RUN_START =
		"[NPC] Safari Manager: I already saw your ticket, so you're free to go.";
	private static final String TICKET_LOCKOUT =
		"[NPC] Safari Manager: Dude, if you're not gonna pay, you're not gonna play.";
	/** Server window measured from the destination play connection, before UI state arrives. */
	private static final long TICKET_WINDOW_MILLIS = 30_000L;
	private static final long JOIN_SIGNAL_MAX_AGE_MILLIS = 45_000L;
	private static final Set<String> MEMBER_RUN_STARTS = Set.of(
		"[NPC] Safari Manager: Looks good to me. Have fun out there!"
	);
	private static final java.util.regex.Pattern SUMMARY_ESSENCE =
		java.util.regex.Pattern.compile("^\\+([\\d,]+) Safari Essence$");
	private static final Critter SNOOZLE = Critters.byName("Snoozle");
	private static final List<Critter> BIRDS = List.of("Bluebird", "Parakeet", "Macaw").stream()
		.map(Critters::byName).filter(java.util.Objects::nonNull).toList();
	private static final Set<Critter> computedUnavailable = new HashSet<>();

	private static SafariSession current;
	private static SafariSession lastSession;

	private static final java.util.EnumSet<SafariBiome> announcedBiomes =
		java.util.EnumSet.noneOf(SafariBiome.class);

	private static boolean announcedAllButMacaw;
	private static boolean announcedAllDone;
	/** Sticky for one active developer test run, even if its setting is later switched off. */
	private static boolean suppressCurrentRunPersistence;

	private static String runLobbyId;
	private static String waitingLobbyId;
	/** What opened the live run, and when — reported by {@code /su debug}. */
	private static String startedBy = "nothing yet";
	private static long startedAt;
	private static boolean rewardSummaryOpen;
	/** Prevents the summary's final scoreboard update from opening a phantom run. */
	private static String completedSummaryLobbyId;
	/** Transient tracking starts at instance entry; persistence starts after the ticket. */
	private static boolean visitPrepared;
	private static String visitLobbyId;
	private static int visitPeakPlayers = 1;
	private static int visitExpectedPlayers = 1;
	private static int runExpectedPlayers = 1;
	private static long visitEnteredAt;
	private static boolean visitRosterLocked;
    private static boolean catchBeforeTicket;
	/** First queue or party-entry signal, retained across the entrance-to-instance transfer. */
	private static long pendingJoinStartedAt;
	/** Destination play-connection boundary; the stable server-window origin. */
	private static long pendingConnectionJoinedAt;
	private static long ticketWindowStartedAt;
	private static boolean ticketWindowLocked;

	private SessionManager() {
	}

	/** Called every client tick to open/close runs as the player moves around. */
	public static void tick() {
		TrackingMode.setUniqueOnly(ConfigManager.get().display.uniqueOnly || SparklingMode.enabled());
		TrackingMode.setCountSpawns(ConfigManager.get().display.countSpawns);
		updateUnavailable();

		String lobbyId = SafariLocation.lobbyId();
		if (lobbyId != null) SafariRuntimeState.lobbyIdentified(lobbyId);
		if (current != null && runLobbyId == null && lobbyId != null) runLobbyId = lobbyId;
		if (current != null && lobbyId != null && runLobbyId != null
			&& !lobbyId.equals(runLobbyId)) {
			DebugLog.line("ACTIVATE", "lobby changed " + runLobbyId + " -> " + lobbyId
				+ ", ending active run");
			endSession();
		}

		if (!SafariLocation.inside()) {
			if (visitPrepared) {
				DebugLog.line("ACTIVATE", "Safari visit ended after "
					+ formatElapsed(System.currentTimeMillis() - visitEnteredAt)
					+ " ticketActivated=" + (current != null));
				// During a server transfer the scoreboard can disappear before the next
				// lobby identity is known. Keep an activated run's epoch alive until the
				// existing lobby/run logic actually closes it.
				if (current == null) {
					SafariRuntimeState.visitEnded();
					SparklingWatch.reset();
					RecatchSpots.clear();
					EncounterAlerts.reset();
				}
			}
			visitPrepared = false;
			visitLobbyId = null;
			ticketWindowStartedAt = 0L;
			StartingItemsWatch.cancelPendingRun();
			waitingLobbyId = null;
			completedSummaryLobbyId = null;
			return;
		}
		if (!visitPrepared || lobbyId != null && visitLobbyId != null
			&& !lobbyId.equals(visitLobbyId)) beginVisit(lobbyId);
		else if (visitLobbyId == null && lobbyId != null) visitLobbyId = lobbyId;
		visitPeakPlayers = Math.max(visitPeakPlayers, Math.max(1, SafariPartyWatch.joinedPlayers()));
		PartySnapshot party = PartyState.snapshot();
		if (!visitRosterLocked && party.capturedAtMillis() >= visitEnteredAt) {
			visitExpectedPlayers = party.expectedPlayers();
			visitRosterLocked = true;
			if (current != null) runExpectedPlayers = visitExpectedPlayers;
			DebugLog.line("PARTYTIME", "run roster locked at " + visitExpectedPlayers
				+ " player" + (visitExpectedPlayers == 1 ? "" : "s"));
		} else if (!visitRosterLocked) {
			// Until the fresh party response arrives, loaded attendance is a safer lower
			// bound than a stale party list from the previous island.
			visitExpectedPlayers = Math.max(visitExpectedPlayers, visitPeakPlayers);
		}
		if (completedSummaryLobbyId != null) {
			if (lobbyId == null || completedSummaryLobbyId.equals(lobbyId)) return;
			completedSummaryLobbyId = null;
		}

		if (lobbyId != null && !lobbyId.equals(waitingLobbyId)) {
			waitingLobbyId = lobbyId;
		}

		if (current != null) {
            observePersonalBestContext();
			Integer balance = SafariLocation.safariEssence();
			if (balance != null) current.updateEssenceBalance(balance, System.currentTimeMillis());
		}
	}

	/** Marks RNG species unavailable once every source is confirmed exhausted. */
	private static void updateUnavailable() {
		// Safe Mode cannot prove that every source was visible, so it avoids this shortcut.
		if (current == null || SafeMode.conservativeAvailability()) {
			TrackingMode.setUnavailable(Set.of());
			return;
		}
		computedUnavailable.clear();

		if (SNOOZLE != null
			&& WallTracker.SNOOPER.allConfirmedBroken()
			&& current.partyCatches(SNOOZLE) == 0
			&& !DetectedCritters.everSeen(SNOOZLE)) {
			computedUnavailable.add(SNOOZLE);
		}

		// Each feed has exactly one outcome. Once every feed is spent, any bird that
		// never spawned is unavailable; this applies to Bluebird and Parakeet as well
		// as the rarer Macaw.
		if (BirdfeederWatch.allForestFeedUsed()) {
			for (Critter bird : BIRDS) {
				if (!BirdfeederWatch.everSpawned(bird)
					&& current.partyCatches(bird) == 0
					&& !DetectedCritters.everSeen(bird)) {
					computedUnavailable.add(bird);
				}
			}
		}

		boolean changed = TrackingMode.setUnavailable(computedUnavailable);
		if (changed) {
			// Exhausting a finite source can complete a species without a catch. Announce
			// that transition now instead of waiting for an unrelated later catch.
			announceCompletionTransitions("availability");
		}
	}

	/** Feeds one raw chat line into the active run. */
	/** Feeds one cleaned chat line with the shared, already-parsed critter event. */
	public static void onChatMessage(String line, CritterEvent parsedEvent) {
		if (line.isEmpty()) return;
		long now = System.currentTimeMillis();
		observeJoinSignal(line, now);
		if (line.equals("SAFARI REWARD SUMMARY")) {
			rewardSummaryOpen = true;
			DebugLog.line("RUN", "Safari reward summary opened");
			return;
		}
		if (rewardSummaryOpen) {
			var essence = SUMMARY_ESSENCE.matcher(line);
			if (essence.matches()) {
				int amount = Integer.parseInt(essence.group(1).replace(",", ""));
				if (current != null) current.confirmSafariEssence(amount, now);
				DebugLog.line("RUN", "reward summary confirmed essence=" + amount);
				return;
			}
			if (isSummaryDivider(line)) {
				rewardSummaryOpen = false;
				completedSummaryLobbyId = runLobbyId != null ? runLobbyId : SafariLocation.lobbyId();
				DebugLog.line("RUN", "reward summary closed, ending active run");
				endSession();
				return;
			}
		}
		if (line.contains("Safari Manager")) {
			if (line.equals(TICKET_LOCKOUT)) {
				ticketWindowLocked = true;
				pendingJoinStartedAt = 0L;
				pendingConnectionJoinedAt = 0L;
			}
			DebugLog.line("ACTIVATE", "Manager line matched=" + isRunStart(line)
				+ " inside=" + SafariLocation.inside()
				+ " visitElapsed=" + formatElapsed(visitElapsedMillis(now))
				+ " raw=\"" + line + "\"");
		}

		// A Manager confirmation is useful early evidence, but capsule allocation is the
		// authoritative server-side acceptance signal and owns actual run activation.
		if (isRunStart(line)) {
			if (current == null) StartingItemsWatch.onTicketSubmitted("Safari Manager");
			else DebugLog.line("ACTIVATE", "Manager confirmation arrived with a run already active");
			return;
		}

		if (current != null && ChatParser.ownEmeraldDye(line, current.selfName())) {
            current.recordRareDrop("DYE_EMERALD");
            dev.serko.safariutils.backend.AuctionMarketClient.quote("DYE_EMERALD").ifPresent(q ->
                current.recordRareDropValue("DYE_EMERALD",q.price(),q.source(),q.observedAt(),now));
            DebugLog.line("RARE_DROP", "item=DYE_EMERALD count=" + current.rareDrops().get("DYE_EMERALD"));
            return;
        }
        ChatParser.SparklingCatch sparkling = ChatParser.sparklingCatch(line);
		if (sparkling != null) {
			SparklingWatch.onCaught(sparkling.critter(), !sparkling.catcher().equalsIgnoreCase(selfName()));
			if (current != null) {
				SparklingMode.onSparklingCaught(sparkling.critter());
				current.recordSparkling(sparkling.critter(), sparkling.catcher(), now);
				recordLifetimeSparkling(sparkling.critter());
			}
			return;
		}

		if (ChatParser.bonusRainbowFeather(line)) {
			if (current != null) {
				current.recordBonusRainbowFeather(now);
				if (!persistenceSuppressed()) SparklingStats.recordRainbowFeather();
			}
			return;
		}

		Critter tradedShard = ChatParser.receivedShard(line);
		if (tradedShard != null) {
			if (current != null) {
				current.recordShardReward(tradedShard, 1, now);
				DebugLog.line("SHARD", "TRADE " + tradedShard.name() + " x1");
			}
			return;
		}

		CritterEvent event = parsedEvent;
		if (event == null) return;

		if (event.type() == CritterEvent.Type.ENTERED_SAFARI) {
			ticketWindowLocked = false;
			if (BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)) {
				JoinWindowDiagnostics.onSelfEntry(line);
			}
			DebugLog.line("ACTIVATE", "self entry chat received at visitElapsed="
				+ formatElapsed(visitElapsedMillis(now)) + " raw=\"" + line + "\"");
			SafariLocation.markEntered();
			return;
		}

		if (current == null) {
			if (event.isCatch()) catchBeforeTicket = true;
            // Pre-ticket messages never activate or populate a run. Objective and private
			// synchronization trackers have their own Safari-visit context.
			return;
		}

		recordEvent(event, now);
	}

	/** Uses the earliest server-visible party entry or local queue notice for this attempt. */
	private static void observeJoinSignal(String line, long now) {
		if (line.equals("You were kicked while joining that server!")
			|| line.startsWith("You tried to rejoin too fast")) {
			pendingJoinStartedAt = 0L;
			pendingConnectionJoinedAt = 0L;
			if (current == null) ticketWindowStartedAt = 0L;
			return;
		}
		String entrant = ChatParser.safariEntrant(line);
		boolean partyEntry = entrant != null
			&& (entrant.equals(selfName()) || PartyState.snapshot().containsName(entrant));
		if (current != null || !ChatParser.safariQueueLine(line) && !partyEntry) return;
		if (ticketWindowLocked && !visitPrepared) ticketWindowLocked = false;
		if (pendingJoinStartedAt <= 0L || now - pendingJoinStartedAt > JOIN_SIGNAL_MAX_AGE_MILLIS
			|| ticketWindowLocked) pendingJoinStartedAt = now;
	}

	private static boolean isRunStart(String line) {
		return line.equals(LEADER_RUN_START) || MEMBER_RUN_STARTS.contains(line);
	}

	private static boolean isSummaryDivider(String line) {
		return line.length() >= 20 && line.chars().allMatch(character -> character == '▬');
	}

	private static void recordEvent(CritterEvent event, long now) {
		observePersonalBestContext();
        current.record(event, now);
        if (event.isCatch()) {
            announcePersonalBests(current.personalBests().catchSpecies(event.critter(),
                event.type() == CritterEvent.Type.OWN_CATCH ? current.selfName() : event.catcher(), current.personalBestElapsed()));
        }
		if (event.type() == CritterEvent.Type.SHARED_CATCH) {
			TicketTrading.onSharedCatch(event.catcher());
		}
		if (event.isCatch()) {
			int caught = current.partyCatches(event.critter());
			CritterState.onConfirmedCatchTotal(event.critter(), caught);
			int expectedMaximum = CritterSpawnRanges.maximum(event.critter());
			if (caught > expectedMaximum) {
				OperationalLog.debug("RANGE/CATCH", event.critter().name() + " caught=" + caught
					+ " expectedMax=" + expectedMaximum);
			}
		}
		if (event.sparkling() && !persistenceSuppressed()) SparklingStats.recordRainbowFeather();
		if (!event.isCatch()) return;
		SafariObjectives.onCatch(event.critter().name());
		EncounterAlerts.onCatch(event.critter().name());
		announceCompletionTransitions("catch:" + event.critter().name());
	}

	/**
	 * Evaluates every completion against one stable post-event state. The announcement
	 * sets are edge detectors: polling or duplicate catch lines cannot re-emit a biome.
	 */
	private static void announceCompletionTransitions(String reason) {
		if (current == null) return;
		boolean allDone = current.dexComplete();
		// This milestone means Macaw is genuinely the outstanding exception. Previously
		// it could fire after Macaw had already been caught if another species finished last.
		boolean allButMacaw = current.allButMacawComplete();
		boolean strongerMilestonePending = !announcedAllDone && allDone
			|| !announcedAllButMacaw && allButMacaw;
		for (SafariBiome biome : SafariBiome.values()) {
			if (announcedBiomes.contains(biome)) continue;
			if (!current.biomeUniquesComplete(biome)) continue;
			announcedBiomes.add(biome);
			DebugLog.line("COMPLETION", "BIOME " + biome.name() + " reason=" + reason
				+ " unique=" + current.partyUnique(biome) + "/" + Critters.totalIn(biome)
				+ " suppressed=" + strongerMilestonePending);
			// One catch can finish both a biome and the whole-run milestone. In that
			// case the stronger message replaces the redundant biome message.
			if (!strongerMilestonePending) EncounterAlerts.onBiomeComplete(biome);
		}
		if (!announcedAllDone && allDone) {
			announcedAllDone = true;
			announcedAllButMacaw = true;
			DebugLog.line("COMPLETION", "ALL_DONE reason=" + reason
				+ " unique=" + current.partyUnique() + "/" + Critters.total());
			EncounterAlerts.onAllDone();
			return;
		}
		if (!announcedAllButMacaw && allButMacaw) {
			announcedAllButMacaw = true;
			DebugLog.line("COMPLETION", "ALL_BUT_MACAW reason=" + reason
				+ " unique=" + current.partyUnique() + "/" + Critters.total());
			EncounterAlerts.onAllButMacaw();
		}
	}

    private static void observePersonalBestContext() {
        if (current == null) return;
        long elapsed = current.personalBestElapsed();
        current.personalBests().enter(SafariLocation.biome(), elapsed);

    }

    private static void announcePersonalBests(List<PersonalBestTracker.Split> added) {
        if (added.isEmpty()) return;
        List<net.minecraft.network.chat.Component> messages = new ArrayList<>();

        for (var split : added) {
            boolean mob = PersonalBestTracker.isMob(split.target());
            int mode = mob ? ConfigManager.get().gameplay.biomeMobBestMessages : ConfigManager.get().gameplay.personalBestMessages;
            var previous = PersonalBests.best(split.target(),split.group());
            long old = previous == null ? -1 : previous.split().elapsedMillis();
            current.personalBests().compared(split,old);
            boolean eligible = PersonalBestTracker.eligible(split);
            boolean improved = eligible && (old < 0 || split.elapsedMillis() < old);
            DebugLog.line("PB/SPLIT", "target="+split.target()+" elapsed="+split.elapsedMillis()
                +" contributors="+split.contributors().size()+" eligible="+eligible+" previous="+old+" rules="+split.rules());
            if (mode == 2 || mode == 1 && !improved) continue;
            String result = mob && eligible && old < 0 ? "First Catch!" : PersonalBestTracker.comparison(split.elapsedMillis(),old,eligible);
            int colour = PersonalBestTracker.comparisonColour(split.elapsedMillis(),old,eligible);
            var message = net.minecraft.network.chat.Component.literal(PersonalBestTracker.title(split.target())
                +(mob ? " " : " ("+(split.group()==null ? split.contributors().size()+" contributors" : split.group().label)+") ")
                +PersonalBestTracker.time(split.elapsedMillis()))
                .withStyle(style -> style.withColor(PersonalBestTracker.ALL.equals(split.target()) ? 0xB58AFF : PersonalBestTracker.biome(split.target()).colour()));
            message.append(net.minecraft.network.chat.Component.literal(" (").withStyle(style -> style.withColor(0xFFFFFF)));
            message.append(net.minecraft.network.chat.Component.literal(result).withStyle(style -> style.withColor(colour)));
            message.append(net.minecraft.network.chat.Component.literal(")").withStyle(style -> style.withColor(0xFFFFFF)));
            messages.add(message);
        }
        if (!messages.isEmpty()) dev.serko.safariutils.client.ClientMessages.sendLines(messages);

    }

	/** Opens a fresh run, then safely files the previous one. */
	public static void startSession(String trigger) {
		if (!visitPrepared) beginVisit(SafariLocation.lobbyId());
		if (BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)) {
			JoinWindowDiagnostics.onRunStarted(trigger);
		}
		pendingJoinStartedAt = 0L;
		pendingConnectionJoinedAt = 0L;
		DebugLog.line("RUN", "==== new run started (" + trigger + ") ====");
		OperationalLog.info("RUN", "Started Safari run via " + trigger);
		SafariSession finished = current;
		boolean finishedSuppressed = persistenceSuppressed();
		current = new SafariSession(selfName(), System.currentTimeMillis());
		SafariRuntimeState.runStarted(SafariLocation.lobbyId());
		suppressCurrentRunPersistence = BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)
			&& ConfigManager.get().advanced.testingDoNotSaveRun;
		runExpectedPlayers = Math.max(visitExpectedPlayers, visitPeakPlayers);
        current.startPersonalBests(!catchBeforeTicket && !suppressCurrentRunPersistence);
        DebugLog.line("PB/START", "rules="+PersonalBestTracker.RULES+" partial="+catchBeforeTicket+" testing="+suppressCurrentRunPersistence);
        observePersonalBestContext();
		SparklingMode.onRunStarted();
		runLobbyId = SafariLocation.lobbyId();
		waitingLobbyId = runLobbyId;
		Integer balance = SafariLocation.safariEssence();
		if (balance != null) current.updateEssenceBalance(balance, System.currentTimeMillis());
		startedBy = trigger;
		startedAt = System.currentTimeMillis();
		rewardSummaryOpen = false;
		completedSummaryLobbyId = null;
		if (finished != null && !finished.isEmpty()) {
			try {
				archive(finished, finishedSuppressed);
			} catch (RuntimeException failed) {
				OperationalLog.error("RUN/ARCHIVE_REPLACED", failed);
			}
		}
		resetCompletionAnnouncements();
		PartyItemSyncProviders.onRunStarted();
		TicketTrading.onRunStarted();
	}

	/** Clears one Safari instance's transient trackers before any ticket is submitted. */
	private static void beginVisit(String lobbyId) {
		long now = System.currentTimeMillis();
		visitPrepared = true;
		visitLobbyId = lobbyId;
		visitEnteredAt = now;
        catchBeforeTicket = false;
		// The destination connection arrives before the delayed sidebar, lobby id, and
		// area state. This also works for a late party warp that has no local queue line.
		ticketWindowStartedAt = pendingConnectionJoinedAt > 0L
			&& now - pendingConnectionJoinedAt <= JOIN_SIGNAL_MAX_AGE_MILLIS
			? pendingConnectionJoinedAt : now;
		ticketWindowLocked = false;
		visitRosterLocked = false;
		visitPeakPlayers = Math.max(1, SafariPartyWatch.joinedPlayers());
		visitExpectedPlayers = visitPeakPlayers;
		SafariRuntimeState.visitStarted(lobbyId);
		CritterState.onVisitStarted();
		runExpectedPlayers = 1;
		CritterCountLog.reset();
		SafariObjectives.reset();
		EncounterAlerts.reset();
		NestTracker.reset();
		RecatchSpots.reset();
		HideyhoSolver.reset();
		StillCritters.reset();
		BirdfeederWatch.reset();
		ShiningCoinWatch.reset();
		HotspotWatch.reset();
		FloorDrops.reset();
		MoundSpotter.reset();
		WallTracker.SNOOPER.reset();
		WallTracker.TROODON.reset();
		DetectedCritters.reset();
		CritterSpotter.reset();
		SparklingWatch.reset();
		StartingItemsWatch.onSafariVisitStarted();
		DebugLog.line("RUN", "Safari visit tracking prepared lobby=" + lobbyId
			+ " visitElapsed=0.000s");
	}

	/** Milliseconds since this client first recognized the current Safari instance. */
	public static long visitElapsedMillis() {
		return visitElapsedMillis(System.currentTimeMillis());
	}

	private static long visitElapsedMillis(long now) {
		return visitPrepared && visitEnteredAt > 0 ? Math.max(0L, now - visitEnteredAt) : 0L;
	}

	private static String formatElapsed(long millis) {
		return "%d.%03ds".formatted(millis / 1_000L, millis % 1_000L);
	}

	/** Remaining conservative pre-ticket join window, or {@code -1} when not waiting. */
	public static long ticketWindowRemainingMillis() {
		if (!SafariLocation.inside() || current != null || ticketWindowStartedAt <= 0L) return -1L;
		if (ticketWindowLocked) return 0L;
		return Math.max(0L, TICKET_WINDOW_MILLIS
			- (System.currentTimeMillis() - ticketWindowStartedAt));
	}

	/** Whether the Manager has begun the dialogue that rejects further tickets. */
	public static boolean ticketWindowLocked() {
		return ticketWindowLocked;
	}

	/** Milliseconds since the destination play connection for this Safari visit. */
	public static long ticketWindowElapsedMillis() {
		return ticketWindowStartedAt <= 0L ? -1L
			: Math.max(0L, System.currentTimeMillis() - ticketWindowStartedAt);
	}

	private static void recordLifetimeSparkling(Critter critter) {
		boolean suppressed = persistenceSuppressed();
		if (!suppressed) {
            if (current != null && SparklingStats.count(critter) == 0) current.recordNewSparkling(critter);
            SparklingStats.recordSparkling(critter);
        }
		int total = SparklingStats.count(critter);
		if (suppressed && current != null) {
			total += (int) current.sparklingOccurrences().stream()
				.filter(occurrence -> occurrence.critter() == critter).count();
		}
		var sparklingConfig = ConfigManager.get().sparkling;
		String message = AlertText.format(total == 1
				? sparklingConfig.sparklingFirstCaughtChatText
				: sparklingConfig.sparklingDuplicateCaughtChatText,
			"<CRITTER>", critter.name(), "<COUNT>", Integer.toString(total));
		SparklingWatch.postCaught(message);
	}

	/** Finalizes a Sparkling whose encounter ends without a capsule catch announcement. */
	public static void onInteractionSparklingCaught(Critter critter) {
		if (critter == null) return;
		long now = System.currentTimeMillis();
		boolean alreadyRecorded = current != null && current.sparklings().contains(critter);
		SparklingWatch.onCaught(critter);
		if (current == null) return;
		SparklingMode.onSparklingCaught(critter);
		// Hideyho normally has no global catch line, but remain idempotent if Hypixel
		// emits one in a future format before its dialogue completion arrives.
		if (alreadyRecorded) return;
		current.recordSparkling(critter, selfName(), now);
		recordLifetimeSparkling(critter);
	}

	private static void endSession() {
		endSession(false);
	}

	private static void endSession(boolean interrupted) {
		SafariSession finished = current;
		boolean suppressed = persistenceSuppressed();
		current = null;
		SafariRuntimeState.runClosed();
		suppressCurrentRunPersistence = false;
		runLobbyId = null;
		SparklingWatch.reset();
		// No recatch marker can remain meaningful after the reward summary closes.
		// Clear unresolved pins immediately instead of retaining them until timeout or
		// the next Safari visit.
		RecatchSpots.clear();
		if (finished == null || finished.isEmpty()) return;
		if (interrupted) finished.finishInterrupted(System.currentTimeMillis());
		else finished.finish(System.currentTimeMillis());
		OperationalLog.info("RUN", "Finished Safari run");
		try {
			archive(finished, suppressed);
		} catch (RuntimeException failed) {
			OperationalLog.error("RUN/ARCHIVE_FINISHED", failed);
		}
	}

	/** Notes a server transfer; the next lobby id decides whether the run ended. */
	public static void onWorldChange() {
		// A connection change also happens during Safari-to-Safari transfers. The next
		// valid scoreboard lobby id decides whether the run truly ended.
	}

	/** Discards server-derived state once a destination is known not to be Hypixel. */
	public static void onNonHypixelConnection() {
		if (current != null) {
			OperationalLog.info("RUN", "Finishing interrupted Safari run after leaving Hypixel");
			endSession(true);
		}
		current = null;
		suppressCurrentRunPersistence = false;
		runLobbyId = null;
		waitingLobbyId = null;
		startedBy = "nothing yet";
		startedAt = 0L;
		rewardSummaryOpen = false;
		completedSummaryLobbyId = null;
		visitPrepared = false;
		visitLobbyId = null;
		visitPeakPlayers = 1;
		visitExpectedPlayers = 1;
		runExpectedPlayers = 1;
		visitEnteredAt = 0L;
		visitRosterLocked = false;
		pendingJoinStartedAt = 0L;
		pendingConnectionJoinedAt = 0L;
		ticketWindowStartedAt = 0L;
		ticketWindowLocked = false;
		TrackingMode.setUnavailable(Set.of());
		resetCompletionAnnouncements();
		SparklingWatch.reset();
		RecatchSpots.clear();
	}

	/** Preserves a meaningful ticket-activated run when Minecraft closes mid-instance. */
	public static void onClientStopping() {
		if (current != null) endSession(true);
	}

	/** Captures the destination boundary before scoreboard-based Safari recognition. */
	public static void onConnectionJoin() {
		long now = System.currentTimeMillis();
		pendingConnectionJoinedAt = now;
		if (pendingJoinStartedAt > 0L && now - pendingJoinStartedAt <= JOIN_SIGNAL_MAX_AGE_MILLIS) {
			DebugLog.line("ACTIVATE", "destination play connection captured +"
				+ formatElapsed(now - pendingJoinStartedAt) + " after queue signal");
		} else {
			DebugLog.line("ACTIVATE", "destination play connection captured without queue signal");
		}
	}

	/** Retains the latest summary and delegates all historical storage to RunHistory. */
	private static void archive(SafariSession session, boolean suppressed) {
		if (suppressed) {
			OperationalLog.info("RUN", "Discarded developer test run without saving");
			return;
		}
		lastSession = session;
		RunHistory.record(session);
	}

	private static boolean persistenceSuppressed() {
		if (current != null && BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)
				&& ConfigManager.get().advanced.testingDoNotSaveRun) {
			suppressCurrentRunPersistence = true;
		}
		return suppressCurrentRunPersistence;
	}

	/** What opened the live run, normally the server's Critter Capsule allocation. */
	public static String startedBy() {
		return current == null ? "no run open"
			: "%s, %ds ago".formatted(startedBy, (System.currentTimeMillis() - startedAt) / 1000);
	}

	/** The run in progress, or {@code null} outside the Safari. */
	public static SafariSession current() {
		return current;
	}

	/** Intended party size for this visit; it never decreases after ticket activation. */
	public static int expectedRunPlayers() {
		return current == null ? Math.max(1, visitExpectedPlayers)
			: Math.max(1, runExpectedPlayers);
	}

	/** Promotes a provisional ticket-trading arrival into the stable run roster. */
	public static void onTicketTradingMemberConfirmed(String name) {
		if (current == null || name == null || name.isBlank()) return;
		current.addParticipant(name);
		runExpectedPlayers = Math.max(runExpectedPlayers, current.players().size());
	}

	/** The run in progress if there is one, otherwise the most recent finished run. */
	public static SafariSession currentOrLast() {
		return current != null ? current : lastSession;
	}

	public static SafariSession lastSession() {
		return lastSession;
	}

	/** Wipes the active run's tallies without waiting to leave the island. */
	public static void reset() {
		current = new SafariSession(selfName(), System.currentTimeMillis());
		TrackingMode.setUnavailable(Set.of());
		resetCompletionAnnouncements();
	}

	private static void resetCompletionAnnouncements() {
		announcedBiomes.clear();
		announcedAllButMacaw = false;
		announcedAllDone = false;
	}

	private static String selfName() {
		return Minecraft.getInstance().getUser().getName();
	}
}
