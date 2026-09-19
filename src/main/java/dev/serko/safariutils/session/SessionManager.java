package dev.serko.safariutils.session;

import dev.serko.safariutils.SafariUtils;
import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.AlertText;
import dev.serko.safariutils.client.EncounterAlerts;
import dev.serko.safariutils.client.FloorDrops;
import dev.serko.safariutils.client.MoundSpotter;
import dev.serko.safariutils.client.CritterCountLog;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.HideyhoSolver;
import dev.serko.safariutils.client.StillCritters;
import dev.serko.safariutils.client.HotspotWatch;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.client.BirdfeederWatch;
import dev.serko.safariutils.client.ShiningCoinWatch;
import dev.serko.safariutils.client.SparklingWatch;
import dev.serko.safariutils.client.SparklingMode;
import dev.serko.safariutils.client.SafariObjectives;
import dev.serko.safariutils.client.NestTracker;
import dev.serko.safariutils.client.RecatchSpots;
import dev.serko.safariutils.client.DetectedCritters;
import dev.serko.safariutils.client.StartingItemsWatch;
import dev.serko.safariutils.client.PartyRosterWatch;
import dev.serko.safariutils.client.SafariPartyWatch;
import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.client.WallTracker;
import dev.serko.safariutils.client.SafeMode;
import dev.serko.safariutils.client.TestingMode;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Starts, updates, finishes, and saves Safari runs. */
public final class SessionManager {

	private static final int MAX_HISTORY = 20;
	private static final String LEADER_RUN_START =
		"[NPC] Safari Manager: I already saw your ticket, so you're free to go.";
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
	private static final List<SafariSession> history = new ArrayList<>();

	private static final java.util.EnumSet<SafariBiome> announcedBiomes =
		java.util.EnumSet.noneOf(SafariBiome.class);

	private static boolean announcedAllButMacaw;
	private static boolean announcedAllDone;

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

	private SessionManager() {
	}

	/** Called every client tick to open/close runs as the player moves around. */
	public static void tick() {
		TrackingMode.setUniqueOnly(ConfigManager.get().display.uniqueOnly || SparklingMode.enabled());
		TrackingMode.setCountSpawns(ConfigManager.get().display.countSpawns);
		updateUnavailable();

		String lobbyId = SafariLocation.lobbyId();
		if (current != null && runLobbyId == null && lobbyId != null) runLobbyId = lobbyId;
		if (current != null && lobbyId != null && runLobbyId != null
			&& !lobbyId.equals(runLobbyId)) {
			DebugLog.line("ACTIVATE", "lobby changed " + runLobbyId + " -> " + lobbyId
				+ ", ending active run");
			endSession();
		}

		if (!SafariLocation.inside()) {
			visitPrepared = false;
			visitLobbyId = null;
			StartingItemsWatch.cancelPendingTicket();
			waitingLobbyId = null;
			completedSummaryLobbyId = null;
			return;
		}
		if (!visitPrepared || lobbyId != null && visitLobbyId != null
			&& !lobbyId.equals(visitLobbyId)) beginVisit(lobbyId);
		else if (visitLobbyId == null && lobbyId != null) visitLobbyId = lobbyId;
		visitPeakPlayers = Math.max(visitPeakPlayers, Math.max(1, SafariPartyWatch.joinedPlayers()));
		if (!visitRosterLocked && PartyRosterWatch.rosterCapturedAt() >= visitEnteredAt) {
			visitExpectedPlayers = Math.max(1, PartyRosterWatch.expectedPlayers());
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
			announceNewlyCompleteBiomes();
			announceRunMilestones();
		}
	}

	/** Feeds one raw chat line into the active run. */
	public static void onChatMessage(String rawText) {
		String line = ChatParser.clean(rawText);
		if (line.isEmpty()) return;
		long now = System.currentTimeMillis();
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
			DebugLog.line("ACTIVATE", "Manager line matched=" + isRunStart(line)
				+ " inside=" + SafariLocation.inside() + " raw=\"" + line + "\"");
		}

		// A Manager confirmation proves ticket submission, but capsule allocation is the
		// final server-side acceptance signal and owns actual run activation.
		if (isRunStart(line)) {
			if (current == null) StartingItemsWatch.onTicketSubmitted("Safari Manager");
			else DebugLog.line("ACTIVATE", "Manager confirmation arrived with a run already active");
			return;
		}

