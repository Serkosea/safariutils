package dev.serko.safariutils.backend;

import dev.serko.safariutils.api.PartySyncNetworkProvider;
import dev.serko.safariutils.api.ServiceRequestException;
import dev.serko.safariutils.state.MonotonicTime;
import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Exercises watchdogs and stale callbacks without contacting any server. */
public final class PartyRecoveryRegression {
    public static int run() throws Exception {
        String id = "a".repeat(32);
        var context = new PartySyncNetworkProvider.Context("m123A", id, List.of(id));
        var provider = new RemotePartySyncProvider();
        field(provider, "signature", "m123A|" + id + "|" + id);
        field(provider, "context", context);
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(), new Class<?>[]{WebSocket.class},
            (proxy, method, args) -> method.getReturnType() == CompletableFuture.class
                ? CompletableFuture.completedFuture((WebSocket) proxy) : null);
        call(provider, "opened", new Class<?>[]{long.class, WebSocket.class}, 0L, socket);
        field(provider, "openedAt", MonotonicTime.nowMillis() - 13000);
        provider.tick(context, message -> { });
        check(!provider.connected() && get(provider, "socket") == null, "unwelcomed socket must reconnect");
        long retry = (long) get(provider, "retryAt");
        check(retry > MonotonicTime.nowMillis(), "watchdog applies backoff");
        call(provider, "opened", new Class<?>[]{long.class, WebSocket.class}, 1L, socket);
        call(provider, "ready", new Class<?>[]{long.class, Map.class, boolean.class}, 1L, Map.of(), true);
        field(provider, "openedAt", MonotonicTime.nowMillis() - 13000);
        provider.tick(context, message -> { });
        check(!provider.connected(), "missing atomic snapshot must reconnect");
        call(provider, "opened", new Class<?>[]{long.class, WebSocket.class}, 2L, socket);
        call(provider, "ready", new Class<?>[]{long.class, Map.class, boolean.class}, 2L, Map.of(), false);
        field(provider, "openedAt", MonotonicTime.nowMillis() - 13000);
        provider.tick(context, message -> { });
        check(provider.connected(), "legacy welcome without atomic feature remains usable");
        call(provider, "failed", new Class<?>[]{long.class, Throwable.class}, 1L, new java.io.IOException());
        check(provider.connected(), "stale connection callback cannot close replacement");
        field(provider, "lastInboundAt", MonotonicTime.nowMillis() - 76000);
        provider.tick(context, message -> { });
        check(!provider.connected(), "silent connected socket must reconnect");
        call(provider, "failed", new Class<?>[]{long.class, Throwable.class}, 3L, new ServiceRequestException("limited", 429, 120));
        check((long)get(provider, "retryAt") - MonotonicTime.nowMillis() > 119000, "service backoff respected");
        provider.disconnect();
        check((long)get(provider, "retryAt") - MonotonicTime.nowMillis() > 119000, "room changes cannot bypass service limits");
        return 8;
    }
    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void field(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, args);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
