package dev.serko.safariutils.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MarkerOwnershipRegression {
	public static int run() {
		UUID first = UUID.fromString("12345678-0000-0000-0000-000000000001");
		UUID second = UUID.fromString("12345678-0000-0000-0000-000000000002");
		var bodies = new HashSet<>(Set.of(first));
		var original = new MarkerRegistry.DiagnosticOwnership(1, 2, 3, Set.of(1L), bodies,
			Set.of(), Set.of(), Set.of());
		bodies.clear();
		check(original.pins().contains(first), "diagnostic snapshot isolates mutable inputs");
		check(!original.equals(new MarkerRegistry.DiagnosticOwnership(1, 2, 3, Set.of(1L), Set.of(second),
			Set.of(), Set.of(), Set.of())), "same shortened UUID cannot hide ownership change");
		check(!original.equals(new MarkerRegistry.DiagnosticOwnership(1, 4, 3, Set.of(1L), Set.of(first),
			Set.of(), Set.of(), Set.of())), "new visit is a new diagnostic state");
		check(original.equals(new MarkerRegistry.DiagnosticOwnership(1, 2, 3, Set.of(1L), Set.of(first),
			Set.of(), Set.of(), Set.of())), "unchanged ownership suppresses repeated formatting");
		check(!original.equals(new MarkerRegistry.DiagnosticOwnership(1, 2, 3, Set.of(2L), Set.of(first),
			Set.of(), Set.of(), Set.of())), "same body with new attempt is a transition");
		return 5;
	}

	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
