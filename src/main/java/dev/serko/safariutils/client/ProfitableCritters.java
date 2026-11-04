package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.CritterCatchEstimates;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Derived market view; never owns prices, network requests, or run progress. */
final class ProfitableCritters {
    static final double ESTIMATED_SHARDS = 1.5025;
    static final long MAX_AGE = 30 * 60_000L;
    private static final double[] ESSENCE_MULTIPLIERS = {1, 1.1, 1.25, 1.5};
    private static final String[] TICKETS = {"Basic", "Economy", "Premium", "First Class"};
    record Entry(Critter critter, double spawns, double perCatch, double perRun, double shardValue, double essenceValue) { }
    private static long revision = Long.MIN_VALUE;
    private static int ticket = -1;
    private static List<Entry> entries = List.of();
    private static Set<Critter> selected = Set.of();
    private ProfitableCritters() { }

    static int ticketIndex() { return Math.clamp(ConfigManager.get().profit.estimateTicket, 0, 3); }

    static List<Entry> ranking() {
        long age = BazaarPrices.ageMillis();
        if (!BazaarPrices.known() || age < 0 || age > MAX_AGE) return List.of();
        long next = BazaarPrices.rankingRevision();
        int nextTicket = ticketIndex();
        if (revision != next || ticket != nextTicket) {
            entries = rank(BazaarPrices::rankingPrice, BazaarPrices.essencePrice(), ESSENCE_MULTIPLIERS[nextTicket]);
            Set<Critter> species = new HashSet<>();
            for (Entry entry : entries) if (!entry.critter().name().equals("Rockmite")) species.add(entry.critter());
            selected = Set.copyOf(species);
            revision = next;
            ticket = nextTicket;
            DebugLog.line("PROFIT/RANKING", "pricedTop=" + entries.size() + " markerSpecies=" + selected.size()
                + " ticket=" + TICKETS[ticket] + " basis=expected-catches shards=" + ESTIMATED_SHARDS);
        }
        return entries;
    }

    static boolean selected(Critter critter) {
        return !ranking().isEmpty() && selected.contains(critter);
    }

    static List<Entry> rank(ToDoubleFunction<Critter> prices, double essence, double multiplier) {
        List<Entry> result = new ArrayList<>();
        for (Critter critter : Critters.all()) {
            double price = prices.applyAsDouble(critter);
            // Missing price is not a zero-value quote. Do not invent a complete rank for it.
            if (!Double.isFinite(price) || price <= 0) continue;
            double spawns = CritterCatchEstimates.perRun(critter);
            double essencePerCatch = 2 << critter.rarity().ordinal();
            double shardValue = price * ESTIMATED_SHARDS;
            double essenceValue = Double.isFinite(essence) && essence > 0 ? essence * essencePerCatch * multiplier : 0;
            double perCatch = shardValue + essenceValue;
            if (Double.isFinite(perCatch) && perCatch > 0 && spawns > 0)
                result.add(new Entry(critter, spawns, perCatch, spawns * perCatch, shardValue * spawns, essenceValue * spawns));
        }
        result.sort(Comparator.comparingDouble(Entry::perRun).reversed().thenComparing(e -> e.critter().name()));
        return List.copyOf(result.subList(0, Math.min(10, result.size())));
    }

    static int show() {
        BazaarPrices.request(true);
        List<Entry> current = ranking();
        if (current.isEmpty()) {
            ClientMessages.send("Profit prices are unavailable or expired. Refresh requested; try /safari profit again shortly", ClientMessages.Tone.INFO);
            return 1;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Top Estimated Critter Values").withColor(StatColours.COINS));
        lines.add(Component.literal(TICKETS[ticketIndex()] + " Ticket • "
            + (ConfigManager.get().profit.priceSource() == SafariConfig.PriceSource.INSTANT_SELL ? "Instant Sell" : "Sell Offer"))
            .withColor(0x8DE8FF));
        int index = 0;
        for (Entry entry : current) {
            String detail = String.format(Locale.ROOT,
                "Approx. %.2f Catches per Run\nShard Profit: %,.0f coins\nEssence Profit: %,.0f coins\nTotal: %,.0f coins",
                entry.spawns(), entry.shardValue(), entry.essenceValue(), entry.perRun());
            lines.add(Component.literal(++index + ". ").withColor(0xFFFFFF)
                .append(Component.literal(entry.critter().name()).withColor(entry.critter().rarity().colour()))
                .append(Component.literal(String.format(Locale.ROOT, " — %,.0f/run (%,.0f/catch)", entry.perRun(), entry.perCatch())).withColor(StatColours.COINS))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(detail)))));
        }
        lines.add(Component.literal("Priced " + BazaarPrices.ageMillis() / 60_000 + "m Ago • Hover for Profit Breakdown")
            .withColor(0x8DE8FF));
        ClientMessages.sendReport(lines);
        return 1;
    }
}
