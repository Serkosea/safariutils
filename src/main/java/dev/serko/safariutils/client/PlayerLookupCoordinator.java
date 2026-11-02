package dev.serko.safariutils.client;

import dev.serko.safariutils.api.*;
import net.minecraft.client.Minecraft;
import java.util.concurrent.CompletableFuture;

/** Shared UI/command admission, publication and history; transport stays in the API provider. */
final class PlayerLookupCoordinator {
    private static CompletableFuture<SparklingPlayerLookup> active;
    private static String activeName;
    private static final LookupHistory HISTORY=new LookupHistory(10);
    static LookupHistory history() { return HISTORY; }
    static SparklingPlayerLookup remember(SparklingPlayerLookup data,boolean checked) {
        var accepted=checked?HISTORY.rememberChecked(data,dev.serko.safariutils.state.MonotonicTime.nowMillis()):HISTORY.remember(data);
        CanonicalPlayerNames.remember(accepted.username());
        return accepted;
    }
    private PlayerLookupCoordinator() { }
    static synchronized CompletableFuture<SparklingPlayerLookup> lookup(String name) {
        if(name==null||!name.matches("[A-Za-z0-9_]{1,16}")) return failed("Invalid Minecraft username");
        if(active!=null&&!active.isDone()) return name.equalsIgnoreCase(activeName)?active:failed("A lookup is already loading");
        if(!SharedSparklingProviders.available())return failed("API unavailable");
        if(System.currentTimeMillis()<SharedSparklingProviders.lookupAvailableAt())return failed("Lookup cooldown active");
        Minecraft client=Minecraft.getInstance();
        var account=client.player==null?null:client.player.getUUID();
        activeName=name;
        var result=new CompletableFuture<SparklingPlayerLookup>(); active=result;
        SharedSparklingProviders.lookupPlayer(name).whenComplete((data,error)->client.execute(()->{
            if(error!=null){result.completeExceptionally(error);return;}
            if(account==null||client.player==null||!account.equals(client.player.getUUID())) {
                result.completeExceptionally(new IllegalStateException("Player session changed"));return;
            }
            try {
                if(data==null)throw new IllegalStateException("Empty lookup response");
                result.complete(remember(data,true));
            } catch(RuntimeException failure) { result.completeExceptionally(failure); }
        }));
        return result;
    }
    private static CompletableFuture<SparklingPlayerLookup> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
