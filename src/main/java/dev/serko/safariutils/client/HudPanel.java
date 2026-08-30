package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A small stacked panel with a measured layout.
 *
 * <p>Minecraft's font is proportional, so padding rows with {@code %-8s} does not
 * line anything up. Columns here are positioned from measured pixel widths instead:
 * labels left, values right-aligned against a common edge, with an optional progress
 * bar between them.
 */
public final class HudPanel {

	private static final int LINE_HEIGHT = 12;
	private static final int TEXT_HEIGHT = 9;
	private static final int PADDING = 6;
	private static final int GUTTER = 6;
	private static final int BAR_WIDTH = 34;
	private static final int BAR_HEIGHT = 4;

	private static final int BACKGROUND_TOP = 0xC20F151E;
	private static final int BACKGROUND_BOTTOM = 0xC20A0E15;
	private static final int BORDER = 0x5AFFFFFF;
	private static final int INNER_KEYLINE = 0x24FFFFFF;
	private static final int BAR_TRACK = 0x55262F3C;

	private final List<Row> rows = new ArrayList<>();
	private int minimumWidth;

	/** Sets a floor for the total panel width while still allowing wider content. */
	public HudPanel minimumWidth(int pixels) {
		minimumWidth = Math.max(0, pixels);
		return this;
	}

	public HudPanel title(String text, int colour) {
		rows.add(new Row(Kind.TITLE, text, null, colour, 0, 0, 0));
		return this;
	}

	/** A title with an immediately adjacent suffix in a second colour. */
	public HudPanel titleSuffix(String text, String suffix, int colour, int suffixColour) {
		rows.add(new Row(Kind.TITLE_SUFFIX, text, suffix, colour, suffixColour, 0, 0));
		return this;
	}

	public HudPanel line(String text, int colour) {
		rows.add(new Row(Kind.TEXT, text, null, colour, 0, 0, 0));
		return this;
	}

	public HudPanel pair(String label, String value, int labelColour, int valueColour) {
		rows.add(new Row(Kind.PAIR, label, value, labelColour, valueColour, 0, 0));
		return this;
	}

	/** A pair that uses the panel's existing width instead of widening it. */
	public HudPanel compactPair(String label, String value, int labelColour, int valueColour) {
		rows.add(new Row(Kind.COMPACT_PAIR, label, value, labelColour, valueColour, 0, 0));
		return this;
	}

	public HudPanel boldPair(String label, String value, int labelColour, int valueColour) {
		rows.add(new Row(Kind.BOLD_PAIR, label, value, labelColour, valueColour, 0, 0));
		return this;
	}

	public HudPanel rainbowPair(String label, String value) {
		rows.add(new Row(Kind.RAINBOW_PAIR, label, value, 0, 0, 0, 0));
		return this;
	}

	public HudPanel statusPair(boolean caught, String label, String value,
						   int labelColour, int valueColour) {
		rows.add(new Row(Kind.STATUS_PAIR, (caught ? "✔ " : "✘ ") + label, value,
			labelColour, valueColour, caught ? 1 : 0, 0));
		return this;
	}

	public HudPanel sparklingModeTitle(String biome, int colour) {
		rows.add(new Row(Kind.SPARKLING_MODE_TITLE, biome + " ", "(SPARKLING Mode)",
			colour, 0, 0, 0));
		return this;
	}

	/** A label, a filled progress bar, and a right-aligned {@code current/max} value. */
	public HudPanel bar(String label, int current, int max, int labelColour, int barColour) {
		rows.add(new Row(Kind.BAR, label, current + "/" + max, labelColour, barColour, current, max));
		return this;
	}

	/** A completed progress row with a separately coloured green checkmark. */
	public HudPanel checkedBar(String label, int current, int max, int labelColour, int barColour) {
		rows.add(new Row(Kind.CHECKED_BAR, label, current + "/" + max,
			labelColour, barColour, current, max));
		return this;
	}

	public HudPanel blank() {
		rows.add(new Row(Kind.BLANK, "", null, 0, 0, 0, 0));
		return this;
	}

