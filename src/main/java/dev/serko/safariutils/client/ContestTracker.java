package dev.serko.safariutils.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks Miria's real-time contest cycle and the player's last tab-list result. */
public final class ContestTracker implements HudElement {
	private final TickCache<HudPanel> panelCache = new TickCache<>();

	private static final long CYCLE_MILLIS = 20 * 60_000L;
	private static final long ACTIVE_MILLIS = 19 * 60_000L + 29_000L;
	private static final long START_OFFSET_MILLIS = 15 * 60_000L + 4_000L;
	private static final long FIVE_MINUTES_MILLIS = 5 * 60_000L;
	private static final long ONE_MINUTE_MILLIS = 60_000L;

	private static final int TITLE = 0xFFFFFF55;
	private static final int LABEL = 0xFFBBBBBB;
	private static final int DIM = 0xFF888888;
	private static final int SCORE = 0xFFFFFF55;
	private static final int YES = 0xFF55FF55;
	private static final int NO = 0xFFFF5555;
	private static final String INACTIVE_VALUE = "—";

	private static final Pattern RESULT = Pattern.compile(
		"^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL) with ([\\d,]+)$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern UNRANKED_SCORE = Pattern.compile("^Collected ([\\d,]+)$",
		Pattern.CASE_INSENSITIVE);

	private static long cycle = Long.MIN_VALUE;
	private static long previousRemaining = -1;
	private static boolean initialized;
	private static Bracket bracket;
	private static int score = -1;
	private static boolean ticket;
	private static boolean ticketAlerted;
	private static String staleResult;
	private static boolean staleResultCleared = true;
	private static long borderPulseUntil;
	private static boolean startAlertPlayed = true;
	private static boolean pendingStartAlert;
	private static boolean persistenceDirty;
	private static long lastPersistenceSave;
	private static List<String> lastParsedTabList = List.of();

	public enum Alert {START, FIVE_MINUTES, ONE_MINUTE, ENDED, TICKET_EARNED}

	/** Updates the clock alerts and reads exact contest values while on Torrhus Canyon. */
	public static void tick() {
		if (Minecraft.getInstance().player == null) return;

		long now = System.currentTimeMillis();
		long currentCycle = Math.floorDiv(now - START_OFFSET_MILLIS, CYCLE_MILLIS);
		long remaining = remainingMillis(now);

		if (!initialized) {
			initialized = true;
			cycle = currentCycle;
			previousRemaining = remaining;
			restoreOrBegin(currentCycle, remaining);
		} else if (currentCycle != cycle) {
			cycle = currentCycle;
			resetResult();
			startAlertPlayed = false;
			pendingStartAlert = true;
			persist(true);
			previousRemaining = remaining;
		} else {
			fireThresholdAlerts(previousRemaining, remaining);
			previousRemaining = remaining;
		}
		firePendingStartAlert();

		if (remaining >= 0 && "Torrhus Canyon".equals(SafariLocation.tabListArea())) {
			List<String> entries = SafariLocation.tabListEntries();
			if (!entries.equals(lastParsedTabList)) {
				lastParsedTabList = List.copyOf(entries);
				readTabList(entries);
			}
		} else {
			lastParsedTabList = List.of();
		}
		flushPersistence();
	}

	private static void fireThresholdAlerts(long previous, long remaining) {
		if (previous > FIVE_MINUTES_MILLIS && remaining <= FIVE_MINUTES_MILLIS) {
			EncounterAlerts.fireContestAlert(Alert.FIVE_MINUTES);
		}
		if (previous > ONE_MINUTE_MILLIS && remaining <= ONE_MINUTE_MILLIS) {
			EncounterAlerts.fireContestAlert(Alert.ONE_MINUTE);
		}
		if (previous >= 0 && remaining < 0) {
			resetResult();
			pendingStartAlert = false;
			persist(true);
			EncounterAlerts.fireContestAlert(Alert.ENDED);
		}
	}

	/** Whether the current contest has already awarded a ticket. */
	public static boolean ticketEarned() {
		return ticket;
	}

