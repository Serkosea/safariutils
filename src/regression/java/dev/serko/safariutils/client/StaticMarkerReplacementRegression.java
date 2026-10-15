package dev.serko.safariutils.client;

public final class StaticMarkerReplacementRegression {
	public static int run() {
		int checks = 0;
		var empty = new StaticMarkerReplacement<String>();
		check(empty.replacement() == null, "no spatial evidence"); checks++;
		empty.consider("old", false);
		check("old".equals(empty.replacement()), "one absent unowned candidate"); checks++;
		for (boolean firstProtected : new boolean[] {false, true}) {
			for (boolean secondProtected : new boolean[] {false, true}) {
				for (boolean reverse : new boolean[] {false, true}) {
					var selection = new StaticMarkerReplacement<String>();
					selection.consider(reverse ? "second" : "first", firstProtected);
					selection.consider(reverse ? "first" : "second", secondProtected);
					check(selection.replacement() == null, "cluster cannot select by iteration order"); checks++;
				}
			}
		}
		var owned = new StaticMarkerReplacement<String>();
		owned.consider("capturing-or-live", true);
		check(owned.replacement() == null, "even unique active owner cannot be replaced"); checks++;
		empty.consider("peer", false);
		check(empty.replacement() == null, "later evidence invalidates provisional uniqueness"); checks++;
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
