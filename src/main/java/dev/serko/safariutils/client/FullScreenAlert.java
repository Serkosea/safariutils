package dev.serko.safariutils.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Draws the large celebration used for rare Safari events. */
public final class FullScreenAlert implements HudElement {

	private static final long DISPLAY_MILLIS = 5000;
	private static final long FADE_MILLIS = 1200;
	private static final float TITLE_SCALE = 4.7f;
	private static final float SUBTITLE_SCALE = 1.92f;
	private static final float SPARKLING_SUBJECT_SCALE = 3.0f;

	/** A sparkling critter. */
	public static final int SPARKLING = 0xFFD700;

	private static final int WHITE = 0xFFFFFF;
	private static final int[] STAR_PALETTE = createStarPalette();
	private static final int STAR_COUNT = 80;
	private static final long[] STAR_LIFE_MILLIS = createStarLifetimes();
	private static String headline;
	private static String subject;
	private static String where;
	private static int tint = SPARKLING;
	private static long shownAtMillis;

	/**
	 * Puts one on screen.
	 *
	 * @param call    the word across the middle, e.g. {@code SPARKLING!}
	 * @param subject what it is about, e.g. the species
	 * @param detail  where it is, or null when the position is not known
	 * @param colour  the wash and headline colour
	 */
	public static void show(String call, String subject, String detail, int colour) {
		headline = call;
		FullScreenAlert.subject = subject;
		where = detail;
		tint = colour;
		shownAtMillis = System.currentTimeMillis();
		if (colour == SPARKLING) AlertSounds.playSparklingCall(Minecraft.getInstance());
	}

