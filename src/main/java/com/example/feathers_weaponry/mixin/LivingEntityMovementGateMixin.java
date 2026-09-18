package com.example.feathers_weaponry.mixin;

import com.example.feathers_weaponry.ClientMovementEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementGateMixin {
    @Inject(method = {"setSprinting", "m_6858_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void feathersweaponry$cancelSprintWhenExhausted(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) {
            return;
        }

        if ((Object) this instanceof LocalPlayer player) {
            if (!ClientMovementEvents.canLocalPlayerSprintOrSwim(player)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = {"jumpFromGround", "m_6135_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void feathersweaponry$cancelJumpWhenExhausted(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) {
            if (!ClientMovementEvents.canLocalPlayerJump(player)) {
                ci.cancel();
            }
        }
    }
}
