package dev.serko.safariutils.client;

import dev.serko.safariutils.session.*;
import dev.serko.safariutils.data.Critters;
import java.util.Set;

public final class RunPersistenceRegression {
    public static int run() throws Exception {
        check(HypixelConnection.isAlphaAddress(" ALPHA.HYPIXEL.NET.:25565 "), "alpha address and port");
        check(!HypixelConnection.isAlphaAddress("mc.hypixel.net"), "production remains separate");
        check(!HypixelConnection.isAlphaAddress("alpha.hypixel.net.example.com"), "lookalike not alpha");
        check(!HypixelConnection.isAlphaAddress(null), "offline not alpha");
        var alpha = HypixelConnection.class.getDeclaredField("alpha"); alpha.setAccessible(true);
        var current = SessionManager.class.getDeclaredField("current"); current.setAccessible(true);
        var sticky = SessionManager.class.getDeclaredField("suppressCurrentRunPersistence"); sticky.setAccessible(true);
        Object oldRun = current.get(null); boolean oldAlpha = alpha.getBoolean(null), oldSticky = sticky.getBoolean(null);
        try {
            current.set(null, null); sticky.setBoolean(null, false); alpha.setBoolean(null, true);
            check(SessionManager.persistenceSuppressed(), "alpha blocks writes before ticket");
            int total = SparklingStats.total(), feathers = SparklingStats.rainbowFeathers();
            long revision = SparklingStats.revision();
            var critter = Critters.all().getFirst();
            SparklingStats.recordSparkling(critter);
            SparklingStats.recordRainbowFeather();
            check(!SparklingStats.set(critter, 10000), "manual collection edit blocked");
            check(!SparklingStats.setRainbowFeathers(10000), "manual feathers blocked");
            check(!SparklingStats.syncApiCollection(Set.of(), 10000, Long.MAX_VALUE), "automatic API sync blocked");
            SparklingStats.importApiCollection(Set.of(), 10000);
            check(total == SparklingStats.total() && feathers == SparklingStats.rainbowFeathers()
                && revision == SparklingStats.revision(), "no lifetime mutations under suppression");
            current.set(null, new SafariSession("Test", 100));
            check(SessionManager.persistenceSuppressed(), "run latches alpha suppression");
            alpha.setBoolean(null, false);
            check(SessionManager.persistenceSuppressed(), "disconnect cannot archive test run");
        } finally {
            alpha.setBoolean(null, oldAlpha); current.set(null, oldRun); sticky.setBoolean(null, oldSticky);
        }
        return 12;
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
