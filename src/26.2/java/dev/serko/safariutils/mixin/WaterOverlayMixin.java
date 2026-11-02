package dev.serko.safariutils.mixin;
import dev.serko.safariutils.client.ClearWater;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ScreenEffectRenderer.class)
public abstract class WaterOverlayMixin {
    @Inject(method="submitWater",at=@At("HEAD"),cancellable=true,require=0)
    private static void safariutils$water(CallbackInfo ci) {if(ClearWater.active())ci.cancel();}
}
