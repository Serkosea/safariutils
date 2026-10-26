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
		return 13 + frameMotion();
	}
	private static int frameMotion() {
		SettingsMotion.Frame frame = new SettingsMotion.Frame();
		frame.begin(0);
		check(Math.abs(frame.approach(0, 1, .2f) - .2f) < .000001f, "initial frame preserves legacy speed");
		frame.begin(0);
		check(frame.approach(0, 1, .2f) == 0, "zero elapsed time does not advance");
		check(frame.approach(0, 1, 1) == 1, "disabled animation snaps even without elapsed time");
		frame.begin(16_666_667);
		float expected = frame.approach(0, 1, .2f);
		check(Math.abs(expected - .2f) < .000001f, "60 FPS coefficient retained");
		check(frame.approach(0, 1, .2f) == expected, "repeated controls use the same frame coefficient");
		for (int rate : new int[]{20, 30, 60, 120, 144, 240}) {
			SettingsMotion.Frame clock = new SettingsMotion.Frame();
			clock.begin(0);
			float value = 0;
			for (int step = 1; step <= rate / 2; step++) {
				clock.begin(Math.round(step * 1_000_000_000d / rate));
				value = clock.approach(value, 1, .1f);
			}
			check(Math.abs(value - (1 - Math.pow(.9, 30))) < .00001, "equal elapsed animation at " + rate + " FPS");
		}
		frame.begin(9_000_000_000L);
		check(Math.abs(frame.approach(0, 1, .2f) - .488f) < .000001f, "focus pause has bounded step");
		frame.begin(8_000_000_000L);
		check(frame.approach(0, 1, .2f) == 0, "backward timestamp does not reverse animation");
		frame.reset(); frame.begin(20_000_000_000L);
		check(Math.abs(frame.approach(0, 1, .2f) - .2f) < .000001f, "screen reinitialization resets clock only");
		check(frame.approach(0, 1, Float.NaN) == 0 && frame.approach(0, 1, 0) == 0, "invalid or stopped speed cannot poison state");
		for (int speed = 1; speed < 100; speed++) {
			float value = frame.approach(0, 1, speed / 100f);
			check(value > 0 && value < 1, "bounded cache overflow preserves calculation");
		}
		return 114;
	}
	private static void check(boolean value, String description) {
		if (!value) throw new AssertionError(description);
	}
}
