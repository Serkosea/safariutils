package dev.serko.safariutils.client;

import dev.serko.safariutils.api.*;
import dev.serko.safariutils.data.*;
import dev.serko.safariutils.state.*;
import com.google.gson.JsonParser;
import java.util.function.*;

/** Temporary remote assistance; never writes local catches or objective trackers. */
final class PartyCompletionSync {
    private static final CompletionSnapshot.Store remote=new CompletionSnapshot.Store();
    private static String run="", lastPayload="";
    private static long nextSample,nextRefresh;
    private static boolean wasReady;
    static void reset() { remote.clear();run="";lastPayload="";nextSample=nextRefresh=0;wasReady=false; }
    static void reconcile(String text) {
        var value=JsonParser.parseString(text).getAsJsonObject();
        if("pending".equals(value.get("status").getAsString())) { remote.clear();wasReady=false;return; }
        String next=value.get("runId").getAsString();
        if(!next.equals(run)) {remote.clear();lastPayload="";run=next;}
        nextSample=nextRefresh=0;
    }
    static void accept(PartySyncNetworkProvider.Message message) {
        if(run.isEmpty() || message.senderUuid().isEmpty())return;
        remote.accept(message.senderUuid(),CompletionSnapshot.parse(message.payload(),run),message.ageMillis(),MonotonicTime.nowMillis());
    }
    static void tick(BiConsumer<String,String> sender,Predicate<Critter> localSources) {
        boolean ready=PartySyncNetworks.completionSupported();
        if(!ready) { wasReady=false;return; }
        if(!wasReady) {nextSample=nextRefresh=0;lastPayload="";} wasReady=true;
        long now=MonotonicTime.nowMillis();if(now<nextSample)return;nextSample=now+500;
        String payload=BiomeCoordination.localCompletionSnapshot(localSources).payload();
        if(!payload.equals(lastPayload) || now>=nextRefresh) { sender.accept("C",payload);lastPayload=payload;nextRefresh=now+10000; }
    }
    static boolean completes(Critter critter,RunMode mode,boolean localCapture) {
        if(!PartySyncNetworks.replayReady() || SparklingWatch.outstandingCount(critter)>0)return false;
        int index=Critters.selectionOrder().indexOf(critter);if(index<0)return false;
        long now=MonotonicTime.nowMillis();int flags=remote.value(index,0,now);
        if(mode==RunMode.FULL_CLEAR && DetectedCritters.currentConcurrent(critter)>0)return false;
        return switch(mode) {
            case FULL_CLEAR -> (flags&4)!=0;
            case SPARKLING -> (flags&2)!=0;
            case UNIQUE -> (flags&1)!=0 || (flags&2)!=0 && (localCapture || remote.value(index,2,now)>0);
        };
    }
    static String status() { return "Completion reporters: "+remote.size(MonotonicTime.nowMillis()); }
}
