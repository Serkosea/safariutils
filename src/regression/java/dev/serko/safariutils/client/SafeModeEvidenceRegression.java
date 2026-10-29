package dev.serko.safariutils.client;

import dev.serko.safariutils.state.EvidenceLevel;
import dev.serko.safariutils.state.EvidencePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

public final class SafeModeEvidenceRegression {
    public static int run() throws Exception {
        int checks = 0;
        for (var evidence : EvidenceLevel.values()) {
            boolean expected = switch (evidence) {
                case VISIBLE, INTERACTION, INVENTORY, SERVER, PARTY_SYNC -> true;
                default -> false;
            };
            require(EvidencePolicy.canRevealLiveWorld(evidence, false) == expected, "wrong evidence policy: " + evidence); checks++;
        }
        var target = new BlockPos(5, 0, 0);
        require(VisibilityCheck.reachesBlockSurface(new BlockHitResult(new Vec3(5, .5, .5), Direction.WEST, target, false), target), "target self-occlusion rejected"); checks++;
        require(!VisibilityCheck.reachesBlockSurface(new BlockHitResult(new Vec3(4, .5, .5), Direction.WEST, new BlockPos(4, 0, 0), false), target), "nearby intervening wall accepted"); checks++;
        require(VisibilityCheck.reachesBlockSurface(BlockHitResult.miss(Vec3.atCenterOf(target), Direction.WEST, target), target), "visible empty location rejected"); checks++;
        var config = ConfigManager.class.getDeclaredField("config"); config.setAccessible(true);
        Object previous = config.get(null); config.set(null, new SafariConfig());
        try {
            var settings = ConfigManager.get();
            settings.advanced.safePredictedInformation = false;
            settings.advanced.safePredictedInformation = false;
            boolean standard = !dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.SAFE_MODE_CONTROLS);
            require(SafeMode.floorDropContents() == standard && SafeMode.scrappyFood() == standard,
                "standard build leaked entity inference despite safe-mode enforcement"); checks++;
            settings.advanced.safePredictedInformation = true;
            settings.advanced.safePredictedInformation = true;
            require(SafeMode.floorDropContents() && SafeMode.scrappyFood(), "safe restriction ignored"); checks++;
            NestTracker.reset();
            BlockPos nest = new BlockPos(1, 2, 3);
            // A previous non-safe observation must not prevent upgrading remote evidence.
            set(NestTracker.class, "punched", null).add(nest);
            NestTracker.onPartyConfirmed(nest);
            require(set(NestTracker.class, "safePunched", null).contains(nest), "party nest not accepted as known"); checks++;
            WallTracker.TROODON.reset();
            require(WallTracker.TROODON.mergePartyCompletionMask(1), "valid wall mask rejected"); checks++;
            require(set(WallTracker.class, "completed", WallTracker.TROODON).isEmpty(), "remote wall echoed as local"); checks++;
            require(!WallTracker.TROODON.mergePartyCompletionMask(8), "invalid wall mask accepted"); checks++;
            MoundSpotter.reset();
            require(MoundSpotter.mergePartyCompletionWords(1, 0), "valid mound mask rejected"); checks++;
            require(java.util.Arrays.equals(MoundSpotter.localCompletionWords(), new int[] {0, 0}), "remote mound echoed as local"); checks++;
        } finally {
            NestTracker.reset(); WallTracker.TROODON.reset(); MoundSpotter.reset(); config.set(null, previous);
        }
        return checks;
    }
    @SuppressWarnings("unchecked")
    private static Set<BlockPos> set(Class<?> type, String name, Object target) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return (Set<BlockPos>) field.get(target);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private SafeModeEvidenceRegression() { }
}
