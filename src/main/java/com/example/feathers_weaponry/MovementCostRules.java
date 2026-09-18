package com.example.feathers_weaponry;

final class MovementCostRules {
    private MovementCostRules() {
    }

    static double adjustedCost(double costPerTick, double learningMultiplier, boolean activeGlider) {
        return activeGlider ? costPerTick : costPerTick * learningMultiplier;
    }

    static boolean isCooldownActive(long currentTick, long cooldownUntilTick) {
        return currentTick < cooldownUntilTick;
    }

    static boolean canStartActivity(int feathers, int endurance, int weight, double minimumFeathers) {
        return !FeatherBudgetRules.isExhausted(feathers, endurance, weight, minimumFeathers);
    }

    static boolean canStartActivity(
            int feathers,
            int endurance,
            int weight,
            double minimumFeathers,
            long currentTick,
            long cooldownUntilTick) {
        return !isCooldownActive(currentTick, cooldownUntilTick)
                && canStartActivity(feathers, endurance, weight, minimumFeathers);
    }

    static boolean canStartActivity(
            String activity,
            int feathers,
            int endurance,
            int weight,
            double minimumFeathers,
            long currentTick,
            long cooldownUntilTick) {
        boolean requiresCooldown = "running".equals(activity) || "swimming".equals(activity);
        return (!requiresCooldown || !isCooldownActive(currentTick, cooldownUntilTick))
                && canStartActivity(feathers, endurance, weight, minimumFeathers);
    }

    static double restrictedVerticalVelocity(String activity, double currentY) {
        if ("flying".equals(activity)) {
            return Math.min(currentY, -0.35);
        }
        if ("swimming".equals(activity)) {
            return currentY * 0.2;
        }
        return currentY;
    }

}
