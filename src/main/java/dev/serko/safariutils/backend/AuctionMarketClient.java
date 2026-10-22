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
    public record Snapshot(long observedAt, Map<String, Double> prices, String status) {
        public Snapshot { prices = Map.copyOf(prices); }
    }
    private AuctionMarketClient() { }

    public static Optional<Double> price(String id) {
        Snapshot value = snapshot;
        long age = System.currentTimeMillis() - value.observedAt;
        return age < 0 || age > MAX_AGE ? Optional.empty() : Optional.ofNullable(value.prices.get(id));
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
        if (failure == null) snapshot = received;
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
            if (root.get("schema").getAsBigDecimal().intValueExact() != 1 || !root.get("coverage").getAsString().equals("kuudra-base-items-v1"))
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
            for (var entry : values.entrySet()) {
                if (!entry.getKey().matches("[A-Z0-9_]{1,96}")) throw new IOException("Invalid market item");
                JsonObject quote = entry.getValue().getAsJsonObject();
                double price = quote.get("price").getAsDouble();
                int samples = quote.get("samples").getAsBigDecimal().intValueExact();
                if (!Double.isFinite(price) || price <= 0 || price > 1e15 || samples < 1 || samples > 96000)
                    throw new IOException("Invalid market quote");
                prices.put(entry.getKey(), price);
            }
            return new Snapshot(observed, prices, status);
        } catch (RuntimeException invalid) { throw new IOException("Malformed market snapshot", invalid); }
    }
}
