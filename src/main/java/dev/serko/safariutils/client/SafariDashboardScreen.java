package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.RunHistory;
import dev.serko.safariutils.session.SparklingStats;
import dev.serko.safariutils.session.RunRecord;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.session.TrackingMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Full-screen view of run, history, statistics, and Sparkling data.
 *
 * <p>One column per biome listing every species, colour-coded by who has it: green if
 * you caught it, aqua if only a partymate did, grey if nobody has. That makes the two
 * questions the run actually turns on — what is left, and who is covering it —
 * answerable at a glance instead of by reading chat.
 */
public final class SafariDashboardScreen extends Screen {

	/** Never narrower than this, however small the window is. */
	private static final int MIN_COLUMN_WIDTH = 70;
	/** Blank space between the longest species name and its count. */
	private static final int COLUMN_GAP = 8;
	/** Blank space between a column's count and the next column. */
	private static final int COLUMN_PAD = 8;
	private static final int LINE_HEIGHT = 11;
	private static final int PANEL_PADDING = 10;
	/** Space reserved for the navigation rail inside the panel. */
	private static final int NAV_CONTENT_OFFSET = 25;
	private static final int NAV_ITEM_WIDTH = 66;
	/** An odd height centers Minecraft's nine-pixel font without a half-pixel bias. */
	private static final int NAV_ITEM_HEIGHT = 21;
	private static final int NAV_TEXT_HEIGHT = 9;
	private static final int NAV_HIGHLIGHT_INSET = 3;
	private static final int ACTION_HEIGHT = 21;
	/** Fixed history viewport; additional saved runs remain available by scrolling. */
	private static final int HISTORY_ROWS = 15;

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d @ h:mm a");
	private static final String STAT_SEPARATOR = "  │  ";

	private static final int CAUGHT_BY_YOU = 0xFF55FF55;
	private static final int CAUGHT_BY_PARTY = 0xFF55FFFF;
	private static final int UNCAUGHT = 0xFF777777;
	private static final int HEADING = 0xFFFFAA00;
	/** Gold, as coins are everywhere else in SkyBlock. */
	private static final int COINS = 0xFFFFD700;
	private static final int LABEL = 0xFFBBBBBB;
	private static final int DIM = 0xFF888888;
	private static final int WHITE = 0xFFFFFFFF;

	private static final int PANEL_BACKGROUND = 0xE00B1017;
	private static final int PANEL_SURFACE = 0xE0141B25;
	private static final int PANEL_SURFACE_HOVER = 0xEF1B2532;
	private static final int PANEL_BORDER = 0x38FFFFFF;
	private static final int PANEL_KEYLINE = 0x24FFFFFF;
	private static final int BAR_TRACK = 0x5A25303D;

	private final SafariSession session;
	private final boolean live;

	/** Which view is showing. Static so it survives closing and reopening the screen. */
	private static Tab tab = Tab.RUN;

	/** The four views available in the Critter Safari panel. */
	private enum Tab {
		/** This run, or the last one if there is none. */
		RUN("Run"),
		/** Filterable saved runs. */
		HISTORY("History"),
		/** Totals per species across every saved run. */
		STATS("Stats"),
		/** Lifetime Sparkling collection, including manually entered older catches. */
		SPARKLING("Sparkling");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private enum HistorySort {
		NEWEST("Newest"),
		OLDEST("Oldest"),
		LONGEST("Longest"),
		SHORTEST("Shortest"),
		MOST_CATCHES("Most Catches"),
		MOST_SPARKLINGS("Most Sparklings");

		final String label;

		HistorySort(String label) {
			this.label = label;
		}
	}

	private enum ControlStyle {TAB, BUTTON, SPARKLING_TOGGLE}

	private record Control(String label, int x, int y, int width, int height,
			ControlStyle style, boolean selected, boolean sparkling, Runnable action) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private final List<Control> controls = new ArrayList<>();
	private boolean sparklingRunsOnly;
	private HistorySort historySort = HistorySort.NEWEST;
	private int historyScroll;
	private List<RunRecord> cachedHistory;
	private int cachedHistorySize = -1;
	private final Map<RunRecord, Integer> cachedRunNumbers = new java.util.IdentityHashMap<>();
	private int cachedRunNumberSize = -1;
	private final Map<RunRecord, HistoryRowView> cachedHistoryRows = new java.util.IdentityHashMap<>();
	private long cachedHistoryPriceRevision = Long.MIN_VALUE;
	private boolean cachedHistoryValueMode;
	private List<RunRecord> cachedHistorySummarySource;
	private long cachedHistorySummaryPriceRevision = Long.MIN_VALUE;
	private String cachedHistorySummary;

	/** Shrinks from the preferred width when the window cannot fit four columns. */
	private int columnWidth;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private float responsiveScale = 1f;
	private int layoutWidth;
	private int layoutHeight;

	public SafariDashboardScreen() {
		super(Component.literal("Critter Safari"));
		tab = Tab.RUN;
		this.session = SessionManager.currentOrLast();
		this.live = SessionManager.current() != null;
		// History controls reset whenever the stats screen is reopened.
		this.sparklingRunsOnly = false;
		this.historySort = HistorySort.NEWEST;
		this.historyScroll = 0;
	}

	/** Whether the money columns and lines are wanted at all. */
	private static boolean showValue() {
		return ConfigManager.get().profit.enabled;
	}

