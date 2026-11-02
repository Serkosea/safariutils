package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import dev.serko.safariutils.BuildVersion;
import net.minecraft.world.level.BlockGetter;
import dev.serko.safariutils.data.SafariBiome;

/** Changes selection only; normal Minecraft use packets, reach and collision stay intact. */
public final class CandleTarget {
    private CandleTarget() { }
    public static boolean enabled(BlockGetter world) {
        var client=Minecraft.getInstance();
        if(!BuildVersion.has(BuildVersion.Capability.SAFE_MODE_CONTROLS) || client==null || client.player==null || client.level==null || world!=client.level
            || !HypixelConnection.active() || !ConfigManager.get().gameplay.fullCandleTarget
            || !SafariLocation.inside() || SafariLocation.biome()!=SafariBiome.HAUNTED) return false;
        return incense(client.player.getMainHandItem()) || incense(client.player.getOffhandItem());
    }
    public static boolean candle(net.minecraft.world.level.block.Block block) {
        return block instanceof net.minecraft.world.level.block.CandleBlock
            && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().equals("red_candle");
    }
    public static boolean unlit(net.minecraft.world.level.block.state.BlockState state) {
        return candle(state.getBlock()) && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
            && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
    }
    /** Reuse vanilla's resolved pick: no second raycast and no through-wall targeting. */
    public static net.minecraft.core.BlockPos aimedBlock() {
        var client=Minecraft.getInstance();
        if(!enabled(client.level)||ClientCompat.screen()!=null||client.player.isSpectator()
            || !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
            || hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        double reach=client.player.blockInteractionRange();
        if(client.player.getEyePosition().distanceToSqr(hit.getLocation())>reach*reach
            ||!client.level.getWorldBorder().isWithinBounds(hit.getBlockPos())
            ||!unlit(client.level.getBlockState(hit.getBlockPos()))) return null;
        return hit.getBlockPos();
    }
    private static boolean incense(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && SafariLocation.strip(stack.getHoverName().getString()).trim().equalsIgnoreCase("Soothing Incense");
    }
}
