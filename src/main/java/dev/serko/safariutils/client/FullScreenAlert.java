package dev.serko.safariutils.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

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
	private static String headline;
	private static String subject;
	private static String where;
	private static int tint = SPARKLING;
	private static long shownAtMillis;
	private static SparklingAlertStyle sparklingStyle;
	private static boolean sparklingCustom;
	private static long geometryFrame = Long.MIN_VALUE;
	private static int geometryWidth;
	private static int geometryHeight;
	private static SparklingAlertStyle geometryCachedStyle;
	private static final QuadCollector GEOMETRY = new QuadCollector(120_000);
	private static int geometryLength;
	private static final QuadCollector PREVIEW_GEOMETRY = new QuadCollector(120_000);
	private static long previewFrame = Long.MIN_VALUE;
	private static int previewWidth;
	private static int previewHeight;
	private static SparklingAlertStyle previewCachedStyle;
	private static int previewGeometryLength;
	private static long previewStartedAt = System.currentTimeMillis();
	private static long previewPausedAge;
	private static boolean previewPaused;
	private static String cachedCalloutSource;
	private static int cachedCalloutLevel = -1;
	private static String cachedCalloutText;

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
		int choice = Math.clamp(forcedIntensity != null
			? forcedIntensity : ConfigManager.get().sparkling.specialSparklingIntensity,
			0, SparklingAlertStyle.CUSTOM_INDEX);
		sparklingCustom = colour == SPARKLING && choice == SparklingAlertStyle.CUSTOM_INDEX;
		sparklingStyle = colour == SPARKLING
			? choice == SparklingAlertStyle.CUSTOM_INDEX
				? SparklingAlertStyle.custom() : SparklingAlertStyle.preset(choice)
			: null;
		if (colour == SPARKLING) {
			AlertSounds.playSparklingTheme(Minecraft.getInstance(), sparklingStyle.soundSong(),
				sparklingStyle.soundTheme(),
				sparklingStyle.durationSeconds(), sparklingStyle.soundVolumePercent());
		}
	}

	/** Settings preview uses the currently selected intensity. */
	public static void testSparklingCatch() {
		ClientCompat.setScreen(null);
		show("SPARKLING!", "Rockmite", null, SPARKLING,
			ConfigManager.get().sparkling.specialSparklingIntensity);
	}

	/** Draws the custom recipe in-place without starting sounds or a global alert. */
	public static void preview(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		SparklingAlertStyle style = SparklingAlertStyle.custom();
		long rawAge = previewPaused ? previewPausedAge : System.currentTimeMillis() - previewStartedAt;
		long age = Math.floorMod(rawAge, Math.max(1L, style.displayMillis()));
		int background = 0xFF080B13;
		graphics.fill(x, y, x + width, y + height, background);
		int pulseAlpha = style.pulse() == 0 ? 20 : 22 + style.pulse() * 8;
		int wash = (pulseAlpha << 24) | (RainbowColours.phased(0f,
			(age % 1_800L) / 1_800f, 0.65f, 1f) & 0xFFFFFF);
		graphics.fill(x, y, x + width, y + height, wash);

		long frame = age / RainbowColours.FRAME_MILLIS;
		if (previewFrame != frame || previewWidth != width || previewHeight != height
				|| previewCachedStyle != style) {
			PREVIEW_GEOMETRY.reset();
			long previewAge = Math.floorMod(frame * RainbowColours.FRAME_MILLIS,
				Math.max(1L, style.displayMillis()));
			drawEffects(PREVIEW_GEOMETRY, width, height, Math.max(1, height / 55),
				timedEffectAlpha(0xE8, previewAge, style.displayMillis(),
					ConfigManager.get().sparkling.customAlertTimingMode), previewAge, style,
				2);
			previewGeometryLength = PREVIEW_GEOMETRY.size();
			previewFrame = frame;
			previewWidth = width;
			previewHeight = height;
			previewCachedStyle = style;
		}
		GuiQuadBatchRenderState.submit(graphics, x, y, width, height,
			PREVIEW_GEOMETRY.values(), previewGeometryLength,
			new ScreenRectangle(x, y, width, height));
		Font font = Minecraft.getInstance().font;
		String previewTitle = "SPARKLING!";
		int titleX = x + (width - font.width(previewTitle)) / 2
			+ textShakeOffset(age, style.textShake(), 0x51A7);
		float textPhase = textGradientPhase(age, style.textGradientSpeed());
		UIDraw.rainbowTextAtPhase(graphics, font, Component.literal(previewTitle), titleX,
			y + height / 2 - 11 + textShakeOffset(age, style.textShake(), 0x27D4),
			0.42f, 0xF0, textPhase);
		String previewSubject = "Rockmite";
		int subjectX = x + (width - font.width(previewSubject)) / 2
			+ textShakeOffset(age, style.textShake(), 0x6A09);
		UIDraw.rainbowTextAtPhase(graphics, font, Component.literal(previewSubject), subjectX,
			y + height / 2 + 5 + textShakeOffset(age, style.textShake(), 0x119D),
			0.24f, 0xD8, textPhase);
		drawCaptureCallout(graphics, font, x, y, width, height, 0xE8, age, style, true);
	}

	public static void restartPreview() {
		previewStartedAt = System.currentTimeMillis();
		previewPausedAge = 0;
		previewPaused = false;
		previewFrame = Long.MIN_VALUE;
	}

	public static void restartPreviewPaused() {
		restartPreview();
		previewPaused = true;
	}

	public static void togglePreview() {
		if (previewPaused) {
			previewStartedAt = System.currentTimeMillis() - previewPausedAge;
			previewPaused = false;
		} else {
			previewPausedAge = System.currentTimeMillis() - previewStartedAt;
			previewPaused = true;
		}
		previewFrame = Long.MIN_VALUE;
	}

	public static boolean previewPaused() {
		return previewPaused;
	}

	public static long previewAgeMillis() {
		return previewPaused ? previewPausedAge : System.currentTimeMillis() - previewStartedAt;
	}

	public static float previewProgress(long duration) {
		long rawAge = previewPaused ? previewPausedAge : System.currentTimeMillis() - previewStartedAt;
		long age = ConfigManager.get().sparkling.customAlertPreviewLoop
			? Math.floorMod(rawAge, Math.max(1L, duration)) : Math.min(rawAge, duration);
		return Math.clamp(age / (float) Math.max(1L, duration), 0f, 1f);
	}

	public static void seekPreview(float progress, long duration) {
		long age = Math.round(Math.clamp(progress, 0f, 1f) * Math.max(1L, duration));
		previewPausedAge = age;
		previewStartedAt = System.currentTimeMillis() - age;
		previewFrame = Long.MIN_VALUE;
	}

	public static void clear() {
		headline = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (headline == null) return;

		long age = System.currentTimeMillis() - shownAtMillis;
		long displayMillis = sparklingStyle != null
			? sparklingStyle.displayMillis() : DISPLAY_MILLIS;
		if (age > displayMillis) {
			headline = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;

		int alpha = 0xFF;
		long fadeMillis = Math.min(FADE_MILLIS, Math.max(200L, displayMillis / 3L));
		long fadeStart = displayMillis - fadeMillis;
		if (age > fadeStart) alpha = (int) (0xFF * (displayMillis - age) / (double) fadeMillis);

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean sparkling = tint == SPARKLING;

		// Keep the wash light enough that the game remains visible underneath it.
		int wash = (alpha / 10) << 24 | tint;
		if (sparkling) {
			int pulseLevel = sparklingStyle.pulse();
			long hueCycle = pulseLevel == 0 ? 3_600L
				: Math.max(480L, 1_180L - pulseLevel * 130L);
			float hue = (age % hueCycle) / (float) hueCycle;
			int pulse = pulseLevel == 0 ? 22 : (int) (18 + pulseLevel * 7
				+ (18 + pulseLevel * 7) * (0.5 + 0.5 * Math.sin(age * Math.PI / 120.0)));
			wash = (Math.min(alpha, pulse) << 24)
				| (RainbowColours.phased(0f, hue, 0.72f, 1f) & 0xFFFFFF);
		}
		graphics.fill(0, 0, width, height, wash);
		int band = Math.max(2, height / 90);
		if (sparkling) {
			// Alert geometry is animated at the shared 40 FPS visual clock.
			// High-refresh clients can reuse the same compact batch instead of rebuilding
			// hundreds of sparkles several times between visible animation changes.
			long frame = age / RainbowColours.FRAME_MILLIS;
			if (geometryFrame != frame || geometryWidth != width || geometryHeight != height
					|| geometryCachedStyle != sparklingStyle) {
				long sampledAge = frame * RainbowColours.FRAME_MILLIS;
				QuadCollector quads = GEOMETRY;
				quads.reset();
				int effectAlpha = sparklingCustom ? timedEffectAlpha(alpha, sampledAge,
					sparklingStyle.displayMillis(), ConfigManager.get().sparkling.customAlertTimingMode)
					: alpha;
				drawEffects(quads, width, height, band, effectAlpha, sampledAge, sparklingStyle, 2);
				geometryLength = quads.size();
				geometryFrame = frame;
				geometryWidth = width;
				geometryHeight = height;
				geometryCachedStyle = sparklingStyle;
			}
			GuiQuadBatchRenderState.submit(graphics, 0, 0, width, height,
				GEOMETRY.values(), geometryLength);
		} else {
			graphics.fill(0, 0, width, band, (alpha << 24) | tint);
			graphics.fill(0, height - band, width, height, (alpha << 24) | tint);
		}

		Font font = client.font;
		graphics.pose().pushMatrix();
		float titleScale = TITLE_SCALE;
		if (sparkling) graphics.pose().translate(
			textShakeOffset(age, sparklingStyle.textShake(), 0x51A7),
			textShakeOffset(age, sparklingStyle.textShake(), 0x27D4));
		graphics.pose().scale(titleScale, titleScale);
		int headlineX = (int) (width / (2.0 * titleScale));
		int headlineY = (int) (height * 0.396 / titleScale);
		if (sparkling) rainbowCenteredText(graphics, font, headline, headlineX, headlineY,
			alpha, textGradientPhase(age, sparklingStyle.textGradientSpeed()));
		else graphics.centeredText(font, Component.literal(headline), headlineX, headlineY,
			(alpha << 24) | tint);
		graphics.pose().popMatrix();

		float subjectScale = sparkling ? SPARKLING_SUBJECT_SCALE : SUBTITLE_SCALE;
		graphics.pose().pushMatrix();
		if (sparkling) graphics.pose().translate(
			textShakeOffset(age, sparklingStyle.textShake(), 0x6A09),
			textShakeOffset(age, sparklingStyle.textShake(), 0x119D));
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

		if (sparkling) drawCaptureCallout(graphics, font, 0, 0, width, height,
			alpha, age, sparklingStyle, sparklingCustom);
	}

	private static void drawCaptureCallout(GuiGraphicsExtractor graphics, Font font,
			int x, int y, int width, int height, int alpha, long age, SparklingAlertStyle style,
			boolean custom) {
		if (style.callout() == 0 || height < 52) return;
		String setting = custom
			? ConfigManager.get().sparkling.customAlertCalloutText : "SPARKLING CAPTURE";
		String configured = setting == null ? "" : setting.trim();
		if (configured.isEmpty()) return;
		String callout = calloutText(configured, style.callout());
		int inset = Math.max(3, height / 7);
		int shakeX = textShakeOffset(age, style.textShake(), 0x45D9);
		int shakeY = textShakeOffset(age, style.textShake(), 0x632B);
		float phase = textGradientPhase(age, style.textGradientSpeed());
		rainbowCenteredText(graphics, font, callout, x + width / 2 + shakeX,
			y + inset + shakeY, alpha, phase);
		if (style.callout() >= 2) {
			rainbowCenteredText(graphics, font, callout, x + width / 2 - shakeX,
				y + height - inset - font.lineHeight - shakeY, alpha, phase);
		}
	}

	private static String calloutText(String configured, int level) {
		if (level != cachedCalloutLevel || !configured.equals(cachedCalloutSource)) {
			cachedCalloutSource = configured;
			cachedCalloutLevel = level;
			cachedCalloutText = switch (level) {
				case 1, 2 -> "✦  " + configured + "  ✦";
				case 3 -> "✦ ✦  " + configured + "  ✦ ✦";
				default -> "✦ ✦ ✦  " + configured + "  ✦ ✦ ✦";
			};
		}
		return cachedCalloutText;
	}

	/** Builds one bounded batch for both production alerts and the live editor preview. */
	private static void drawEffects(QuadCollector quads, int width, int height, int band,
			int alpha, long age, SparklingAlertStyle style, int performanceBudget) {
		quads.setLimit(switch (Math.clamp(performanceBudget, 0, 2)) {
			case 0 -> 70_000;
			case 1 -> 110_000;
			default -> 150_000;
		});
		if (alpha <= 0) return;
		if (style.frame() > 0) {
			drawRainbowFrame(quads, width, height,
				Math.max(1, band + (style.frame() - 1) / 2), alpha, age);
		}
		drawIntenseLayer(quads, width, height, alpha, age, style);
		if (style.ribbons() > 0) drawRibbons(quads, width, height, alpha, age, style.ribbons());
		if (style.corners() > 0) drawCornerBursts(quads, width, height, alpha, age, style.corners());
		if (style.rays() > 0) drawLightRays(quads, width, height, alpha, age, style.rays());
		if (style.shockwaves() > 0) drawShockwaves(quads, width, height, alpha, age, style.shockwaves());
		if (style.glitterWaves() > 0) drawGlitterWaves(quads, width, height, alpha, age, style.glitterWaves());
		if (style.prisms() > 0) drawPrismColumns(quads, width, height, alpha, age, style.prisms());
		if (style.fireworks() > 0) drawFireworkBlooms(quads, width, height, alpha, age, style.fireworks());
		if (style.halos() > 0) drawHaloRings(quads, width, height, alpha, age, style.halos());
		if (style.nebulae() > 0) drawNebulaClouds(quads, width, height, alpha, age, style.nebulae());
		if (style.spirals() > 0) drawPixelSpirals(quads, width, height, alpha, age, style.spirals());
		if (style.curtains() > 0) drawStarCurtains(quads, width, height, alpha, age, style.curtains());
		if (style.chromaticRain() > 0) drawChromaticRain(quads, width, height, alpha, age, style.chromaticRain());
		if (style.diamonds() > 0) drawDiamondBursts(quads, width, height, alpha, age, style.diamonds());
		if (style.horizonFlares() > 0) drawHorizonFlares(quads, width, height, alpha, age, style.horizonFlares());
		if (style.constellations() > 0) drawConstellations(quads, width, height, alpha, age, style.constellations());
		if (style.lattice() > 0) drawPrismLattice(quads, width, height, alpha, age, style.lattice());
		if (style.embers() > 0) drawRisingEmbers(quads, width, height, alpha, age, style.embers());
		if (style.kaleidoscope() > 0) drawKaleidoscope(quads, width, height, alpha, age, style.kaleidoscope());
		if (style.eclipses() > 0) drawEclipseArcs(quads, width, height, alpha, age, style.eclipses());
		if (style.runes() > 0) drawRunicOrbit(quads, width, height, alpha, age, style.runes());
		if (style.crownRays() > 0) drawCrownRays(quads, width, height, alpha, age, style.crownRays());
		if (style.hearts() > 0) drawPixelHearts(quads, width, height, alpha, age, style.hearts());
		if (style.mirrorComets() > 0) drawMirrorComets(quads, width, height, alpha, age, style.mirrorComets());
		if (style.sweeps() > 0) drawStarlightSweeps(quads, width, height, alpha, age, style.sweeps());
		if (style.twinHelix() > 0) drawTwinHelix(quads, width, height, alpha, age, style.twinHelix());
		if (style.lightning() > 0) drawLightningForks(quads, width, height, alpha, age, style.lightning());
		if (style.prismFans() > 0) drawPrismFans(quads, width, height, alpha, age, style.prismFans());
		if (style.satellites() > 0) drawSatelliteTrails(quads, width, height, alpha, age, style.satellites());
		if (style.hourglass() > 0) drawHourglassTrails(quads, width, height, alpha, age, style.hourglass());
		if (style.glyphRain() > 0) drawGlyphRain(quads, width, height, alpha, age, style.glyphRain());
		if (style.novaCrosses() > 0) drawNovaCrosses(quads, width, height, alpha, age, style.novaCrosses());
		if (style.portalGates() > 0) drawPortalGates(quads, width, height, alpha, age, style.portalGates());
		if (style.crystalShards() > 0) drawCrystalShards(quads, width, height, alpha, age, style.crystalShards());
		if (style.infinityTrails() > 0) drawInfinityTrails(quads, width, height, alpha, age, style.infinityTrails());
		if (style.gravityWells() > 0) drawGravityWells(quads, width, height, alpha, age, style.gravityWells());
		if (style.spectrumSteps() > 0) drawSpectrumSteps(quads, width, height, alpha, age, style.spectrumSteps());
		if (style.starfallColumns() > 0) drawStarfallColumns(quads, width, height, alpha, age, style.starfallColumns());
		drawExpandedEffects(quads, width, height, alpha, age, style);
	}

	/** Diverse but bounded geometry for the explicitly confirmed celebrations. */
	private static void drawIntenseLayer(QuadCollector quads, int width, int height,
			int alpha, long age, SparklingAlertStyle style) {
		float phase = (age % 1_500L) / 1_500f;
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		int stars = style.stars() == 0 ? 0 : 32 + style.stars() * 42;
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
		// is a handful of tiny quads in the same cached batch as the star field.
		int streaks = style.comets() * 12;
		for (int i = 0; i < streaks; i++) {
			int hash = mix(i * 0x6A09E667 + (int) (age / 65L));
			int x = Math.floorMod(hash, safeWidth);
			int y = Math.floorMod(mix(hash), safeHeight);
			int colour = rainbow(Math.min(alpha, 225), phase + i / (float) streaks);
			int direction = (hash & 1) == 0 ? 1 : -1;
			int length = 3 + Math.floorMod(hash >>> 8, 5 + style.comets());
			for (int step = 0; step < length; step++) {
				int px = x - step;
				int py = y + direction * step;
				quads.add(px, py, px + Math.max(1, 3 - step / 3), py + 1, colour);
			}
		}

		// Pulsing radial bursts make each higher level feel stronger without adding
		// another full-screen border. Their bounded point count is resolution-independent.
		int rays = style.bursts() == 0 ? 0 : 8 + style.bursts() * 5;
		int points = 4 + style.bursts() * 2;
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
		if (style.orbits() > 0) drawOrbit(quads, centreX, centreY,
			Math.max(28, radius * 3 / 5), 6 + style.orbits() * 3,
			turn * 1.35, alpha, phase);
		if (style.orbits() >= 3) {
			drawOrbit(quads, centreX, centreY, Math.max(36, radius * 4 / 5),
			14, -turn, alpha, phase + 0.5f);
		}

		if (style.confetti() > 0) {
			drawConfetti(quads, width, height, alpha, age, style.confetti());
		}
	}

	/** Smooth aurora ribbons, sampled into a fixed number of horizontal quads. */
	private static void drawRibbons(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int segments = 24 + intensity * 8;
		int ribbons = 1 + (intensity + 1) / 2;
		for (int ribbon = 0; ribbon < ribbons; ribbon++) {
			for (int i = 0; i < segments; i++) {
				int x0 = width * i / segments;
				int x1 = Math.max(x0 + 1, width * (i + 1) / segments);
				double wave = Math.sin(i * 0.42 + age * 0.0022 + ribbon * 2.1);
				int baseY = ribbons == 1 ? height / 4
					: height / 4 + ribbon * (height / 2) / (ribbons - 1);
				int y = baseY
					+ (int) Math.round(wave * (5 + intensity * 2));
				int colour = rainbow(Math.min(alpha, 28 + intensity * 10),
					i / (float) segments + ribbon * 0.27f + age / 4_000f);
				quads.add(x0, y, x1, y + 1 + intensity / 2, colour);
			}
		}
	}

	/** Symmetric corner fountains add energy without screen-sized overdraw. */
	private static void drawCornerBursts(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int arms = 5 + intensity * 3;
		double reach = Math.min(width, height) * (0.08 + intensity * 0.025);
		for (int corner = 0; corner < 4; corner++) {
			int cx = (corner & 1) == 0 ? 0 : width;
			int cy = (corner & 2) == 0 ? 0 : height;
			int sx = cx == 0 ? 1 : -1;
			int sy = cy == 0 ? 1 : -1;
			for (int arm = 1; arm <= arms; arm++) {
				double progress = arm / (double) arms;
				double pulse = 0.55 + 0.45 * Math.sin(age * 0.004 + arm);
				int x = cx + sx * (int) Math.round(reach * progress * pulse);
				int y = cy + sy * (int) Math.round(reach * (1.0 - progress * 0.55) * pulse);
				drawStar(quads, x, y, 1 + intensity / 2, arm % 3,
					rainbow(Math.min(alpha, 190), arm / (float) arms + age / 2_500f));
			}
		}
	}

	private static void drawLightRays(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 5 + intensity * 3;
		int points = 5 + intensity * 2;
		int cx = width / 2;
		int cy = height * 9 / 20;
		double radius = Math.hypot(width, height) * 0.55;
		for (int ray = 0; ray < count; ray++) {
			double angle = age * 0.00035 + ray * Math.PI * 2.0 / count;
			int colour = rainbow(Math.min(alpha, 40 + intensity * 15),
				ray / (float) count + age / 5_000f);
			for (int point = 2; point <= points; point++) {
				double distance = radius * point / points;
				int x = cx + (int) Math.round(Math.cos(angle) * distance);
				int y = cy + (int) Math.round(Math.sin(angle) * distance);
				quads.add(x, y, x + 1 + intensity / 3, y + 1 + intensity / 3, colour);
			}
		}
	}

	private static void drawShockwaves(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int rings = 1 + intensity;
		int points = 18 + intensity * 5;
		int cx = width / 2;
		int cy = height * 9 / 20;
		int limit = Math.max(18, Math.min(width, height) / 2);
		for (int ring = 0; ring < rings; ring++) {
			double progress = ((age / 900.0) + ring / (double) rings) % 1.0;
			double radius = 8 + progress * limit;
			int ringAlpha = (int) (Math.min(alpha, 190) * (1.0 - progress));
			for (int point = 0; point < points; point++) {
				double angle = point * Math.PI * 2.0 / points;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius * 0.48);
				quads.add(x, y, x + 2, y + 1, rainbow(ringAlpha,
					(float) progress + point / (float) points));
			}
		}
	}

	private static void drawGlitterWaves(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int rows = 1 + intensity;
		int points = 12 + intensity * 6;
		for (int row = 0; row < rows; row++) {
			for (int point = 0; point < points; point++) {
				int x = width * point / Math.max(1, points - 1);
				double wave = Math.sin(point * 0.58 + age * 0.003 + row * 1.7);
				int y = height * (row + 1) / (rows + 1)
					+ (int) Math.round(wave * (4 + intensity * 2));
				drawStar(quads, x, y, 1 + ((point + row) & 1), point % 3,
					rainbow(Math.min(alpha, 125 + intensity * 18),
						point / (float) points + row * 0.19f + age / 3_200f));
			}
		}
	}

	private static void drawPrismColumns(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 3 + intensity * 2;
		for (int i = 0; i < count; i++) {
			int hash = mix(i * 0x27D4EB2D);
			int x = Math.floorMod(hash, Math.max(1, width));
			int columnWidth = 1 + intensity / 2;
			int top = Math.floorMod((int) (age / (28L + i * 3L)) + hash, Math.max(1, height));
			int length = Math.max(8, height / (5 + Math.floorMod(hash >>> 8, 4)));
			quads.add(x, Math.max(0, top - length), x + columnWidth, top,
				rainbow(Math.min(alpha, 34 + intensity * 12), i / (float) count + age / 4_500f));
		}
	}

	private static void drawFireworkBlooms(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int blooms = 1 + intensity;
		int spokes = 5 + intensity * 3;
		for (int bloom = 0; bloom < blooms; bloom++) {
			int hash = mix(bloom * 0x632BE5AB + (int) (age / 850L));
			int cx = width / 5 + Math.floorMod(hash, Math.max(1, width * 3 / 5));
			int cy = height / 6 + Math.floorMod(mix(hash), Math.max(1, height / 2));
			double progress = ((age + bloom * 170L) % 850L) / 850.0;
			double radius = (7 + intensity * 5) * progress;
			for (int spoke = 0; spoke < spokes; spoke++) {
				double angle = spoke * Math.PI * 2.0 / spokes;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius);
				drawStar(quads, x, y, 1 + intensity / 3, spoke % 3,
					rainbow((int) (Math.min(alpha, 220) * (1.0 - progress * 0.65)),
						spoke / (float) spokes + bloom * 0.23f));
			}
		}
	}

	private static void drawHaloRings(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int rings = 1 + intensity / 2;
		int points = 16 + intensity * 6;
		int cx = width / 2;
		int cy = height * 9 / 20;
		for (int ring = 0; ring < rings; ring++) {
			double radius = Math.max(20, Math.min(width, height) * (0.12 + ring * 0.07));
			double turn = age * (ring % 2 == 0 ? 0.001 : -0.0012);
			for (int point = 0; point < points; point++) {
				double angle = turn + point * Math.PI * 2.0 / points;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius * 0.34);
				quads.add(x, y, x + 1 + intensity / 4, y + 1,
					rainbow(Math.min(alpha, 150 + intensity * 18),
						point / (float) points + age / 3_000f));
			}
		}
	}

	private static void drawNebulaClouds(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int clouds = 4 + intensity * 3;
		for (int i = 0; i < clouds; i++) {
			int hash = mix(i * 0x45D9F3B);
			int cloudWidth = 10 + intensity * 4 + Math.floorMod(hash, 14);
			int cloudHeight = 3 + intensity + Math.floorMod(hash >>> 7, 5);
			int travel = Math.max(1, width + cloudWidth * 2);
			int x = Math.floorMod((int) (age / (22L + i * 2L)) + hash, travel) - cloudWidth;
			int y = Math.floorMod(mix(hash), Math.max(1, height));
			quads.add(x, y, x + cloudWidth, y + cloudHeight,
				rainbow(Math.min(alpha, 18 + intensity * 9), i / (float) clouds + age / 6_000f));
		}
	}

	private static void drawPixelSpirals(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int arms = 1 + intensity;
		int points = 10 + intensity * 7;
		int cx = width / 2;
		int cy = height * 9 / 20;
		double maximum = Math.max(18, Math.min(width, height) * 0.32);
		for (int arm = 0; arm < arms; arm++) {
			for (int point = 0; point < points; point++) {
				double progress = point / (double) points;
				double angle = age * 0.0012 + arm * Math.PI * 2.0 / arms + progress * Math.PI * 3.5;
				int x = cx + (int) Math.round(Math.cos(angle) * maximum * progress);
				int y = cy + (int) Math.round(Math.sin(angle) * maximum * progress * 0.55);
				quads.add(x, y, x + 1 + intensity / 3, y + 1 + intensity / 3,
					rainbow(Math.min(alpha, 150 + intensity * 18),
						(float) progress + arm / (float) arms));
			}
		}
	}

	private static void drawStarCurtains(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int strands = 4 + intensity * 3;
		int points = 3 + intensity * 2;
		for (int strand = 0; strand < strands; strand++) {
			int hash = mix(strand * 0x119DE1F3);
			int x = Math.floorMod(hash, Math.max(1, width));
			int offset = Math.floorMod((int) (age / 24L) + hash, Math.max(1, height + 30)) - 30;
			for (int point = 0; point < points; point++) {
				int y = offset - point * (4 + intensity);
				drawStar(quads, x, y, 1 + (point == 0 ? intensity / 2 : 0), point % 3,
					rainbow(Math.min(alpha, 165), strand / (float) strands + point * 0.08f));
			}
		}
	}

	private static void drawChromaticRain(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int drops = 8 + intensity * 7;
		for (int i = 0; i < drops; i++) {
			int hash = mix(i * 0x6A09E667);
			int x = Math.floorMod(hash + (int) (age / 35L), Math.max(1, width + 16)) - 8;
			int y = Math.floorMod(mix(hash) + (int) (age / (12L + intensity)),
				Math.max(1, height + 20)) - 10;
			int length = 2 + intensity + Math.floorMod(hash >>> 9, 4);
			quads.add(x, y, x + 1, y + length,
				rainbow(Math.min(alpha, 125 + intensity * 20), i / (float) drops + age / 3_500f));
		}
	}

	private static void drawDiamondBursts(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 1 + intensity;
		for (int i = 0; i < count; i++) {
			int hash = mix(i * 0x27D4EB2D + (int) (age / 1_100L));
			int cx = width / 5 + Math.floorMod(hash, Math.max(1, width * 3 / 5));
			int cy = height / 5 + Math.floorMod(mix(hash), Math.max(1, height * 3 / 5));
			double progress = ((age + i * 190L) % 1_100L) / 1_100.0;
			int radius = 2 + (int) Math.round(progress * (8 + intensity * 5));
			int colour = rainbow((int) (Math.min(alpha, 210) * (1.0 - progress)),
				i / (float) count + age / 4_000f);
			for (int step = 0; step < radius; step++) {
				quads.add(cx - radius + step, cy - step, cx - radius + step + 1, cy - step + 1, colour);
				quads.add(cx + step, cy - radius + step, cx + step + 1, cy - radius + step + 1, colour);
				quads.add(cx + radius - step - 1, cy + step, cx + radius - step, cy + step + 1, colour);
				quads.add(cx - step - 1, cy + radius - step - 1, cx - step, cy + radius - step, colour);
			}
		}
	}

	private static void drawHorizonFlares(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int bands = 1 + intensity;
		for (int band = 0; band < bands; band++) {
			double wave = 0.5 + 0.5 * Math.sin(age * 0.002 + band * 1.4);
			int y = height * (band + 1) / (bands + 1);
			int half = (int) Math.round(width * (0.08 + 0.07 * intensity) * wave);
			int thickness = 1 + intensity / 2;
			int colour = rainbow(Math.min(alpha, 35 + intensity * 14),
				band / (float) bands + age / 5_000f);
			quads.add(Math.max(0, width / 2 - half), y,
				Math.min(width, width / 2 + half), y + thickness, colour);
		}
	}

	private static void drawConstellations(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int nodes = 5 + intensity * 2;
		int previousX = 0;
		int previousY = 0;
		for (int i = 0; i < nodes; i++) {
			int hash = mix(i * 0x45D9F3B + intensity * 71);
			int x = Math.floorMod(hash, Math.max(1, width));
			int y = Math.floorMod(mix(hash), Math.max(1, height));
			drawStar(quads, x, y, 1 + intensity / 3, i % 3,
				rainbow(Math.min(alpha, 205), i / (float) nodes + age / 5_000f));
			if (i > 0) drawDottedLine(quads, previousX, previousY, x, y,
				8 + intensity * 2, rainbow(Math.min(alpha, 60 + intensity * 16),
					i / (float) nodes + age / 5_000f));
			previousX = x;
			previousY = y;
		}
	}

	private static void drawPrismLattice(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int lines = 3 + intensity * 2;
		int driftX = Math.floorMod((int) (age / 32L), Math.max(1, width / lines + 1));
		int driftY = Math.floorMod((int) (age / 45L), Math.max(1, height / lines + 1));
		for (int i = 0; i < lines; i++) {
			int x = (width * i / lines + driftX) % Math.max(1, width);
			int y = (height * i / lines + driftY) % Math.max(1, height);
			quads.add(x, 0, x + 1, height,
				rainbow(Math.min(alpha, 18 + intensity * 8), i / (float) lines + age / 6_000f));
			quads.add(0, y, width, y + 1,
				rainbow(Math.min(alpha, 18 + intensity * 8), 0.5f + i / (float) lines + age / 6_000f));
		}
	}

	private static void drawRisingEmbers(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 10 + intensity * 9;
		for (int i = 0; i < count; i++) {
			int hash = mix(i * 0x632BE5AB);
			int x = Math.floorMod(hash, Math.max(1, width));
			int speed = 1 + Math.floorMod(hash >>> 8, 3 + intensity);
			int y = height - Math.floorMod((int) (age / 20L) * speed + mix(hash),
				Math.max(1, height + 12));
			drawStar(quads, x, y, 1 + intensity / 3, i % 3,
				rainbow(Math.min(alpha, 135 + intensity * 20), i / (float) count + age / 3_000f));
		}
	}

	private static void drawKaleidoscope(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int petals = 4 + intensity * 2;
		int layers = 2 + intensity;
		int cx = width / 2;
		int cy = height * 9 / 20;
		double turn = age * 0.0007;
		for (int petal = 0; petal < petals; petal++) {
			double angle = turn + petal * Math.PI * 2.0 / petals;
			for (int layer = 1; layer <= layers; layer++) {
				int radius = (8 + intensity * 3) * layer;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius * 0.55);
				int mirrorX = cx - (x - cx);
				int colour = rainbow(Math.min(alpha, 150 + intensity * 18),
					petal / (float) petals + layer * 0.08f);
				quads.add(x - 1, y - 1, x + 2, y + 2, colour);
				quads.add(mirrorX - 1, y - 1, mirrorX + 2, y + 2, colour);
			}
		}
	}

	private static void drawEclipseArcs(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int arcs = 1 + intensity;
		int points = 14 + intensity * 5;
		int cx = width / 2;
		int cy = height * 9 / 20;
		for (int arc = 0; arc < arcs; arc++) {
			double radius = Math.max(16, Math.min(width, height) * (0.10 + arc * 0.045));
			double start = age * 0.0005 + arc * 1.1;
			for (int point = 0; point < points; point++) {
				double angle = start + point * Math.PI * 1.35 / points;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius * 0.55);
				quads.add(x, y, x + 2, y + 1,
					rainbow(Math.min(alpha, 115 + intensity * 18), point / (float) points + arc * 0.2f));
			}
		}
	}

	private static void drawRunicOrbit(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 4 + intensity * 2;
		int cx = width / 2;
		int cy = height * 9 / 20;
		int radius = Math.max(24, Math.min(width, height) * (18 + intensity * 3) / 100);
		for (int i = 0; i < count; i++) {
			double angle = age * 0.0008 + i * Math.PI * 2.0 / count;
			int x = cx + (int) Math.round(Math.cos(angle) * radius);
			int y = cy + (int) Math.round(Math.sin(angle) * radius * 0.42);
			int colour = rainbow(Math.min(alpha, 180), i / (float) count + age / 4_000f);
			quads.add(x - 2, y - 2, x + 3, y - 1, colour);
			quads.add(x - 2, y + 2, x + 3, y + 3, colour);
			quads.add(x - 2, y - 1, x - 1, y + 2, colour);
			quads.add(x + 2, y - 1, x + 3, y + 2, colour);
		}
	}

	private static void drawCrownRays(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int rays = 5 + intensity * 2;
		int centre = width / 2;
		int baseY = Math.max(3, height / 9);
		int spread = Math.max(18, width / 5);
		for (int i = 0; i < rays; i++) {
			int x = centre - spread / 2 + spread * i / Math.max(1, rays - 1);
			int peak = baseY - 4 - (i % 2 == 0 ? 4 + intensity * 2 : 0);
			int colour = rainbow(Math.min(alpha, 170 + intensity * 16), i / (float) rays + age / 4_500f);
			drawDottedLine(quads, centre, baseY + 3, x, peak, 5 + intensity, colour);
		}
	}

	private static void drawPixelHearts(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 1 + intensity;
		int pulse = 1 + (int) ((age / 180L) & 1L);
		for (int i = 0; i < count; i++) {
			int hash = mix(i * 0x119DE1F3 + intensity * 31);
			int x = width / 6 + Math.floorMod(hash, Math.max(1, width * 2 / 3));
			int y = height / 6 + Math.floorMod(mix(hash), Math.max(1, height * 2 / 3));
			int colour = rainbow(Math.min(alpha, 170 + intensity * 15), i / (float) count + age / 3_600f);
			quads.add(x - 2 * pulse, y - pulse, x, y + pulse, colour);
			quads.add(x, y - pulse, x + 2 * pulse, y + pulse, colour);
			quads.add(x - pulse, y + pulse, x + pulse, y + 3 * pulse, colour);
		}
	}

	private static void drawMirrorComets(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int pairs = 2 + intensity;
		for (int i = 0; i < pairs; i++) {
			int travel = Math.max(1, width / 2 + 20);
			int progress = Math.floorMod((int) (age / (18L + i * 3L)) + i * 53, travel);
			int y = height * (i + 1) / (pairs + 1);
			int colour = rainbow(Math.min(alpha, 215), i / (float) pairs + age / 2_800f);
			for (int tail = 0; tail < 5 + intensity; tail++) {
				quads.add(progress - tail, y + tail / 2, progress - tail + 2, y + tail / 2 + 1, colour);
				quads.add(width - progress + tail - 2, y - tail / 2,
					width - progress + tail, y - tail / 2 + 1, colour);
			}
		}
	}

	private static void drawStarlightSweeps(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int sweeps = 1 + intensity;
		for (int sweep = 0; sweep < sweeps; sweep++) {
			int travel = Math.max(1, width + 40);
			int x = Math.floorMod((int) (age / (12L + sweep * 2L)) + sweep * travel / sweeps,
				travel) - 20;
			int colour = rainbow(Math.min(alpha, 32 + intensity * 13), sweep / (float) sweeps + age / 5_000f);
			for (int y = 0; y < height; y += Math.max(3, 8 - intensity)) {
				int offset = y / Math.max(3, 7 - intensity);
				quads.add(x + offset, y, x + offset + 2 + intensity / 2, y + 2, colour);
			}
		}
	}

	/** Two counter-phased strands crossing the screen like a luminous helix. */
	private static void drawTwinHelix(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int points = 18 + intensity * 8;
		int amplitude = Math.max(8, height * (10 + intensity * 2) / 100);
		int centreY = height / 2;
		double motion = age * 0.003;
		for (int point = 0; point < points; point++) {
			int x = width * point / Math.max(1, points - 1);
			double wave = Math.sin(point * 0.48 + motion);
			for (int strand = 0; strand < 2; strand++) {
				int y = centreY + (int) Math.round((strand == 0 ? wave : -wave) * amplitude);
				int colour = rainbow(Math.min(alpha, 145 + intensity * 20),
					point / (float) points + strand * 0.5f + age / 4_000f);
				drawStar(quads, x, y, 1 + intensity / 3, strand, colour);
			}
		}
	}

	/** Deterministic branching bolts; no random objects or per-frame collections. */
	private static void drawLightningForks(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int bolts = 1 + intensity;
		int segments = 7 + intensity * 2;
		int epoch = (int) (age / 240L);
		for (int bolt = 0; bolt < bolts; bolt++) {
			int hash = mix(epoch * 0x45D9F3B + bolt * 0x632BE5AB);
			int lastX = width * (bolt + 1) / (bolts + 1);
			int lastY = 0;
			int colour = rainbow(Math.min(alpha, 175 + intensity * 18),
				bolt / (float) bolts + age / 2_500f);
			for (int segment = 1; segment <= segments; segment++) {
				hash = mix(hash + segment * 97);
				int x = Math.clamp(lastX + Math.floorMod(hash, 15 + intensity * 4)
					- (7 + intensity * 2), 0, width);
				int y = height * segment / segments;
				drawDottedLine(quads, lastX, lastY, x, y, 2 + intensity, colour);
				if (segment > 2 && (hash & 3) == 0) {
					drawDottedLine(quads, x, y, x + ((hash & 8) == 0 ? -1 : 1) * (5 + intensity * 2),
						Math.min(height, y + 5 + intensity * 2), 2 + intensity / 2, colour);
				}
				lastX = x;
				lastY = y;
			}
		}
	}

	private static void drawPrismFans(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int rays = 4 + intensity * 3;
		for (int side = 0; side < 2; side++) {
			int originX = side == 0 ? 0 : width;
			for (int ray = 0; ray < rays; ray++) {
				int targetX = width * (ray + 1) / (rays + 1);
				int targetY = height / 8 + (ray & 1) * height / 8;
				int colour = rainbow(Math.min(alpha, 48 + intensity * 14),
					ray / (float) rays + side * 0.5f + age / 5_000f);
				drawDottedLine(quads, originX, height, targetX, targetY,
					6 + intensity * 2, colour);
			}
		}
	}

	private static void drawSatelliteTrails(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 2 + intensity;
		int cx = width / 2;
		int cy = height * 9 / 20;
		int radius = Math.max(20, Math.min(width, height) * (22 + intensity * 3) / 100);
		for (int satellite = 0; satellite < count; satellite++) {
			double angle = age * 0.001 + satellite * Math.PI * 2.0 / count;
			int colour = rainbow(Math.min(alpha, 210), satellite / (float) count + age / 3_500f);
			for (int tail = 4 + intensity; tail >= 0; tail--) {
				double sample = angle - tail * 0.055;
				int x = cx + (int) Math.round(Math.cos(sample) * radius);
				int y = cy + (int) Math.round(Math.sin(sample) * radius * 0.42);
				int size = tail == 0 ? 2 + intensity / 2 : 1;
				drawStar(quads, x, y, size, satellite % 3, colour);
			}
		}
	}

	private static void drawHourglassTrails(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int trails = 1 + intensity;
		int points = 12 + intensity * 4;
		for (int trail = 0; trail < trails; trail++) {
			for (int point = 0; point <= points; point++) {
				double progress = point / (double) points;
				int inset = trail * (2 + intensity);
				int x1 = inset + (int) Math.round((width - inset * 2) * progress);
				int x2 = width - x1;
				int y = (int) Math.round(height * progress
					+ Math.sin(age * 0.002 + point * 0.5) * intensity);
				int colour = rainbow(Math.min(alpha, 95 + intensity * 18),
					(float) progress + trail * 0.14f + age / 4_500f);
				quads.add(x1, y, x1 + 1 + intensity / 3, y + 2, colour);
				quads.add(x2, y, x2 + 1 + intensity / 3, y + 2, colour);
			}
		}
	}

	private static void drawGlyphRain(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int glyphs = 7 + intensity * 6;
		for (int glyph = 0; glyph < glyphs; glyph++) {
			int hash = mix(glyph * 0x27D4EB2D);
			int x = Math.floorMod(hash, Math.max(1, width));
			int y = Math.floorMod(mix(hash) + (int) (age / (18L + glyph % 5)),
				Math.max(1, height + 16)) - 8;
			int size = 2 + intensity / 2;
			int colour = rainbow(Math.min(alpha, 145 + intensity * 18),
				glyph / (float) glyphs + age / 3_200f);
			if ((hash & 1) == 0) {
				quads.add(x, y, x + 1, y + size * 2 + 1, colour);
				quads.add(x, y + size, x + size + 1, y + size + 1, colour);
			} else {
				quads.add(x, y, x + size + 1, y + 1, colour);
				quads.add(x + size / 2, y, x + size / 2 + 1, y + size * 2 + 1, colour);
			}
		}
	}

	private static void drawNovaCrosses(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int novas = 1 + intensity;
		for (int nova = 0; nova < novas; nova++) {
			int hash = mix(nova * 0x6A09E667 + (int) (age / 900L));
			int cx = width / 6 + Math.floorMod(hash, Math.max(1, width * 2 / 3));
			int cy = height / 6 + Math.floorMod(mix(hash), Math.max(1, height * 2 / 3));
			double progress = ((age + nova * 137L) % 900L) / 900.0;
			int reach = 3 + (int) Math.round(progress * (10 + intensity * 5));
			int colour = rainbow((int) (Math.min(alpha, 215) * (1.0 - progress * 0.75)),
				nova / (float) novas + age / 3_000f);
			quads.add(cx - reach, cy, cx + reach + 1, cy + 1, colour);
			quads.add(cx, cy - reach, cx + 1, cy + reach + 1, colour);
			quads.add(cx - reach / 2, cy - reach / 2, cx - reach / 2 + 1, cy - reach / 2 + 1, colour);
			quads.add(cx + reach / 2, cy + reach / 2, cx + reach / 2 + 1, cy + reach / 2 + 1, colour);
		}
	}

	private static void drawPortalGates(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int gates = 1 + intensity / 2;
		int cx = width / 2;
		int cy = height * 9 / 20;
		for (int gate = 0; gate < gates; gate++) {
			double pulse = 0.72 + 0.28 * Math.sin(age * 0.0025 + gate * 1.8);
			int halfW = (int) Math.round((18 + intensity * 7 + gate * 11) * pulse);
			int halfH = Math.max(8, halfW * 5 / 9);
			int colour = rainbow(Math.min(alpha, 95 + intensity * 22),
				gate / (float) gates + age / 4_000f);
			quads.add(cx - halfW, cy - halfH, cx + halfW + 1, cy - halfH + 1, colour);
			quads.add(cx - halfW, cy + halfH, cx + halfW + 1, cy + halfH + 1, colour);
			quads.add(cx - halfW, cy - halfH + 1, cx - halfW + 1, cy + halfH, colour);
			quads.add(cx + halfW, cy - halfH + 1, cx + halfW + 1, cy + halfH, colour);
		}
	}

	private static void drawCrystalShards(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int count = 4 + intensity * 4;
		for (int shard = 0; shard < count; shard++) {
			int hash = mix(shard * 0x45D9F3B + (int) (age / 500L));
			int cx = Math.floorMod(hash, Math.max(1, width));
			int cy = Math.floorMod(mix(hash), Math.max(1, height));
			int size = 2 + intensity + Math.floorMod(hash >>> 9, 3);
			int colour = rainbow(Math.min(alpha, 145 + intensity * 18),
				shard / (float) count + age / 3_600f);
			for (int step = 0; step < size; step++) {
				quads.add(cx - step, cy + step, cx - step + 1, cy + step + 2, colour);
				quads.add(cx + step, cy + step, cx + step + 1, cy + step + 2, colour);
			}
			quads.add(cx, cy - size, cx + 1, cy + size * 2, colour);
		}
	}

	private static void drawInfinityTrails(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int trails = 1 + intensity / 2;
		int points = 24 + intensity * 8;
		int cx = width / 2;
		int cy = height * 9 / 20;
		int radius = Math.max(18, Math.min(width, height) * (17 + intensity * 3) / 100);
		for (int trail = 0; trail < trails; trail++) {
			for (int point = 0; point < points; point++) {
				double angle = age * 0.0012 + point * Math.PI * 2.0 / points
					+ trail * Math.PI / Math.max(1, trails);
				int x = cx + (int) Math.round(Math.sin(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle * 2.0) * radius * 0.38);
				drawStar(quads, x, y, 1 + intensity / 3, point % 3,
					rainbow(Math.min(alpha, 135 + intensity * 20),
						point / (float) points + trail * 0.23f));
			}
		}
	}

	private static void drawGravityWells(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int wells = 1 + intensity / 2;
		int particles = 10 + intensity * 6;
		for (int well = 0; well < wells; well++) {
			int cx = width * (well + 1) / (wells + 1);
			int cy = height * (well % 2 == 0 ? 2 : 3) / 5;
			for (int particle = 0; particle < particles; particle++) {
				double progress = ((age / 1_200.0) + particle / (double) particles) % 1.0;
				double radius = (8 + intensity * 8) * (1.0 - progress);
				double angle = progress * Math.PI * (5.0 + intensity) + particle * 0.7;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius * 0.55);
				quads.add(x, y, x + 1 + intensity / 3, y + 1 + intensity / 3,
					rainbow(Math.min(alpha, 120 + intensity * 22),
						(float) progress + well * 0.4f));
			}
		}
	}

	private static void drawSpectrumSteps(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int staircases = 1 + intensity;
		int steps = 7 + intensity * 3;
		for (int staircase = 0; staircase < staircases; staircase++) {
			int direction = (staircase & 1) == 0 ? 1 : -1;
			int travel = Math.floorMod((int) (age / (22L + staircase * 3L)),
				Math.max(1, width + 30)) - 15;
			for (int step = 0; step < steps; step++) {
				int x = direction > 0 ? travel + step * 4 : width - travel - step * 4;
				int y = height * (staircase + 1) / (staircases + 1) - step * 2;
				int colour = rainbow(Math.min(alpha, 105 + intensity * 20),
					step / (float) steps + staircase * 0.17f + age / 4_000f);
				quads.add(x, y, x + 4, y + 1, colour);
				quads.add(direction > 0 ? x + 3 : x, y - 2,
					direction > 0 ? x + 4 : x + 1, y, colour);
			}
		}
	}

	private static void drawStarfallColumns(QuadCollector quads, int width, int height,
			int alpha, long age, int intensity) {
		int columns = 3 + intensity * 2;
		int stars = 3 + intensity;
		for (int column = 0; column < columns; column++) {
			int hash = mix(column * 0x632BE5AB);
			int x = width * (column + 1) / (columns + 1);
			int offset = Math.floorMod((int) (age / (16L + column * 2L)) + hash,
				Math.max(1, height + 24)) - 12;
			for (int star = 0; star < stars; star++) {
				int y = offset - star * (5 + intensity);
				drawStar(quads, x, y, star == 0 ? 2 + intensity / 2 : 1,
					(column + star) % 3, rainbow(Math.min(alpha, 160 + intensity * 18),
						column / (float) columns + star * 0.07f));
			}
		}
	}

	/** New effects share the existing deterministic batch and allocate no frame objects. */
	private static void drawExpandedEffects(QuadCollector quads, int width, int height,
			int alpha, long age, SparklingAlertStyle style) {
		int cx = width / 2;
		int cy = height * 9 / 20;
		float phase = age / 4_000f;
		int level;
		if ((level = style.addition(0)) > 0) { // Magic Circle
			int points = 24 + level * 8;
			int radius = Math.max(18, Math.min(width, height) * (16 + level * 2) / 100);
			for (int i = 0; i < points; i++) {
				double angle = age * .0007 + i * Math.PI * 2 / points;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius * .55);
				quads.add(x, y, x + 2, y + 1, rainbow(Math.min(alpha, 180), phase + i / (float) points));
				if (i % Math.max(2, 8 - level) == 0) drawDottedLine(quads, cx, cy, x, y,
					4 + level, rainbow(Math.min(alpha, 70 + level * 14), phase + i / (float) points));
			}
		}
		if ((level = style.addition(1)) > 0) { // Constellation Reveal
			int nodes = 5 + level * 2;
			int visible = 1 + (int) ((age / 180L) % nodes);
			int px = cx, py = cy;
			for (int i = 0; i < visible; i++) {
				int hash = mix(i * 0x45D9F3B + level * 91);
				int x = width / 8 + Math.floorMod(hash, Math.max(1, width * 3 / 4));
				int y = height / 8 + Math.floorMod(mix(hash), Math.max(1, height * 3 / 4));
				if (i > 0) drawDottedLine(quads, px, py, x, y, 8 + level,
					rainbow(Math.min(alpha, 100), phase + i / (float) nodes));
				drawStar(quads, x, y, 1 + level / 3, i % 3,
					rainbow(Math.min(alpha, 225), phase + i / (float) nodes));
				px = x; py = y;
			}
		}
		if ((level = style.addition(2)) > 0) drawWings(quads, width, height, alpha, age, level, false);
		if ((level = style.addition(3)) > 0) { // Northern Lights
			int bands = 2 + level;
			int segments = 28 + level * 6;
			for (int band = 0; band < bands; band++) for (int i = 0; i < segments; i++) {
				int x0 = width * i / segments;
				int x1 = Math.max(x0 + 1, width * (i + 1) / segments);
				int y = height / 8 + band * 3 + (int) Math.round(Math.sin(i * .32
					+ age * .0012 + band) * (5 + level));
				quads.add(x0, y, x1, y + 2 + level / 2,
					rainbow(Math.min(alpha, 24 + level * 9), phase + band * .17f + i / (float) segments));
			}
		}
		if ((level = style.addition(4)) > 0) { // Celestial Clock
			int ticks = 12 + level * 2;
			int radius = Math.max(22, Math.min(width, height) / 5);
			for (int i = 0; i < ticks; i++) {
				double angle = i * Math.PI * 2 / ticks;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius * .55);
				drawStar(quads, x, y, 1, i % 3, rainbow(Math.min(alpha, 170), phase + i / (float) ticks));
			}
			for (int hand = 0; hand < 2 + level / 3; hand++) {
				double angle = age * (.00045 + hand * .0003) * (hand % 2 == 0 ? 1 : -1);
				drawDottedLine(quads, cx, cy, cx + (int) (Math.cos(angle) * radius),
					cy + (int) (Math.sin(angle) * radius * .55), 8 + level,
					rainbow(Math.min(alpha, 190), phase + hand * .3f));
			}
		}
		if ((level = style.addition(5)) > 0) { // Crystal Bloom
			int petals = 6 + level * 2;
			double bloom = .45 + .55 * Math.sin(age * .002);
			for (int i = 0; i < petals; i++) {
				double angle = i * Math.PI * 2 / petals;
				int reach = (int) ((12 + level * 5) * bloom);
				int x = cx + (int) (Math.cos(angle) * reach);
				int y = cy + (int) (Math.sin(angle) * reach * .65);
				drawDottedLine(quads, cx, cy, x, y, 3 + level,
					rainbow(Math.min(alpha, 210), phase + i / (float) petals));
				drawStar(quads, x, y, 2 + level / 2, 1,
					rainbow(Math.min(alpha, 235), phase + i / (float) petals));
			}
		}
		if ((level = style.addition(6)) > 0) { // Stardust Vortex
			int particles = 18 + level * 9;
			for (int i = 0; i < particles; i++) {
				double progress = i / (double) particles;
				double angle = age * .002 + progress * Math.PI * (4 + level);
				int radius = (int) (Math.min(width, height) * .3 * progress);
				drawStar(quads, cx + (int) (Math.cos(angle) * radius),
					cy + (int) (Math.sin(angle) * radius * .52), 1 + level / 4, i % 3,
					rainbow(Math.min(alpha, 195), phase + (float) progress));
			}
		}
		if ((level = style.addition(7)) > 0) { // Comet Impact
			long cycle = Math.floorMod(age, 1_800L);
			double progress = Math.min(1, cycle / 1_050.0);
			int x = (int) (width * (.08 + .42 * progress));
			int y = (int) (height * (.12 + .33 * progress));
			for (int tail = 0; tail < 8 + level * 2; tail++) quads.add(x - tail * 2, y - tail,
				x - tail * 2 + 2, y - tail + 1, rainbow(Math.min(alpha, 230), phase + tail * .03f));
			if (progress >= 1) drawBurst(quads, cx, cy, 8 + level * 3, level,
				alpha, phase + cycle / 1_800f);
		}
		if ((level = style.addition(8)) > 0) { // Arcane Seal
			for (int ring = 1; ring <= 1 + level / 2; ring++) {
				int radius = 14 + ring * (8 + level);
				int points = 5 + ring + level;
				double turn = age * .0008 * (ring % 2 == 0 ? -1 : 1);
				for (int i = 0; i < points; i++) {
					double a = turn + i * Math.PI * 2 / points;
					int x = cx + (int) (Math.cos(a) * radius);
					int y = cy + (int) (Math.sin(a) * radius * .58);
					drawDottedLine(quads, cx, cy, x, y, 4 + level,
						rainbow(Math.min(alpha, 115), phase + i / (float) points));
				}
			}
		}
		if ((level = style.addition(9)) > 0) { // Light Pillars
			int pillars = 3 + level * 2;
			for (int i = 0; i < pillars; i++) {
				int x = width * (i + 1) / (pillars + 1);
				int pulse = (int) ((.5 + .5 * Math.sin(age * .002 + i)) * height / 3);
				quads.add(x - 1 - level / 2, height - pulse, x + 2 + level / 2, height,
					rainbow(Math.min(alpha, 22 + level * 10), phase + i / (float) pillars));
			}
		}
		if ((level = style.addition(10)) > 0) { // Dimensional Tear
			int points = 12 + level * 4;
			int lastX = cx, lastY = height / 8;
			for (int i = 1; i <= points; i++) {
				int hash = mix(i * 0x632BE5AB + (int) (age / 90));
				int x = cx + Math.floorMod(hash, 9 + level * 3) - (4 + level);
				int y = height / 8 + height * 3 / 4 * i / points;
				drawDottedLine(quads, lastX, lastY, x, y, 3 + level,
					rainbow(Math.min(alpha, 210), phase + i / (float) points));
				lastX = x; lastY = y;
			}
		}
		if ((level = style.addition(11)) > 0) drawWings(quads, width, height, alpha, age, level, true);
		if ((level = style.addition(12)) > 0) { // Meteor Shower
			int meteors = 6 + level * 5;
			for (int i = 0; i < meteors; i++) {
				int hash = mix(i * 0x27D4EB2D);
				int travel = width + height + 40;
				int p = Math.floorMod((int) (age / (10 + i % 5)) + hash, travel) - 20;
				int x = p;
				int y = Math.floorMod(hash, Math.max(1, height / 2)) + p / 3;
				for (int tail = 0; tail < 5 + level; tail++) quads.add(x - tail, y - tail,
					x - tail + 2, y - tail + 1, rainbow(Math.min(alpha, 205), phase + i / (float) meteors));
			}
		}
		if ((level = style.addition(13)) > 0) { // Crown Formation
			int points = 5 + level * 2;
			int baseY = Math.max(8, height / 7);
			for (int i = 0; i < points; i++) {
				int x = cx - width / 8 + width / 4 * i / Math.max(1, points - 1);
				int y = baseY - ((i & 1) == 0 ? 5 + level * 2 : 0);
				drawDottedLine(quads, cx, baseY + 8, x, y, 5 + level,
					rainbow(Math.min(alpha, 210), phase + i / (float) points));
				drawStar(quads, x, y, 1 + level / 3, 0,
					rainbow(Math.min(alpha, 240), phase + i / (float) points));
			}
		}
		if ((level = style.addition(14)) > 0) { // Reality Ripple
			int ripples = 1 + level;
			for (int ripple = 0; ripple < ripples; ripple++) {
				double progress = ((age / 1_200.0) + ripple / (double) ripples) % 1;
				int radius = 5 + (int) (Math.min(width, height) * .55 * progress);
				int points = 20 + level * 5;
				for (int i = 0; i < points; i++) {
					double a = i * Math.PI * 2 / points;
					int x = cx + (int) (Math.cos(a) * radius);
					int y = cy + (int) (Math.sin(a) * radius * .45);
					quads.add(x, y, x + 2, y + 1, rainbow((int) (Math.min(alpha, 130)
						* (1 - progress)), phase + (float) progress));
				}
			}
		}
	}

	private static void drawWings(QuadCollector quads, int width, int height, int alpha,
			long age, int level, boolean celestial) {
		int cx = width / 2;
		int cy = height * 9 / 20;
		int feathers = 4 + level * 2;
		for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
			int side = sideIndex == 0 ? -1 : 1;
			for (int feather = 0; feather < feathers; feather++) {
			double spread = feather / (double) feathers;
			int reach = 15 + level * 5 + feather * (celestial ? 3 : 2);
			int x = cx + side * (10 + (int) (reach * (.45 + spread)));
			int y = cy - (int) (Math.sin(spread * Math.PI) * (12 + level * 3))
				+ feather * (celestial ? 1 : 2);
			drawDottedLine(quads, cx + side * 8, cy, x, y, 5 + level,
				rainbow(Math.min(alpha, celestial ? 215 : 165),
					(float) (age / 4_000f + spread + (side > 0 ? .5f : 0))));
		}
		}
	}

	private static void drawBurst(QuadCollector quads, int cx, int cy, int rays,
			int level, int alpha, float phase) {
		for (int ray = 0; ray < rays; ray++) {
			double angle = ray * Math.PI * 2 / rays;
			int reach = 8 + level * 5;
			drawDottedLine(quads, cx, cy, cx + (int) (Math.cos(angle) * reach),
				cy + (int) (Math.sin(angle) * reach), 3 + level,
				rainbow(Math.min(alpha, 230), phase + ray / (float) rays));
		}
	}

	private static void drawDottedLine(QuadCollector quads, int x1, int y1, int x2, int y2,
			int points, int colour) {
		for (int point = 0; point <= points; point++) {
			int x = x1 + (x2 - x1) * point / Math.max(1, points);
			int y = y1 + (y2 - y1) * point / Math.max(1, points);
			quads.add(x, y, x + 1, y + 1, colour);
		}
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
		int innerHeight = Math.max(0, height - thickness * 2);
		for (int i = 0; i < segments; i++) {
			int colour = rainbow(alpha, phase + i / (float) segments);
			int x1 = width * i / segments;
			int x2 = width * (i + 1) / segments;
			int y1 = thickness + innerHeight * i / segments;
			int y2 = thickness + innerHeight * (i + 1) / segments;
			quads.add(x1, 0, x2, thickness, colour);
			quads.add(width - x2, height - thickness, width - x1, height, colour);
			if (y2 > y1) {
				quads.add(0, y1, thickness, y2, colour);
				quads.add(width - thickness, height - y2, width, height - y1, colour);
			}
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
			int centerX, int y, int alpha, float phase) {
		int x = centerX - font.width(text) / 2;
		UIDraw.rainbowTextAtPhase(graphics, font, Component.literal(text), x, y,
			0.5f, alpha, phase);
	}

	private static int textShakeOffset(long age, int intensity, int salt) {
		if (intensity <= 0) return 0;
		int frame = (int) (age / 66L);
		return Math.floorMod(mix(frame * 0x45D9F3B + salt), intensity * 2 + 1) - intensity;
	}

	private static float textGradientPhase(long age, int speed) {
		if (speed <= 0) return 0f;
		long cycleMillis = 8_000L / speed;
		return Math.floorMod(age, cycleMillis) / (float) cycleMillis;
	}

	private static int rainbow(int alpha, float hue) {
		return RainbowColours.phased(0f, hue, 0.5f,
			Math.clamp(alpha, 0, 255) / 255f);
	}

	private static int timedEffectAlpha(int alpha, long age, long duration, int mode) {
		if (mode == 0 || duration <= 0) return alpha;
		double progress = Math.clamp(age / (double) duration, 0, 1);
		double multiplier = switch (mode) {
			case 1 -> Math.clamp(1.0 - progress * 2.2, 0, 1);
			case 2 -> Math.clamp(1.0 - Math.abs(progress - .32) * 4.2, 0, 1);
			case 3 -> Math.clamp(Math.min(progress * 4, (1 - progress) * 4), 0, 1);
			default -> Math.clamp((progress - .58) * 2.8, 0, 1);
		};
		return (int) Math.round(alpha * multiplier);
	}

	/** Primitive builder used only for the active frame, avoiding one GUI state per sparkle. */
	private static final class QuadCollector {
		private static final int MAX_INTS = 150_000;
		private int[] values;
		private int size;
		private int limit = MAX_INTS;

		private QuadCollector(int initialInts) {
			values = new int[initialInts];
		}

		private void add(int x0, int y0, int x1, int y1, int colour) {
			if (x1 <= x0 || y1 <= y0) return;
			if (size + 5 > limit) return;
			if (size + 5 > values.length) {
				values = Arrays.copyOf(values, Math.min(MAX_INTS, values.length * 2));
			}
			values[size++] = x0;
			values[size++] = y0;
			values[size++] = x1;
			values[size++] = y1;
			values[size++] = colour;
		}

		private void reset() {
			size = 0;
		}

		private void setLimit(int requested) {
			limit = Math.clamp(requested, 5, MAX_INTS);
		}

		private int[] values() {
			return values;
		}

		private int size() {
			return size;
		}
	}
}
