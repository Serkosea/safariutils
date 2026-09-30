package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Compact editor for the explicitly trusted players used by ticket trading. */
public final class TicketTradingScreen extends Screen {
	private int background;
	private int surface;
	private int card;
	private int hover;
	private int border;
	private int primary;
	private int secondary;
	private int text;
	private int label;
	private int green;
	private int red;

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private final Screen parent;
	private final java.util.ArrayList<Hit> hits = new java.util.ArrayList<>();
	private final EditBox[] names = new EditBox[3];
	private final int[] nameHintPhases = {-1, -1, -1};
	private static final String NAME_HINT = "Optional Minecraft username";
	private static final Component NAME_HINT_COMPONENT = Component.literal(NAME_HINT);
	private static final int ROW_MARGIN = 40;
	private static final int LABEL_FIELD_GAP = 14;
	private static final int SPARKLING_BUTTON_SIZE = 24;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private boolean showInfo;
	private boolean stackedRows;
	private int cachedInfoWidth = -1;
	private List<InfoLine> cachedInfoLines = List.of();

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
		panelWidth = ResponsiveUI.panelWidth(width, 520, 300);
		stackedRows = panelWidth < 410;
		int preferredHeight = stackedRows ? 318 : 294;
		panelHeight = Math.min(preferredHeight, Math.max(238,
			height - ResponsiveUI.gutter(width) * 2));
		if (stackedRows && panelHeight < 285) stackedRows = false;
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		String[] values = {config.ticketTradingPlayer1, config.ticketTradingPlayer2,
			config.ticketTradingPlayer3};
		for (int i = 0; i < names.length; i++) {
			int index = i;
			int frameLeft = nameFrameLeft();
			// Match Safari Settings' ordinary inline text editor exactly: the native
			// The field is borderless and optically centered inside the 24px frame.
			EditBox field = new EditBox(font, frameLeft + 8, nameFrameTop(i) + 8,
				nameFrameWidth() - 16, 10, Component.literal("Username " + (i + 1)));
			field.setMaxLength(16);
			field.setBordered(false);
			field.setValue(values[i] == null ? "" : values[i]);
			field.setHint(NAME_HINT_COMPONENT);
			field.setResponder(value -> setName(config, index, value));
			field.setTextColor(text);
			field.setTextColorUneditable(label);
			UIDraw.rainbowEditBox(field, font,
				() -> SpecialTheme.rainbow() || sparklingEnabled(config, index));
			field.visible = !showInfo;
			names[i] = field;
			addRenderableWidget(field);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		applyTheme();
		if (parent != null) parent.extractRenderState(graphics, Integer.MIN_VALUE,
			Integer.MIN_VALUE, partialTick);
		else graphics.fill(0, 0, width, height, background);
		graphics.fill(0, 0, width, height, 0x96000000);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, surface);
		SpecialTheme.stars(graphics, left + 2, top + 2, panelWidth - 4, panelHeight - 4, 0.7f);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, left, top, panelWidth, panelHeight, 1);
		else UIDraw.outline(graphics, left, top, panelWidth, panelHeight, border);
		hits.clear();

		centered(graphics, "Ticket Trading", top + 17, text);
		centered(graphics, "Timed invites for trusted players sharing Safari tickets", top + 35, label);

		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (!showInfo) {
			button(graphics, left + 22, top + 57, 112, 22,
				config.ticketTradingEnabled ? "Enabled" : "Disabled", mouseX, mouseY, () -> {
					config.ticketTradingEnabled = !config.ticketTradingEnabled;
					ConfigManager.save();
				});
			button(graphics, left + panelWidth - 48, top + 57, 26, 22, "i", mouseX, mouseY,
				this::toggleInfo);
			for (int i = 0; i < names.length; i++) {
				int frameLeft = nameFrameLeft();
				boolean sparkling = sparklingEnabled(config, i);
				int rowBackground = sparkling ? sparklingRowBackground(i) : card;
				String playerLabel = "Player " + (i + 1);
				int labelX = stackedRows ? left + 20 : left + ROW_MARGIN;
				int labelY = stackedRows ? nameFrameTop(i) - 11 : nameFrameTop(i) + 8;
				if (sparkling) UIDraw.rainbowText(graphics, font, playerLabel,
					labelX, labelY, 0.5f);
				else SpecialTheme.text(graphics, font, Component.literal(playerLabel),
					labelX, labelY, label);
				graphics.fill(frameLeft, nameFrameTop(i),
					frameLeft + nameFrameWidth(), nameFrameTop(i) + 24, card);
				if (sparkling) {
					graphics.fill(frameLeft + 1, nameFrameTop(i) + 1,
						frameLeft + nameFrameWidth() - 1, nameFrameTop(i) + 23,
						rowBackground);
				}
				if (SpecialTheme.rainbow() || sparkling) {
					SpecialTheme.border(graphics, frameLeft, nameFrameTop(i),
						nameFrameWidth(), 24, 1);
				} else UIDraw.outline(graphics, frameLeft, nameFrameTop(i),
					nameFrameWidth(), 24, border);
				UIDraw.updateRainbowCaret(names[i], text,
					SpecialTheme.rainbow() || sparkling);
				nameHintPhases[i] = UIDraw.updateRainbowHint(names[i], font, NAME_HINT,
					NAME_HINT_COMPONENT, nameHintPhases[i],
					SpecialTheme.rainbow() || sparkling);
				drawSparklingButton(graphics, config, i, rowBackground, mouseX, mouseY);
			}
		}

		for (EditBox name : names) if (name != null) name.visible = !showInfo;
		if (showInfo) drawInfo(graphics);
		button(graphics, left + panelWidth / 2 - 52, top + panelHeight - 36,
			104, 22, "Done", mouseX, mouseY, this::onClose);
		// Minecraft's current GUI pipeline does not render child widgets implicitly
		// when a screen overrides extraction. Submit the three EditBoxes after their
		// themed frames so text, hints, selection, and the caret are actually visible.
		if (!showInfo) super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawInfo(GuiGraphicsExtractor graphics) {
		int x = left + 20;
		int y = top + 52;
		int w = panelWidth - 40;
		List<InfoLine> lines = infoLines(w - 24);
		int lineSpacing = 14;
		int h = Math.min(panelHeight - 96, Math.max(112, 20 + lines.size() * lineSpacing));
		graphics.fill(x, y, x + w, y + h, surface);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, w, h, 1);
		else UIDraw.outline(graphics, x, y, w, h, border);
		for (int i = 0; i < lines.size(); i++) {
			InfoLine info = lines.get(i);
			String line = info.text();
			int lineX = x + 12;
			int lineY = y + 11 + i * lineSpacing;
			if (info.role() != null) {
				String role = info.role();
				SpecialTheme.text(graphics, font, Component.literal(role), lineX, lineY, primary);
				drawInfoText(graphics, line, lineX + font.width(role), lineY);
			} else {
				drawInfoText(graphics, line, lineX, lineY);
			}
		}
	}

	private void drawInfoText(GuiGraphicsExtractor graphics, String line, int x, int y) {
		int iconAt = line.indexOf('✦');
		if (iconAt < 0) {
			SpecialTheme.text(graphics, font, Component.literal(line), x, y, label);
			return;
		}
		String before = line.substring(0, iconAt);
		String after = line.substring(iconAt + 1);
		SpecialTheme.text(graphics, font, Component.literal(before), x, y, label);
		int iconX = x + font.width(before);
		UIDraw.rainbowText(graphics, font, Component.literal("✦"),
			iconX, y, 0.5f, 0xFF, true);
		SpecialTheme.text(graphics, font, Component.literal(after),
			iconX + font.width("✦"), y, label);
	}

	private record InfoLine(String role, String text) { }

	private List<InfoLine> infoLines(int width) {
		if (width == cachedInfoWidth) return cachedInfoLines;
		List<InfoLine> result = new java.util.ArrayList<>();
		addInfoLines(result, null, "Ticket trading must be enabled for either role", width);
		addInfoLines(result, "Host", ": Must use a ticket before 20 seconds after joining a Safari run", width);
		addInfoLines(result, null, "- Trusted players are automatically invited to the party", width);
		addInfoLines(result, null, "- Trusted ✦ players will only be invited if a Sparkling is detected in time", width);
		addInfoLines(result, null, "- Any players who joined will be automatically warped", width);
		addInfoLines(result, null, "- Party will automatically disband after the Host leaves the Safari run", width);
		addInfoLines(result, "Guest", ": Automatically accepts party invites from trusted players", width);
		addInfoLines(result, null, "- Automatically leaves your current party for trusted ✦ players' invites", width);
		cachedInfoWidth = width;
		cachedInfoLines = List.copyOf(result);
		return cachedInfoLines;
	}

	private void addInfoLines(List<InfoLine> output, String role, String text, int width) {
		int firstWidth = Math.max(20, width - (role == null ? 0 : font.width(role)));
		List<String> wrapped = wrapPlain(text, firstWidth);
		for (int i = 0; i < wrapped.size(); i++) {
			output.add(new InfoLine(i == 0 ? role : null, wrapped.get(i)));
		}
	}

	private List<String> wrapPlain(String text, int width) {
		List<String> lines = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (!current.isEmpty() && font.width(candidate) > width) {
				lines.add(current.toString());
				current.setLength(0);
			}
			if (!current.isEmpty()) current.append(' ');
			current.append(word);
		}
		if (!current.isEmpty()) lines.add(current.toString());
		return lines;
	}

	private void toggleInfo() {
		showInfo = !showInfo;
		for (EditBox name : names) if (name != null) name.visible = !showInfo;
		setFocused(null);
	}

	private void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			String label, int mouseX, int mouseY, Runnable action) {
		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		graphics.fill(x, y, x + w, y + h, hovered ? hover : card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, w, h, 1);
		else UIDraw.outline(graphics, x, y, w, h, border);
		int colour = label.equals("Enabled") ? green : label.equals("Disabled") ? red : text;
		SpecialTheme.text(graphics, font, Component.literal(label),
			x + (w - font.width(label)) / 2, y + (h - 8) / 2, colour);
		hits.add(new Hit(x, y, w, h, action));
	}

	private void drawSparklingButton(GuiGraphicsExtractor graphics,
			SafariConfig.SparklingConfig config, int index, int rowBackground,
			int mouseX, int mouseY) {
		int x = sparklingButtonLeft();
		int y = nameFrameTop(index);
		boolean active = sparklingEnabled(config, index);
		boolean hovered = mouseX >= x && mouseX < x + SPARKLING_BUTTON_SIZE
			&& mouseY >= y && mouseY < y + SPARKLING_BUTTON_SIZE;
		graphics.fill(x, y, x + SPARKLING_BUTTON_SIZE, y + SPARKLING_BUTTON_SIZE,
			active ? rowBackground : hovered ? hover : card);
		if (SpecialTheme.rainbow() || active) {
			SpecialTheme.border(graphics, x, y, SPARKLING_BUTTON_SIZE, SPARKLING_BUTTON_SIZE, 1);
		} else {
			UIDraw.outline(graphics, x, y, SPARKLING_BUTTON_SIZE, SPARKLING_BUTTON_SIZE, border);
		}
		drawSparklingGlyph(graphics, x, y, active);
		hits.add(new Hit(x, y, SPARKLING_BUTTON_SIZE, SPARKLING_BUTTON_SIZE, () -> {
			setSparkling(config, index, !sparklingEnabled(config, index));
			ConfigManager.save();
		}));
	}

	/** The standard Sparkling glyph, moderately enlarged and centered before its shadow. */
	private void drawSparklingGlyph(GuiGraphicsExtractor graphics,
			int buttonX, int buttonY, boolean active) {
		String icon = "✦";
		float scale = 1.65f;
		float iconWidth = font.width(icon) * scale;
		float iconHeight = 8f * scale;
		// The glyph's advance box has more unused space on its right. One screen
		// pixel of optical-bearing compensation centers the actual lit pixels.
		float x = buttonX + (SPARKLING_BUTTON_SIZE - iconWidth) / 2f + 1f;
		float y = buttonY + (SPARKLING_BUTTON_SIZE - iconHeight) / 2f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		if (active) {
			UIDraw.rainbowText(graphics, font, Component.literal(icon),
				0, 0, 0.5f, 0xFF, true);
		} else {
			graphics.text(font, Component.literal(icon), 0, 0, label, true);
		}
		graphics.pose().popMatrix();
	}

	private int sparklingRowBackground(int index) {
		int accent = RainbowColours.shared(index * 0.17f, 0.42f);
		float amount = 0.20f;
		float inverse = 1f - amount;
		int red = Math.round((card >> 16 & 0xFF) * inverse + (accent >> 16 & 0xFF) * amount);
		int green = Math.round((card >> 8 & 0xFF) * inverse + (accent >> 8 & 0xFF) * amount);
		int blue = Math.round((card & 0xFF) * inverse + (accent & 0xFF) * amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private void centered(GuiGraphicsExtractor graphics, String text, int y, int colour) {
		SpecialTheme.text(graphics, font, Component.literal(text),
			left + (panelWidth - font.width(text)) / 2, y, colour);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (showInfo) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if (hit.contains(event.x(), event.y())) {
					hit.action().run();
					return true;
				}
			}
			toggleInfo();
			return true;
		}
		for (int i = 0; i < names.length; i++) {
			EditBox name = names[i];
			int fieldTop = nameFrameTop(i);
			int frameLeft = nameFrameLeft();
			boolean overFrame = event.x() >= frameLeft && event.x() < frameLeft + nameFrameWidth()
				&& event.y() >= fieldTop && event.y() < fieldTop + 24;
			if (name != null && name.visible && overFrame) {
				for (EditBox other : names) if (other != null) other.setFocused(other == name);
				setFocused(name);
				// The visible themed frame is slightly larger than the borderless native
				// editor. Forward a clamped click so its caret always activates as well.
				int editorLeft = frameLeft + 8;
				int editorTop = nameFrameTop(i) + 8;
				double x = Math.clamp(event.x(), editorLeft,
					editorLeft + nameFrameWidth() - 17);
				double y = Math.clamp(event.y(), editorTop, editorTop + 9);
				name.mouseClicked(new MouseButtonEvent(x, y, event.buttonInfo()), doubled);
				return true;
			}
		}
		setFocused(null);
		for (EditBox name : names) if (name != null) name.setFocused(false);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.action().run();
				return true;
			}
		}
		return false;
	}

	private int nameFrameLeft() {
		return stackedRows ? left + 20
			: left + ROW_MARGIN + font.width("Player 3") + LABEL_FIELD_GAP;
	}

	private int nameFrameWidth() {
		int oldWidth = stackedRows ? panelWidth - 40
			: left + panelWidth - ROW_MARGIN - nameFrameLeft();
		return oldWidth - LABEL_FIELD_GAP - SPARKLING_BUTTON_SIZE;
	}

	private int sparklingButtonLeft() {
		return nameFrameLeft() + nameFrameWidth() + LABEL_FIELD_GAP;
	}

	private int nameFrameTop(int index) {
		return top + (stackedRows ? 99 + index * 45 : 89 + index * 35);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if ((event.key() == 257 || event.key() == 335) && getFocused() instanceof EditBox field) {
			field.setFocused(false);
			setFocused(null);
			ConfigManager.save();
			return true;
		}
		if (getFocused() instanceof EditBox field && field.isFocused()) {
			return field.keyPressed(event);
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (getFocused() instanceof EditBox field && field.isFocused()) {
			return field.charTyped(event);
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		if (parent != null) ClientCompat.setScreen(parent);
		else super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static void setName(SafariConfig.SparklingConfig config, int index, String value) {
		switch (index) {
			case 0 -> config.ticketTradingPlayer1 = value;
			case 1 -> config.ticketTradingPlayer2 = value;
			case 2 -> config.ticketTradingPlayer3 = value;
			default -> { }
		}
	}

	private static boolean sparklingEnabled(SafariConfig.SparklingConfig config, int index) {
		return switch (index) {
			case 0 -> config.ticketTradingSparkling1;
			case 1 -> config.ticketTradingSparkling2;
			case 2 -> config.ticketTradingSparkling3;
			default -> false;
		};
	}

	private static void setSparkling(SafariConfig.SparklingConfig config,
			int index, boolean enabled) {
		switch (index) {
			case 0 -> config.ticketTradingSparkling1 = enabled;
			case 1 -> config.ticketTradingSparkling2 = enabled;
			case 2 -> config.ticketTradingSparkling3 = enabled;
			default -> { }
		}
	}

	private void applyTheme() {
		int[] palette = SafariSettingsScreen.activeThemePalette();
		background = palette[0];
		surface = palette[1];
		card = palette[2];
		hover = palette[3];
		border = palette[4];
		primary = palette[5];
		secondary = palette[6];
		green = palette[7];
		red = palette[8];
		text = palette[10];
		label = palette[11];
		if (SpecialTheme.rainbow()) {
			primary = SpecialTheme.accent(0);
			secondary = SpecialTheme.accent(18);
			green = SpecialTheme.accent(36);
			red = SpecialTheme.accent(54);
			border = SpecialTheme.accent(72);
		}
	}
}
