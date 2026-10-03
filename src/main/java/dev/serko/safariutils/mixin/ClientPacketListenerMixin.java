package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.InteractionDebugLog;
import dev.serko.safariutils.client.HypixelConnection;
import dev.serko.safariutils.client.ParticleDiagnostics;
import dev.serko.safariutils.client.PartyObjectiveHud;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minimal packet hooks required by live SafariUtils features. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleLogin", at = @At("HEAD"))
	private void safariutils$login(ClientboundLoginPacket packet, CallbackInfo info) {
		if (!HypixelConnection.active()) return;
		PartyObjectiveHud.invalidatePlayerColours();
	}

	@Inject(method = "handleRespawn", at = @At("HEAD"))
	private void safariutils$respawn(ClientboundRespawnPacket packet, CallbackInfo info) {
		if (!HypixelConnection.active()) return;
		PartyObjectiveHud.invalidatePlayerColours();
	}

	@Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"))
	private void safariutils$playerInfo(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo info) {
		if (!HypixelConnection.active()) return;
		if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
			|| packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) {
			PartyObjectiveHud.invalidatePlayerColours();
		}
	}

	@Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"))
	private void safariutils$playerInfoRemove(ClientboundPlayerInfoRemovePacket packet,
			CallbackInfo info) {
		if (!HypixelConnection.active()) return;
		PartyObjectiveHud.invalidatePlayerColours();
	}

	@Inject(method = "sendCommand", at = @At("HEAD"))
	private void safariutils$outboundCommand(String command, CallbackInfo info) {
		if (!HypixelConnection.active()) return;
		InteractionDebugLog.onCommand(command);
	}

	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void safariutils$particle(ClientboundLevelParticlesPacket packet, CallbackInfo info) {
		if (!HypixelConnection.active()) return;
		ParticleDiagnostics.onParticle(packet);
	}
}
