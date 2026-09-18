package com.example.feathers_weaponry;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterCombatSwingPacketTest {
    @Test
    void packetRoundTripsHandAndSequence() {
        BetterCombatSwingPacket packet = new BetterCombatSwingPacket(true, 42L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        BetterCombatSwingPacket.encode(packet, buffer);
        BetterCombatSwingPacket decoded = BetterCombatSwingPacket.decode(buffer);

        assertTrue(decoded.offHand());
        assertEquals(42L, decoded.sequence());
    }
}
