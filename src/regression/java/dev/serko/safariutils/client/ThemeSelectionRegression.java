package dev.serko.safariutils.client;

public final class ThemeSelectionRegression {
	public static int run() throws Exception {
		var field = ConfigManager.class.getDeclaredField("config");
		field.setAccessible(true);
		Object previous = field.get(null);
		var config = new SafariConfig();
		field.set(null, config);
		int normal = config.display.settingsTheme;
		int special = config.advanced.specialTheme;
		boolean unlocked = config.advanced.constellationThemeUnlocked;
		try {
			SpecialTheme.selectNormal(34);
			check(SpecialTheme.selectedMode() == SpecialTheme.OFF, "normal selection disables special");
			SpecialTheme.selectSpecial(SpecialTheme.RAINBOW);
			int[] rainbow = SpecialTheme.settingsPalette().clone();
			check(SpecialTheme.selectedMode() == SpecialTheme.RAINBOW && config.display.settingsTheme == 34,
				"special selection retains inactive custom preference");
			config.display.settingsTheme = 1;
			check(java.util.Arrays.equals(rainbow, SpecialTheme.settingsPalette()), "normal palette cannot tint rainbow");
			config.advanced.constellationThemeUnlocked = false;
			SpecialTheme.selectSpecial(SpecialTheme.CONSTELLATION);
			check(SpecialTheme.selectedMode() == SpecialTheme.RAINBOW, "locked Astral cannot be selected");
			config.advanced.constellationThemeUnlocked = true;
			SpecialTheme.selectSpecial(SpecialTheme.CONSTELLATION);
			int[] astral = SpecialTheme.settingsPalette().clone();
			check(!java.util.Arrays.equals(rainbow, astral), "special themes have distinct palettes");
			config.display.settingsTheme = 34;
			check(java.util.Arrays.equals(astral, SpecialTheme.settingsPalette()), "custom palette cannot tint Astral");
			SpecialTheme.selectNormal(2);
			check(SpecialTheme.selectedMode() == SpecialTheme.OFF && SpecialTheme.selectedName() == null,
				"normal selection clears special label and selection");
			return 7;
		} finally {
			SpecialTheme.selectNormal(normal);
			config.advanced.constellationThemeUnlocked = unlocked;
			SpecialTheme.selectSpecial(special);
			field.set(null, previous);
		}
	}
	private static void check(boolean condition, String description) {
		if (!condition) throw new AssertionError(description);
	}
}