	/** Removes layout separators that would otherwise become bottom padding. */
	public HudPanel trimTrailingBlanks() {
		while (!rows.isEmpty() && rows.getLast().kind() == Kind.BLANK) rows.removeLast();
		return this;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	/** Panel width in pixels at scale 1, measured from the widest row. */
	public int width(Font font) {
		return Math.max(minimumWidth, contentWidth(font) + PADDING * 2);
	}

	/** Panel height in pixels at scale 1. */
	public int height() {
		return rows.isEmpty() ? 0
			: (rows.size() - 1) * LINE_HEIGHT + TEXT_HEIGHT + PADDING * 2;
	}

	private int contentWidth(Font font) {
		int pairLabelWidth = 0;
		int pairValueWidth = 0;
		int barLabelWidth = 0;
		int barValueWidth = 0;
		for (Row row : rows) {
			if (row.kind() == Kind.TITLE_SUFFIX || row.kind() == Kind.SPARKLING_MODE_TITLE) continue;
			if (row.kind() == Kind.BAR || row.kind() == Kind.CHECKED_BAR) {
				barLabelWidth = Math.max(barLabelWidth, labelWidth(font, row));
				barValueWidth = Math.max(barValueWidth, valueWidth(font, row));
			} else if (row.kind() != Kind.COMPACT_PAIR) {
				pairLabelWidth = Math.max(pairLabelWidth, labelWidth(font, row));
				if (row.value() != null) pairValueWidth = Math.max(pairValueWidth, valueWidth(font, row));
			}
		}
		int contentWidth = pairLabelWidth + (pairValueWidth > 0 ? GUTTER + pairValueWidth : 0);
		if (barLabelWidth > 0) {
			contentWidth = Math.max(contentWidth,
				barLabelWidth + GUTTER + BAR_WIDTH + GUTTER + barValueWidth);
		}
		// A title spans the whole panel, so it can widen it on its own.
		for (Row row : rows) {
			if (row.kind() == Kind.TITLE || row.kind() == Kind.TEXT) {
				contentWidth = Math.max(contentWidth, font.width(row.label()));
			} else if (row.kind() == Kind.TITLE_SUFFIX || row.kind() == Kind.SPARKLING_MODE_TITLE) {
				contentWidth = Math.max(contentWidth,
					font.width(row.label()) + font.width(row.value()));
			}
		}
		return contentWidth;
	}

	/**
	 * Draws the panel with its top-left corner at {@code x, y}, scaled about that
	 * corner. Scaling is applied to the matrix rather than to every coordinate, so
	 * the layout maths stays in unscaled pixels.
	 */
	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
		render(graphics, font, x, y, scale, BORDER);
	}

	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale,
					   int borderColour) {
		if (rows.isEmpty()) return;
		if (SpecialTheme.rainbow()) {
			renderSpecialRainbow(graphics, font, x, y, scale);
			return;
		}
		if (scale == 1.0f) {
			draw(graphics, font, x, y, borderColour);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		draw(graphics, font, 0, 0, borderColour);
		graphics.pose().popMatrix();
	}

	/** Draws this panel with the same continuous animated rainbow used by Sparkling. */
	public void renderRainbow(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
		if (rows.isEmpty()) return;
		if (SpecialTheme.rainbow()) {
			renderSpecialRainbow(graphics, font, x, y, scale);
			return;
		}
		if (scale == 1.0f) {
			draw(graphics, font, x, y, 0, true);
			drawRainbowBorder(graphics, x, y, width(font), height());
			drawInnerKeyline(graphics, x, y, width(font), height());
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		draw(graphics, font, 0, 0, 0, true);
		drawRainbowBorder(graphics, 0, 0, width(font), height());
		drawInnerKeyline(graphics, 0, 0, width(font), height());
		graphics.pose().popMatrix();
	}

	/** Draws ordinary panel content with only the frame changed to rainbow. */
	public void renderRainbowBorder(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
		if (rows.isEmpty()) return;
		if (SpecialTheme.rainbow()) {
			renderSpecialRainbow(graphics, font, x, y, scale);
			return;
		}
		if (scale == 1.0f) {
			draw(graphics, font, x, y, 0);
			drawRainbowBorder(graphics, x, y, width(font), height());
			drawInnerKeyline(graphics, x, y, width(font), height());
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		draw(graphics, font, 0, 0, 0);
		drawRainbowBorder(graphics, 0, 0, width(font), height());
		drawInnerKeyline(graphics, 0, 0, width(font), height());
		graphics.pose().popMatrix();
	}

	private void renderSpecialRainbow(GuiGraphicsExtractor graphics, Font font,
								  int x, int y, float scale) {
		if (scale == 1.0f) {
			draw(graphics, font, x, y, 0, true, true);
			SpecialTheme.border(graphics, x, y, width(font), height(), 1);
			drawInnerKeyline(graphics, x, y, width(font), height());
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		draw(graphics, font, 0, 0, 0, true, true);
		SpecialTheme.border(graphics, 0, 0, width(font), height(), 1);
		drawInnerKeyline(graphics, 0, 0, width(font), height());
		graphics.pose().popMatrix();
	}

	private void draw(GuiGraphicsExtractor graphics, Font font, int left, int y, int borderColour) {
		draw(graphics, font, left, y, borderColour, false);
	}

	private void draw(GuiGraphicsExtractor graphics, Font font, int left, int y,
					  int borderColour, boolean rainbowTitle) {
		draw(graphics, font, left, y, borderColour, rainbowTitle, false);
	}

	private void draw(GuiGraphicsExtractor graphics, Font font, int left, int y,
					  int borderColour, boolean rainbowTitle, boolean rainbowAll) {
		int barLabelWidth = 0;
		int barValueWidth = 0;
		for (Row row : rows) {
			if (row.kind() == Kind.TITLE_SUFFIX || row.kind() == Kind.SPARKLING_MODE_TITLE) continue;
			if (row.kind() == Kind.BAR || row.kind() == Kind.CHECKED_BAR) {
				barLabelWidth = Math.max(barLabelWidth, labelWidth(font, row));
				barValueWidth = Math.max(barValueWidth, valueWidth(font, row));
			}
		}

		int panelWidth = width(font);
		int panelHeight = height();

		// A quiet inner keyline separates the panel from the world without a shadow.
		graphics.fillGradient(left, y, left + panelWidth, y + panelHeight,
			BACKGROUND_TOP, BACKGROUND_BOTTOM);
		UIDraw.outline(graphics, left + 1, y + 1, panelWidth - 2, panelHeight - 2, INNER_KEYLINE);
		if (rainbowAll) SpecialTheme.stars(graphics, left + 2, y + 2,
			panelWidth - 4, panelHeight - 4, 1.5f);
		if ((borderColour >>> 24) != 0) {
			UIDraw.outline(graphics, left, y, panelWidth, panelHeight, borderColour);
		}

		int textLeft = left + PADDING;
		int valueRight = left + panelWidth - PADDING;
		int barLeft = valueRight - barValueWidth - GUTTER - BAR_WIDTH;
		int rowY = y + PADDING;

		for (Row row : rows) {
			switch (row.kind()) {
				case BLANK -> {
				}
				case TITLE -> {
					if (rainbowTitle) rainbowText(graphics, font, row.label(), textLeft, rowY);
					else graphics.text(font, Component.literal(row.label()), textLeft, rowY,
						row.labelColour());
				}
				case TEXT -> {
					if (rainbowAll) rainbowText(graphics, font, row.label(), textLeft, rowY);
					else graphics.text(font, Component.literal(row.label()),
						textLeft, rowY, row.labelColour());
				}
				case TITLE_SUFFIX -> {
					if (rainbowTitle) {
						rainbowText(graphics, font, row.label() + row.value(), textLeft, rowY);
					} else {
						graphics.text(font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
						graphics.text(font, Component.literal(row.value()),
							textLeft + font.width(row.label()), rowY, row.valueColour());
					}
				}
				case SPARKLING_MODE_TITLE -> {
					if (rainbowTitle) {
						rainbowText(graphics, font, row.label() + row.value(), textLeft, rowY);
					} else {
						graphics.text(font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
						rainbowText(graphics, font, row.value(),
							textLeft + font.width(row.label()), rowY);
					}
				}
				case PAIR, COMPACT_PAIR, BOLD_PAIR -> {
					if (rainbowAll) rainbowText(graphics, font, row.label(), textLeft, rowY);
					else graphics.text(font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
					Component value = value(row);
					if (rainbowAll) SpecialTheme.rainbowText(graphics, font, value,
						valueRight - font.width(value), rowY);
					else graphics.text(font, value,
						valueRight - font.width(value), rowY, row.valueColour());
				}
				case RAINBOW_PAIR -> {
					rainbowText(graphics, font, row.label(), textLeft, rowY);
					Component value = Component.literal(row.value());
					rainbowText(graphics, font, row.value(),
						valueRight - font.width(value), rowY);
				}
				case STATUS_PAIR -> {
					if (rainbowAll) {
						rainbowText(graphics, font, row.label(), textLeft, rowY);
						rainbowText(graphics, font, row.value(),
							valueRight - font.width(row.value()), rowY);
					} else {
						String mark = row.label().substring(0, 1);
						String name = row.label().substring(1);
						graphics.text(font, Component.literal(mark), textLeft, rowY,
							row.current() == 1 ? 0xFF55FF55 : 0xFFFF5555);
						graphics.text(font, Component.literal(name), textLeft + font.width(mark),
							rowY, row.labelColour());
						graphics.text(font, Component.literal(row.value()),
							valueRight - font.width(row.value()), rowY, row.valueColour());
					}
				}
				case BAR, CHECKED_BAR -> {
					String label = row.kind() == Kind.CHECKED_BAR ? row.label() + " ✔" : row.label();
					if (rainbowAll) rainbowText(graphics, font, label, textLeft, rowY);
					else {
						graphics.text(font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
						if (row.kind() == Kind.CHECKED_BAR) {
							graphics.text(font, Component.literal(" ✔"), textLeft + font.width(row.label()),
								rowY, 0xFF55FF55);
						}
					}
					int barY = rowY + 2;
					graphics.fill(barLeft, barY, barLeft + BAR_WIDTH, barY + BAR_HEIGHT, BAR_TRACK);
					if (row.max() > 0 && row.current() > 0) {
						int filled = Math.max(1, BAR_WIDTH * row.current() / row.max());
						graphics.fill(barLeft, barY, barLeft + filled, barY + BAR_HEIGHT, row.valueColour());
					}
					if (rainbowAll) rainbowText(graphics, font, row.value(),
						valueRight - font.width(row.value()), rowY);
					else graphics.text(font, Component.literal(row.value()),
						valueRight - font.width(row.value()), rowY, row.valueColour());
				}
			}
			rowY += LINE_HEIGHT;
		}
	}

	private static void drawRainbowBorder(GuiGraphicsExtractor graphics, int left, int top,
									  int panelWidth, int panelHeight) {
		int horizontal = 28;
		int vertical = Math.max(1, Math.round(horizontal * panelHeight / (float) panelWidth));
		int total = 2 * (horizontal + vertical);
		float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
		for (int i = 0; i < horizontal; i++) {
			int x1 = left + panelWidth * i / horizontal;
			int x2 = left + panelWidth * (i + 1) / horizontal;
			graphics.fill(x1, top, x2, top + 1, UIDraw.rainbow(phase, i, total, 0.55f));
			graphics.fill(left + panelWidth - (x2 - left), top + panelHeight - 1,
				left + panelWidth - (x1 - left), top + panelHeight,
				UIDraw.rainbow(phase, horizontal + vertical + i, total, 0.55f));
		}
		for (int i = 0; i < vertical; i++) {
			int y1 = top + panelHeight * i / vertical;
			int y2 = top + panelHeight * (i + 1) / vertical;
			graphics.fill(left + panelWidth - 1, y1, left + panelWidth, y2,
				UIDraw.rainbow(phase, horizontal + i, total, 0.55f));
			graphics.fill(left, top + panelHeight - (y2 - top), left + 1,
				top + panelHeight - (y1 - top),
				UIDraw.rainbow(phase, 2 * horizontal + vertical + i, total, 0.55f));
		}
	}

	private static void drawInnerKeyline(GuiGraphicsExtractor graphics, int left, int top,
			int panelWidth, int panelHeight) {
		UIDraw.outline(graphics, left + 1, top + 1, panelWidth - 2, panelHeight - 2,
			INNER_KEYLINE);
	}

	private static void rainbowText(GuiGraphicsExtractor graphics, Font font,
								 String text, int x, int y) {
		UIDraw.rainbowText(graphics, font, text, x, y, 0.45f);
	}

	private static int valueWidth(Font font, Row row) {
		return font.width(value(row));
	}

	private static int labelWidth(Font font, Row row) {
		return font.width(row.kind() == Kind.CHECKED_BAR ? row.label() + " ✔" : row.label());
	}

	private static Component value(Row row) {
		Component value = Component.literal(row.value());
		return row.kind() == Kind.BOLD_PAIR ? value.copy().withStyle(ChatFormatting.BOLD) : value;
	}

	private enum Kind {TITLE, TITLE_SUFFIX, SPARKLING_MODE_TITLE, TEXT, PAIR, COMPACT_PAIR, BOLD_PAIR,
		STATUS_PAIR, RAINBOW_PAIR, BAR, CHECKED_BAR, BLANK}

	private record Row(Kind kind, String label, String value,
					   int labelColour, int valueColour, int current, int max) {
	}
}
