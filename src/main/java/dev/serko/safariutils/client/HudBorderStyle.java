package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;

/** Resolves configurable panel borders and their live biome/contest states. */
final class HudBorderStyle {
	private static final int TICKET_EARNED_GREEN = 0xFF55FF55;
	private static final int TICKET_MISSING_RED = 0xFFFF5555;
	private static final int BETWEEN_CONTESTS_GOLD = 0xFFE0B845;

	private HudBorderStyle() {
	}

	static int progress() {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.progressHudBorder) return 0;
		SafariBiome biome = SafariLocation.biome();
		if (display.progressBorderUseBiomeColour && biome != null) return 0xFF000000 | biome.colour();
		return Colours.argb(display.progressHudBorderColour, 0xFF55FF55);
	}

	static int progressTitle() {
		int colour = progress();
		return colour == 0 ? 0xFFFFAA00 : colour;
	}

	static int missing(SafariBiome biome, SafariSession session) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.missingHudBorder) return 0;
		if (session != null && biome != null && session.biomeComplete(biome)) {
			return Colours.argb(display.completedMissingHudBorderColour, 0xFF55FF55);
		}
		if (display.missingBorderUseBiomeColour && biome != null) return 0xFF000000 | biome.colour();
		return Colours.argb(display.missingHudBorderColour, 0xFFFFAA00);
	}

	static int missingTitle(SafariBiome biome, SafariSession session) {
		// Completion may recolor the border, but the title remains the biome identity.
		return biome == null ? 0xFFFFAA00 : 0xFF000000 | biome.colour();
	}

	static int contest(ContestTracker.Bracket bracket, boolean ticketEarned, boolean betweenContests) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		if (!display.contestHudBorder) return 0;
		int base;
		if (betweenContests) base = BETWEEN_CONTESTS_GOLD;
		else if (display.contestBorderUseBracketColour && bracket != null
			&& (!display.contestBorderUseTicketStatus || ticketEarned)) base = bracket.colour();
		else if (display.contestBorderUseTicketStatus) {
			base = ticketEarned ? TICKET_EARNED_GREEN : TICKET_MISSING_RED;
		} else if (bracket == null) base = TICKET_MISSING_RED;
		else base = Colours.argb(display.contestHudBorderColour, BETWEEN_CONTESTS_GOLD);
		return blend(base, 0xFFFFFFFF, ContestTracker.borderPulse());
	}

	static int contestTitle(ContestTracker.Bracket bracket, boolean ticketEarned, boolean betweenContests) {
		int colour = contest(bracket, ticketEarned, betweenContests);
		return colour == 0 ? 0xFFFFAA00 : colour;
	}

	static int editor(HudBox box) {
		return switch (box) {
			case PROGRESS -> progress();
			case MISSING -> missing(SafariLocation.biome(),
				dev.serko.safariutils.session.SessionManager.currentOrLast());
			case CONTEST -> Colours.argb(ConfigManager.get().display.contestHudBorderColour,
				BETWEEN_CONTESTS_GOLD);
			case ALERTS -> 0xFFFFC857;
		};
	}

	private static int blend(int from, int to, float amount) {
		if (amount <= 0f) return from;
		int red = Math.round(((from >> 16) & 0xFF) * (1 - amount) + ((to >> 16) & 0xFF) * amount);
		int green = Math.round(((from >> 8) & 0xFF) * (1 - amount) + ((to >> 8) & 0xFF) * amount);
		int blue = Math.round((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
