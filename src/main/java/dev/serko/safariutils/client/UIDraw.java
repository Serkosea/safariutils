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
    /** Slim luminous wave with subpixel fading and pinpoint glimmers; no particle allocations. */
    static void collectionProgress(GuiGraphicsExtractor graphics,int x,int y,int width,int height,int count,int total) {
        if(width<=0 || height<=0) return;
        graphics.fill(x,y,x+width,y+height,0x70402F60);
        int filled=total<=0?0:(int)((long)width*Math.clamp(count,0,total)/total);
        if(filled==0) return;
        if(SpecialTheme.rainbow()) SpecialTheme.bar(graphics,x,y,filled,height);
        else {
            int strips=Math.min(24,filled);
            for(int i=0;i<strips;i++) {
                float ratio=strips<=1?0f:i/(float)(strips-1);
                int red=Math.round(102+(181-102)*ratio);
                int green=Math.round(222+(138-222)*ratio);
                int blue=Math.round(206+(255-206)*ratio);
                graphics.fill(x+i*filled/strips,y,x+(i+1)*filled/strips,y+height,
                    0xFF000000|red<<16|green<<8|blue);
            }
        }
        long now=dev.serko.safariutils.state.MonotonicTime.nowMillis();
        double phase=(now%6000L)/6000.0;
        // Sample a genuine wave rather than a full-height brightness gradient.
        // Fractional row coverage crossfades adjacent pixels instead of snapping
        // the ribbon up/down. A dark underside keeps it readable on the pale fill.
        int segments=Math.min(192,filled);
        for(int i=0;i<segments;i++) {
            double position=(i+0.5)/segments;
            double wave=(height-1)*(0.5+0.45*Math.sin((position*1.75-phase)*Math.PI*2));
            int left=x+i*filled/segments, right=x+(i+1)*filled/segments;
            double crest=0.75+0.25*Math.sin((position-phase)*Math.PI*2);
            for(int row=0;row<height;row++) {
                double distance=Math.abs(row-wave);
                int light=(int)(210*crest*Math.max(0,1-distance/1.25));
                int shade=(int)(65*Math.max(0,1-Math.abs(row-wave-1.2)));
                if(shade>0) graphics.fill(left,y+row,right,y+row+1,(shade<<24)|0x38266B);
                if(light>0) graphics.fill(left,y+row,right,y+row+1,(light<<24)|0xF2FFFF);
            }
        }
        // Independent deterministic glimmers: each has its own lifetime, position
        // and brightness. Positions only change while faded out, never jitter each frame.
        for(int star=0;star<9;star++) {
            long period=2100L+star*317L;
            long clock=now+star*743L;
            long cycle=Math.floorDiv(clock,period);
            double life=Math.floorMod(clock,period)/(double)period;
            long seed=(cycle*0x9E3779B97F4A7C15L)^(star*0x632BE59BD9B4E019L);
            seed=(seed^(seed>>>30))*0xBF58476D1CE4E5B9L;
            seed=(seed^(seed>>>27))*0x94D049BB133111EBL;
            seed^=seed>>>31;
            int px=x+(int)Math.floorMod(seed,(long)filled);
            int cy=y+(int)Math.floorMod(seed>>>16,(long)height);
            double pulse=Math.pow(Math.sin(life*Math.PI),4);
            int alpha=(int)(235*pulse);
            if(alpha<4) continue;
            // A tiny bright core with faint lateral spill, never a large cross/block.
            graphics.fill(Math.max(x,px-1),cy,Math.min(x+filled,px+2),cy+1,(alpha/5<<24)|0xF6E9FF);
            graphics.fill(px,cy,px+1,cy+1,(alpha<<24)|0xFFFFFF);
        }
        int edgeAlpha=100+(int)(55*(1+Math.sin(phase*Math.PI*4)));
        graphics.fill(x+filled-1,y,x+filled,y+height,(edgeAlpha<<24)|0xEEFFFF);

    }

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
	private record RainbowTextKey(int theme, String text, Style style, int xPhase,
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
		Component rainbow = cachedThemeComponent(font, component, x, saturation,
			SpecialTheme.RAINBOW);
		graphics.text(font, rainbow, x, y,
			Math.clamp(alpha, 0, 255) << 24 | 0xFFFFFF, shadow);
	}

	static void specialText(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation) {
		specialText(graphics, font, component, x, y, saturation, SpecialTheme.mode());
	}

	static void specialText(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation, int theme) {
		Component themed = cachedThemeComponent(font, component, x, saturation, theme);
		graphics.text(font, themed, x, y, 0xFFFFFFFF);
	}

	/** Draws rainbow text at a caller-controlled phase without changing the shared theme clock. */
	static void rainbowTextAtPhase(GuiGraphicsExtractor graphics, Font font,
			Component component, int x, int y, float saturation, int alpha, float phase) {
		int phaseBucket = Math.floorMod(Math.round(phase * RAINBOW_TEXT_PHASES),
			RAINBOW_TEXT_PHASES);
		Component rainbow = cachedThemeComponent(font, component, x, saturation,
			phaseBucket, SpecialTheme.RAINBOW);
		graphics.text(font, rainbow, x, y,
			Math.clamp(alpha, 0, 255) << 24 | 0xFFFFFF, false);
	}

	static Component rainbowComponent(Font font, String text, int x, float saturation) {
		return cachedThemeComponent(font, Component.literal(text), x, saturation,
			SpecialTheme.RAINBOW);
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
		int theme = SpecialTheme.rainbow() ? SpecialTheme.mode() : SpecialTheme.RAINBOW;
		int phase = SpecialTheme.phaseBucket(theme, RAINBOW_TEXT_PHASES);
		if (phase != previousPhase) {
			editor.setHint(cachedThemeComponent(font, Component.literal(text),
				editor.getScreenX(0), 0.24f, theme));
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
			int theme = SpecialTheme.rainbow() ? SpecialTheme.mode() : SpecialTheme.RAINBOW;
			return cachedThemeComponent(font, Component.literal(visibleText), x, 0.5f, theme)
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
			? SpecialTheme.rainbow()
				? SpecialTheme.colourAt(editor.getScreenX(editor.getCursorPosition()), 0.5f)
				: rainbowAt(editor.getScreenX(editor.getCursorPosition()), 0.5f)
			: fallbackColour;
		editor.setTextColor(colour);
	}

	private static Component cachedThemeComponent(Font font, Component component,
			int x, float saturation, int theme) {
		return cachedThemeComponent(font, component, x, saturation,
			SpecialTheme.phaseBucket(theme, RAINBOW_TEXT_PHASES), theme);
	}

	private static Component cachedThemeComponent(Font font, Component component,
			int x, float saturation, int phaseBucket, int theme) {
		String text = component.getString();
		RainbowTextKey key = new RainbowTextKey(theme, text, component.getStyle(),
			Math.floorMod(x, (int) RAINBOW_CYCLE_PIXELS),
			Math.round(saturation * 100f), phaseBucket);
		Component rainbow = RAINBOW_TEXT_CACHE.get(key);
		if (rainbow == null) {
			rainbow = themedComponent(font, component, x, saturation,
				phaseBucket / (float) RAINBOW_TEXT_PHASES, theme);
			RAINBOW_TEXT_CACHE.put(key, rainbow);
		}
		return rainbow;
	}

	private static Component themedComponent(Font font, Component component,
			int x, float saturation, float phase, int theme) {
		String text = component.getString();
		MutableComponent rainbow = Component.empty();
		int cursor = x;
		for (int i = 0; i < text.length(); i++) {
			int colour = (theme == SpecialTheme.CONSTELLATION
				? SpecialTheme.colourAt(theme, cursor, saturation)
				: rainbowAt(phase, cursor, saturation)) & 0xFFFFFF;
			Component character = Component.literal(String.valueOf(text.charAt(i)))
				.withStyle(component.getStyle())
				.withStyle(style -> style.withColor(colour));
			rainbow.append(character);
			cursor += font.width(character);
		}
		return rainbow;
	}
}
