package dev.serko.safariutils.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * Shared, theme-agnostic settings primitives used by SafariUtils and addon
 * settings screens. Callers retain ownership of text, themes, and actions.
 */
public final class SettingsUiKit {
	@FunctionalInterface
	public interface RowHover {
		boolean test(int top, int height);
	}

	@FunctionalInterface
	public interface RowRenderer {
		void draw(int top, int height);
	}

	public record CardRow(int height, RowHover hover, RowRenderer renderer) { }
	public record ChoiceGrid(int width, int height, int left, int top,
			int columns, int rows, int cellWidth) { }

	private SettingsUiKit() { }
	@FunctionalInterface
	public interface HitSink { void add(int left, int top, int right, int bottom, Runnable action); }

	/** The same persisted special-theme control in every settings theme popup. */
	public static void specialThemeRow(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
			int x, int y, int width, int mouseX, int mouseY, int card, int hover, int selected,
			int border, int accent, int text, HitSink hits) {
		var settings = ConfigManager.get().advanced;
		UIDraw.rainbowText(graphics, font, "Special Theme", x, y, 0.65f);
		int count = settings.constellationThemeUnlocked ? 2 : 1;
		int cellWidth = (width - (count - 1) * 6) / count;
		for (int index = 0; index < count; index++) {
			int value = count == 2 && index == 0 ? SpecialTheme.CONSTELLATION : SpecialTheme.RAINBOW;
			String label = value == SpecialTheme.CONSTELLATION ? "Astral" : "Rainbow";
			int left = x + index * (cellWidth + 6), top = y + 12;
			boolean active = SpecialTheme.selectedMode() == value;
			pickerCell(graphics, left, top, cellWidth, 22, active,
				mouseX >= left && mouseX < left + cellWidth && mouseY >= top && mouseY < top + 22,
				card, hover, selected, border, accent);
			SpecialTheme.text(graphics, font, net.minecraft.network.chat.Component.literal(label),
				left + (cellWidth - font.width(label)) / 2, top + 7, text);
			hits.add(left, top, left + cellWidth, top + 22, () -> {
				SpecialTheme.selectSpecial(value);
				ConfigManager.save();
			});
		}
	}
	private static SettingsMotion activeMotion;
	private static int pointerX = Integer.MIN_VALUE, pointerY = Integer.MIN_VALUE;
	public static void pointer(int x, int y) { pointerX = x; pointerY = y; }
	public static boolean hovered(int x, int y, int width, int height) {
		return pointerX >= x && pointerX < x + width && pointerY >= y && pointerY < y + height;
	}
	public static boolean enter(int key) { return key == 257 || key == 335; }

	/** Same optical baseline and side padding as a 22px picker shell. */
	public static void inlineBounds(EditBox editor, int x, int y, int width, int height) {
		editor.setX(x + 8);
		editor.setY(y + (height - 9) / 2 + 1);
		editor.setWidth(Math.max(8, width - 16));
	}

	public static void available(EditBox editor, boolean available) {
		if (editor == null) return;
		editor.visible = available;
		editor.active = available;
		if (!available) editor.setFocused(false);
	}
	public static void control(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			boolean hovered, int base, int hover, int border, int accent) {
		float amount = hoverProgress(x, y, hovered);
		graphics.fill(x, y, x + width, y + height, blend(base, hover, amount));
		outline(graphics, x, y, width, height, blend(border, accent, amount));
	}
	private static net.minecraft.client.gui.navigation.ScreenRectangle activeScissor;

	public static void contentClip(net.minecraft.client.gui.navigation.ScreenRectangle clip) { activeScissor = clip; }

	/** Call once at the beginning of each settings/hub frame, on the render thread. */
	public static void beginFrame(SettingsMotion motion, int layoutSignature) {
		activeMotion = motion;
		pointer(Integer.MIN_VALUE, Integer.MIN_VALUE);
		activeScissor = null;
		motion.begin(System.nanoTime(), layoutSignature, false);
	}

	private static long key(int kind, int x, int y) {
		return ((long) kind << 48) ^ ((long) x << 24) ^ (y & 0xFFFFFFL);
	}

