package dev.serko.safariutils.mixin;
import dev.serko.safariutils.client.ClearWater;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogMixin {
    @Inject(method="setupFog",at=@At("RETURN"))
    private void safariutils$water(FogData fog,Camera camera,ClientLevel level,float distance,DeltaTracker delta,CallbackInfo ci) {
        if(!ClearWater.active()||fog==null)return;
        float far=Math.max(4096,distance*2);fog.environmentalStart=far;fog.environmentalEnd=far+1024;fog.skyEnd=far+1024;fog.cloudEnd=far+1024;
    }
}
