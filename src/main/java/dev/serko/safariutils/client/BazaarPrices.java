package dev.serko.safariutils.client;

import com.google.gson.stream.JsonReader;
import dev.serko.safariutils.SafariUtils;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.session.RunRecord;
import dev.serko.safariutils.session.SafariSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches shard prices from Hypixel's public Bazaar API when profit is visible.
 * Instant sell uses the best buy order; sell offer uses the cheapest sell order.
 * A daemon worker applies retry and backoff without blocking the client thread.
 */
public final class BazaarPrices {

	private static final URI ENDPOINT = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
	private static final String SAFARI_ESSENCE = "ESSENCE_SAFARI";
	private static final String RAINBOW_FEATHER = "RAINBOW_FEATHER";

	/** The endpoint updates slowly enough that refreshing more often adds no value. */
	private static final long REFRESH_MILLIS = 120_000;
	/** Failed requests retry sooner than the normal refresh without rapid polling. */
	private static final long RETRY_MILLIS = 60_000;
	private static final int QUIET_FAILURES = 3;

	/** Bazaar tax on the sell side, which both price modes are. */
	private static final double TAX = 0.0125;

	/** The Safari products, so the parser can skip the rest without building them. */
	private static final Set<String> WANTED = wantedIds();

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "safariutils-bazaar");
		thread.setDaemon(true);
		return thread;
	});

	private static final AtomicBoolean fetching = new AtomicBoolean();

	private static volatile Map<String, Price> prices = Map.of();
	private static volatile long fetchedAt;
	private static volatile long nextFetchAt;
	private static volatile String lastError;
	private static volatile HttpClient http;
	/** Written by the worker and reset after a successful response. */
	private static volatile int failures;

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
		if (!ConfigManager.get().profit.enabled) return;

		long now = System.currentTimeMillis();
		if (now < nextFetchAt) return;
		// Held off before the job starts, so a slow request cannot queue a second one.
		nextFetchAt = now + REFRESH_MILLIS;
		if (!fetching.compareAndSet(false, true)) return;
		WORKER.execute(BazaarPrices::fetch);
	}

	private static void fetch() {
		try {
			HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", SafariUtils.MOD_ID)
				.GET().build();
			HttpResponse<InputStream> response = client()
				.send(request, HttpResponse.BodyHandlers.ofInputStream());
			Map<String, Price> parsed;
			try (InputStream body = response.body()) {
				if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
				parsed = parse(body);
			}
			// An empty result is a shape change, not an update: the old prices are still
			// the best thing known, so they are kept rather than blanked.
			if (parsed.isEmpty()) throw new IOException("no shard products in the response");

			// Keep the last known value for a product omitted from a partial response.
			Map<String, Price> merged = new HashMap<>(prices);
			merged.putAll(parsed);
			prices = Map.copyOf(merged);
			fetchedAt = System.currentTimeMillis();
			lastError = null;
			failures = 0;
			nextFetchAt = fetchedAt + REFRESH_MILLIS;
		} catch (Exception failed) {
			failures++;
			lastError = failed.getClass().getSimpleName()
				+ (failed.getMessage() == null ? "" : ": " + failed.getMessage());
			nextFetchAt = System.currentTimeMillis() + RETRY_MILLIS;
			// Quiet at first — a dropped request during a server hop is not news. Said once
			// when it stops looking like a blip, and not repeated every retry after that.
			if (failures == QUIET_FAILURES + 1) {
				SafariUtils.LOGGER.warn("Bazaar prices unavailable ({}), backing off", lastError);
			}
		} finally {
			fetching.set(false);
		}
	}

	/** Stops the owned worker when the client closes; the reusable HTTP client needs no close call. */
	public static void shutdown() {
		WORKER.shutdownNow();
	}

	private static HttpClient client() {
		HttpClient existing = http;
		if (existing != null) return existing;
		HttpClient built = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		http = built;
		return built;
	}

	/**
	 * Reads the response as a stream, keeping only the 37 products this mod cares about.
	 *
	 * <p>The whole payload is several megabytes of order books for two thousand products.
	 * Skipping the rest as they go past costs nothing and never builds them.
	 */
	private static Map<String, Price> parse(InputStream body) throws IOException {
		Map<String, Price> found = new HashMap<>();
		try (JsonReader reader = new JsonReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
			reader.beginObject();
			while (reader.hasNext()) {
				if (!reader.nextName().equals("products")) {
					reader.skipValue();
					continue;
				}
				reader.beginObject();
				while (reader.hasNext()) {
					String id = reader.nextName();
					if (!WANTED.contains(id)) {
						reader.skipValue();
						continue;
					}
					found.put(id, product(reader));
				}
				reader.endObject();
			}
			reader.endObject();
		}
		return found;
	}

	private static Price product(JsonReader reader) throws IOException {
		double instantSell = 0;
		double sellOffer = 0;
		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				// Hypixel's naming is the reverse of what it reads like: sell_summary holds
				// the buy orders you can sell into, buy_summary the sell offers you buy from.
				case "sell_summary" -> instantSell = topOfBook(reader, true);
				case "buy_summary" -> sellOffer = topOfBook(reader, false);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		return new Price(instantSell, sellOffer);
	}

	/** The best price in one summary — highest for buy orders, lowest for sell offers. */
	private static double topOfBook(JsonReader reader, boolean highest) throws IOException {
		double best = 0;
		reader.beginArray();
		while (reader.hasNext()) {
			double price = 0;
			reader.beginObject();
			while (reader.hasNext()) {
				if (reader.nextName().equals("pricePerUnit")) price = reader.nextDouble();
				else reader.skipValue();
			}
			reader.endObject();
			if (price <= 0) continue;
			best = best == 0 ? price : highest ? Math.max(best, price) : Math.min(best, price);
		}
		reader.endArray();
		return best;
	}

	// --- pricing -------------------------------------------------------------

	/** True once a fetch has landed, so a view can say "not yet" instead of "nothing". */
	public static boolean known() {
		return fetchedAt != 0 && !prices.isEmpty();
	}

	/** Changes only when a fresh price snapshot is installed. */
	static long revision() {
		return fetchedAt;
	}

	/** How long ago the prices were fetched, or {@code -1} if they never have been. */
	public static long ageMillis() {
		return fetchedAt == 0 ? -1 : System.currentTimeMillis() - fetchedAt;
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
		double total = 0;
		for (Map.Entry<String, Integer> entry : run.shards.entrySet()) {
			// A saved run holds species by name, so one dropped from the roster since is
			// simply skipped — the same thing the rest of the history does with it.
			Critter critter = Critters.byName(entry.getKey());
			if (critter != null) total += price(critter, source) * entry.getValue();
		}
		total += price(SAFARI_ESSENCE, source) * run.safariEssence;
		total += price(RAINBOW_FEATHER, source) * run.rainbowFeathers;
		return Math.round(total);
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
