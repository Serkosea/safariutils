package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.AlertSounds;
import dev.serko.safariutils.client.ConfigManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Filters non-alert audio without changing the player's saved sound sliders. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
	@Shadow protected abstract float calculateVolume(SoundInstance sound);
	// Completed sounds can be reclaimed without a second cleanup loop.
	@Unique private final Set<SoundInstance> safariutils$alerts =
		Collections.newSetFromMap(new WeakHashMap<>());
	@Unique private boolean safariutils$wasMuted;

	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void safariutils$filterPlayback(SoundInstance sound,
			CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
		if (AlertSounds.playingAlert()) safariutils$alerts.add(sound);
		if (ConfigManager.get().alerts.muteOtherSounds && !safariutils$alerts.contains(sound)) {
			callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
		at = @At("HEAD"), cancellable = true)
	private void safariutils$muteTickingSound(SoundInstance sound, CallbackInfoReturnable<Float> callback) {
		if (ConfigManager.get().alerts.muteOtherSounds && !safariutils$alerts.contains(sound)) {
			callback.setReturnValue(0f);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void safariutils$refreshExistingSounds(boolean paused, CallbackInfo callback) {
		boolean muted = ConfigManager.get().alerts.muteOtherSounds;
		if (muted == safariutils$wasMuted) return;
		safariutils$wasMuted = muted;
		// Existing music and loops need one gain update when the setting changes.
		instanceToChannel.forEach((sound, handle) -> {
			if (safariutils$alerts.contains(sound)) return;
			float volume = calculateVolume(sound);
			handle.execute(channel -> channel.setVolume(volume));
		});
	}
}
