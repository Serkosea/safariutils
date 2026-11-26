package dev.serko.safariutils.mixin;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.SafariAreaMap;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class HauntedPaintingMixin {
    private static final java.util.Map<Painting,net.minecraft.core.BlockPos> safariutils$haunted =
        new java.util.WeakHashMap<>();
    private static final java.util.Map<Painting,net.minecraft.core.BlockPos> safariutils$ordinary =
        new java.util.WeakHashMap<>();
    private static boolean safariutils$isHaunted(Painting painting) {
        var position=painting.blockPosition();
        if(position.equals(safariutils$haunted.get(painting)))return true;
        if(position.equals(safariutils$ordinary.get(painting)))return false;
        boolean haunted=SafariAreaMap.biomeAt(painting.getX(),painting.getY(),painting.getZ())==SafariBiome.HAUNTED;
        (haunted?safariutils$ordinary:safariutils$haunted).remove(painting);
        (haunted?safariutils$haunted:safariutils$ordinary).put(painting,position);
        return haunted;
    }
    @Inject(method="shouldRender(DDD)Z",at=@At("HEAD"),cancellable=true)
    private void safariutils$hideHauntedPainting(double x,double y,double z,CallbackInfoReturnable<Boolean> result){
        if ((Object)this instanceof Painting painting && BuildVersion.has(BuildVersion.Capability.SAFE_MODE_CONTROLS)
                && ConfigManager.get().display.hideHauntedPaintings && SafariLocation.inside()
                && safariutils$isHaunted(painting))
            result.setReturnValue(false);
    }
}
