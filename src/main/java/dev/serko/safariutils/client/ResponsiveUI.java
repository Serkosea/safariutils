package dev.serko.safariutils.client;

/** Shared sizing rules for custom screens and HUDs on smaller GUI canvases. */
final class ResponsiveUI {
	private static final int REFERENCE_WIDTH = 854;
	private static final int REFERENCE_HEIGHT = 480;

	private ResponsiveUI() {}

	/** Keeps the normal layout at full size and shrinks it proportionally when needed. */
	static float scale(int width, int height) {
		return Math.min(1f, Math.min(width / (float) REFERENCE_WIDTH,
			height / (float) REFERENCE_HEIGHT));
	}

	static int logicalWidth(int width, float scale) {
		return Math.max(width, Math.round(width / scale));
	}

	static int logicalHeight(int height, float scale) {
		return Math.max(height, Math.round(height / scale));
	}
}
