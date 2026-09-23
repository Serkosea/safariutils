package dev.serko.safariutils.client;

import dev.serko.safariutils.parse.ChatParser;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Display-only filtering for recognized Safari server messages. */
public final class ChatMessageFilter {
	private static final int CRITTER_CATCHES = 1;
	private static final int LOOT_SHARED_CRITTERS = 1 << 1;
	private static final int FLOOR_DROPS = 1 << 2;
	private static final int MANAGER = 1 << 3;
	private static final int GEMZIE = 1 << 4;
	private static final int MOUNDS = 1 << 5;
	private static final int WUMPA = 1 << 6;
	private static final int COLD = 1 << 7;
	private static final int DOOMSPIRAL = 1 << 8;
	private static final int HIDEYHO_DIALOGUE = 1 << 9;
	private static final int BIRD_SPAWNS = 1 << 10;
	private static final int EMPTY_NESTS = 1 << 11;
	private static final java.util.Set<String> MOUND_MESSAGES = java.util.Set.of(
		"Small cracks begin to form in the mound...",
		"The cracks seem to be getting larger, keep hitting it!",
		"Chunks of the mound begin falling away...",
		"The mound is about to fall to pieces! Keep going!",
		"The mound falls apart, but nothing is inside...",
		"The mound fell apart, revealing a Rockmite hidden inside!"
	);

	private ChatMessageFilter() { }

	/** Called only after normal automation/sync filters have accepted the message. */
	public static boolean shouldHide(Component message, boolean overlay) {
		if (overlay || message == null) return false;
		int selected = ConfigManager.get().display.hiddenChatMessages;
		if (selected == 0) return false;
		for (String part : message.getString().split("\\r?\\n|\\\\n")) {
			String line = ChatParser.clean(part);
			if (line.isEmpty() || ChatParser.playerSaid(line)) continue;
			if ((selected & CRITTER_CATCHES) != 0 && line.startsWith("CAPTURE!")) return true;
			if ((selected & LOOT_SHARED_CRITTERS) != 0 && line.startsWith("LOOT SHARE!")) return true;
			if ((selected & FLOOR_DROPS) != 0 && line.startsWith("FLOOR DROP!")) return true;
			if ((selected & MANAGER) != 0 && line.startsWith("[NPC] Safari Manager:")) return true;
			if ((selected & MOUNDS) != 0 && MOUND_MESSAGES.contains(line)) return true;
			if ((selected & GEMZIE) != 0 && gemzieMessage(line)) return true;
			if ((selected & COLD) != 0 && coldMessage(line)) return true;
			if ((selected & WUMPA) != 0 && wumpaMessage(line)) return true;
			if ((selected & DOOMSPIRAL) != 0 && doomspiralMessage(line)) return true;
			if ((selected & HIDEYHO_DIALOGUE) != 0 && line.startsWith("[MOB] Hideyho:")) return true;
			if ((selected & EMPTY_NESTS) != 0 && line.equals("Looks like the hive is empty now...")) return true;
			if ((selected & BIRD_SPAWNS) != 0 && BirdfeederWatch.isBirdSpawnMessage(line)) return true;
		}
		return false;
	}

	private static boolean gemzieMessage(String line) {
		return line.startsWith("You placed the ") && line.endsWith(" Gem on its podium!")
			|| line.startsWith("A rumbling sound can be heard");
	}

	private static boolean coldMessage(String line) {
		String lower = line.toLowerCase(Locale.ROOT);
		return line.startsWith("BRRR!")
			|| lower.contains("getting really cold");
	}

	private static boolean wumpaMessage(String line) {
		return line.startsWith("You hear the sound of massive footsteps")
			|| line.startsWith("The Wumpa has awoken")
			|| line.startsWith("The cave opens up again")
			|| line.contains("fainted by a Wumpa") && line.endsWith("lost some of your items!");
	}

	private static boolean doomspiralMessage(String line) {
		return line.startsWith("You used the Soothing Incense to light the candle")
			|| line.startsWith("Your ritual summoned a Doomspiral")
			|| line.startsWith("Something stirs in the Haunted Biome")
			|| line.startsWith("The darkness in the Haunted Biome fades away")
			|| line.startsWith("The Doomspiral retreats back underground");
	}
}
