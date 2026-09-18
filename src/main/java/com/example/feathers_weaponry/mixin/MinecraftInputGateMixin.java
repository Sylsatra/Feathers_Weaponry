package com.example.feathers_weaponry.mixin;

import com.elenai.feathers.api.FeathersHelper;
import com.elenai.feathers.client.ClientFeathersData;
import com.example.feathers_weaponry.ClientAttackGate;
import com.example.feathers_weaponry.FeathersWeaponry;
import com.example.feathers_weaponry.FeathersWeaponryConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 2000)
public abstract class MinecraftInputGateMixin {
    @Inject(method = {"startAttack", "m_202354_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void feathersweaponry$cancelAttackBelowFeatherLimit(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        boolean shouldCancel = ClientAttackGate.shouldCancelAttack(
                FeathersHelper.getFeathers(),
                FeathersHelper.getEndurance(),
                ClientFeathersData.getWeight(),
                FeathersWeaponry.clientWeaponMinimum(stack),
                FeathersWeaponry.clientWeaponStartCost(stack),
                FeathersWeaponry.isClientRestrictedWeapon(stack),
                FeathersWeaponryConfig.cancelAnimations.get());

        if (shouldCancel) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = {"startUseItem", "m_91277_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void feathersweaponry$cancelUseBelowFeatherLimit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack mainStack = player.getMainHandItem();
        ItemStack offhandStack = player.getOffhandItem();
        int feathers = FeathersHelper.getFeathers();
        double threshold = FeathersWeaponryConfig.rightClickMinFeathers.get();
        boolean mainRestricted = FeathersWeaponry.isClientRestrictedUse(mainStack);
        boolean mainFallback = FeathersWeaponry.allowsClientProjectileFallback(mainStack);
        boolean offhandRestricted = FeathersWeaponry.isClientRestrictedUse(offhandStack);
        boolean offhandFallback = FeathersWeaponry.allowsClientProjectileFallback(offhandStack);
        boolean shouldCancel = ClientAttackGate.shouldCancelEitherHandUse(
                feathers,
                FeathersHelper.getEndurance(),
                ClientFeathersData.getWeight(),
                threshold,
                FeathersWeaponry.clientUseStartCost(mainStack),
                mainRestricted,
                mainFallback,
                FeathersWeaponry.clientUseStartCost(offhandStack),
                offhandRestricted,
                offhandFallback,
                FeathersWeaponryConfig.cancelAnimations.get());

        if (shouldCancel) {
            ci.cancel();
        }
    }
}
