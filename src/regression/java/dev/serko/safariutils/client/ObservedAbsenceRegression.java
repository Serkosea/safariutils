package dev.serko.safariutils.client;

import java.util.Set;

public final class ObservedAbsenceRegression {
	private static int checks;
	public static int run() {
		checks = 0;
		var evidence = new ObservedAbsence<String>(1500, 1000);
		check(!evidence.observe("a", 0), "first empty observation is insufficient");
		check(!evidence.observe("a", 750), "partial window");
		check(!evidence.observe("b", 1499), "peer starts independent window");
		check(evidence.observe("a", 1500), "continuous observations meet exact boundary");
		check(!evidence.observe("b", 1500), "other critter does not inherit elapsed evidence");
		evidence.remove("a");
		check(!evidence.observe("a", 1501), "visibility or capture resets evidence");
		check(!evidence.observe("a", 5000), "long pause restarts evidence");
		check(!evidence.observe("a", 5000), "duplicate observation adds no elapsed time");
		check(!evidence.observe("a", 6000), "maximum allowed gap retains incomplete evidence");
		check(evidence.observe("a", 6500), "resumed continuous sequence completes");
		check(!evidence.observe("a", 6400), "backward clock resets evidence");
		evidence.retain(Set.of("b"));
		check(!evidence.observe("a", 7000), "retired identity cannot retain timer");
		evidence.clear();
		check(!evidence.observe("b", 7000), "visit reset clears evidence");
		var rollover = new ObservedAbsence<String>(100, 100);
		check(!rollover.observe("a", Long.MAX_VALUE - 49), "negative nanoTime origin supported");
		check(rollover.observe("a", Long.MIN_VALUE + 50), "nanoTime rollover keeps elapsed duration");
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
		checks++;
	}
}
