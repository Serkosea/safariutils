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
        field(provider, "signature", "m123A||" + id + "|" + id);
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
        modernReplay(context,socket);
        efficientTransport();
        noticeLifecycle();
        return 35;
    }
    private static void noticeLifecycle() {
        check(ConnectionNotices.closedReason(1008, "message rate exceeded").contains("limit"), "socket limit is explained");
        check(!ConnectionNotices.closedReason(1008, "private payload").contains("private"), "unrecognized close text stays private");
        var notices = new ConnectionNotices();
        notices.visit("visit-one");
        check(notices.poll(true) == null, "healthy initial connection is silent");
        notices.failure(ConnectionNotices.reason(429, null));
        notices.failure("duplicate");
        var warning = notices.poll(false);
        check(warning != null && warning.text().contains("limit") && !warning.recovered(), "first failure explains limit");
        check(notices.poll(false) == null, "retry is silent");
        notices.visit("visit-one");
        notices.failure("duplicate");
        check(notices.poll(false) == null, "same visit reconnect retains suppression");
        check(notices.poll(true).recovered(), "reconciled connection produces recovery");
        notices.failure("flapping");
        check(notices.poll(false) == null && notices.poll(true) == null, "flapping never floods the same run");
        notices.visit("visit-two");
        notices.failure("old pending error");
        notices.visit("visit-three");
        check(notices.poll(false) == null, "old visit queued failure is discarded");
        notices.failure(ConnectionNotices.reason(426, null));
        check(notices.poll(false).text().contains("update"), "new visit can report its own error");
        check(ConnectionNotices.reason(0, new java.io.IOException("secret token")).equals(
            "connection interrupted. Retrying automatically."), "exception contents remain private");
        check(ConnectionNotices.reason(0, new java.io.IOException("Party sync response timeout")).contains("timed out"),
            "fixed watchdog reason is understandable");
    }
    private static void efficientTransport() throws Exception {
        var sent = new ArrayList<com.google.gson.JsonObject>();
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(), new Class<?>[]{WebSocket.class},
            (proxy, method, args) -> {
                if (method.getName().equals("sendText")) sent.add(com.google.gson.JsonParser.parseString(args[0].toString()).getAsJsonObject());
                return method.getReturnType() == CompletableFuture.class ? CompletableFuture.completedFuture((WebSocket)proxy) : null;
            });
        String uuid = "a".repeat(32), visit = UUID.randomUUID().toString(), run = UUID.randomUUID().toString();
        var provider = new RemotePartySyncProvider();
        field(provider,"context",new PartySyncNetworkProvider.Context("m123",uuid,List.of(uuid),visit));
        call(provider,"opened",new Class<?>[]{long.class,WebSocket.class},0L,socket);
        field(provider,"connected",true);field(provider,"runSync",true);field(provider,"efficient",true);
        field(provider,"replayReady",true);field(provider,"runId",run);
        provider.send(100,"N","1");provider.send(101,"U","1,0,0,0,-1,0");
        check(sent.isEmpty(),"ordinary updates wait for the same-tick batch");
        provider.send(102,"S","1,0,1,0");
        check(sent.size()==2 && sent.get(0).get("type").getAsString().equals("batch"),"urgent catch flushes ordinary batch first");
        check(sent.get(0).getAsJsonArray("frames").size()==2 && sent.get(1).get("kind").getAsString().equals("S"),"catch is never deferred into a batch");
        var full = new com.google.gson.JsonObject();full.addProperty("version",1);
        var rows = new com.google.gson.JsonArray();for(int i=0;i<37;i++){var row=new com.google.gson.JsonArray();row.add(1);row.add(2);row.add(3);rows.add(row);}full.add("rows",rows);
        provider.send(103,"C",full.toString());call(provider,"flushFrames",new Class<?>[]{});
        long revision=sent.get(2).getAsJsonArray("frames").get(0).getAsJsonObject().get("sequence").getAsLong();
        provider.send(104,"C",full.toString());call(provider,"flushFrames",new Class<?>[]{});
        var refresh=sent.get(3).getAsJsonArray("frames").get(0).getAsJsonObject();
        check(refresh.get("kind").getAsString().equals("F"),"unchanged completion uses compact refresh");
        check(com.google.gson.JsonParser.parseString(refresh.get("payload").getAsString()).getAsJsonObject().get("revision").getAsLong()==revision,"refresh references full baseline");
        call(provider,"resendCompletion",new Class<?>[]{long.class},0L);
        var recovered=sent.get(4).getAsJsonArray("frames").get(0).getAsJsonObject();
        check(recovered.get("kind").getAsString().equals("C") && recovered.get("sequence").getAsLong()>refresh.get("sequence").getAsLong(),"missing baseline resends full state with a fresh ordered sequence");
        provider.send(105,"N","3");provider.disconnect();
        call(provider,"opened",new Class<?>[]{long.class,WebSocket.class},1L,socket);
        field(provider,"connected",true);call(provider,"flushFrames",new Class<?>[]{});
        check(sent.size()==5,"reconnect cannot flush an old room batch");
        field(provider,"updateRequired",true);
        check((boolean)get(provider,"updateRequired"),"update requirement is retained independently of visit state");
        provider.disconnect();
    }
    private static void modernReplay(PartySyncNetworkProvider.Context old, WebSocket socket) throws Exception {
        String visit=UUID.randomUUID().toString(),run=UUID.randomUUID().toString(),connection=UUID.randomUUID().toString();
        var context=new PartySyncNetworkProvider.Context(old.lobbyId(),old.localUuid(),old.roster(),visit);
        var provider=new RemotePartySyncProvider();
        field(provider,"context",context);field(provider,"signature",context.lobbyId()+"|"+visit+"|"+context.localUuid()+"|"+context.localUuid());
        call(provider,"opened",new Class<?>[]{long.class,WebSocket.class},0L,socket);
        Class<?> type=Class.forName("dev.serko.safariutils.backend.RemotePartySyncProvider$Listener");
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object listener=constructor.newInstance(provider,0L,context);
        String tag="t".repeat(43);
        call(listener,"receiveMessage",new Class<?>[]{String.class},"{\"type\":\"welcome\",\"self\":\""+tag+"\",\"roster\":[\""+tag+"\"],\"connectionId\":\""+connection+"\",\"features\":[\"run-reconciliation-v1\",\"critter-completion-v1\",\"room-sparklings-v1\",\"hotspots-v1\"]}");
        check(provider.connected() && !provider.replayReady(),"welcome alone is not replay readiness");
        String common="\"version\":1,\"connectionId\":\""+connection+"\",\"visitId\":\""+visit+"\",\"runId\":\""+run+"\"";
        call(listener,"receiveMessage",new Class<?>[]{String.class},"{\"type\":\"reconcile\",\"status\":\"new\","+common+"}");
        call(listener,"receiveMessage",new Class<?>[]{String.class},"{\"type\":\"replay-complete\","+common+"}");
        check(!provider.replayReady(),"network receipt alone cannot mark replay applied");
        provider.tick(context,message->check(!provider.replayReady(),"reconciliation callback precedes ready boundary"));
        check(provider.replayReady() && provider.completionSupported() && provider.runId().equals(run),"ordered queue application opens completion gate");
        call(listener,"receiveMessage",new Class<?>[]{String.class},"{\"type\":\"hotspots-unavailable\"}");
        check(!provider.connected() && !provider.replayReady() && !provider.coordinationSupported(),"authority outage closes the whole connection, not just assignments");
        check(provider.connectedPlayers().isEmpty(),"unavailable service cannot advertise connected peers");
        provider.disconnect();check(!provider.replayReady(),"disconnect closes readiness gate");
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
