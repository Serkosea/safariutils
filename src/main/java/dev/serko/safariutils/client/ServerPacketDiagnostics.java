package dev.serko.safariutils.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Read-only summaries of packets vanilla Minecraft has already received.
 *
 * <p>Every caller is a non-cancellable HEAD injection. Nothing in this class holds
 * a packet past the callback, mutates one, writes to the connection, or influences
 * the vanilla handler. No payload bytes, credentials, addresses, or session data
 * are inspected. High-volume world/entity traffic is aggregated once per second.
 */
public final class ServerPacketDiagnostics {

	private static final Map<String, Integer> entityAdds = new HashMap<>();
	private static final Set<String> payloadChannels = new HashSet<>();
	private static int entityRemovals;
	private static int entityMetadata;
	private static int chunks;
	private static int bossUpdates;
	private static long flushAt;
	private static String lastActionBar;
	private static String lastTitle;
	private static String lastSubtitle;
	private static String lastTabList;

	private ServerPacketDiagnostics() {
	}

	public static void configurationStart() {
		JoinWindowDiagnostics.onInboundPacket("configuration-start", 2);
		line("PKT-TRANS", "configuration start");
	}

	public static void login() {
		JoinWindowDiagnostics.onWorldBoundaryPacket("login");
		line("PKT-TRANS", "login/world creation");
	}

	public static void respawn(ClientboundRespawnPacket packet) {
		JoinWindowDiagnostics.onWorldBoundaryPacket("respawn");
		line("PKT-TRANS", "respawn/world replacement keep=" + packet.dataToKeep());
	}

	public static void position(ClientboundPlayerPositionPacket packet) {
		JoinWindowDiagnostics.onInboundPacket("player-position", 6);
		if (!enabled(ConfigManager.get().advanced.logPacketTransitions)) return;
		var pos = packet.change().position();
		DebugLog.line("PKT-TRANS", "position id=" + packet.id() + " at="
			+ "%.2f,%.2f,%.2f".formatted(pos.x, pos.y, pos.z)
			+ " relative=" + packet.relatives());
	}

	public static void chunk(ClientboundLevelChunkWithLightPacket packet) {
		JoinWindowDiagnostics.onInboundPacket("level-chunk", 4);
		if (!enabled(ConfigManager.get().advanced.logPacketWorld)) return;
		chunks++;
		scheduleFlush();
	}

	public static void chunkCenter(ClientboundSetChunkCacheCenterPacket packet) {
		JoinWindowDiagnostics.onInboundPacket("chunk-cache-center", 3);
		line("PKT-TRANS", "chunk center " + packet.getX() + "," + packet.getZ());
	}

	public static void defaultSpawn(ClientboundSetDefaultSpawnPositionPacket packet) {
		JoinWindowDiagnostics.onInboundPacket("default-spawn", 2);
		line("PKT-TRANS", "default spawn updated");
	}

	public static void objective(ClientboundSetObjectivePacket packet) {
		JoinWindowDiagnostics.onInboundPacket("scoreboard-objective", 4);
		line("PKT-HUD", "objective method=" + packet.getMethod() + " id="
			+ quote(packet.getObjectiveName()) + " display="
			+ quote(packet.getDisplayName() == null ? "" : packet.getDisplayName().getString()));
	}

	public static void displayObjective(ClientboundSetDisplayObjectivePacket packet) {
		JoinWindowDiagnostics.onInboundPacket("scoreboard-display", 3);
		line("PKT-HUD", "display objective slot=" + packet.getSlot()
			+ " id=" + quote(packet.getObjectiveName()));
	}

	public static void score(ClientboundSetScorePacket packet) {
		JoinWindowDiagnostics.onInboundPacket("scoreboard-score", 8);
		String shown = packet.display().map(component -> component.getString()).orElse("");
		line("PKT-HUD", "score objective=" + quote(packet.objectiveName())
			+ " owner=" + quote(packet.owner()) + " value=" + packet.score()
			+ (shown.isBlank() ? "" : " display=" + quote(shown)));
	}

