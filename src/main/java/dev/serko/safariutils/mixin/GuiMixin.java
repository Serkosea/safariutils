package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.SafariLocation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Suppresses only the vanilla freezing overlay while the player is inside Safari. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Redirect(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"))
	private int safariutils$removeSafariColdOverlay(LocalPlayer player) {
		return ConfigManager.get().display.removeColdOverlay && SafariLocation.inSafari()
			? 0 : player.getTicksFrozen();
	}
}
