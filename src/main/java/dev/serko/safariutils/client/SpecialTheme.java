package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Global novelty themes unlocked with the Advanced category. */
public final class SpecialTheme {
	private static final long CYCLE_MILLIS = 4_000L;

	private SpecialTheme() {
	}

	public static boolean rainbow() {
		return ConfigManager.get().advanced.specialTheme == 1;
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, Component component,
						int x, int y, int fallbackColour) {
		if (!rainbow()) {
			graphics.text(font, component, x, y, fallbackColour);
			return;
		}
		rainbowText(graphics, font, component, x, y);
	}

	public static void rainbowText(GuiGraphicsExtractor graphics, Font font,
							   String text, int x, int y) {
		rainbowText(graphics, font, Component.literal(text), x, y);
	}

	/** Rainbow text which retains bold and any other style carried by the component. */
	public static void rainbowText(GuiGraphicsExtractor graphics, Font font,
							   Component component, int x, int y) {
		String text = component.getString();
		float phase = phase();
		int cursor = x;
		for (int i = 0; i < text.length(); i++) {
			Component character = Component.literal(String.valueOf(text.charAt(i)))
				.withStyle(component.getStyle());
			graphics.text(font, character, cursor, y,
				rainbowColour(phase, i, Math.max(1, text.length()), 0.5f, 1f));
			cursor += font.width(character);
		}
	}

	/** Small deterministic twinkles behind panel text, with no per-frame allocations. */
	public static void stars(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
		stars(graphics, left, top, width, height, 1f);
	}

	public static void stars(GuiGraphicsExtractor graphics, int left, int top,
						 int width, int height, float density) {
		if (!rainbow() || width < 8 || height < 8) return;
		int count = Math.clamp(Math.round(width * height / 1_300f * density),
			Math.round(8 * density), Math.round(72 * density));
		long now = System.currentTimeMillis();
		float phase = phase(now);
		for (int i = 0; i < count; i++) {
			long seed = mix(i * 0x9E3779B97F4A7C15L);
			long life = 650L + positive(seed >>> 17) % 1_050L;
			long offset = positive(seed) % life;
			long age = Math.floorMod(now + offset, life);
			long generation = Math.floorDiv(now + offset, life);
			long positionSeed = mix(seed ^ generation * 0xD1B54A32D192ED03L);
			float progress = age / (float) life;
			float alpha = 1f - Math.abs(progress * 2f - 1f);
			int x = left + 3 + (int) (positive(positionSeed >>> 7) % Math.max(1, width - 6));
			int y = top + 3 + (int) (positive(positionSeed >>> 29) % Math.max(1, height - 6));
			int size = 1 + (int) (positive(positionSeed >>> 43) % 3);
			int colour = rainbowColour(phase, i, count, 0.35f, 0.18f + alpha * 0.38f);
			drawStar(graphics, x, y, size, colour, (int) (positionSeed & 3));
		}
	}

	public static void border(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
		border(graphics, left, top, width, height, 2);
	}

	public static void border(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, int thickness) {
		int horizontal = 28;
		int vertical = Math.max(1, Math.round(horizontal * height / (float) width));
		int total = 2 * (horizontal + vertical);
		float phase = phase();
		for (int i = 0; i < horizontal; i++) {
			int x1 = left + width * i / horizontal;
			int x2 = left + width * (i + 1) / horizontal;
			graphics.fill(x1, top, x2, top + thickness, rainbowColour(phase, i, total, 0.55f, 1f));
			graphics.fill(left + width - (x2 - left), top + height - thickness,
				left + width - (x1 - left), top + height,
				rainbowColour(phase, horizontal + vertical + i, total, 0.55f, 1f));
		}
		for (int i = 0; i < vertical; i++) {
			int y1 = top + height * i / vertical;
			int y2 = top + height * (i + 1) / vertical;
			graphics.fill(left + width - thickness, y1, left + width, y2,
				rainbowColour(phase, horizontal + i, total, 0.55f, 1f));
			graphics.fill(left, top + height - (y2 - top), left + thickness, top + height - (y1 - top),
				rainbowColour(phase, 2 * horizontal + vertical + i, total, 0.55f, 1f));
		}
	}

	private static void drawStar(GuiGraphicsExtractor graphics, int x, int y,
							 int size, int colour, int shape) {
		graphics.fill(x - size, y, x + size + 1, y + 1, colour);
		graphics.fill(x, y - size, x + 1, y + size + 1, colour);
		if (shape >= 1 && size >= 2) {
			graphics.fill(x - 1, y - 1, x, y, colour);
			graphics.fill(x + 1, y - 1, x + 2, y, colour);
			graphics.fill(x - 1, y + 1, x, y + 2, colour);
			graphics.fill(x + 1, y + 1, x + 2, y + 2, colour);
		}
	}

	private static float phase() {
		return phase(System.currentTimeMillis());
	}

	private static float phase(long now) {
		return (now % CYCLE_MILLIS) / (float) CYCLE_MILLIS;
	}

	private static int rainbowColour(float phase, int part, int total,
								 float saturation, float alpha) {
		int rgb = java.awt.Color.HSBtoRGB((phase + part / (float) total) % 1f, saturation, 1f);
		return Math.clamp(Math.round(alpha * 255f), 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static long positive(long value) {
		return value & Long.MAX_VALUE;
	}
}
