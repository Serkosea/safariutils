package dev.serko.safariutils.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Global novelty themes unlocked with the Advanced category. */
public final class SpecialTheme {
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

	private record StarKey(int width, int height, int density) { }
	private record EffectKey(int type, int width, int height, int variant) { }
	private static final class EffectCache {
		private long frameId = Long.MIN_VALUE;
		private int[] quads = new int[0];
	}
	private static final class StarCache {
		private final int count;
		private final long[] seeds;
		private final long[] lives;
		private final long[] offsets;
		private long frameId = Long.MIN_VALUE;
		private final int[] quads;
		private int quadLength;

		private StarCache(int count) {
			this.count = count;
			this.seeds = new long[count];
			this.lives = new long[count];
			this.offsets = new long[count];
			this.quads = new int[count * 6 * 5];
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
		return ConfigManager.get().advanced.specialTheme == 1;
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
		UIDraw.rainbowText(graphics, font, component, x, y, 0.5f);
	}

	/** Small deterministic twinkles behind panel text, with no per-frame allocations. */
	public static void stars(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
		stars(graphics, left, top, width, height, 1f);
	}

	public static void stars(GuiGraphicsExtractor graphics, int left, int top,
						 int width, int height, float density) {
		stars(graphics, left, top, width, height, density, false);
	}

	/** Cached star field which can also be used by the one-time theme unlock preview. */
	static void stars(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height, float density, boolean force) {
		if ((!force && !rainbow()) || width < 8 || height < 8) return;
		int densityKey = Math.max(1, Math.round(density * 100f));
		StarKey key = new StarKey(width, height, densityKey);
		StarCache cache = STAR_CACHE.computeIfAbsent(key, ignored -> {
			float actualDensity = densityKey / 100f;
			// The layout is generated once and only its compact quad batch changes at
			// 25 FPS, so the denser field does not add widget or render-state churn.
			int count = Math.clamp(Math.round(width * height / 760f * actualDensity),
				Math.max(1, Math.round(13 * actualDensity)),
				Math.max(1, Math.round(120 * actualDensity)));
			return new StarCache(count);
		});
		long frameId = RainbowColours.frameId();
		if (cache.frameId != frameId) rebuildStars(cache, width, height, frameId);
		GuiQuadBatchRenderState.submit(graphics, left, top, width, height,
			cache.quads, cache.quadLength);
	}

	private static void rebuildStars(StarCache cache, int width, int height, long frameId) {
		long now = RainbowColours.frameTime(frameId);
		int[] quads = cache.quads;
		int cursor = 0;
		for (int i = 0; i < cache.count; i++) {
			long seed = cache.seeds[i];
			long life = cache.lives[i];
			long age = Math.floorMod(now + cache.offsets[i], life);
			long generation = Math.floorDiv(now + cache.offsets[i], life);
			long positionSeed = mix(seed ^ generation * 0xD1B54A32D192ED03L);
			float progress = age / (float) life;
			float alpha = 1f - Math.abs(progress * 2f - 1f);
			int x = 3 + (int) (positive(positionSeed >>> 7) % Math.max(1, width - 6));
			int y = 3 + (int) (positive(positionSeed >>> 29) % Math.max(1, height - 6));
			int size = 1 + (int) (positive(positionSeed >>> 43) % 3);
			int colour = RainbowColours.shared((x + y * 0.35f) / 96f,
				0.35f, 0.18f + alpha * 0.38f);
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
		if (width <= 0 || height <= 0) return;
		int xPhase = Math.floorMod(left, 96);
		EffectKey key = new EffectKey(0, width, height, thickness * 128 + xPhase);
		EffectCache cache = EFFECT_CACHE.computeIfAbsent(key, ignored -> new EffectCache());
		long frameId = RainbowColours.frameId();
		if (cache.frameId != frameId) {
			cache.quads = borderQuads(xPhase, width, height, thickness, cache.quads);
			cache.frameId = frameId;
		}
		GuiQuadBatchRenderState.submit(graphics, left, top, width, height,
			cache.quads, scissorArea);
	}

	private static int[] borderQuads(int xPhase, int width, int height,
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
				UIDraw.rainbowAt(xPhase + x, 0.55f));
		}
		for (int x = 0; x < width; x++) {
			cursor = quad(quads, cursor, x, height - edge, x + 1, height,
				UIDraw.rainbowAt(xPhase + x, 0.55f));
		}
		if (sideLength > 0) {
			cursor = quad(quads, cursor, 0, edge, edge, height - edge,
				UIDraw.rainbowAt(xPhase, 0.55f));
			cursor = quad(quads, cursor, width - edge, edge, width, height - edge,
				UIDraw.rainbowAt(xPhase + width - 1, 0.55f));
		}
		return quads;
	}

	/** A smooth horizontal theme accent submitted as one GUI batch. */
	public static void bar(GuiGraphicsExtractor graphics, int left, int top,
			int width, int height) {
		if (width <= 0 || height <= 0) return;
		int xPhase = Math.floorMod(left, 96);
		EffectKey key = new EffectKey(1, width, height, xPhase);
		EffectCache cache = EFFECT_CACHE.computeIfAbsent(key, ignored -> new EffectCache());
		long frameId = RainbowColours.frameId();
		if (cache.frameId != frameId) {
			cache.quads = barQuads(xPhase, width, height, cache.quads);
			cache.frameId = frameId;
		}
		GuiQuadBatchRenderState.submit(graphics, left, top, width, height, cache.quads);
	}

	private static int[] barQuads(int xPhase, int width, int height, int[] reusable) {
		int[] quads = reusable.length == width * 5 ? reusable : new int[width * 5];
		int cursor = 0;
		for (int x = 0; x < width; x++) {
			cursor = quad(quads, cursor, x, 0, x + 1, height,
				UIDraw.rainbowAt(xPhase + x, 0.45f));
		}
		return quads;
	}

	public static int accent(int screenX) {
		return UIDraw.rainbowAt(screenX, 0.5f);
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
