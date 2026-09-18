package com.example.feathers_weaponry.mixin;

import com.elenai.feathers.api.FeathersHelper;
import com.elenai.feathers.client.ClientFeathersData;
import com.example.feathers_weaponry.ClientAttackGate;
import com.example.feathers_weaponry.FeathersWeaponry;
import com.example.feathers_weaponry.FeathersWeaponryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 2100)
public abstract class BetterCombatUpswingMixin {
    @Inject(method = "startUpswing", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void feathersweaponry$cancelBetterCombatUpswingBelowFeatherLimit(@Coerce Object attributes, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        boolean shouldCancel = ClientAttackGate.shouldCancelAttack(
                FeathersHelper.getFeathers(),
                FeathersHelper.getEndurance(),
                ClientFeathersData.getWeight(),
                FeathersWeaponry.clientWeaponMinimum(stack),
                FeathersWeaponry.clientWeaponStartCost(stack),
                FeathersWeaponry.isClientRestrictedWeapon(stack),
                FeathersWeaponryConfig.cancelAnimations.get());

        if (shouldCancel) {
            ci.cancel();
        }
    }
}
