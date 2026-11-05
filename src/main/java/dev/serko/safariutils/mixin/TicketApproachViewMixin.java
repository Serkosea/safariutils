package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.AutomaticTicketEntry;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after physical mouse input, without cancelling or replacing another input handler. */
@Mixin(MouseHandler.class)
public abstract class TicketApproachViewMixin {
    @Inject(method = "handleAccumulatedMovement", at = @At("RETURN"), require = 0)
    private void safariutils$smoothTicketAim(CallbackInfo callback) {
        AutomaticTicketEntry.updateView();
    }
}
