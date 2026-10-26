package dev.serko.safariutils.session;

import dev.serko.safariutils.data.*;
import java.util.*;

/** Confirmed unique-clear and personal biome-mob milestones, timed from ticket use. */
public final class PersonalBestTracker {
    public enum Group {
        SOLO("Solo"), DUO("Duo"), TRIO("Trio"), SQUAD("Squad");
        public final String label;
        Group(String label) { this.label = label; }
    }
    public record Split(int rules, String target, long elapsedMillis, List<String> contributors,
            boolean timingKnown, long sinceEntryMillis, long previousBestMillis) {
        public Split { contributors = contributors == null ? List.of() : List.copyOf(contributors); }
        public Group group() { return contributors.size() >= 1 && contributors.size() <= 4
            ? Group.values()[contributors.size()-1] : null; }
    }
    // Version one grouped by attendance and Personal/Team; those records cannot be
    // reclassified without first-catch attribution, so they never compete with v2.
    public static final int RULES = 2;
    public static final String ALL = "ALL";
    private static final Map<String,String> MOB_SPECIES = Map.of(
        "MOB_GEMZIE", "Gemzie", "MOB_DOOMSPIRAL", "Doomspiral", "MOB_WUMPA", "Wumpa", "MOB_MACAW", "Macaw");
    public static boolean isMob(String target) { return target != null && MOB_SPECIES.containsKey(target); }
    public static String mobTarget(SafariBiome biome) {
        return switch (biome) {
            case CAVERN -> "MOB_GEMZIE";
            case HAUNTED -> "MOB_DOOMSPIRAL";
            case ICY -> "MOB_WUMPA";
            case FOREST -> "MOB_MACAW";
        };
    }
    public static SafariBiome biome(String target) {
        return ALL.equals(target) ? null : isMob(target) ? Critters.byName(MOB_SPECIES.get(target)).biome() : SafariBiome.valueOf(target);
    }
    private final Map<String,String> firstCatchers = new HashMap<>();
    private final EnumMap<SafariBiome, Long> entries = new EnumMap<>(SafariBiome.class);
    private final List<Split> splits = new ArrayList<>();
    private final Set<String> completed = new HashSet<>();
    private final boolean timingKnown;

    private final String self;
    public PersonalBestTracker(boolean timingKnown, String self) {
        this.timingKnown = timingKnown;
        this.self = self == null ? "" : self.toLowerCase(Locale.ROOT);
    }
    public void enter(SafariBiome biome, long elapsed) {
        if (biome != null) entries.putIfAbsent(biome, Math.max(0, elapsed));
    }
    public List<Split> catchSpecies(Critter critter, String catcher, long elapsed) {
        if (critter == null || elapsed < 0 || catcher == null || !catcher.matches("[A-Za-z0-9_]{1,16}")) return List.of();
        List<Split> added = new ArrayList<>(3);
        String mob = mobTarget(critter.biome());
        // Own first catch is independent of whoever supplied the party's first unique.
        if (self.equalsIgnoreCase(catcher) && critter.name().equals(MOB_SPECIES.get(mob)) && completed.add(mob)) {
            Split split = new Split(RULES, mob, elapsed, List.of(self), timingKnown, -1, -1);
            splits.add(split); added.add(split);
        }
        if (firstCatchers.putIfAbsent(critter.name(),catcher.toLowerCase(Locale.ROOT)) != null) return List.copyOf(added);
        if (Critters.inBiome(critter.biome()).stream().allMatch(c -> firstCatchers.containsKey(c.name())))
            complete(critter.biome().name(), elapsed, added);
        if (Critters.all().stream().allMatch(c -> firstCatchers.containsKey(c.name()))) complete(ALL, elapsed, added);
        return List.copyOf(added);
    }
    private void complete(String target, long elapsed, List<Split> added) {
        if (!completed.add(target)) return;
        var species = ALL.equals(target) ? Critters.all() : Critters.inBiome(SafariBiome.valueOf(target));
        List<String> contributors = species.stream().map(c -> firstCatchers.get(c.name())).distinct().sorted().toList();
        if (!contributors.contains(self)) return;
        Long entry = ALL.equals(target) ? null : entries.get(SafariBiome.valueOf(target));
        Split split = new Split(RULES, target, elapsed, contributors, timingKnown, entry == null ? -1 : elapsed-entry, -1);
        splits.add(split); added.add(split);
    }
    public void compared(Split split, long previous) {
        int index = splits.indexOf(split);
        if (index >= 0) splits.set(index, new Split(split.rules,split.target,split.elapsedMillis,
            split.contributors,split.timingKnown,split.sinceEntryMillis,previous));
    }
    public List<Split> splits() { return List.copyOf(splits); }
    public static boolean valid(Split split) {
        return split != null && split.rules == RULES && split.group() != null
            && new HashSet<>(split.contributors).size() == split.contributors.size()
            && split.contributors.stream().allMatch(name -> name != null && name.matches("[a-z0-9_]{1,16}"))
            && split.elapsedMillis >= 0 && split.sinceEntryMillis >= -1 && split.sinceEntryMillis <= split.elapsedMillis && split.previousBestMillis >= -1
            && (!isMob(split.target) || split.group() == Group.SOLO)
            && (isMob(split.target) || ALL.equals(split.target) || Arrays.stream(SafariBiome.values()).anyMatch(b -> b.name().equals(split.target)));
    }
    public static boolean includesPlayer(Split split, String player) {
        return split != null && player != null && split.contributors.stream().anyMatch(player::equalsIgnoreCase);
    }
    public static boolean eligible(Split split) { return valid(split) && split.timingKnown; }
    public static String title(String target) { return ALL.equals(target) ? "All Uniques" : isMob(target) ? MOB_SPECIES.get(target) : SafariBiome.valueOf(target).displayName(); }
    public static String time(long millis) { long hundredths = Math.max(0,millis)/10; return String.format(Locale.ROOT,"%d:%02d.%02d",hundredths/6000,(hundredths/100)%60,hundredths%100); }
    public static String comparison(long elapsed,long previous,boolean eligible) {
        if(!eligible) return "Timing Error";
        if(previous<0) return "First Clear!";
        if(elapsed<previous) return "PB! -" + time(previous-elapsed);
        if(elapsed==previous) return "Tied PB?!";
        return "+"+time(elapsed-previous);
    }
    public static int comparisonColour(long elapsed,long previous,boolean eligible) {
        if(!eligible) return 0xB5BBC7;
        if(previous<0 || elapsed<previous) return 0x55FF88;
        if(elapsed==previous) return 0x55FFFF;
        double slower = (elapsed-previous)/(double)Math.max(1,previous);
        return slower < .05 ? 0xFFD45A : slower < .15 ? 0xFFAA33 : slower < .30 ? 0xFF6666 : 0xC43C45;
    }
}
