package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;

/** Allocation-free decisions over existing run evidence. Never changes catch or PB state. */
final class RunRelevance {
    static final int UNIQUE = 1, SEARCH = 2, PROFIT = 4, ALWAYS = 8, PREREQUISITE = 16, FULL_CLEAR = 32;
    private RunRelevance() { }

    static boolean indicateProfit(Critter critter) {
        return ConfigManager.get().display.indicateProfitableCritters && ProfitableCritters.selected(critter);
    }

    static boolean profitable(Critter critter) {
        return SparklingMode.enabled() && ConfigManager.get().sparkling.alwaysShowProfitableCritters
            && ProfitableCritters.selected(critter);
    }

    static boolean prerequisite(Critter critter, SafariSession session) {
        if (!SparklingMode.enabled() || critter.biome() != SafariBiome.ICY || critter.name().equals("Wumpa")) return false;
        if (session != null && session.caughtByParty(critter) || SafariObjectives.wumpaComplete()) return false;
        Critter wumpa = Critters.byName("Wumpa");
        return !SparklingMode.isShared(wumpa) || profitable(wumpa) || SparklingMode.alwaysActive(wumpa);
    }

    static int reasons(Critter critter, SafariSession session) {
        boolean caught = session != null && session.caughtByParty(critter);
        boolean unavailable = session != null && session.isUnavailable(critter) && session.nearby(critter) == 0;
        int reasons = 0;
        RunMode mode = RunMode.current();
        if (!mode.focused()) return FULL_CLEAR;
        if (!unavailable) {
            if (mode.seeksRunUniques() && !caught) reasons |= UNIQUE;
            // A single ordinary sighting/catch does not prove every spawn was checked.
            if (!SparklingMode.isShared(critter) && remainingOpportunity(critter, session)) reasons |= SEARCH;
            if (profitable(critter) && remainingOpportunity(critter, session)) reasons |= PROFIT;
            if (prerequisite(critter, session)) reasons |= PREREQUISITE;
        }
        if (SparklingMode.alwaysActive(critter)) reasons |= ALWAYS;
        return reasons;
    }

    static boolean retainOverride(Critter critter, SafariSession session) {
        return SparklingMode.alwaysActive(critter) || profitable(critter) || prerequisite(critter, session)
            || SparklingWatch.outstandingCount(critter) > 0;
    }

    private static int hudReasons(Critter critter, SafariSession session) {
        int reasons = reasons(critter, session);
        if (ConfigManager.get().display.missingUniqueOnly && session != null && session.caughtByParty(critter))
            reasons &= ~(UNIQUE | SEARCH);
        return reasons;
    }

    static boolean showInHud(Critter critter, SafariSession session) {
        int reasons = hudReasons(critter, session);
        if ((reasons & (UNIQUE | SEARCH | PREREQUISITE)) != 0) return true;
        if ((reasons & (PROFIT | ALWAYS)) == 0) return false;
        // Do not retain a dead profit row once an exact quota is caught; dynamic
        // species retain uncertainty until the existing tracker proves exhaustion.
        return remainingOpportunity(critter, session);
    }

    private static boolean remainingOpportunity(Critter critter, SafariSession session) {
        return session == null || session.nearby(critter) > 0
            || !critter.hasQuota() || session.partyCatches(critter) < critter.spawnQuota();
    }

    static boolean fullClearHud(Critter critter, SafariSession session) {
        if (session == null) return true;
        if (ConfigManager.get().display.missingUniqueOnly && session.caughtByParty(critter)) return false;
        // A remaining live critter is stronger evidence than a nominal quota.
        return !critter.hasQuota() || !session.isComplete(critter) || session.nearby(critter) > 0;
    }
}
