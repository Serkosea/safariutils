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
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private boolean showInfo;

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
		panelWidth = Math.min(520, Math.max(300, width - 20));
		panelHeight = Math.min(294, Math.max(250, height - 20));
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		String[] values = {config.ticketTradingPlayer1, config.ticketTradingPlayer2,
			config.ticketTradingPlayer3};
		for (int i = 0; i < names.length; i++) {
			int index = i;
			int frameLeft = nameFrameLeft();
			// Match Safari Settings' ordinary inline text editor exactly: the native
			// field is borderless and inset 8px/7px inside the themed 24px frame.
			EditBox field = new EditBox(font, frameLeft + 8, top + 96 + i * 35,
				nameFrameWidth() - 16, 10, Component.literal("Username " + (i + 1)));
			field.setMaxLength(16);
			field.setBordered(false);
			field.setValue(values[i] == null ? "" : values[i]);
			field.setHint(NAME_HINT_COMPONENT);
			field.setResponder(value -> setName(config, index, value));
			field.setTextColor(text);
			field.setTextColorUneditable(label);
			UIDraw.rainbowEditBox(field, font);
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
		button(graphics, left + 22, top + 57, 112, 22,
			config.ticketTradingEnabled ? "Enabled" : "Disabled", mouseX, mouseY, () -> {
				config.ticketTradingEnabled = !config.ticketTradingEnabled;
				ConfigManager.save();
			});
		button(graphics, left + panelWidth - 48, top + 57, 26, 22, "i", mouseX, mouseY,
			this::toggleInfo);
		for (int i = 0; i < names.length; i++) {
			int frameLeft = nameFrameLeft();
			SpecialTheme.text(graphics, font, Component.literal("Player " + (i + 1)),
				left + ROW_MARGIN,
				top + 97 + i * 35, label);
			graphics.fill(frameLeft, top + 89 + i * 35,
				frameLeft + nameFrameWidth(), top + 113 + i * 35, card);
			UIDraw.outline(graphics, frameLeft, top + 89 + i * 35,
				nameFrameWidth(), 24, SpecialTheme.rainbow()
					? SpecialTheme.accent(i * 18) : border);
			UIDraw.updateRainbowCaret(names[i], text);
			nameHintPhases[i] = UIDraw.updateRainbowHint(names[i], font, NAME_HINT,
				NAME_HINT_COMPONENT, nameHintPhases[i]);
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
		int y = top + 82;
		int w = panelWidth - 40;
		int h = 144;
		graphics.fill(x, y, x + w, y + h, surface);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, w, h, 1);
		else UIDraw.outline(graphics, x, y, w, h, border);
		List<String> lines = List.of(
			"Ticket trading must be enabled for either role",
			"Host: Must use a ticket before 20 seconds after joining a Safari run",
			"- Trusted players are automatically invited to the party",
			"- Any players who joined will be automatically warped",
			"- Party will automatically disband after the Host leaves the Safari run",
			"Guest: Automatically accepts party invites from trusted players"
		);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int lineX = x + 12;
			int lineY = y + 12 + i * 19;
			if (line.startsWith("Host:") || line.startsWith("Guest:")) {
				String role = line.substring(0, line.indexOf(':') + 1);
				SpecialTheme.text(graphics, font, Component.literal(role), lineX, lineY, primary);
				SpecialTheme.text(graphics, font, Component.literal(line.substring(role.length())),
					lineX + font.width(role), lineY, label);
			} else {
				SpecialTheme.text(graphics, font, Component.literal(line), lineX, lineY, label);
			}
		}
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
			int fieldTop = top + 89 + i * 35;
			int frameLeft = nameFrameLeft();
			boolean overFrame = event.x() >= frameLeft && event.x() < frameLeft + nameFrameWidth()
				&& event.y() >= fieldTop && event.y() < fieldTop + 24;
			if (name != null && name.visible && overFrame) {
				for (EditBox other : names) if (other != null) other.setFocused(other == name);
				setFocused(name);
				// The visible themed frame is slightly larger than the borderless native
				// editor. Forward a clamped click so its caret always activates as well.
				int editorLeft = frameLeft + 8;
				int editorTop = top + 96 + i * 35;
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
		return left + ROW_MARGIN + font.width("Player 3") + LABEL_FIELD_GAP;
	}

	private int nameFrameWidth() {
		return left + panelWidth - ROW_MARGIN - nameFrameLeft();
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
