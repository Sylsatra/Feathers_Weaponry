package com.example.feathers_weaponry;

final class FeatherCostRules {
    private FeatherCostRules() {
    }

    record FractionalSpend(int wholeCost, double remainingAccumulator) {
    }

    static boolean canSpend(int feathers, int endurance, int weight, int cost) {
        return FeatherBudgetRules.canAfford(feathers, endurance, weight, cost);
    }

    static FractionalSpend accumulate(double currentAccumulator, double amount) {
        if (amount <= 0.0) {
            return new FractionalSpend(0, Math.max(0.0, currentAccumulator));
        }

        double next = Math.max(0.0, currentAccumulator) + amount;
        int whole = (int) next;
        return new FractionalSpend(whole, next - whole);
    }

    static boolean meetsMinimum(int feathers, double minimum) {
        return FeatherBudgetRules.meetsMinimum(feathers, minimum);
    }
}
