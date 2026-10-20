package dev.serko.safariutils.client;

/** Checks the live coordinator's shared gap without sending commands or starting a client. */
public final class CommandSpacingRegression {
	public static int run() {
		int checks = 0;
		CommandCoordinator.resetConnection();
		for (var category : CommandCoordinator.Cooldown.values()) {
			if (CommandCoordinator.remaining(category) != 0) throw new AssertionError("reset cooldown");
			CommandCoordinator.observeOutboundCommand();
			if (CommandCoordinator.remaining(category) <= 0) throw new AssertionError("shared command gap: " + category);
			CommandCoordinator.resetConnection();
			checks += 2;
		}
		return checks;
	}
}
