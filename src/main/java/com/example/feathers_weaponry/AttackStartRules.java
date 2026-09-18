package com.example.feathers_weaponry;

final class AttackStartRules {
    private AttackStartRules() {
    }

    static boolean shouldCancelAttackStart(
            boolean validWeapon,
            int feathers,
            int endurance,
            int weight,
            double minimumFeathers,
            int cost,
            boolean cancelAnimations) {
        return cancelAnimations && validWeapon
                && !FeatherBudgetRules.canStartAction(feathers, endurance, weight, minimumFeathers, cost);
    }
}
