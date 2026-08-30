package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;

/** Removes the client-side Warden darkness effect and fog while inside the Safari. */
public final class DarknessFilter {

	private DarknessFilter() {
	}

	public static void tick() {
		if (!ConfigManager.get().display.removeDarkness) return;
		// Kept to the Safari like everything else here: this is a Safari tracker, not a
		// general-purpose visual mod, and elsewhere the effect may be worth seeing.
		if (!SafariLocation.inSafari()) return;

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.hasEffect(MobEffects.DARKNESS)) return;
		player.removeEffect(MobEffects.DARKNESS);
	}
}
