package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SharedSparklingProviders;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Applies the private UUID-backed rainbow identity style anywhere player text is rendered. */
public final class PlayerNameStyle {
	private static final long REFRESH_CHECK_MILLIS = 5 * 60_000L;
	private static final int REFRESH_LIMIT = 512;
	private static final Map<String, Long> refreshAfter = new ConcurrentHashMap<>();
	private static final int OBSERVED_LIMIT = 512;
	private static final Map<String, Integer> observedColours = new LinkedHashMap<>(64, 0.75f, true);

	private PlayerNameStyle() { }

	/** Live rank evidence wins; cached/API data fills names that are not currently visible. */
	public static int colour(String name) {
		int live = PartyObjectiveHud.observedPlayerNameColour(name);
		if (live != -1) return live;
		int observed = observedColour(name);
		if (observed != -1) return observed;
		String uuid = CanonicalPlayerNames.uuid(name);
		PlayerIdentityStore.RankSnapshot persisted =
			PlayerIdentityStore.rankSnapshot(name, uuid);
		int colour = persisted.colour();
		refreshColourIfDue(name, uuid, persisted);
		return colour == -1 ? 0xFFFFFFFF : colour;
	}

	/** Records rank evidence already visible in chat without making a network request. */
	public static void rememberChatRank(String name, String message) {
		if (name == null || name.isBlank() || message == null) return;
		String lowerMessage = message.toLowerCase(Locale.ROOT);
		int colon = lowerMessage.indexOf(':');
		int nameAt = lowerMessage.lastIndexOf(name.toLowerCase(Locale.ROOT),
			colon < 0 ? lowerMessage.length() : colon);
		String beforeName = message.substring(0, Math.max(0, nameAt));
		String upper = beforeName.toUpperCase(Locale.ROOT);
		int colour;
		int permanentLevel;
		if (upper.contains("[MVP++]")) {
			colour = 0xFFFFAA00;
			permanentLevel = -1;
		} else if (upper.contains("[MVP+]")) {
			colour = 0xFF55FFFF;
			permanentLevel = 4;
		} else if (upper.contains("[MVP]")) {
			colour = 0xFF55FFFF;
			permanentLevel = 3;
		} else if (upper.contains("[VIP+]")) {
			colour = 0xFF55FF55;
			permanentLevel = 2;
		} else if (upper.contains("[VIP]")) {
			colour = 0xFF55FF55;
			permanentLevel = 1;
		} else if (upper.matches("(?s).*\\[(?:YOUTUBE|ADMIN|MODERATOR|MOD|GAME_MASTER|GM|HELPER|OWNER)]\\s*$")) {
			colour = 0xFFFF5555;
			permanentLevel = -1;
		} else {
			colour = 0xFFAAAAAA;
			permanentLevel = 0;
		}
		String key = name.toLowerCase(Locale.ROOT);
		synchronized (observedColours) {
			observedColours.put(key, colour);
			while (observedColours.size() > OBSERVED_LIMIT) {
				var iterator = observedColours.keySet().iterator();
				if (!iterator.hasNext()) break;
				iterator.next();
				iterator.remove();
			}
		}
		String uuid = CanonicalPlayerNames.uuid(name);
		if (!uuid.isEmpty()) PlayerIdentityStore.rememberLiveRank(name, uuid, colour, permanentLevel);
	}

	private static int observedColour(String name) {
		if (name == null) return -1;
		synchronized (observedColours) {
			return observedColours.getOrDefault(name.toLowerCase(Locale.ROOT), -1);
		}
	}

	static void refreshColour(String name) {
		if (name == null || name.isBlank()) return;
		String uuid = CanonicalPlayerNames.uuid(name);
		PlayerIdentityStore.RankSnapshot persisted =
			PlayerIdentityStore.rankSnapshot(name, uuid);
		if (persisted.fresh(System.currentTimeMillis())) return;
		refreshAfter.put(name.toLowerCase(Locale.ROOT),
			System.currentTimeMillis() + REFRESH_CHECK_MILLIS);
		SharedSparklingProviders.refreshNameColour(name, uuid);
	}

	private static void refreshColourIfDue(String name, String uuid,
			PlayerIdentityStore.RankSnapshot persisted) {
		if (!SharedSparklingProviders.available() || name == null || name.isBlank()) return;
		String key = name.toLowerCase(Locale.ROOT);
		long now = System.currentTimeMillis();
		if (persisted.fresh(now)) return;
		if (now < refreshAfter.getOrDefault(key, 0L)) return;
		if (!refreshAfter.containsKey(key) && refreshAfter.size() >= REFRESH_LIMIT) {
			var iterator = refreshAfter.keySet().iterator();
			if (iterator.hasNext()) refreshAfter.remove(iterator.next());
		}
		refreshAfter.put(key, now + REFRESH_CHECK_MILLIS);
		SharedSparklingProviders.refreshNameColour(name, uuid);
	}

	/** Draws one canonical username with UUID-backed owner styling or its rank colour. */
	static String drawName(GuiGraphicsExtractor graphics, Font font, String name, int x, int y) {
		String displayed = CanonicalPlayerNames.display(name);
		if (SpecialTheme.rainbow()) {
			SpecialTheme.rainbowText(graphics, font, displayed, x, y);
		} else if (SharedSparklingProviders.specialName(displayed)) {
			UIDraw.rainbowText(graphics, font, displayed, x, y, 0.45f);
		} else graphics.text(font, Component.literal(displayed), x, y, colour(displayed));
		return displayed;
	}

	/** Draws mixed ordinary/rainbow text and returns whether a special name was present. */
	static boolean drawIfPresent(GuiGraphicsExtractor graphics, Font font, Component component,
			int x, int y, int fallbackColour) {
		String text = component.getString();
		Set<String> names = SharedSparklingProviders.specialNames();
		if (text.isEmpty() || names.isEmpty()) return false;
		String lower = text.toLowerCase(Locale.ROOT);
		int cursor = x;
		int from = 0;
		boolean found = false;
		while (from < text.length()) {
			String match = null;
			int at = text.length();
			for (String candidate : names) {
				// Providers expose normalized lower-case names, avoiding repeated case
				// conversion for every HUD text segment and rendered frame.
				int candidateAt = lower.indexOf(candidate, from);
				if (candidateAt >= 0 && (candidateAt < at
					|| candidateAt == at && (match == null || candidate.length() > match.length()))) {
					at = candidateAt;
					match = candidate;
				}
			}
			if (match == null) break;
			found = true;
			if (at > from) {
				String ordinary = text.substring(from, at);
				Component segment = Component.literal(ordinary).withStyle(component.getStyle());
				graphics.text(font, segment, cursor, y, fallbackColour);
				cursor += font.width(segment);
			}
			String displayed = text.substring(at, at + match.length());
			if (SpecialTheme.rainbow()) {
				SpecialTheme.rainbowText(graphics, font, displayed, cursor, y);
			} else UIDraw.rainbowText(graphics, font, displayed, cursor, y, 0.45f);
			cursor += font.width(displayed);
			from = at + match.length();
		}
		if (!found) return false;
		if (from < text.length()) {
			Component remainder = Component.literal(text.substring(from)).withStyle(component.getStyle());
			graphics.text(font, remainder, cursor, y, fallbackColour);
		}
		return true;
	}
}
