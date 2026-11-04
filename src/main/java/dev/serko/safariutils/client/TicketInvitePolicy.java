package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pure ordered-wave selection shared by live trading and command-free simulations. */
final class TicketInvitePolicy {
	private TicketInvitePolicy() { }

    private static final java.util.regex.Pattern TRANSFER = java.util.regex.Pattern.compile(
        "^» (?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{1,16}) is traveling to .+ FOLLOW$");

    static String transferredPlayer(String line) {
        var match = TRANSFER.matcher(line);
        return match.matches() ? match.group(1) : null;
    }

    /** The final entry opportunity takes precedence over the extra settling buffer. */
    static long warpDueAt(long normalDue, long readySince, long transferAt, long deadline) {
        return Math.min(deadline - 250L, Math.max(normalDue,
            Math.max(readySince > 0 ? readySince + 2_000L : 0L,
                transferAt > 0 ? transferAt + 3_000L : 0L)));
    }

	static int capacity(int baselinePlayers, int joinedGuests) {
		return Math.max(0, 4 - Math.max(1, baselinePlayers) - Math.max(0, joinedGuests));
	}

	/** Early warp only after a sparkling wave settles without skipping eligible guests. */
	static boolean earlyWarpReady(boolean sparkling, Set<String> invited, Set<String> joined,
			Set<String> unavailable, boolean moreEligible) {
		return sparkling && !joined.isEmpty() && !moreEligible
			&& invited.stream().allMatch(name -> joined.contains(name) || unavailable.contains(name));
	}

	static <T> List<T> select(List<T> queue, int capacity, Set<String> baseline,
			Set<String> invited, boolean sparklingOnlyWave, Function<T, String> name,
			Predicate<T> sparklingOnly, Predicate<T> qualifies) {
		if (capacity <= 0 || queue.isEmpty()) return List.of();
		List<T> selected = new ArrayList<>(Math.min(capacity, queue.size()));
		Set<String> seen = new HashSet<>();
		for (T player : queue) {
			String key = name.apply(player);
			if (baseline.contains(key) || invited.contains(key) || !seen.add(key)) continue;
			boolean sparkling = sparklingOnly.test(player);
			if (sparklingOnlyWave && !sparkling || sparkling && !qualifies.test(player)) continue;
			selected.add(player);
			if (selected.size() == capacity) break;
		}
		return List.copyOf(selected);
	}
}