	@Override
	protected void init() {
		controls.clear();
		responsiveScale = ResponsiveUI.scale(width, height);
		layoutWidth = ResponsiveUI.logicalWidth(width, responsiveScale);
		layoutHeight = ResponsiveUI.logicalHeight(height, responsiveScale);
		int columns = SafariBiome.values().length;
		int available = layoutWidth - PANEL_PADDING * 2 - 8;
		// Measured from the longest species name rather than guessed at: "Mantis Shrimp"
		// and "Shuddersquid" ran into their own counts at a fixed width.
		columnWidth = Math.max(MIN_COLUMN_WIDTH, Math.min(measureColumnWidth(), available / columns));
		int columnsWidth = columnWidth * columns + PANEL_PADDING * 2;
		int textWidth = measureTabTextWidth() + PANEL_PADDING * 2;
		panelWidth = Math.min(layoutWidth - 8, Math.max(columnsWidth, textWidth));
		// Price totals add one row to the header and Stats summary.
		int valueRows = showValue() ? 1 : 0;
		int statsValueRows = valueRows;
		panelHeight = NAV_CONTENT_OFFSET + switch (tab) {
			// Header block, the longest biome column (Haunted has 10), then the player table.
			case RUN -> 46 + (2 + valueRows + 10) * LINE_HEIGHT
				+ ((session == null ? 0 : session.uniquePerPlayer().size()) + 2) * LINE_HEIGHT + 40;
			// A summary block, then one line per run shown.
			case HISTORY -> 46 + (HISTORY_ROWS + 2) * LINE_HEIGHT + 46;
			// A summary block, then the species columns.
			case STATS -> 46 + (4 + statsValueRows + 10) * LINE_HEIGHT;
			case SPARKLING -> 46 + (6 + 10) * LINE_HEIGHT;
		};
		panelLeft = Math.max(4, (layoutWidth - panelWidth) / 2);
		panelTop = Math.max(10, (layoutHeight - panelHeight) / 2);

		addTabButtons();

		int buttonY = panelTop + panelHeight - 30;
		if (tab == Tab.HISTORY) addHistoryControls(buttonY);
		else {
			int buttonWidth = 80;
			int buttonX = panelLeft + (panelWidth - buttonWidth) / 2;
			controls.add(new Control("Close", buttonX, buttonY, buttonWidth, ACTION_HEIGHT,
				ControlStyle.BUTTON, false, tab == Tab.SPARKLING, this::onClose));
		}
	}

	private void addHistoryControls(int y) {
		int filterWidth = Math.max(108, font.width("Sparkling Runs") + 30);
		int widestSort = 0;
		for (HistorySort value : HistorySort.values()) {
			widestSort = Math.max(widestSort, font.width("Sort: " + value.label));
		}
		int sortWidth = Math.max(104, widestSort + 16);
		int closeWidth = 60;
		int spacing = 4;
		int totalWidth = filterWidth + sortWidth + closeWidth + spacing * 2;
		int x = panelLeft + (panelWidth - totalWidth) / 2;
		controls.add(new Control("Sparkling Runs", x, y, filterWidth, ACTION_HEIGHT,
			ControlStyle.SPARKLING_TOGGLE, sparklingRunsOnly, true, () -> {
			sparklingRunsOnly = !sparklingRunsOnly;
			historyScroll = 0;
			cachedHistory = null;
			rebuildWidgets();
		}));
		x += filterWidth + spacing;
		controls.add(new Control("Sort: " + historySort.label, x, y, sortWidth, ACTION_HEIGHT,
			ControlStyle.BUTTON, false, false, () -> {
			historySort = next(historySort, HistorySort.values());
			historyScroll = 0;
			cachedHistory = null;
			rebuildWidgets();
		}));
		x += sortWidth + spacing;
		controls.add(new Control("Close", x, y, closeWidth, ACTION_HEIGHT,
			ControlStyle.BUTTON, false, false, this::onClose));
	}

	private static <T extends Enum<T>> T next(T current, T[] values) {
		return values[(current.ordinal() + 1) % values.length];
	}

	/** Wide enough for equal padding and at least three spaces between name and count. */
	private int measureColumnWidth() {
		int widestName = 0;
		int widestCount = font.width("—");
		for (Critter critter : Critters.all()) {
			widestName = Math.max(widestName, font.width(critter.name()));
			widestCount = Math.max(widestCount,
				font.width(String.valueOf(RunHistory.statFor(critter).total())));
		}
		return COLUMN_PAD * 2 + widestName + font.width("   ") + widestCount;
	}

	/** Lets summary-heavy tabs grow equally left and right around their text. */
	private int measureTabTextWidth() {
		int widest = 0;
		if (tab == Tab.HISTORY) {
			widest = Math.max(widest, font.width(historySummary()));
			widest = Math.max(widest, font.width("A run is saved when the next one starts."));
			widest = Math.max(widest, measureHistoryControlsWidth());
			widest = Math.max(widest, measureHistoryTableWidth());
		} else if (tab == Tab.STATS) {
			widest = Math.max(widest, font.width(statsSummary()));
			widest = Math.max(widest, font.width(valueTotalsText()));
		}
		return widest;
	}

	private int measureHistoryControlsWidth() {
		int filterWidth = Math.max(108, font.width("Sparkling Runs") + 30);
		int widestSort = 0;
		for (HistorySort value : HistorySort.values()) {
			widestSort = Math.max(widestSort, font.width("Sort: " + value.label));
		}
		return filterWidth + Math.max(104, widestSort + 16) + 60 + 8;
	}

	private int measureHistoryTableWidth() {
		HistoryColumnWidths widths = historyColumnWidths(filteredHistory(), showValue(), Critters.total());
		return widths.total() + 48 + (font.width("✦") + 3) * 2;
	}

	/** A single navigation rail inside the panel, with one active section. */
	private void addTabButtons() {
		Tab[] tabs = Tab.values();
		int totalWidth = NAV_ITEM_WIDTH * tabs.length;
		int x = panelLeft + (panelWidth - totalWidth) / 2;
		int y = panelTop + 5;

		for (Tab value : tabs) {
			controls.add(new Control(value.label, x, y, NAV_ITEM_WIDTH, NAV_ITEM_HEIGHT, ControlStyle.TAB,
				value == tab, value == Tab.SPARKLING, () -> switchTo(value)));
			x += NAV_ITEM_WIDTH;
		}
	}

	private void switchTo(Tab value) {
		tab = value;
		// The panel is a different height per tab, so everything is laid out again.
		rebuildWidgets();
	}

