package dev.serko.safariutils.mixin;
import dev.serko.safariutils.client.ClearWater;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(FluidRenderer.class)
public abstract class WaterSurfaceMixin {
    @ModifyExpressionValue(method="tesselate",at=@At(value="INVOKE",target="Lnet/minecraft/client/color/block/BlockTintSource;colorInWorld(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"),require=0)
    private int safariutils$water(int colour,@Local(argsOnly=true) FluidState fluid) {return ClearWater.surface(colour,fluid!=null&&fluid.is(FluidTags.WATER));}
}
