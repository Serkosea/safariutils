package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.AutomaticTicketEntry;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid the unfocused pause-screen/reclose loop during bounded automatic ticket entry. */
@Mixin(Minecraft.class)
public abstract class TicketApproachFocusMixin {
    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
    private void safariutils$keepTicketEntryActive(CallbackInfo callback) {
        if (AutomaticTicketEntry.keepsBackgroundEntryActive()) callback.cancel();
    }
}