	private static void readTabList(List<String> entries) {
		if (entries.stream().noneMatch("Miria's Contest:"::equals)) {
			staleResultCleared = true;
			return;
		}

		boolean foundResult = false;
		for (String entry : entries) {
			Matcher ranked = RESULT.matcher(entry);
			if (ranked.matches()) {
				foundResult = true;
				if (!staleResultCleared && entry.equals(staleResult)) return;
				staleResultCleared = true;
				Bracket found = Bracket.valueOf(ranked.group(1).toUpperCase(Locale.ROOT));
				int foundScore = parseScore(ranked.group(2));
				boolean bracketImproved = bracket == null || found.ordinal() > bracket.ordinal();
				boolean changed = bracket != found || score != foundScore;
				bracket = found;
				score = foundScore;
				if (found.ticket() && !ticket) {
					ticket = true;
					borderPulseUntil = System.currentTimeMillis() + 1_800L;
					if (!ticketAlerted) {
						ticketAlerted = true;
						EncounterAlerts.fireContestAlert(Alert.TICKET_EARNED);
						persist(true);
					}
				}
				if (bracketImproved) borderPulseUntil = System.currentTimeMillis() + 1_800L;
				if (changed) persist(false);
				return;
			}

			Matcher unranked = UNRANKED_SCORE.matcher(entry);
			if (unranked.matches()) {
				foundResult = true;
				if (!staleResultCleared && entry.equals(staleResult)) return;
				staleResultCleared = true;
				int foundScore = parseScore(unranked.group(1));
				boolean changed = bracket != null || score != foundScore;
				bracket = null;
				score = foundScore;
				if (changed) persist(false);
			}
		}
		if (!foundResult) staleResultCleared = true;
	}

	private static int parseScore(String text) {
		try {
			return Integer.parseInt(text.replace(",", ""));
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static void resetResult() {
		lastParsedTabList = List.of();
		if (score >= 0) {
			staleResult = bracket == null
				? "Collected " + "%,d".formatted(score)
				: bracket.name() + " with " + "%,d".formatted(score);
			staleResultCleared = false;
		}
		bracket = null;
		score = -1;
		ticket = false;
		ticketAlerted = false;
	}

	private static void restoreOrBegin(long currentCycle, long remaining) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (remaining >= 0 && display.contestSavedCycle == currentCycle) {
			try {
				bracket = display.contestSavedBracket.isBlank()
					? null : Bracket.valueOf(display.contestSavedBracket);
			} catch (IllegalArgumentException malformed) {
				bracket = null;
			}
			score = display.contestSavedScore;
			ticket = display.contestSavedTicket;
			ticketAlerted = display.contestSavedTicketAlerted;
			startAlertPlayed = display.contestSavedStartAlertPlayed;
			pendingStartAlert = !startAlertPlayed;
			return;
		}

		if (display.contestSavedScore >= 0) {
			staleResult = display.contestSavedBracket.isBlank()
				? "Collected " + "%,d".formatted(display.contestSavedScore)
				: display.contestSavedBracket + " with " + "%,d".formatted(display.contestSavedScore);
			staleResultCleared = false;
		}
		resetResult();
		startAlertPlayed = remaining < 0;
		pendingStartAlert = remaining >= 0;
		persist(true);
	}

	private static void firePendingStartAlert() {
		if (!pendingStartAlert || SafariLocation.tabListArea() == null) return;
		pendingStartAlert = false;
		startAlertPlayed = true;
		EncounterAlerts.fireContestAlert(Alert.START);
		persist(true);
	}

	private static void persist(boolean saveNow) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		display.contestSavedCycle = cycle;
		display.contestSavedBracket = bracket == null ? "" : bracket.name();
		display.contestSavedScore = score;
		display.contestSavedTicket = ticket;
		display.contestSavedTicketAlerted = ticketAlerted;
		display.contestSavedStartAlertPlayed = startAlertPlayed;
		persistenceDirty = true;
		if (saveNow) savePersistence();
	}

	private static void flushPersistence() {
		if (persistenceDirty && System.currentTimeMillis() - lastPersistenceSave >= 1000) {
			savePersistence();
		}
	}

	private static void savePersistence() {
		ConfigManager.save();
		lastPersistenceSave = System.currentTimeMillis();
		persistenceDirty = false;
	}

	/** Remaining active contest time, or {@code -1} during the inactive window. */
	static long remainingMillis(long now) {
		long phase = Math.floorMod(now - START_OFFSET_MILLIS, CYCLE_MILLIS);
		return phase < ACTIVE_MILLIS ? ACTIVE_MILLIS - phase : -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!shouldShow()) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;