	private void drawControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawNavigationRail(graphics);
		for (Control control : controls) {
			boolean hovered = control.contains(mouseX, mouseY);
			int colour = control.sparkling
				? UIDraw.rainbow((System.currentTimeMillis() % 4_000L) / 4_000f, 0, 12, 0.55f)
				: control.style == ControlStyle.TAB
					? tabColour(Tab.valueOf(control.label.toUpperCase(java.util.Locale.ROOT)))
					: tabBorderColour();

			if (control.style == ControlStyle.SPARKLING_TOGGLE) {
				graphics.fill(control.x, control.y, control.x + control.width,
					control.y + control.height, hovered ? PANEL_SURFACE_HOVER : PANEL_SURFACE);
				int boxX = control.x + 6;
				int boxY = control.y + (control.height - 9) / 2;
				UIDraw.outline(graphics, control.x, control.y, control.width, control.height, PANEL_BORDER);
				UIDraw.outline(graphics, boxX, boxY, 9, 9, colour);
				if (control.selected) {
					graphics.fill(boxX + 2, boxY + 2, boxX + 7, boxY + 7,
						0xA0000000 | (colour & 0xFFFFFF));
					rainbowText(graphics, font, control.label, control.x + 20,
						control.y + (control.height - NAV_TEXT_HEIGHT) / 2);
				} else {
					text(graphics, font, Component.literal(control.label),
						control.x + 20, control.y + (control.height - NAV_TEXT_HEIGHT) / 2,
						hovered ? WHITE : LABEL);
				}
				continue;
			}

			if (control.style == ControlStyle.TAB) {
				// The rail is one control. Only the current section receives a surface,
				// which keeps the labels from reading as four unrelated buttons.
				if (control.selected) {
					int highlightLeft = control.x + NAV_HIGHLIGHT_INSET;
					int highlightRight = control.x + control.width - NAV_HIGHLIGHT_INSET;
					int highlightBottom = control.y + control.height - NAV_HIGHLIGHT_INSET;
					graphics.fill(highlightLeft, control.y + NAV_HIGHLIGHT_INSET,
						highlightRight, highlightBottom,
						0xB025303D);
					graphics.fill(highlightLeft, highlightBottom - 2,
						highlightRight, highlightBottom, colour);
				} else if (hovered) {
					graphics.fill(control.x + NAV_HIGHLIGHT_INSET,
						control.y + NAV_HIGHLIGHT_INSET,
						control.x + control.width - NAV_HIGHLIGHT_INSET,
						control.y + control.height - NAV_HIGHLIGHT_INSET,
						0x5825303D);
				}
			} else {
				graphics.fill(control.x, control.y, control.x + control.width,
					control.y + control.height, hovered ? PANEL_SURFACE_HOVER : PANEL_SURFACE);
				UIDraw.outline(graphics, control.x, control.y,
					control.width, control.height, PANEL_BORDER);
				if (hovered) {
					graphics.fill(control.x + 1, control.y + 1, control.x + 3,
						control.y + control.height - 1, colour);
				}
			}
			int textX = control.x + Math.round((control.width - font.width(control.label)) / 2.0f);
			int textY = control.y + (control.height - NAV_TEXT_HEIGHT) / 2;
			if (control.sparkling) rainbowText(graphics, font, control.label, textX, textY);
			else text(graphics, font, Component.literal(control.label), textX, textY,
				control.selected || hovered ? WHITE : LABEL);
		}
	}

