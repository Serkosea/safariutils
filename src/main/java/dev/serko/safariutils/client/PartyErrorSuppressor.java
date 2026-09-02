package dev.serko.safariutils.client;

import net.minecraft.network.chat.Component;

/** Hides Hypixel's not-in-a-party response only after a Safari Utils party send. */
public final class PartyErrorSuppressor {
	private static final String NOT_IN_PARTY = "You are not in a party right now.";
	private static final long RESPONSE_WINDOW_MILLIS = 3_000L;
	private static long expectedUntil;

	private PartyErrorSuppressor() {
	}

	public static void expectResponse() {
		expectedUntil = System.currentTimeMillis() + RESPONSE_WINDOW_MILLIS;
	}

	public static boolean allow(Component message, boolean overlay) {
		if (overlay || System.currentTimeMillis() > expectedUntil) return true;
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
