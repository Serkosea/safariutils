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
import dev.serko.safariutils.client.HeadStartWatch;
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
	private static Integer waitingEssenceBalance;
	private static final List<PendingEvent> pendingEvents = new ArrayList<>();
	/** What opened the live run, and when — reported by {@code /su debug}. */
	private static String startedBy = "nothing yet";
	private static long startedAt;
	/** When the last critter event landed, used to keep an active run from closing. */
	private static long lastEventMillis;
	private static boolean rewardSummaryOpen;
	/** Prevents the summary's final scoreboard update from opening a phantom run. */
	private static String completedSummaryLobbyId;

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
			clearPending();
			completedSummaryLobbyId = null;
			return;
		}
		if (completedSummaryLobbyId != null) {
			if (lobbyId == null || completedSummaryLobbyId.equals(lobbyId)) return;
			completedSummaryLobbyId = null;
		}

		if (lobbyId != null && !lobbyId.equals(waitingLobbyId)) {
			if (current == null) pendingEvents.clear();
			waitingLobbyId = lobbyId;
			waitingEssenceBalance = SafariLocation.safariEssence();
		}

		if (current != null) {
			Integer balance = SafariLocation.safariEssence();
			if (balance != null) current.updateEssenceBalance(balance, System.currentTimeMillis());
		} else {
			Integer balance = SafariLocation.safariEssence();
			if (balance != null && waitingEssenceBalance != null
				&& balance > waitingEssenceBalance) {
				startSession("Safari Essence gained");
			} else if (balance != null) {
				waitingEssenceBalance = balance;
			}
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

		// The Manager confirmation is itself authoritative Safari-only evidence. It can
		// arrive before the tab list/area state has caught up, so location must not gate it.
		if (isRunStart(line)) {
			if (current == null) startSession("Safari Manager");
			else DebugLog.line("ACTIVATE", "Manager confirmation arrived with a run already active");
			return;
		}

		ChatParser.SparklingCatch sparkling = ChatParser.sparklingCatch(line);
		if (sparkling != null) {
			if (current == null && SafariLocation.inside()) startSession("Sparkling catch");
			SparklingWatch.onCaught(sparkling.critter());
			if (current != null) {
				SparklingMode.onSparklingCaught(sparkling.critter());
				current.recordSparkling(sparkling.critter(), sparkling.catcher(), now);
				recordLifetimeSparkling(sparkling.critter());
			}
			else if (SafariLocation.inside()) {
				pendingEvents.add(PendingEvent.sparkling(sparkling, now));
				DebugLog.line("ACTIVATE", "queued pre-activation sparkling "
					+ sparkling.critter().name() + ", pending=" + pendingEvents.size());
			}
			return;
		}

		if (ChatParser.bonusRainbowFeather(line)) {
			if (current == null && SafariLocation.inside()) startSession("Rainbow Feather");
			if (current != null) {
				current.recordBonusRainbowFeather(now);
				if (!TestingMode.enabled()) SparklingStats.recordRainbowFeather();
			}
			else if (SafariLocation.inside()) {
				pendingEvents.add(PendingEvent.bonusFeather(now));
				DebugLog.line("ACTIVATE", "queued pre-activation bonus feather, pending="
					+ pendingEvents.size());
			}
			return;
		}

		// Any collected floor drop proves the ticketed run is already live. This is a
		// safety net for an unexpectedly changed or filtered Manager line.
		if (current == null && SafariLocation.inside() && line.startsWith("FLOOR DROP!")) {
			startSession("first floor drop");
		}

		CritterEvent event = ChatParser.parse(line, selfName());
		if (event == null) return;

		if (event.type() == CritterEvent.Type.ENTERED_SAFARI) {
			SafariLocation.markEntered();
			return;
		}

		if (current == null && SafariLocation.inside() && event.isCatch()) {
			startSession("first catch");
		}

		if (current == null) {
			// A loot share may arrive during the ticket grace period. Keep it provisional:
			// the Manager confirmation commits it, while a warp discards it.
			if (SafariLocation.inside() && event.type() == CritterEvent.Type.SHARED_CATCH) {
				pendingEvents.add(PendingEvent.critter(event, now));
				DebugLog.line("ACTIVATE", "queued pre-activation loot share "
					+ event.critter().name() + ", pending=" + pendingEvents.size());
			}
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
		lastEventMillis = now;
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

	public static void startSession() {
		startSession("command");
	}

	/** Opens a fresh run, then safely files the previous one. */
	public static void startSession(String trigger) {
		DebugLog.line("RUN", "==== new run started (" + trigger + ") ====");
		CritterCountLog.reset();
		SafariSession finished = current;
		current = new SafariSession(selfName(), System.currentTimeMillis());
		SparklingMode.onRunStarted();
		SafariObjectives.reset();
		runLobbyId = SafariLocation.lobbyId();
		waitingLobbyId = runLobbyId;
		waitingEssenceBalance = null;
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
		HeadStartWatch.reset();
		commitPending();
	}

	private static void commitPending() {
		DebugLog.line("ACTIVATE", "committing " + pendingEvents.size() + " pending event(s)");
		for (PendingEvent pending : pendingEvents) {
			if (pending.event() != null) recordEvent(pending.event(), pending.atMillis());
			else if (pending.sparkling() != null) {
				SparklingMode.onSparklingCaught(pending.sparkling().critter());
				current.recordSparkling(pending.sparkling().critter(),
					pending.sparkling().catcher(), pending.atMillis());
				recordLifetimeSparkling(pending.sparkling().critter());
			} else {
				current.recordBonusRainbowFeather(pending.atMillis());
				if (!TestingMode.enabled()) SparklingStats.recordRainbowFeather();
			}
		}
		pendingEvents.clear();
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

	private static void clearPending() {
		if (!pendingEvents.isEmpty()) {
			DebugLog.line("ACTIVATE", "discarding " + pendingEvents.size()
				+ " pending event(s) outside Safari");
		}
		pendingEvents.clear();
		waitingLobbyId = null;
		waitingEssenceBalance = null;
	}

	private static void endSession() {
		SafariSession finished = current;
		current = null;
		runLobbyId = null;
		waitingEssenceBalance = null;
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

	/** What opened the live run — "entry banner", "arrival", "first catch" or a command. */
	public static String startedBy() {
		return current == null ? "no run open"
			: "%s, %ds ago".formatted(startedBy, (System.currentTimeMillis() - startedAt) / 1000);
	}

	/** The run in progress, or {@code null} outside the Safari. */
	public static SafariSession current() {
		return current;
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
		waitingEssenceBalance = null;
		pendingEvents.clear();
		rewardSummaryOpen = false;
		SparklingWatch.reset();
	}

	public static List<SafariSession> history() {
		return List.copyOf(history);
	}

	/** Wipes the active run's tallies without waiting to leave the island. */
	public static void reset() {
		current = new SafariSession(selfName(), System.currentTimeMillis());
	}

	private record PendingEvent(CritterEvent event, ChatParser.SparklingCatch sparkling,
								long atMillis) {
		static PendingEvent critter(CritterEvent event, long atMillis) {
			return new PendingEvent(event, null, atMillis);
		}

		static PendingEvent sparkling(ChatParser.SparklingCatch sparkling, long atMillis) {
			return new PendingEvent(null, sparkling, atMillis);
		}

		static PendingEvent bonusFeather(long atMillis) {
			return new PendingEvent(null, null, atMillis);
		}
	}

	private static String selfName() {
		return Minecraft.getInstance().getUser().getName();
	}
}
