package dev.serko.safariutils.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** Draws the large celebration used for rare Safari events. */
public final class FullScreenAlert implements HudElement {

	private static final long DISPLAY_MILLIS = 5000;
	private static final long EXTREME_DISPLAY_MILLIS = 7000;
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
	private static int sparklingIntensity = -1;
	private static long geometryFrame = Long.MIN_VALUE;
	private static int geometryWidth;
	private static int geometryHeight;
	private static int geometryIntensity = Integer.MIN_VALUE;
	private static final QuadCollector GEOMETRY = new QuadCollector(50_000);
	private static int geometryLength;

	/**
	 * Puts one on screen.
	 *
	 * @param call    the word across the middle, e.g. {@code SPARKLING!}
	 * @param subject what it is about, e.g. the species
	 * @param detail  where it is, or null when the position is not known
	 * @param colour  the wash and headline colour
	 */
	public static void show(String call, String subject, String detail, int colour) {
		show(call, subject, detail, colour, null);
	}

	private static void show(String call, String subject, String detail, int colour,
			Integer forcedIntensity) {
		headline = call;
		FullScreenAlert.subject = subject;
		where = detail;
		tint = colour;
		shownAtMillis = System.currentTimeMillis();
		geometryFrame = Long.MIN_VALUE;
		sparklingIntensity = colour == SPARKLING ? Math.clamp(forcedIntensity != null
			? forcedIntensity : ConfigManager.get().sparkling.specialSparklingIntensity, 0, 3) : -1;
		if (colour == SPARKLING) {
			if (sparklingIntensity > 0) {
				AlertSounds.playExtremeSparklingCall(Minecraft.getInstance());
			} else {
				AlertSounds.playSparklingCall(Minecraft.getInstance());
			}
		}
	}

	/** Settings preview uses the currently selected intensity. */
	public static void testSparklingCatch() {
		ClientCompat.setScreen(null);
		show("SPARKLING!", "Rockmite", null, SPARKLING,
			ConfigManager.get().sparkling.specialSparklingIntensity);
	}