	/** Draws one quiet backdrop behind all four section labels. */
	private void drawNavigationRail(GuiGraphicsExtractor graphics) {
		Control first = null;
		Control last = null;
		for (Control control : controls) {
			if (control.style != ControlStyle.TAB) continue;
			if (first == null) first = control;
			last = control;
		}
		if (first == null) return;
		int left = first.x;
		int right = last.x + last.width;
		graphics.fillGradient(left, first.y, right, first.y + first.height,
			0x9A18212C, 0x9A111820);
		UIDraw.outline(graphics, left, first.y, right - left, first.height, PANEL_BORDER);
		Control previous = null;
		for (Control control : controls) {
			if (control.style != ControlStyle.TAB) continue;
			if (previous != null) {
				int dividerX = control.x;
				graphics.fill(dividerX, control.y + 5, dividerX + 1,
					control.y + control.height - 5, PANEL_BORDER);
			}
			previous = control;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int logicalMouseX = Math.round(mouseX / responsiveScale);
		int logicalMouseY = Math.round(mouseY / responsiveScale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(responsiveScale, responsiveScale);
		graphics.fillGradient(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight,
			0xE0121922, PANEL_BACKGROUND);
		if (SpecialTheme.rainbow()) {
			SpecialTheme.stars(graphics, panelLeft + 2, panelTop + 2, panelWidth - 4, panelHeight - 4);
			SpecialTheme.border(graphics, panelLeft, panelTop, panelWidth, panelHeight);
		} else if (tab == Tab.SPARKLING) drawSparklingBorder(graphics);
		else drawPanelBorder(graphics, tabBorderColour());
		// Drawn after the main frame so a two-pixel themed border cannot cover it.
		UIDraw.outline(graphics, panelLeft + 2, panelTop + 2, panelWidth - 4,
			panelHeight - 4, PANEL_KEYLINE);

		Font font = this.font;
		int y = panelTop + PANEL_PADDING + NAV_CONTENT_OFFSET;

		switch (tab) {
			case RUN -> {
				if (session == null) {
					text(graphics, font, Component.literal("Not Currently in a Safari Run"),
						panelLeft + PANEL_PADDING, y, LABEL);
				} else {
					y = drawHeader(graphics, font, y);
					y = drawBiomeColumns(graphics, font, y + 4);
					drawPlayers(graphics, font, y + 6);
				}
			}
			case HISTORY -> drawHistory(graphics, font, y, logicalMouseX, logicalMouseY);
			case STATS -> drawStats(graphics, font, y);
			case SPARKLING -> drawSparkling(graphics, font, y);
		}

		// Screen children first, then the themed controls above the panel.
		super.extractRenderState(graphics, logicalMouseX, logicalMouseY, partialTick);
		drawControls(graphics, logicalMouseX, logicalMouseY);
		graphics.pose().popMatrix();
	}

	private int drawHeader(GuiGraphicsExtractor graphics, Font font, int y) {
		int total = Critters.total();
		int left = panelLeft + PANEL_PADDING;

		String heading = live
			? "Critter Safari  " + ProgressHud.formatDuration(session.elapsedMillis(System.currentTimeMillis()))
			: "Critter Safari  (Last Run)";
		text(graphics, font, Component.literal(heading), left, y, tabBorderColour());

		String legend = "You" + STAT_SEPARATOR + "Party" + STAT_SEPARATOR + "Uncaught";
		text(graphics, font, Component.literal(legend),
			panelLeft + panelWidth - PANEL_PADDING - font.width(legend), y, DIM);
		y += LINE_HEIGHT + 3;

		y = drawSummaryBar(graphics, font, left, y, "Party", session.partyUnique(), total,
			session.dexComplete() ? CAUGHT_BY_YOU : WHITE);
		y = drawSummaryBar(graphics, font, left, y, "You", session.ownUnique(), total, CAUGHT_BY_PARTY);
		if (showValue()) y = drawValue(graphics, font, left, y);
		return y;
	}

	/**
	 * What the shards this run has given you are worth.
	 *
	 * <p>Yours and loot-shared together, since both land in your inventory — and only
	 * yours, because a partymate's own catches are never itemised to this client.
	 */
	private int drawValue(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		text(graphics, font, Component.literal("Value"), x, y, LABEL);

		int shards = session.totalShards();
		String text;
		int colour = COINS;
		if (!BazaarPrices.known()) {
			text = shards == 0 ? "No Shards Yet" : "%d Shards%sBazaar Prices Not Loaded"
				.formatted(shards, STAT_SEPARATOR);
			colour = DIM;
		} else {
			long coins = BazaarPrices.valueOf(session);
			text = "%s%s%d Shard%s%s%d Essence%s%d Feather%s".formatted(
				BazaarPrices.coinsText(session), STAT_SEPARATOR, shards, shards == 1 ? "" : "s",
				STAT_SEPARATOR, session.safariEssence(), STAT_SEPARATOR,
				session.rainbowFeathers(), session.rainbowFeathers() == 1 ? "" : "s");
			// Per hour only once there is enough of a run to divide by; a minute in, the
			// rate says more about the last catch than about the run.
			long elapsed = live ? session.elapsedMillis(System.currentTimeMillis())
				: session.durationMillis();
			if (elapsed >= 60_000 && coins > 0) {
				text += STAT_SEPARATOR + "%s/h".formatted(BazaarPrices.format(coins * 3_600_000L / elapsed));
			}
		}
		text(graphics, font, Component.literal(text), x + 40, y, colour);
		return y + LINE_HEIGHT + 2;
	}

	private int drawSummaryBar(GuiGraphicsExtractor graphics, Font font, int x, int y,
							   String label, int current, int max, int colour) {
		text(graphics, font, Component.literal(label), x, y, LABEL);

		int barLeft = x + 40;
		int barWidth = 150;
		int barY = y + 2;
		graphics.fill(barLeft, barY, barLeft + barWidth, barY + 5, BAR_TRACK);
		if (current > 0) {
			graphics.fill(barLeft, barY, barLeft + Math.max(1, barWidth * current / max), barY + 5, colour);
		}
		text(graphics, font, Component.literal(current + "/" + max), barLeft + barWidth + 8, y, colour);
		return y + LINE_HEIGHT + 2;
	}

	private int drawBiomeColumns(GuiGraphicsExtractor graphics, Font font, int y) {
		int bottom = y;
		SafariBiome[] biomes = SafariBiome.values();

		for (int i = 0; i < biomes.length; i++) {
			SafariBiome biome = biomes[i];
			int x = panelLeft + PANEL_PADDING + i * columnWidth;
			int rowY = y;

			boolean complete = session.biomeComplete(biome);
			int max = Critters.totalIn(biome);
			text(graphics, font, Component.literal(biome.displayName()), x, rowY,
				0xFF000000 | biome.colour());
			String count = session.partyUnique(biome) + "/" + max;
			text(graphics, font, Component.literal(count), x + columnWidth - COLUMN_PAD - font.width(count), rowY,
				complete ? CAUGHT_BY_YOU : WHITE);
			rowY += LINE_HEIGHT + 2;

			for (Critter critter : Critters.inBiome(biome)) {
				// Grey until the run is actually finished with it, so a quota species
				// caught once still reads as outstanding.
				int colour = session.isUnavailable(critter) ? UNCAUGHT
					: !session.isComplete(critter) ? UNCAUGHT
					: session.caughtByYou(critter) ? CAUGHT_BY_YOU : CAUGHT_BY_PARTY;
				text(graphics, font, Component.literal(critter.name()), x, rowY, colour);

				// Quota species show progress towards their total; the rest show repeat
				// catches, or an attempt count meaning it is around and escaping.
				int caught = session.partyCatches(critter);
				int total = session.required(critter);
				boolean known = total > 1 && !TrackingMode.uniqueOnly();
				String note = session.isUnavailable(critter)
					? ("Rockmite".equals(critter.name()) ? "" : "N/A")
					: known ? caught + "/" + total
					: caught > 1 ? "x" + caught
					: caught == 0 && session.attempts(critter) > 0 ? session.attempts(critter) + "t" : "";
				if (!note.isEmpty()) {
					text(graphics, font, Component.literal(note),
						x + columnWidth - COLUMN_PAD - font.width(note), rowY, DIM);
				}
				rowY += LINE_HEIGHT;
			}
			bottom = Math.max(bottom, rowY);
		}
		return bottom;
	}

	private void drawPlayers(GuiGraphicsExtractor graphics, Font font, int y) {
		Map<String, Map<SafariBiome, Integer>> perPlayer = session.uniquePerPlayer();
		int left = panelLeft + PANEL_PADDING;

		graphics.fill(left, y, panelLeft + panelWidth - PANEL_PADDING, y + 1, PANEL_BORDER);
		y += 5;

		if (perPlayer.isEmpty()) {
			text(graphics, font, Component.literal("Nobody has caught anything yet."), left, y, DIM);
			return;
		}

		// Fixed columns, since the proportional font makes padded text impossible to align.
		int nameWidth = Math.min(110,
			Math.max(font.width("Unique Per Player") + 12, panelWidth / 3));
		int cellWidth = Math.max(28, (panelWidth - PANEL_PADDING * 2 - nameWidth) / SafariBiome.values().length);
		SafariBiome[] biomes = SafariBiome.values();

		text(graphics, font, Component.literal("Unique Per Player"), left, y, LABEL);
		for (int i = 0; i < biomes.length; i++) {
			String header = biomes[i].displayName();
			int cellLeft = left + nameWidth + i * cellWidth;
			text(graphics, font, Component.literal(header),
				cellLeft + (cellWidth - font.width(header)) / 2, y,
				0xFF000000 | biomes[i].colour());
		}
		y += LINE_HEIGHT + 2;

		for (Map.Entry<String, Map<SafariBiome, Integer>> entry : perPlayer.entrySet()) {
			boolean self = entry.getKey().equals(session.selfName());
			text(graphics, font, Component.literal(entry.getKey()), left, y, self ? CAUGHT_BY_YOU : WHITE);
			for (int i = 0; i < biomes.length; i++) {
				String value = String.valueOf(entry.getValue().getOrDefault(biomes[i], 0));
				int cellLeft = left + nameWidth + i * cellWidth;
				text(graphics, font, Component.literal(value),
					cellLeft + (cellWidth - font.width(value)) / 2, y,
					"0".equals(value) ? UNCAUGHT : LABEL);
			}
			y += LINE_HEIGHT;
		}
	}

	/**
	 * Filtered and sorted saved runs in a fixed scrollable viewport.
	 *
	 * <p>Runs are written when the next one starts, so the one you are in is not here
	 * yet — that is what the Run tab is for.
	 */
	private void drawHistory(GuiGraphicsExtractor graphics, Font font, int y, int mouseX, int mouseY) {
		int left = panelLeft + PANEL_PADDING;
		int right = panelLeft + panelWidth - PANEL_PADDING;
		List<RunRecord> allRuns = RunHistory.runs();
		List<RunRecord> runs = filteredHistory();
		List<HistoryDisplayRow> displayRows = historyDisplayRows(runs);
		historyScroll = Math.clamp(historyScroll, 0, Math.max(0, displayRows.size() - HISTORY_ROWS));

		text(graphics, font, Component.literal("Saved Runs"), left, y, tabBorderColour());
		String count = !sparklingRunsOnly
			? allRuns.size() + " Kept"
			: runs.size() + "/" + allRuns.size() + " Shown";
		text(graphics, font, Component.literal(count), right - font.width(count), y, DIM);
		y += LINE_HEIGHT + 3;

		if (allRuns.isEmpty()) {
			centered(graphics, font, "No runs saved yet.", y, LABEL);
			y += LINE_HEIGHT;
			centered(graphics, font, "A run is saved when the next one starts.", y, DIM);
			y += LINE_HEIGHT;
			return;
		}

		boolean value = showValue();
		centered(graphics, font, historySummary(runs), y, LABEL);
		y += LINE_HEIGHT + 5;

		// Fixed columns: the proportional font makes padded text impossible to align.
		int sparkleGutter = font.width("✦") + 3;
		int tableLeft = left + sparkleGutter;
		int tableRight = right - sparkleGutter;
		HistoryColumnWidths widths = historyColumnWidths(runs, value, Critters.total());
		int free = Math.max(0, tableRight - tableLeft - widths.total());
		int runX = tableLeft + distributedGap(free, 1);
		int dateX = tableLeft + widths.run() + distributedGap(free, 2);
		int lengthX = tableLeft + widths.run() + widths.date() + distributedGap(free, 3);
		int uniquesX = tableLeft + widths.run() + widths.date() + widths.length()
			+ distributedGap(free, 4);
		int catchesX = tableLeft + widths.run() + widths.date() + widths.length()
			+ widths.uniques() + distributedGap(free, 5);
		centeredCell(graphics, font, "Run", runX, widths.run(), y, LABEL);
		centeredCell(graphics, font, "Date", dateX, widths.date(), y, LABEL);
		centeredCell(graphics, font, "Length", lengthX, widths.length(), y, LABEL);
		centeredCell(graphics, font, "Uniques", uniquesX, widths.uniques(), y, LABEL);
		centeredCell(graphics, font, "Catches", catchesX, widths.catches(), y, LABEL);
		y += LINE_HEIGHT + 2;
		int rowsTop = y;

		int total = Critters.total();
		RunRecord hovered = null;
		int end = Math.min(displayRows.size(), historyScroll + HISTORY_ROWS);
		for (int i = historyScroll; i < end; i++) {
			HistoryDisplayRow displayRow = displayRows.get(i);
			if (displayRow.divider()) {
				drawYearDivider(graphics, font, displayRow.year(), left, right, y);
				y += LINE_HEIGHT;
				continue;
			}
			RunRecord run = displayRow.run();
			HistoryRowView row = historyRow(run, value, total);
			boolean sparkling = row.sparkling;
			if (mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + LINE_HEIGHT) {
				hovered = run;
				graphics.fill(left, y - 1, right, y + LINE_HEIGHT - 1, 0x24FFFFFF);
			}
			if (sparkling) {
				int runTextLeft = runX + (widths.run() - font.width(row.runLabel)) / 2;
				int finalTextRight = catchesX + (widths.catches() + font.width(row.catches)) / 2;
				rainbowText(graphics, font, "✦", runTextLeft - 8 - font.width("✦"), y);
				rainbowText(graphics, font, "✦", finalTextRight + 8, y);
			}
			historyCell(graphics, font, row.runLabel,
				runX, widths.run(), y, WHITE, sparkling);
			historyCell(graphics, font, row.date,
				dateX, widths.date(), y, WHITE, sparkling);
			historyCell(graphics, font, row.length,
				lengthX, widths.length(), y, DIM, sparkling);
			historyCell(graphics, font, row.uniques,
				uniquesX, widths.uniques(), y,
				row.perfect ? CAUGHT_BY_YOU : WHITE, sparkling);
			historyCell(graphics, font, row.catches,
				catchesX, widths.catches(), y,
				row.priced ? COINS : DIM, sparkling);
			y += LINE_HEIGHT;
		}

		int detailsY = rowsTop + HISTORY_ROWS * LINE_HEIGHT + 3;
		if (hovered != null && sparklingCount(hovered) > 0) {
			rainbowCentered(graphics, font, sparklingSummary(hovered), detailsY);
		} else if (runs.isEmpty()) {
			centered(graphics, font, "No runs match this filter.", detailsY, DIM);
		} else {
			int first = 1 + countRuns(displayRows, 0, historyScroll);
			int last = first + countRuns(displayRows, historyScroll, end) - 1;
			centered(graphics, font, "Showing %d–%d of %d%sScroll for more"
				.formatted(first, Math.max(first, last), runs.size(), STAT_SEPARATOR), detailsY, DIM);
		}
	}

	/**
	 * What every species has been worth across the saved runs.
	 *
	 * <p>The totals are cached by {@link RunHistory}; opening this tab does not rescan
	 * every saved run each frame.
	 */
	private void drawStats(GuiGraphicsExtractor graphics, Font font, int y) {
		int left = panelLeft + PANEL_PADDING;
		int right = panelLeft + panelWidth - PANEL_PADDING;
		int runs = RunHistory.size();

		text(graphics, font, Component.literal("Total Run Stats"), left, y, tabBorderColour());
		String legend = "Total Catches";
		text(graphics, font, Component.literal(legend), right - font.width(legend), y, DIM);
		y += LINE_HEIGHT + 3;

		if (runs == 0) {
			centered(graphics, font, "Nothing saved yet.", y, LABEL);
			return;
		}

		centered(graphics, font, statsSummary(), y, LABEL);
		y += LINE_HEIGHT;

		if (showValue()) {
			y = drawValueTotals(graphics, font, y);
		}
		y += 5;

		SafariBiome[] biomes = SafariBiome.values();
		int gridLeft = panelLeft + (panelWidth - columnWidth * biomes.length) / 2;
		for (int i = 0; i < biomes.length; i++) {
			SafariBiome biome = biomes[i];
			int x = gridLeft + i * columnWidth;
			int rowY = y;

			centeredCell(graphics, font, biome.displayName(), x, columnWidth, rowY,
				0xFF000000 | biome.colour());
			rowY += LINE_HEIGHT + 2;

			for (Critter critter : Critters.inBiome(biome)) {
				RunHistory.SpeciesStat stat = RunHistory.statFor(critter);
				String note = stat.total() == 0 ? "—" : String.valueOf(stat.total());
				text(graphics, font, Component.literal(critter.name()), x + COLUMN_PAD, rowY,
					0xFF000000 | critter.rarity().colour());
				text(graphics, font, Component.literal(note),
					x + columnWidth - COLUMN_PAD - font.width(note), rowY,
					stat.total() == 0 ? UNCAUGHT : DIM);
				rowY += LINE_HEIGHT;
			}
		}
	}

	/**
	 * Money across the saved runs: everything they were worth, and what that is a run.
	 *
	 * <p>The average excludes old runs without a shard breakdown instead of treating
	 * their unknown value as zero.
	 */
	private int drawValueTotals(GuiGraphicsExtractor graphics, Font font, int y) {
		int priced = RunHistory.pricedRuns();

		if (!BazaarPrices.known()) {
			centered(graphics, font, BazaarPrices.lastError() == null
					? "Waiting for bazaar prices…"
					: "No bazaar prices: " + BazaarPrices.lastError(), y, DIM);
			return y + LINE_HEIGHT;
		}

		if (priced > 0) {
			centered(graphics, font, valueTotalsText(), y, COINS);
			y += LINE_HEIGHT;
		}

		return y;
	}

	private void drawSparkling(GuiGraphicsExtractor graphics, Font font, int y) {
		int left = panelLeft + PANEL_PADDING;
		int right = panelLeft + panelWidth - PANEL_PADDING;
		int totalSpecies = Critters.total();

		rainbowText(graphics, font, "Sparkling Collection", left, y);
		String lifetime = "Lifetime Totals";
		text(graphics, font, Component.literal(lifetime), right - font.width(lifetime), y, 0xFFFFD86B);
		y += LINE_HEIGHT + 4;

		String summary = "Unique Sparklings  %d/%d   ✦   Sparklings  %d   ✦   Duplicates  %d   ✦   Rainbow Feathers  %d"
			.formatted(SparklingStats.unique(), totalSpecies, SparklingStats.total(),
				SparklingStats.duplicates(), SparklingStats.rainbowFeathers());
		centered(graphics, font, summary, y, 0xFFFFE08A);
		y += LINE_HEIGHT + 3;

		int barLeft = panelLeft + PANEL_PADDING * 2;
		int barRight = panelLeft + panelWidth - PANEL_PADDING * 2;
		graphics.fill(barLeft, y, barRight, y + 4, 0x553A2A10);
		int filled = totalSpecies == 0 ? 0
			: (barRight - barLeft) * SparklingStats.unique() / totalSpecies;
		graphics.fill(barLeft, y, barLeft + filled, y + 4, 0xFFFFC83D);
		y += 10;

		SafariBiome[] biomes = SafariBiome.values();
		int gridLeft = panelLeft + (panelWidth - columnWidth * biomes.length) / 2;
		for (int i = 0; i < biomes.length; i++) {
			SafariBiome biome = biomes[i];
			int x = gridLeft + i * columnWidth;
			int rowY = y;
			centeredCell(graphics, font, "✦ " + biome.displayName() + " ✦", x, columnWidth,
				rowY, 0xFF000000 | biome.colour());
			rowY += LINE_HEIGHT + 2;

			for (Critter critter : Critters.inBiome(biome)) {
				int count = SparklingStats.count(critter);
				String note = count == 0 ? "—" : String.valueOf(count);
				int nameColour = count == 0 ? 0xFF5F594E
					: 0xFF000000 | critter.rarity().colour();
				int countColour = count > 1 ? 0xFFFFD700 : count == 1 ? 0xFFFFF2B2 : 0xFF5F594E;
				text(graphics, font, Component.literal(critter.name()), x + COLUMN_PAD, rowY, nameColour);
				text(graphics, font, Component.literal(note),
					x + columnWidth - COLUMN_PAD - font.width(note), rowY, countColour);
				rowY += LINE_HEIGHT;
			}
		}
	}

	private void drawSparklingBorder(GuiGraphicsExtractor graphics) {
		int horizontalSegments = 28;
		int verticalSegments = Math.max(1, Math.round(horizontalSegments * panelHeight / (float) panelWidth));
		int totalSegments = 2 * (horizontalSegments + verticalSegments);
		int segmentWidth = Math.max(1, panelWidth / horizontalSegments);
		int segmentHeight = Math.max(1, panelHeight / verticalSegments);
		float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
		for (int i = 0; i < horizontalSegments; i++) {
			int x1 = panelLeft + i * segmentWidth;
			int x2 = i == horizontalSegments - 1 ? panelLeft + panelWidth : Math.min(panelLeft + panelWidth, x1 + segmentWidth);
			int colour = UIDraw.rainbow(phase, i, totalSegments, 0.55f);
			graphics.fill(x1, panelTop, x2, panelTop + 2, colour);
			// Bottom runs right-to-left so the hue continues clockwise from the
			// right edge instead of beginning a visibly separate gradient.
			graphics.fill(panelLeft + panelWidth - (x2 - panelLeft), panelTop + panelHeight - 2,
				panelLeft + panelWidth - (x1 - panelLeft), panelTop + panelHeight,
				UIDraw.rainbow(phase, horizontalSegments + verticalSegments + i, totalSegments, 0.55f));
		}
		for (int i = 0; i < verticalSegments; i++) {
			int y1 = panelTop + i * segmentHeight;
			int y2 = i == verticalSegments - 1 ? panelTop + panelHeight : Math.min(panelTop + panelHeight, y1 + segmentHeight);
			graphics.fill(panelLeft + panelWidth - 2, y1, panelLeft + panelWidth, y2,
				UIDraw.rainbow(phase, horizontalSegments + i, totalSegments, 0.55f));
			// Left runs bottom-to-top to complete the same clockwise loop.
			graphics.fill(panelLeft, panelTop + panelHeight - (y2 - panelTop), panelLeft + 2,
				panelTop + panelHeight - (y1 - panelTop),
				UIDraw.rainbow(phase, 2 * horizontalSegments + verticalSegments + i, totalSegments, 0.55f));
		}
	}

	private int tabBorderColour() {
		return tabColour(tab);
	}

	private static int tabColour(Tab target) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		return switch (target) {
			case RUN -> Colours.argb(display.currentRunTabBorderColour, 0xFF55FF55);
			case HISTORY -> Colours.argb(display.historyTabBorderColour, 0xFFFFAA00);
			case STATS -> Colours.argb(display.statsTabBorderColour, 0xFF55FFFF);
			case SPARKLING -> 0xFFFFD700;
		};
	}

	private void drawPanelBorder(GuiGraphicsExtractor graphics, int colour) {
		UIDraw.outline(graphics, panelLeft, panelTop, panelWidth, panelHeight, colour);
		UIDraw.outline(graphics, panelLeft + 1, panelTop + 1,
			panelWidth - 2, panelHeight - 2, colour);
	}

	private static void rainbowText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
		if (SpecialTheme.rainbow()) {
			SpecialTheme.rainbowText(graphics, font, text, x, y);
			return;
		}
		UIDraw.rainbowText(graphics, font, text, x, y, 0.45f);
	}

