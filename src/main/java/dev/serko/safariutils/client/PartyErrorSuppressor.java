package dev.serko.safariutils.client;

import net.minecraft.network.chat.Component;

/** Hides Hypixel's not-in-a-party response only after a SafariUtils party send. */
public final class PartyErrorSuppressor {
	private static final String NOT_IN_PARTY = "You are not in a party right now.";
	private static final long RESPONSE_WINDOW_MILLIS = 3_000L;
	private static final dev.serko.safariutils.state.MonotonicCooldowns<Boolean> responses =
		new dev.serko.safariutils.state.MonotonicCooldowns<>(System::nanoTime);

	private PartyErrorSuppressor() {
	}

	public static void expectResponse() {
		responses.mark(Boolean.TRUE);
	}

	public static void clear() { responses.clear(); }

	public static boolean allow(Component message, boolean overlay) {
		if (overlay || responses.remainingMillis(Boolean.TRUE, RESPONSE_WINDOW_MILLIS) == 0) return true;
		String line = message.getString().trim();
		if (NOT_IN_PARTY.equals(line)) return false;
		return !isDivider(line);
	}

	private static boolean isDivider(String line) {
		if (line.length() < 8) return false;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (character != '-' && character != '═' && character != '━' && character != '▬') return false;
		}
		return true;
	}
}
