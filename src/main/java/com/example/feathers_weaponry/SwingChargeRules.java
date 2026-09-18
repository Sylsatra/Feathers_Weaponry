package com.example.feathers_weaponry;

final class SwingChargeRules {
    private SwingChargeRules() {
    }

    record HitCharge(double totalCost, int wholeCost, boolean consumedPendingSwing) {
    }

    static HitCharge resolveHitCharge(double hitCost, double pendingSwingCost) {
        double total = Math.max(0.0, hitCost) + Math.max(0.0, pendingSwingCost);
        return new HitCharge(total, FeatherBudgetRules.toHalfFeatherUnits(total), pendingSwingCost > 0.0);
    }

    static boolean shouldChargeMiss(long currentTick, long swingTick, int delayTicks) {
        return currentTick > swingTick + delayTicks;
    }
}
