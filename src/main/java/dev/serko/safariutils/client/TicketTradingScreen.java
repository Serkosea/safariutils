package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.client.SafariConfig.SparklingConfig;
import dev.serko.safariutils.client.SafariConfig.SparklingConfig.TicketTraderProfile;
import dev.serko.safariutils.client.TicketTradingProfiles.GuestMode;
import dev.serko.safariutils.client.TicketTradingProfiles.HostMode;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Responsive, role-oriented editor for Ticket Trading players. */
public final class TicketTradingScreen extends Screen {
	private static final int ROW_HEIGHT = 36;
	private static final int ROW_STEP = 40;
	private static final int ROW_TOP = 29;
	private static final int COLUMN_GAP = 8;
	private static final int DIVIDER_WIDTH = 2;
	private static final long DELETE_CONFIRM_MS = 3_000L;
	private static final List<List<Critter>> CRITTER_GROUPS = Critters.selectionBiomes().stream()
		.map(biome -> Critters.selectionOrder().stream()
			.filter(critter -> critter.biome() == biome).toList()).toList();
	private static final List<String> INFO = List.of(
		"Host",
		"Uses your ticket and invites players from the Host queue in order.",
		"- Every Run: Invites this player during the normal invite window",
		"- Sparklings Only: Invites this player when a selected Sparkling is detected",
		"- Queue Order: Attempts eligible players from top to bottom while space and time remain",
		"",
		"Guest",
		"Accepts party invites from trusted players in the Guest list.",
		"- Off: Keeps this player trusted without accepting their invites",
		"- Join: Accepts this player's invite only when you are not already in a party",
		"- Leave Party & Join: Leaves your current party before accepting this player's invite");
	private enum View { MAIN, INFO, NEW_PLAYER, HOST_EDITOR }
	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) { return inside(mx, my, x, y, w, h); }
	}
	private record DragHit(TicketTraderProfile profile, int kind, int index,
		int x, int y, int w, int h) {
		boolean contains(double mx, double my) { return inside(mx, my, x, y, w, h); }
	}
	private record Columns(int hostX, int hostW, int guestX, int guestW,
		int dividerX, int savedX, int savedW) { }

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final List<DragHit> dragHits = new ArrayList<>();
	private View view = View.MAIN;
	private EditBox editorName;
	private TicketTraderProfile editing;
	private String previousName = "", editorError = "";
	private HostMode editorHost = HostMode.EVERY_RUN;
	private long editorMask = Critters.allSelectionMask();
	private TicketTraderProfile dragging;
	private TicketTraderProfile pendingDelete;
	private long deleteConfirmUntil;
	private int dragOriginKind = -1, dragOriginIndex = -1;
	private boolean dragMoved;
	private int hostScroll, hostMaxScroll, guestScroll, guestMaxScroll;
	private int savedScroll, savedMaxScroll, editorScroll, editorMaxScroll;
	private boolean saving;
	private long editorRevision;
	private List<TicketTraderProfile> profiles = List.of(), queue = List.of(), guests = List.of();
	private int cachedInfoWidth = -1;
	private List<String> cachedInfo = List.of();
	private int left, top, panelWidth, panelHeight, logicalWidth, logicalHeight;
	private float scale = 1f;
	private int background, surface, card, hover, border, primary, secondary, text, label, green, red;

	private TicketTradingScreen(Screen parent) {
		super(Component.literal("Ticket Trading"));
		this.parent = parent;
	}

	public static void open() {
		Minecraft client = Minecraft.getInstance();
		Screen parent = ClientCompat.screen();
		client.execute(() -> ClientCompat.setScreen(new TicketTradingScreen(parent)));
	}

	@Override protected void init() {
		boolean editorFocused = editorName != null && editorName.isFocused();
		applyTheme();
		clearWidgets();
		layout();
		TicketTradingProfiles.sanitize(config());
		refresh();
		refreshCanonical();
		if (view == View.NEW_PLAYER) createEditor();
		if (view == View.NEW_PLAYER && editorFocused) setFocused(editorName);
	}

	private void layout() {
		int preferredW = view == View.NEW_PLAYER ? 420
			: view == View.MAIN ? 750 : 720;
		int preferredH = view == View.INFO ? 280
			: view == View.NEW_PLAYER ? 140 : view == View.MAIN ? 426 : 330;
		scale = ResponsiveUI.fitScale(width, height, preferredW, preferredH, 8);
		logicalWidth = ResponsiveUI.logicalWidth(width, scale);
		logicalHeight = ResponsiveUI.logicalHeight(height, scale);
		panelWidth = Math.min(preferredW, logicalWidth - 16);
		if (view == View.INFO) preferredH = 112 + wrapInfo(panelWidth - 52).size() * 16;
		panelHeight = Math.min(preferredH, logicalHeight - 16);
		left = (logicalWidth - panelWidth) / 2;
		top = (logicalHeight - panelHeight) / 2;
	}

	private void createEditor() {
		int w = Math.min(260, panelWidth - 48), x = left + (panelWidth - w) / 2, y = top + 48;
		if (editorName != null) {
			editorName.setX(x + 8);
			editorName.setY(y + 8);
			editorName.setWidth(w - 16);
			addRenderableWidget(editorName);
			return;
		}
		editorName = new EditBox(font, x + 8, y + 8, w - 16, 10,
			Component.literal("Minecraft username"));
		editorName.setMaxLength(16);
		editorName.setBordered(false);
		editorName.setValue("");
		SettingsUiKit.showStart(editorName);
		editorName.setHint(Component.literal("Minecraft username"));
		editorName.setTextColor(text);
		editorName.setTextColorUneditable(label);
		UIDraw.rainbowEditBox(editorName, font, SpecialTheme::rainbow);
		addRenderableWidget(editorName);
	}

	@Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
			float partialTick) {
		applyTheme();
		if (parent != null) parent.extractRenderState(g, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
		else g.fill(0, 0, width, height, background);
		g.fill(0, 0, width, height, 0x96000000);
		int mx = Math.round(mouseX / scale), my = Math.round(mouseY / scale);
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		g.fill(left, top, left + panelWidth, top + panelHeight, surface);
		SpecialTheme.stars(g, left + 2, top + 2, panelWidth - 4, panelHeight - 4, 0.7f);
		if (SpecialTheme.rainbow()) SpecialTheme.border(g, left, top, panelWidth, panelHeight, 1);
		else UIDraw.outline(g, left, top, panelWidth, panelHeight, border);
		hits.clear();
		String title = switch (view) {
			case NEW_PLAYER -> "Setup Trusted Player";
			case HOST_EDITOR -> "Host Sparkling Invites";
			default -> "Ticket Trading";
		};
		center(g, title, top + 14, text);
		dragHits.clear();
		switch (view) {
			case MAIN -> main(g, mx, my);
			case INFO -> info(g, mx, my);
			case NEW_PLAYER -> newPlayer(g, mx, my);
			case HOST_EDITOR -> hostEditor(g, mx, my);
		}
		if (view == View.MAIN && dragging != null && dragMoved) dragPreview(g, mx, my);
		g.pose().popMatrix();
	}

	private void toolbar(GuiGraphicsExtractor g, int mx, int my) {
		int y = top + 34, x = left + 18;
		button(g, x, y, 84, 22, config().ticketTradingEnabled ? "Enabled" : "Disabled", mx, my, () -> {
			config().ticketTradingEnabled = !config().ticketTradingEnabled;
			ConfigManager.save();
		});
		int infoW = 30, gap = 6;
		int testW = BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS) ? 44 : 0;
		x = left + panelWidth - 18 - infoW;
		button(g, x, y, infoW, 22, "i", mx, my, () -> switchView(View.INFO));
		if (testW > 0) {
			x -= testW + gap;
			button(g, x, y, testW, 22, "Test", mx, my,
				() -> dev.serko.safariutils.api.SharedSparklingProviders.openTicketTradingTest(this));
		}
	}

	private void main(GuiGraphicsExtractor g, int mx, int my) {
		toolbar(g, mx, my);
		int y = top + 64;
		int h = panelHeight - 108;
		Columns columns = columns();
		roleColumn(g, columns.hostX(), y, columns.hostW(), h,
			"Invite Players", queue, 0, mx, my);
		roleColumn(g, columns.guestX(), y, columns.guestW(), h,
			"Receive Invites", guests, 1, mx, my);
		roleColumn(g, columns.savedX(), y, columns.savedW(), h,
			"Saved Players", profiles, 2, mx, my);
		g.fill(columns.dividerX(), y, columns.dividerX() + DIVIDER_WIDTH, y + h, secondary);
		if (dragging != null && dragMoved) dropTarget(g, mx, my, y, h, columns);
		done(g, mx, my);
	}

	private Columns columns() {
		int hostX = left + 18;
		int available = panelWidth - 36 - COLUMN_GAP * 3 - DIVIDER_WIDTH;
		int hostW = available / 3;
		int guestX = hostX + hostW + COLUMN_GAP;
		int guestW = available / 3;
		int dividerX = guestX + guestW + COLUMN_GAP;
		int savedX = dividerX + DIVIDER_WIDTH + COLUMN_GAP;
		int savedW = left + panelWidth - 18 - savedX;
		return new Columns(hostX, hostW, guestX, guestW, dividerX, savedX, savedW);
	}

	private void roleColumn(GuiGraphicsExtractor g, int x, int y, int w, int h,
			String title, List<TicketTraderProfile> entries, int kind, int mx, int my) {
		section(g, x, y, w, h, title);
		if (kind == 1) button(g, x + w - 64, y + 5, 56, 18, "Swap All", mx, my,
			this::swapAllGuests);
		if (kind == 2) button(g, x + w - 48, y + 5, 40, 18, "New", mx, my,
			this::openNewPlayer);
		int visible = Math.max(1, (h - 36) / ROW_STEP);
		int scroll = kind == 0 ? hostScroll : kind == 1 ? guestScroll : savedScroll;
		int max = Math.max(0, entries.size() - visible);
		scroll = Math.clamp(scroll, 0, max);
		if (kind == 0) { hostScroll = scroll; hostMaxScroll = max; }
		else if (kind == 1) { guestScroll = scroll; guestMaxScroll = max; }
		else { savedScroll = scroll; savedMaxScroll = max; }
		for (int row = 0; row < visible && row + scroll < entries.size(); row++) {
			int index = row + scroll;
			int ry = y + ROW_TOP + row * ROW_STEP;
			if (kind == 0) hostRow(g, entries.get(index), index, x + 6, ry, w - 12, mx, my);
			else if (kind == 1) guestRow(g, entries.get(index), x + 6, ry, w - 12, index, mx, my);
			else playerRow(g, entries.get(index), x + 6, ry, w - 12, index, mx, my);
		}
		if (max > 0) scrollBar(g, x + w - 4, y + ROW_TOP, h - ROW_TOP - 6,
			scroll, max, visible, entries.size());
		if (entries.isEmpty()) {
			String empty = kind == 0 ? "No Invited Players" : kind == 1
				? "No Trusted Invites" : "No Saved Players";
			centerWithin(g, empty, x, y + h / 2 - 4, w, label);
		}
	}

	private void hostRow(GuiGraphicsExtractor g, TicketTraderProfile p, int index,
			int x, int y, int w, int mx, int my) {
		boolean sparkle = TicketTradingProfiles.hostMode(p) == HostMode.SPARKLING;
		boolean overCard = inside(mx, my, x, y, w - 66, ROW_HEIGHT);
		card(g, p, x, y, w, ROW_HEIGHT, index, overCard, true);
		passiveBox(g, x + 7, y + 9, 20, Integer.toString(index + 1), sparkle, index);
		name(g, p, x + 34, y + 6, sparkle);
		if (sparkle) rainbow(g, hostSummary(p), x + 34, y + 21);
		else draw(g, hostSummary(p), x + 34, y + 21, label);
		pButton(g, x + w - 62, y + 9, 34, "Edit", p, index, mx, my,
			() -> openHostEditor(p), true);
		pButton(g, x + w - 24, y + 9, 18, "×", p, index, mx, my,
			() -> removeQueue(p), true);
		dragHits.add(new DragHit(p, 0, index, x, y, w, ROW_HEIGHT));
	}

	private void guestRow(GuiGraphicsExtractor g, TicketTraderProfile p,
			int x, int y, int w, int phase, int mx, int my) {
		GuestMode mode = TicketTradingProfiles.guestMode(p);
		int dragW = w - 75;
		card(g, p, x, y, w, ROW_HEIGHT, phase,
			inside(mx, my, x, y, dragW, ROW_HEIGHT), false);
		name(g, p, x + 8, y + 6, false);
		draw(g, switch (mode) {
			case OFF -> "Off";
			case JOIN -> "Join";
			case SWITCH -> "Leave Party & Join";
		},
			x + 8, y + 21, label);
		pButton(g, x + w - 72, y + 9, 42, "Swap", p, phase, mx, my,
			() -> swapGuest(p), false);
		pButton(g, x + w - 24, y + 9, 18, "×", p, phase, mx, my,
			() -> removeGuest(p), false);
		dragHits.add(new DragHit(p, 1, phase, x, y, dragW, ROW_HEIGHT));
	}

	private void playerRow(GuiGraphicsExtractor g, TicketTraderProfile p,
			int x, int y, int w, int phase, int mx, int my) {
		boolean confirm = confirmingDelete(p);
		String action = confirm ? "Confirm" : "Delete";
		int buttonW = font.width(action) + 12;
		int buttonX = x + w - 6 - buttonW;
		int dragW = buttonX - x - 3;
		boolean overCard = inside(mx, my, x, y, dragW, ROW_HEIGHT);
		card(g, p, x, y, w, ROW_HEIGHT, phase, overCard, false);
		name(g, p, x + 8, y + 6, false);
		draw(g, "Drag to Setup", x + 8, y + 21, label);
		pButton(g, buttonX, y + 9, buttonW, action, p, phase, mx, my,
			() -> requestDelete(p), false);
		dragHits.add(new DragHit(p, 2, phase, x, y, dragW, ROW_HEIGHT));
	}

	private void scrollBar(GuiGraphicsExtractor g, int x, int y, int h,
			int scroll, int max, int visible, int total) {
		g.fill(x, y, x + 2, y + h, border);
		int thumbH = Math.max(12, Math.round(h * (visible / (float) total)));
		int thumbY = y + Math.round((h - thumbH) * (scroll / (float) max));
		g.fill(x, thumbY, x + 2, thumbY + thumbH, primary);
	}

	private void newPlayer(GuiGraphicsExtractor g, int mx, int my) {
		int fw = Math.min(260, panelWidth - 48), fx = left + (panelWidth - fw) / 2, fy = top + 48;
		g.fill(fx, fy, fx + fw, fy + 26, card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(g, fx, fy, fw, 26, 1);
		else UIDraw.outline(g, fx, fy, fw, 26, border);
		UIDraw.updateRainbowCaret(editorName, text, SpecialTheme.rainbow());
		if (!editorError.isEmpty()) center(g, editorError, top + 84, red);
		int by = top + panelHeight - 34;
		button(g, left + 20, by, 70, 22, "Cancel", mx, my, () -> switchView(View.MAIN));
		button(g, left + panelWidth - 90, by, 70, 22, "Save", mx, my, this::saveNewPlayer);
		super.extractRenderState(g, mx, my, 0f);
	}

	private void hostEditor(GuiGraphicsExtractor g, int mx, int my) {
		int gridTop = top + 48, bottom = top + panelHeight - 45;
		button(g, left + 26, gridTop, 126, 18,
			editorHost == HostMode.EVERY_RUN ? "Every Run" : "Sparklings Only",
			mx, my, () -> editorHost = editorHost.next());
		if (editorHost == HostMode.SPARKLING) {
			draw(g, "Invite when these Sparklings are found", left + 170, gridTop + 5, label);
			button(g, left + panelWidth - 148, gridTop, 66, 18, "Select All", mx, my,
				() -> editorMask = Critters.allSelectionMask());
			button(g, left + panelWidth - 74, gridTop, 48, 18, "Clear", mx, my, () -> editorMask = 0L);
			critterGrid(g, gridTop + 25, bottom, mx, my);
		} else center(g, "This player is invited to every run you host.", gridTop + 44, label);
		int by = top + panelHeight - 34;
		button(g, left + 20, by, 70, 22, "Cancel", mx, my, () -> switchView(View.MAIN));
		button(g, left + panelWidth - 90, by, 70, 22, "Save", mx, my, this::saveHostEditing);
	}

	private void critterGrid(GuiGraphicsExtractor g, int gridTop, int bottom, int mx, int my) {
		int columns = panelWidth >= 650 ? 4 : 2, gap = 8, gridX = left + 20, gridW = panelWidth - 40;
		int colW = (gridW - gap * (columns - 1)) / columns;
		if (columns == 4) {
			editorMaxScroll = editorScroll = 0;
			for (int col = 0; col < 4; col++) {
				int x = gridX + col * (colW + gap);
				SafariBiome biome = Critters.selectionBiomes().get(col);
				centerWithin(g, biome.displayName(), x, gridTop, colW, 0xFF000000 | biome.colour());
				for (int row = 0; row < CRITTER_GROUPS.get(col).size(); row++)
					critterChoice(g, CRITTER_GROUPS.get(col).get(row), x, gridTop + 15 + row * 18,
						colW, mx, my, gridTop, bottom);
			}
		} else {
			int[] cursor = {gridTop, gridTop};
			for (int group = 0; group < CRITTER_GROUPS.size(); group++) {
				int col = group % 2, x = gridX + col * (colW + gap), headingY = cursor[col] - editorScroll;
				SafariBiome biome = Critters.selectionBiomes().get(group);
				if (headingY >= gridTop && headingY + 12 <= bottom)
					centerWithin(g, biome.displayName(), x, headingY, colW, 0xFF000000 | biome.colour());
				cursor[col] += 14;
				for (Critter critter : CRITTER_GROUPS.get(group)) {
					critterChoice(g, critter, x, cursor[col] - editorScroll, colW, mx, my, gridTop, bottom);
					cursor[col] += 18;
				}
				cursor[col] += 8;
			}
			editorMaxScroll = Math.max(0, Math.max(cursor[0], cursor[1]) - bottom);
			editorScroll = Math.clamp(editorScroll, 0, editorMaxScroll);
		}
	}

	private void critterChoice(GuiGraphicsExtractor g, Critter critter, int x, int y,
			int w, int mx, int my, int clipTop, int clipBottom) {
		if (y < clipTop || y + 16 > clipBottom) return;
		long bit = Critters.selectionMask(critter);
		boolean enabled = (editorMask & bit) != 0L;
		g.fill(x, y, x + w, y + 16, inside(mx, my, x, y, w, 16) ? hover : card);
		UIDraw.outline(g, x, y, w, 16, enabled ? green : border);
		Component mark = Component.literal(enabled ? "✓" : "×").withStyle(style -> style.withBold(true));
		SpecialTheme.text(g, font, mark, x + 5, y + 4, enabled ? green : red);
		draw(g, critter.name(), x + 17, y + 4, 0xFF000000 | critter.rarity().colour());
		hits.add(new Hit(x, y, w, 16, () -> editorMask ^= bit));
	}

	private void info(GuiGraphicsExtractor g, int mx, int my) {
		int x = left + 14, y = top + 36, w = panelWidth - 28;
		List<String> lines = wrapInfo(w - 24);
		int h = 24 + lines.size() * 16;
		g.fill(x, y, x + w, y + h, card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(g, x, y, w, h, 1);
		else UIDraw.outline(g, x, y, w, h, border);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int lineY = y + 12 + i * 16;
			if (line.equals("Host") || line.equals("Guest")) {
				draw(g, line, x + 12, lineY, primary);
				continue;
			}
			int separator = line.indexOf(": ");
			if (line.startsWith("- ") && separator > 2) {
				String setting = line.substring(0, separator);
				draw(g, setting, x + 12, lineY, secondary);
				draw(g, line.substring(separator), x + 12 + font.width(setting), lineY, label);
			} else draw(g, line, x + 12, lineY, label);
		}
		button(g, left + panelWidth / 2 - 50, y + h + 10, 100, 22, "Back", mx, my,
			() -> switchView(View.MAIN));
	}

	private List<String> wrapInfo(int width) {
		if (cachedInfoWidth == width) return cachedInfo;
		List<String> result = new ArrayList<>();
		for (String source : INFO) {
			if (source.isEmpty()) {
				result.add("");
				continue;
			}
			StringBuilder line = new StringBuilder();
			for (String word : source.split("\\s+")) {
				String next = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && font.width(next) > width) {
					result.add(line.toString()); line.setLength(0);
				}
				if (!line.isEmpty()) line.append(' ');
				line.append(word);
			}
			if (!line.isEmpty()) result.add(line.toString());
		}
		cachedInfoWidth = width;
		return cachedInfo = List.copyOf(result);
	}

	private void section(GuiGraphicsExtractor g, int x, int y, int w, int h, String title) {
		g.fill(x, y, x + w, y + h, 0x66000000);
		UIDraw.outline(g, x, y, w, h, border);
		draw(g, title, x + 8, y + 8, secondary);
	}

	private void card(GuiGraphicsExtractor g, TicketTraderProfile p, int x, int y,
			int w, int h, int phase, boolean over, boolean allowSparkle) {
		boolean sparkle = allowSparkle && TicketTradingProfiles.hostMode(p) == HostMode.SPARKLING;
		int fill = sparkle ? sparklingBackground(phase, over) : over ? lighter(card) : card;
		g.fill(x, y, x + w, y + h, fill);
		if (sparkle || SpecialTheme.rainbow()) SpecialTheme.border(g, x, y, w, h, 1);
		else UIDraw.outline(g, x, y, w, h, border);
	}

	private void name(GuiGraphicsExtractor g, TicketTraderProfile p, int x, int y, boolean sparkle) {
		if (!sparkle) PlayerNameStyle.drawName(g, font, p.username, x, y);
		else rainbow(g, "✦ " + p.username + " ✦", x, y);
	}

	private String hostSummary(TicketTraderProfile p) {
		if (TicketTradingProfiles.hostMode(p) == HostMode.EVERY_RUN) return "Every Run";
		long selected = Long.bitCount(p.sparklingCritters & Critters.allSelectionMask());
		long total = Long.bitCount(Critters.allSelectionMask());
		return selected == total ? "All Sparklings"
			: selected + "/" + total + " Sparklings";
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h,
			String value, int mx, int my, Runnable action) {
		boolean over = inside(mx, my, x, y, w, h);
		g.fill(x, y, x + w, y + h, over ? hover : card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(g, x, y, w, h, 1);
		else UIDraw.outline(g, x, y, w, h, border);
		int colour = value.equals("Enabled") ? green : value.equals("Disabled") ? red : text;
		centerWithin(g, value, x, y + (h - 8) / 2, w, colour);
		hits.add(new Hit(x, y, w, h, action));
	}

	private void pButton(GuiGraphicsExtractor g, int x, int y, int w, String value,
			TicketTraderProfile p, int phase, int mx, int my, Runnable action, boolean allowSparkle) {
		boolean over = inside(mx, my, x, y, w, 18);
		boolean sparkle = allowSparkle && TicketTradingProfiles.hostMode(p) == HostMode.SPARKLING;
		g.fill(x, y, x + w, y + 18, sparkle ? sparklingBackground(phase, over) : over ? lighter(card) : card);
		if (sparkle || SpecialTheme.rainbow()) SpecialTheme.border(g, x, y, w, 18, 1);
		else UIDraw.outline(g, x, y, w, 18, border);
		centerWithin(g, value, x, y + 5, w, text);
		hits.add(new Hit(x, y, w, 18, action));
	}

	private void passiveBox(GuiGraphicsExtractor g, int x, int y, int w,
			String value, boolean sparkle, int phase) {
		g.fill(x, y, x + w, y + 18, sparkle ? sparklingBackground(phase, false) : card);
		if (sparkle || SpecialTheme.rainbow()) SpecialTheme.border(g, x, y, w, 18, 1);
		else UIDraw.outline(g, x, y, w, 18, border);
		centerWithin(g, value, x, y + 5, w, text);
	}

	private void dropTarget(GuiGraphicsExtractor g, int mx, int my,
			int y, int h, Columns columns) {
		if ((dragOriginKind == 0 || dragOriginKind == 1 || dragOriginKind == 2)
				&& inside(mx, my, columns.hostX(), y, columns.hostW(), h)) {
			UIDraw.outline(g, columns.hostX(), y, columns.hostW(), h, primary);
		} else if ((dragOriginKind == 0 || dragOriginKind == 2)
				&& inside(mx, my, columns.guestX(), y, columns.guestW(), h)) {
			UIDraw.outline(g, columns.guestX(), y, columns.guestW(), h, primary);
		} else if ((dragOriginKind == 0 || dragOriginKind == 1)
				&& inside(mx, my, columns.savedX(), y, columns.savedW(), h)) {
			UIDraw.outline(g, columns.savedX(), y, columns.savedW(), h, primary);
		}
	}

	private void dragPreview(GuiGraphicsExtractor g, int mx, int my) {
		boolean hostStyle = dragOriginKind == 0;
		boolean sparkle = hostStyle
			&& TicketTradingProfiles.hostMode(dragging) == HostMode.SPARKLING;
		String previewName = sparkle ? "✦ " + dragging.username + " ✦"
			: CanonicalPlayerNames.display(dragging.username);
		String action = switch (dragOriginKind) {
			case 0 -> "Reorder, add to Receive Invites, or remove role";
			case 1 -> "Add to Invite Players or remove role";
			default -> "Drop into Invite Players or Receive Invites";
		};
		int padding = 8;
		int w = Math.min(panelWidth - 8,
			Math.max(font.width(previewName), font.width(action)) + padding * 2);
		int h = 34, x = Math.clamp(mx - w / 2, left + 4, left + panelWidth - w - 4);
		int y = Math.clamp(my - h / 2, top + 4, top + panelHeight - h - 4);
		card(g, dragging, x, y, w, h, dragOriginIndex, true, hostStyle);
		name(g, dragging, x + padding, y + 5, sparkle);
		draw(g, action, x + padding, y + 19, label);
	}

	private int hostDropIndex(double mouseY) {
		int row = Math.max(0, (int) Math.floor((mouseY - (top + 64 + ROW_TOP)) / ROW_STEP));
		return Math.clamp(hostScroll + row, 0, queue.size());
	}

	private void finishDrag(double mouseX, double mouseY) {
		int y = top + 64, h = panelHeight - 108;
		Columns columns = columns();
		boolean changed = false;
		if ((dragOriginKind == 0 || dragOriginKind == 1 || dragOriginKind == 2)
				&& inside(mouseX, mouseY, columns.hostX(), y, columns.hostW(), h)) {
			int target = hostDropIndex(mouseY);
			TicketTradingProfiles.queue(config(), dragging, target);
			changed = true;
		} else if ((dragOriginKind == 0 || dragOriginKind == 2)
				&& inside(mouseX, mouseY, columns.guestX(), y, columns.guestW(), h)) {
			TicketTradingProfiles.enableGuest(config(), dragging);
			changed = true;
		} else if ((dragOriginKind == 0 || dragOriginKind == 1)
				&& inside(mouseX, mouseY, columns.savedX(), y, columns.savedW(), h)) {
			if (dragOriginKind == 0) TicketTradingProfiles.removeFromQueue(config(), dragging);
			else TicketTradingProfiles.disableGuest(config(), dragging);
			changed = true;
		}
		if (changed) {
			refresh();
			ConfigManager.save();
		}
	}

	private void done(GuiGraphicsExtractor g, int mx, int my) {
		button(g, left + panelWidth / 2 - 50, top + panelHeight - 34,
			100, 22, "Done", mx, my, this::onClose);
	}

	private void openNewPlayer() {
		editing = null;
		previousName = "";
		editorError = "";
		switchView(View.NEW_PLAYER);
	}

	private void openHostEditor(TicketTraderProfile profile) {
		editing = profile;
		editorHost = TicketTradingProfiles.hostMode(profile);
		editorMask = profile.sparklingCritters;
		editorScroll = 0;
		switchView(View.HOST_EDITOR);
	}

	private void saveNewPlayer() {
		if (saving) return;
		String requested = editorName.getValue().trim();
		if (!requested.matches("[A-Za-z0-9_]{1,16}")) {
			editorError = "Enter a valid Minecraft username"; return;
		}
		TicketTraderProfile target = new TicketTraderProfile();
		long revision = ++editorRevision;
		SparklingConfig owner = config();
		saving = true; editorError = "Checking Minecraft username…";
		CanonicalPlayerNames.resolve(requested).whenComplete((canonical, error) ->
			Minecraft.getInstance().execute(() -> {
				if (revision != editorRevision) return;
				saving = false;
				if (ClientCompat.screen() != this || view != View.NEW_PLAYER || editorName == null
						|| config() != owner) return;
				if (!editorName.getValue().trim().equals(requested)) {
					editorError = "Name changed; save again"; return;
				}
				if (error != null) { editorError = "Could not verify Minecraft username"; return; }
				if (!TicketTradingProfiles.save(config(), target, previousName, canonical,
						HostMode.EVERY_RUN, GuestMode.OFF, Critters.allSelectionMask())) {
					editorError = "That player is already saved"; return;
				}
				PlayerNameStyle.refreshColour(canonical);
				ConfigManager.save(); switchView(View.MAIN);
			}));
	}

	private void saveHostEditing() {
		if (editing == null) { switchView(View.MAIN); return; }
		TicketTradingProfiles.setHostMode(editing, editorHost);
		editing.sparklingCritters = editorMask & Critters.allSelectionMask();
		ConfigManager.save();
		switchView(View.MAIN);
	}

	private void refreshCanonical() {
		for (TicketTraderProfile p : List.copyOf(profiles)) {
			String requested = p.username;
			SparklingConfig owner = config();
			CanonicalPlayerNames.resolve(requested).thenAccept(canonical ->
				Minecraft.getInstance().execute(() -> {
					if (config() != owner || !p.username.equals(requested)) return;
					PlayerNameStyle.refreshColour(canonical);
					if (!TicketTradingProfiles.applyCanonicalName(config(), p, canonical)) return;
					refresh(); ConfigManager.save();
				})).exceptionally(error -> null);
		}
	}

	private void refresh() {
		profiles = TicketTradingProfiles.sorted(config());
		queue = TicketTradingProfiles.hostQueue(config());
		guests = TicketTradingProfiles.guestPlayers(config());
	}

	private void removeQueue(TicketTraderProfile p) {
		TicketTradingProfiles.removeFromQueue(config(), p); refresh(); ConfigManager.save();
	}

	private void swapGuest(TicketTraderProfile p) {
		TicketTradingProfiles.setGuestMode(p, TicketTradingProfiles.guestMode(p).next());
		refresh(); ConfigManager.save();
	}

	private void swapAllGuests() {
		if (guests.isEmpty()) return;
		GuestMode first = TicketTradingProfiles.guestMode(guests.getFirst());
		boolean same = guests.stream().allMatch(profile ->
			TicketTradingProfiles.guestMode(profile) == first);
		GuestMode target = same ? first.next() : GuestMode.OFF;
		for (TicketTraderProfile profile : guests) {
			TicketTradingProfiles.setGuestMode(profile, target);
		}
		refresh();
		ConfigManager.save();
	}

	private void removeGuest(TicketTraderProfile p) {
		TicketTradingProfiles.disableGuest(config(), p);
		refresh(); ConfigManager.save();
	}

	private boolean confirmingDelete(TicketTraderProfile profile) {
		if (pendingDelete == null || System.currentTimeMillis() > deleteConfirmUntil) {
			pendingDelete = null;
			return false;
		}
		return pendingDelete == profile;
	}

	private void requestDelete(TicketTraderProfile profile) {
		if (!confirmingDelete(profile)) {
			pendingDelete = profile;
			deleteConfirmUntil = System.currentTimeMillis() + DELETE_CONFIRM_MS;
			return;
		}
		TicketTradingProfiles.remove(config(), profile);
		pendingDelete = null;
		refresh(); ConfigManager.save();
	}

	private void switchView(View next) {
		editorRevision++;
		editorName = null;
		view = next; saving = false; setFocused(null); rebuildWidgets();
	}

	@Override public void removed() {
		editorRevision++;
		saving = false;
		super.removed();
	}

	@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		MouseButtonEvent logical = logicalEvent(event);
		if (view == View.NEW_PLAYER && editorName != null) {
			int w = Math.min(260, panelWidth - 48), x = left + (panelWidth - w) / 2, y = top + 48;
			if (inside(logical.x(), logical.y(), x, y, w, 26)) {
				setFocused(editorName); SettingsUiKit.focusAt(editorName, logical, doubled); return true;
			}
		}
		setFocused(null);
		if (editorName != null) SettingsUiKit.blurToStart(editorName);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(logical.x(), logical.y())) { hit.action().run(); return true; }
		}
		if (view == View.MAIN) {
			for (int i = dragHits.size() - 1; i >= 0; i--) {
				DragHit hit = dragHits.get(i);
				if (!hit.contains(logical.x(), logical.y())) continue;
				dragging = hit.profile();
				dragOriginKind = hit.kind();
				dragOriginIndex = hit.index();
				dragMoved = false;
				return true;
			}
		}
		return false;
	}

	@Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null) {
			dragMoved = true;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging == null) return super.mouseReleased(event);
		MouseButtonEvent logical = logicalEvent(event);
		if (dragMoved) finishDrag(logical.x(), logical.y());
		dragging = null;
		dragOriginKind = dragOriginIndex = -1;
		dragMoved = false;
		return true;
	}

	@Override public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
		if (view == View.MAIN) {
			double logicalX = mouseX / scale, logicalY = mouseY / scale;
			Columns columns = columns();
			if (!inside(logicalX, logicalY, left + 18, top + 64,
					panelWidth - 36, panelHeight - 108)) return false;
			int step = sy < 0 ? 1 : -1;
			if (inside(logicalX, logicalY, columns.hostX(), top + 64,
					columns.hostW(), panelHeight - 108))
				hostScroll = Math.clamp(hostScroll + step, 0, hostMaxScroll);
			else if (inside(logicalX, logicalY, columns.guestX(), top + 64,
					columns.guestW(), panelHeight - 108))
				guestScroll = Math.clamp(guestScroll + step, 0, guestMaxScroll);
			else savedScroll = Math.clamp(savedScroll + step, 0, savedMaxScroll);
			return true;
		}
		if (view == View.HOST_EDITOR && editorHost == HostMode.SPARKLING) {
			editorScroll = Math.clamp(editorScroll + (sy < 0 ? 18 : -18), 0, editorMaxScroll); return true;
		}
		return super.mouseScrolled(mouseX / scale, mouseY / scale, sx, sy);
	}

	private MouseButtonEvent logicalEvent(MouseButtonEvent event) {
		return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.buttonInfo());
	}

	@Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 258) return true;
		if (event.key() == 256) {
			if (view != View.MAIN) switchView(View.MAIN);
			else onClose();
			return true;
		}
		if ((event.key() == 257 || event.key() == 335) && editorName != null && editorName.isFocused()) {
			SettingsUiKit.blurToStart(editorName); setFocused(null); return true;
		}
		if (editorName != null && editorName.isFocused()) return editorName.keyPressed(event);
		return super.keyPressed(event);
	}

	@Override public boolean charTyped(CharacterEvent event) {
		return editorName != null && editorName.isFocused()
			? editorName.charTyped(event) : super.charTyped(event);
	}

	@Override public void onClose() {
		dragging = null;
		ConfigManager.save();
		if (parent != null) ClientCompat.setScreen(parent); else super.onClose();
	}
	@Override public boolean isPauseScreen() { return false; }

	private SparklingConfig config() { return ConfigManager.get().sparkling; }

	private int sparklingBackground(int index, boolean over) {
		int accent = RainbowColours.shared(index * 0.17f, 0.42f);
		float a = 0.20f, inv = 1f - a;
		int r = Math.round((card >> 16 & 0xFF) * inv + (accent >> 16 & 0xFF) * a);
		int gr = Math.round((card >> 8 & 0xFF) * inv + (accent >> 8 & 0xFF) * a);
		int b = Math.round((card & 0xFF) * inv + (accent & 0xFF) * a);
		int base = 0xFF000000 | r << 16 | gr << 8 | b;
		return over ? lighter(base) : base;
	}

	private static int lighter(int colour) {
		int r = Math.round((colour >> 16 & 0xFF) * .86f + 255 * .14f);
		int g = Math.round((colour >> 8 & 0xFF) * .86f + 255 * .14f);
		int b = Math.round((colour & 0xFF) * .86f + 255 * .14f);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private void draw(GuiGraphicsExtractor g, String value, int x, int y, int colour) {
		SpecialTheme.text(g, font, Component.literal(value), x, y, colour);
	}
	private void rainbow(GuiGraphicsExtractor g, String value, int x, int y) {
		if (SpecialTheme.rainbow()) SpecialTheme.rainbowText(g, font, value, x, y);
		else UIDraw.rainbowText(g, font, Component.literal(value), x, y, .5f, 0xFF, true);
	}
	private void center(GuiGraphicsExtractor g, String value, int y, int colour) {
		centerWithin(g, value, left, y, panelWidth, colour);
	}
	private void centerWithin(GuiGraphicsExtractor g, String value, int x, int y, int w, int colour) {
		draw(g, value, x + (w - font.width(value)) / 2, y, colour);
	}
	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void applyTheme() {
		int[] p = SafariSettingsScreen.activeThemePalette();
		background = p[0]; surface = p[1]; card = p[2]; hover = p[3]; border = p[4];
		primary = p[5]; secondary = p[6]; green = p[7]; red = p[8]; text = p[10]; label = p[11];
		if (SpecialTheme.rainbow()) {
			primary = SpecialTheme.accent(0); secondary = SpecialTheme.accent(18);
			green = SpecialTheme.accent(36); red = SpecialTheme.accent(54); border = SpecialTheme.accent(72);
		}
	}
}
