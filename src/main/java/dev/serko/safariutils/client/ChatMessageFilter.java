package dev.serko.safariutils.client;

import dev.serko.safariutils.parse.ChatParser;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Display-only filtering for recognized Safari server messages. */
public final class ChatMessageFilter {
	private static final int CRITTER_CATCHES = 1;
	private static final int LOOT_SHARED_CRITTERS = 1 << 1;
	private static final int FLOOR_DROPS = 1 << 2;
	private static final int OBJECTIVES = 1 << 3;
	private static final int MANAGER = 1 << 4;

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
			if ((selected & OBJECTIVES) != 0 && objectiveMessage(line)) return true;
		}
		return false;
	}

	private static boolean objectiveMessage(String line) {
		String lower = line.toLowerCase(Locale.ROOT);
		return line.startsWith("BRRR!")
			|| lower.contains("getting really cold")
			|| line.startsWith("You placed the ") && line.endsWith(" Gem on its podium!")
			|| line.startsWith("A rumbling sound can be heard")
			|| line.startsWith("You hear the sound of massive footsteps")
			|| line.startsWith("The Wumpa has awoken")
			|| line.startsWith("The cave opens up again")
			|| lower.contains("fainted by a wumpa")
			|| line.startsWith("You used the Soothing Incense to light the candle")
			|| line.startsWith("Your ritual summoned a Doomspiral")
			|| line.startsWith("Something stirs in the Haunted Biome")
			|| line.startsWith("The darkness in the Haunted Biome fades away")
			|| line.startsWith("The Doomspiral retreats back underground")
			|| line.contains("attracted to the Birdfeeder!")
			|| lower.contains("mound")
			|| lower.contains("rockmite");
	}
}
