package dev.serko.safariutils.mixin;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.client.InteractionDebugLog;
import dev.serko.safariutils.client.ParticleDiagnostics;
import dev.serko.safariutils.client.PartyObjectiveHud;
import dev.serko.safariutils.client.ServerPacketDiagnostics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Passive observations at the start of vanilla packet handlers.
 *
 * <p>Every inbound injection is non-cancellable, returns {@code void}, and only
 * forwards read-only values to diagnostics. It never modifies a packet,
 * connection, callback, or vanilla control flow. The sole outbound hook observes
 * commands for the existing interface debugger and likewise never changes them.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleConfigurationStart", at = @At("HEAD"))
	private void safariutils$configurationStart(ClientboundStartConfigurationPacket packet,
			CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.configurationStart();
	}

	@Inject(method = "handleLogin", at = @At("HEAD"))
	private void safariutils$login(ClientboundLoginPacket packet, CallbackInfo info) {
		PartyObjectiveHud.invalidatePlayerColours();
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.login();
	}

	@Inject(method = "handleRespawn", at = @At("HEAD"))
	private void safariutils$respawn(ClientboundRespawnPacket packet, CallbackInfo info) {
		PartyObjectiveHud.invalidatePlayerColours();
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.respawn(packet);
	}

	@Inject(method = "handleMovePlayer", at = @At("HEAD"))
	private void safariutils$playerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.position(packet);
	}

	@Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
	private void safariutils$chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.chunk(packet);
	}

	@Inject(method = "handleSetChunkCacheCenter", at = @At("HEAD"))
	private void safariutils$chunkCenter(ClientboundSetChunkCacheCenterPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.chunkCenter(packet);
	}

	@Inject(method = "handleSetSpawn", at = @At("HEAD"))
	private void safariutils$spawn(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.defaultSpawn(packet);
	}

	@Inject(method = "handleAddObjective", at = @At("HEAD"))
	private void safariutils$objective(ClientboundSetObjectivePacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.objective(packet);
	}

	@Inject(method = "handleSetDisplayObjective", at = @At("HEAD"))
	private void safariutils$displayObjective(ClientboundSetDisplayObjectivePacket packet,
			CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.displayObjective(packet);
	}

	@Inject(method = "handleSetScore", at = @At("HEAD"))
	private void safariutils$score(ClientboundSetScorePacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.score(packet);
	}

	@Inject(method = "handleResetScore", at = @At("HEAD"))
	private void safariutils$resetScore(ClientboundResetScorePacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.resetScore(packet);
	}

	@Inject(method = "setActionBarText", at = @At("HEAD"))
	private void safariutils$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.actionBar(packet);
	}

	@Inject(method = "setTitleText", at = @At("HEAD"))
	private void safariutils$title(ClientboundSetTitleTextPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.title(packet);
	}

	@Inject(method = "setSubtitleText", at = @At("HEAD"))
	private void safariutils$subtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.subtitle(packet);
	}

	@Inject(method = "setTitlesAnimation", at = @At("HEAD"))
	private void safariutils$titleTiming(ClientboundSetTitlesAnimationPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.titleTiming(packet);
	}

	@Inject(method = "handleTitlesClear", at = @At("HEAD"))
	private void safariutils$clearTitles(ClientboundClearTitlesPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.clearTitles(packet);
	}

	@Inject(method = "handleTabListCustomisation", at = @At("HEAD"))
	private void safariutils$tabList(ClientboundTabListPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.tabList(packet);
	}

	@Inject(method = "handleBossUpdate", at = @At("HEAD"))
	private void safariutils$boss(ClientboundBossEventPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.boss(packet);
	}

	@Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"))
	private void safariutils$playerInfo(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo info) {
		if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
			|| packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) {
			PartyObjectiveHud.invalidatePlayerColours();
		}
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.playerInfo(packet);
	}

	@Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"))
	private void safariutils$playerInfoRemove(ClientboundPlayerInfoRemovePacket packet,
			CallbackInfo info) {
		PartyObjectiveHud.invalidatePlayerColours();
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.playerInfoRemove(packet);
	}

	@Inject(method = "handleContainerContent", at = @At("HEAD"))
	private void safariutils$container(ClientboundContainerSetContentPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.container(packet);
	}

	@Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
	private void safariutils$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.slot(packet);
	}

	@Inject(method = "handleSetPlayerInventory", at = @At("HEAD"))
	private void safariutils$playerInventory(ClientboundSetPlayerInventoryPacket packet,
			CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.playerInventory(packet);
	}

	@Inject(method = "handleSetCursorItem", at = @At("HEAD"))
	private void safariutils$cursor(ClientboundSetCursorItemPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.cursor(packet);
	}

	@Inject(method = "handleSetHeldSlot", at = @At("HEAD"))
	private void safariutils$heldSlot(ClientboundSetHeldSlotPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.heldSlot(packet);
	}

	@Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
	private void safariutils$takeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.takeItem(packet);
	}

	@Inject(method = "handleAddEntity", at = @At("HEAD"))
	private void safariutils$entityAdded(ClientboundAddEntityPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.entityAdded(packet);
	}

	@Inject(method = "handleRemoveEntities", at = @At("HEAD"))
	private void safariutils$entitiesRemoved(ClientboundRemoveEntitiesPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.entitiesRemoved(packet);
	}

	@Inject(method = "handleSetEntityData", at = @At("HEAD"))
	private void safariutils$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.entityData(packet);
	}

	@Inject(method = "handleSetTime", at = @At("HEAD"))
	private void safariutils$time(ClientboundSetTimePacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.time(packet);
	}

	@Inject(method = "handleTickingState", at = @At("HEAD"))
	private void safariutils$ticking(ClientboundTickingStatePacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.ticking(packet);
	}

	@Inject(method = "handleGameEvent", at = @At("HEAD"))
	private void safariutils$gameEvent(ClientboundGameEventPacket packet, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.gameEvent(packet);
	}

	@Inject(method = "handleCustomPayload", at = @At("HEAD"))
	private void safariutils$customPayload(CustomPacketPayload payload, CallbackInfo info) {
		if (BuildVersion.DEVELOPER) ServerPacketDiagnostics.customPayload(payload);
	}

	@Inject(method = "sendCommand", at = @At("HEAD"))
	private void safariutils$outboundCommand(String command, CallbackInfo info) {
		InteractionDebugLog.onCommand(command);
	}

	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void safariutils$particle(ClientboundLevelParticlesPacket packet, CallbackInfo info) {
		ParticleDiagnostics.onParticle(packet);
	}
}
