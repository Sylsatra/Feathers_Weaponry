package com.example.feathers_weaponry;

public final class ClientAttackGate {
    private ClientAttackGate() {
    }

    public static boolean shouldCancelAttack(
            int feathers,
            int endurance,
            int weight,
            double threshold,
            int cost,
            boolean validWeapon,
            boolean cancelAnimations) {
        return cancelAnimations && validWeapon
                && !FeatherBudgetRules.canStartAction(feathers, endurance, weight, threshold, cost);
    }

    public static boolean shouldCancelUse(
            int feathers,
            int endurance,
            int weight,
            double threshold,
            int cost,
            boolean restrictedItem,
            boolean cancelAnimations,
            boolean allowProjectileFallback) {
        return cancelAnimations && restrictedItem && !allowProjectileFallback
                && !FeatherBudgetRules.canStartAction(feathers, endurance, weight, threshold, cost);
    }

    public static boolean shouldCancelEitherHandUse(
            int feathers,
            int endurance,
            int weight,
            double threshold,
            int mainCost,
            boolean mainRestrictedItem,
            boolean mainAllowsProjectileFallback,
            int offhandCost,
            boolean offhandRestrictedItem,
            boolean offhandAllowsProjectileFallback,
            boolean cancelAnimations) {
        return shouldCancelUse(feathers, endurance, weight, threshold, mainCost, mainRestrictedItem, cancelAnimations, mainAllowsProjectileFallback)
                || shouldCancelUse(feathers, endurance, weight, threshold, offhandCost, offhandRestrictedItem, cancelAnimations, offhandAllowsProjectileFallback);
    }

    public static boolean canContinueMovement(int feathers, int endurance, int weight, double threshold) {
        return !FeatherBudgetRules.isExhausted(feathers, endurance, weight, threshold);
    }

    public static boolean isCooldownActive(long currentTick, long cooldownUntilTick) {
        return currentTick < cooldownUntilTick;
    }

    public static long calculateCooldownTicks(double seconds) {
        if (seconds <= 0.0) {
            return 0L;
        }
        return (long) Math.ceil(seconds * 20.0);
    }

    public static boolean canSprintOrSwim(
            int feathers,
            int endurance,
            int weight,
            double threshold,
            long currentTick,
            long cooldownUntilTick) {
        return !isCooldownActive(currentTick, cooldownUntilTick)
                && canContinueMovement(feathers, endurance, weight, threshold);
    }

    public static boolean canJump(int feathers, int endurance, int weight, double threshold, double cost) {
        int halfCost = FeatherBudgetRules.toHalfFeatherUnits(cost);
        return FeatherBudgetRules.canStartAction(feathers, endurance, weight, threshold, halfCost);
    }
}
