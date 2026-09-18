package com.example.feathers_weaponry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

record BetterCombatSwingPacket(boolean offHand, long sequence) {
    static void encode(BetterCombatSwingPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.offHand);
        buffer.writeVarLong(packet.sequence);
    }

    static BetterCombatSwingPacket decode(FriendlyByteBuf buffer) {
        return new BetterCombatSwingPacket(buffer.readBoolean(), buffer.readVarLong());
    }

    static void handle(BetterCombatSwingPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier == null) {
            return;
        }
        NetworkEvent.Context context = contextSupplier.get();
        if (context == null) {
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                FeathersWeaponry.handleBetterCombatSwing(player, packet.offHand, packet.sequence);
            }
        });
        context.setPacketHandled(true);
    }
}
