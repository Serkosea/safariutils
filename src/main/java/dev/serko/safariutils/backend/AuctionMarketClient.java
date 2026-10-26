package dev.serko.safariutils.backend;

import com.google.gson.*;
import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.NetworkRuntime;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.concurrent.HttpBodies;
import java.io.IOException;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Shared compact official-auction estimates; no auction scanning or third-party prices on clients. */
public final class AuctionMarketClient {
    private static final long MAX_AGE = 30 * 60000L;
    private static volatile Snapshot snapshot = new Snapshot(0, Map.of(), "unavailable");
    private static boolean pending, stopped;
    private static long due;
    private static volatile long revision, nextExpiry = Long.MAX_VALUE;
    public static long revision() { expireQuotes(); return revision; }
    private static void expireQuotes() {
        long now = System.currentTimeMillis();
        if (now < nextExpiry) return;
        synchronized (AuctionMarketClient.class) {
            if (now < nextExpiry) return;
            Snapshot old = snapshot;
            Map<String, Quote> quotes = new HashMap<>();
            Map<String, Double> prices = new HashMap<>();
            if (now-old.observedAt < MAX_AGE && now >= old.observedAt) old.quotes.forEach((id,q) -> {
                if (now < q.expiresAt) { quotes.put(id,q); prices.put(id,q.price); }
            });
            snapshot = new Snapshot(old.observedAt,prices,prices.isEmpty()?"unavailable":old.status,quotes);
            revision += 2;
            nextExpiry = prices.isEmpty() ? Long.MAX_VALUE : Math.min(old.observedAt+MAX_AGE,
                quotes.values().stream().mapToLong(Quote::expiresAt).min().orElse(Long.MAX_VALUE));
        }
    }
    public record Quote(double price, int samples, String source, String confidence, long observedAt, long expiresAt) { }
    public record Snapshot(long observedAt, Map<String, Double> prices, String status, Map<String, Quote> quotes) {
        public Snapshot { prices = Map.copyOf(prices); quotes = Map.copyOf(quotes); }
        public Snapshot(long observedAt, Map<String, Double> prices, String status) { this(observedAt, prices, status, Map.of()); }
    }
    public static Optional<Quote> quote(String id) {
        expireQuotes();
        Snapshot value = snapshot;
        Quote quote = value.quotes.get(id);
        long now = System.currentTimeMillis();
        return quote == null || now > quote.expiresAt || now < value.observedAt || now-value.observedAt>MAX_AGE
            ? Optional.empty() : Optional.of(quote);
    }
    private AuctionMarketClient() { }

