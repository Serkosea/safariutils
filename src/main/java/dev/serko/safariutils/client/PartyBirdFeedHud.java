package dev.serko.safariutils.client;

import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.session.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Party feed panel, enhanced with authoritative shared state when available. */
public final class PartyBirdFeedHud implements HudElement {
	private static final String[] BIRDS = {"Bluebird", "Parakeet", "Macaw"};
	private static final int[] BIRD_COLOURS = {0xFF1647D8, 0xFFA7E522, 0xFFF04A24};
	private final TickCache<HudPanel> panelCache = new TickCache<>();

	/** The exact border colour used by both the panel frame and its title. */
	public static int borderColour() {
		return HudBorderStyle.birdFeed();
	}

	/** Uses synchronized party data when present and a conservative local view otherwise. */
	public static HudPanel panel() {
		HudPanel synchronizedPanel = PartyItemSyncProviders.birdFeedPanel();
		if (synchronizedPanel != null) return synchronizedPanel;
		Minecraft client = Minecraft.getInstance();
		if (SessionManager.current() == null || client.player == null) return null;

		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = new HudPanel().title("Party Bird Feed", borderColour()).minimumWidth(155);
		if (config.birdFeedShowPlayers) addUnsynchronizedPlayers(panel, client);

		int drops = Math.min(9, BirdfeederWatch.floorFeedFound());
		int found = Math.max(0, BirdfeederWatch.feedFound());
		int used = Math.min(Math.max(0, BirdfeederWatch.feedUsed()), found);
		boolean done = drops >= 9 && found > 0 && used >= found;
		panel.pair("Forest Drops", drops >= 9 ? "✔" : drops + "/9",
			0xFF55FFAA, drops >= 9 ? 0xFF55FF55 : 0xFFFFFFFF);
		if (config.birdFeedShowFeedDone) {
			panel.pair("Feed Done", used + "/" + found,
				done ? 0xFF55FF55 : 0xFFFFAA00, done ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.birdFeedShowBirdfeeder) {
			addFeederRow(panel, BirdfeederWatch.feederType(), BirdfeederWatch.feederCount());
		}
		if (config.birdFeedShowBirdCounts) {
			panel.iconPair("Birds", 0xFF55FFFF, visibleBirds());
		}
		return panel;
	}

	private static void addUnsynchronizedPlayers(HudPanel panel, Minecraft client) {
		int seeds = SafariObjectives.bagOfSeedsHeld();
		int worms = SafariObjectives.wrigglewormsHeld();
		int berries = SafariObjectives.yogiBerriesHeld();
		boolean showedPlayer = false;
		if (seeds + worms + berries > 0) {
			panel.iconPair(client.player.getGameProfile().name(), 0xFFFFFFFF,
				feedValues(String.valueOf(berries), String.valueOf(worms), String.valueOf(seeds)));
			showedPlayer = true;
		}
		String localName = client.player.getGameProfile().name();
		for (String name : SafariPartyWatch.presentPlayerNames()) {
			if (name.equalsIgnoreCase(localName)) continue;
			panel.iconPair(name, 0xFFFFFFFF, feedValues("?", "?", "?"));
			showedPlayer = true;
		}
		if (showedPlayer) panel.blank();
	}

	private static HudPanel.IconValue[] feedValues(String berries, String worms, String seeds) {
		return new HudPanel.IconValue[] {
			new HudPanel.IconValue(HudPanel.HudIcon.BERRY, berries, 0xFF2D69EA),
			new HudPanel.IconValue(HudPanel.HudIcon.WORM, worms, 0xFFFF64AD),
			new HudPanel.IconValue(HudPanel.HudIcon.SEED_BAG, seeds, 0xFFFFD36A)
		};
	}

	private static void addFeederRow(HudPanel panel, int type, int count) {
		if (type < 0 || count <= 0) {
			panel.pair("Birdfeeder", "Empty", 0xFFFFAA00, 0xFF888888);
			return;
		}
		HudPanel.HudIcon icon = switch (type) {
			case 0 -> HudPanel.HudIcon.SEED_BAG;
			case 1 -> HudPanel.HudIcon.WORM;
			case 2 -> HudPanel.HudIcon.BERRY;
			default -> HudPanel.HudIcon.SEED_BAG;
		};
		int colour = switch (type) {
			case 0 -> 0xFFFFD36A;
			case 1 -> 0xFFFF64AD;
			case 2 -> 0xFF2D69EA;
			default -> 0xFFFFFFFF;
		};
		panel.iconPair("Birdfeeder", 0xFFFFAA00,
			new HudPanel.IconValue(icon, String.valueOf(count), colour));
	}

	private static HudPanel.IconValue[] visibleBirds() {
		List<HudPanel.IconValue> values = new ArrayList<>();
		for (int index = 0; index < BIRDS.length; index++) {
			var critter = Critters.byName(BIRDS[index]);
			int count = critter == null ? 0 : DetectedCritters.currentConcurrent(critter);
			if (count > 0) {
				values.add(new HudPanel.IconValue(HudPanel.HudIcon.BIRD,
					String.valueOf(count), BIRD_COLOURS[index]));
			}
		}
		if (values.isEmpty()) {
			for (int colour : BIRD_COLOURS) {
				values.add(new HudPanel.IconValue(HudPanel.HudIcon.BIRD, "?", colour));
			}
		}
		return values.toArray(HudPanel.IconValue[]::new);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ConfigManager.get().display.showBirdFeedHud) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;
		HudPanel panel = panelCache.get(PartyBirdFeedHud::panel);
		if (panel == null || panel.isEmpty()) return;
		HudBox box = HudBox.BIRD_FEED;
		float scale = box.scale() * ResponsiveUI.scale(graphics.guiWidth(), graphics.guiHeight());
		int x = box.pixelX(graphics.guiWidth(), panel, client.font, scale);
		int y = box.pixelY(graphics.guiHeight(), panel, scale);
		panel.render(graphics, client.font, x, y, scale, borderColour());
	}
}
