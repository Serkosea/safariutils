package dev.serko.safariutils.client;

import dev.serko.safariutils.data.*;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.parse.CritterEvent;
import java.lang.reflect.Field;
import java.util.*;

/** Exercises public-edition policy without a renderer, network, or live account. */
public final class ProfitRelevanceRegression {
    public static int run() throws Exception {
        var saved = new LinkedHashMap<Field, Object>();
        for (String name : List.of("config")) save(saved, ConfigManager.class, name);
        for (String name : List.of("prices", "freshProducts", "fetchedAt", "priceRevision")) save(saved, BazaarPrices.class, name);
        for (String name : List.of("revision", "ticket", "entries", "selected")) save(saved, ProfitableCritters.class, name);
        Set<Critter> shared = SparklingMode.shared();
        boolean configured = SparklingMode.sharedConfigured();
        try {
            set(ConfigManager.class, "config", new SafariConfig());
            var config = ConfigManager.get();
            check(!config.sparkling.alwaysShowProfitableCritters, "profit override defaults off");
            var all = ProfitableCritters.rank(c -> 100, 1, 1);
            check(all.size() == 10, "bounded top ten");
            for (int i = 1; i < all.size(); i++) check(all.get(i-1).perRun() >= all.get(i).perRun(), "sorted by run revenue");
            check(ProfitableCritters.rank(c -> Double.NaN, 1, 1).isEmpty(), "invalid quotes excluded");
            check(ProfitableCritters.rank(c -> 0, 1, 1).isEmpty(), "missing quotes excluded");
            var troodon = Critters.byName("Troodon");
            var rockmite = Critters.byName("Rockmite");
            var basic = ProfitableCritters.rank(c -> c.equals(troodon) ? 100 : 0, 2, 1).getFirst();
            var premium = ProfitableCritters.rank(c -> c.equals(troodon) ? 100 : 0, 2, 1.25).getFirst();
            check(premium.perRun() > basic.perRun(), "ticket essence multiplier affects estimate");
            check(basic.spawns() == 3, "central population range used");
            String[] modeled = {"Rockmite", "Snoozle", "Bluebird", "Parakeet", "Macaw", "Gimmiegold"};
            double[] expected = {4.125, 2.5, 6.060355555555556, 3.083022222222222, 1.0459333333333334, 4.722222222222222};
            for (int i = 0; i < modeled.length; i++) {
                Critter species = Critters.byName(modeled[i]);
                check(Math.abs(CritterCatchEstimates.perRun(species) - expected[i]) < 1e-10, "expected catches " + modeled[i]);
                var quote = ProfitableCritters.rank(c -> c.equals(species) ? 100 : 0, 2, 1.25).getFirst();
                check(Math.abs(quote.shardValue() + quote.essenceValue() - quote.perRun()) < 1e-8, "profit breakdown sums " + modeled[i]);
            }
            for (Critter species : Critters.all()) {
                check(Double.isFinite(CritterCatchEstimates.perRun(species)) && CritterCatchEstimates.perRun(species) > 0,
                    "valid estimate " + species.name());
                if (!List.of(modeled).contains(species.name())) {
                    var range = CritterSpawnRanges.forCritter(species);
                    check(CritterCatchEstimates.perRun(species) == (range.minimum() + range.maximum()) / 2.0,
                        "ordinary midpoint preserved " + species.name());
                }
            }
            check(CritterSpawnRanges.forCritter(Critters.MACAW).maximum() == 16, "estimate must not change population bounds");
            Map<String, BazaarPrices.Price> prices = new HashMap<>();
            prices.put(troodon.bazaarId(), new BazaarPrices.Price(10_000, 20_000));
            prices.put(rockmite.bazaarId(), new BazaarPrices.Price(100_000, 200_000));
            set(BazaarPrices.class, "prices", Map.copyOf(prices));
            set(BazaarPrices.class, "freshProducts", Set.copyOf(prices.keySet()));
            set(BazaarPrices.class, "fetchedAt", System.currentTimeMillis());
            set(BazaarPrices.class, "priceRevision", 999L);
            set(ProfitableCritters.class, "revision", Long.MIN_VALUE);
            check(config.display.indicateProfitableCritters, "HUD profit indicator defaults on");
            config.sparkling.sparklingMode = false;
            check(RunRelevance.indicateProfit(troodon), "full clear indicates profit independently of retention");
            config.display.indicateProfitableCritters = false;
            check(!RunRelevance.indicateProfit(troodon), "indicator can be hidden without changing selection");
            config.display.indicateProfitableCritters = true;
            config.sparkling.sparklingMode = true;
            config.sparkling.sparklingIgnoreUniques = true;
            SparklingMode.replaceShared(new HashSet<>(Critters.all()));
            var session = new SafariSession("test", 0);
            check(RunRelevance.reasons(troodon, session) == 0, "shared sparkling skipped without override");
            config.sparkling.alwaysShowProfitableCritters = true;
            check((RunRelevance.reasons(troodon, session) == RunRelevance.PROFIT), "profit-only reason");
            check(!RunRelevance.profitable(rockmite), "Rockmite excluded even when first ranked");
            check(!SparklingMode.hideTroodonWalls(session), "profit retains walls");
            check(!SparklingMode.hideOrdinarySpecies(troodon, session), "profit retains recatch");
            check(!SparklingMode.hideOrdinaryWaypoint(troodon, session), "profit retains tracked marker");
            check(RunRelevance.showInHud(troodon, session), "profit retains HUD");
            var cached = ProfitableCritters.ranking();
            check(cached == ProfitableCritters.ranking(), "unchanged prices reuse immutable ranking");
            config.sparkling.sparklingAlwaysActiveCritters = 1L << Critters.selectionOrder().indexOf(troodon);
            check(!(RunRelevance.reasons(troodon, session) == RunRelevance.PROFIT), "explicit selection can share a profit reason");
            config.sparkling.sparklingAlwaysActiveCritters = 0;
            config.sparkling.sparklingIgnoreUniques = false;
            check(!(RunRelevance.reasons(troodon, session) == RunRelevance.PROFIT) && RunRelevance.profitable(troodon), "profitable indicator remains when unique is also needed");
            session.record(new CritterEvent(CritterEvent.Type.OWN_CATCH, troodon, null, 1, false), 1);
            check((RunRelevance.reasons(troodon, session) == RunRelevance.PROFIT), "dollar after unique catch");
            config.display.missingUniqueOnly = true;
            check(RunRelevance.showInHud(troodon, session), "profit override survives unique-only HUD setting");
            config.sparkling.alwaysShowProfitableCritters = false;
            check(!RunRelevance.showInHud(troodon, session), "unique-only HUD setting remains functional");
            config.sparkling.alwaysShowProfitableCritters = true;
            config.display.missingUniqueOnly = false;
            session.record(new CritterEvent(CritterEvent.Type.OWN_CATCH, troodon, null, 1, false), 2);
            session.record(new CritterEvent(CritterEvent.Type.OWN_CATCH, troodon, null, 1, false), 3);
            check(!RunRelevance.showInHud(troodon, session), "finished quota removes optional row");
            session.setNearby(Map.of(troodon, 1));
            check(RunRelevance.showInHud(troodon, session), "live evidence beats quota");
            check(session.partyCatches(troodon) == 3, "policy cannot mutate catches");
            var withoutWumpa = new HashSet<>(Critters.all()); withoutWumpa.remove(Critters.byName("Wumpa"));
            SparklingMode.replaceShared(withoutWumpa);
            config.sparkling.sparklingIgnoreUniques = true;
            check(RunRelevance.prerequisite(Critters.byName("Polaris"), new SafariSession("test", 0)), "Wumpa prerequisite retained");
            set(BazaarPrices.class, "fetchedAt", System.currentTimeMillis() - ProfitableCritters.MAX_AGE - 1_000);
            check(!RunRelevance.profitable(troodon), "expired price never retains profit marker");
            set(BazaarPrices.class, "fetchedAt", System.currentTimeMillis());
            set(BazaarPrices.class, "freshProducts", Set.of(rockmite.bazaarId()));
            set(BazaarPrices.class, "priceRevision", 1000L);
            check(!RunRelevance.profitable(troodon), "omitted quote not silently renewed");
            config.sparkling.sparklingMode = false;
            check(RunRelevance.reasons(troodon, session) == RunRelevance.FULL_CLEAR, "full clear independent of profit");
            check(!SparklingMode.hideOrdinaryWaypoint(troodon, session), "full clear preserves ordinary markers");
            return 121;
        } finally {
            for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
            if (configured) SparklingMode.replaceShared(shared); else SparklingMode.clearShared();
        }
    }
    private static void save(Map<Field,Object> saved, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); saved.put(field, field.get(null));
    }
    private static void set(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
