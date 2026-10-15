package dev.serko.safariutils.client;

/** Canonical geometry shared by the banner HUD editor and every live banner. */
final class BannerPlacement {
	private BannerPlacement() { }

	static float scale() {
		return Math.clamp(ConfigManager.get().alerts.alertScale, 0.1f, 10f);
	}

	/** Stored as the editor preview's normalized top edge for settings compatibility. */
	static float top() {
		return Math.clamp(ConfigManager.get().alerts.alertVerticalPosition, 0f, 1f);
	}

	/**
	 * Converts the editor preview's top-edge anchor to the centre anchor used by the
	 * responsive live renderer. Multiline banners can then expand equally upward and
	 * downward without maintaining a second set of placement settings.
	 */
	static float physicalCentre(int screenHeight, float renderedScale, float normalizedTop) {
		if (screenHeight <= 0) return 0f;
		HudPanel preview = EncounterAlerts.editorPanel();
		int previewHeight = Math.max(1, Math.round(preview.height() * renderedScale));
		int minimumTop = previewHeight < screenHeight ? 1 : 0;
		int maximumTop = Math.max(minimumTop, screenHeight - previewHeight - 1);
		int top = Math.clamp(Math.round(Math.clamp(normalizedTop, 0f, 1f) * screenHeight),
			minimumTop, maximumTop);
		return top + previewHeight / 2f;
	}
}
