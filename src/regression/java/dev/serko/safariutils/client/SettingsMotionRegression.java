package dev.serko.safariutils.client;

public final class SettingsMotionRegression {
	public static int run() {
		SettingsMotion motion = new SettingsMotion();
		motion.begin(0, 1, false);
		check(motion.value(1, 0) == 0, "initial state must snap");
		check(motion.value(1, 1) == 0, "changed state starts at current value");
		motion.begin(46_666_667, 1, false);
		float half = motion.value(1, 1);
		check(Math.abs(half - 0.5f) < 0.001f, "time-based midpoint");
		check(motion.value(1, 0) == half, "reverse without a jump");
		motion.begin(140_000_000, 1, false);
		check(motion.value(1, 0) == 0, "settles exactly");
		motion.begin(220_000_000, 1, true);
		check(motion.value(1, 1) == 1, "reduced motion snaps");
		motion.begin(230_000_000, 2, false);
		check(motion.retained() == 0 && motion.value(1, 0) == 0, "layout changes cannot animate unrelated controls");
		for (int i = 0; i < 2000; i++) motion.value(i, 1);
		check(motion.retained() <= 512, "bounded state");
		motion.navigation(20);
		motion.navigation(60);
		motion.begin(370_000_000, 3, false);
		check(motion.navigation(60) == 60, "navigation persists across content changes");
		motion.begin(900_000_000_000L, 3, false);
		check(motion.value(1999, 1) == 1, "long focus gaps do not replay animation");
		check(SettingsUiKit.enter(257) && SettingsUiKit.enter(335) && !SettingsUiKit.enter(258), "both enter keys, not Tab");
		SettingsUiKit.pointer(25, 40);
		check(SettingsUiKit.hovered(10, 20, 100, 60), "card whitespace belongs to card hover");
		SettingsUiKit.pointer(110, 40);
		check(!SettingsUiKit.hovered(10, 20, 100, 60), "adjacent card edge excluded");
		return 13;
	}
	private static void check(boolean value, String description) {
		if (!value) throw new AssertionError(description);
	}
}
