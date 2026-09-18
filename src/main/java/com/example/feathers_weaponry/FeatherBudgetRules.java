package com.example.feathers_weaponry;

final class FeatherBudgetRules {
    private FeatherBudgetRules() {
    }

    record FractionalSpend(int halfFeatherCost, double remainingVisibleCost) {
    }

    static int toHalfFeatherUnits(double visibleFeathers) {
        if (visibleFeathers <= 0.0) {
            return 0;
        }
        return (int) Math.ceil(visibleFeathers * 2.0);
    }

    static boolean meetsMinimum(int feathers, double minimumVisibleFeathers) {
        return feathers >= toHalfFeatherUnits(minimumVisibleFeathers);
    }

    static boolean canAfford(int feathers, int endurance, int weight, int cost) {
        if (cost <= 0) {
            return true;
        }
        return Math.min(weight, 20) <= feathers + endurance - cost;
    }

    static boolean isExhausted(int feathers, int endurance, int weight, double minimumVisibleFeathers) {
        if (minimumVisibleFeathers <= 0.0) {
            return false;
        }
        return !meetsMinimum(feathers, minimumVisibleFeathers)
                || feathers + endurance <= Math.min(weight, 20);
    }

    static boolean canStartAction(
            int feathers,
            int endurance,
            int weight,
            double minimumVisibleFeathers,
            int cost) {
        return !isExhausted(feathers, endurance, weight, minimumVisibleFeathers)
                && canAfford(feathers, endurance, weight, cost);
    }

    static FractionalSpend accumulateVisibleCost(double currentVisibleAccumulator, double visibleCost) {
        if (visibleCost <= 0.0) {
            return new FractionalSpend(0, Math.max(0.0, currentVisibleAccumulator));
        }

        double next = Math.max(0.0, currentVisibleAccumulator) + visibleCost;
        int halfFeatherCost = (int) Math.floor(next * 2.0 + 0.000001);
        double remaining = next - (halfFeatherCost / 2.0);
        return new FractionalSpend(halfFeatherCost, Math.max(0.0, remaining));
    }
}
