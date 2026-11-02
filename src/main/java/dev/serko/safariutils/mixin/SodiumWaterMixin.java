package dev.serko.safariutils.mixin;
import dev.serko.safariutils.client.ClearWater;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer",remap=false)
public abstract class SodiumWaterMixin {
    @ModifyExpressionValue(method="updateQuad",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;toABGR(I)I"),require=0)
    private int safariutils$water(int colour,@Local(argsOnly=true) FluidState fluid) {return ClearWater.surface(colour,fluid!=null&&fluid.is(FluidTags.WATER));}
}
