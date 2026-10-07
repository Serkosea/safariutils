package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.client.SafariConfig.SparklingConfig;
import dev.serko.safariutils.client.SafariConfig.SparklingConfig.TicketTraderProfile;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Responsive profile-and-slot editor for trusted Ticket Trading players. */
public final class TicketTradingScreen extends Screen {
	private static final List<List<Critter>> CRITTER_GROUPS = Critters.selectionBiomes().stream()
		.map(biome -> Critters.selectionOrder().stream()
			.filter(critter -> critter.biome() == biome).toList())
		.toList();
	private static final List<String> INFO_LINES = List.of(
		"Ticket trading must be enabled for either role",
		"Host: Must use a ticket before 20 seconds after joining a Safari run",
		"- Normal profiles are invited automatically",
		"- Trusted ✦ profiles are invited immediately for selected detected critters",
		"- Offline active players are replaced by backup players after 3 seconds",
		"- Any players who joined will be automatically warped",
		"- Party disbands after the Host leaves the Safari run",
		"Guest: Automatically accepts party invites from trusted active profiles",
		"- Trusted ✦ profiles leave the current party before accepting");
	private enum View { MAIN, INFO, EDITOR }
	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) { return inside(mx, my, x, y, w, h); }
	}
	private record DragHit(int x, int y, int w, int h, TicketTraderProfile profile, int slot) {
		boolean contains(double mx, double my) { return inside(mx, my, x, y, w, h); }
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final List<DragHit> dragHits = new ArrayList<>();
	private final int[] slotX = new int[TicketTradingProfiles.TOTAL_SLOTS];
	private final int[] slotY = new int[TicketTradingProfiles.TOTAL_SLOTS];
	private final int[] slotW = new int[TicketTradingProfiles.TOTAL_SLOTS];
	private final int[] slotH = new int[TicketTradingProfiles.TOTAL_SLOTS];
	private View view = View.MAIN;
	private EditBox editorName;
	private TicketTraderProfile editing;
	private String previousName = "";
	private boolean editorSparkling;
	private long editorMask = Critters.allSelectionMask();
	private String editorError = "";
	private TicketTraderProfile dragged;
	private int draggedFromSlot = -1;
	private int savedScroll;
	private int editorScroll;
	private int editorMaxScroll;
	private boolean savingProfile;
	private List<TicketTraderProfile> allProfiles = List.of();
	private List<TicketTraderProfile> savedProfiles = List.of();
	private int cachedInfoWidth = -1;
	private List<String> cachedInfoLines = List.of();
	private int left, top, panelWidth, panelHeight;
	private int layoutWidth, layoutHeight;
	private float responsiveScale = 1f;
	private int savedPanelX, savedPanelY, savedPanelW, savedPanelH;
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

	@Override
	protected void init() {
		applyTheme();
		clearWidgets();
		updateLayoutBounds();
		TicketTradingProfiles.sanitize(config());
		refreshProfileLists();
		refreshCanonicalProfiles();
		editorName = null;
		if (view == View.EDITOR) createNameEditor();
	}

	private void updateLayoutBounds() {
		int preferredWidth;
		int preferredHeight;
		if (view == View.INFO) {
			int longestLine = INFO_LINES.stream().mapToInt(font::width).max().orElse(220);
			preferredWidth = Math.max(260, Math.min(720,
				Math.max(longestLine + 52, font.width("Ticket Trading") + 28)));
			preferredHeight = 100 + INFO_LINES.size() * 16;
		} else {
			boolean compactLayout = width < 736 || height < 464;
			preferredWidth = compactLayout ? 600 : 720;
			preferredHeight = compactLayout ? 344 : view == View.MAIN ? 448 : 366;
		}
		responsiveScale = ResponsiveUI.fitScale(width, height,
			preferredWidth, preferredHeight, 8);
		layoutWidth = ResponsiveUI.logicalWidth(width, responsiveScale);
		layoutHeight = ResponsiveUI.logicalHeight(height, responsiveScale);
		panelWidth = Math.min(preferredWidth, layoutWidth - 16);
		if (view == View.INFO) {
			preferredHeight = 100 + wrappedInfoLines(panelWidth - 52).size() * 16;
		}
		panelHeight = Math.min(preferredHeight, layoutHeight - 16);
		left = (layoutWidth - panelWidth) / 2;
		top = (layoutHeight - panelHeight) / 2;
	}

	private void createNameEditor() {
		int frameW = Math.min(240, panelWidth - 48);
		int frameX = left + (panelWidth - frameW) / 2;
		int frameY = top + 34;
		editorName = new EditBox(font, frameX + 8, frameY + 8, frameW - 16, 10,
			Component.literal("Minecraft username"));
		editorName.setMaxLength(16);
		editorName.setBordered(false);
		editorName.setValue(editing == null ? "" : editing.username);
		editorName.setHint(Component.literal("Minecraft username"));
		editorName.setTextColor(text);
		editorName.setTextColorUneditable(label);
		UIDraw.rainbowEditBox(editorName, font, () -> SpecialTheme.rainbow() || editorSparkling);
		addRenderableWidget(editorName);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		applyTheme();
		if (parent != null) parent.extractRenderState(graphics, Integer.MIN_VALUE,
			Integer.MIN_VALUE, partialTick);
		else graphics.fill(0, 0, width, height, background);
		graphics.fill(0, 0, width, height, 0x96000000);
		int logicalMouseX = Math.round(mouseX / responsiveScale);
		int logicalMouseY = Math.round(mouseY / responsiveScale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(responsiveScale, responsiveScale);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, surface);
		SpecialTheme.stars(graphics, left + 2, top + 2, panelWidth - 4, panelHeight - 4, 0.7f);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, left, top, panelWidth, panelHeight, 1);
		else UIDraw.outline(graphics, left, top, panelWidth, panelHeight, border);
		hits.clear();
		dragHits.clear();
		centered(graphics, view == View.EDITOR ? "Trusted Player" : "Ticket Trading", top + 15, text);
		if (view == View.MAIN) drawMain(graphics, logicalMouseX, logicalMouseY);
		else if (view == View.INFO) drawInfo(graphics, logicalMouseX, logicalMouseY);
		else drawEditor(graphics, logicalMouseX, logicalMouseY);
		if (dragged != null && view == View.MAIN) {
			drawDragged(graphics, logicalMouseX, logicalMouseY);
		}
		graphics.pose().popMatrix();
	}

	private void drawMain(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		SparklingConfig config = config();
		centered(graphics, "Drag saved players into active or backup slots", top + 32, label);
		button(graphics, left + 18, top + 50, 104, 22,
			config.ticketTradingEnabled ? "Enabled" : "Disabled", mouseX, mouseY, () -> {
				config.ticketTradingEnabled = !config.ticketTradingEnabled;
				ConfigManager.save();
			});
		if (BuildVersion.DEVELOPER) {
			button(graphics, left + 132, top + 50, 54, 22, "Test", mouseX, mouseY,
				() -> dev.serko.safariutils.api.SharedSparklingProviders
					.openTicketTradingTest(this));
		}
		button(graphics, left + panelWidth - 104, top + 50, 40, 22, "New", mouseX, mouseY,
			() -> openEditor(null));
		button(graphics, left + panelWidth - 54, top + 50, 36, 22, "i", mouseX, mouseY,
			() -> switchView(View.INFO));

		int contentTop = top + 84;
		int contentBottom = top + panelHeight - 46;
		int gap = 16;
		int sectionWidth = (panelWidth - 36 - gap) / 2;
		int slotGap = 10;
		int slotPanelHeight = (contentBottom - contentTop - slotGap) / 2;
		drawSlots(graphics, left + 18, contentTop, sectionWidth, slotPanelHeight,
			"Active Slots", 0, mouseX, mouseY);
		drawSlots(graphics, left + 18, contentTop + slotPanelHeight + slotGap,
			sectionWidth, slotPanelHeight, "Backup Slots", 3, mouseX, mouseY);
		drawSaved(graphics, left + 18 + sectionWidth + gap, contentTop,
			sectionWidth, contentBottom - contentTop, mouseX, mouseY);
		button(graphics, left + panelWidth / 2 - 50, top + panelHeight - 34,
			100, 22, "Done", mouseX, mouseY, this::onClose);
	}

	private void drawSlots(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			String title, int slotOffset, int mouseX, int mouseY) {
		section(graphics, x, y, w, h, title);
		int rowTop = y + 25;
		int rowGap = 4;
		int rowHeight = Math.min(34, Math.max(18, (h - 29 - rowGap * 2) / 3));
		for (int row = 0; row < 3; row++) {
			int index = slotOffset + row;
			int slot = index;
			int sy = rowTop + row * (rowHeight + rowGap);
			slotX[index] = x + 8; slotY[index] = sy; slotW[index] = w - 16; slotH[index] = rowHeight;
			TicketTraderProfile profile = TicketTradingProfiles.slot(config(), index);
			int buttonX = slotX[index] + slotW[index] - 24;
			int buttonY = sy + (rowHeight - 18) / 2;
			boolean overButton = profile != null
				&& inside(mouseX, mouseY, buttonX, buttonY, 18, 18);
			boolean over = inside(mouseX, mouseY, slotX[index], sy, slotW[index], rowHeight)
				&& !overButton;
			int fill = profileRowBackground(profile, index, over);
			graphics.fill(slotX[index], sy, slotX[index] + slotW[index], sy + rowHeight, fill);
			if (profile != null && profile.sparklingOnly || SpecialTheme.rainbow()) {
				SpecialTheme.border(graphics, slotX[index], sy, slotW[index], rowHeight, 1);
			} else UIDraw.outline(graphics, slotX[index], sy, slotW[index], rowHeight, border);
			String slotLabel = "Slot " + (row + 1);
			int slotTextY = sy + Math.max(5, (rowHeight - 8) / 2);
			if (profile != null && profile.sparklingOnly) {
				drawSparklingText(graphics, slotLabel, slotX[index] + 8, slotTextY);
			} else SpecialTheme.text(graphics, font, Component.literal(slotLabel),
				slotX[index] + 8, slotTextY, label);
			if (profile == null) {
				String empty = "Drop player here";
				SpecialTheme.text(graphics, font, Component.literal(empty),
					slotX[index] + slotW[index] - font.width(empty) - 8,
					sy + Math.max(5, (rowHeight - 8) / 2), label);
			} else {
				int nameX = slotX[index] + 58;
				int nameY = sy + (rowHeight >= 32 ? 6 : Math.max(2, (rowHeight - 8) / 2));
				drawProfileName(graphics, profile, nameX, nameY);
				if (rowHeight >= 32) {
					String detail = profile.sparklingOnly
						? Long.bitCount(profile.sparklingCritters) + "/37 Sparklings" : "All runs";
					if (profile.sparklingOnly) drawSparklingText(graphics, detail, nameX, sy + 20);
					else SpecialTheme.text(graphics, font, Component.literal(detail), nameX, sy + 20, label);
				} else {
					String detail = profile.sparklingOnly
						? Long.bitCount(profile.sparklingCritters) + "/37" : "All";
					int detailX = buttonX - font.width(detail) - 6;
					if (profile.sparklingOnly) drawSparklingText(graphics, detail, detailX, nameY);
					else SpecialTheme.text(graphics, font, Component.literal(detail),
						detailX, nameY, label);
				}
				profileButton(graphics, buttonX, buttonY, 18, 18, "×", profile, index,
					mouseX, mouseY, () -> clearSlot(slot));
				dragHits.add(new DragHit(slotX[index], sy, slotW[index] - 30, rowHeight, profile, index));
			}
		}
	}

	private void drawSaved(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			int mouseX, int mouseY) {
		savedPanelX = x;
		savedPanelY = y;
		savedPanelW = w;
		savedPanelH = h;
		section(graphics, x, y, w, h, "Saved Players");
		if (draggedFromSlot >= 0 && inside(mouseX, mouseY, x, y, w, h)) {
			if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, w, h, 1);
			else UIDraw.outline(graphics, x, y, w, h, lighterHover(border));
		}
		int rowHeight = 38;
		int viewportTop = y + 25;
		int visible = Math.max(1, (h - 32) / rowHeight);
		int maxScroll = Math.max(0, savedProfiles.size() - visible);
		savedScroll = Math.clamp(savedScroll, 0, maxScroll);
		for (int row = 0; row < visible && row + savedScroll < savedProfiles.size(); row++) {
			TicketTraderProfile profile = savedProfiles.get(row + savedScroll);
			int ry = viewportTop + row * rowHeight;
			int buttonX = x + w - 48;
			int buttonY = ry + 8;
			boolean overButton = inside(mouseX, mouseY, buttonX, buttonY, 32, 18);
			boolean over = inside(mouseX, mouseY, x + 8, ry, w - 16, 34) && !overButton;
			int fill = profileRowBackground(profile, row, over);
			graphics.fill(x + 8, ry, x + w - 8, ry + 34, fill);
			if (profile.sparklingOnly || SpecialTheme.rainbow()) {
				SpecialTheme.border(graphics, x + 8, ry, w - 16, 34, 1);
			} else UIDraw.outline(graphics, x + 8, ry, w - 16, 34, border);
			drawProfileName(graphics, profile, x + 16, ry + 6);
			String detail = profile.sparklingOnly
				? Long.bitCount(profile.sparklingCritters) + "/37" : "All runs";
			if (profile.sparklingOnly) drawSparklingText(graphics, detail, x + 16, ry + 20);
			else SpecialTheme.text(graphics, font, Component.literal(detail), x + 16, ry + 20, label);
			profileButton(graphics, buttonX, buttonY, 32, 18, "Edit", profile, row,
				mouseX, mouseY, () -> openEditor(profile));
			dragHits.add(new DragHit(x + 8, ry, w - 64, 34, profile, -1));
		}
		if (savedProfiles.isEmpty()) {
			String empty = allProfiles.isEmpty() ? "Create a profile, then drag it into a slot"
				: "All saved players are assigned";
			centeredWithin(graphics, empty,
				x, y + h / 2 - 4, w, label);
		}
	}

	private void drawEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int frameW = Math.min(240, panelWidth - 48);
		int frameX = left + (panelWidth - frameW) / 2;
		int frameY = top + 34;
		graphics.fill(frameX, frameY, frameX + frameW, frameY + 26, card);
		if (SpecialTheme.rainbow() || editorSparkling) SpecialTheme.border(graphics, frameX, frameY, frameW, 26, 1);
		else UIDraw.outline(graphics, frameX, frameY, frameW, 26, border);
		UIDraw.updateRainbowCaret(editorName, text, SpecialTheme.rainbow() || editorSparkling);
		button(graphics, left + panelWidth / 2 - 94, top + 68, 188, 22,
			editorSparkling ? "Sparkling Only" : "All Runs", mouseX, mouseY, () -> {
				editorSparkling = !editorSparkling;
				editorError = "";
			});

		int gridTop = top + 100;
		int bottom = top + panelHeight - 45;
		if (editorSparkling) {
			SpecialTheme.text(graphics, font, Component.literal("Invite for these Sparklings"),
				left + 154, gridTop + 5, label);
			button(graphics, left + 22, gridTop, 66, 18, "Select All", mouseX, mouseY,
				() -> editorMask = Critters.allSelectionMask());
			button(graphics, left + 96, gridTop, 48, 18, "Clear", mouseX, mouseY,
				() -> editorMask = 0L);
			drawCritterGrid(graphics, gridTop + 25, bottom, mouseX, mouseY);
		} else centered(graphics, "This player is invited to every ticket-trading run.",
			gridTop + 24, label);
		if (!editorError.isEmpty()) centered(graphics, editorError, bottom + 2, red);
		int buttonY = top + panelHeight - 34;
		button(graphics, left + 20, buttonY, 70, 22, "Cancel", mouseX, mouseY,
			() -> switchView(View.MAIN));
		if (editing != null) button(graphics, left + 98, buttonY, 62, 22, "Delete", mouseX, mouseY,
			this::deleteEditing);
		button(graphics, left + panelWidth - 90, buttonY, 70, 22, "Save", mouseX, mouseY,
			this::saveEditing);
		super.extractRenderState(graphics, mouseX, mouseY, 0f);
	}

	private void drawCritterGrid(GuiGraphicsExtractor graphics, int gridTop, int bottom,
			int mouseX, int mouseY) {
		int columns = panelWidth >= 650 ? 4 : 2;
		int gap = 8, gridX = left + 20, gridW = panelWidth - 40;
		int colW = (gridW - gap * (columns - 1)) / columns;
		if (columns == 4) {
			editorMaxScroll = 0;
			editorScroll = 0;
			for (int col = 0; col < 4; col++) {
				int x = gridX + col * (colW + gap);
				SafariBiome biome = Critters.selectionBiomes().get(col);
				centeredWithin(graphics, biome.displayName(), x, gridTop, colW,
					0xFF000000 | biome.colour());
				for (int row = 0; row < CRITTER_GROUPS.get(col).size(); row++) {
					drawCritterChoice(graphics, CRITTER_GROUPS.get(col).get(row), x,
						gridTop + 15 + row * 18 - editorScroll, colW, mouseX, mouseY, gridTop, bottom);
				}
			}
		} else {
			int[] cursor = {gridTop, gridTop};
			for (int group = 0; group < CRITTER_GROUPS.size(); group++) {
				int col = group % 2;
				int x = gridX + col * (colW + gap);
				int headingY = cursor[col] - editorScroll;
				SafariBiome biome = Critters.selectionBiomes().get(group);
				if (headingY >= gridTop && headingY + 12 <= bottom) {
					centeredWithin(graphics, biome.displayName(), x, headingY, colW,
						0xFF000000 | biome.colour());
				}
				cursor[col] += 14;
				for (Critter critter : CRITTER_GROUPS.get(group)) {
					drawCritterChoice(graphics, critter, x, cursor[col] - editorScroll,
						colW, mouseX, mouseY, gridTop, bottom);
					cursor[col] += 18;
				}
				cursor[col] += 8;
			}
			editorMaxScroll = Math.max(0, Math.max(cursor[0], cursor[1]) - bottom);
			editorScroll = Math.clamp(editorScroll, 0, editorMaxScroll);
		}
	}

	private void drawCritterChoice(GuiGraphicsExtractor graphics, Critter critter, int x, int y,
			int w, int mouseX, int mouseY, int clipTop, int clipBottom) {
		if (y < clipTop || y + 16 > clipBottom) return;
		long bit = Critters.selectionMask(critter);
		boolean enabled = (editorMask & bit) != 0L;
		graphics.fill(x, y, x + w, y + 16, inside(mouseX, mouseY, x, y, w, 16) ? hover : card);
		UIDraw.outline(graphics, x, y, w, 16, enabled ? green : border);
		SpecialTheme.text(graphics, font, Component.literal(enabled ? "✓" : "×"), x + 5, y + 4,
			enabled ? green : red);
		SpecialTheme.text(graphics, font, Component.literal(critter.name()), x + 17, y + 4,
			0xFF000000 | critter.rarity().colour());
		hits.add(new Hit(x, y, w, 16, () -> editorMask ^= bit));
	}

	private void drawInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int boxX = left + 14, boxY = top + 34, boxW = panelWidth - 28;
		List<String> lines = wrappedInfoLines(boxW - 24);
		int boxH = 24 + lines.size() * 16;
		graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, boxX, boxY, boxW, boxH, 1);
		else UIDraw.outline(graphics, boxX, boxY, boxW, boxH, border);
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			int lineX = boxX + 12, lineY = boxY + 12 + index * 16;
			String role = line.startsWith("Host:") ? "Host"
				: line.startsWith("Guest:") ? "Guest" : null;
			if (role != null) {
				SpecialTheme.text(graphics, font, Component.literal(role), lineX, lineY, primary);
				drawInfoText(graphics, line.substring(role.length()),
					lineX + font.width(role), lineY);
			} else drawInfoText(graphics, line, lineX, lineY);
		}
		button(graphics, left + panelWidth / 2 - 50, boxY + boxH + 10,
			100, 22, "Back", mouseX, mouseY, () -> switchView(View.MAIN));
	}

	private void drawInfoText(GuiGraphicsExtractor graphics, String value, int x, int y) {
		int icon = value.indexOf('✦');
		if (icon < 0) {
			SpecialTheme.text(graphics, font, Component.literal(value), x, y, label);
			return;
		}
		String before = value.substring(0, icon);
		String after = value.substring(icon + 1);
		SpecialTheme.text(graphics, font, Component.literal(before), x, y, label);
		int iconX = x + font.width(before);
		drawSparklingText(graphics, "✦", iconX, y);
		SpecialTheme.text(graphics, font, Component.literal(after),
			iconX + font.width("✦"), y, label);
	}

	private List<String> wrappedInfoLines(int width) {
		if (cachedInfoWidth == width) return cachedInfoLines;
		List<String> wrapped = new ArrayList<>();
		for (String source : INFO_LINES) {
			StringBuilder line = new StringBuilder();
			for (String word : source.split("\\s+")) {
				String next = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && font.width(next) > width) {
					wrapped.add(line.toString());
					line.setLength(0);
				}
				if (!line.isEmpty()) line.append(' ');
				line.append(word);
			}
			if (!line.isEmpty()) wrapped.add(line.toString());
		}
		cachedInfoWidth = width;
		cachedInfoLines = List.copyOf(wrapped);
		return cachedInfoLines;
	}

	private void drawDragged(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int w = font.width(profileName(dragged)) + 16, h = 24;
		int x = Math.clamp(mouseX + 8, 2, layoutWidth - w - 2);
		int y = Math.clamp(mouseY + 8, 2, layoutHeight - h - 2);
		graphics.fill(x, y, x + w, y + h, dragged.sparklingOnly ? sparklingRowBackground(1) : card);
		SpecialTheme.border(graphics, x, y, w, h, 1);
		drawProfileName(graphics, dragged, x + 8, y + 8);
	}

	private void section(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title) {
		graphics.fill(x, y, x + w, y + h, 0x66000000);
		UIDraw.outline(graphics, x, y, w, h, border);
		SpecialTheme.text(graphics, font, Component.literal(title), x + 8, y + 8, secondary);
	}

	private void drawProfileName(GuiGraphicsExtractor graphics, TicketTraderProfile profile,
			int x, int y) {
		if (!profile.sparklingOnly) {
			PlayerNameStyle.drawName(graphics, font, profile.username, x, y);
			return;
		}
		if (SpecialTheme.rainbow()) {
			SpecialTheme.rainbowText(graphics, font, profileName(profile), x, y);
		} else UIDraw.rainbowText(graphics, font, Component.literal(profileName(profile)),
			x, y, 0.5f, 0xFF, true);
	}

	private void drawSparklingText(GuiGraphicsExtractor graphics, String value, int x, int y) {
		if (SpecialTheme.rainbow()) SpecialTheme.rainbowText(graphics, font, value, x, y);
		else UIDraw.rainbowText(graphics, font, Component.literal(value),
			x, y, 0.5f, 0xFF, true);
	}

	private static String profileName(TicketTraderProfile profile) {
		return profile.sparklingOnly ? "✦ " + profile.username + " ✦" : profile.username;
	}

	private void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			String value, int mouseX, int mouseY, Runnable action) {
		boolean over = inside(mouseX, mouseY, x, y, w, h);
		graphics.fill(x, y, x + w, y + h, over ? hover : card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, w, h, 1);
		else UIDraw.outline(graphics, x, y, w, h, border);
		int colour = value.equals("Enabled") ? green : value.equals("Disabled") ? red : text;
		centeredWithin(graphics, value, x, y + (h - 8) / 2, w, colour);
		hits.add(new Hit(x, y, w, h, action));
	}

	private void profileButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			String value, TicketTraderProfile profile, int phase, int mouseX, int mouseY,
			Runnable action) {
		boolean over = inside(mouseX, mouseY, x, y, w, h);
		int fill = profile.sparklingOnly ? sparklingRowBackground(phase, over)
			: over ? lighterHover(card) : card;
		graphics.fill(x, y, x + w, y + h, fill);
		if (profile.sparklingOnly || SpecialTheme.rainbow()) {
			SpecialTheme.border(graphics, x, y, w, h, 1);
		} else UIDraw.outline(graphics, x, y, w, h, border);
		int textX = x + (w - font.width(value)) / 2;
		int textY = y + (h - 8) / 2;
		if (profile.sparklingOnly) drawSparklingText(graphics, value, textX, textY);
		else SpecialTheme.text(graphics, font, Component.literal(value), textX, textY, text);
		hits.add(new Hit(x, y, w, h, action));
	}

	private void openEditor(TicketTraderProfile profile) {
		editing = profile;
		previousName = profile == null ? "" : profile.username;
		editorSparkling = profile != null && profile.sparklingOnly;
		editorMask = profile == null ? Critters.allSelectionMask() : profile.sparklingCritters;
		editorError = "";
		editorScroll = 0;
		switchView(View.EDITOR);
	}

	private void saveEditing() {
		if (savingProfile) return;
		String requested = editorName.getValue().trim();
		if (!requested.matches("[A-Za-z0-9_]{1,16}")) {
			editorError = "Enter a valid Minecraft username";
			return;
		}
		TicketTraderProfile target = editing == null ? new TicketTraderProfile() : editing;
		savingProfile = true;
		editorError = "Checking Minecraft username…";
		CanonicalPlayerNames.resolve(requested).whenComplete((canonical, error) ->
			Minecraft.getInstance().execute(() -> {
				savingProfile = false;
				if (ClientCompat.screen() != this || view != View.EDITOR || editorName == null) return;
				if (!editorName.getValue().trim().equals(requested)) {
					editorError = "Name changed; save again";
					return;
				}
				if (error != null) {
					editorError = "Could not verify Minecraft username";
					return;
				}
				if (!TicketTradingProfiles.save(config(), target, previousName,
						canonical, editorSparkling, editorMask)) {
					editorError = "That player is already saved";
					return;
				}
				PlayerNameStyle.refreshColour(canonical);
				ConfigManager.save();
				switchView(View.MAIN);
			}));
	}

	private void refreshCanonicalProfiles() {
		for (TicketTraderProfile profile : List.copyOf(allProfiles)) {
			CanonicalPlayerNames.resolve(profile.username).thenAccept(canonical ->
				Minecraft.getInstance().execute(() -> {
					PlayerNameStyle.refreshColour(canonical);
					if (!TicketTradingProfiles.applyCanonicalName(config(), profile, canonical)) return;
					refreshProfileLists();
					ConfigManager.save();
				})).exceptionally(error -> null);
		}
	}

	private void refreshProfileLists() {
		allProfiles = TicketTradingProfiles.sorted(config());
		Set<String> assigned = new HashSet<>();
		for (int index = 0; index < TicketTradingProfiles.TOTAL_SLOTS; index++) {
			String name = TicketTradingProfiles.slotName(config(), index);
			if (!name.isBlank()) assigned.add(TicketTradingProfiles.normalize(name));
		}
		savedProfiles = allProfiles.stream()
			.filter(profile -> !assigned.contains(TicketTradingProfiles.normalize(profile.username)))
			.toList();
	}

	private void deleteEditing() {
		TicketTradingProfiles.remove(config(), editing);
		ConfigManager.save();
		switchView(View.MAIN);
	}

	private void clearSlot(int index) {
		TicketTradingProfiles.assign(config(), index, null);
		refreshProfileLists();
		ConfigManager.save();
	}

	private void switchView(View next) {
		view = next;
		savingProfile = false;
		setFocused(null);
		rebuildWidgets();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		MouseButtonEvent logicalEvent = logicalEvent(event);
		if (view == View.EDITOR && editorName != null) {
			int frameW = Math.min(240, panelWidth - 48);
			int frameX = left + (panelWidth - frameW) / 2;
			int frameY = top + 34;
			if (inside(logicalEvent.x(), logicalEvent.y(), frameX, frameY, frameW, 26)) {
				editorName.setFocused(true);
				setFocused(editorName);
				editorName.mouseClicked(new MouseButtonEvent(
					Math.clamp(logicalEvent.x(), frameX + 8, frameX + frameW - 9),
					Math.clamp(logicalEvent.y(), frameY + 8, frameY + 17),
					logicalEvent.buttonInfo()), doubled);
				return true;
			}
		}
		setFocused(null);
		if (editorName != null) editorName.setFocused(false);
		for (int index = hits.size() - 1; index >= 0; index--) {
			Hit hit = hits.get(index);
			if (hit.contains(logicalEvent.x(), logicalEvent.y())) {
				hit.action().run();
				return true;
			}
		}
		if (view == View.MAIN && logicalEvent.button() == 0) {
			for (int index = dragHits.size() - 1; index >= 0; index--) {
				DragHit hit = dragHits.get(index);
				if (hit.contains(logicalEvent.x(), logicalEvent.y())) {
					dragged = hit.profile();
					draggedFromSlot = hit.slot();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		MouseButtonEvent logicalEvent = logicalEvent(event);
		if (dragged == null || view != View.MAIN) return super.mouseReleased(logicalEvent);
		boolean changed = false;
		for (int index = 0; index < TicketTradingProfiles.TOTAL_SLOTS; index++) {
			if (!inside(logicalEvent.x(), logicalEvent.y(),
				slotX[index], slotY[index], slotW[index], slotH[index])) continue;
			TicketTraderProfile displaced = TicketTradingProfiles.slot(config(), index);
			TicketTradingProfiles.assign(config(), index, dragged);
			if (draggedFromSlot >= 0 && draggedFromSlot != index) {
				TicketTradingProfiles.assign(config(), draggedFromSlot, displaced);
			}
			changed = true;
			break;
		}
		if (!changed && draggedFromSlot >= 0
				&& inside(logicalEvent.x(), logicalEvent.y(),
					savedPanelX, savedPanelY, savedPanelW, savedPanelH)) {
			TicketTradingProfiles.assign(config(), draggedFromSlot, null);
			changed = true;
		}
		if (changed) {
			refreshProfileLists();
			ConfigManager.save();
		}
		dragged = null;
		draggedFromSlot = -1;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (view == View.MAIN) {
			savedScroll = Math.max(0, savedScroll + (scrollY < 0 ? 1 : -1));
			return true;
		}
		if (view == View.EDITOR && editorSparkling) {
			editorScroll = Math.clamp(editorScroll + (scrollY < 0 ? 18 : -18),
				0, editorMaxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX / responsiveScale, mouseY / responsiveScale,
			scrollX, scrollY);
	}

	private MouseButtonEvent logicalEvent(MouseButtonEvent event) {
		return new MouseButtonEvent(event.x() / responsiveScale,
			event.y() / responsiveScale, event.buttonInfo());
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 256) {
			if (view != View.MAIN) switchView(View.MAIN);
			else onClose();
			return true;
		}
		if ((event.key() == 257 || event.key() == 335) && editorName != null
				&& editorName.isFocused()) {
			editorName.setFocused(false);
			setFocused(null);
			return true;
		}
		if (editorName != null && editorName.isFocused()) return editorName.keyPressed(event);
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (editorName != null && editorName.isFocused()) return editorName.charTyped(event);
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		if (parent != null) ClientCompat.setScreen(parent);
		else super.onClose();
	}

	@Override public boolean isPauseScreen() { return false; }

	private SparklingConfig config() { return ConfigManager.get().sparkling; }

	private int sparklingRowBackground(int index) {
		return sparklingRowBackground(index, false);
	}

	private int sparklingRowBackground(int index, boolean hovered) {
		int accent = RainbowColours.shared(index * 0.17f, 0.42f);
		float amount = 0.20f, inverse = 1f - amount;
		int r = Math.round((card >> 16 & 0xFF) * inverse + (accent >> 16 & 0xFF) * amount);
		int g = Math.round((card >> 8 & 0xFF) * inverse + (accent >> 8 & 0xFF) * amount);
		int b = Math.round((card & 0xFF) * inverse + (accent & 0xFF) * amount);
		int base = 0xFF000000 | r << 16 | g << 8 | b;
		if (!hovered) return base;
		return lighterHover(base);
	}

	private int profileRowBackground(TicketTraderProfile profile, int phase, boolean hovered) {
		if (profile != null && profile.sparklingOnly) {
			return sparklingRowBackground(phase, hovered);
		}
		return hovered ? lighterHover(card) : card;
	}

	private static int lighterHover(int colour) {
		return blendOpaque(colour, 0xFFFFFFFF, 0.14f);
	}

	private static int blendOpaque(int first, int second, float amount) {
		float inverse = 1f - amount;
		int r = Math.round((first >> 16 & 0xFF) * inverse + (second >> 16 & 0xFF) * amount);
		int g = Math.round((first >> 8 & 0xFF) * inverse + (second >> 8 & 0xFF) * amount);
		int b = Math.round((first & 0xFF) * inverse + (second & 0xFF) * amount);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void centered(GuiGraphicsExtractor graphics, String value, int y, int colour) {
		centeredWithin(graphics, value, left, y, panelWidth, colour);
	}

	private void centeredWithin(GuiGraphicsExtractor graphics, String value,
			int x, int y, int w, int colour) {
		SpecialTheme.text(graphics, font, Component.literal(value),
			x + (w - font.width(value)) / 2, y, colour);
	}

	private void applyTheme() {
		int[] palette = SafariSettingsScreen.activeThemePalette();
		background = palette[0]; surface = palette[1]; card = palette[2]; hover = palette[3];
		border = palette[4]; primary = palette[5]; secondary = palette[6]; green = palette[7];
		red = palette[8]; text = palette[10]; label = palette[11];
		if (SpecialTheme.rainbow()) {
			primary = SpecialTheme.accent(0); secondary = SpecialTheme.accent(18);
			green = SpecialTheme.accent(36); red = SpecialTheme.accent(54);
			border = SpecialTheme.accent(72);
		}
	}
}
