package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SharedSparklingProviders;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/** Applies the private UUID-backed rainbow identity style anywhere player text is rendered. */
final class PlayerNameStyle {
	private PlayerNameStyle() { }

	/** Private rank data wins; live tab-list colour is the public, zero-request fallback. */
	static int colour(String name) {
		int network = SharedSparklingProviders.nameColour(name);
		return network == -1 ? PartyObjectiveHud.playerNameColour(name) : network;
	}

	static void refreshColour(String name) {
		SharedSparklingProviders.refreshNameColour(name, CanonicalPlayerNames.uuid(name));
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
