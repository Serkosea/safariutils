package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.ParticleDiagnostics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the server particle packet before Minecraft expands it into client particles. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void safariutils$particle(ClientboundLevelParticlesPacket packet, CallbackInfo info) {
		ParticleDiagnostics.onParticle(packet);
	}
}
