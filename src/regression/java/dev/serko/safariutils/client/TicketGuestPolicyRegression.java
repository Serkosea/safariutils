package dev.serko.safariutils.client;

public final class TicketGuestPolicyRegression {
	public static int run() {
		int checks = 0;
		for (int bits = 0; bits < 64; bits++) {
			boolean enabled = (bits & 1) != 0, trusted = (bits & 2) != 0;
			boolean blocked = (bits & 4) != 0, duplicate = (bits & 8) != 0;
			boolean switching = (bits & 16) != 0, inParty = (bits & 32) != 0;
			var actual = TicketGuestPolicy.invite(enabled, trusted, blocked, duplicate, switching, inParty);
			String expected = !enabled || !trusted || blocked || duplicate ? "IGNORE"
				: switching && inParty ? "LEAVE_THEN_ACCEPT" : "ACCEPT";
			if (!actual.name().equals(expected)) throw new AssertionError("Invite state " + bits);
			if (TicketGuestPolicy.pending(enabled, trusted, blocked) != (enabled && trusted && !blocked))
				throw new AssertionError("Pending state " + bits);
			checks += 2;
			for (long age : new long[]{-1, 0, 2999, 3000, 3001}) {
				boolean allowed = enabled && trusted && switching && !blocked && age >= 0 && age <= 3000;
				if (TicketGuestPolicy.fallback(enabled, trusted && switching, blocked, age, 3000) != allowed)
					throw new AssertionError("Fallback state " + bits + " age=" + age);
				checks++;
			}
		}
		return checks;
	}
}
