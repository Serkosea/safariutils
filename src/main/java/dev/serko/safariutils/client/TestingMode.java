package dev.serko.safariutils.client;

import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.session.SessionManager;

/** Keeps Alpha and diagnostic sessions isolated from the player's saved data. */
public final class TestingMode {
	private static boolean wasEnabled;
	private static boolean savingSuspended;

	private TestingMode() {
	}

	public static boolean enabled() {
		return ConfigManager.get().advanced.testingSession;
	}

	/** Once testing starts, saving stays off until restart so test-only memory cannot leak later. */
	public static boolean savingSuspended() {
		return savingSuspended;
	}

	/** Whether clean solo discoveries should extend the local location catalogs. */
	public static boolean saveLearnedLocations() {
		return ConfigManager.get().advanced.testingSaveLearnedLocations;
	}

	/** Applies the session-only toggle immediately after the settings screen changes it. */
	public static void settingChanged() {
		boolean enabled = enabled();
		if (enabled) savingSuspended = true;
		AtomicFiles.suspendWrites(savingSuspended);
		if (enabled && !wasEnabled) SessionManager.discardForTesting();
		wasEnabled = enabled;
	}
}
