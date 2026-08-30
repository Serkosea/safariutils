package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Drag-to-place editor for the on-screen boxes, reached from the Edit button in the
 * settings.
 *
 * <p>Every box is shown with its real contents where it has any, and a labelled
 * placeholder where it does not, so positions can be set outside a run. Drag to move,
 * scroll over a box to resize it.
 */
public final class HudEditorScreen extends Screen {

	private static final float SCALE_STEP = 0.1f;
	private static final int CENTRE_SNAP_DISTANCE = 5;
	private static final int EDGE_SNAP_DISTANCE = 3;
	private int hint, dim, outline, outlineIdle, backdropTop, backdropBottom;
	private int surface, card, cardHover, border, accent;

	/** Where each box was drawn last frame, so clicks and scrolls can be hit-tested. */
	private final Map<HudBox, Rect> bounds = new EnumMap<>(HudBox.class);

	private HudBox dragging;
	private int grabOffsetX;
	private int grabOffsetY;
	private HudBox hovered;
	private Rect resetButton;
	private Rect snapButton;
	private Rect doneButton;
	private boolean snappedHorizontal;
	private boolean snappedVertical;
	private final Screen parent;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD positions"));
		this.parent = parent;
	}

	/** Opens the editor on the next tick, from wherever the settings screen was. */
	public static void open() {
		Minecraft client = Minecraft.getInstance();
		Screen parent = ClientCompat.screen();
		client.execute(() -> ClientCompat.setScreen(new HudEditorScreen(parent)));
	}

	@Override
	protected void init() {
		resetButton = new Rect(width / 2 - 154, height - 32, 98, 22);
		snapButton = new Rect(width / 2 - 49, height - 32, 98, 22);
		doneButton = new Rect(width / 2 + 56, height - 32, 98, 22);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		applyTheme();
		graphics.fillGradient(0, 0, width, height, backdropTop, backdropBottom);
		graphics.fill(0, 0, width, 43, surface);
		graphics.fill(0, height - 42, width, height, surface);
		graphics.fill(0, 42, width, 43, border);

		Font font = this.font;
		bounds.clear();
		hovered = null;
		snappedHorizontal = false;
		snappedVertical = false;

		for (HudBox box : HudBox.values()) {
			if (!box.enabled()) continue;

			HudPanel panel = box.panel();
			if (panel == null || panel.isEmpty()) panel = box.placeholderPanel();

			float scale = box.scale();
			int x = box.pixelX(width, panel, font);
			int y = box.pixelY(height, panel, font);
			int w = Math.round(panel.width(font) * scale);
			int h = Math.round(panel.height() * scale);

			if (dragging == box) {
				x = clamp(mouseX - grabOffsetX, 0, width - w);
				y = clamp(mouseY - grabOffsetY, 0, height - h);
				if (ConfigManager.get().display.hudSnapping) {
					x = snapX(x, w);
					y = snapY(y, h);
				}
				box.setPixelPosition(x, y, width, height, panel, font);
			}

			bounds.put(box, new Rect(x, y, w, h));
			graphics.fill(x - 4, y - 4, x + w + 4, y + h + 4, card);
			panel.render(graphics, font, x, y, scale, HudBorderStyle.editor(box));

			boolean over = dragging == box
				|| (dragging == null && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h);
			if (over) hovered = box;
			outline(graphics, x, y, w, h, over ? outline : outlineIdle);

			if (over) {
				String tag = "%s  ·  %.0f%%".formatted(box.label(), scale * 100);
				int tagY = Math.max(2, y - 14);
				int tagW = font.width(tag) + 8;
				graphics.fill(x - 1, tagY - 2, x + tagW, tagY + 11, cardHover);
				outline(graphics, x - 1, tagY - 2, tagW + 1, 13, accent);
				graphics.text(font, Component.literal(tag), x + 3, tagY, hint);
			}
		}
		if (dragging != null && snappedHorizontal) {
			graphics.fill(width / 2, 43, width / 2 + 1, height - 42, accent);
		}
		if (dragging != null && snappedVertical) {
			graphics.fill(0, height / 2, width, height / 2 + 1, accent);
		}

		String title = "HUD LAYOUT";
		graphics.text(font, Component.literal(title), (width - font.width(title)) / 2, 12, hint);
		String hint2 = "Drag to move  ·  Scroll to resize  ·  Snapping "
			+ (ConfigManager.get().display.hudSnapping ? "on" : "off");
		graphics.text(font, Component.literal(hint2), (width - font.width(hint2)) / 2, 24, dim);
		drawButton(graphics, resetButton, "Reset Layout", mouseX, mouseY, false);
		drawButton(graphics, snapButton,
			"Snap: " + (ConfigManager.get().display.hudSnapping ? "On" : "Off"),
			mouseX, mouseY, ConfigManager.get().display.hudSnapping);
		drawButton(graphics, doneButton, "Done", mouseX, mouseY, true);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawButton(GuiGraphicsExtractor graphics, Rect rect, String label,
			int mouseX, int mouseY, boolean primary) {
		boolean hovered = rect.contains(mouseX, mouseY);
		int buttonBorder = primary ? accent : outline;
		graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
			hovered ? cardHover : card);
		outline(graphics, rect.x, rect.y, rect.w, rect.h, buttonBorder);
		graphics.text(font, Component.literal(label),
			rect.x + (rect.w - font.width(label)) / 2, rect.y + 7, hovered ? hint : dim);
	}

	private void applyTheme() {
		int[] palette = SafariSettingsScreen.activeThemePalette();
		backdropTop = palette[0];
		backdropBottom = palette[2];
		surface = palette[1];
		card = palette[2];
		cardHover = palette[3];
		border = palette[4];
		accent = palette[6];
		outline = palette[9];
		hint = palette[10];
		dim = palette[12];
		outlineIdle = (palette[4] & 0x00FFFFFF) | 0x70000000;
	}

	private int snapX(int value, int boxWidth) {
		int centre = (width - boxWidth) / 2;
		int snapped = snapAxis(value, centre, width - boxWidth);
		snappedHorizontal = snapped == centre;
		return snapped;
	}

	private int snapY(int value, int boxHeight) {
		int centre = (height - boxHeight) / 2;
		int snapped = snapAxis(value, centre, height - boxHeight);
		snappedVertical = snapped == centre;
		return snapped;
	}

	private static int snapAxis(int value, int centre, int farEdge) {
		if (Math.abs(value - centre) <= CENTRE_SNAP_DISTANCE) return centre;
		if (Math.abs(value) <= EDGE_SNAP_DISTANCE) return 0;
		if (Math.abs(value - farEdge) <= EDGE_SNAP_DISTANCE) return farEdge;
		return value;
	}

	private void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
		graphics.fill(x - 1, y - 1, x + w + 1, y, colour);
		graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, colour);
		graphics.fill(x - 1, y, x, y + h, colour);
		graphics.fill(x + w, y, x + w + 1, y + h, colour);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (super.mouseClicked(event, doubled)) return true;

		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		if (resetButton != null && resetButton.contains(mouseX, mouseY)) {
			resetAll();
			return true;
		}
		if (doneButton != null && doneButton.contains(mouseX, mouseY)) {
			onClose();
			return true;
		}
		if (snapButton != null && snapButton.contains(mouseX, mouseY)) {
			ConfigManager.get().display.hudSnapping = !ConfigManager.get().display.hudSnapping;
			ConfigManager.save();
			return true;
		}
		for (Map.Entry<HudBox, Rect> hit : bounds.entrySet()) {
			Rect rect = hit.getValue();
			if (mouseX < rect.x() || mouseX >= rect.x() + rect.w()) continue;
			if (mouseY < rect.y() || mouseY >= rect.y() + rect.h()) continue;
			dragging = hit.getKey();
			grabOffsetX = mouseX - rect.x();
			grabOffsetY = mouseY - rect.y();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			ConfigManager.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		HudBox target = hovered;
		if (target == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		float scale = target.scale() + (float) Math.signum(scrollY) * SCALE_STEP;
		scale = Math.round(scale * 100) / 100f;
		target.setScale(Math.clamp(scale, target.minScale(), target.maxScale()));
		ConfigManager.save();
		return true;
	}

	private void resetAll() {
		HudBox.PROGRESS.setScale(1.0f);
		HudBox.MISSING.setScale(1.0f);
		HudBox.CONTEST.setScale(1.0f);
		HudBox.ALERTS.setScale(4.0f);
		HudBox.PROGRESS.setPosition(0.0035128805f, 0.00625f);
		HudBox.MISSING.setPosition(0.0035128805f, 0.29375f);
		HudBox.CONTEST.setPosition(0.99531615f, 0.00625f);
		HudBox.ALERTS.setPosition(0.5f, 0.4f);
		ConfigManager.save();
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

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}
}
