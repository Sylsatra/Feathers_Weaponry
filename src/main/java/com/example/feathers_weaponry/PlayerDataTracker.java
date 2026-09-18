package com.example.feathers_weaponry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataTracker {
    private static final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    public static PlayerData getOrCreate(UUID playerId) {
        if (playerId == null) {
            return new PlayerData();
        }
        return playerDataMap.computeIfAbsent(playerId, id -> new PlayerData());
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            playerDataMap.remove(playerId);
        }
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
        public long lastLowFeatherPenaltyTime = 0;


        public double featherAccumulator = 0.0;


        public double lastFlyYaw = 0.0;
        public double lastFlyPitch = 0.0;
        public double lastFlyRoll = 0.0;
        public boolean isFirstFlyTick = true;
        public double pendingSwingCost = 0.0;
        public double pendingSwingMinimum = 0.0;
        public long pendingSwingTick = 0;
        public long pendingSwingSequence = Long.MIN_VALUE;
        public long lastSwingTick = 0;
        public long lastBlockInteractionTick = Long.MIN_VALUE;
        public long crossbowInaccuracyUntilTick = 0;
        public double crossbowInaccuracyDegrees = 0.0;
        public long lastBetterCombatSwingSequence = Long.MIN_VALUE;
        public long lastBetterCombatSwingTick = 0;
        public long regenBlockedUntilTick = 0;
        public int regenHoldFeathers = -1;
        public long sprintExhaustedCooldownUntil = 0;

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
            lastLowFeatherPenaltyTime = 0;
            featherAccumulator = 0.0;
            lastFlyYaw = 0.0;
            lastFlyPitch = 0.0;
            lastFlyRoll = 0.0;
            isFirstFlyTick = true;
            pendingSwingCost = 0.0;
            pendingSwingMinimum = 0.0;
            pendingSwingTick = 0;
            pendingSwingSequence = Long.MIN_VALUE;
            lastSwingTick = 0;
            lastBlockInteractionTick = Long.MIN_VALUE;
            crossbowInaccuracyUntilTick = 0;
            crossbowInaccuracyDegrees = 0.0;
            lastBetterCombatSwingSequence = Long.MIN_VALUE;
            lastBetterCombatSwingTick = 0;
            regenBlockedUntilTick = 0;
            regenHoldFeathers = -1;
            sprintExhaustedCooldownUntil = 0;
        }
    }
}