	private void historyCell(GuiGraphicsExtractor graphics, Font font, String value,
			int x, int width, int y, int colour, boolean sparkling) {
		int textX = x + (width - font.width(value)) / 2;
		if (sparkling && !SpecialTheme.rainbow()) {
			float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
			float position = (textX - panelLeft) / (float) Math.max(1, panelWidth);
			int rainbow = 0xFF000000 | (java.awt.Color.HSBtoRGB(
				(phase + position) % 1f, 0.45f, 1f) & 0xFFFFFF);
			text(graphics, font, Component.literal(value), textX, y, rainbow);
		} else {
			text(graphics, font, Component.literal(value), textX, y, colour);
		}
	}

	private HistoryRowView historyRow(RunRecord run, boolean value, int totalCritters) {
		long priceRevision = BazaarPrices.revision();
		if (cachedHistoryPriceRevision != priceRevision || cachedHistoryValueMode != value) {
			cachedHistoryRows.clear();
			cachedHistoryPriceRevision = priceRevision;
			cachedHistoryValueMode = value;
		}
		return cachedHistoryRows.computeIfAbsent(run, current -> {
			boolean priced = value && current.hasShardData() && BazaarPrices.known();
			String catches = String.valueOf(current.partyTotal());
			if (value) catches += " (" + (priced
				? BazaarPrices.format(BazaarPrices.valueOf(current)) : "—") + ")";
			return new HistoryRowView("#" + runNumber(current), formatWhen(current.started),
				ProgressHud.formatDuration(current.durationMillis()), current.partyUnique() + "/" + totalCritters,
				catches,
				sparklingCount(current) > 0, current.partyUnique() == totalCritters, priced);
		});
	}

