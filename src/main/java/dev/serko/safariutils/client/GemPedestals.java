package dev.serko.safariutils.client;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Fixed Gemzie objective geometry, shared by observation and presentation. */
final class GemPedestals {
    record Pedestal(int bit, String label, BlockPos base, int colour) {
        Block support() {
            return switch (bit) {
                case 1 -> Blocks.VERDANT_FROGLIGHT;
                case 2 -> Blocks.OCHRE_FROGLIGHT;
                case 4 -> Blocks.PEARLESCENT_FROGLIGHT;
                default -> throw new IllegalStateException("Unknown pedestal");
            };
        }
        BlockPos gem() { return base.above(); }
        boolean matches(Block baseBlock, Block gemBlock) {
            return baseBlock == support() && gemBlock == Blocks.PLAYER_HEAD;
        }
    }
    static final List<Pedestal> ALL = List.of(
        new Pedestal(4, "Purple", new BlockPos(-140, 61, 56), 0xFF9D39DB),
        new Pedestal(1, "Lime", new BlockPos(-137, 61, 51), 0xFF9DFF00),
        new Pedestal(2, "Orange", new BlockPos(-140, 61, 46), 0xFFFF5B24));
    private GemPedestals() { }
}