    public static Optional<Double> price(String id) {
        return quote(id).map(Quote::price);
    }
    public static String status() {
        Snapshot value = snapshot;
        long age = System.currentTimeMillis() - value.observedAt;
        return age < 0 || age > MAX_AGE ? "unavailable" : age > 5 * 60000 ? "stale" : value.status;
    }
    /** Call only in a context that needs auction estimates. No work runs from lookup/render. */
    public static synchronized void requestIfNeeded() {
        long now = System.nanoTime();
        if (stopped || pending || NetworkRuntime.stopping() || due != 0 && now - due < 0) return;
        pending = true;
        NetworkRuntime.submit(AuctionMarketClient::fetch).whenComplete(AuctionMarketClient::apply);
    }
    private static Snapshot fetch() {
        try {
            String token = RemoteSession.instance().token();
            HttpRequest request = HttpRequest.newBuilder(BackendEndpoints.AUCTIONS)
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> response = NetworkRuntime.authenticatedHttp().send(request, HttpBodies.utf8(256 * 1024));
            if (response.statusCode() == 401) RemoteSession.instance().invalidate(token);
            if (response.statusCode() != 200) throw new IOException("Market HTTP " + response.statusCode());
            return parse(response.body(), System.currentTimeMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new CompletionException(interrupted);
        } catch (IOException | RuntimeException failure) { throw new CompletionException(failure); }
    }
    private static synchronized void apply(Snapshot received, Throwable failure) {
        pending = false;
        if (stopped || NetworkRuntime.stopping()) return;
        // Empty initial responses poll at one minute while the server builds its first snapshot.
        due = System.nanoTime() + Duration.ofMinutes(failure == null && received.observedAt == 0 ? 1 : 5).toNanos();
        if (failure == null) {
            snapshot = received; revision += 2;
            nextExpiry = received.quotes.isEmpty() ? Long.MAX_VALUE : Math.min(received.observedAt+MAX_AGE,
                received.quotes.values().stream().mapToLong(Quote::expiresAt).min().orElse(Long.MAX_VALUE));
        }
        if (ConfigManager.automaticLoggingEnabled()) OperationalLog.info("MARKET/AUCTION",
            "result=" + (failure == null ? received.status : failureCode(failure)) + " usable=" + status()
                + " products=" + snapshot.prices.size());
    }
    private static String failureCode(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        String message = failure.getMessage();
        return message != null && message.matches("Market HTTP [0-9]{3}")
            ? message.replace(' ', '-') : failure.getClass().getSimpleName();
    }
    public static synchronized void shutdown() { stopped = true; }

    public static Snapshot parse(String body, long now) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int schema = root.get("schema").getAsBigDecimal().intValueExact();
            if (!(schema == 1 && root.get("coverage").getAsString().equals("kuudra-base-items-v1")
                    || schema == 2 && root.get("coverage").getAsString().equals("safari-kuudra-v2")))
                throw new IOException("Unknown market schema");
            String status = root.get("status").getAsString();
            long observed = root.get("observedAt").getAsBigDecimal().longValueExact();
            JsonObject values = root.getAsJsonObject("prices");
            if (!Set.of("fresh", "stale", "unavailable").contains(status) || values.size() > 256)
                throw new IOException("Invalid market snapshot");
            if (status.equals("unavailable")) {
                if (observed != 0 || !values.isEmpty()) throw new IOException("Invalid unavailable snapshot");
                return new Snapshot(0, Map.of(), status);
            }
            if (observed <= 0 || observed > now + 5000 || now - observed > MAX_AGE) throw new IOException("Expired market snapshot");
            Map<String, Double> prices = new HashMap<>();
            Map<String, Quote> quotes = new HashMap<>();
            for (var entry : values.entrySet()) {
                if (!entry.getKey().matches("[A-Z0-9_]{1,96}")) throw new IOException("Invalid market item");
                JsonObject quote = entry.getValue().getAsJsonObject();
                double price = quote.get("price").getAsDouble();
                int samples = quote.get("samples").getAsBigDecimal().intValueExact();
                if (!Double.isFinite(price) || price <= 0 || price > 1e15 || samples < 1 || samples > 96000)
                    throw new IOException("Invalid market quote");
                String source = schema == 1 ? "listing" : quote.get("source").getAsString();
                String confidence = schema == 1 ? "low" : quote.get("confidence").getAsString();
                long at = schema == 1 ? observed : quote.get("observedAt").getAsBigDecimal().longValueExact();
                long expires = schema == 1 ? observed + MAX_AGE : quote.get("expiresAt").getAsBigDecimal().longValueExact();
                long maximumAge = switch (source) {
                    case "listing" -> MAX_AGE;
                    case "sale" -> Duration.ofDays(30).toMillis();
                    case "historical_listing" -> Duration.ofDays(7).toMillis();
                    default -> throw new IOException("Unknown quote source");
                };
                if (!Set.of("low", "medium").contains(confidence) || at <= 0 || at > now + 5000
                        || expires <= at || expires - at > maximumAge) throw new IOException("Invalid quote provenance");
                if (expires < now) continue;
                prices.put(entry.getKey(), price);
                quotes.put(entry.getKey(), new Quote(price, samples, source, confidence, at, expires));
            }
            return new Snapshot(observed, prices, status, quotes);
        } catch (RuntimeException invalid) { throw new IOException("Malformed market snapshot", invalid); }
    }
}
