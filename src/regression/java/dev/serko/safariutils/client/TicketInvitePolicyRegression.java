package dev.serko.safariutils.client;

import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.ArrayList;

public final class TicketInvitePolicyRegression {
	private record Player(String name, boolean sparkling, boolean qualifies) { }
	public static int run() {
		int checks = 0;
		for (boolean sparkling : List.of(false, true))
			for (boolean waiting : List.of(false, true))
				for (boolean more : List.of(false, true)) {
					boolean actual = TicketInvitePolicy.earlyWarpReady(sparkling,
						Set.of("a", "b"), Set.of("a"), waiting ? Set.of() : Set.of("b"), more);
					if (actual != (sparkling && !waiting && !more))
						throw new AssertionError("Early warp must await guests and the eligible queue");
					checks++;
				}
		if (TicketInvitePolicy.earlyWarpReady(true, Set.of("a"), Set.of(), Set.of("a"), false))
			throw new AssertionError("Do not warp an empty guest party");
		checks++;
		Random random = new Random(0x534146415249L);
		for (int sample = 0; sample < 2000; sample++) {
			List<Player> queue = new ArrayList<>();
			Set<String> baseline = new java.util.HashSet<>(), invited = new java.util.HashSet<>();
			for (int i = 0, count = random.nextInt(40); i < count; i++) {
				String name = "p" + i;
				Player player = new Player(name, random.nextBoolean(), random.nextBoolean());
				queue.add(player);
				if (random.nextInt(4) == 0) queue.add(player);
				if (random.nextInt(8) == 0) baseline.add(name);
				if (random.nextInt(6) == 0) invited.add(name);
			}
			int partySize = 1 + random.nextInt(4), joined = random.nextInt(4);
			int capacity = TicketInvitePolicy.capacity(partySize, joined);
			boolean early = random.nextBoolean();
			var actual = TicketInvitePolicy.select(queue, capacity, baseline, invited, early,
				Player::name, Player::sparkling, Player::qualifies);
			var expected = queue.stream().distinct()
				.filter(p -> !baseline.contains(p.name) && !invited.contains(p.name))
				.filter(p -> (!early || p.sparkling) && (!p.sparkling || p.qualifies))
				.limit(Math.max(0, 4 - partySize - joined)).toList();
			if (!actual.equals(expected)) throw new AssertionError("Queue selection scenario " + sample);
			checks++;
		}
		return checks;
	}
}
