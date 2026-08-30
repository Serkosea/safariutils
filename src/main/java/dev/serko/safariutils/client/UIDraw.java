package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small drawing primitives shared by Safari Utils screens and HUDs. */
final class UIDraw {
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

	static void rainbowText(GuiGraphicsExtractor graphics, Font font,
			String text, int x, int y, float saturation) {
		float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
		int cursor = x;
		for (int i = 0; i < text.length(); i++) {
			String character = String.valueOf(text.charAt(i));
			graphics.text(font, Component.literal(character), cursor, y,
				rainbow(phase, i, text.length(), saturation));
			cursor += font.width(character);
		}
	}
}
