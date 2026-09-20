package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small drawing primitives shared by Safari Utils screens and HUDs. */
final class UIDraw {
	/** Fixed spatial wavelength keeps adjacent or changing-length text on one gradient. */
	private static final float RAINBOW_CYCLE_PIXELS = 96f;
	private static final int RAINBOW_TEXT_PHASES = 50;
	private static final int RAINBOW_TEXT_CACHE_LIMIT = 4_096;
	private static final Map<RainbowTextKey, Component> RAINBOW_TEXT_CACHE =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<RainbowTextKey, Component> eldest) {
				return size() > RAINBOW_TEXT_CACHE_LIMIT;
			}
		};
	private record RainbowTextKey(String text, Style style, int xPhase,
			int saturation, int phaseBucket) { }

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
		return RainbowColours.phased(phase, offset, saturation, 1f);
	}

	static int rainbow(int part, int total, float saturation) {
		return RainbowColours.shared(part / (float) Math.max(1, total), saturation);
	}

	static int rainbowAt(float phase, int pixelX, float saturation) {
		float offset = Math.floorMod(pixelX, (int) RAINBOW_CYCLE_PIXELS)
			/ RAINBOW_CYCLE_PIXELS;
		return RainbowColours.phased(phase, offset, saturation, 1f);
	}

	static int rainbowAt(int pixelX, float saturation) {
		float offset = Math.floorMod(pixelX, (int) RAINBOW_CYCLE_PIXELS)
			/ RAINBOW_CYCLE_PIXELS;
		return RainbowColours.shared(offset, saturation);
	}

	static void rainbowText(GuiGraphicsExtractor graphics, Font font,
			String text, int x, int y, float saturation) {
		rainbowText(graphics, font, Component.literal(text), x, y, saturation);
	}

	/** Draws styled text against one screen-space rainbow, independent of string length. */
	static void rainbowText(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation) {
		rainbowText(graphics, font, component, x, y, saturation, 0xFF);
	}

	static void rainbowText(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation, int alpha) {
		rainbowText(graphics, font, component, x, y, saturation, alpha, false);
	}

	static void rainbowText(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation, int alpha, boolean shadow) {
		Component rainbow = cachedRainbowComponent(font, component, x, saturation);
		graphics.text(font, rainbow, x, y,
			Math.clamp(alpha, 0, 255) << 24 | 0xFFFFFF, shadow);
	}

	/** Gives editable text the same screen-positioned gradient as labels around it. */
	static void rainbowEditBox(EditBox editor, Font font) {
		editor.addFormatter((visibleText, sourceOffset) -> {
			if (!SpecialTheme.rainbow()) {
				return FormattedCharSequence.forward(visibleText, Style.EMPTY);
			}
			int x = editor.getScreenX(sourceOffset);
			return cachedRainbowComponent(font, Component.literal(visibleText), x, 0.5f)
				.getVisualOrderText();
		});
	}

	/** Keeps the native blinking insertion cursor on the same gradient as editable text. */
	static void updateRainbowCaret(EditBox editor, int fallbackColour) {
		if (editor == null) return;
		int colour = SpecialTheme.rainbow()
			? rainbowAt(editor.getScreenX(editor.getCursorPosition()), 0.5f)
			: fallbackColour;
		editor.setTextColor(colour);
	}

	private static Component cachedRainbowComponent(Font font, Component component,
			int x, float saturation) {
		String text = component.getString();
		RainbowTextKey key = new RainbowTextKey(text, component.getStyle(),
			Math.floorMod(x, (int) RAINBOW_CYCLE_PIXELS),
			Math.round(saturation * 100f), RainbowColours.phaseBucket(RAINBOW_TEXT_PHASES));
		Component rainbow = RAINBOW_TEXT_CACHE.get(key);
		if (rainbow == null) {
			rainbow = rainbowComponent(font, component, x, saturation);
			RAINBOW_TEXT_CACHE.put(key, rainbow);
		}
		return rainbow;
	}

	private static Component rainbowComponent(Font font, Component component,
			int x, float saturation) {
		String text = component.getString();
		MutableComponent rainbow = Component.empty();
		int cursor = x;
		for (int i = 0; i < text.length(); i++) {
			int colour = rainbowAt(cursor, saturation) & 0xFFFFFF;
			Component character = Component.literal(String.valueOf(text.charAt(i)))
				.withStyle(component.getStyle())
				.withStyle(style -> style.withColor(colour));
			rainbow.append(character);
			cursor += font.width(character);
		}
		return rainbow;
	}
}