	private record HistoryRowView(String runLabel, String date, String length, String uniques,
			String catches, boolean sparkling, boolean perfect,
			boolean priced) { }

	private record HistoryColumnWidths(int run, int date, int length, int uniques, int catches) {
		int total() {
			return run + date + length + uniques + catches;
		}
	}

	private HistoryColumnWidths historyColumnWidths(List<RunRecord> runs, boolean value,
			int totalCritters) {
		int run = font.width("Run");
		int date = font.width("Date");
		int length = font.width("Length");
		int uniques = font.width("Uniques");
		int catches = font.width("Catches");
		for (RunRecord savedRun : runs) {
			HistoryRowView row = historyRow(savedRun, value, totalCritters);
			run = Math.max(run, font.width(row.runLabel()));
			date = Math.max(date, font.width(row.date()));
			length = Math.max(length, font.width(row.length()));
			uniques = Math.max(uniques, font.width(row.uniques()));
			catches = Math.max(catches, font.width(row.catches()));
		}
		return new HistoryColumnWidths(run, date, length, uniques, catches);
	}

	/** Cumulative sixths keep all five column gaps and both outer margins even. */
	private static int distributedGap(int free, int boundary) {
		return Math.round(free * boundary / 6f);
	}

	private record HistoryDisplayRow(RunRecord run, int year, boolean divider) { }

