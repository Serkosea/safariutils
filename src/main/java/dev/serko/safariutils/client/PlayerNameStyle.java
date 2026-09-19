package dev.serko.safariutils.client;

import dev.serko.safariutils.api.PartyItemSyncProviders;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/** Applies the private UUID-backed rainbow identity style anywhere player text is rendered. */
final class PlayerNameStyle {
	private PlayerNameStyle() { }

	/** Draws mixed ordinary/rainbow text and returns whether a whitelisted name was present. */
	static boolean drawIfPresent(GuiGraphicsExtractor graphics, Font font, Component component,
			int x, int y, int fallbackColour) {
		String text = component.getString();
		Set<String> names = PartyItemSyncProviders.whitelistedNames();
		if (text.isEmpty() || names.isEmpty()) return false;
		String lower = text.toLowerCase(Locale.ROOT);
		int cursor = x;
		int from = 0;
		boolean found = false;
		while (from < text.length()) {
			String match = null;
			int at = text.length();
			for (String candidate : names) {
				int candidateAt = lower.indexOf(candidate.toLowerCase(Locale.ROOT), from);
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
			UIDraw.rainbowText(graphics, font, displayed, cursor, y, 0.45f);
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
