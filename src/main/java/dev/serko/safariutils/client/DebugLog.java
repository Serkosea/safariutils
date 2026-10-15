package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;

/** Lightweight compatibility facade for automatic, asynchronous diagnostics. */
public final class DebugLog {
	private DebugLog() { }

	/** Detailed gameplay diagnostics are useful only while a Safari world is active. */
	public static boolean isEnabled() {
		return BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)
			&& !ConfigManager.automaticLoggingDisabled()
			&& SafariLocation.inSafari();
	}

	public static void line(String category, String message) {
		if (BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)
				&& !ConfigManager.automaticLoggingDisabled()
			&& (isEnabled() || alwaysUseful(category))) {
			OperationalLog.debug(category, message);
		}
	}

	private static boolean alwaysUseful(String category) {
		return switch (category) {
			case "LOG", "RUN", "ACTIVATE", "JOINTIME", "LOCATION",
				"PARTY", "PARTYTIME", "SYNC", "PARTYAPI", "BAZAAR", "UPDATES", "PLAYER_LOOKUP" -> true;
			default -> false;
		};
	}
}