	private static List<HistoryDisplayRow> historyDisplayRows(List<RunRecord> runs) {
		List<HistoryDisplayRow> rows = new ArrayList<>();
		Integer previousYear = null;
		for (RunRecord run : runs) {
			int year = yearOf(run.started);
			if (previousYear != null && year != previousYear) {
				rows.add(new HistoryDisplayRow(null, previousYear, true));
			}
			rows.add(new HistoryDisplayRow(run, year, false));
			previousYear = year;
		}
		if (previousYear != null) rows.add(new HistoryDisplayRow(null, previousYear, true));
		return rows;
	}

	private static int countRuns(List<HistoryDisplayRow> rows, int from, int to) {
		int count = 0;
		for (int i = from; i < to; i++) if (!rows.get(i).divider()) count++;
		return count;
	}

	private void drawYearDivider(GuiGraphicsExtractor graphics, Font font, int year,
			int left, int right, int y) {
		String label = String.valueOf(year);
		int centre = (left + right) / 2;
		int halfLabel = font.width(label) / 2;
		int lineY = y + 4;
		graphics.fill(left + 8, lineY, centre - halfLabel - 8, lineY + 1, PANEL_BORDER);
		graphics.fill(centre + halfLabel + 8, lineY, right - 8, lineY + 1, PANEL_BORDER);
		centered(graphics, font, label, y, tabBorderColour());
	}

	private void rainbowCentered(GuiGraphicsExtractor graphics, Font font, String value, int y) {
		rainbowText(graphics, font, value, panelLeft + (panelWidth - font.width(value)) / 2, y);
	}

	private static int sparklingCount(RunRecord run) {
		return run.sparklings == null ? 0 : run.sparklings.size();
	}

	/** Chronological saved-run number, independent of the current filter and sort. */
	private int runNumber(RunRecord target) {
		if (cachedRunNumberSize != RunHistory.size()) {
			List<RunRecord> chronological = new ArrayList<>(RunHistory.runs());
			chronological.sort(Comparator.comparingLong(run -> run.started));
			cachedRunNumbers.clear();
			for (int i = 0; i < chronological.size(); i++) {
				cachedRunNumbers.put(chronological.get(i), i + 1);
			}
			cachedRunNumberSize = RunHistory.size();
		}
		return cachedRunNumbers.getOrDefault(target, 0);
	}

