package dev.serko.safariutils.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Tracks the session-only Advanced unlock. It is never saved to the config. */
public final class AdvancedUnlock {

	private static boolean unlocked;
	private AdvancedUnlock() {
	}

	public static boolean isUnlocked() {
		return unlocked;
	}

	public static void unlock() {
		unlocked = true;
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			ClientCompat.addSystemMessage(
				Component.literal("[SafariUtils] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal("Advanced Mode Enabled").withStyle(ChatFormatting.GREEN)));
		}
		if (client.player != null) {
			// Layering is needed because one Minecraft sound barely changes above volume 1.
			for (int layer = 0; layer < 25; layer++) {
				client.player.playSound(SoundEvents.BEACON_ACTIVATE, 1f, 2f);
			}
		}
	}
}
