package dev.serko.safariutils.state;

import dev.serko.safariutils.data.SafariBiome;
import java.util.*;

/** Shared hotspot allocation: fewest retained hotspots, UUID hash tie-break, shared report window. */
public final class HotspotAllocation {
    private final Map<String, SafariBiome> reports = new LinkedHashMap<>();
    private final Map<String, Integer> wins;
    private String self;
    private SafariBiome actual, finished;
    private Map<String, SafariBiome> finalized = Map.of();
    public Map<String, SafariBiome> finalizedAssignments() { return finalized; }
    private long started;
    private Long firstReport;
    private long lateAt;
    public HotspotAllocation() { this(new HashMap<>()); }
    /** The owner retains wins across runs, clearing them when the roster changes. */
    public HotspotAllocation(Map<String, Integer> wins) { this.wins = wins; }
    public void begin(String name, SafariBiome hotspot, long now) {
        if (self != null) return;
        self = name.toLowerCase(Locale.ROOT); actual = hotspot; started = now;
    }
    public void report(String name, SafariBiome biome, long now) {
        if (name == null || biome == null) return;
        String key = name.toLowerCase(Locale.ROOT);
        if (reports.size() >= 4 && !reports.containsKey(key)) return;
        if (firstReport == null) firstReport = now;
        reports.putIfAbsent(key, biome);
        if(finished!=null && !finalized.containsKey(key) && lateAt==0)lateAt=now+250;
    }
    public SafariBiome assignment(Set<String> members, Map<String, String> uuids, int expected, long now) {
        if (self == null) return null;
        if (finished != null) {
            if(lateAt!=0 && now>=lateAt) {
                Map<String,SafariBiome> fixed=new LinkedHashMap<>(finalized);
                Set<String> attendees=new HashSet<>();members.forEach(n->attendees.add(n.toLowerCase(Locale.ROOT)));
                List<String> late=reports.keySet().stream().filter(n->attendees.contains(n) && !fixed.containsKey(n)).sorted(
                    Comparator.comparingInt((String n)->wins.getOrDefault(n,0)).thenComparingInt(n->identity(n,uuids).hashCode()).thenComparing(n->identity(n,uuids))).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                for(SafariBiome biome:SafariBiome.values())if(!fixed.containsValue(biome)) {
                    String winner=late.stream().filter(n->reports.get(n)==biome).findFirst().orElse(null);
                    if(winner!=null) {fixed.put(winner,biome);late.remove(winner);wins.merge(winner,1,Integer::sum);}
                }
                late.sort(Comparator.comparingInt((String n)->identity(n,uuids).hashCode()).thenComparing(n->identity(n,uuids)));
                for(SafariBiome biome:SafariBiome.values())if(!fixed.containsValue(biome) && !late.isEmpty())fixed.put(late.removeFirst(),biome);
                finalized=Map.copyOf(fixed);lateAt=0;
            }
            return finished;
        }
        if (expected <= 1) { finalized = Map.of(self, actual); return finished = actual; }
        if (firstReport == null) return null;
        Set<String> names = new HashSet<>();
        for (String name : members) names.add(name.toLowerCase(Locale.ROOT));
        names.add(self);
        boolean complete = names.size() >= expected && reports.keySet().containsAll(names);
        long elapsed = now - firstReport;
        if (!complete && elapsed < 30000) return null;
        Map<String, SafariBiome> known = new LinkedHashMap<>();
        reports.forEach((name, biome) -> { if (names.contains(name)) known.put(name, biome); });
        known.putIfAbsent(self, actual);
        Map<String, SafariBiome> assignments = allocate(known, uuids, wins);
        finalized = assignments;
        assignments.forEach((name, biome) -> {
            if (biome == known.get(name)) wins.merge(name, 1, Integer::sum);
        });
        return finished = assignments.getOrDefault(self, actual);
    }
    public boolean finished() { return finished != null; }
    public static Map<String, SafariBiome> allocate(Map<String, SafariBiome> reports, Map<String, String> uuids) {
        return allocate(reports, uuids, Map.of());
    }
    public static Map<String, SafariBiome> allocate(Map<String, SafariBiome> reports,
            Map<String, String> uuids, Map<String, Integer> wins) {
        Map<String, SafariBiome> result = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        Comparator<String> identityOrder = Comparator.comparingInt((String name) -> identity(name, uuids).hashCode())
            .thenComparing(name -> identity(name, uuids));
        Comparator<String> priority = Comparator.comparingInt((String name) -> wins.getOrDefault(name, 0))
            .thenComparing(identityOrder);
        for (SafariBiome biome : SafariBiome.values()) {
            List<String> holders = new ArrayList<>();
            reports.forEach((name, reported) -> { if (reported == biome) holders.add(name); });
            holders.sort(priority);
            if (!holders.isEmpty()) {
                result.put(holders.getFirst(), biome);
                duplicates.addAll(holders.subList(1, holders.size()));
            }
        }
        duplicates.sort(identityOrder);
        int next = 0;
        for (SafariBiome biome : SafariBiome.values()) {
            if (!result.containsValue(biome) && next < duplicates.size()) result.put(duplicates.get(next++), biome);
        }
        return Map.copyOf(result);
    }
    private static String identity(String name, Map<String, String> uuids) {
        String key = name.toLowerCase(Locale.ROOT);
        String uuid = uuids.get(key);
        return uuid == null || uuid.isBlank() ? key : uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }
}
