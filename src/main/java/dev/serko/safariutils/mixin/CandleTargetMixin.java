package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.CandleTarget;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CandleTargetMixin {
    @Shadow public abstract Block getBlock();
    @Inject(method="getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at=@At("RETURN"), cancellable=true)
    private void safariutils$candle(BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> result) {
        if(CandleTarget.candle(getBlock()) && CandleTarget.enabled(world) && CandleTarget.unlit(world.getBlockState(pos))) result.setReturnValue(Shapes.block());
    }
}
