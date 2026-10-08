package dev.serko.safariutils.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

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

	@FunctionalInterface
	public interface OutlineRenderer {
		void draw(int x, int y, int width, int height, int colour);
	}

	public record CardRow(int height, RowHover hover, RowRenderer renderer) { }
	public record ChoiceGrid(int width, int height, int left, int top,
			int columns, int rows, int cellWidth) { }

	private SettingsUiKit() { }

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

	public static void blurToStart(EditBox editor) {
		if (editor == null) return;
		editor.setFocused(false);
		showStart(editor);
	}

	public static void drawToggle(GuiGraphicsExtractor graphics, int x, int y,
			boolean enabled, int surface, int enabledTrack, int enabledBorder,
			int disabledBorder, int enabledKnob, int disabledKnob, OutlineRenderer outline) {
		graphics.fill(x, y, x + 38, y + 18, enabled ? enabledTrack : surface);
		outline.draw(x, y, 38, 18, enabled ? enabledBorder : disabledBorder);
		int knob = enabled ? x + 23 : x + 3;
		graphics.fill(knob, y + 3, knob + 12, y + 15,
			enabled ? enabledKnob : disabledKnob);
	}

	/** Draws one card containing independently interactive rows. */
	public static int drawCombinedCard(GuiGraphicsExtractor graphics,
			int left, int right, int top, int viewportTop, int viewportBottom,
			List<CardRow> rows, int card, int hover, int border, int hoverBorder,
			OutlineRenderer outline) {
		int totalHeight = rows.stream().mapToInt(CardRow::height).sum();
		boolean visible = top + totalHeight > viewportTop && top < viewportBottom;
		if (visible) {
			graphics.fill(left, top, right, top + totalHeight, card);
			int dividerY = top;
			for (int index = 1; index < rows.size(); index++) {
				dividerY += rows.get(index - 1).height();
				graphics.fill(left + 8, dividerY, right - 8, dividerY + 1, border);
			}
		}

		int rowTop = top;
		int hoveredTop = Integer.MIN_VALUE;
		int hoveredHeight = 0;
		for (CardRow row : rows) {
			if (rowTop + row.height() > viewportTop && rowTop < viewportBottom) {
				boolean rowHovered = row.hover() != null && row.hover().test(rowTop, row.height());
				if (rowHovered) {
					graphics.fill(left + 1, rowTop, right - 1, rowTop + row.height(), hover);
					hoveredTop = rowTop;
					hoveredHeight = row.height();
				}
				row.renderer().draw(rowTop, row.height());
			}
			rowTop += row.height();
		}
		if (visible) outline.draw(left, top, right - left, totalHeight + 1, border);
		if (hoveredTop != Integer.MIN_VALUE) {
			outline.draw(left, hoveredTop, right - left, hoveredHeight + 1, hoverBorder);
		}
		return top + totalHeight;
	}
}
