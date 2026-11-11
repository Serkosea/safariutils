package dev.serko.safariutils.client;

import dev.serko.safariutils.session.*;
import java.time.*;
import java.util.*;
import java.util.function.ToLongFunction;

/** Daily historical projection without a second persistence store. */
final class SavedProfitSummary {
    record Day(LocalDate date,int runs,long millis,long value,long best,boolean incomplete) {
        long perHour() { return millis>0?Math.round(value*3600000d/millis):0; }
    }
    private long historyRevision=Long.MIN_VALUE,priceRevision=Long.MIN_VALUE;
    private ZoneId zone;
    private List<Day> days=List.of();
    List<Day> days() {
        ZoneId currentZone=ZoneId.systemDefault();
        long history=RunHistory.revision(),prices=BazaarPrices.revision();
        if(history!=historyRevision||prices!=priceRevision||!currentZone.equals(zone)) {
            days=group(RunHistory.runs(),currentZone,BazaarPrices::valueOf);
            zone=currentZone;historyRevision=history;priceRevision=prices;
        }
        return days;
    }
    static List<Day> group(List<RunRecord> runs,ZoneId zone,ToLongFunction<RunRecord> prices) {
        Map<LocalDate,Day> grouped=new TreeMap<>(Comparator.reverseOrder());
        for(var run:runs) {
            if(run.started<=0)continue;
            LocalDate date=Instant.ofEpochMilli(run.started).atZone(zone).toLocalDate();
            long value=prices.applyAsLong(run);
            Day previous=grouped.getOrDefault(date,new Day(date,0,0,0,0,false));
            grouped.put(date,new Day(date,previous.runs+1,previous.millis+run.durationMillis(),previous.value+value,
                Math.max(previous.best,value),previous.incomplete||!run.hasShardData()||BazaarPrices.rareDropPricingIncomplete(run.rareDrops)));
        }
        return List.copyOf(grouped.values());
    }
}
