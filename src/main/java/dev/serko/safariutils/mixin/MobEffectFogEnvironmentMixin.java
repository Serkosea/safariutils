package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.DarknessFilter;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Haunted status effects intact while excluding only their visual fog layer. */
@Mixin(MobEffectFogEnvironment.class)
public abstract class MobEffectFogEnvironmentMixin {
	@Shadow
	public abstract Holder<MobEffect> getMobEffect();

	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
	private void safariutils$hauntedFog(FogType fogType, Entity entity,
			CallbackInfoReturnable<Boolean> result) {
		if (DarknessFilter.suppressesFog(entity, getMobEffect())) result.setReturnValue(false);
	}
}