	public static void clear() {
		headline = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (headline == null) return;

		long age = System.currentTimeMillis() - shownAtMillis;
		long displayMillis = sparklingIntensity > 0
			? EXTREME_DISPLAY_MILLIS + (sparklingIntensity - 1) * 350L : DISPLAY_MILLIS;
		if (age > displayMillis) {
			headline = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;

		int alpha = 0xFF;
		long fadeStart = displayMillis - FADE_MILLIS;
		if (age > fadeStart) alpha = (int) (0xFF * (displayMillis - age) / (double) FADE_MILLIS);

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean sparkling = tint == SPARKLING;

		// Keep the wash light enough that the game remains visible underneath it.
		int wash = (alpha / (sparkling ? 8 : 10)) << 24 | tint;
		if (sparklingIntensity > 0) {
			long hueCycle = Math.max(420L, 1_020L - sparklingIntensity * 150L);
			float hue = (age % hueCycle) / (float) hueCycle;
			int pulse = (int) (30 + sparklingIntensity * 6
				+ (36 + sparklingIntensity * 5) * (0.5 + 0.5 * Math.sin(age * Math.PI / 90.0)));
			wash = (Math.min(alpha, pulse) << 24)
				| (java.awt.Color.HSBtoRGB(hue, 0.72f, 1f) & 0xFFFFFF);
		}
		graphics.fill(0, 0, width, height, wash);
		int band = Math.max(2, height / 90);
		if (sparkling) {
			// Alert geometry is intentionally animated at the shared 25 FPS visual clock.
			// High-refresh clients can reuse the same compact batch instead of rebuilding
			// hundreds of sparkles several times between visible animation changes.
			long frame = age / RainbowColours.FRAME_MILLIS;
			if (geometryFrame != frame || geometryWidth != width || geometryHeight != height
					|| geometryIntensity != sparklingIntensity) {
				long sampledAge = frame * RainbowColours.FRAME_MILLIS;
				QuadCollector quads = GEOMETRY;
				quads.reset();
				drawRainbowFrame(quads, width, height, band, alpha, sampledAge);
				if (sparklingIntensity > 0) {
					drawIntenseLayer(quads, width, height, alpha, sampledAge,
						sparklingIntensity);
				} else drawSparkles(quads, width, height, alpha, sampledAge);
				geometryLength = quads.size();
				geometryFrame = frame;
				geometryWidth = width;
				geometryHeight = height;
				geometryIntensity = sparklingIntensity;
			}
			GuiQuadBatchRenderState.submit(graphics, 0, 0, width, height,
				GEOMETRY.values(), geometryLength);
		} else {
			graphics.fill(0, 0, width, band, (alpha << 24) | tint);
			graphics.fill(0, height - band, width, height, (alpha << 24) | tint);
		}

		Font font = client.font;
		graphics.pose().pushMatrix();
		float titleScale = sparklingIntensity > 0
			? TITLE_SCALE + 0.65f + (sparklingIntensity - 1) * 0.08f
				+ 0.28f * (float) Math.sin(age * Math.PI / 180.0)
			: TITLE_SCALE;
		graphics.pose().scale(titleScale, titleScale);
		int headlineX = (int) (width / (2.0 * titleScale));
		int headlineY = (int) (height * 0.396 / titleScale);
		if (sparkling) rainbowCenteredText(graphics, font, headline, headlineX, headlineY, alpha);
		else graphics.centeredText(font, Component.literal(headline), headlineX, headlineY,
			(alpha << 24) | tint);
		graphics.pose().popMatrix();

		float subjectScale = sparkling ? SPARKLING_SUBJECT_SCALE : SUBTITLE_SCALE;
		if (sparklingIntensity > 0) subjectScale += 0.42f + (sparklingIntensity - 1) * 0.05f
			+ 0.18f * (float) Math.sin(age * Math.PI / 210.0);
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

		if (sparkling && sparklingIntensity > 0) {
			String callout = "✦  SPARKLING CAPTURE  ✦";
			int calloutY = Math.max(12, height / 7);
			rainbowCenteredText(graphics, font, callout, width / 2, calloutY, alpha);
			rainbowCenteredText(graphics, font, "✦  SPARKLING CAPTURE  ✦",
				width / 2, height - calloutY - font.lineHeight, alpha);
		}
	}

	/** Diverse but bounded geometry for the explicitly confirmed celebrations. */
	private static void drawIntenseLayer(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		float phase = (age % 1_500L) / 1_500f;
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		int stars = 92 + intensity * 30;
		for (int i = 0; i < stars; i++) {
			long life = 180L + Math.floorMod(mix(i * 0x45D9F3B), 420);
			long shifted = age + i * 37L;
			int cycle = (int) (shifted / life);
			double progress = (shifted % life) / (double) life;
			int hx = mix(i * 0x27D4EB2D + cycle * 0x165667B1);
			int hy = mix(i * 0x119DE1F3 + cycle * 0x632BE5AB);
			int x = Math.floorMod(hx, safeWidth);
			int y = Math.floorMod(hy, safeHeight);
			int sparkleAlpha = (int) (alpha * Math.sin(Math.PI * progress));
			int size = 1 + Math.floorMod(mix(hx ^ hy), 5);
			int colour = rainbow(sparkleAlpha, phase + i / (float) stars);
			drawStar(quads, x, y, size, Math.floorMod(hx, 3), colour);
		}

		// Angled comet trails replace the old nested rectangular frames. Each trail
		// is a handful of tiny quads in the same 25 FPS batch as the star field.
		int streaks = 18 + intensity * 8;
		for (int i = 0; i < streaks; i++) {
			int hash = mix(i * 0x6A09E667 + (int) (age / 65L));
			int x = Math.floorMod(hash, safeWidth);
			int y = Math.floorMod(mix(hash), safeHeight);
			int colour = rainbow(Math.min(alpha, 225), phase + i / (float) streaks);
			int direction = (hash & 1) == 0 ? 1 : -1;
			int length = 3 + Math.floorMod(hash >>> 8, 5 + intensity);
			for (int step = 0; step < length; step++) {
				int px = x - step;
				int py = y + direction * step;
				quads.add(px, py, px + Math.max(1, 3 - step / 3), py + 1, colour);
			}
		}

		// Pulsing radial bursts make each higher level feel stronger without adding
		// another full-screen border. Their bounded point count is resolution-independent.
		int rays = 10 + intensity * 4;
		int points = 5 + intensity * 2;
		double turn = age * 0.0014;
		double pulse = 0.35 + 0.65 * ((age % 900L) / 900.0);
		int centreX = width / 2;
		int centreY = height * 9 / 20;
		int radius = Math.max(18, Math.min(width, height) / 4);
		for (int ray = 0; ray < rays; ray++) {
			double angle = turn + ray * Math.PI * 2.0 / rays;
			int colour = rainbow(Math.min(alpha, 235), phase + ray / (float) rays);
			for (int point = 1; point <= points; point++) {
				double distance = radius * pulse * point / points;
				int x = centreX + (int) Math.round(Math.cos(angle) * distance);
				int y = centreY + (int) Math.round(Math.sin(angle) * distance * 0.58);
				int size = point == points ? 2 : 1;
				drawStar(quads, x, y, size, ray % 3, colour);
			}
		}

		// Orbiting accent stars distinguish the upper levels from a denser version
		// of the same effect. Level three gains a second counter-rotating orbit.
		drawOrbit(quads, centreX, centreY, Math.max(28, radius * 3 / 5),
			8 + intensity * 2, turn * 1.35, alpha, phase);
		if (intensity >= 3) {
			drawOrbit(quads, centreX, centreY, Math.max(36, radius * 4 / 5),
			14, -turn, alpha, phase + 0.5f);
		}

		if (intensity >= 2) drawConfetti(quads, width, height, alpha, age, intensity);
	}

	private static void drawOrbit(QuadCollector quads, int centreX, int centreY, int radius,
			int count, double turn, int alpha, float phase) {
		for (int i = 0; i < count; i++) {
			double angle = turn + i * Math.PI * 2.0 / count;
			int x = centreX + (int) Math.round(Math.cos(angle) * radius);
			int y = centreY + (int) Math.round(Math.sin(angle) * radius * 0.42);
			drawStar(quads, x, y, 2 + (i & 1), i % 3,
				rainbow(Math.min(alpha, 235), phase + i / (float) count));
		}
	}

	private static void drawConfetti(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 26 + intensity * 12;
		for (int i = 0; i < count; i++) {
			int hash = mix(i * 0x632BE5AB);
			int x = Math.floorMod(hash, Math.max(1, width));
			int speed = 2 + Math.floorMod(hash >>> 7, 5);
			int y = Math.floorMod((int) (age / 18L) * speed + mix(hash), Math.max(1, height + 18)) - 9;
			int length = 2 + Math.floorMod(hash >>> 13, 4);
			int colour = rainbow(Math.min(alpha, 220), i / (float) count + age / 2_000f);
			if ((hash & 1) == 0) quads.add(x, y, x + 1, y + length, colour);
			else quads.add(x, y, x + length, y + 1, colour);
		}
	}

	private static void drawRainbowFrame(QuadCollector quads, int width, int height,
									 int thickness, int alpha, long age) {
		int segments = 48;
		float phase = (age % 3_000L) / 3_000f;
		for (int i = 0; i < segments; i++) {
			int colour = rainbow(alpha, phase + i / (float) segments);
			int x1 = width * i / segments;
			int x2 = width * (i + 1) / segments;
			int y1 = height * i / segments;
			int y2 = height * (i + 1) / segments;
			quads.add(x1, 0, x2, thickness, colour);
			quads.add(width - x2, height - thickness, width - x1, height, colour);
			quads.add(0, y1, thickness, y2, colour);
			quads.add(width - thickness, height - y2, width, height - y1, colour);
		}
	}

	private static void drawSparkles(QuadCollector quads, int width, int height,
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
			drawStar(quads, x, y, size, Math.floorMod(mix(hashX ^ hashY), 3), colour);
		}
	}

