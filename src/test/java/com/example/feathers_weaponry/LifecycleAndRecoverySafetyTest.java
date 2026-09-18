package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleAndRecoverySafetyTest {

    @Test
    void testPlayerDataTrackerNullSafety() {
        assertNotNull(PlayerDataTracker.getOrCreate(null));
        PlayerDataTracker.remove(null);
    }

    @Test
    void testPlayerDataReset() {
        UUID uuid = UUID.randomUUID();
        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(uuid);
        assertNotNull(data);

        data.featherAccumulator = 0.85;
        data.lastPosX = 120.5;
        data.lastPosY = 64.0;
        data.lastPosZ = -300.2;
        data.regenBlockedUntilTick = 1000L;
        data.isFirstFlyTick = false;

        data.reset();

        assertEquals(0.0, data.featherAccumulator);
        assertEquals(0.0, data.lastPosX);
        assertEquals(0.0, data.lastPosY);
        assertEquals(0.0, data.lastPosZ);
        assertEquals(0L, data.regenBlockedUntilTick);
        assertTrue(data.isFirstFlyTick);

        PlayerDataTracker.remove(uuid);
    }

    @Test
    void testConcurrentTrackerAccess() throws InterruptedException {
        int threads = 8;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        UUID uuid = new UUID(0, i % 20);
                        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(uuid);
                        if (data == null) {
                            failed.set(true);
                        }
                        if (i % 10 == 0) {
                            PlayerDataTracker.remove(uuid);
                        }
                    }
                } catch (Throwable t1) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(finished);
        assertFalse(failed.get());
    }

    @Test
    void testGlidersCompatNullSafety() {
        assertFalse(GlidersCompat.isActivelyGliding(null));
        GlidersCompat.forceStopGliding(null);
    }

    @Test
    void testBetterCombatSwingPacketNullContext() {
        BetterCombatSwingPacket packet = new BetterCombatSwingPacket(false, 1L);
        BetterCombatSwingPacket.handle(packet, null);
        BetterCombatSwingPacket.handle(packet, () -> null);
    }

    @Test
    void testCrossbowProjectileSpreadNullMotion() {
        assertNotNull(CrossbowProjectileSpread.rotate(null, 1.0, 1.0));
    }
}
