package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Compact settings-transfer and run-history tools opened from the settings header. */
final class DataToolsScreen extends Screen {
	private enum Tab {
		SETTINGS("Settings", "Import or export current settings"),
		RUNS("Run History", "Check or safely repair saved run data");

		private final String label;
		private final String description;

		Tab(String label, String description) {
			this.label = label;
			this.description = description;
		}
	}

	private record Hit(int x, int y, int width, int height, Runnable action) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width
				&& mouseY >= y && mouseY < y + height;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private Tab tab = Tab.SETTINGS;
	private boolean settingsImported;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int logicalWidth;
	private int logicalHeight;
	private float scale = 1f;
	private int background;
	private int surface;
	private int card;
	private int hover;
	private int border;
	private int primary;
	private int secondary;
	private int green;
	private int red;
	private int gold;
	private int text;
	private int muted;

	private DataToolsScreen(Screen parent) {
		super(Component.literal("SafariUtils Data Tools"));
		this.parent = parent;
	}

	static void open() {
		Minecraft client = Minecraft.getInstance();
		Screen parent = ClientCompat.screen();
		client.execute(() -> {
			DataActions.clearStatus();
			ClientCompat.setScreen(new DataToolsScreen(parent));
		});
	}

	@Override
	protected void init() {
		applyTheme();
		int preferredWidth = 520;
		int preferredHeight = 192;
		scale = ResponsiveUI.fitScale(width, height, preferredWidth, preferredHeight, 8);
		logicalWidth = ResponsiveUI.logicalWidth(width, scale);
		logicalHeight = ResponsiveUI.logicalHeight(height, scale);
		panelWidth = Math.min(preferredWidth, logicalWidth - 16);
		panelHeight = Math.min(preferredHeight, logicalHeight - 16);
		left = (logicalWidth - panelWidth) / 2;
		top = (logicalHeight - panelHeight) / 2;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		applyTheme();
		if (parent != null) parent.extractRenderState(graphics, Integer.MIN_VALUE,
			Integer.MIN_VALUE, partialTick);
		else graphics.fill(0, 0, width, height, background);
		graphics.fill(0, 0, width, height, 0xA0000000);
		int logicalMouseX = Math.round(mouseX / scale);
		int logicalMouseY = Math.round(mouseY / scale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, surface);
		SpecialTheme.stars(graphics, left + 2, top + 2,
			panelWidth - 4, panelHeight - 4, 0.55f);
		if (SpecialTheme.rainbow()) {
			SpecialTheme.border(graphics, left, top, panelWidth, panelHeight, 1);
		} else UIDraw.outline(graphics, left, top, panelWidth, panelHeight, border);
		hits.clear();
		centered(graphics, "Data Tools", top + 14, text);
		drawTabs(graphics, logicalMouseX, logicalMouseY);
		drawActions(graphics, logicalMouseX, logicalMouseY);
		drawFooter(graphics, logicalMouseX, logicalMouseY);
		graphics.pose().popMatrix();
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int gap = 8;
		int tabWidth = (panelWidth - 40 - gap) / 2;
		int x = left + 20;
		for (Tab choice : Tab.values()) {
			boolean selected = choice == tab;
			button(graphics, x, top + 34, tabWidth, 22, choice.label,
				mouseX, mouseY, selected, () -> select(choice));
			x += tabWidth + gap;
		}
	}

