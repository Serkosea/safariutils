package dev.serko.safariutils.client;

import java.util.List;

public final class ScanPublicationRegression {
	public static int run() {
		var scan = new ScanPublication<List<String>>(List.of());
		check(scan.current().revision() == 0 && scan.current().value().isEmpty(), "initial empty publication");
		scan.publish(List.of("first"));
		var previous = scan.current();
		scan.publish(List.of("second"));
		check(scan.current().revision() == previous.revision() + 1, "successive scans do not need different timestamps");
		check(previous.value().equals(List.of("first")), "held publication remains stable");
		check(scan.current().value().equals(List.of("second")), "latest complete data published");
		try { scan.publish(null); throw new AssertionError("invalid publication accepted"); }
		catch (NullPointerException expected) { }
		check(scan.current().revision() == 2 && scan.current().value().equals(List.of("second")),
			"failed publication preserves revision and data together");
		scan.publish(List.of());
		check(scan.current().revision() == 3 && scan.current().value().isEmpty(), "world reset is a distinct empty publication");
		scan.publish(List.of());
		check(scan.current().revision() == 4, "empty scans are still completed observations");
		return 7;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
