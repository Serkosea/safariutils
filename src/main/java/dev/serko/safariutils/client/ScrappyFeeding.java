package dev.serko.safariutils.client;

import dev.serko.safariutils.state.MonotonicTime;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import java.util.*;

/** Initial-body food inference is presentation only, never capture ownership or party evidence. */
public final class ScrappyFeeding {
    private static final String[] FOODS = {"Wholesale Wheat", "Flavor-packed Fish", "Lush Lily Pad"};
    private static final Map<Integer, Boolean> observed = new HashMap<>();
    private static final Map<UUID, Progress> progress = new HashMap<>();
    private static final Deque<Click> pending = new ArrayDeque<>();
    private static Integer base;
    private static boolean invalid;
    private record Click(UUID target, int food, long time) { }
    static final class Progress {
        int food = -1, feeds;
        boolean awake;
        void confirm(int confirmedFood, boolean cautious, boolean paw, boolean fullyAwake) {
            food = confirmedFood;
            if (awake) return;
            feeds = fullyAwake ? 4 : paw ? 3 : cautious ? Math.max(feeds, Math.min(2, feeds + 1)) : feeds;
            awake = fullyAwake;
        }
    }
    private ScrappyFeeding() { }

    static void scan() {
        if (!SafariLocation.inSafari()) { reset(); return; }
        if (base != null || invalid) return;
        for (Entity entity : WorldEntities.current()) {
            if (observed.size() < 8192) observed.putIfAbsent(entity.getId(), entity instanceof Armadillo);
        }
        Set<Integer> possibilities = initialCandidates(observed);
        if (possibilities == null) return;
        if (possibilities.size() == 1) {
            base = possibilities.iterator().next();
            observed.clear();
            if (StructuredDiagnostics.enabled()) DebugLog.line("SCRAPPY_FOOD", "initial trio base=" + base);
        } else if (possibilities.isEmpty() || observed.size() >= 8192) {
            invalid = true; observed.clear();
            if (StructuredDiagnostics.enabled()) DebugLog.line("SCRAPPY_FOOD", "inference unavailable; using confirmed feeding only");
        }
    }

    /** Null means no bodies observed, empty means positive observations contradict the pattern. */
    static Set<Integer> initialCandidates(Map<Integer, Boolean> observed) {
        Set<Integer> possibilities = null;
        for (var entry : observed.entrySet()) {
            if (!entry.getValue()) continue;
            int id = entry.getKey();
            Set<Integer> starts = new HashSet<>(List.of(id, id - 3, id - 6));
            if (possibilities == null) possibilities = starts; else possibilities.retainAll(starts);
        }
        if (possibilities != null) possibilities.removeIf(start -> Boolean.FALSE.equals(observed.get(start))
            || Boolean.FALSE.equals(observed.get(start + 3)) || Boolean.FALSE.equals(observed.get(start + 6)));
        return possibilities;
    }

    public static void interact(Entity target, InteractionHand hand, String heldName) {
        if (!SafariLocation.inside() || hand != InteractionHand.MAIN_HAND) return;
        int food = food(heldName);
        if (food < 0) return;
        var sighting = CritterEntities.sightingFor(target.getUUID());
        Entity body = target instanceof Armadillo ? target : sighting != null && "Scrappy".equals(sighting.critter().name()) ? sighting.mob() : null;
        if (!(body instanceof Armadillo)) return;
        expire();
        if (pending.size() >= 32) pending.removeFirst();
        pending.addLast(new Click(body.getUUID(), food, MonotonicTime.nowMillis()));
    }

    private static void expire() {
        long now = MonotonicTime.nowMillis();
        pending.removeIf(click -> now - click.time() > 3000);
    }

    public static void onChatMessage(String line) {
        if (!SafariLocation.inside() || !(line.startsWith("The Scrappy ") || line.startsWith("This particular Scrappy "))) return;
        boolean cautious = line.contains("cautiously started eating");
        boolean paw = line.contains("reaches out its paw");
        boolean awake = line.contains("came out of its shell") || line.contains("happily eats");
        boolean rejected = line.contains("doesn't seem to care for this type of food");
        if (!cautious && !paw && !awake && !rejected) return;
        expire();
        int food = food(line);
        Set<UUID> targets = new HashSet<>();
        for (Click click : pending) if (food < 0 || click.food() == food) targets.add(click.target());
        if (targets.size() != 1) {
            if (StructuredDiagnostics.enabled()) DebugLog.line("SCRAPPY_FOOD", "unattributed reply candidates=" + targets.size());
            pending.clear(); return;
        }
        UUID target = targets.iterator().next();
        // Consume one matching request, including rejected feeds. Repeated valid text is not a duplicate.
        var iterator = pending.iterator();
        while (iterator.hasNext()) { Click click = iterator.next(); if (click.target().equals(target) && (food < 0 || click.food() == food)) { iterator.remove(); break; } }
        if (rejected || food < 0) return;
        if (progress.size() >= 64 && !progress.containsKey(target)) return;
        Progress state = progress.computeIfAbsent(target, ignored -> new Progress());
        Entity body = WorldEntities.find(target);
        int predicted = predicted(body);
        if (predicted >= 0 && predicted != food) {
            invalid = true;
            if (StructuredDiagnostics.enabled()) DebugLog.line("SCRAPPY_FOOD", "food mismatch; inference disabled for visit");
        }
        state.confirm(food, cautious, paw, awake);
        if (StructuredDiagnostics.enabled()) DebugLog.line("SCRAPPY_FOOD", "target=" + target + " food=" + FOODS[food] + " confirmed=" + state.feeds + " awake=" + state.awake);
    }

    private static int food(String text) {
        for (int index = 0; index < FOODS.length; index++) if (text.contains(FOODS[index])) return index;
        return -1;
    }
    private static int predicted(Entity body) {
        if (body == null || base == null || invalid) return -1;
        int offset = body.getId() - base;
        return offset >= 0 && offset <= 6 && offset % 3 == 0 ? offset / 3 : -1;
    }
    record Presentation(String title, int colour) { }
    static Presentation presentation(Entity body) {
        if (!(body instanceof Armadillo armadillo)) return null;
        Progress state = progress.get(body.getUUID());
        if (state != null && state.awake) return null;
        // The visible rolled-up state confirms it still needs food. Moving/awake bodies keep normal titles.
        if (!armadillo.isScared()) return null;
        int food = state != null && state.food >= 0 ? state.food : SafeMode.scrappyFood() ? -1 : predicted(body);
        if (food < 0) return null;
        boolean solo = SafariPartyWatch.readyForLocationLearning() && SafariPartyWatch.joinedPlayers() == 1
            && dev.serko.safariutils.session.SessionManager.expectedRunPlayers() == 1;
        String title = FOODS[food] + " (" + (state == null ? solo ? "0" : "?" : Integer.toString(state.feeds)) + "/4)";
        int colour = switch (food) { case 0 -> 0xFFF0D943; case 1 -> 0xFF41CCE8; case 2 -> 0xFF34C421; default -> 0; };
        return new Presentation(title, colour);
    }
    static void reset() { observed.clear(); progress.clear(); pending.clear(); base = null; invalid = false; }
}