	private void drawActions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left + 20;
		int y = top + 64;
		int width = panelWidth - 40;
		if (tab == Tab.SETTINGS) {
			action(graphics, x, y, width, "Import Settings",
				"Reads settings from clipboard; Click again to confirm valid import",
				mouseX, mouseY, this::importSettings);
			action(graphics, x, y + 48, width, "Export Settings",
				"Exports settings to clipboard", mouseX, mouseY, DataActions::exportSettings);
		} else {
			action(graphics, x, y, width, "Check Run History",
				"Checks saved runs without changing any data",
				mouseX, mouseY, DataActions::checkRunHistory);
			action(graphics, x, y + 48, width, "Repair Run History",
				"Backs up runs before repairing objectively invalid structure",
				mouseX, mouseY, DataActions::repairRunHistory);
		}
	}

	private void action(GuiGraphicsExtractor graphics, int x, int y, int width,
			String title, String description, int mouseX, int mouseY, Runnable action) {
		graphics.fill(x, y, x + width, y + 42, 0x66000000);
		UIDraw.outline(graphics, x, y, width, 42, border);
		draw(graphics, title, x + 10, y + 9, text);
		int buttonWidth = Math.max(86, font.width(title) + 18);
		draw(graphics, trim(description, width - buttonWidth - 30), x + 10, y + 26, muted);
		button(graphics, x + width - buttonWidth - 10, y + 10,
			buttonWidth, 22, title, mouseX, mouseY, false, action);
	}

	private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String status = DataActions.status();
		String description = status.isBlank() ? tab.description : status;
		int colour = status.isBlank() ? muted : statusColour();
		int doneWidth = 72;
		int doneX = left + panelWidth - doneWidth - 20;
		int footerY = top + panelHeight - 32;
		draw(graphics, trim(description, doneX - left - 38), left + 20,
			footerY + 7, colour);
		button(graphics, doneX, footerY, doneWidth, 22, "Done",
			mouseX, mouseY, false, this::onClose);
	}

	private void importSettings() {
		if (!DataActions.importSettings()) return;
		settingsImported = true;
	}

	private void select(Tab selected) {
		if (tab == selected) return;
		tab = selected;
		DataActions.clearStatus();
	}

	private void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			String value, int mouseX, int mouseY, boolean selected, Runnable action) {
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		graphics.fill(x, y, x + width, y + height,
			selected ? hover : hovered ? hover : card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, width, height, 1);
		else UIDraw.outline(graphics, x, y, width, height, selected ? secondary : border);
		centeredWithin(graphics, value, x, y + (height - 8) / 2 + 1, width,
			selected ? secondary : text);
		hits.add(new Hit(x, y, width, height, action));
	}

	private int statusColour() {
		return switch (DataActions.statusTone()) {
			case SUCCESS -> green;
			case WARNING -> gold;
			case ERROR -> red;
			case INFO -> secondary;
			case MUTED -> muted;
		};
	}

	private String trim(String value, int maximumWidth) {
		if (font.width(value) <= maximumWidth) return value;
		return font.plainSubstrByWidth(value, Math.max(0, maximumWidth - font.width("…"))) + "…";
	}

	private void centered(GuiGraphicsExtractor graphics, String value, int y, int colour) {
		centeredWithin(graphics, value, left, y, panelWidth, colour);
	}

	private void centeredWithin(GuiGraphicsExtractor graphics, String value,
			int x, int y, int width, int colour) {
		draw(graphics, value, x + (width - font.width(value)) / 2, y, colour);
	}

	private void draw(GuiGraphicsExtractor graphics, String value, int x, int y, int colour) {
		SpecialTheme.text(graphics, font, Component.literal(value), x, y, colour);
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
		gold = palette[9];
		text = palette[10];
		muted = palette[11];
		if (SpecialTheme.rainbow()) {
			primary = SpecialTheme.accent(0);
			secondary = SpecialTheme.accent(18);
			green = SpecialTheme.accent(36);
			red = SpecialTheme.accent(54);
			gold = SpecialTheme.accent(72);
			border = SpecialTheme.accent(90);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x() / scale;
		double mouseY = event.y() / scale;
		for (int index = hits.size() - 1; index >= 0; index--) {
			Hit hit = hits.get(index);
			if (!hit.contains(mouseX, mouseY)) continue;
			hit.action.run();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 256) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		DataActions.clearStatus();
		if (settingsImported && parent instanceof SafariSettingsScreen settings) {
			ClientCompat.setScreen(ConfigManager.createScreen(settings.parentScreen()));
		} else ClientCompat.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static boolean inside(double mouseX, double mouseY,
			int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width
			&& mouseY >= y && mouseY < y + height;
	}
}
