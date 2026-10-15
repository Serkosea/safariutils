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

	static int capacity(int baselinePlayers, int joinedGuests) {
		return Math.max(0, 4 - Math.max(1, baselinePlayers) - Math.max(0, joinedGuests));
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
