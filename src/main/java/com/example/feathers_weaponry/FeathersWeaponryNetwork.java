package com.example.feathers_weaponry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

final class FeathersWeaponryNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel channel;

    private FeathersWeaponryNetwork() {
    }

    static void register() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("feathersweaponry", "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals);

        channel.registerMessage(
                0,
                BetterCombatSwingPacket.class,
                BetterCombatSwingPacket::encode,
                BetterCombatSwingPacket::decode,
                BetterCombatSwingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    static void sendBetterCombatSwing(boolean offHand, long sequence) {
        if (channel != null) {
            channel.sendToServer(new BetterCombatSwingPacket(offHand, sequence));
        }
    }
}