	public static void clear() {
		headline = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (headline == null) return;

		long age = System.currentTimeMillis() - shownAtMillis;
		if (age > DISPLAY_MILLIS) {
			headline = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;

		int alpha = 0xFF;
		long fadeStart = DISPLAY_MILLIS - FADE_MILLIS;
		if (age > fadeStart) alpha = (int) (0xFF * (DISPLAY_MILLIS - age) / (double) FADE_MILLIS);

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean sparkling = tint == SPARKLING;

		// Keep the wash light enough that the game remains visible underneath it.
		int wash = (alpha / (sparkling ? 8 : 10)) << 24 | tint;
		graphics.fill(0, 0, width, height, wash);
		int band = Math.max(2, height / 90);
		if (sparkling) {
			drawRainbowFrame(graphics, width, height, band, alpha, age);
			drawSparkles(graphics, width, height, alpha, age);
		} else {
			graphics.fill(0, 0, width, band, (alpha << 24) | tint);
			graphics.fill(0, height - band, width, height, (alpha << 24) | tint);
		}

		Font font = client.font;
		graphics.pose().pushMatrix();
		graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
		int headlineX = (int) (width / (2.0 * TITLE_SCALE));
		int headlineY = (int) (height * 0.396 / TITLE_SCALE);
		if (sparkling) rainbowCenteredText(graphics, font, headline, headlineX, headlineY, alpha, age);
		else graphics.centeredText(font, Component.literal(headline), headlineX, headlineY,
			(alpha << 24) | tint);
		graphics.pose().popMatrix();

		float subjectScale = sparkling ? SPARKLING_SUBJECT_SCALE : SUBTITLE_SCALE;
		graphics.pose().pushMatrix();
		graphics.pose().scale(subjectScale, subjectScale);
		int subtitleY = (int) (height * 0.517 / subjectScale);
		int subjectColour = sparkling ? sparklingSubjectColour(subject) : WHITE;
		graphics.centeredText(font, Component.literal(subject),
			(int) (width / (2.0 * subjectScale)), subtitleY,
			(alpha << 24) | (subjectColour & 0xFFFFFF));
		if (!sparkling && where != null) {
			graphics.centeredText(font, Component.literal(where),
				(int) (width / (2.0 * subjectScale)), subtitleY + 12, (alpha << 24) | WHITE);
		}
		graphics.pose().popMatrix();
	}

	private static void drawRainbowFrame(GuiGraphicsExtractor graphics, int width, int height,
									 int thickness, int alpha, long age) {
		int segments = 48;
		float phase = (age % 3_000L) / 3_000f;
		for (int i = 0; i < segments; i++) {
			int colour = rainbow(alpha, phase + i / (float) segments);
			int x1 = width * i / segments;
			int x2 = width * (i + 1) / segments;
			int y1 = height * i / segments;
			int y2 = height * (i + 1) / segments;
			graphics.fill(x1, 0, x2, thickness, colour);
			graphics.fill(width - x2, height - thickness, width - x1, height, colour);
			graphics.fill(0, y1, thickness, y2, colour);
			graphics.fill(width - thickness, height - y2, width, height - y1, colour);
		}
	}

	private static void drawSparkles(GuiGraphicsExtractor graphics, int width, int height,
								 int alpha, long age) {
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		for (int i = 0; i < STAR_COUNT; i++) {
			// Stagger every slot so the whole field never resets together. Once a
			// star finishes fading, its cycle number changes both coordinate hashes,
			// respawning its replacement somewhere unrelated to the old position.
			long lifeMillis = STAR_LIFE_MILLIS[i];
			long staggeredAge = age + i * lifeMillis / STAR_COUNT;
			int cycle = (int) (staggeredAge / lifeMillis);
			double progress = (staggeredAge % lifeMillis) / (double) lifeMillis;
			int hashX = mix(i * 0x45D9F3B + cycle * 0x27D4EB2D + 0x27100001);
			int hashY = mix(i * 0x119DE1F3 + cycle * 0x165667B1 + 0x6A09E667);
			int x = Math.floorMod(hashX, safeWidth);
			int y = Math.floorMod(hashY, safeHeight);
			double wave = Math.sin(Math.PI * progress);
			int sparkleAlpha = (int) (alpha * wave);
			int sizeRoll = Math.floorMod(mix(hashX + hashY), 16);
			int size = sizeRoll == 0 ? 5 : sizeRoll == 1 ? 4
				: sizeRoll <= 4 ? 3 : sizeRoll <= 9 ? 2 : 1;
			// Nearly-white pastel hues echo the frame without turning the star field
			// into visual noise behind the much stronger headline gradient.
			int rgb = STAR_PALETTE[Math.floorMod(mix(hashY), STAR_PALETTE.length)];
			int colour = (sparkleAlpha << 24) | rgb;
			drawStar(graphics, x, y, size, Math.floorMod(mix(hashX ^ hashY), 3), colour);
		}
	}

	private static void drawStar(GuiGraphicsExtractor graphics, int x, int y,
								 int size, int shape, int colour) {
		switch (shape) {
			case 0 -> { // Classic four-point sparkle.
				graphics.fill(x - size, y, x + size + 1, y + 1, colour);
				graphics.fill(x, y - size, x + 1, y + size + 1, colour);
			}
			case 1 -> { // Continuous diagonal sparkle.
				for (int offset = -size; offset <= size; offset++) {
					graphics.fill(x + offset, y + offset, x + offset + 1, y + offset + 1, colour);
					graphics.fill(x + offset, y - offset, x + offset + 1, y - offset + 1, colour);
				}
			}
			default -> { // Fuller eight-point sparkle.
				graphics.fill(x - size, y, x + size + 1, y + 1, colour);
				graphics.fill(x, y - size, x + 1, y + size + 1, colour);
				for (int offset = -size; offset <= size; offset++) {
					graphics.fill(x + offset, y + offset, x + offset + 1, y + offset + 1, colour);
					graphics.fill(x + offset, y - offset, x + offset + 1, y - offset + 1, colour);
				}
			}
		}
	}

	private static int[] createStarPalette() {
		int[] palette = new int[18];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = java.awt.Color.HSBtoRGB(i / (float) palette.length, 0.16f, 1f) & 0xFFFFFF;
		}
		return palette;
	}

	private static long[] createStarLifetimes() {
		long[] lifetimes = new long[STAR_COUNT];
		for (int i = 0; i < lifetimes.length; i++) {
			lifetimes[i] = 350L + Math.floorMod(mix(i * 0x632BE5AB), 551);
		}
		return lifetimes;
	}

	private static int mix(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}

	private static int sparklingSubjectColour(String name) {
		var critter = dev.serko.safariutils.data.Critters.byName(name);
		return critter == null ? SPARKLING : 0xFF000000 | critter.rarity().colour();
	}

	private static void rainbowCenteredText(GuiGraphicsExtractor graphics, Font font, String text,
										int centerX, int y, int alpha, long age) {
		int x = centerX - font.width(text) / 2;
		float phase = (age % 2_400L) / 2_400f;
		for (int i = 0; i < text.length(); i++) {
			String character = String.valueOf(text.charAt(i));
			graphics.text(font, Component.literal(character), x, y,
				rainbow(alpha, phase + i / (float) text.length()));
			x += font.width(character);
		}
	}

	private static int rainbow(int alpha, float hue) {
		return (alpha << 24) | (java.awt.Color.HSBtoRGB(hue % 1f, 0.5f, 1f) & 0xFFFFFF);
	}
}
