package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.session.RunHistory;
import dev.serko.safariutils.session.RunRecord;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.state.MonotonicCooldowns;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Fetches shard prices from Hypixel's public Bazaar API when profit is visible.
 * Instant sell uses the best buy order; sell offer uses the cheapest sell order.
 * A daemon worker applies retry and backoff without blocking the client thread.
 */
public final class BazaarPrices {

	private static final String SAFARI_ESSENCE = "ESSENCE_SAFARI";
	private static final String RAINBOW_FEATHER = "RAINBOW_FEATHER";

	/** Successful and failed automatic requests both wait five minutes before retrying. */
	private static final long REFRESH_MILLIS = 300_000;
	private static final int QUIET_FAILURES = 3;

	/** Bazaar tax on the sell side, which both price modes are. */
	private static final double TAX = 0.0125;

	/** The Safari products, so the parser can skip the rest without building them. */
	private static final Set<String> WANTED = wantedIds();

	private static boolean fetching;
	private static boolean stopped;
	private static final MonotonicCooldowns<Boolean> REFRESH = new MonotonicCooldowns<>(System::nanoTime);

	private static volatile Map<String, Price> prices = Map.of();
	private static volatile long fetchedAt;
	private static long priceRevision;
	private static volatile String lastError;
	/** Published on the client thread, together with the price snapshot. */
	private static volatile int failures;

	/** Saved runs are immutable, so each only needs pricing once per market snapshot/mode. */
	private static final Map<RunRecord, Long> RUN_VALUE_CACHE = new IdentityHashMap<>();
	private static long runValueHistoryRevision = Long.MIN_VALUE;
	private static long runValuePriceRevision = Long.MIN_VALUE;
	private static SafariConfig.PriceSource runValuePriceSource;
	private static long totalHistoryRevision = Long.MIN_VALUE;
	private static long totalHistoryPriceRevision = Long.MIN_VALUE;
	private static SafariConfig.PriceSource totalHistoryPriceSource;
	private static long totalHistoryValue;

	/** One shard's two prices, both top-of-book; {@code 0} where the book is empty. */
	public record Price(double instantSell, double sellOffer) {
	}

	private BazaarPrices() {
	}

	private static Set<String> wantedIds() {
		Set<String> ids = new HashSet<>();
		for (Critter critter : Critters.all()) ids.add(critter.bazaarId());
		ids.add(SAFARI_ESSENCE);
		ids.add(RAINBOW_FEATHER);
		return Set.copyOf(ids);
	}

	// --- fetching ------------------------------------------------------------

	/**
	 * Refreshes the prices when they are stale and there is a reason to have them.
	 *
	 * <p>Called from the tick pipeline. Everything after the gate happens off-thread; the
	 * client thread only ever hands the job over.
	 */
	public static void tick() {
		if (stopped || fetching || NetworkRuntime.stopping() || !ConfigManager.get().profit.enabled) return;

		if (REFRESH.remainingMillis(true, REFRESH_MILLIS) > 0) return;
		// Held off before the job starts, so a slow request cannot queue a second one.
		REFRESH.mark(true);
		fetching = true;
		BazaarMarket.request().thenApply(BazaarPrices::project).whenComplete((parsed, error) ->
			net.minecraft.client.Minecraft.getInstance().execute(() -> apply(parsed, error)));
	}

	private static Map<String, Price> project(BazaarMarket.Snapshot snapshot) {
		Map<String, Price> result = new HashMap<>();
		for (String id : WANTED) {
			BazaarMarket.Product product = snapshot.products().get(id);
			if (product != null) result.put(id, new Price(product.instantSell(), product.sellOffer()));
		}
		if (result.isEmpty()) throw new CompletionException(new IOException("No Safari products in Bazaar snapshot"));
		return Map.copyOf(result);
	}

	private static void apply(Map<String, Price> parsed, Throwable failed) {
		fetching = false;
		if (stopped || NetworkRuntime.stopping()) return;
		REFRESH.mark(true);
		if (failed == null) {
			// Keep the last known value for a product omitted from a partial response.
			Map<String, Price> merged = new HashMap<>(prices);
			merged.putAll(parsed);
			prices = Map.copyOf(merged);
			fetchedAt = System.currentTimeMillis();
			priceRevision++;
			lastError = null;
			failures = 0;
			DebugLog.line("BAZAAR", "prices loaded products=" + parsed.size());
		} else {
			while (failed instanceof CompletionException && failed.getCause() != null) failed = failed.getCause();
			failures++;
			lastError = "Price service unavailable; retrying automatically";
			String message = ClientMessages.apiFailure("load Bazaar price data", failed);
			ClientMessages.send(message, ClientMessages.Tone.ERROR);
			DebugLog.line("BAZAAR", "price refresh failed type=" + failed.getClass().getSimpleName()
				+ " cachedProducts=" + prices.size());
			// Quiet at first — a dropped request during a server hop is not news. Said once
			// when it stops looking like a blip, and not repeated every retry after that.
			if (failures == QUIET_FAILURES + 1) {
				OperationalLog.error("BAZAAR/FETCH", failed);
			}
		}
	}

	/** Rejects late publication; the shared network runtime owns worker/transport shutdown. */
	public static void shutdown() {
		stopped = true;
		fetching = false;
	}

	/** Shared bounded parser, with Safari-only valuation projected from its immutable snapshot. */
	static Map<String, Price> parse(InputStream body) throws IOException {
		return project(BazaarMarket.parse(body));
	}

	// --- pricing -------------------------------------------------------------

