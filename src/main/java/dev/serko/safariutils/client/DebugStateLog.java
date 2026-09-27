package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Change-only snapshots for server state that does not arrive through chat. */
public final class DebugStateLog {

	private static String lastLocation;
	private static List<String> lastRoster = List.of();
	private static List<String> lastTabList = List.of();
	private static List<String> lastScoreboard = List.of();
	private static List<String> lastInventory = List.of();
	private static String lastObjectives;
	private static int ticks;

	private DebugStateLog() {
	}

	public static void tick() {
		if (++ticks % 10 != 0) return;
		boolean safari = SafariLocation.inSafari();
		if (ticks % 20 == 0) {
			String location = "area=" + SafariLocation.tabListArea()
				+ " subArea=" + SafariLocation.area()
				+ " where=" + SafariLocation.where()
				+ " biome=" + SafariLocation.biome()
				+ " source=" + SafariLocation.source()
				+ " lobby=" + SafariLocation.lobbyId()
				+ " essence=" + SafariLocation.safariEssence();
			if (!Objects.equals(location, lastLocation)) {
				lastLocation = location;
				DebugLog.line("LOCATION", location);
			}

			if (safari) {
				List<String> roster = rosterSnapshot();
				if (!roster.equals(lastRoster)) {
					lastRoster = roster;
					DebugLog.line("PARTY", snapshot(roster));
				}
			} else lastRoster = List.of();

			if (safari) {
				// Metadata rows contain location and server state without duplicating every
				// player already represented by the roster snapshot.
				List<String> tabList = SafariLocation.tabListEntries().stream()
					.filter(DebugStateLog::usefulTabEntry).toList();
				if (!tabList.equals(lastTabList)) {
					lastTabList = tabList;
					DebugLog.line("TABLIST", snapshot(tabList));
				}

				List<String> scoreboard = List.copyOf(SafariLocation.sidebarLines());
				if (!scoreboard.equals(lastScoreboard)) {
					lastScoreboard = scoreboard;
					DebugLog.line("SCORE", snapshot(scoreboard));
				}
			} else {
				lastTabList = List.of();
				lastScoreboard = List.of();
			}
		}

		if (safari) {
			List<String> inventory = inventorySnapshot();
			if (!inventory.equals(lastInventory)) {
				lastInventory = inventory;
				DebugLog.line("INVENTORY", snapshot(inventory));
			}
			String objective = objectiveSnapshot();
			if (!Objects.equals(objective, lastObjectives)) {
				lastObjectives = objective;
				DebugLog.line("OBJECTIVE", objective);
			}
		} else {
			lastInventory = List.of();
			lastObjectives = null;
		}
	}

	private static boolean usefulTabEntry(String entry) {
		String lower = entry.toLowerCase(java.util.Locale.ROOT);
		return lower.contains("area") || lower.contains("biome") || lower.contains("safari")
			|| lower.contains("zone") || entry.contains("⏣");
	}

	private static String objectiveSnapshot() {
		if (dev.serko.safariutils.session.SessionManager.current() == null) return "no active run";
		return "gems=" + SafariObjectives.purpleGemsHeld() + "/" + SafariObjectives.limeGemsHeld()
			+ "/" + SafariObjectives.orangeGemsHeld() + " placed=" + SafariObjectives.placedGemMask()
			+ " door=" + SafariObjectives.gemzieDoorOpened()
			+ " incense=" + SafariObjectives.incenseHeld() + " lit=" + SafariObjectives.incenseUsed()
			+ " doom=" + SafariObjectives.doomspiralSpawned() + "/"
			+ SafariObjectives.doomspiralCaught() + "/" + SafariObjectives.doomspiralRetreated()
			+ " icy=" + PartyObjectiveHud.icyUniqueCatches() + "/" + SafariObjectives.wumpaSpawned()
			+ "/" + SafariObjectives.wumpaCaught() + "/" + SafariObjectives.wumpaRetreated()
			+ " feed held=" + SafariObjectives.yogiBerriesHeld() + "/"
			+ SafariObjectives.wrigglewormsHeld() + "/" + SafariObjectives.bagOfSeedsHeld()
			+ " found=" + BirdfeederWatch.feedFound() + " used=" + BirdfeederWatch.feedUsed()
			+ " feeder=" + BirdfeederWatch.feederType() + "/" + BirdfeederWatch.feederCount();
	}

	private static List<String> rosterSnapshot() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) return List.of();
		List<String> result = new ArrayList<>();
		for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
			if (!info.getProfile().name().matches("[A-Za-z0-9_]{1,16}")) continue;
			String shown = info.getTabListDisplayName() == null
				? "" : info.getTabListDisplayName().getString();
			result.add(info.getProfile().name() + " uuid=" + info.getProfile().id()
				+ " shown=\"" + shown + "\"");
		}
		result.sort(String.CASE_INSENSITIVE_ORDER);
		return List.copyOf(result);
	}

	private static List<String> inventorySnapshot() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return List.of();
		Inventory inventory = client.player.getInventory();
		List<String> result = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) result.add(slot + "=" + stack.getCount() + "x "
				+ stack.getHoverName().getString());
		}
		return List.copyOf(result);
	}

	private static String snapshot(List<String> lines) {
		return lines.isEmpty() ? "[]" : "[" + String.join(" | ", lines) + "]";
	}

}
