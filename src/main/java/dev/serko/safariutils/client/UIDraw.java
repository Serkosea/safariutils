package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small drawing primitives shared by Safari Utils screens and HUDs. */
final class UIDraw {
	/** Fixed spatial wavelength keeps adjacent or changing-length text on one gradient. */
	private static final float RAINBOW_CYCLE_PIXELS = 96f;

	private UIDraw() {
	}

	static void outline(GuiGraphicsExtractor graphics, int x, int y,
			int width, int height, int colour) {
		if (width <= 0 || height <= 0) return;
		graphics.fill(x, y, x + width, y + 1, colour);
		graphics.fill(x, y + height - 1, x + width, y + height, colour);
		graphics.fill(x, y + 1, x + 1, y + height - 1, colour);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, colour);
	}

	static int rainbow(float phase, int part, int total, float saturation) {
		float offset = part / (float) Math.max(1, total);
		return 0xFF000000 | (java.awt.Color.HSBtoRGB(
			(phase + offset) % 1f, saturation, 1f) & 0xFFFFFF);
	}

	static int rainbowAt(float phase, int pixelX, float saturation) {
		float offset = Math.floorMod(pixelX, (int) RAINBOW_CYCLE_PIXELS)
			/ RAINBOW_CYCLE_PIXELS;
		return 0xFF000000 | (java.awt.Color.HSBtoRGB(
			(phase + offset) % 1f, saturation, 1f) & 0xFFFFFF);
	}

	static void rainbowText(GuiGraphicsExtractor graphics, Font font,
			String text, int x, int y, float saturation) {
		rainbowText(graphics, font, Component.literal(text), x, y, saturation);
	}

	/** Draws styled text against one screen-space rainbow, independent of string length. */
	static void rainbowText(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation) {
		String text = component.getString();
		float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
		int cursor = x;
		for (int i = 0; i < text.length(); i++) {
			Component character = Component.literal(String.valueOf(text.charAt(i)))
				.withStyle(component.getStyle());
			graphics.text(font, character, cursor, y, rainbowAt(phase, cursor, saturation));
			cursor += font.width(character);
		}
	}
}