	public static int blend(int from, int to, float amount) {
		float t = Math.clamp(amount, 0f, 1f);
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8)
			result |= Math.round(((from >>> shift) & 255) * (1f - t) + ((to >>> shift) & 255) * t) << shift;
		return result;
	}

	public static int hoverColour(int x, int y, boolean hovered, int base, int highlight) {
		return blend(base, highlight, hoverProgress(x, y, hovered));
	}

	private static float hoverProgress(int x, int y, boolean hovered) {
		return activeMotion == null ? (hovered ? 1f : 0f)
			: activeMotion.value(key(1, x, y), hovered ? 1f : 0f);
	}

	public static void card(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
			boolean hovered, int base, int hover, int border, int accent) {
		float progress = hoverProgress(left, top, hovered);
		boolean cardHovered = hovered || hovered(left, top, width, height);
		float borderProgress = activeMotion == null ? (cardHovered ? 1f : 0f)
			: activeMotion.value(key(4, left, top), cardHovered ? 1f : 0f);
		graphics.fillGradient(left, top, left + width, top + height,
			blend(base, blend(hover, 0xFFFFFFFF, 0.10f), progress), blend(base, hover, progress * 0.45f));
		outline(graphics, left, top, width, height, blend(border, accent, borderProgress));
	}

	public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour) {
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, width, height, 1, activeScissor);
		else graphics.outline(x, y, width, height, colour);
	}

	private static void accentFill(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int colour) {
		if (SpecialTheme.rainbow()) SpecialTheme.bar(graphics, left, top, right - left, bottom - top, activeScissor);
		else graphics.fill(left, top, right, bottom, colour);
	}

	/** Layout separators use the same screen-space special-theme gradient as card borders. */
	public static void divider(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int colour) {
		accentFill(graphics, left, top, right, bottom, colour);
	}

	public static void section(GuiGraphicsExtractor graphics, int left, int right, int top,
			int height, int surface, int accent) {
		graphics.fill(left, top, right, top + height, surface);
		accentFill(graphics, left, top, left + 2, top + height, accent);
	}

	public static void selectionBar(GuiGraphicsExtractor graphics, int x, int targetY, int height, int colour) {
		int y = Math.round(activeMotion == null ? targetY : activeMotion.navigation(targetY));
		accentFill(graphics, x, y, x + 3, y + height, colour);
	}

	/** Consistent expand indicator: the vertical stroke folds into the horizontal stroke. */
	public static void disclosure(GuiGraphicsExtractor graphics, int centerX, int centerY,
			boolean expanded, int colour) {
		float amount = activeMotion == null ? (expanded ? 1f : 0f)
			: activeMotion.value(key(3, centerX, centerY), expanded ? 1f : 0f);
		accentFill(graphics, centerX - 3, centerY, centerX + 4, centerY + 1, colour);
		int half = Math.round(3 * (1f - amount));
		if (half > 0) accentFill(graphics, centerX, centerY - half, centerX + 1, centerY + half + 1, colour);
	}

	public static void pickerCell(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			boolean selected, boolean hovered, int card, int hover, int selectedFill, int border, int accent) {
		graphics.fill(x, y, x + width, y + height, selected ? selectedFill : hoverColour(x, y, hovered, card, hover));
		outline(graphics, x, y, width, height, selected ? accent : blend(blend(card, border, 0.6f), accent, hoverProgress(x, y, hovered)));
		if (selected) accentFill(graphics, x + 5, y + height - 2, x + width - 5, y + height - 1, accent);
	}

	public static void status(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
			boolean hovered, int card, int hover, int accent) {
		graphics.fill(left, top, left + width, top + height, hoverColour(left, top, hovered, card, hover));
		graphics.fill(left, top, left + 3, top + height, accent);
	}

	public static boolean stacked(int left, int right) {
		return right - left < 430;
	}

	public static int controlWidth(int left, int right, boolean stacked) {
		return stacked ? Math.max(80, right - left - 24)
			: Math.clamp((right - left) / 3, 116, 210);
	}

	/** Exact responsive geometry used by the SafariUtils choice picker. */
	public static ChoiceGrid choiceGrid(int screenWidth, int screenHeight, int choices,
			int hintHeight) {
		int maxRows = Math.max(1, (screenHeight - 98 - hintHeight) / 27);
		int columns = Math.clamp((choices + maxRows - 1) / maxRows, 2, 6);
		int rows = (choices + columns - 1) / columns;
		int width = Math.min(680, screenWidth - 30);
		int height = Math.min(screenHeight - 30, 68 + hintHeight + rows * 27);
		int left = (screenWidth - width) / 2;
		int top = (screenHeight - height) / 2;
		int cellWidth = (width - 28 - (columns - 1) * 6) / columns;
		return new ChoiceGrid(width, height, left, top, columns, rows, cellWidth);
	}

	/** Resting inline fields always show the beginning of their value. */
	public static void showStart(EditBox editor) {
		if (editor == null) return;
		editor.setCursorPosition(0);
		editor.setHighlightPos(0);
	}

	/** Selected inline fields show the end of their value and place the caret there. */
	public static void focusEnd(EditBox editor) {
		if (editor == null) return;
		editor.setFocused(true);
		int end = editor.getValue().length();
		editor.setCursorPosition(end);
		editor.setHighlightPos(end);
	}

	/** Focuses an inline field and lets the native widget place its caret at the click. */
	public static void focusAt(EditBox editor, MouseButtonEvent event, boolean doubleClick) {
		if (editor == null || event == null) return;
		editor.setFocused(true);
		double x = Math.clamp(event.x(), editor.getX(), editor.getX() + editor.getWidth() - 1.0);
		double y = Math.clamp(event.y(), editor.getY(), editor.getY() + editor.getHeight() - 1.0);
		editor.mouseClicked(new MouseButtonEvent(x, y, event.buttonInfo()), doubleClick);
	}

	public static void blurToStart(EditBox editor) {
		if (editor == null) return;
		editor.setFocused(false);
		showStart(editor);
	}

	public static void drawToggle(GuiGraphicsExtractor graphics, int x, int y,
			boolean enabled, boolean interactiveHovered, int surface, int enabledTrack, int enabledBorder,
			int disabledBorder, int enabledKnob, int disabledKnob) {
		float progress = activeMotion == null ? (enabled ? 1f : 0f)
			: activeMotion.value(key(2, x, y), enabled ? 1f : 0f);
		graphics.fill(x, y, x + 38, y + 18, blend(surface, enabledTrack, progress));
		outline(graphics, x, y, 38, 18, blend(blend(disabledBorder, enabledBorder, progress), enabledKnob,
			hoverProgress(x, y, interactiveHovered)));
		int knob = x + 3 + Math.round(progress * 20f);
		accentFill(graphics, knob, y + 3, knob + 12, y + 15,
			blend(disabledKnob, enabledKnob, progress));
	}

	/** Draws one card containing independently interactive rows. */
	public static int drawCombinedCard(GuiGraphicsExtractor graphics,
			int left, int right, int top, int viewportTop, int viewportBottom,
			List<CardRow> rows, int card, int hover, int border, int hoverBorder) {
		int totalHeight = 0;
		for (CardRow row : rows) totalHeight += row.height();
		boolean visible = top + totalHeight > viewportTop && top < viewportBottom;
		boolean cardHovered = hovered(left, Math.max(top, viewportTop), right - left,
			Math.max(0, Math.min(top + totalHeight, viewportBottom) - Math.max(top, viewportTop)));
		float cardHover = activeMotion == null ? (cardHovered ? 1f : 0f)
			: activeMotion.value(key(4, left, top), cardHovered ? 1f : 0f);
		if (visible) graphics.fill(left, top, right, top + totalHeight, card);

		int rowTop = top;
		for (CardRow row : rows) {
			if (rowTop + row.height() > viewportTop && rowTop < viewportBottom) {
				boolean selected = cardHovered && row.hover().test(rowTop, row.height());
				float rowHover = activeMotion == null ? (selected ? 1f : 0f)
					: activeMotion.value(key(5, left, rowTop), selected ? 1f : 0f);
				// Include the divider's full width; paint its inset line afterward.
				if (rowHover > 0f) graphics.fillGradient(left, rowTop, right, rowTop + row.height() + 1,
					blend(card, blend(hover, 0xFFFFFFFF, 0.10f), rowHover), blend(card, hover, rowHover * 0.45f));
				row.renderer().draw(rowTop, row.height());
			}
			rowTop += row.height();
		}
		if (visible) {
			int dividerY = top;
			for (int index = 1; index < rows.size(); index++) {
				dividerY += rows.get(index - 1).height();
				accentFill(graphics, left + 8, dividerY, right - 8, dividerY + 1, blend(border, hoverBorder, cardHover));
			}
			if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, left, top, right - left, totalHeight + 1, 1,
				new net.minecraft.client.gui.navigation.ScreenRectangle(left, viewportTop, right - left, viewportBottom - viewportTop));
			else graphics.outline(left, top, right - left, totalHeight + 1, blend(border, hoverBorder, cardHover));
		}
		return top + totalHeight;
	}
}
