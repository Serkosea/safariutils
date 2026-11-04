package dev.serko.safariutils.backend;

import java.net.http.WebSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, non-blocking text serialization: the JDK permits only one pending text send. */
public final class WebSocketOutbox {
    private final WebSocket socket;
    private final AtomicInteger pending = new AtomicInteger();
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private boolean closed;
    public WebSocketOutbox(WebSocket socket) { this.socket = socket; }
    public synchronized CompletableFuture<Void> send(String text) {
        if (closed || pending.get() >= 32) return CompletableFuture.failedFuture(new IllegalStateException("Party send queue unavailable"));
        pending.incrementAndGet();
        tail = tail.thenCompose(ignored -> socket.sendText(text, true).thenApply(sent -> (Void) null))
            .whenComplete((ignored, error) -> pending.decrementAndGet());
        return tail;
    }
    public synchronized void close() {
        if (closed) return;
        closed = true;
        tail.handle((ignored, error) -> null).thenCompose(ignored -> socket.sendClose(WebSocket.NORMAL_CLOSURE, "leaving Safari"))
            .orTimeout(2, TimeUnit.SECONDS).whenComplete((ignored, error) -> socket.abort());
    }
}
