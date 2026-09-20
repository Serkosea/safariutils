package dev.serko.safariutils.client;

import java.util.HashMap;
import java.util.Map;

/** Shared, allocation-free rainbow clock and colour lookup tables. */
final class RainbowColours {
	static final long FRAME_MILLIS = 40L;
	private static final long CYCLE_MILLIS = 4_000L;
	private static final int COLOURS = 256;
	private static final Map<Integer, int[]> PALETTES = new HashMap<>();

	private RainbowColours() {
	}

	static long frameId() {
		return System.currentTimeMillis() / FRAME_MILLIS;
	}

	static long frameTime(long frameId) {
		return frameId * FRAME_MILLIS;
	}

	static float phase(long frameId) {
		return Math.floorMod(frameTime(frameId), CYCLE_MILLIS) / (float) CYCLE_MILLIS;
	}

	/** Finite animation phase used by caches that should repeat rather than churn forever. */
	static int phaseBucket(int buckets) {
		long framesPerCycle = Math.max(1L, CYCLE_MILLIS / FRAME_MILLIS);
		long frameInCycle = Math.floorMod(frameId(), framesPerCycle);
		return (int) (frameInCycle * Math.max(1, buckets) / framesPerCycle);
	}

	static int shared(float offset, float saturation) {
		return shared(offset, saturation, 1f);
	}

	static int shared(float offset, float saturation, float alpha) {
		return colour(phase(frameId()) + offset, saturation, alpha);
	}

	static int phased(float phase, float offset, float saturation, float alpha) {
		return colour(phase + offset, saturation, alpha);
	}

	private static int colour(float hue, float saturation, float alpha) {
		int[] palette = palette(saturation);
		int index = Math.floorMod(Math.round(hue * COLOURS), COLOURS);
		int a = Math.clamp(Math.round(alpha * 255f), 0, 255);
		return a << 24 | palette[index];
	}

	private static int[] palette(float saturation) {
		int key = Math.clamp(Math.round(saturation * 100f), 0, 100);
		return PALETTES.computeIfAbsent(key, ignored -> {
			int[] colours = new int[COLOURS];
			float actual = key / 100f;
			for (int i = 0; i < colours.length; i++) {
				colours[i] = java.awt.Color.HSBtoRGB(i / (float) COLOURS, actual, 1f) & 0xFFFFFF;
			}
			return colours;
		});
	}
}
