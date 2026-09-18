package com.example.feathers_weaponry;

import com.elenai.feathers.api.FeathersHelper;
import com.elenai.feathers.client.ClientFeathersData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ClientMovementEvents {
    private static long clientSprintCooldownUntil = 0;

    private ClientMovementEvents() {
    }

    static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientMovementEvents());
    }

    public static boolean canLocalPlayerSprintOrSwim(LocalPlayer player) {
        if (player == null || player.level() == null) {
            return true;
        }
        if (!FeathersWeaponryConfig.movementCostsEnabled.get()) {
            return true;
        }

        double threshold = player.isInWater() || player.isUnderWater()
                ? FeathersWeaponryConfig.swimmingMinFeathers.get()
                : FeathersWeaponryConfig.runningMinFeathers.get();

        boolean canContinue = ClientAttackGate.canContinueMovement(
                FeathersHelper.getFeathers(),
                FeathersHelper.getEndurance(),
                ClientFeathersData.getWeight(),
                threshold);

        long gameTime = player.level().getGameTime();
        if (clientSprintCooldownUntil < 0 || clientSprintCooldownUntil > gameTime + 1200) {
            clientSprintCooldownUntil = 0;
        }
        if (!canContinue) {
            long cooldownTicks = ClientAttackGate.calculateCooldownTicks(
                    FeathersWeaponryConfig.sprintExhaustionCooldownSeconds.get());
            clientSprintCooldownUntil = Math.max(clientSprintCooldownUntil, gameTime + cooldownTicks);
            return false;
        }

        return !ClientAttackGate.isCooldownActive(gameTime, clientSprintCooldownUntil);
    }

    public static boolean canLocalPlayerJump(LocalPlayer player) {
        if (player == null) {
            return true;
        }
        if (!FeathersWeaponryConfig.movementCostsEnabled.get() || player.isCreative() || player.isSpectator()) {
            return true;
        }

        return ClientAttackGate.canJump(
                FeathersHelper.getFeathers(),
                FeathersHelper.getEndurance(),
                ClientFeathersData.getWeight(),
                FeathersWeaponryConfig.jumpingMinFeathers.get(),
                FeathersWeaponryConfig.jumpingCost.get());
    }

    @SubscribeEvent
    public void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (event == null || !(event.getPlayer() instanceof LocalPlayer player)) {
            return;
        }

        if (!canLocalPlayerSprintOrSwim(player)) {
            if (event.getNewFovModifier() > 1.0F) {
                event.setNewFovModifier(1.0F);
            }
        }
    }

    @SubscribeEvent
    public void onClientPlayerLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        clientSprintCooldownUntil = 0;
    }

    @SubscribeEvent
    public void onClientPlayerClone(net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        clientSprintCooldownUntil = 0;
    }
}
