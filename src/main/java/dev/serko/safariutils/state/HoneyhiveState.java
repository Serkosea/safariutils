package dev.serko.safariutils.state;

import java.util.*;
import java.util.regex.Pattern;

/** Bounded client-session evidence; never confuses a click with a successful harvest. */
public final class HoneyhiveState {
    public enum Status { UNKNOWN, READY, COOLDOWN }
    private static final long HOUR=3_600_000, CLICK_WINDOW=5_000, SETTLE=2_000;
    private static final Pattern COUNTDOWN=Pattern.compile("(?i)^Refill in: (?:(\\d+)m\\s*)?(?:(\\d+)s)?$");
    private record Click(String key,long at) { }
    private final Map<String,Long> refills=new HashMap<>(), confirmed=new HashMap<>();
    private final Map<String,Long> collected=new HashMap<>();
    private final Set<String> ready=new HashSet<>();
    private final Deque<Click> clicks=new ArrayDeque<>();
    private Click lastLoot, queenBeforeLoot;
    public record Saved(long collectedAt,long refillAt) { }
    public Map<String,Saved> snapshot(long monotonicNow,long wallNow) {
        Map<String,Saved> result=new HashMap<>();
        Set<String> keys=new HashSet<>(refills.keySet()); keys.addAll(ready);
        for(String key:keys) {
            long last=collected.containsKey(key)?wallNow-(monotonicNow-collected.get(key)):0;
            long refill=refills.containsKey(key)?wallNow+(refills.get(key)-monotonicNow):0;
            result.put(key,new Saved(Math.max(0,last),Math.max(0,refill)));
        }
        return Map.copyOf(result);
    }
    public void restore(Map<String,Saved> saved,long monotonicNow,long wallNow) {
        restore(saved,monotonicNow,wallNow,false);
    }
    /** Initial profile discovery must not discard an in-flight harvest or its settle guard. */
    public void restore(Map<String,Saved> saved,long monotonicNow,long wallNow,boolean keepInteractions) {
        if(keepInteractions) { refills.clear(); ready.clear(); collected.clear(); } else clear();
        for(var entry:saved.entrySet()) {
            Saved value=entry.getValue();
            if(value.collectedAt<0 || value.refillAt<0 || value.collectedAt>wallNow+60_000 || value.refillAt>wallNow+HOUR+60_000) continue;
            if(value.collectedAt>0) collected.put(entry.getKey(),monotonicNow-(wallNow-value.collectedAt));
            if(value.refillAt>wallNow) refills.put(entry.getKey(),monotonicNow+(value.refillAt-wallNow));
            else ready.add(entry.getKey());
        }
    }
    public Status status(String key,long now) {
        Long deadline=refills.get(key);
        return deadline!=null ? (now<deadline?Status.COOLDOWN:Status.READY) : ready.contains(key)?Status.READY:Status.UNKNOWN;
    }
    public void resetInteractions() { clicks.clear(); lastLoot=null; queenBeforeLoot=null; }
    public void clear() { resetInteractions(); refills.clear(); confirmed.clear(); ready.clear(); collected.clear(); }
    public void click(String key,long now) {
        expire(now);
        if(status(key,now)==Status.COOLDOWN || clicks.stream().anyMatch(c->c.key.equals(key))) return;
        if(clicks.size()==8) clicks.removeFirst();
        clicks.addLast(new Click(key,now));
    }
    public String chat(String text,long now) {
        expire(now);
        if(text.startsWith("HIVE!")) {
            if(queenBeforeLoot!=null) {
                String key=queenBeforeLoot.key; queenBeforeLoot=null; confirmed.put(key,now); collected.put(key,now); return key;
            }
            Click click=clicks.pollFirst(); if(click==null) return null;
            collected.put(click.key,now); lastLoot=new Click(click.key,now); refills.put(click.key,now+HOUR); ready.remove(click.key);
            confirmed.put(click.key,now); return click.key;
        }
        if(text.startsWith("QUEEN BEE!")) {
            Click target=lastLoot;
            if(clicks.size()==1) { target=clicks.removeFirst(); queenBeforeLoot=new Click(target.key,now); }
            if(target==null) return null;
            refills.remove(target.key); ready.add(target.key); confirmed.put(target.key,now); lastLoot=null;
            return target.key;
        }
        return null;
    }
    public boolean observe(String key,String text,long now) {
        if(now-confirmed.getOrDefault(key,Long.MIN_VALUE/2)<SETTLE) return false;
        Long delay=countdown(text);
        if(delay!=null) {
            long deadline=now+Math.max(1000,delay);
            Long old=refills.get(key);
            if(old!=null && old>now && Math.abs(old-deadline)<=1500) return false;
            refills.put(key,deadline); ready.remove(key); return true;
        }
        if(text.equalsIgnoreCase("Click to loot!")) {
            boolean changed=refills.remove(key)!=null; changed|=ready.add(key); return changed;
        }
        return false;
    }
    public static Long countdown(String text) {
        var match=COUNTDOWN.matcher(text.trim());
        if(!match.matches() || match.group(1)==null&&match.group(2)==null) return null;
        try {
            long minutes=match.group(1)==null?0:Long.parseLong(match.group(1));
            long seconds=match.group(2)==null?0:Long.parseLong(match.group(2));
            if(minutes>60 || seconds>60) return null;
            long value=minutes*60_000+seconds*1000; return value<=HOUR?value:null;
        } catch(NumberFormatException ignored) { return null; }
    }
    private void expire(long now) {
        clicks.removeIf(c->now<c.at||now-c.at>CLICK_WINDOW);
        if(lastLoot!=null&&(now<lastLoot.at||now-lastLoot.at>CLICK_WINDOW)) lastLoot=null;
        if(queenBeforeLoot!=null&&(now<queenBeforeLoot.at||now-queenBeforeLoot.at>CLICK_WINDOW)) queenBeforeLoot=null;
    }
}