	private static void drawStar(QuadCollector quads, int x, int y,
								 int size, int shape, int colour) {
		switch (shape) {
			case 0 -> { // Classic four-point sparkle.
				quads.add(x - size, y, x + size + 1, y + 1, colour);
				quads.add(x, y - size, x + 1, y + size + 1, colour);
			}
			case 1 -> { // Continuous diagonal sparkle.
				for (int offset = -size; offset <= size; offset++) {
					quads.add(x + offset, y + offset, x + offset + 1, y + offset + 1, colour);
					quads.add(x + offset, y - offset, x + offset + 1, y - offset + 1, colour);
				}
			}
			default -> { // Fuller eight-point sparkle.
				quads.add(x - size, y, x + size + 1, y + 1, colour);
				quads.add(x, y - size, x + 1, y + size + 1, colour);
				for (int offset = -size; offset <= size; offset++) {
					quads.add(x + offset, y + offset, x + offset + 1, y + offset + 1, colour);
					quads.add(x + offset, y - offset, x + offset + 1, y - offset + 1, colour);
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
										int centerX, int y, int alpha) {
		int x = centerX - font.width(text) / 2;
		UIDraw.rainbowText(graphics, font, Component.literal(text), x, y, 0.5f, alpha);
	}

	private static int rainbow(int alpha, float hue) {
		return (alpha << 24) | (java.awt.Color.HSBtoRGB(hue % 1f, 0.5f, 1f) & 0xFFFFFF);
	}

	/** Primitive builder used only for the active frame, avoiding one GUI state per sparkle. */
	private static final class QuadCollector {
		private int[] values;
		private int size;

		private QuadCollector(int initialInts) {
			values = new int[initialInts];
		}

		private void add(int x0, int y0, int x1, int y1, int colour) {
			if (x1 <= x0 || y1 <= y0) return;
			if (size + 5 > values.length) values = Arrays.copyOf(values, values.length * 2);
			values[size++] = x0;
			values[size++] = y0;
			values[size++] = x1;
			values[size++] = y1;
			values[size++] = colour;
		}

		private void reset() {
			size = 0;
		}

		private int[] values() {
			return values;
		}

		private int size() {
			return size;
		}
	}
}
