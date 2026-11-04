package dev.serko.safariutils.client;

import dev.serko.safariutils.session.*;
import dev.serko.safariutils.state.MonotonicTime;
import java.util.*;

/** Read-only session projection. No new reward store or price requests; refreshed only while viewed. */
final class SessionProfitSummary {
    private static final long LAUNCH=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    record Line(String text,int colour) { }
    record Snapshot(List<Line> lines) { }
    private long nextRefresh;
    private Snapshot cached;
    /** Live state replaces an archived copy at the save boundary; old launches are excluded. */
    static List<RunRecord> sessionRuns(List<RunRecord> history,RunRecord live,long launch) {
        Map<Long,RunRecord> selected=new LinkedHashMap<>();
        for(var run:history)if(run.started>=launch)selected.put(run.started,run);
        if(live!=null&&live.started>=launch)selected.put(live.started,live);
        return List.copyOf(selected.values());
    }
    Snapshot snapshot() {
        long now=MonotonicTime.nowMillis();
        if(cached!=null&&now<nextRefresh)return cached;
        nextRefresh=now+1000;
        var current=SessionManager.current();
        RunRecord live=null;
        if(current!=null&&!SessionManager.persistenceSuppressed()&&current.startedAtMillis()>=LAUNCH) {
            live=RunRecord.of(current); live.ended=live.started+current.elapsedMillis(System.currentTimeMillis());
        }
        List<RunRecord> runs=sessionRuns(RunHistory.runs(),live,LAUNCH);
        long value=0,time=0,catches=0,shards=0,essence=0,feathers=0,sparklings=0,dyes=0; boolean incomplete=false;
        for(var run:runs) {
            boolean active=current!=null&&run.started==current.startedAtMillis();
            value+=active?BazaarPrices.valueOf(current):BazaarPrices.valueOf(run);
            time+=run.durationMillis(); catches+=run.own.values().stream().mapToInt(Integer::intValue).sum();
            shards+=run.totalShards;essence+=run.safariEssence;feathers+=run.rainbowFeathers;sparklings+=run.sparklings.size();dyes+=run.emeraldDyes();
            incomplete|=!run.hasShardData()||BazaarPrices.rareDropPricingIncomplete(run.rareDrops);
        }
        List<Line> lines=new ArrayList<>();
        lines.add(new Line("Runs  "+runs.size()+"  │  Run Time  "+ProgressHud.formatDuration(time),StatColours.TIME));
        lines.add(new Line("Your Catches  "+catches+"  │  Shards Received  "+shards,StatColours.UNIQUE));
        lines.add(new Line("Safari Essence  "+essence+"  │  Rainbow Feathers  "+feathers,StatColours.COINS));
        lines.add(new Line("Party Sparklings  "+sparklings+"  │  Emerald Dyes  "+dyes,StatColours.PERSONAL_BEST));
        String money=BazaarPrices.known()?BazaarPrices.format(value)+(incomplete?" + unpriced loot":""):"Prices unavailable";
        lines.add(new Line("Estimated Profit  "+money,StatColours.COINS));
        lines.add(new Line("Per Run  "+(BazaarPrices.known()&&!runs.isEmpty()?BazaarPrices.format(value/runs.size()):"—")
            +"  │  Per Hour  "+(BazaarPrices.known()&&time>0?BazaarPrices.format(Math.round(value*3600000d/time)):"—"),StatColours.COINS));
        lines.add(new Line("Pricing: "+ConfigManager.get().profit.priceSource().name().replace('_',' ')
            +"  │  "+(BazaarPrices.known()?"Updated "+Math.max(0,BazaarPrices.ageMillis()/60000)+"m ago":"Waiting for prices"),StatColours.TIME));
        return cached=new Snapshot(List.copyOf(lines));
    }
}
