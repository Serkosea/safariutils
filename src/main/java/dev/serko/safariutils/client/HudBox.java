package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;
import net.minecraft.client.gui.Font;

import java.util.function.Supplier;

/**
 * The movable on-screen boxes, and where each one lives.
 *
 * <p>Positions are stored as fractions of the screen rather than pixels, so a box
 * stays where you put it across resolution and GUI-scale changes. Both the live HUD
 * and {@link HudEditorScreen} go through here, so a box can only ever be drawn where
 * the editor lets you drag it.
 */
public enum HudBox {

	PROGRESS("Progress HUD", ProgressHud::buildPanel) {
		@Override
		public float x() {
			return ConfigManager.get().display.progressX;
		}

		@Override
		public float y() {
			return ConfigManager.get().display.progressY;
		}

		@Override
		public float scale() {
			return ConfigManager.get().display.progressScale;
		}

		@Override
		public int expansion() {
			return ConfigManager.get().display.progressExpansion;
		}

		@Override
		public void setPosition(float x, float y) {
			ConfigManager.get().display.progressX = x;
			ConfigManager.get().display.progressY = y;
		}

		@Override
		public void setScale(float scale) {
			ConfigManager.get().display.progressScale = scale;
		}

		@Override
		public boolean enabled() {
			return ConfigManager.get().display.hudEnabled;
		}
	},

	MISSING("Missing panel", HudBox::missingPanel) {
		@Override
		public float x() {
			return ConfigManager.get().display.missingX;
		}

		@Override
		public float y() {
			return ConfigManager.get().display.missingY;
		}

		@Override
		public float scale() {
			return ConfigManager.get().display.missingScale;
		}

		@Override
		public int expansion() {
			return ConfigManager.get().display.missingExpansion;
		}

		@Override
		public void setPosition(float x, float y) {
			ConfigManager.get().display.missingX = x;
			ConfigManager.get().display.missingY = y;
		}

		@Override
		public void setScale(float scale) {
			ConfigManager.get().display.missingScale = scale;
		}

		@Override
		public boolean enabled() {
			return ConfigManager.get().display.hudEnabled && ConfigManager.get().display.showMissing;
		}
	},

	CONTEST("Contest HUD", ContestTracker::buildPanel) {
		@Override
		public float x() {
			return ConfigManager.get().display.contestX;
		}

		@Override
		public float y() {
			return ConfigManager.get().display.contestY;
		}

		@Override
		public float scale() {
			return ConfigManager.get().display.contestScale;
		}

		@Override
		public int expansion() {
			return ConfigManager.get().display.contestExpansion;
		}

		@Override
		public void setPosition(float x, float y) {
			ConfigManager.get().display.contestX = x;
			ConfigManager.get().display.contestY = y;
		}

		@Override
		public void setScale(float scale) {
			ConfigManager.get().display.contestScale = scale;
		}

		@Override
		public boolean enabled() {
			return ConfigManager.get().display.showContestHud;
		}
	},

	ALERTS("Banner Alerts", EncounterAlerts::editorPanel) {
		@Override
		public float x() {
			return ConfigManager.get().alerts.alertHorizontalPosition;
		}

		@Override
		public float y() {
			return ConfigManager.get().alerts.alertVerticalPosition;
		}

		@Override
		public float scale() {
			return ConfigManager.get().alerts.alertScale;
		}

		@Override
		public int expansion() {
			return 1;
		}

		@Override
		public void setPosition(float x, float y) {
			ConfigManager.get().alerts.alertHorizontalPosition = x;
			ConfigManager.get().alerts.alertVerticalPosition = y;
		}

		@Override
		public void setScale(float scale) {
			ConfigManager.get().alerts.alertScale = scale;
		}

		@Override
		public float maxScale() {
			return 8f;
		}

		@Override
		public boolean enabled() {
			return true;
		}
	};

	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;
	/** HUDs start at their natural rendered size. */
	public static final float DEFAULT_SCALE = 1.0f;

	private final String label;
	private final Supplier<HudPanel> builder;

	HudBox(String label, Supplier<HudPanel> builder) {
		this.label = label;
		this.builder = builder;
	}

	public String label() {
		return label;
	}

	public abstract float x();

	public abstract float y();

	public abstract float scale();

	/** 0 expands left, 1 equally, and 2 right from the saved horizontal anchor. */
	public int expansion() {
		return 2;
	}

	public abstract void setPosition(float x, float y);

	public abstract void setScale(float scale);

	public float minScale() {
		return MIN_SCALE;
	}

	public float maxScale() {
		return MAX_SCALE;
	}

	public abstract boolean enabled();

	/** The panel as it would be drawn right now, or {@code null} if it has no content. */
	public HudPanel panel() {
		return builder.get();
	}

	/**
	 * A stand-in used by the editor when the real panel has nothing to show, so every
	 * box stays draggable outside a run.
	 */
	public HudPanel placeholderPanel() {
		HudPanel panel = new HudPanel();
		panel.title(label, 0xFFFFAA00);
		panel.line("(nothing to show yet)", 0xFF888888);
		return panel;
	}

	public int pixelX(int screenWidth, HudPanel panel, Font font) {
		return pixelX(screenWidth, panel, font, scale());
	}

	public int pixelX(int screenWidth, HudPanel panel, Font font, float renderedScale) {
		int width = Math.round(panel.width(font) * renderedScale);
		int anchor = Math.round(x() * screenWidth);
		int left = switch (Math.clamp(expansion(), 0, 2)) {
			case 0 -> anchor - width;
			case 1 -> anchor - width / 2;
			default -> anchor;
		};
		return Math.clamp(left, 0, Math.max(0, screenWidth - width));
	}

	public int pixelY(int screenHeight, HudPanel panel, Font font) {
		return pixelY(screenHeight, panel, scale());
	}

	public int pixelY(int screenHeight, HudPanel panel, float renderedScale) {
		int max = Math.max(0, screenHeight - Math.round(panel.height() * renderedScale));
		return Math.min(max, Math.round(y() * screenHeight));
	}

	/** Saves a dragged top-left pixel as this box's configured expansion anchor. */
	public void setPixelPosition(int left, int top, int screenWidth, int screenHeight,
							 HudPanel panel, Font font) {
		setPixelPosition(left, top, screenWidth, screenHeight, panel, font, scale());
	}

	public void setPixelPosition(int left, int top, int screenWidth, int screenHeight,
							 HudPanel panel, Font font, float renderedScale) {
		int width = Math.round(panel.width(font) * renderedScale);
		int anchor = switch (Math.clamp(expansion(), 0, 2)) {
			case 0 -> left + width;
			case 1 -> left + width / 2;
			default -> left;
		};
		setPosition(anchor / (float) screenWidth, top / (float) screenHeight);
	}

	private static HudPanel missingPanel() {
		SafariBiome biome = SafariLocation.biome();
		if (biome == null) return null;
		return MissingHud.buildPanel(biome, SessionManager.currentOrLast());
	}

	/** Convenience for the editor, which wants the live session without re-querying. */
	static SafariSession session() {
		return SessionManager.currentOrLast();
	}
}
