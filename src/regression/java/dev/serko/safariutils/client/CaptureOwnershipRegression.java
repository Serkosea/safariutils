package dev.serko.safariutils.client;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class CaptureOwnershipRegression {
	public static int run() {
		var ownership = new CaptureOwnership();
		UUID first = new UUID(0, 1), second = new UUID(0, 2), peer = new UUID(0, 3);
		int checks = 0;
		checks += check(ownership.claim(1, first, false).accepted(), "initial claim");
		ownership.claim(1, second, false);
		checks += check(ownership.bodies(1).equals(Set.of(first, second)), "rekey keeps aliases");
		ownership.claim(2, peer, false);
		checks += check(!ownership.claim(1, peer, true).accepted(), "older correction cannot steal peer");
		checks += check(ownership.bodies(1).equals(Set.of(first, second)), "rejected correction preserves aliases");
		checks += check(ownership.claim(3, second, false).displaced() == 1L, "new attempt supersedes old owner");
		checks += check(!ownership.containsKey(first) && ownership.bodies(1).isEmpty(), "supersession releases all old aliases");
		ownership.release(1);
		checks += check(ownership.owner(second) == 3L, "late expiry cannot release newer owner");
		checks += check(!ownership.claim(1, second, false).accepted(), "late rekey cannot reclaim newer body");
		ownership.claim(3, first, true);
		checks += check(!ownership.containsKey(second) && ownership.bodies(3).equals(Set.of(first)), "correction replaces aliases atomically");
		checks += check(ownership.claim(3, first, false).displaced() == null, "duplicate claim is idempotent");
		ownership.clear();
		checks += check(ownership.entries().isEmpty() && ownership.bodies(3).isEmpty(), "visit reset clears both indices");
		checks += check(ownership.claim(1, first, false).accepted(), "new visit may restart attempt IDs");
		Random random = new Random(0xCA970);
		for (int step = 0; step < 1_000; step++) {
			long attempt = 1 + random.nextInt(30);
			UUID body = new UUID(0, random.nextInt(12));
			if (random.nextInt(4) == 0) ownership.release(attempt);
			else ownership.claim(attempt, body, random.nextBoolean());
			for (var entry : ownership.entries().entrySet()) {
				if (!ownership.bodies(entry.getValue()).contains(entry.getKey())) {
					throw new AssertionError("body index has no inverse owner");
				}
			}
			for (long owner = 1; owner <= 30; owner++) {
				for (UUID alias : ownership.bodies(owner)) {
					if (!Long.valueOf(owner).equals(ownership.owner(alias))) {
						throw new AssertionError("alias index disagrees with body owner");
					}
				}
			}
			checks++;
		}
		return checks;
	}

	private static int check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
		return 1;
	}
}
