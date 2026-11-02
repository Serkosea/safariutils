package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.HypixelConnection;
import dev.serko.safariutils.client.SafariLocation;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Suppresses only the vanilla freezing overlay while the player is inside Safari. */
@Mixin(Hud.class)
public abstract class GuiMixin {
	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"), require = 0)
	private int safariutils$removeSafariColdOverlay(LocalPlayer player, Operation<Integer> original) {
		return HypixelConnection.active() && ConfigManager.get().display.removeColdOverlay
			&& SafariLocation.inSafari()
			? 0 : player == null ? 0 : original.call(player);
	}
}
