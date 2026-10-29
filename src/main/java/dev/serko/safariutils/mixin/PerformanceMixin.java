package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.PerformanceDiagnostics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class PerformanceMixin {
    @Unique private long safari$frameStart;
    @Unique private long safari$tickStart;
    @Inject(method = "runTick", at = @At("HEAD"))
    private void safari$frameBegin(CallbackInfo ci) {
        PerformanceDiagnostics.frame();
        safari$frameStart = PerformanceDiagnostics.begin();
    }
    @Inject(method = "runTick", at = @At("RETURN"))
    private void safari$frameEnd(CallbackInfo ci) { PerformanceDiagnostics.end("minecraft/frame-loop", safari$frameStart); }
    @Inject(method = "tick", at = @At("HEAD"))
    private void safari$tickBegin(CallbackInfo ci) { safari$tickStart = PerformanceDiagnostics.begin(); }
    @Inject(method = "tick", at = @At("RETURN"))
    private void safari$tickEnd(CallbackInfo ci) { PerformanceDiagnostics.end("minecraft/tick", safari$tickStart); }
}
