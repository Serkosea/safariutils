package dev.serko.safariutils.client;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Process-wide public Bazaar transport/cache. Consumers own valuation, not duplicate requests. */
public final class BazaarMarket {
    private static final long REFRESH = Duration.ofMinutes(5).toNanos();
    private static final long MAX_BACKOFF = Duration.ofHours(1).toNanos();
    private static final Duration BUDGET = Duration.ofSeconds(20);
    private static final Cache CACHE = new Cache(System::nanoTime, () -> NetworkRuntime.submit(BazaarMarket::fetch));

    /** Keep first-book entries for consumers that require original order, plus best prices for valuation. */
    public record Product(Double firstSell, Double firstBuy, double instantSell, double sellOffer) { }
    public record Snapshot(Map<String, Product> products) {
        public Snapshot { products = Map.copyOf(products); }
    }
    private BazaarMarket() { }

    public static CompletableFuture<Snapshot> request() { return CACHE.request(); }
    public static Snapshot snapshot() { return CACHE.snapshot; }
    public static void shutdown() { CACHE.close(); }

    private static Snapshot fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                .timeout(BUDGET).header("User-Agent", "SafariUtils").GET().build();
            Snapshot result = NetworkRuntime.read(request, 16 * 1024 * 1024, BUDGET, (status, body) -> {
                if (status != 200) throw new StatusFailure(status);
                return parse(body);
            });
            if (ConfigManager.automaticLoggingEnabled())
                OperationalLog.info("MARKET/BAZAAR", "request completed products=" + result.products().size());
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new CompletionException(interrupted);
        } catch (IOException | RuntimeException failure) {
            if (ConfigManager.automaticLoggingEnabled())
                OperationalLog.info("MARKET/BAZAAR", "request failed type=" + failure.getClass().getSimpleName()
                    + " status=" + (failure instanceof StatusFailure status ? status.status : -1));
            throw new CompletionException(failure);
        }
    }

    static final class StatusFailure extends IOException {
        final int status;
        StatusFailure(int status) { super("HTTP " + status); this.status = status; }
    }

    /** Short synchronized admission only; never waits for network work. Caller cancellation is isolated. */
    static final class Cache {
        private final LongSupplier clock;
        private final Supplier<CompletableFuture<Snapshot>> fetch;
        private CompletableFuture<Snapshot> pending, settled;
        private long due, backoff = REFRESH * 2;
        private boolean stopped;
        private volatile Snapshot snapshot = new Snapshot(Map.of());
        Cache(LongSupplier clock, Supplier<CompletableFuture<Snapshot>> fetch) { this.clock = clock; this.fetch = fetch; }
        synchronized CompletableFuture<Snapshot> request() {
            if (stopped) return CompletableFuture.failedFuture(new CancellationException("Bazaar service stopped"));
            if (pending != null) return pending.copy();
            if (settled != null && clock.getAsLong() - due < 0) return settled.copy();
            CompletableFuture<Snapshot> owner = new CompletableFuture<>();
            pending = owner;
            CompletableFuture<Snapshot> source;
            try { source = Objects.requireNonNull(fetch.get()); }
            catch (RuntimeException failure) { source = CompletableFuture.failedFuture(failure); }
            source.whenComplete((value, failure) -> complete(owner, value, failure));
            return owner.copy();
        }
        private void complete(CompletableFuture<Snapshot> owner, Snapshot value, Throwable failure) {
            synchronized (this) {
                if (pending != owner || stopped) return;
                if (failure == null && value == null) failure = new IOException("Missing Bazaar snapshot");
                Throwable cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                boolean limited = cause instanceof StatusFailure status && status.status == 429;
                due = clock.getAsLong() + (limited ? backoff : REFRESH);
                backoff = limited ? Math.min(MAX_BACKOFF, backoff * 2) : failure == null ? REFRESH * 2 : backoff;
                if (failure == null) snapshot = value;
                settled = failure == null ? CompletableFuture.completedFuture(value) : CompletableFuture.failedFuture(failure);
                pending = null;
            }
            // Consumer continuations cannot execute while holding the cache admission lock.
            if (failure == null) owner.complete(value); else owner.completeExceptionally(failure);
        }
        void close() {
            CompletableFuture<Snapshot> cancel;
            synchronized (this) { stopped = true; cancel = pending; pending = null; }
            if (cancel != null) cancel.completeExceptionally(new CancellationException("Bazaar service stopped"));
        }
    }

    public static Snapshot parse(InputStream body) throws IOException {
        Map<String, Product> products = new HashMap<>();
        boolean seen = false, success = true;
        try (JsonReader reader = new JsonReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "success" -> success = reader.nextBoolean();
                    case "products" -> {
                        if (seen) throw new IOException("Duplicate products");
                        seen = true; reader.beginObject();
                        while (reader.hasNext()) {
                            String id = reader.nextName();
                            if (id.isEmpty() || id.length() > 256 || products.size() >= 100_000 || products.containsKey(id))
                                throw new IOException("Invalid Bazaar product");
                            Book sell = new Book(null, 0), buy = new Book(null, 0);
                            reader.beginObject();
                            boolean sellSeen = false, buySeen = false;
                            while (reader.hasNext()) {
                                switch (reader.nextName()) {
                                    case "sell_summary" -> {
                                        if (sellSeen) throw new IOException("Duplicate sell book");
                                        sellSeen = true; sell = book(reader, true);
                                    }
                                    case "buy_summary" -> {
                                        if (buySeen) throw new IOException("Duplicate buy book");
                                        buySeen = true; buy = book(reader, false);
                                    }
                                    default -> reader.skipValue();
                                }
                            }
                            reader.endObject();
                            products.put(id, new Product(sell.first, buy.first, sell.best, buy.best));
                        }
                        reader.endObject();
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || !seen || !success || products.isEmpty())
                throw new IOException("Incomplete Bazaar snapshot");
        }
        return new Snapshot(products);
    }
    private record Book(Double first, double best) { }
    private static Book book(JsonReader reader, boolean highest) throws IOException {
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return new Book(null, 0); }
        Double first = null; double best = 0; int count = 0;
        reader.beginArray();
        while (reader.hasNext()) {
            if (++count > 10_000) throw new IOException("Oversized Bazaar book");
            Double price = null;
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("pricePerUnit")) {
                    price = reader.nextDouble();
                    if (!Double.isFinite(price) || price < 0) throw new IOException("Invalid Bazaar price");
                } else reader.skipValue();
            }
            reader.endObject();
            if (count == 1) first = price;
            if (price != null && price > 0) best = best == 0 ? price : highest ? Math.max(best, price) : Math.min(best, price);
        }
        reader.endArray(); return new Book(first, best);
    }
}
