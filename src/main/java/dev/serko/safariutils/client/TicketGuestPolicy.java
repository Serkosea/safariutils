package dev.serko.safariutils.client;

/** Permission is rechecked at each command boundary, not only when an invite was received. */
final class TicketGuestPolicy {
	enum Action { IGNORE, ACCEPT, LEAVE_THEN_ACCEPT }
	private TicketGuestPolicy() { }

	static Action invite(boolean enabled, boolean trusted, boolean blocked, boolean duplicate,
			boolean switchParty, boolean inParty) {
		if (!enabled || !trusted || blocked || duplicate) return Action.IGNORE;
		return switchParty && inParty ? Action.LEAVE_THEN_ACCEPT : Action.ACCEPT;
	}

	static boolean pending(boolean enabled, boolean trusted, boolean blocked) {
		return enabled && trusted && !blocked;
	}

	static boolean fallback(boolean enabled, boolean trustedSwitch, boolean blocked,
			long age, long window) {
		return pending(enabled, trustedSwitch, blocked) && age >= 0 && age <= window;
	}
}
