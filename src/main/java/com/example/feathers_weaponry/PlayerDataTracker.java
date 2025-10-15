package com.example.feathers_weaponry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-player data for feather consumption and activity monitoring
 */
public class PlayerDataTracker {
    private static final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    public static PlayerData getOrCreate(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, id -> new PlayerData());
    }

    public static void remove(UUID playerId) {
        playerDataMap.remove(playerId);
    }

    public static void clear() {
        playerDataMap.clear();
    }

    public static class PlayerData {

        public int lastShieldDurability = -1;
        public long shieldRaisedTicks = 0;
        public long lastShieldCheckTick = 0;


        public double lastPosX = 0;
        public double lastPosY = 0;
        public double lastPosZ = 0;
        public long swimTicks = 0;
        public long runTicks = 0;
        public long flyTicks = 0;
        public long walkTicks = 0;
        public long lastMovementTick = 0;
        public boolean wasOnGround = true;


        public String lastExhaustionActivity = "";
        public long lastExhaustionTime = 0;
        public int consecutiveSwimTicks = 0;
        public int consecutiveRunTicks = 0;
        public int consecutiveFlyTicks = 0;
        public int consecutiveWalkTicks = 0;


        public long lastRotatorCuffTime = 0;
        public long lastSwimmerShoulderTime = 0;
        public long lastRunnerFatigueTime = 0;
        public long lastPilotStrainTime = 0;
        public long lastWalkerCrampTime = 0;


        public double featherAccumulator = 0.0;


        public double lastFlyYaw = 0.0;
        public double lastFlyPitch = 0.0;
        public double lastFlyRoll = 0.0;
        public boolean isFirstFlyTick = true;

        public void reset() {
            lastShieldDurability = -1;
            shieldRaisedTicks = 0;
            lastShieldCheckTick = 0;
            lastPosX = 0;
            lastPosY = 0;
            lastPosZ = 0;
            swimTicks = 0;
            runTicks = 0;
            flyTicks = 0;
            walkTicks = 0;
            lastMovementTick = 0;
            wasOnGround = true;
            lastExhaustionActivity = "";
            lastExhaustionTime = 0;
            consecutiveSwimTicks = 0;
            consecutiveRunTicks = 0;
            consecutiveFlyTicks = 0;
            consecutiveWalkTicks = 0;
            lastRotatorCuffTime = 0;
            lastSwimmerShoulderTime = 0;
            lastRunnerFatigueTime = 0;
            lastPilotStrainTime = 0;
            lastWalkerCrampTime = 0;
            featherAccumulator = 0.0;
            lastFlyYaw = 0.0;
            lastFlyPitch = 0.0;
            lastFlyRoll = 0.0;
            isFirstFlyTick = true;
        }
    }
}
