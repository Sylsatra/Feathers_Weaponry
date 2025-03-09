package com.example.feathers_weaponry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;
import com.elenai.feathers.api.FeathersHelper;

@Mod("feathersweaponry")
public class FeathersWeaponry {

    private static final Map<UUID, DrawData> activeDraws = new HashMap<>();

    public FeathersWeaponry() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FeathersWeaponryConfig.CONFIG);
        MinecraftForge.EVENT_BUS.register(new EventHandlers());
    }

    public static void subtractFeathers(Player player, int amount) {
        if (player instanceof ServerPlayer) {
            FeathersHelper.subFeathers((ServerPlayer) player, amount);
        }
    }

    public static void applyPenaltyIfLowFeathers(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        int currentFeathers = FeathersHelper.getFeathers((ServerPlayer) player);
        if (currentFeathers <= 1) {
            int amplifier = (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) ? 1 : 0;
            int duration = 60; 
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, amplifier));
        }
    }

    private static class DrawData {
        int tickCount = 0;
        int secondsDrawn = 0;
    }

    public static boolean isValidBow(ItemStack stack) {
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (registryName == null) return false;
        String id = registryName.toString();
        return FeathersWeaponryConfig.validBowItems.get().contains(id);
    }

    public static boolean isValidWeapon(ItemStack stack) {
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (registryName == null) return false;
        String id = registryName.toString();
        return FeathersWeaponryConfig.validWeaponItems.get().contains(id);
    }

    public static class EventHandlers {

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            Player player = event.player;
            if (player.level.isClientSide) return;
            UUID playerId = player.getUUID();
            if (player.isUsingItem()) {
                ItemStack itemStack = player.getUseItem();
                if (itemStack != null && isValidBow(itemStack)) {
                    DrawData data = activeDraws.getOrDefault(playerId, new DrawData());
                    data.tickCount++;
                    if (data.tickCount >= FeathersWeaponryConfig.drawTimeThreshold.get()) {
                        data.secondsDrawn++;
                        int deduction = data.secondsDrawn; 
                        if (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) {
                            deduction *= 2;
                        }
                        subtractFeathers(player, deduction);
                        applyPenaltyIfLowFeathers(player);
                        data.tickCount = 0;
                    }
                    activeDraws.put(playerId, data);
                } else {
                    activeDraws.remove(playerId);
                }
            } else {
                activeDraws.remove(playerId);
            }
        }

        @SubscribeEvent
        public void onLivingHurt(LivingHurtEvent event) {
            if (!(event.getSource().getEntity() instanceof ServerPlayer)) return;
            ServerPlayer player = (ServerPlayer) event.getSource().getEntity();
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand != null && isValidWeapon(mainHand)) {
                int deduction = 1; 
                if (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) {
                    deduction *= 2;
                }
                subtractFeathers(player, deduction);
                applyPenaltyIfLowFeathers(player);
            }
        }

        @SubscribeEvent
        public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (!(event.getEntity() instanceof Player)) return;
            Player player = (Player) event.getEntity();
            if (player.level.isClientSide) return;
            if (player instanceof ServerPlayer) {
                int feathers = FeathersHelper.getFeathers((ServerPlayer) player);
                if (feathers <= 1) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
