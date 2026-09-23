package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small stacked panel with a measured layout.
 *
 * <p>Minecraft's font is proportional, so padding rows with {@code %-8s} does not
 * line anything up. Columns here are positioned from measured pixel widths instead:
 * labels left, values right-aligned against a common edge, with an optional progress
 * bar between them.
 */
public final class HudPanel {
	private static final String[] BIRD_ICON = {".....ss....", "....s##ss..", "....wwwd#s.",
		"...hhw###oo", ".wwww####s.", "smww####s..", "mmmmms##s..", ".mmmms#s...",
		"..mm..ss..."};
	private static final String[] BERRY_ICON = {"....bb....", "...#b#....", "..#hh###..",
		"..hh#####.", ".########s", ".########s", ".#######ss", "..######s.",
		"...####s.."};
	private static final String[] WORM_ICON = {"..........", "........h.", ".......#hs",
		".......mms", "......h#s.", "......sss.", "....#mm...", ".m#hsss...",
		"..sss....."};
	private static final String[] SEED_BAG_ICON = {"..........", "...#ggg#..", "...shhh##.",
		"...hh####.", "..s#######", ".s########", ".s###dd###", ".s##d####s",
		"..s#####s."};
	private static final String[] INCENSE_ICON = {".....t....", "....tc....", "...tc.....",
		"....c.....", "..tcdhd...", "..d###ds..", ".d#####ds.", ".d#####ds.",
		"..ddddds.."};
	private static final String[] GEM_ICON = {"..........", "...hhhh...", "..hh##hh..",
		".hh####hs.", ".m######s.", ".m######s.", ".mm####ss.", "..mm##ss..",
		"...msss..."};
	// Compact fixed-palette symbols for Party Objective title states.
	private static final String[] MOUNTAIN_ICON = {"....l......", "...loo..l..", "..looosloo.",
		".looogloogs", "looogggggss", "oogggggssss", "ogggggsssss", "sssdddsssss",
		".sssssssss."};
	// The Icy symbol intentionally uses a fixed pale-blue palette.
	private static final String[] SNOWFLAKE_ICON = {".....g.....", "..l..g..l..", "...l.g.l...",
		".l..ggg..l.", "..gggdggg..", ".l..ggg..l.", "...l.g.l...",
		"..l..g..l..", ".....g....."};
	private static final String[] SKULL_ICON = {"..lllll....", ".lgggggl...", "lgggggggl..",
		"lgpdgdpgl..", "lgggggggl..", ".lggdggl...", "..gdddg....", "..s.s.s....",
		"..sssss...."};
	private static final String[] LEAF_ICON = {".....l.....", "....lgl....", "...lgggl...",
		"..lggvggs..", "..lggvggs..", "..lggvggs..", "...lgvgs...", "....vs.....",
		"....s......"};

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
	private static final int ICON_CACHE_LIMIT = 64;
	private static final Map<IconKey, int[]> ICON_QUADS =
		new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<IconKey, int[]> eldest) {
				return size() > ICON_CACHE_LIMIT;
			}
		};

	private final List<Row> rows = new ArrayList<>();
	private int minimumWidth;
	private Layout layout;

	/** Sets a floor for the total panel width while still allowing wider content. */
	public HudPanel minimumWidth(int pixels) {
		minimumWidth = Math.max(0, pixels);
		layout = null;
		return this;
	}

	private void add(Row row) {
		rows.add(row);
		layout = null;
	}

	public HudPanel title(String text, int colour) {
		add(new Row(Kind.TITLE, text, null, colour, 0, 0, 0));
		return this;
	}

	/** A compact objective title with semantic biome icons and completion marks. */
	public HudPanel objectiveTitle(String text, int colour, int shownMask, int knownMask,
			boolean cavern, boolean icy, boolean haunted, boolean forest) {
		int completeMask = (cavern ? 1 : 0) | (icy ? 2 : 0)
			| (haunted ? 4 : 0) | (forest ? 8 : 0);
		add(new Row(Kind.OBJECTIVE_TITLE, text, "", colour, knownMask & 15,
			completeMask, shownMask & 15));
		return this;
	}

	/** A title with an immediately adjacent suffix in a second colour. */
	public HudPanel titleSuffix(String text, String suffix, int colour, int suffixColour) {
		add(new Row(Kind.TITLE_SUFFIX, text, suffix, colour, suffixColour, 0, 0));
		return this;
	}

	public HudPanel line(String text, int colour) {
		add(new Row(Kind.TEXT, text, null, colour, 0, 0, 0));
		return this;
	}

	public HudPanel pair(String label, String value, int labelColour, int valueColour) {
		add(new Row(Kind.PAIR, label, value, labelColour, valueColour, 0, 0));
		return this;
	}

	/** A right-aligned value made from mod-rendered icons and ordinary count text. */
	public HudPanel iconPair(String label, int labelColour, IconValue... values) {
		add(new Row(Kind.ICON_PAIR, label, "", labelColour, 0xFFFFFFFF,
			0, 0, null, List.of(values)));
		return this;
	}

	public HudPanel rainbowIconPair(String label, IconValue... values) {
		add(new Row(Kind.RAINBOW_ICON_PAIR, label, "", 0xFFFFFFFF, 0xFFFFFFFF,
			0, 0, null, List.of(values)));
		return this;
	}

	/** A pair that uses the panel's existing width instead of widening it. */
	public HudPanel compactPair(String label, String value, int labelColour, int valueColour) {
		add(new Row(Kind.COMPACT_PAIR, label, value, labelColour, valueColour, 0, 0));
		return this;
	}

	public HudPanel boldPair(String label, String value, int labelColour, int valueColour) {
		add(new Row(Kind.BOLD_PAIR, label, value, labelColour, valueColour, 0, 0));
		return this;
	}

	public HudPanel rainbowPair(String label, String value) {
		add(new Row(Kind.RAINBOW_PAIR, label, value, 0, 0, 0, 0));
		return this;
	}

	public HudPanel statusPair(boolean caught, String label, String value,
						   int labelColour, int valueColour) {
		add(new Row(Kind.STATUS_PAIR, (caught ? "✔ " : "✘ ") + label, value,
			labelColour, valueColour, caught ? 1 : 0, 0));
		return this;
	}

	public HudPanel sparklingModeTitle(String biome, int colour) {
		add(new Row(Kind.SPARKLING_MODE_TITLE, biome + " ", "(SPARKLING Mode)",
			colour, 0, 0, 0));
		return this;
	}

	/** A label, a filled progress bar, and a right-aligned {@code current/max} value. */
	public HudPanel bar(String label, int current, int max, int labelColour, int barColour) {
		add(new Row(Kind.BAR, label, current + "/" + max, labelColour, barColour, current, max));
		return this;
	}

	/** A completed progress row with a separately coloured green checkmark. */
	public HudPanel checkedBar(String label, int current, int max, int labelColour, int barColour) {
		add(new Row(Kind.CHECKED_BAR, label, current + "/" + max,
			labelColour, barColour, current, max));
		return this;
	}

	public HudPanel blank() {
		add(new Row(Kind.BLANK, "", null, 0, 0, 0, 0));
		return this;
	}

	/** Removes layout separators that would otherwise become bottom padding. */
	public HudPanel trimTrailingBlanks() {
		while (!rows.isEmpty() && rows.getLast().kind() == Kind.BLANK) {
			rows.removeLast();
			layout = null;
		}
		return this;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	/** Panel width in pixels at scale 1, measured from the widest row. */
	public int width(Font font) {
		return layout(font).width();
	}

	/** Panel height in pixels at scale 1. */
	public int height() {
		return rows.isEmpty() ? 0
			: (rows.size() - 1) * LINE_HEIGHT + TEXT_HEIGHT + PADDING * 2;
	}

	private Layout layout(Font font) {
		if (layout != null) return layout;
		int pairLabelWidth = 0;
		int pairValueWidth = 0;
		int barLabelWidth = 0;
		int barValueWidth = 0;
		for (Row row : rows) {
			if (row.kind() == Kind.TITLE_SUFFIX || row.kind() == Kind.SPARKLING_MODE_TITLE
				|| row.kind() == Kind.OBJECTIVE_TITLE) continue;
			if (row.kind() == Kind.BAR || row.kind() == Kind.CHECKED_BAR) {
				barLabelWidth = Math.max(barLabelWidth, labelWidth(font, row));
				barValueWidth = Math.max(barValueWidth, valueWidth(font, row));
			} else if (row.kind() != Kind.COMPACT_PAIR) {
				pairLabelWidth = Math.max(pairLabelWidth, labelWidth(font, row));
				if (row.kind() == Kind.ICON_PAIR || row.kind() == Kind.RAINBOW_ICON_PAIR)
					pairValueWidth = Math.max(pairValueWidth, iconValueWidth(font, row.icons()));
				else if (row.value() != null)
					pairValueWidth = Math.max(pairValueWidth, valueWidth(font, row));
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
			} else if (row.kind() == Kind.OBJECTIVE_TITLE) {
				contentWidth = Math.max(contentWidth,
					font.width(row.label()) + objectiveStatusWidth(font, row.max()));
			} else if (row.kind() == Kind.TITLE_SUFFIX || row.kind() == Kind.SPARKLING_MODE_TITLE) {
				contentWidth = Math.max(contentWidth,
					font.width(row.label()) + font.width(row.value()));
			}
		}
		int width = Math.max(minimumWidth, contentWidth + PADDING * 2);
		layout = new Layout(width, barQuads(width, barValueWidth));
		return layout;
	}

	private int[] barQuads(int panelWidth, int barValueWidth) {
		int[] quads = new int[rows.size() * 10];
		int cursor = 0;
		int barLeft = panelWidth - PADDING - barValueWidth - GUTTER - BAR_WIDTH;
		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			if (row.kind() != Kind.BAR && row.kind() != Kind.CHECKED_BAR) continue;
			int barY = PADDING + index * LINE_HEIGHT + 2;
			cursor = addQuad(quads, cursor, barLeft, barY,
				barLeft + BAR_WIDTH, barY + BAR_HEIGHT, BAR_TRACK);
			if (row.max() > 0 && row.current() > 0) {
				int filled = Math.max(1, BAR_WIDTH * row.current() / row.max());
				cursor = addQuad(quads, cursor, barLeft, barY,
					barLeft + filled, barY + BAR_HEIGHT, row.valueColour());
			}
		}
		return Arrays.copyOf(quads, cursor);
	}

	private static int addQuad(int[] values, int cursor,
			int x0, int y0, int x1, int y1, int colour) {
		values[cursor++] = x0;
		values[cursor++] = y0;
		values[cursor++] = x1;
		values[cursor++] = y1;
		values[cursor++] = colour;
		return cursor;
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
		Layout measured = layout(font);
		int panelWidth = measured.width();
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
		GuiQuadBatchRenderState.submit(graphics, left, y, panelWidth, panelHeight,
			measured.barQuads());

		int textLeft = left + PADDING;
		int valueRight = left + panelWidth - PADDING;
		int rowY = y + PADDING;

		for (Row row : rows) {
			switch (row.kind()) {
				case BLANK -> {
				}
				case TITLE -> {
					if (rainbowTitle) rainbowText(graphics, font, row.label(), textLeft, rowY);
					else drawText(graphics, font, Component.literal(row.label()), textLeft, rowY,
						row.labelColour());
				}
				case OBJECTIVE_TITLE -> {
					if (rainbowTitle) rainbowText(graphics, font, row.label(), textLeft, rowY);
					else drawText(graphics, font, Component.literal(row.label()), textLeft, rowY,
						row.labelColour());
					drawObjectiveStatus(graphics, font, textLeft + font.width(row.label()), rowY,
						row.current(), row.max(), row.valueColour(), left + panelWidth);
				}
				case TEXT -> {
					if (rainbowAll) rainbowText(graphics, font, row.label(), textLeft, rowY);
					else drawText(graphics, font, Component.literal(row.label()),
						textLeft, rowY, row.labelColour());
				}
				case TITLE_SUFFIX -> {
					if (rainbowTitle) {
						rainbowText(graphics, font, row.label() + row.value(), textLeft, rowY);
					} else {
						drawText(graphics, font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
						drawText(graphics, font, Component.literal(row.value()),
							textLeft + font.width(row.label()), rowY, row.valueColour());
					}
				}
				case SPARKLING_MODE_TITLE -> {
					if (rainbowTitle) {
						rainbowText(graphics, font, row.label() + row.value(), textLeft, rowY);
					} else {
						drawText(graphics, font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
						rainbowText(graphics, font, row.value(),
							textLeft + font.width(row.label()), rowY);
					}
				}
				case PAIR, COMPACT_PAIR, BOLD_PAIR -> {
					if (rainbowAll) rainbowText(graphics, font, row.label(), textLeft, rowY);
					else drawText(graphics, font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
					Component value = value(row);
					if (rainbowAll) SpecialTheme.rainbowText(graphics, font, value,
						valueRight - font.width(value), rowY);
					else drawText(graphics, font, value,
						valueRight - font.width(value), rowY, row.valueColour());
				}
				case ICON_PAIR, RAINBOW_ICON_PAIR -> {
					if (rainbowAll || row.kind() == Kind.RAINBOW_ICON_PAIR)
						rainbowText(graphics, font, row.label(), textLeft, rowY);
					else drawText(graphics, font, Component.literal(row.label()),
						textLeft, rowY, row.labelColour());
					// Icons retain their semantic feed/bird colours under every panel theme.
					drawIconValue(graphics, font, row.icons(),
						valueRight - iconValueWidth(font, row.icons()), rowY, rainbowAll);
				}
				case RAINBOW_PAIR -> {
					rainbowText(graphics, font, row.label(), textLeft, rowY);
					Component value = Component.literal(row.value());
					rainbowText(graphics, font, row.value(),
						valueRight - font.width(value), rowY);
				}
				case STATUS_PAIR -> {
					String mark = row.label().substring(0, 1);
					String name = row.label().substring(1);
					if (rainbowAll) {
						rainbowText(graphics, font, row.label(), textLeft, rowY);
						rainbowText(graphics, font, row.value(),
							valueRight - font.width(row.value()), rowY);
					} else {
						drawText(graphics, font, Component.literal(mark), textLeft, rowY,
							row.current() == 1 ? 0xFF55FF55 : 0xFFFF5555);
						drawText(graphics, font, Component.literal(name), textLeft + font.width(mark),
							rowY, row.labelColour());
						drawText(graphics, font, Component.literal(row.value()),
							valueRight - font.width(row.value()), rowY, row.valueColour());
					}
				}
				case BAR, CHECKED_BAR -> {
					String label = row.kind() == Kind.CHECKED_BAR ? row.label() + " ✔" : row.label();
					if (rainbowAll) rainbowText(graphics, font, label, textLeft, rowY);
					else {
						drawText(graphics, font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
						if (row.kind() == Kind.CHECKED_BAR) {
							drawText(graphics, font, Component.literal(" ✔"), textLeft + font.width(row.label()),
								rowY, 0xFF55FF55);
						}
					}
					if (rainbowAll) rainbowText(graphics, font, row.value(),
						valueRight - font.width(row.value()), rowY);
					else drawText(graphics, font, Component.literal(row.value()),
						valueRight - font.width(row.value()), rowY, row.valueColour());
				}
			}
			rowY += LINE_HEIGHT;
		}
	}

	private static void drawRainbowBorder(GuiGraphicsExtractor graphics, int left, int top,
									  int panelWidth, int panelHeight) {
		SpecialTheme.border(graphics, left, top, panelWidth, panelHeight, 1);
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

	private static String mark(boolean known, boolean complete) {
		return !known ? "?" : complete ? "✔" : "✘";
	}

	private static void drawObjectiveStatus(GuiGraphicsExtractor graphics, Font font,
			int x, int y, int completeMask, int shownMask, int knownMask, int borderRight) {
		HudIcon[] icons = {HudIcon.MOUNTAIN, HudIcon.SNOWFLAKE, HudIcon.SKULL, HudIcon.LEAF};
		int entriesWidth = 0;
		int entries = 0;
		for (int index = 0; index < icons.length; index++) {
			if ((shownMask & (1 << index)) == 0) continue;
			entries++;
			entriesWidth += objectiveIconWidth(icons[index]) + 2 + objectiveMarkWidth(font);
		}
		if (entries == 0) return;
		int gap = Math.max(1, (borderRight - x - entriesWidth) / (entries + 1));
		int cursor = x + gap;
		for (int index = 0; index < icons.length; index++) {
			int bit = 1 << index;
			if ((shownMask & bit) == 0) continue;
			cursor = drawStatusIcon(graphics, icons[index], cursor, y);
			boolean known = (knownMask & bit) != 0;
			boolean complete = (completeMask & bit) != 0;
			cursor = drawStatusPart(graphics, font, mark(known, complete), cursor, y,
				!known ? 0xFFFFD24A : complete ? 0xFF55FF55 : 0xFFFF5555);
			cursor += gap;
		}
	}

	private static int drawStatusIcon(GuiGraphicsExtractor graphics, HudIcon icon, int x, int y) {
		drawIcon(graphics, x - objectiveIconLeft(icon), y - 1, icon, 0xFFFFFFFF);
		return x + objectiveIconWidth(icon) + 2;
	}

	private static int objectiveStatusWidth(Font font, int shownMask) {
		int width = 0;
		HudIcon[] icons = {HudIcon.MOUNTAIN, HudIcon.SNOWFLAKE, HudIcon.SKULL, HudIcon.LEAF};
		for (int index = 0; index < icons.length; index++) {
			if ((shownMask & (1 << index)) != 0) {
				width += 6 + objectiveIconWidth(icons[index]) + 2 + objectiveMarkWidth(font);
			}
		}
		return width;
	}

	private static int objectiveMarkWidth(Font font) {
		return Math.max(font.width("?"), Math.max(font.width("✘"), font.width("✔")));
	}

	private static int objectiveIconLeft(HudIcon icon) {
		return switch (icon) {
			case SNOWFLAKE -> 1;
			case LEAF -> 2;
			default -> 0;
		};
	}

	private static int objectiveIconWidth(HudIcon icon) {
		return switch (icon) {
			case MOUNTAIN -> 11;
			case SNOWFLAKE, SKULL -> 9;
			case LEAF -> 7;
			default -> iconWidth(icon);
		};
	}

	private static int drawStatusPart(GuiGraphicsExtractor graphics, Font font,
			String text, int x, int y, int colour) {
		drawText(graphics, font, Component.literal(text), x, y, colour);
		return x + font.width(text);
	}

	private static void drawText(GuiGraphicsExtractor graphics, Font font, Component component,
			int x, int y, int colour) {
		if (!PlayerNameStyle.drawIfPresent(graphics, font, component, x, y, colour))
			graphics.text(font, component, x, y, colour);
	}

	private static int valueWidth(Font font, Row row) {
		return font.width(value(row));
	}

	private static int labelWidth(Font font, Row row) {
		return font.width(row.kind() == Kind.CHECKED_BAR ? row.label() + " ✔" : row.label());
	}

	private static int iconValueWidth(Font font, List<IconValue> values) {
		int width = 0;
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) width += 5;
			width += iconWidth(values.get(i).icon()) + 2 + font.width(values.get(i).text());
		}
		return width;
	}

	private static void drawIconValue(GuiGraphicsExtractor graphics, Font font,
			List<IconValue> values, int x, int y, boolean rainbowCounts) {
		int cursor = x;
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) cursor += 5;
			IconValue value = values.get(i);
			// The nine-pixel sprites sit visually one pixel lower than Minecraft's
			// count glyphs when they share the text baseline.
			drawIcon(graphics, cursor, y - 1, value.icon(), value.colour());
			cursor += iconWidth(value.icon()) + 2;
			if ("✔".equals(value.text())) {
				graphics.text(font, Component.literal(value.text()), cursor, y, 0xFF55FF55);
			} else if (rainbowCounts) rainbowText(graphics, font, value.text(), cursor, y);
			else graphics.text(font, Component.literal(value.text()), cursor, y, 0xFFFFFFFF);
			cursor += font.width(value.text());
		}
	}

	private static int iconWidth(HudIcon icon) {
		return switch (icon) {
			case BIRD, MOUNTAIN, SNOWFLAKE, SKULL, LEAF -> 11;
			case BERRY, WORM, SEED_BAG, INCENSE, GEM -> 10;
		};
	}

	/** Compact sprites replace font-dependent Unicode while retaining each item's accent details. */
	private static void drawIcon(GuiGraphicsExtractor graphics, int x, int y,
			HudIcon icon, int colour) {
		int[] quads = ICON_QUADS.computeIfAbsent(new IconKey(icon, colour), HudPanel::iconQuads);
		GuiQuadBatchRenderState.submit(graphics, x, y, iconWidth(icon), 9, quads);
	}

	/** Converts a sprite to horizontal runs once per icon/colour combination. */
	private static int[] iconQuads(IconKey key) {
		HudIcon icon = key.icon();
		int colour = key.colour();
		String[] sprite = switch (icon) {
			case BIRD -> BIRD_ICON;
			case BERRY -> BERRY_ICON;
			case WORM -> WORM_ICON;
			case SEED_BAG -> SEED_BAG_ICON;
			case INCENSE -> INCENSE_ICON;
			case GEM -> GEM_ICON;
			case MOUNTAIN -> MOUNTAIN_ICON;
			case SNOWFLAKE -> SNOWFLAKE_ICON;
			case SKULL -> SKULL_ICON;
			case LEAF -> LEAF_ICON;
		};
		int shadow = shade(colour, 0.48f);
		int highlight = shade(colour, 1.30f);
		int medium = shade(colour, 0.72f);
		int wing = shade(colour, 1.18f);
		int dark = shade(colour, 0.28f);
		int[] quads = new int[450];
		int cursor = 0;
		for (int row = 0; row < sprite.length; row++) {
			String pixels = sprite[row];
			int runStart = 0;
			int runColour = 0;
			for (int column = 0; column <= pixels.length(); column++) {
				int pixel = column == pixels.length() ? 0 : objectivePixel(icon, pixels.charAt(column));
				if (pixel == Integer.MIN_VALUE) pixel = switch (pixels.charAt(column)) {
					case '#' -> icon == HudIcon.INCENSE ? 0xFF354B5E : colour;
					case 's' -> icon == HudIcon.INCENSE ? 0xFF0C151E : shadow;
					case 'h' -> icon == HudIcon.INCENSE ? 0xFF7897AA : highlight;
					case 'm' -> medium;
					case 'w' -> wing;
					case 'd' -> icon == HudIcon.INCENSE ? 0xFF1B2936 : dark;
					case 'c' -> 0xFF08CFD2;
					case 't' -> 0xFF63FFFF;
					case 'g' -> 0xFF65B84B;
					case 'b' -> 0xFF70401F;
					case 'o' -> 0xFFFFB33B;
					default -> 0;
				};
				if (pixel == runColour) continue;
				if (runColour != 0) {
					quads[cursor++] = runStart;
					quads[cursor++] = row;
					quads[cursor++] = column;
					quads[cursor++] = row + 1;
					quads[cursor++] = runColour;
				}
				runStart = column;
				runColour = pixel;
			}
		}
		return Arrays.copyOf(quads, cursor);
	}

	/** Fixed semantic palettes keep objective symbols recognizable under every theme. */
	private static int objectivePixel(HudIcon icon, char token) {
		return switch (icon) {
			case MOUNTAIN -> switch (token) {
				case 'l' -> 0xFFFFD09A;
				case 'o' -> 0xFFEF8A42;
				case 'g' -> 0xFFA85F3F;
				case 's' -> 0xFF5A3C32;
				case 'd' -> 0xFF2F211E;
				default -> 0;
			};
			case SKULL -> switch (token) {
				case 'l' -> 0xFFD6C3DF;
				case 'g' -> 0xFF8B7895;
				case 's' -> 0xFF483752;
				case 'd' -> 0xFF1C1325;
				case 'p' -> 0xFF45155F;
				default -> 0;
			};
			case SNOWFLAKE -> switch (token) {
				case 'l' -> 0xFFB9F3FF;
				case 'g' -> 0xFF65CFFF;
				case 'd' -> 0xFF318CCF;
				default -> 0;
			};
			case LEAF -> switch (token) {
				case 'l' -> 0xFFA9F05E;
				case 'g' -> 0xFF43C83F;
				case 'v' -> 0xFF279D36;
				case 's' -> 0xFF176F2D;
				default -> 0;
			};
			default -> Integer.MIN_VALUE;
		};
	}

	private static int shade(int colour, float multiplier) {
		int red = Math.clamp(Math.round(((colour >> 16) & 0xFF) * multiplier), 0, 255);
		int green = Math.clamp(Math.round(((colour >> 8) & 0xFF) * multiplier), 0, 255);
		int blue = Math.clamp(Math.round((colour & 0xFF) * multiplier), 0, 255);
		return colour & 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static Component value(Row row) {
		if (row.styledValue() != null) return row.styledValue();
		Component value = Component.literal(row.value());
		return row.kind() == Kind.BOLD_PAIR ? value.copy().withStyle(ChatFormatting.BOLD) : value;
	}

	public enum HudIcon { BIRD, BERRY, WORM, SEED_BAG, INCENSE, GEM, MOUNTAIN, SNOWFLAKE, SKULL, LEAF }
	private record IconKey(HudIcon icon, int colour) { }
	private record Layout(int width, int[] barQuads) { }

	public record IconValue(HudIcon icon, String text, int colour) { }

	private enum Kind {TITLE, OBJECTIVE_TITLE, TITLE_SUFFIX, SPARKLING_MODE_TITLE, TEXT, PAIR, COMPACT_PAIR, BOLD_PAIR,
		ICON_PAIR, RAINBOW_ICON_PAIR, STATUS_PAIR, RAINBOW_PAIR, BAR, CHECKED_BAR, BLANK}

	private record Row(Kind kind, String label, String value,
					   int labelColour, int valueColour, int current, int max,
					   Component styledValue, List<IconValue> icons) {
		private Row(Kind kind, String label, String value, int labelColour,
				int valueColour, int current, int max) {
			this(kind, label, value, labelColour, valueColour, current, max, null, List.of());
		}
		private Row(Kind kind, String label, String value, int labelColour,
				int valueColour, int current, int max, Component styledValue) {
			this(kind, label, value, labelColour, valueColour, current, max,
				styledValue, List.of());
		}
	}
}