		ChatParser.SparklingCatch sparkling = ChatParser.sparklingCatch(line);
		if (sparkling != null) {
			SparklingWatch.onCaught(sparkling.critter());
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
				if (!TestingMode.enabled()) SparklingStats.recordRainbowFeather();
			}
			return;
		}

		CritterEvent event = ChatParser.parse(line, selfName());
		if (event == null) return;

		if (event.type() == CritterEvent.Type.ENTERED_SAFARI) {
			SafariLocation.markEntered();
			return;
		}

		if (current == null) {
			// Pre-ticket messages never activate or populate a run. Objective and private
			// synchronization trackers have their own Safari-visit context.
			return;
		}

		recordEvent(event, now);
	}

	private static boolean isRunStart(String line) {
		return line.equals(LEADER_RUN_START) || MEMBER_RUN_STARTS.contains(line);
	}

	private static boolean isSummaryDivider(String line) {
		return line.length() >= 20 && line.chars().allMatch(character -> character == '▬');
	}

	private static void recordEvent(CritterEvent event, long now) {
		if (event.type() == CritterEvent.Type.ATTEMPT
			|| event.type() == CritterEvent.Type.FAILED) {
			SparklingWatch.onCaptureInteraction(event.critter());
		}
		current.record(event, now);
		if (event.sparkling() && !TestingMode.enabled()) SparklingStats.recordRainbowFeather();
		if (!event.isCatch()) return;
		EncounterAlerts.onCatch(event.critter().name());
		announceNewlyCompleteBiomes();
		announceRunMilestones();
	}

	/**
	 * Fires a completion alert the moment a biome's last species is caught by anyone.
	 * Each biome announces at most once per run.
	 */
	private static void announceNewlyCompleteBiomes() {
		boolean strongerMilestonePending = !announcedAllDone && current.dexComplete()
			|| !announcedAllButMacaw && current.allCaughtExcept(Critters.MACAW);
		for (SafariBiome biome : SafariBiome.values()) {
			if (announcedBiomes.contains(biome)) continue;
			if (!current.biomeUniquesComplete(biome)) continue;
			announcedBiomes.add(biome);
			// One catch can finish both a biome and the whole-run milestone. In that
			// case the stronger message replaces the redundant biome message.
			if (!strongerMilestonePending) EncounterAlerts.onBiomeComplete(biome);
		}
	}

	/**
	 * Fires the two whole-run milestones, at most once each per run.
	 *
	 * <p>They are mutually exclusive: if a single catch completes the dex outright,
	 * only "Everything Done!" fires, and the weaker "except Macaw" message is marked
	 * as already announced so it cannot follow it.
	 */
	private static void announceRunMilestones() {
		if (!announcedAllDone && current.dexComplete()) {
			announcedAllDone = true;
			announcedAllButMacaw = true;
			EncounterAlerts.onAllDone();
			return;
		}
		if (!announcedAllButMacaw && current.allCaughtExcept(Critters.MACAW)) {
			announcedAllButMacaw = true;
			EncounterAlerts.onAllButMacaw();
		}
	}

	/** Opens a fresh run, then safely files the previous one. */
	public static void startSession(String trigger) {
		if (!visitPrepared) beginVisit(SafariLocation.lobbyId());
		DebugLog.line("RUN", "==== new run started (" + trigger + ") ====");
		SafariSession finished = current;
		current = new SafariSession(selfName(), System.currentTimeMillis());
		runExpectedPlayers = Math.max(visitExpectedPlayers, visitPeakPlayers);
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
				archive(finished);
			} catch (RuntimeException failed) {
				SafariUtils.LOGGER.warn("Could not file the finished run", failed);
			}
		}
		announcedBiomes.clear();
		announcedAllButMacaw = false;
		announcedAllDone = false;
		PartyItemSyncProviders.onRunStarted();
	}

	/** Clears one Safari instance's transient trackers before any ticket is submitted. */
	private static void beginVisit(String lobbyId) {
		visitPrepared = true;
		visitLobbyId = lobbyId;
		visitEnteredAt = System.currentTimeMillis();
		visitRosterLocked = false;
		visitPeakPlayers = Math.max(1, SafariPartyWatch.joinedPlayers());
		visitExpectedPlayers = visitPeakPlayers;
		runExpectedPlayers = 1;
		StartingItemsWatch.cancelPendingTicket();
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
		DebugLog.line("RUN", "Safari visit tracking prepared lobby=" + lobbyId);
	}

	private static void recordLifetimeSparkling(Critter critter) {
		if (!TestingMode.enabled()) SparklingStats.recordSparkling(critter);
		int total = SparklingStats.count(critter) + (TestingMode.enabled() ? 1 : 0);
		var sparklingConfig = ConfigManager.get().sparkling;
		String message = AlertText.format(total == 1
				? sparklingConfig.sparklingFirstCaughtChatText
				: sparklingConfig.sparklingDuplicateCaughtChatText,
			"<CRITTER>", critter.name(), "<COUNT>", Integer.toString(total));
		SparklingWatch.postCaught(message);
	}

	private static void endSession() {
		SafariSession finished = current;
		current = null;
		runLobbyId = null;
		SparklingWatch.reset();
		if (finished == null || finished.isEmpty()) return;
		finished.finish(System.currentTimeMillis());
		if (TestingMode.enabled()) {
			// Keep the completed session available to the live Run panel, but never add
			// an Alpha/test run to history or lifetime totals.
			lastSession = finished;
			DebugLog.line("RUN", "testing session finished without archiving");
			return;
		}
		try {
			archive(finished);
		} catch (RuntimeException failed) {
			SafariUtils.LOGGER.warn("Could not file the finished run", failed);
		}
	}

	/** Notes a server transfer; the next lobby id decides whether the run ended. */
	public static void onWorldChange() {
		// A connection change also happens during Safari-to-Safari transfers. The next
		// valid scoreboard lobby id decides whether the run truly ended.
	}

	/** Keeps a finished run in memory and writes it to history. */
	private static void archive(SafariSession session) {
		lastSession = session;
		history.add(session);
		while (history.size() > MAX_HISTORY) history.removeFirst();
		RunHistory.record(session);
	}

	/** What opened the live run, normally the Safari Manager ticket confirmation. */
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

	/** The run in progress if there is one, otherwise the most recent finished run. */
	public static SafariSession currentOrLast() {
		return current != null ? current : lastSession;
	}

	public static SafariSession lastSession() {
		return lastSession;
	}

	/** Drops transient run state when an isolated test begins, without archiving it. */
	public static void discardForTesting() {
		current = null;
		runLobbyId = null;
		waitingLobbyId = null;
		StartingItemsWatch.cancelPendingTicket();
		rewardSummaryOpen = false;
		visitPrepared = false;
		visitLobbyId = null;
		SparklingWatch.reset();
	}

	public static List<SafariSession> history() {
		return List.copyOf(history);
	}

	/** Wipes the active run's tallies without waiting to leave the island. */
	public static void reset() {
		current = new SafariSession(selfName(), System.currentTimeMillis());
	}

	private static String selfName() {
		return Minecraft.getInstance().getUser().getName();
	}
}
