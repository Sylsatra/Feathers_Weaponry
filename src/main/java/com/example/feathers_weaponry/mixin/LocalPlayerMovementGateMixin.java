package com.example.feathers_weaponry.mixin;

import com.elenai.feathers.api.FeathersHelper;
import com.elenai.feathers.client.ClientFeathersData;
import com.example.feathers_weaponry.ClientAttackGate;
import com.example.feathers_weaponry.ClientMovementEvents;
import com.example.feathers_weaponry.FeathersWeaponryConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMovementGateMixin {
    @Inject(method = {"canStartSprinting", "m_264082_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void feathersweaponry$preventExhaustedSprint(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player == null) {
            return;
        }
        if (!ClientMovementEvents.canLocalPlayerSprintOrSwim(player)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = {"aiStep", "m_8107_"}, at = @At("HEAD"), remap = false)
    private void feathersweaponry$stopExhaustedActiveMovement(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player == null) {
            return;
        }
        if (!ClientMovementEvents.canLocalPlayerSprintOrSwim(player)) {
            if (player.isSwimming()) {
                player.setSwimming(false);
                player.setSprinting(false);
            } else if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }
    }

    @Inject(method = {"aiStep", "m_8107_"}, at = @At("TAIL"), remap = false)
    private void feathersweaponry$stopExhaustedSprintAndSwim(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player == null) {
            return;
        }
        if (!ClientMovementEvents.canLocalPlayerSprintOrSwim(player)) {
            if (player.isSwimming()) {
                player.setSwimming(false);
                player.setSprinting(false);
            } else if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }

        if (!FeathersWeaponryConfig.movementCostsEnabled.get()) {
            return;
        }

        if (!player.isCrouching() && !player.isPassenger() && !player.isFallFlying()) {
            double walkThreshold = FeathersWeaponryConfig.walkingMinFeathers.get();
            int feathers = FeathersHelper.getFeathers();
            int endurance = FeathersHelper.getEndurance();
            int weight = ClientFeathersData.getWeight();
            if (walkThreshold > 0.0 && !ClientAttackGate.canContinueMovement(feathers, endurance, weight, walkThreshold)) {
                Vec3 motion = player.getDeltaMovement();
                if (motion != null) {
                    player.setDeltaMovement(motion.x * 0.2, motion.y, motion.z * 0.2);
                }
            }
        }
    }
}
