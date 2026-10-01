package dev.serko.safariutils.client;

/** Shared breakpoints and sizing rules for custom screens and HUDs. */
final class ResponsiveUI {
	private static final int REFERENCE_WIDTH = 854;
	private static final int REFERENCE_HEIGHT = 480;
	private static final int COMPACT_WIDTH = 600;
	private static final int WIDE_WIDTH = 820;

	private ResponsiveUI() {}

	/**
	 * Last-resort scale for dense data screens. Layout code should reflow first and
	 * use this only when the remaining canvas cannot hold its minimum content.
	 */
	static float scale(int width, int height) {
		return Math.min(1f, Math.min(width / (float) REFERENCE_WIDTH,
			height / (float) REFERENCE_HEIGHT));
	}

	static boolean compact(int width) {
		return width < COMPACT_WIDTH;
	}

	static boolean wide(int width) {
		return width >= WIDE_WIDTH;
	}

	static int gutter(int width) {
		return wide(width) ? 20 : compact(width) ? 8 : 14;
	}

	static int panelWidth(int width, int preferred, int minimum) {
		int gutter = gutter(width);
		int available = Math.max(1, width - gutter * 2);
		return Math.max(Math.min(minimum, available), Math.min(preferred, available));
	}

	/** Fits a fixed minimum layout inside a canvas while preserving an outer gutter. */
	static float fitScale(int width, int height, int contentWidth, int contentHeight, int gutter) {
		int paddedWidth = Math.max(1, contentWidth + gutter * 2);
		int paddedHeight = Math.max(1, contentHeight + gutter * 2);
		return Math.min(1f, Math.min(width / (float) paddedWidth,
			height / (float) paddedHeight));
	}

	static int columns(int availableWidth, int preferredWidth, int minimumWidth, int maximum) {
		if (availableWidth <= 0) return 1;
		int preferred = Math.max(1, availableWidth / Math.max(1, preferredWidth));
		int permitted = Math.max(1, availableWidth / Math.max(1, minimumWidth));
		return Math.max(1, Math.min(maximum, Math.min(preferred, permitted)));
	}

	static int logicalWidth(int width, float scale) {
		return Math.max(width, Math.round(width / scale));
	}

	static int logicalHeight(int height, float scale) {
		return Math.max(height, Math.round(height / scale));
	}
}