		HudPanel panel = panelCache.get(ContestTracker::buildPanel);
		HudBox box = HudBox.CONTEST;
		float scale = box.scale() * ResponsiveUI.scale(graphics.guiWidth(), graphics.guiHeight());
		int x = box.pixelX(graphics.guiWidth(), panel, client.font, scale);
		int y = box.pixelY(graphics.guiHeight(), panel, scale);
		if (SparklingWatch.hudThemeActive()) panel.renderRainbow(graphics, client.font, x, y, scale);
		else panel.render(graphics, client.font, x, y, scale,
			HudBorderStyle.contest(bracket, ticket, remainingMillis(System.currentTimeMillis()) < 0));
	}

	private static boolean shouldShow() {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.showContestHud || display.contestHideOnComplete && ticket) return false;
		String area = SafariLocation.tabListArea();
		if (area == null) return display.contestShowOutsideSkyblock;
		return "Torrhus Canyon".equals(area) || "Safari".equals(area)
			|| display.contestShowEverywhere;
	}

	static HudPanel buildPanel() {
		long now = System.currentTimeMillis();
		long remaining = remainingMillis(now);
		boolean inactive = remaining < 0;
		String time = formatTime(inactive ? untilNextContestMillis(now) : remaining);
		int timeColour = inactive ? YES : timeColour(remaining);

		HudPanel panel = new HudPanel();
		panel.title("Miria's Contest", HudBorderStyle.contestTitle(bracket, ticket, inactive));
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (display.contestTimeRemaining) {
			panel.pair(inactive ? "Contest Starting" : "Contest Time", time, LABEL, timeColour);
		}
		if (!inactive && display.contestCurrentStanding) {
			if (bracket == null) panel.pair("Bracket", INACTIVE_VALUE, LABEL, DIM);
			else panel.boldPair("Bracket", bracket.name(), LABEL, bracket.colour());
			panel.pair("Score", score < 0 ? INACTIVE_VALUE : "%,d".formatted(score),
				LABEL, score < 0 ? DIM : SCORE);
		}
		if (!inactive && display.contestTicketEarned) {
			panel.pair("Safari Ticket", ticket ? "✔" : "✘", LABEL, ticket ? YES : NO);
		}
		return panel;
	}

	static float borderPulse() {
		long left = borderPulseUntil - System.currentTimeMillis();
		if (left <= 0) return 0f;
		return (float) (0.35 + 0.35 * Math.sin(left / 85.0));
	}

	/** Time until the next fixed 20-minute contest cycle begins. */
	private static long untilNextContestMillis(long now) {
		long phase = Math.floorMod(now - START_OFFSET_MILLIS, CYCLE_MILLIS);
		return CYCLE_MILLIS - phase;
	}

	private static String formatTime(long millis) {
		long seconds = (millis + 999) / 1000;
		return "%d:%02d".formatted(seconds / 60, seconds % 60);
	}

	private static int timeColour(long remaining) {
		// Hold the endpoint steady across the active/inactive boundary. The HUD render
		// and contest tick sample the wall clock separately, and without this clamp the
		// final frame can briefly show an interpolated red before the green start timer.
		if (remaining <= 5_000L) return 0xFFAA0000;
		if (remaining >= 10 * 60_000L) return 0xFF55FF55;
		if (remaining >= 5 * 60_000L) {
			return blend(0xFF55FF55, 0xFFFFFF55,
				(10 * 60_000L - remaining) / (5 * 60_000.0));
		}
		if (remaining >= 150_000L) {
			return blend(0xFFFFFF55, 0xFFFFAA00,
				(5 * 60_000L - remaining) / 150_000.0);
		}
		if (remaining >= ONE_MINUTE_MILLIS) {
			return blend(0xFFFFAA00, 0xFFFF5555,
				(150_000L - remaining) / 90_000.0);
		}
		return blend(0xFFFF5555, 0xFFAA0000,
			(ONE_MINUTE_MILLIS - remaining) / (double) ONE_MINUTE_MILLIS);
	}

	private static int blend(int from, int to, double amount) {
		amount = Math.clamp(amount, 0.0, 1.0);
		int red = channel(from, to, 16, amount);
		int green = channel(from, to, 8, amount);
		int blue = channel(from, to, 0, amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static int channel(int from, int to, int shift, double amount) {
		int start = from >> shift & 0xFF;
		int end = to >> shift & 0xFF;
		return (int) Math.round(start + (end - start) * amount);
	}

	enum Bracket {
		COMMON(0xFFFFFFFF, false),
		UNCOMMON(0xFF55FF55, true),
		RARE(0xFF5555FF, true),
		EPIC(0xFFAA00AA, true),
		LEGENDARY(0xFFFFAA00, true),
		MYTHIC(0xFFFF55FF, true),
		DIVINE(0xFF55FFFF, true),
		SPECIAL(0xFFFF5555, true);

		private final int colour;
		private final boolean ticket;

		Bracket(int colour, boolean ticket) {
			this.colour = colour;
			this.ticket = ticket;
		}

		int colour() {
			return colour;
		}

		boolean ticket() {
			return ticket;
		}
	}
}
