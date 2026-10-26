package dev.serko.safariutils.client;

/** Inventory and chat can arrive in either order; discarded coins are not spawned mobs. */
public final class ShiningCoinRegression {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Coin quota regression"); }
    public static int run() {
        ShiningCoinWatch.reset();
        ShiningCoinWatch.creditFound(1);
        ShiningCoinWatch.observeInventory(1,0);
        ShiningCoinWatch.observeInventory(1,750);
        check(ShiningCoinWatch.found()==1);
        ShiningCoinWatch.observeInventory(0,1000);
        ShiningCoinWatch.observeInventory(0,1750);
        check(ShiningCoinWatch.found()==0); // discarded, no spawn
        ShiningCoinWatch.observeInventory(1,2000);
        ShiningCoinWatch.observeInventory(1,2750);
        check(ShiningCoinWatch.found()==1); // picked back up
        ShiningCoinWatch.observeInventory(0,3000);
        ShiningCoinWatch.observeInventory(1,3250);
        ShiningCoinWatch.observeInventory(1,4000);
        check(ShiningCoinWatch.found()==1); // transient inventory resync
        String spawn="A Gimmiegold appeared out of nowhere and gobbled up your Shining Coin!";
        ShiningCoinWatch.onChatMessage(spawn,4100);
        ShiningCoinWatch.observeInventory(1,4250);
        check(ShiningCoinWatch.found()==1); // chat before inventory, no double count
        ShiningCoinWatch.observeInventory(0,4500);
        ShiningCoinWatch.observeInventory(0,5250);
        check(ShiningCoinWatch.found()==1);
        ShiningCoinWatch.onChatMessage("FLOOR DROP! You found Shining Coin on the ground!",5300);
        ShiningCoinWatch.observeInventory(1,5500);
        ShiningCoinWatch.observeInventory(1,6250);
        check(ShiningCoinWatch.found()==2 && ShiningCoinWatch.floorFound()==1);
        ShiningCoinWatch.observeInventory(0,6500);
        ShiningCoinWatch.observeInventory(0,7250);
        check(ShiningCoinWatch.found()==1);
        ShiningCoinWatch.onChatMessage(spawn,7500);
        ShiningCoinWatch.observeInventory(0,8250);
        check(ShiningCoinWatch.found()==2); // late spawn restores discarded candidate
        ShiningCoinWatch.reset();
        check(ShiningCoinWatch.found()==0 && ShiningCoinWatch.acquired()==0);
        return 10;
    }
}
