package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.BooleanSupplier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small drawing primitives shared by SafariUtils screens and HUDs. */
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

	/** Draws rainbow text at a caller-controlled phase without changing the shared theme clock. */
	static void rainbowTextAtPhase(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation, int alpha, float phase) {
		int phaseBucket = Math.floorMod(Math.round(phase * RAINBOW_TEXT_PHASES),
			RAINBOW_TEXT_PHASES);
		Component rainbow = cachedRainbowComponent(font, component, x, saturation, phaseBucket);
		graphics.text(font, rainbow, x, y,
			Math.clamp(alpha, 0, 255) << 24 | 0xFFFFFF, false);
	}

	static Component rainbowComponent(Font font, String text, int x, float saturation) {
		return cachedRainbowComponent(font, Component.literal(text), x, saturation);
	}

	/** Updates a placeholder only when its animation phase or theme state changes. */
	static int updateRainbowHint(EditBox editor, Font font, String text,
			Component normal, int previousPhase) {
		return updateRainbowHint(editor, font, text, normal, previousPhase,
			SpecialTheme.rainbow());
	}

	static int updateRainbowHint(EditBox editor, Font font, String text,
			Component normal, int previousPhase, boolean rainbow) {
		if (!rainbow) {
			if (previousPhase >= 0) editor.setHint(normal);
			return -1;
		}
		int phase = RainbowColours.phaseBucket(RAINBOW_TEXT_PHASES);
		if (phase != previousPhase) {
			editor.setHint(rainbowComponent(font, text, editor.getScreenX(0), 0.24f));
		}
		return phase;
	}

	/** Gives editable text the same screen-positioned gradient as labels around it. */
	static void rainbowEditBox(EditBox editor, Font font) {
		rainbowEditBox(editor, font, SpecialTheme::rainbow);
	}

	static void rainbowEditBox(EditBox editor, Font font, BooleanSupplier rainbow) {
		editor.addFormatter((visibleText, sourceOffset) -> {
			if (!rainbow.getAsBoolean()) {
				return FormattedCharSequence.forward(visibleText, Style.EMPTY);
			}
			int x = editor.getScreenX(sourceOffset);
			return cachedRainbowComponent(font, Component.literal(visibleText), x, 0.5f)
				.getVisualOrderText();
		});
	}

	/** Keeps the native blinking insertion cursor on the same gradient as editable text. */
	static void updateRainbowCaret(EditBox editor, int fallbackColour) {
		updateRainbowCaret(editor, fallbackColour, SpecialTheme.rainbow());
	}

	static void updateRainbowCaret(EditBox editor, int fallbackColour, boolean rainbow) {
		if (editor == null) return;
		int colour = rainbow
			? rainbowAt(editor.getScreenX(editor.getCursorPosition()), 0.5f)
			: fallbackColour;
		editor.setTextColor(colour);
	}

	private static Component cachedRainbowComponent(Font font, Component component,
			int x, float saturation) {
		return cachedRainbowComponent(font, component, x, saturation,
			RainbowColours.phaseBucket(RAINBOW_TEXT_PHASES));
	}

	private static Component cachedRainbowComponent(Font font, Component component,
			int x, float saturation, int phaseBucket) {
		String text = component.getString();
		RainbowTextKey key = new RainbowTextKey(text, component.getStyle(),
			Math.floorMod(x, (int) RAINBOW_CYCLE_PIXELS),
			Math.round(saturation * 100f), phaseBucket);
		Component rainbow = RAINBOW_TEXT_CACHE.get(key);
		if (rainbow == null) {
			rainbow = rainbowComponent(font, component, x, saturation,
				phaseBucket / (float) RAINBOW_TEXT_PHASES);
			RAINBOW_TEXT_CACHE.put(key, rainbow);
		}
		return rainbow;
	}

	private static Component rainbowComponent(Font font, Component component,
			int x, float saturation, float phase) {
		String text = component.getString();
		MutableComponent rainbow = Component.empty();
		int cursor = x;
		for (int i = 0; i < text.length(); i++) {
			int colour = rainbowAt(phase, cursor, saturation) & 0xFFFFFF;
			Component character = Component.literal(String.valueOf(text.charAt(i)))
				.withStyle(component.getStyle())
				.withStyle(style -> style.withColor(colour));
			rainbow.append(character);
			cursor += font.width(character);
		}
		return rainbow;
	}
}
