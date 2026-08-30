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
	private static boolean wasLogging;

	private DebugStateLog() {
	}

	public static void tick() {
		boolean logging = DebugLog.isEnabled();
		if (!logging) {
			if (wasLogging) reset();
			wasLogging = false;
			return;
		}
		wasLogging = true;
		SafariConfig.AdvancedConfig options = ConfigManager.get().advanced;

		if (options.logLocation) {
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
		} else lastLocation = null;

		if (options.logPartyRoster) {
			List<String> roster = rosterSnapshot();
			if (!roster.equals(lastRoster)) {
				lastRoster = roster;
				DebugLog.line("PARTY", snapshot(roster));
			}
		} else lastRoster = List.of();

		if (options.logTabList) {
			List<String> tabList = List.copyOf(SafariLocation.tabListEntries());
			if (!tabList.equals(lastTabList)) {
				lastTabList = tabList;
				DebugLog.line("TABLIST", snapshot(tabList));
			}
		} else lastTabList = List.of();

		if (options.logScoreboard) {
			List<String> scoreboard = List.copyOf(SafariLocation.sidebarLines());
			if (!scoreboard.equals(lastScoreboard)) {
				lastScoreboard = scoreboard;
				DebugLog.line("SCORE", snapshot(scoreboard));
			}
		} else lastScoreboard = List.of();

		if (options.logInventory) {
			List<String> inventory = inventorySnapshot();
			if (!inventory.equals(lastInventory)) {
				lastInventory = inventory;
				DebugLog.line("INVENTORY", snapshot(inventory));
			}
		} else lastInventory = List.of();
	}

	private static List<String> rosterSnapshot() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) return List.of();
		List<String> result = new ArrayList<>();
		for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
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

	private static void reset() {
		lastLocation = null;
		lastRoster = List.of();
		lastTabList = List.of();
		lastScoreboard = List.of();
		lastInventory = List.of();
	}
}
