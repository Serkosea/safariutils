package dev.serko.safariutils.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;

/** Builds client-only SafariUtils messages with one recognizable fixed palette. */
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

    /** Separate notices preserve semantic colors and each receive the selected prefix. */
    public static void sendLines(java.util.List<Component> lines) {
        for (Component line : lines) {
            MutableComponent message = SpecialTheme.rainbow() ? themedMessage("",false).copy() : prefix();
            ClientCompat.addSystemMessage(message.append(line));
        }
    }

    /** Bordered API report; semantic colors and hover events are retained on each body row. */
    static java.util.List<Component> reportLines(java.util.List<Component> lines) {
        return reportLines(lines, " ");
    }
    static java.util.List<Component> reportLines(java.util.List<Component> lines, String bodyPrefix) {
        if(lines.isEmpty())return java.util.List.of();
        var result=new java.util.ArrayList<Component>();
        // Strikethrough joins the glyph spacing into a continuous, compact rule.
        Component rule=Component.literal("-".repeat(28)).withColor(BRACKET)
            .withStyle(ChatFormatting.STRIKETHROUGH);
        result.add(rule);result.add(prefixPreview(ConfigManager.get().display.clientMessagePrefix).copy().append(lines.getFirst()));
        for(int i=1;i<lines.size();i++) result.add(Component.literal(bodyPrefix).withColor(BRACKET).append(lines.get(i)));
        result.add(rule.copy());return java.util.List.copyOf(result);
    }
    static void sendReport(java.util.List<Component> lines) {
        if (!lines.isEmpty()) ClientCompat.addSystemMessage(reportMessage(lines));
    }

    /** Preserve the complete frame as one history/compaction unit, including hover styles. */
    static Component reportMessage(java.util.List<Component> lines) {
        return reportMessage(lines, " ");
    }
    private static Component reportMessage(java.util.List<Component> lines, String bodyPrefix) {
        MutableComponent message = Component.empty();
        boolean first = true;
        for (Component line : reportLines(lines, bodyPrefix)) {
            if (!first) message.append(Component.literal("\n"));
            message.append(line);
            first = false;
        }
        return message;
    }

    /** A single report: only the first line gets the selected prefix. */
    public static void sendBlock(java.util.List<Component> lines) {
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            if (index == 0) {
                MutableComponent start = SpecialTheme.rainbow() ? themedMessage("", false).copy() : prefix();
                line = start.append(line);
            }
            ClientCompat.addSystemMessage(line);
        }
    }

	public static void sendUpdate(String available, URI release, String linkLabel) {
		String body = "SafariUtils v" + available + " is available ";
		String linkText = "[" + linkLabel + "]";
		MutableComponent message;
		if (SpecialTheme.rainbow()) {
			message = themedMessage(body, false).copy()
				.append(themedLink(linkText, release, linkLabel));
		} else {
			message = prefix()
				.append(Component.literal("SafariUtils ").withStyle(style -> style.withColor(INFO)))
				.append(Component.literal("v" + available).withStyle(style ->
					style.withColor(WARNING).withBold(true)))
				.append(Component.literal(" is available ").withStyle(style -> style.withColor(INFO)))
				.append(link(linkText, release, linkLabel));
		}
		ClientCompat.addSystemMessage(message);
	}

	public static Component prefixed(String text, Tone tone) {
		String message = withoutTrailingPeriod(text);
		if (SpecialTheme.rainbow()) return themedMessage(message, false);
		return prefix().append(Component.literal(message)
			.withStyle(style -> style.withColor(toneColour(tone))));
	}

	public static Component prefixed(String text, ChatFormatting formatting) {
		return prefixed(text, tone(formatting));
	}

	public static Component header(String text) {
		String message = withoutTrailingPeriod(text);
		if (SpecialTheme.rainbow()) return themedMessage(message, true);
		return prefix()
			.append(Component.literal(message).withStyle(style ->
				style.withColor(WARNING).withBold(true)));
	}

	/**
	 * Builds one immutable chat component at send time. Unlike animated GUI text,
	 * retained chat lines never need to be rebuilt while they remain visible.
	 */
	private static Component themedMessage(String text, boolean boldBody) {
        return themedMessage(text, boldBody, ConfigManager.get().display.clientMessagePrefix);
    }

    private static Component themedMessage(String text, boolean boldBody, int choice) {
        String label = "[" + prefixName(choice) + "] ";
        String complete = label + text;
        int bodyStart = label.length();
		Font font = Minecraft.getInstance().font;
		MutableComponent result = Component.empty();
		int cursor = 0;
		for (int index = 0; index < complete.length(); index++) {
			String character = String.valueOf(complete.charAt(index));
			int colour = SpecialTheme.colourAt(cursor, 0.5f) & 0xFFFFFF;
			boolean bold = boldBody && index >= bodyStart;
			Component segment = Component.literal(character).withStyle(style ->
				style.withColor(colour).withBold(bold));
			result.append(segment);
			cursor += font.width(segment);
		}
		return result;
	}

	private static Component themedLink(String text, URI release, String linkLabel) {
		Font font = Minecraft.getInstance().font;
		ClickEvent click = new ClickEvent.OpenUrl(release);
		HoverEvent hover = new HoverEvent.ShowText(Component.literal("Open " + linkLabel));
		MutableComponent result = Component.empty();
		int cursor = 0;
		for (int index = 0; index < text.length(); index++) {
			String character = String.valueOf(text.charAt(index));
			int colour = SpecialTheme.colourAt(cursor, 0.5f) & 0xFFFFFF;
			Component segment = Component.literal(character).withStyle(style -> style
				.withColor(colour).withBold(true).withUnderlined(true)
				.withClickEvent(click).withHoverEvent(hover));
			result.append(segment);
			cursor += font.width(segment);
		}
		return result;
	}

	private static Component link(String text, URI release, String linkLabel) {
		return Component.literal(text).withStyle(style -> style
			.withColor(NAME_LEFT).withBold(true).withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenUrl(release))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open " + linkLabel))));
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

	/** Keeps lookup details in diagnostics while presenting stable, useful UI errors. */
	public static String playerLookupFailure(Throwable error) {
        // Walk wrappers without letting a malformed cause cycle hang the game thread.
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if ((cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                    && cause.getCause() != null) continue;
            if (cause instanceof dev.serko.safariutils.api.LookupCooldownException cooldown)
                return "Lookup cooldown — try again in " + cooldown.seconds() + "s";
            String detail = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (detail.contains("no skyblock profiles") || detail.contains("no selected skyblock profile"))
                return "This player has no available SkyBlock profile";
            if (detail.contains("player is unavailable in the selected skyblock profile"))
                return "This player's data is unavailable in their selected SkyBlock profile";
            if (cause instanceof dev.serko.safariutils.api.ServiceRequestException response) {
                int status = response.status();
                String wait = response.retrySeconds() > 0 ? "; try again in " + response.retrySeconds() + "s" : "; try again shortly";
                if (status == 429) return "Lookup service request limit reached" + wait;
                if (status == 401) return "Lookup service session expired; retry to reconnect";
                if (status == 403) return "Lookup service denied this request; retry later or report it if it continues";
                if (status == 404) return "The requested profile or lookup endpoint was not found; check the name and mod version";
                if (status >= 500) return "Lookup service is temporarily unavailable" + wait;
                if (status == 400) return "Lookup service rejected the request; check the username or update the mod";
            }
            if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.util.concurrent.TimeoutException)
                return "Player lookup timed out; try again shortly";
            if (cause instanceof java.net.UnknownHostException || cause instanceof java.net.ConnectException
                    || cause instanceof javax.net.ssl.SSLException)
                return "Could not connect securely to the lookup service; check your connection and try again";
            if (cause instanceof java.util.concurrent.RejectedExecutionException)
                return "Lookup requests are busy; wait for the current requests to finish";
            if (cause instanceof java.util.concurrent.CancellationException || cause instanceof InterruptedException)
                return "Player lookup was cancelled; try again";
            String lower = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("invalid minecraft username")) return "Enter a Minecraft username using 1–16 letters, numbers, or underscores";
            if (lower.contains("player not found")) return "Minecraft player not found; check the username";
            if (lower.contains("already loading")) return "Another player lookup is still loading; wait for it to finish";
            if (lower.contains("player session changed")) return "Player lookup cancelled because your game session changed; try again";
            if (lower.contains("join a minecraft server")) return "Join a server before looking up a player";
            if (lower.contains("timed out")) return "Player lookup timed out; try again shortly";
            if (lower.contains("unavailable")) return "Lookup service is currently unavailable; try again shortly";
            if (lower.contains("interrupted") || lower.contains("stopped")) return "Player lookup was interrupted; try again after reconnecting";
            if (lower.contains("schema")) return "Lookup service response is incompatible; check for a mod update";
            if (lower.contains("malformed") || lower.contains("incomplete") || lower.contains("empty lookup")
                    || lower.contains("unexpected player") || lower.contains("omitted requested") || lower.contains("invalid minecraft profile"))
                return "Lookup service returned incomplete or invalid data; try again shortly";
            if (lower.contains("http 429")) return "Lookup service request limit reached; try again shortly";
            if (lower.matches(".*http 5\\d\\d.*")) return "Lookup service is temporarily unavailable; try again shortly";
        }
        return "Player lookup could not be completed; try again, and report it with your log if it continues";
    }

	/** Keep client notices consistent, including messages supplied by the private provider. */
	private static String withoutTrailingPeriod(String text) {
		String result = text.stripTrailing();
		int end = result.length();
		while (end > 0 && result.charAt(end - 1) == '.') end--;
		return result.substring(0, end);
	}

	private static MutableComponent prefix() {
        return prefix(ConfigManager.get().display.clientMessagePrefix);
    }

    private static String prefixName(int choice) { return choice == 1 ? "SU" : choice == 2 ? "Safari" : "SafariUtils"; }

    public static Component prefixPreview(int choice) {
        return SpecialTheme.rainbow() ? themedMessage("", false, choice) : prefix(choice);
    }

    private static MutableComponent prefix(int choice) {
		MutableComponent result = Component.literal("[").withStyle(style -> style.withColor(BRACKET));
		String name = prefixName(choice);
		for (int index = 0; index < name.length(); index++) {
			// All prefixes sample the original SafariUtils gradient without stretching it.
            float progress = index / 10f;
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
