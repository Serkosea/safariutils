package dev.serko.safariutils.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class BazaarMarketRegression {
    private static int checks;
    public static int run() throws Exception {
        checks = 0;
        var snapshot = parse("{\"products\":{\"A\":{\"sell_summary\":[{\"pricePerUnit\":3},{\"pricePerUnit\":5}],\"buy_summary\":[{\"pricePerUnit\":9},{\"pricePerUnit\":7}]}}}");
        var product = snapshot.products().get("A");
        check(product.firstSell() == 3 && product.firstBuy() == 9, "First-book policy retained");
        check(product.instantSell() == 5 && product.sellOffer() == 7, "Best-book policy retained");
        try { snapshot.products().clear(); throw new AssertionError("Mutable snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        for (String bad : new String[]{"{}", "{\"products\":{}}", "{\"success\":false,\"products\":{\"A\":{}}}",
            "{\"products\":{\"A\":{},\"A\":{}}}", "{\"products\":{\"A\":{}}} true",
            "{\"products\":{\"A\":{\"sell_summary\":[{\"pricePerUnit\":-1}]}}}",
            "{\"products\":{\"A\":{\"sell_summary\":[],\"sell_summary\":[]}}}"}) {
            try { parse(bad); throw new AssertionError("Invalid response accepted"); }
            catch (IOException | IllegalStateException expected) { checks++; }
        }
        AtomicLong clock = new AtomicLong(1);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<CompletableFuture<BazaarMarket.Snapshot>> source = new AtomicReference<>();
        var cache = new BazaarMarket.Cache(clock::get, () -> { requests.incrementAndGet(); return source.get(); });
        source.set(new CompletableFuture<>());
        var first = cache.request(); var second = cache.request();
        check(requests.get() == 1, "Concurrent consumers share one fetch");
        first.cancel(false);
        source.get().complete(snapshot);
        check(second.join() == snapshot, "Consumer cancellation does not poison another consumer");
        check(cache.request().join() == snapshot && requests.get() == 1, "Fresh snapshot reused");
        clock.addAndGet(Duration.ofMinutes(5).toNanos());
        source.set(new CompletableFuture<>());
        var failed = cache.request();
        source.get().completeExceptionally(new BazaarMarket.StatusFailure(429));
        check(failed.isCompletedExceptionally() && requests.get() == 2, "Rate limit propagated once");
        for (int i = 0; i < 100; i++) check(cache.request().isCompletedExceptionally(), "Failure cooldown shared");
        check(requests.get() == 2, "No failure request storm");
        clock.addAndGet(Duration.ofMinutes(9).toNanos()); cache.request();
        check(requests.get() == 2, "Rate-limit delay exceeds normal refresh");
        clock.addAndGet(Duration.ofMinutes(1).toNanos());
        source.set(CompletableFuture.failedFuture(new BazaarMarket.StatusFailure(429))); cache.request();
        check(requests.get() == 3, "Retry after rate-limit delay");
        clock.addAndGet(Duration.ofMinutes(19).toNanos()); cache.request();
        check(requests.get() == 3, "Repeated rate limit doubles delay");
        clock.addAndGet(Duration.ofMinutes(1).toNanos());
        source.set(CompletableFuture.completedFuture(snapshot));
        check(cache.request().join() == snapshot && requests.get() == 4, "Recovery with immediately completed future");
        clock.addAndGet(Duration.ofMinutes(5).toNanos()); source.set(new CompletableFuture<>());
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Future<CompletableFuture<BazaarMarket.Snapshot>>>();
            for (int i = 0; i < 100; i++) tasks.add(callers.submit(cache::request));
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            check(requests.get() == 5, "Parallel admission remains single-flight");
            source.get().complete(snapshot);
            for (var task : tasks) check(task.get().join() == snapshot, "All consumers receive same immutable snapshot");
        } finally { callers.shutdownNow(); }
        var rejected = new BazaarMarket.Cache(clock::get, () -> { throw new RejectedExecutionException(); });
        check(rejected.request().isCompletedExceptionally(), "Synchronous queue rejection settles admission");
        check(rejected.request().isCompletedExceptionally(), "Queue rejection observes cooldown");
        clock.addAndGet(Duration.ofMinutes(5).toNanos()); source.set(new CompletableFuture<>());
        var closing = cache.request(); cache.close();
        check(closing.isCompletedExceptionally(), "Shutdown settles waiting consumers");
        source.get().complete(new BazaarMarket.Snapshot(Map.of()));
        check(cache.request().isCompletedExceptionally() && requests.get() == 6, "No requests after shutdown");
        return checks;
    }
    private static BazaarMarket.Snapshot parse(String input) throws IOException {
        return BazaarMarket.parse(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
