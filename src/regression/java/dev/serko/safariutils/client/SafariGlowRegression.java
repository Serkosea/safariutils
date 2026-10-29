package dev.serko.safariutils.client;
import java.util.List;
import net.minecraft.world.phys.Vec3;
public final class SafariGlowRegression {
    public static int run() {
        Vec3 origin = Vec3.ZERO;
        var floor = new SafariGlows.ModelAnchor("Hideonfloor", origin);
        var wall = new SafariGlows.ModelAnchor("Hideonwall", origin);
        var duplico = new SafariGlows.ModelAnchor("Duplico", origin);
        require(SafariGlows.uniqueModelOwner("green_shulker_box", origin, List.of(floor, wall, duplico)) == 0, "floor shell cannot become Duplico");
        require(SafariGlows.uniqueModelOwner("purple_shulker_box", origin, List.of(floor, wall, duplico)) == 1, "wall shell cannot become Duplico");
        require(SafariGlows.uniqueModelOwner("stone", origin, List.of(floor, wall, duplico)) == 2, "Duplico model");
        require(SafariGlows.uniqueModelOwner("player_head", origin, List.of(duplico)) == -1, "capsule/head excluded");
        require(SafariGlows.uniqueModelOwner("stone", origin, List.of(duplico, duplico)) == -1, "ambiguous visual excluded");
        require(SafariGlows.uniqueModelOwner("stone", new Vec3(2,0,0), List.of(duplico)) == -1, "nearby unrelated visual excluded");
        require(SafariGlows.uniqueModelOwner("green_shulker_box", new Vec3(0,2,0), List.of(floor)) == -1, "different height excluded");
        require(!SafariGlows.species("Gazer") && SafariGlows.species("Bloodbat"), "requested scope only");
        require(SafariGlows.uniqueModelOwner("red_shulker_box", origin, List.of(duplico)) == 0, "Duplico shulker model retained");
        SafariGlows.clear();
        return 9;
    }
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
