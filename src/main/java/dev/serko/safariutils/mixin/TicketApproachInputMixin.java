package dev.serko.safariutils.mixin;

import dev.serko.safariutils.client.AutomaticTicketEntry;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class TicketApproachInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void safariutils$approach(CallbackInfo callback) {
        float forward = AutomaticTicketEntry.forwardInput(keyPresses, moveVector);
        if (forward > 0) {
            keyPresses = new Input(true, false, false, false, false, false, false);
            moveVector = new Vec2(0, forward);
        }
    }
}
