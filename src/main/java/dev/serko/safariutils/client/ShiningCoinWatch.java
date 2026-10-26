package dev.serko.safariutils.client;

/** Inventory-backed Gimmiegold quota. Acquisitions remain separate historical counters. */
public final class ShiningCoinWatch {
    private static final String SPAWN = "A Gimmiegold appeared out of nowhere and gobbled up your Shining Coin!";
    private static final long SETTLE_MILLIS = 750;
    private static int found, acquired, floorFound, spawned;
    private static int candidateHeld = -1;
    private static long settleAt;
    private ShiningCoinWatch() { }

    public static void onChatMessage(String line) {
        onChatMessage(line, dev.serko.safariutils.state.MonotonicTime.nowMillis());
    }
    static void onChatMessage(String line, long now) {
        if (line.equals(SPAWN)) {
            spawned++;
            // Wait for the inventory packet before combining held and consumed coins.
            settleAt = now + SETTLE_MILLIS;
            if (DebugLog.isEnabled()) DebugLog.line("COINS", "spawn confirmed total="+spawned);
        } else if (line.startsWith("FLOOR DROP!") && line.contains("Shining Coin")) {
            acquired++;
            floorFound++;
        }
    }
    /** Starting inventory evidence; later authoritative inventory replaces this quota. */
    public static void creditFound(int amount) {
        found += Math.max(0, amount);
        acquired += Math.max(0, amount);
    }
    public static int acquired() { return acquired; }
    public static int floorFound() { return floorFound; }
    /** Available coins plus coins confirmed to have spawned a Gimmiegold. */
    public static int found() { return found; }

    /** Uses the shared inventory scan, including its carried cursor stack. */
    public static void observeInventory(int held, long now) {
        held = Math.max(0, held);
        if (candidateHeld != held) {
            candidateHeld = held;
            settleAt = now + SETTLE_MILLIS;
            return;
        }
        if (now < settleAt) return;
        int next = held + spawned;
        if (next != found) {
            if (DebugLog.isEnabled()) DebugLog.line("COINS", "quota="+found+"->"+next+" held="+held+" spawned="+spawned+" acquired="+acquired);
            found = next;
        }
    }
    public static void reset() {
        found = acquired = floorFound = spawned = 0;
        candidateHeld = -1;
        settleAt = 0;
    }
}
