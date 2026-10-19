package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

/** Suppresses only Haunted fog rendering while preserving the real status effect. */
public final class DarknessFilter {
	private static boolean loggedDarkness;
	private static boolean loggedBlindness;

	private DarknessFilter() {
	}

	public static void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!enabled() || player == null) {
			loggedDarkness = false;
			loggedBlindness = false;
			return;
		}
		// Log once per enabled Safari context, not once per rendered frame when
		// another mod suppresses effect queries or vanilla probes an inactive layer.
	}

	/** Leaves effect state untouched and hides only its local fog environment. */
	public static boolean suppressesFog(Entity entity, Holder<MobEffect> effect) {
		// Other fog-removal mods can return null from getMobEffect to disable their
		// environment. Respect that sentinel before consulting client/settings state.
		if (effect == null) return true;
		LocalPlayer player = Minecraft.getInstance().player;
		boolean suppress = enabled() && player != null && entity == player
			&& (effect.is(MobEffects.DARKNESS) || effect.is(MobEffects.BLINDNESS));
		if (!suppress) return false;
		if (effect.is(MobEffects.DARKNESS) && !loggedDarkness) {
			loggedDarkness = true;
			DebugLog.line("DARKNESS", "suppressed darkness fog; effect preserved");
		} else if (effect.is(MobEffects.BLINDNESS) && !loggedBlindness) {
			loggedBlindness = true;
			DebugLog.line("DARKNESS", "suppressed blindness fog; effect preserved");
		}
		return true;
	}

	private static boolean enabled() {
		// Scope the setting to Critter Safari so ordinary blindness remains meaningful
		// everywhere else in Minecraft.
		return ConfigManager.get().display.removeDarkness && SafariLocation.inSafari();
	}
}
