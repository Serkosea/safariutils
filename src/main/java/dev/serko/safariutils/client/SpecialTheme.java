package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cached global novelty themes shared by every SafariUtils screen and HUD. */
public final class SpecialTheme {
	public static final int OFF = 0;
	public static final int RAINBOW = 1;
	public static final int CONSTELLATION = 2;
	private static final long CONSTELLATION_DURATION_MS = 30_000L;
	private static final long ASTRAL_CYCLE_MS = 9_500L;
	private static final int[] CONSTELLATION_COLOURS = {
		0xEAFBFF, 0x83E9FF, 0x579DFF, 0x8178E8, 0xC6B8FF,
		0xF4F1FF, 0xFFD987, 0xFFF4C7, 0xB8F5FF
	};
	private static long temporaryConstellationUntil;
	private static long resolvedModeFrame = Long.MIN_VALUE;
	private static int resolvedMode = OFF;
	private static final int STAR_CACHE_LIMIT = 48;
	private static final int EFFECT_CACHE_LIMIT = 128;
	private static final Map<StarKey, StarCache> STAR_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<StarKey, StarCache> eldest) {
			return size() > STAR_CACHE_LIMIT;
		}
	};
	private static final Map<EffectKey, EffectCache> EFFECT_CACHE =
		new LinkedHashMap<>(32, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<EffectKey, EffectCache> eldest) {
				return size() > EFFECT_CACHE_LIMIT;
			}
		};

	private record StarKey(int theme, int width, int height, int density) { }
	private record EffectKey(int theme, int type, int width, int height, int variant) { }
	private static final class EffectCache {
		private long frameId = Long.MIN_VALUE;
		private int[] quads = new int[0];
	}
	private static final class StarCache {
		private final int count;
		private final long[] seeds;
		private final long[] lives;
		private final long[] offsets;
		private final int[] xs;
		private final int[] ys;
		private long frameId = Long.MIN_VALUE;
		private final int[] quads;
		private int quadLength;

		private StarCache(int count) {
			this.count = count;
			this.seeds = new long[count];
			this.lives = new long[count];
			this.offsets = new long[count];
			this.xs = new int[count];
			this.ys = new int[count];
			this.quads = new int[count * 14 * 5];
			for (int i = 0; i < count; i++) {
				long seed = mix(i * 0x9E3779B97F4A7C15L);
				seeds[i] = seed;
				lives[i] = 650L + positive(seed >>> 17) % 1_050L;
				offsets[i] = positive(seed) % lives[i];
			}
		}
	}

	private SpecialTheme() {
	}

	public static boolean rainbow() {
		return mode() != OFF;
	}

	public static boolean constellation() {
		return mode() == CONSTELLATION;
	}

	public static int mode() {
		long frame = RainbowColours.frameId();
		if (resolvedModeFrame == frame) return resolvedMode;
		resolvedModeFrame = frame;
		resolvedMode = resolveMode();
		return resolvedMode;
	}

	private static int resolveMode() {
		if (System.currentTimeMillis() < temporaryConstellationUntil) return CONSTELLATION;
		SafariConfig.AdvancedConfig advanced = ConfigManager.get().advanced;
		if (advanced.specialTheme == CONSTELLATION && advanced.constellationThemeUnlocked) {
			return CONSTELLATION;
		}
		return advanced.specialTheme == RAINBOW ? RAINBOW : OFF;
	}

	/** Replays the temporary effect and permanently exposes its picker option. */
	public static void completeConstellation() {
		temporaryConstellationUntil = System.currentTimeMillis() + CONSTELLATION_DURATION_MS;
		resolvedModeFrame = Long.MIN_VALUE;
		SafariConfig.AdvancedConfig advanced = ConfigManager.get().advanced;
		if (!advanced.constellationThemeUnlocked) {
			advanced.constellationThemeUnlocked = true;
			ConfigManager.save();
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			// Preserve the original constellation completion sound exactly.
			for (int layer = 0; layer < 25; layer++) {
				client.player.playSound(SoundEvents.BEACON_ACTIVATE, 1f, 2f);
			}
		}
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, Component component,
						int x, int y, int fallbackColour) {
		if (rainbow()) {
			// The global theme owns the complete line. Splitting out a special
			// player name first would leave status prefixes/suffixes in fallback colours.
			rainbowText(graphics, font, component, x, y);
			return;
		}
		if (PlayerNameStyle.drawIfPresent(graphics, font, component, x, y, fallbackColour)) return;
		graphics.text(font, component, x, y, fallbackColour);
	}

	public static void rainbowText(GuiGraphicsExtractor graphics, Font font,
							   String text, int x, int y) {
		rainbowText(graphics, font, Component.literal(text), x, y);
	}

	/** Rainbow text which retains bold and any other style carried by the component. */
	public static void rainbowText(GuiGraphicsExtractor graphics, Font font,
							   Component component, int x, int y) {
		UIDraw.specialText(graphics, font, component, x, y, 0.5f);
	}

	/** Small deterministic twinkles behind panel text, with no per-frame allocations. */
	public static void stars(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
		stars(graphics, left, top, width, height, 1f);
	}

	public static void stars(GuiGraphicsExtractor graphics, int left, int top,
						 int width, int height, float density) {
		stars(graphics, left, top, width, height, density, mode());
	}

	/** Cached star field which can also be used by explicit theme previews. */
	static void constellationStars(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, float density) {
		stars(graphics, left, top, width, height, density, CONSTELLATION);
	}

	static void stars(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, float density, int theme) {
		if (theme == OFF || width < 8 || height < 8) return;
		int densityKey = Math.max(1, Math.round(density * 100f));
		StarKey key = new StarKey(theme, width, height, densityKey);
		StarCache cache = STAR_CACHE.computeIfAbsent(key, ignored -> {
			float actualDensity = densityKey / 100f;
			// The layout is generated once and only its compact quad batch changes at
			// 40 FPS, so the denser field does not add widget or render-state churn.
			int count = Math.clamp(Math.round(width * height / 760f * actualDensity),
				Math.max(1, Math.round(13 * actualDensity)),
				Math.max(1, Math.round(120 * actualDensity)));
			return new StarCache(count);
		});
		long frameId = RainbowColours.frameId();
		if (cache.frameId != frameId) rebuildStars(cache, width, height, frameId, theme);
		GuiQuadBatchRenderState.submit(graphics, left, top, width, height,
			cache.quads, cache.quadLength);
	}

	private static void rebuildStars(StarCache cache, int width, int height,
			long frameId, int theme) {
		long now = RainbowColours.frameTime(frameId);
		int[] quads = cache.quads;
		int cursor = 0;
		for (int i = 0; i < cache.count; i++) {
			long seed = cache.seeds[i];
			long life = cache.lives[i];
			long generation = Math.floorDiv(now + cache.offsets[i], life);
			long positionSeed = mix(seed ^ generation * 0xD1B54A32D192ED03L);
			cache.xs[i] = 3 + (int) (positive(positionSeed >>> 7) % Math.max(1, width - 6));
			cache.ys[i] = 3 + (int) (positive(positionSeed >>> 29) % Math.max(1, height - 6));
		}
		if (theme == CONSTELLATION) {
			for (int i = 0; i + 1 < cache.count; i += 6) {
				int fromX = cache.xs[i];
				int fromY = cache.ys[i];
				int toX = cache.xs[i + 1];
				int toY = cache.ys[i + 1];
				int samples = Math.min(48, Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY)));
				for (int point = 0; point <= samples; point += 2) {
					float amount = point / (float) Math.max(1, samples);
					int x = Math.round(fromX + (toX - fromX) * amount);
					int y = Math.round(fromY + (toY - fromY) * amount);
					cursor = quad(quads, cursor, x, y, x + 1, y + 1,
						withAlpha(colourAt(CONSTELLATION, x + y * 0.35f, 0.72f), 62));
				}
				// A bright signal travels along each connection, making the effect read
				// as an active constellation rather than another continuous gradient.
				float signal = Math.floorMod(now / 9L + i * 19L, 1_000L) / 1_000f;
				int signalX = Math.round(fromX + (toX - fromX) * signal);
				int signalY = Math.round(fromY + (toY - fromY) * signal);
				int signalColour = withAlpha(0xFFFFF2C2, 210);
				cursor = quad(quads, cursor, signalX - 2, signalY,
					signalX + 3, signalY + 1, signalColour);
				cursor = quad(quads, cursor, signalX, signalY - 2,
					signalX + 1, signalY + 3, signalColour);
			}
		}
		for (int i = 0; i < cache.count; i++) {
			long seed = cache.seeds[i];
			long life = cache.lives[i];
			long age = Math.floorMod(now + cache.offsets[i], life);
			long generation = Math.floorDiv(now + cache.offsets[i], life);
			long positionSeed = mix(seed ^ generation * 0xD1B54A32D192ED03L);
			float progress = age / (float) life;
			float alpha = 1f - Math.abs(progress * 2f - 1f);
			int x = cache.xs[i];
			int y = cache.ys[i];
			int size = 1 + (int) (positive(positionSeed >>> 43) % 3);
			int colour = withAlpha(colourAt(theme, x + y * 0.35f,
				theme == CONSTELLATION ? 0.82f : 0.35f),
				Math.round(255f * (0.18f + alpha * (theme == CONSTELLATION ? 0.55f : 0.38f))));
			cursor = quad(quads, cursor, x - size, y, x + size + 1, y + 1, colour);
			cursor = quad(quads, cursor, x, y - size, x + 1, y + size + 1, colour);
			if ((positionSeed & 3) >= 1 && size >= 2) {
				cursor = quad(quads, cursor, x - 1, y - 1, x, y, colour);
				cursor = quad(quads, cursor, x + 1, y - 1, x + 2, y, colour);
				cursor = quad(quads, cursor, x - 1, y + 1, x, y + 2, colour);
				cursor = quad(quads, cursor, x + 1, y + 1, x + 2, y + 2, colour);
			}
		}
		cache.quadLength = cursor;
		cache.frameId = frameId;
	}

	public static void border(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
		border(graphics, left, top, width, height, 2);
	}

	public static void border(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, int thickness) {
		border(graphics, left, top, width, height, thickness, null);
	}

	public static void border(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, int thickness, ScreenRectangle scissorArea) {
		border(graphics, left, top, width, height, thickness, scissorArea, mode());
	}

	static void constellationBorder(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, int thickness) {
		border(graphics, left, top, width, height, thickness, null, CONSTELLATION);
	}

	static void border(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, int thickness, ScreenRectangle scissorArea, int theme) {
		if (width <= 0 || height <= 0) return;
		int xPhase = Math.floorMod(left, 96);
		EffectKey key = new EffectKey(theme, 0, width, height, thickness * 128 + xPhase);
		EffectCache cache = EFFECT_CACHE.computeIfAbsent(key, ignored -> new EffectCache());
		long frameId = RainbowColours.frameId();
		if (cache.frameId != frameId) {
			cache.quads = borderQuads(theme, xPhase, width, height, thickness, cache.quads);
			cache.frameId = frameId;
		}
		GuiQuadBatchRenderState.submit(graphics, left, top, width, height,
			cache.quads, scissorArea);
	}

	private static int[] borderQuads(int theme, int xPhase, int width, int height,
			int thickness, int[] reusable) {
		int edge = Math.max(1, Math.min(thickness, Math.min(width, height)));
		int sideLength = Math.max(0, height - edge * 2);
		// Both horizontal edges sample the same screen-space gradient. Each vertical
		// edge is one constant-colour quad sampled at its own x coordinate, avoiding
		// direction changes and corner seams while reducing work on tall panels.
		int quadCount = width * 2 + (sideLength > 0 ? 2 : 0);
		int[] quads = reusable.length == quadCount * 5 ? reusable : new int[quadCount * 5];
		int cursor = 0;
		for (int x = 0; x < width; x++) {
			cursor = quad(quads, cursor, x, 0, x + 1, edge,
				colourAt(theme, xPhase + x, 0.55f));
		}
		for (int x = 0; x < width; x++) {
			cursor = quad(quads, cursor, x, height - edge, x + 1, height,
				colourAt(theme, xPhase + x, 0.55f));
		}
		if (sideLength > 0) {
			cursor = quad(quads, cursor, 0, edge, edge, height - edge,
				colourAt(theme, xPhase, 0.55f));
			cursor = quad(quads, cursor, width - edge, edge, width, height - edge,
				colourAt(theme, xPhase + width - 1, 0.55f));
		}
		return quads;
	}

	/** A smooth horizontal theme accent submitted as one GUI batch. */
	public static void bar(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height) {
		if (width <= 0 || height <= 0) return;
		int theme = mode();
		int xPhase = Math.floorMod(left, 96);
		EffectKey key = new EffectKey(theme, 1, width, height, xPhase);
		EffectCache cache = EFFECT_CACHE.computeIfAbsent(key, ignored -> new EffectCache());
		long frameId = RainbowColours.frameId();
		if (cache.frameId != frameId) {
			cache.quads = barQuads(theme, xPhase, width, height, cache.quads);
			cache.frameId = frameId;
		}
		GuiQuadBatchRenderState.submit(graphics, left, top, width, height, cache.quads);
	}

	private static int[] barQuads(int theme, int xPhase, int width, int height, int[] reusable) {
		int[] quads = reusable.length == width * 5 ? reusable : new int[width * 5];
		int cursor = 0;
		for (int x = 0; x < width; x++) {
			cursor = quad(quads, cursor, x, 0, x + 1, height,
				colourAt(theme, xPhase + x, 0.45f));
		}
		return quads;
	}

	public static int accent(int screenX) {
		return colourAt(mode(), screenX, 0.5f);
	}

	static int colourAt(float screenX, float saturation) {
		return colourAt(mode(), screenX, saturation);
	}

	static int colourAt(int theme, float screenX, float saturation) {
		if (theme != CONSTELLATION) return UIDraw.rainbowAt(Math.round(screenX), saturation);
		long frameTime = RainbowColours.frameTime(RainbowColours.frameId());
		float animated = screenX / 138f
			+ Math.floorMod(frameTime, ASTRAL_CYCLE_MS) / (float) ASTRAL_CYCLE_MS;
		float wrapped = animated - (float) Math.floor(animated);
		float scaled = wrapped * CONSTELLATION_COLOURS.length;
		int index = (int) scaled;
		int next = (index + 1) % CONSTELLATION_COLOURS.length;
		int blended = blend(CONSTELLATION_COLOURS[index], CONSTELLATION_COLOURS[next], scaled - index);
		return 0xFF000000 | desaturate(blended, saturation);
	}

	static int phaseBucket(int theme, int buckets) {
		int count = Math.max(1, buckets);
		if (theme != CONSTELLATION) return RainbowColours.phaseBucket(count);
		long frameTime = RainbowColours.frameTime(RainbowColours.frameId());
		return (int) (Math.floorMod(frameTime, ASTRAL_CYCLE_MS) * count
			/ ASTRAL_CYCLE_MS);
	}

	private static int withAlpha(int colour, int alpha) {
		return Math.clamp(alpha, 0, 255) << 24 | colour & 0xFFFFFF;
	}

	private static int blend(int from, int to, float amount) {
		int red = Math.round(((from >>> 16) & 0xFF) * (1f - amount) + ((to >>> 16) & 0xFF) * amount);
		int green = Math.round(((from >>> 8) & 0xFF) * (1f - amount) + ((to >>> 8) & 0xFF) * amount);
		int blue = Math.round((from & 0xFF) * (1f - amount) + (to & 0xFF) * amount);
		return red << 16 | green << 8 | blue;
	}

	private static int desaturate(int colour, float saturation) {
		float amount = Math.clamp(saturation, 0f, 1f);
		int red = colour >>> 16 & 0xFF;
		int green = colour >>> 8 & 0xFF;
		int blue = colour & 0xFF;
		int grey = Math.round(red * 0.299f + green * 0.587f + blue * 0.114f);
		return Math.round(grey + (red - grey) * amount) << 16
			| Math.round(grey + (green - grey) * amount) << 8
			| Math.round(grey + (blue - grey) * amount);
	}

	private static int quad(int[] quads, int cursor, int x0, int y0,
			int x1, int y1, int colour) {
		quads[cursor++] = x0;
		quads[cursor++] = y0;
		quads[cursor++] = x1;
		quads[cursor++] = y1;
		quads[cursor++] = colour;
		return cursor;
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
