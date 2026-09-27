package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.WaypointRenderer;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers Hypixel's visible critter labels, whose renderer overrides the base name check. */
@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ArmorStand;D)Z",
		at = @At("HEAD"), cancellable = true)
	private void safariutils$hideReplacedCritterName(ArmorStand entity, double distance,
			CallbackInfoReturnable<Boolean> callback) {
		if (WaypointRenderer.replacesVanillaName(entity)) callback.setReturnValue(false);
	}
}
