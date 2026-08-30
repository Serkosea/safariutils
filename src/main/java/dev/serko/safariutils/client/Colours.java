package dev.serko.safariutils.client;

/**
 * Reads both Safari Utils hex colours and the legacy {@code speed:alpha:r:g:b}
 * representation so existing settings migrate without visible changes.
 */
public final class Colours {

	private Colours() {
	}

	/**
	 * The packed ARGB for a stored colour, or {@code fallback} if it cannot be read.
	 *
	 * <p>A hand-edited config file can hold anything, and a colour that fails to parse
	 * should cost the mark its colour, not the frame.
	 */
	public static int argb(String stored, int fallback) {
		if (stored == null || stored.isBlank()) return fallback;
		try {
			int argb;
			if (stored.startsWith("#")) {
				long parsed = Long.parseLong(stored.substring(1), 16);
				argb = stored.length() <= 7 ? 0xFF000000 | (int) parsed : (int) parsed;
			} else {
				String[] pieces = stored.split(":");
				if (pieces.length != 5) return fallback;
				int speed = Integer.parseInt(pieces[0]);
				int alpha = clamp(Integer.parseInt(pieces[1]));
				int red = clamp(Integer.parseInt(pieces[2]));
				int green = clamp(Integer.parseInt(pieces[3]));
				int blue = clamp(Integer.parseInt(pieces[4]));
				if (speed != 0) {
					float hue = (System.currentTimeMillis() % Math.max(250L, 20_000L / Math.abs(speed)))
						/ (float) Math.max(250L, 20_000L / Math.abs(speed));
					int rgb = java.awt.Color.HSBtoRGB(hue, 0.8f, 1f);
					red = rgb >> 16 & 0xFF;
					green = rgb >> 8 & 0xFF;
					blue = rgb & 0xFF;
				}
				argb = alpha << 24 | red << 16 | green << 8 | blue;
			}
			// A colour with no alpha would draw nothing at all, which reads as the
			// feature being broken rather than as a deliberate choice.
			return (argb >>> 24) == 0 ? 0xFF000000 | argb : argb;
		} catch (RuntimeException malformed) {
			return fallback;
		}
	}

	public static String stored(int argb) {
		return "0:%d:%d:%d:%d".formatted(argb >>> 24, argb >> 16 & 0xFF,
			argb >> 8 & 0xFF, argb & 0xFF);
	}

	private static int clamp(int value) {
		return Math.clamp(value, 0, 255);
	}
}
