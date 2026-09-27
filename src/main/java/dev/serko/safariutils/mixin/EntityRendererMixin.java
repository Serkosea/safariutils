package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.WaypointRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides only vanilla labels that Safari Utils replaced during its last render pass. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private void safariutils$hideReplacedCritterName(T entity, double distance,
			CallbackInfoReturnable<Boolean> callback) {
		if (WaypointRenderer.replacesVanillaName(entity)) callback.setReturnValue(false);
	}

	/**
	 * Specialized renderers such as armor stands override {@code shouldShowName}.
	 * Clearing the completed state also covers those overrides while leaving the
	 * entity's actual custom name intact for detection and pairing.
	 */
	@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)"
		+ "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
	private void safariutils$hideOverriddenCritterName(T entity, float partialTick,
			CallbackInfoReturnable<S> callback) {
		if (!WaypointRenderer.replacesVanillaName(entity)) return;
		S completed = callback.getReturnValue();
		completed.nameTag = null;
		completed.scoreText = null;
	}
}