	/** True once a fetch has landed, so a view can say "not yet" instead of "nothing". */
	public static boolean known() {
		return fetchedAt != 0 && !prices.isEmpty();
	}

	/** Changes when a fresh price snapshot or the selected side of the market changes. */
	static long revision() {
		return priceRevision * 2L + ConfigManager.get().profit.priceSource().ordinal();
	}

	/** How long ago the prices were fetched, or {@code -1} if they never have been. */
	public static long ageMillis() {
		return fetchedAt == 0 ? -1 : Math.max(0, System.currentTimeMillis() - fetchedAt);
	}

	/** The last fetch failure, or {@code null} while things are working. */
	public static String lastError() {
		return lastError;
	}

	/** What one {@code critter} shard fetches on {@code source}'s side, tax taken off. */
	public static double price(Critter critter, SafariConfig.PriceSource source) {
		return price(critter.bazaarId(), source);
	}

	private static double price(String productId, SafariConfig.PriceSource source) {
		Price price = prices.get(productId);
		if (price == null) return 0;
		double raw = source == SafariConfig.PriceSource.SELL_OFFER
			? price.sellOffer() : price.instantSell();
		return raw * (1 - TAX);
	}

	/** The same, at whichever side the settings pick — the figure everything shows. */
	public static double price(Critter critter) {
		return price(critter, ConfigManager.get().profit.priceSource());
	}

	/** What a pile of shards is worth. Species with no price simply add nothing. */
	public static long valueOf(Map<Critter, Integer> shards, SafariConfig.PriceSource source) {
		double total = 0;
		for (Map.Entry<Critter, Integer> entry : shards.entrySet()) {
			total += price(entry.getKey(), source) * entry.getValue();
		}
		return Math.round(total);
	}

	/** What the shards of a live or just-finished run are worth. */
	public static long valueOf(SafariSession session, SafariConfig.PriceSource source) {
		if (session == null) return 0;
		double total = valueOf(session.shardCounts(), source);
		total += price(SAFARI_ESSENCE, source) * session.safariEssence();
		total += price(RAINBOW_FEATHER, source) * session.rainbowFeathers();
		return Math.round(total);
	}

	public static long valueOf(SafariSession session) {
		return valueOf(session, ConfigManager.get().profit.priceSource());
	}

	/** What a saved run's shards are worth, or {@code 0} if it kept no breakdown. */
	public static long valueOf(RunRecord run) {
		if (run == null || !run.hasShardData()) return 0;
		SafariConfig.PriceSource source = ConfigManager.get().profit.priceSource();
		prepareRunValueCache(source);
		Long cached = RUN_VALUE_CACHE.get(run);
		if (cached != null) return cached;
		double total = 0;
		for (Map.Entry<String, Integer> entry : run.shards.entrySet()) {
			// A saved run holds species by name, so one dropped from the roster since is
			// simply skipped — the same thing the rest of the history does with it.
			Critter critter = Critters.byName(entry.getKey());
			if (critter != null) total += price(critter, source) * entry.getValue();
		}
		total += price(SAFARI_ESSENCE, source) * run.safariEssence;
		total += price(RAINBOW_FEATHER, source) * run.rainbowFeathers;
		long value = Math.round(total);
		RUN_VALUE_CACHE.put(run, value);
		return value;
	}

	private static void prepareRunValueCache(SafariConfig.PriceSource source) {
		long historyRevision = RunHistory.revision();
		long priceRevision = BazaarPrices.priceRevision;
		if (runValueHistoryRevision == historyRevision
				&& runValuePriceRevision == priceRevision && runValuePriceSource == source) return;
		RUN_VALUE_CACHE.clear();
		runValueHistoryRevision = historyRevision;
		runValuePriceRevision = priceRevision;
		runValuePriceSource = source;
	}

	/** A run's tax-adjusted value using the selected pricing option. */
	public static String coinsText(SafariSession session) {
		return format(valueOf(session)) + " Coins";
	}

	/** Every priceable run added up. Runs with no breakdown are left out, not zeroed. */
	public static long totalValue(List<RunRecord> runs) {
		long total = 0;
		for (RunRecord run : runs) total += valueOf(run);
		return total;
	}

	/** Full saved-history value, calculated once per history/price/source combination. */
	public static long totalHistoryValue() {
		long historyRevision = RunHistory.revision();
		long priceRevision = BazaarPrices.priceRevision;
		SafariConfig.PriceSource source = ConfigManager.get().profit.priceSource();
		if (totalHistoryRevision == historyRevision
				&& totalHistoryPriceRevision == priceRevision
				&& totalHistoryPriceSource == source) return totalHistoryValue;
		totalHistoryValue = totalValue(RunHistory.runs());
		totalHistoryRevision = historyRevision;
		totalHistoryPriceRevision = priceRevision;
		totalHistoryPriceSource = source;
		return totalHistoryValue;
	}

	/**
	 * Coins as they are said out loud: {@code 1.2M}, {@code 340k}, {@code 8,120}.
	 *
	 * <p>A run is worth millions and the screen has a column's width to say so in.
	 */
	public static String format(long coins) {
		long value = Math.abs(coins);
		if (value >= 1_000_000_000L) return "%.2fB".formatted(coins / 1_000_000_000.0);
		if (value >= 1_000_000L) return "%.2fM".formatted(coins / 1_000_000.0);
		if (value >= 10_000L) return "%.0fk".formatted(coins / 1_000.0);
		return "%,d".formatted(coins);
	}
}