	public static void resetScore(ClientboundResetScorePacket packet) {
		line("PKT-HUD", "reset score objective=" + quote(packet.objectiveName())
			+ " owner=" + quote(packet.owner()));
	}

	public static void actionBar(ClientboundSetActionBarTextPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketHud)) return;
		String text = clean(packet.text().getString());
		if (!text.equals(lastActionBar)) {
			lastActionBar = text;
			line("PKT-HUD", "actionbar " + quote(text));
		}
	}

	public static void title(ClientboundSetTitleTextPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketHud)) return;
		String text = clean(packet.text().getString());
		if (!text.equals(lastTitle)) {
			lastTitle = text;
			line("PKT-HUD", "title " + quote(text));
		}
	}

	public static void subtitle(ClientboundSetSubtitleTextPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketHud)) return;
		String text = clean(packet.text().getString());
		if (!text.equals(lastSubtitle)) {
			lastSubtitle = text;
			line("PKT-HUD", "subtitle " + quote(text));
		}
	}

	public static void titleTiming(ClientboundSetTitlesAnimationPacket packet) {
		line("PKT-HUD", "title timing " + packet.getFadeIn() + "/"
			+ packet.getStay() + "/" + packet.getFadeOut());
	}

	public static void clearTitles(ClientboundClearTitlesPacket packet) {
		line("PKT-HUD", "clear titles resetTiming=" + packet.shouldResetTimes());
	}

	public static void tabList(ClientboundTabListPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketHud)) return;
		String value = "header=" + quote(packet.header().getString())
			+ " footer=" + quote(packet.footer().getString());
		if (!value.equals(lastTabList)) {
			lastTabList = value;
			line("PKT-HUD", "tab " + value);
		}
	}

	public static void boss(ClientboundBossEventPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketHud)) return;
		bossUpdates++;
		scheduleFlush();
	}

	public static void playerInfo(ClientboundPlayerInfoUpdatePacket packet) {
		line("PKT-HUD", "player info actions=" + packet.actions()
			+ " entries=" + packet.entries().size());
	}

	public static void playerInfoRemove(ClientboundPlayerInfoRemovePacket packet) {
		line("PKT-HUD", "player info removed=" + packet.profileIds().size());
	}

	public static void container(ClientboundContainerSetContentPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketInventory)) return;
		long occupied = packet.items().stream().filter(stack -> !stack.isEmpty()).count();
		DebugLog.line("PKT-INV", "container=" + packet.containerId() + " state="
			+ packet.stateId() + " slots=" + packet.items().size() + " occupied=" + occupied
			+ " carried=" + item(packet.carriedItem()));
	}

	public static void slot(ClientboundContainerSetSlotPacket packet) {
		line("PKT-INV", "container=" + packet.getContainerId() + " state="
			+ packet.getStateId() + " slot=" + packet.getSlot() + " item=" + item(packet.getItem()));
	}

	public static void playerInventory(ClientboundSetPlayerInventoryPacket packet) {
		line("PKT-INV", "player slot=" + packet.slot() + " item=" + item(packet.contents()));
	}

	public static void cursor(ClientboundSetCursorItemPacket packet) {
		line("PKT-INV", "cursor=" + item(packet.contents()));
	}

	public static void heldSlot(ClientboundSetHeldSlotPacket packet) {
		line("PKT-INV", "selected hotbar slot=" + packet.slot());
	}

	public static void takeItem(ClientboundTakeItemEntityPacket packet) {
		line("PKT-INV", "pickup itemEntity=" + packet.getItemId()
			+ " collector=" + packet.getPlayerId() + " amount=" + packet.getAmount());
	}

	public static void entityAdded(ClientboundAddEntityPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketEntities)) return;
		entityAdds.merge(String.valueOf(packet.getType()), 1, Integer::sum);
		scheduleFlush();
	}

	public static void entitiesRemoved(ClientboundRemoveEntitiesPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketEntities)) return;
		entityRemovals += packet.getEntityIds().size();
		scheduleFlush();
	}

	public static void entityData(ClientboundSetEntityDataPacket packet) {
		if (!enabled(ConfigManager.get().advanced.logPacketEntities)) return;
		entityMetadata++;
		scheduleFlush();
	}

	public static void time(ClientboundSetTimePacket packet) {
		line("PKT-WORLD", "time game=" + packet.gameTime()
			+ " clocks=" + packet.clockUpdates().size());
	}

	public static void ticking(ClientboundTickingStatePacket packet) {
		line("PKT-WORLD", "ticking rate=" + packet.tickRate()
			+ " frozen=" + packet.isFrozen());
	}

	public static void gameEvent(ClientboundGameEventPacket packet) {
		line("PKT-WORLD", "game event=" + packet.getEvent() + " param=" + packet.getParam());
	}

	public static void customPayload(CustomPacketPayload payload) {
		if (!enabled(ConfigManager.get().advanced.logPacketChannels)) return;
		String channel = String.valueOf(payload.type().id());
		if (payloadChannels.add(channel)) {
			DebugLog.line("PKT-CHANNEL", "observed channel=" + channel
				+ " (contents intentionally unread)");
		}
	}

	/** Flushes bounded summaries; called once per client tick only in developer builds. */
	public static void tick() {
		if (!DebugLog.isEnabled()) {
			clearAggregates();
			return;
		}
		if (!ConfigManager.get().advanced.logPacketChannels) payloadChannels.clear();
		long now = System.currentTimeMillis();
		if (flushAt == 0L || now < flushAt) return;
		if (!entityAdds.isEmpty() || entityRemovals > 0 || entityMetadata > 0) {
			DebugLog.line("PKT-ENTITY", "1s summary added=" + new TreeMap<>(entityAdds)
				+ " removed=" + entityRemovals + " metadata=" + entityMetadata);
		}
		if (chunks > 0) DebugLog.line("PKT-WORLD", "1s summary chunks=" + chunks);
		if (bossUpdates > 0) DebugLog.line("PKT-HUD", "1s summary bossbar updates=" + bossUpdates);
		entityAdds.clear();
		entityRemovals = 0;
		entityMetadata = 0;
		chunks = 0;
		bossUpdates = 0;
		flushAt = 0L;
	}

	private static void line(String category, String message) {
		if (!enabled(switch (category) {
			case "PKT-TRANS" -> ConfigManager.get().advanced.logPacketTransitions;
			case "PKT-HUD" -> ConfigManager.get().advanced.logPacketHud;
			case "PKT-INV" -> ConfigManager.get().advanced.logPacketInventory;
			case "PKT-WORLD" -> ConfigManager.get().advanced.logPacketWorld;
			default -> false;
		})) return;
		DebugLog.line(category, message);
	}

	private static boolean enabled(boolean option) {
		return option && DebugLog.isEnabled();
	}

	private static void scheduleFlush() {
		if (flushAt == 0L) flushAt = System.currentTimeMillis() + 1_000L;
	}

	private static void clearAggregates() {
		entityAdds.clear();
		entityRemovals = 0;
		entityMetadata = 0;
		chunks = 0;
		flushAt = 0L;
		payloadChannels.clear();
		bossUpdates = 0;
		lastActionBar = null;
		lastTitle = null;
		lastSubtitle = null;
		lastTabList = null;
	}

	private static String item(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "empty"
			: stack.getCount() + "x " + clean(stack.getHoverName().getString());
	}

	private static String quote(String text) {
		return '"' + clean(text) + '"';
	}

	private static String clean(String text) {
		if (text == null) return "";
		String cleaned = text.replaceAll("§.", "").replace('\n', ' ').replace('\r', ' ');
		return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 497) + "...";
	}
}