	private static String sparklingSummary(RunRecord run) {
		Map<String, Integer> species = new java.util.LinkedHashMap<>();
		if (run.sparklings != null) {
			for (RunRecord.SparklingRecord sparkling : run.sparklings) {
				if (sparkling != null && sparkling.species() != null) {
					species.merge(sparkling.species(), 1, Integer::sum);
				}
			}
		}
		String names = species.entrySet().stream()
			.map(entry -> entry.getKey() + (entry.getValue() > 1 ? " ×" + entry.getValue() : ""))
			.collect(java.util.stream.Collectors.joining(", "));
		return "✦ " + sparklingCount(run) + " Sparkling" + (sparklingCount(run) == 1 ? "" : "s")
			+ (names.isEmpty() ? "" : ": " + names) + " ✦";
	}

	private List<RunRecord> filteredHistory() {
		if (cachedHistory != null && cachedHistorySize == RunHistory.size()) return cachedHistory;
		List<RunRecord> runs = new ArrayList<>();
		for (RunRecord run : RunHistory.runs()) {
			if (!sparklingRunsOnly || sparklingCount(run) > 0) runs.add(run);
		}
		Comparator<RunRecord> order = switch (historySort) {
			case NEWEST -> Comparator.comparingLong((RunRecord run) -> run.started).reversed();
			case OLDEST -> Comparator.comparingLong(run -> run.started);
			case LONGEST -> Comparator.comparingLong(RunRecord::durationMillis).reversed()
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
			case SHORTEST -> Comparator.comparingLong(RunRecord::durationMillis)
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
			case MOST_CATCHES -> Comparator.comparingInt(RunRecord::partyTotal).reversed()
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
			case MOST_SPARKLINGS -> Comparator.comparingInt(SafariDashboardScreen::sparklingCount).reversed()
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
		};
		runs.sort(order);
		cachedHistorySize = RunHistory.size();
		cachedHistory = List.copyOf(runs);
		return cachedHistory;
	}

	private String historySummary() {
		return historySummary(filteredHistory());
	}

	private String historySummary(List<RunRecord> runs) {
		long priceRevision = BazaarPrices.revision();
		if (cachedHistorySummarySource == runs
				&& cachedHistorySummaryPriceRevision == priceRevision
				&& cachedHistorySummary != null) return cachedHistorySummary;
		long played = runs.stream().mapToLong(RunRecord::durationMillis).sum();
		int catches = runs.stream().mapToInt(RunRecord::partyTotal).sum();
		String summary = "%d Run%s%s%s Played%s%d Caught".formatted(
			runs.size(), runs.size() == 1 ? "" : "s", STAT_SEPARATOR,
			formatHours(played), STAT_SEPARATOR, catches);
		if (showValue() && BazaarPrices.known()) {
			summary += STAT_SEPARATOR + "%s Coins".formatted(BazaarPrices.format(BazaarPrices.totalValue(runs)));
		}
		int unknown = sparklingRunsOnly ? unknownSparklings() : 0;
		if (unknown > 0) {
			summary += STAT_SEPARATOR + "%d Unknown".formatted(unknown);
		}
		cachedHistorySummarySource = runs;
		cachedHistorySummaryPriceRevision = priceRevision;
		cachedHistorySummary = summary;
		return summary;
	}

	/** Lifetime Sparkling catches that predate or otherwise fall outside saved runs. */
	private static int unknownSparklings() {
		int recorded = 0;
		for (RunRecord run : RunHistory.runs()) recorded += sparklingCount(run);
		return Math.max(0, SparklingStats.total() - recorded);
	}

	private String statsSummary() {
		int runs = RunHistory.size();
		if (runs == 0) return "Nothing saved yet.";
		return "%d Runs%s%s Played%s%d Catches%s%.1f Catches Per Run".formatted(
			runs, STAT_SEPARATOR, formatHours(RunHistory.totalTimeMillis()), STAT_SEPARATOR,
			RunHistory.totalCatches(), STAT_SEPARATOR,
			(double) RunHistory.totalCatches() / runs);
	}

	private String valueTotalsText() {
		List<RunRecord> runs = RunHistory.runs();
		int priced = RunHistory.pricedRuns();
		if (!BazaarPrices.known() || priced == 0) return "";
		long total = BazaarPrices.totalValue(runs);
		return "%s Coins%s%s Per Run%s%d Shards%s%d Essence"
			.formatted(BazaarPrices.format(total), STAT_SEPARATOR,
				BazaarPrices.format(total / priced), STAT_SEPARATOR, RunHistory.totalShards(),
				STAT_SEPARATOR, RunHistory.totalSafariEssence());
	}

	private void centered(GuiGraphicsExtractor graphics, Font font, String text, int y, int colour) {
		text(graphics, font, Component.literal(text),
			panelLeft + (panelWidth - font.width(text)) / 2, y, colour);
	}

	private static void centeredCell(GuiGraphicsExtractor graphics, Font font, String text,
								 int x, int width, int y, int colour) {
		text(graphics, font, Component.literal(text), x + (width - font.width(text)) / 2, y, colour);
	}

	private static void text(GuiGraphicsExtractor graphics, Font font, Component component,
						 int x, int y, int colour) {
		SpecialTheme.text(graphics, font, component, x, y, colour);
	}

	/** Date and time of day, which is how you recognise a run you remember. */
	private static String formatWhen(long millis) {
		return WHEN.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
	}

	private static int yearOf(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).getYear();
	}

	/** Hours and minutes, for spans far longer than one run. */
	private static String formatHours(long millis) {
		long minutes = millis / 60_000;
		return minutes < 60 ? minutes + "M" : "%dH %02dM".formatted(minutes / 60, minutes % 60);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x() / responsiveScale;
		double mouseY = event.y() / responsiveScale;
		for (Control control : controls) {
			if (!control.contains(mouseX, mouseY)) continue;
			if (control.style == ControlStyle.TAB && control.selected) return true;
			control.action.run();
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		mouseX /= responsiveScale;
		mouseY /= responsiveScale;
		if (tab != Tab.HISTORY || mouseX < panelLeft || mouseX >= panelLeft + panelWidth
			|| mouseY < panelTop || mouseY >= panelTop + panelHeight) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		int maxScroll = Math.max(0,
			historyDisplayRows(filteredHistory()).size() - HISTORY_ROWS);
		int direction = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
		if (direction == 0) return false;
		historyScroll = Math.clamp(historyScroll + direction, 0, maxScroll);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
