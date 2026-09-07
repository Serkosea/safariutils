package dev.serko.safariutils.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Builds client-only Safari Utils messages with one recognizable fixed palette. */
public final class ClientMessages {
	public enum Tone { INFO, SUCCESS, WARNING, ERROR, MUTED }
	private static final int BRACKET = 0xFFD45A;
	private static final int NAME_LEFT = 0x8DE8FF;
	private static final int NAME_RIGHT = 0x557AFF;
	private static final int INFO = 0xF2F5FA;
	private static final int SUCCESS = 0x55FF88;
	private static final int WARNING = 0xFFC857;
	private static final int ERROR = 0xFF6677;
	private static final int MUTED = 0x9DA7B8;

	private ClientMessages() {
	}

	public static void send(String text, Tone tone) {
		ClientCompat.addSystemMessage(prefixed(text, tone));
	}

	public static Component prefixed(String text, Tone tone) {
		return prefix().append(Component.literal(withoutTrailingPeriod(text))
			.withStyle(style -> style.withColor(toneColour(tone))));
	}

	public static Component prefixed(String text, ChatFormatting formatting) {
		return prefixed(text, tone(formatting));
	}

	public static Component header(String text) {
		return prefix()
			.append(Component.literal(withoutTrailingPeriod(text)).withStyle(style ->
				style.withColor(WARNING).withBold(true)));
	}

	/** Turns common API outages into a useful message while preserving specific errors. */
	public static String apiFailure(String operation, Throwable error) {
		Throwable cause = error;
		while (cause != null && cause.getCause() != null) cause = cause.getCause();
		String detail = cause == null || cause.getMessage() == null
			? "" : cause.getMessage().strip();
		String lower = detail.toLowerCase(java.util.Locale.ROOT);
		boolean unavailable = cause instanceof java.net.http.HttpTimeoutException
			|| cause instanceof java.net.ConnectException
			|| cause instanceof java.net.UnknownHostException
			|| lower.contains("timed out")
			|| lower.matches(".*http 5\\d\\d.*");
		if (unavailable) return "Failed to " + operation + "; Hypixel API may be unavailable";
		return "Failed to " + operation + (detail.isEmpty() ? "" : ": " + detail);
	}

	/** Keep client notices consistent, including messages supplied by the private provider. */
	private static String withoutTrailingPeriod(String text) {
		String result = text.stripTrailing();
		int end = result.length();
		while (end > 0 && result.charAt(end - 1) == '.') end--;
		return result.substring(0, end);
	}

	private static MutableComponent prefix() {
		MutableComponent result = Component.literal("[").withStyle(style -> style.withColor(BRACKET));
		String name = "SafariUtils";
		for (int index = 0; index < name.length(); index++) {
			float progress = index / (float) Math.max(1, name.length() - 1);
			int colour = blend(NAME_LEFT, NAME_RIGHT, progress);
			result.append(Component.literal(String.valueOf(name.charAt(index)))
				.withStyle(style -> style.withColor(colour)));
		}
		return result.append(Component.literal("] ").withStyle(style -> style.withColor(BRACKET)));
	}

	private static int blend(int from, int to, float amount) {
		int red = Math.round(((from >> 16) & 0xFF) * (1f - amount) + ((to >> 16) & 0xFF) * amount);
		int green = Math.round(((from >> 8) & 0xFF) * (1f - amount) + ((to >> 8) & 0xFF) * amount);
		int blue = Math.round((from & 0xFF) * (1f - amount) + (to & 0xFF) * amount);
		return red << 16 | green << 8 | blue;
	}

	public static int colour(Tone tone) {
		return toneColour(tone);
	}

	private static int toneColour(Tone tone) {
		return switch (tone) {
			case INFO -> INFO;
			case SUCCESS -> SUCCESS;
			case WARNING -> WARNING;
			case ERROR -> ERROR;
			case MUTED -> MUTED;
		};
	}

	private static Tone tone(ChatFormatting formatting) {
		if (formatting == ChatFormatting.GREEN || formatting == ChatFormatting.AQUA) return Tone.SUCCESS;
		if (formatting == ChatFormatting.RED || formatting == ChatFormatting.DARK_RED) return Tone.ERROR;
		if (formatting == ChatFormatting.YELLOW || formatting == ChatFormatting.GOLD) return Tone.WARNING;
		if (formatting == ChatFormatting.GRAY || formatting == ChatFormatting.DARK_GRAY) return Tone.MUTED;
		return Tone.INFO;
	}

}
