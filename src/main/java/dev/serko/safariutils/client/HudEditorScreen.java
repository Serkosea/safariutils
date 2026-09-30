package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
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
	private static final long EDITOR_CHROME_DELAY_MILLIS = 3_000L;
	private int hint, dim, outline, outlineIdle, backdropTop, backdropBottom;
	private int surface, card, cardHover, border, accent;

	/** Where each box was drawn last frame, so clicks and scrolls can be hit-tested. */
	private final Map<HudBox, Rect> bounds = new EnumMap<>(HudBox.class);
	/** Real HUD contents change on ticks, not between multiple render frames in one tick. */
	private final Map<HudBox, TickCache<HudPanel>> panelCaches = new EnumMap<>(HudBox.class);

	private HudBox dragging;
	private int grabOffsetX;
	private int grabOffsetY;
	private HudBox hovered;
	private HudBox lastHovered;
	private Rect resetButton;
	private Rect snapButton;
	private Rect doneButton;
	private long resetArmedUntil;
	private boolean snappedHorizontal;
	private boolean snappedVertical;
	private long lastHudInteractionAt;
	private int lastDragPointerX;
	private int lastDragPointerY;
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
		int gutter = ResponsiveUI.gutter(width);
		int gap = width < 360 ? 4 : 7;
		int available = Math.max(3, width - gutter * 2 - gap * 2);
		int buttonWidth = Math.min(98, available / 3);
		int groupWidth = buttonWidth * 3 + gap * 2;
		int startX = (width - groupWidth) / 2;
		resetButton = new Rect(startX, height - 32, buttonWidth, 22);
		snapButton = new Rect(startX + buttonWidth + gap, height - 32, buttonWidth, 22);
		doneButton = new Rect(startX + (buttonWidth + gap) * 2, height - 32, buttonWidth, 22);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		applyTheme();
		graphics.fillGradient(0, 0, width, height, backdropTop, backdropBottom);
		graphics.fill(0, 0, width, 43, surface);
		graphics.fill(0, height - 42, width, height, surface);
		graphics.fill(0, 42, width, 43, border);
		if (SpecialTheme.rainbow()) SpecialTheme.stars(graphics, 0, 0, width, height, 0.7f);

		Font font = this.font;
		bounds.clear();
		hovered = null;
		snappedHorizontal = false;
		snappedVertical = false;
		long now = System.currentTimeMillis();
		if (dragging != null && (mouseX != lastDragPointerX || mouseY != lastDragPointerY)) {
			lastHudInteractionAt = now;
			lastDragPointerX = mouseX;
			lastDragPointerY = mouseY;
		}
		boolean chromeBehindHuds = lastHudInteractionAt != 0
			&& now - lastHudInteractionAt < EDITOR_CHROME_DELAY_MILLIS;
		if (chromeBehindHuds) drawEditorChrome(graphics, mouseX, mouseY, now);

		for (HudBox box : HudBox.values()) {
			if (!box.enabled()) continue;

			HudPanel panel = panelCaches.computeIfAbsent(box, ignored -> new TickCache<>())
				.get(box::panel);
			if (panel == null || panel.isEmpty()) panel = box.placeholderPanel();

			float scale = box.scale() * ResponsiveUI.scale(width, height);
			int x = box.pixelX(width, panel, font, scale);
			int y = box.pixelY(height, panel, scale);
			int w = Math.round(panel.width(font) * scale);
			int h = Math.round(panel.height() * scale);
			// Normalized anchors can round outward by one pixel after a drag, especially
			// for left-expanding HUDs. Apply the same visible-border bounds on every
			// frame so all four screen edges remain perfectly symmetrical.
			x = clamp(x, edgeMinimum(width, w), edgeMaximum(width, w));
			y = clamp(y, edgeMinimum(height, h), edgeMaximum(height, h));

			if (dragging == box) {
				x = clamp(mouseX - grabOffsetX, edgeMinimum(width, w), edgeMaximum(width, w));
				y = clamp(mouseY - grabOffsetY, edgeMinimum(height, h), edgeMaximum(height, h));
				if (ConfigManager.get().display.hudSnapping) {
					x = snapX(x, w);
					y = snapY(y, h);
				}
				box.setPixelPosition(x, y, width, height, panel, font, scale);
			}

			bounds.put(box, new Rect(x, y, w, h));
			graphics.fill(x - 4, y - 4, x + w + 4, y + h + 4, card);
			panel.render(graphics, font, x, y, scale, HudBorderStyle.editor(box));

			boolean over = dragging == box
				|| (dragging == null && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h);
			if (over) {
				hovered = box;
				lastHovered = box;
			}
			outline(graphics, x, y, w, h, over ? outline : outlineIdle);

			if (over) {
				String tag = "%s  ·  %.0f%%".formatted(box.label(), box.scale() * 100);
				int tagY = Math.max(3, y - 14);
				int tagW = font.width(tag) + 8;
				int tagX = clamp(x + (w - tagW) / 2, 1, Math.max(1, width - tagW - 1));
				graphics.fill(tagX, tagY - 2, tagX + tagW, tagY + 11, cardHover);
				outline(graphics, tagX, tagY - 2, tagW, 13, accent);
				drawText(graphics, tag, tagX + 4, tagY, hint);
			}
		}
		if (dragging != null && snappedHorizontal) {
			graphics.fill(width / 2, 43, width / 2 + 1, height - 42, accent);
		}
		if (dragging != null && snappedVertical) {
			graphics.fill(0, height / 2, width, height / 2 + 1, accent);
		}

		if (!chromeBehindHuds) drawEditorChrome(graphics, mouseX, mouseY, now);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawEditorChrome(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now) {
		String title = "HUD LAYOUT";
		drawText(graphics, title, (width - font.width(title)) / 2, 12, hint);
		String hint2 = responsiveHint();
		drawText(graphics, hint2, (width - font.width(hint2)) / 2, 24, dim);
		boolean resetArmed = now < resetArmedUntil;
		drawButton(graphics, resetButton, resetArmed ? "Confirm" : responsiveLabel("Reset Layout", "Reset"),
			mouseX, mouseY, resetArmed);
		drawButton(graphics, snapButton,
			responsiveLabel("Snap: ", "Snap ")
				+ (ConfigManager.get().display.hudSnapping ? "On" : "Off"),
			mouseX, mouseY, ConfigManager.get().display.hudSnapping);
		drawButton(graphics, doneButton, "Done", mouseX, mouseY, true);
	}

	private void drawButton(GuiGraphicsExtractor graphics, Rect rect, String label,
			int mouseX, int mouseY, boolean primary) {
		boolean hovered = rect.contains(mouseX, mouseY);
		int buttonBorder = primary ? accent : outline;
		graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
			hovered ? cardHover : card);
		outline(graphics, rect.x, rect.y, rect.w, rect.h, buttonBorder);
		String fitted = fit(label, Math.max(1, rect.w - 8));
		drawText(graphics, fitted,
			rect.x + (rect.w - font.width(fitted)) / 2, rect.y + 7, hovered ? hint : dim);
	}

	private String responsiveHint() {
		String snap = ConfigManager.get().display.hudSnapping ? "on" : "off";
		String full = "Drag to move  ·  Arrows to nudge  ·  Scroll to resize  ·  Snapping " + snap;
		if (font.width(full) <= width - 16) return full;
		String medium = "Drag  ·  Arrows nudge  ·  Scroll resize  ·  Snap " + snap;
		if (font.width(medium) <= width - 16) return medium;
		return fit("Drag · Arrows · Scroll · Snap " + snap, Math.max(1, width - 16));
	}

	private String responsiveLabel(String full, String compact) {
		return resetButton != null && resetButton.w >= font.width(full) + 8 ? full : compact;
	}

	private String fit(String value, int maxWidth) {
		if (font.width(value) <= maxWidth) return value;
		String suffix = "…";
		return font.plainSubstrByWidth(value,
			Math.max(0, maxWidth - font.width(suffix))) + suffix;
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
		if (SpecialTheme.rainbow()) {
			accent = SpecialTheme.accent(18);
			outline = SpecialTheme.accent(54);
			border = SpecialTheme.accent(72);
			cardHover = blend(card, SpecialTheme.accent(36), 0.20f);
			outlineIdle = (SpecialTheme.accent(0) & 0x00FFFFFF) | 0x70000000;
		}
	}

	private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int colour) {
		SpecialTheme.text(graphics, font, Component.literal(text), x, y, colour);
	}

	private static int blend(int base, int accent, float amount) {
		float inverse = 1f - amount;
		int red = Math.round((base >> 16 & 0xFF) * inverse + (accent >> 16 & 0xFF) * amount);
		int green = Math.round((base >> 8 & 0xFF) * inverse + (accent >> 8 & 0xFF) * amount);
		int blue = Math.round((base & 0xFF) * inverse + (accent & 0xFF) * amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
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
		if (SpecialTheme.rainbow()) {
			SpecialTheme.border(graphics, x - 1, y - 1, w + 2, h + 2, 1);
			return;
		}
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
			if (System.currentTimeMillis() >= resetArmedUntil) {
				resetArmedUntil = System.currentTimeMillis() + 3_000L;
				return true;
			}
			resetAll();
			resetArmedUntil = 0;
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
			lastHudInteractionAt = System.currentTimeMillis();
			lastDragPointerX = mouseX;
			lastDragPointerY = mouseY;
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
		lastHudInteractionAt = System.currentTimeMillis();
		ConfigManager.save();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int dx = 0;
		int dy = 0;
		switch (event.key()) {
			case 262 -> dx = 1;
			case 263 -> dx = -1;
			case 264 -> dy = 1;
			case 265 -> dy = -1;
			default -> { return super.keyPressed(event); }
		}
		HudBox target = lastHovered;
		Rect rect = target == null ? null : bounds.get(target);
		if (rect == null) return super.keyPressed(event);
		HudPanel panel = target.panel();
		if (panel == null || panel.isEmpty()) panel = target.placeholderPanel();
		float renderedScale = target.scale() * ResponsiveUI.scale(width, height);
		int x = clamp(rect.x() + dx, edgeMinimum(width, rect.w()), edgeMaximum(width, rect.w()));
		int y = clamp(rect.y() + dy, edgeMinimum(height, rect.h()), edgeMaximum(height, rect.h()));
		target.setPixelPosition(x, y, width, height, panel, font, renderedScale);
		lastHudInteractionAt = System.currentTimeMillis();
		ConfigManager.save();
		return true;
	}

	private void resetAll() {
		SafariConfig.DisplayConfig display = new SafariConfig.DisplayConfig();
		SafariConfig.AlertConfig alerts = new SafariConfig.AlertConfig();
		HudBox.PROGRESS.setScale(display.progressScale);
		HudBox.MISSING.setScale(display.missingScale);
		HudBox.CONTEST.setScale(display.contestScale);
		HudBox.PARTY_OBJECTIVE.setScale(display.partyObjectiveScale);
		HudBox.ALERTS.setScale(alerts.alertScale);
		HudBox.PROGRESS.setPosition(display.progressX, display.progressY);
		HudBox.MISSING.setPosition(display.missingX, display.missingY);
		HudBox.CONTEST.setPosition(display.contestX, display.contestY);
		HudBox.PARTY_OBJECTIVE.setPosition(display.partyObjectiveX, display.partyObjectiveY);
		HudBox.ALERTS.setPosition(alerts.alertHorizontalPosition, alerts.alertVerticalPosition);
		lastHudInteractionAt = System.currentTimeMillis();
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

	private static int edgeMinimum(int screenSize, int boxSize) {
		return boxSize < screenSize ? 1 : 0;
	}

	private static int edgeMaximum(int screenSize, int boxSize) {
		return Math.max(edgeMinimum(screenSize, boxSize), screenSize - boxSize - 1);
	}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}
}
