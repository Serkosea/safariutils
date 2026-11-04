package dev.serko.safariutils.backend;
import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class WebSocketOutboxRegression {
    public static int run() {
        List<String> sent = new ArrayList<>();
        List<CompletableFuture<WebSocket>> operations = new ArrayList<>();
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(), new Class<?>[]{WebSocket.class}, (proxy, method, args) -> {
            if (method.getName().equals("sendText")) {
                sent.add(args[0].toString()); var future = new CompletableFuture<WebSocket>(); operations.add(future); return future;
            }
            if (method.getName().equals("sendClose")) return CompletableFuture.completedFuture((WebSocket)proxy);
            return null;
        });
        var queue = new WebSocketOutbox(socket);
        var first = queue.send("hello"); var second = queue.send("report");
        if (!sent.equals(List.of("hello")) || second.isDone()) throw new AssertionError("text sends must serialize");
        operations.get(0).complete(socket);
        if (!sent.equals(List.of("hello","report")) || !first.isDone()) throw new AssertionError("second send starts after first");
        operations.get(1).complete(socket);
        for (int i=0;i<32;i++) queue.send("pending");
        if (!queue.send("overflow").isCompletedExceptionally()) throw new AssertionError("outbox must be bounded");
        operations.get(2).completeExceptionally(new IllegalStateException("test disconnect"));
        queue.close();
        if (!queue.send("closed").isCompletedExceptionally()) throw new AssertionError("closed queue rejects new sends");
        return 4;
    }
}
