package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatherBudgetRulesTest {
    @Test
    void visibleFeatherAmountsConvertToHalfFeatherUnits() {
        assertEquals(0, FeatherBudgetRules.toHalfFeatherUnits(0.0));
        assertEquals(1, FeatherBudgetRules.toHalfFeatherUnits(0.5));
        assertEquals(2, FeatherBudgetRules.toHalfFeatherUnits(1.0));
        assertEquals(3, FeatherBudgetRules.toHalfFeatherUnits(1.25));
    }

    @Test
    void minimumUsesVisibleFeatherUnits() {
        assertTrue(FeatherBudgetRules.meetsMinimum(1, 0.5));
        assertFalse(FeatherBudgetRules.meetsMinimum(0, 0.5));
        assertTrue(FeatherBudgetRules.meetsMinimum(2, 1.0));
        assertFalse(FeatherBudgetRules.meetsMinimum(1, 1.0));
    }

    @Test
    void zeroWeightAllowsSpendingLastHalfFeather() {
        assertTrue(FeatherBudgetRules.canAfford(1, 0, 0, 1));
    }

    @Test
    void weightReservePreventsSpendingBelowReserve() {
        assertTrue(FeatherBudgetRules.canAfford(3, 0, 2, 1));
        assertFalse(FeatherBudgetRules.canAfford(2, 0, 2, 1));
    }

    @Test
    void enduranceCanPayBeforeVisibleFeathers() {
        assertTrue(FeatherBudgetRules.canAfford(2, 1, 1, 2));
        assertFalse(FeatherBudgetRules.canAfford(2, 0, 1, 2));
    }

    @Test
    void exhaustionIncludesConfiguredMinimumAndWeightReserve() {
        assertFalse(FeatherBudgetRules.isExhausted(2, 0, 2, 0.0));
        assertFalse(FeatherBudgetRules.isExhausted(0, 0, 0, 0.0));
        assertTrue(FeatherBudgetRules.isExhausted(2, 0, 2, 0.5));
        assertTrue(FeatherBudgetRules.isExhausted(1, 0, 0, 1.0));
        assertFalse(FeatherBudgetRules.isExhausted(3, 0, 2, 0.5));
        assertFalse(FeatherBudgetRules.isExhausted(2, 1, 2, 0.5));
    }

    @Test
    void actionStartRequiresMinimumAndEnoughBudgetForItsCost() {
        assertTrue(FeatherBudgetRules.canStartAction(6, 0, 2, 1.0, 4));
        assertFalse(FeatherBudgetRules.canStartAction(5, 0, 2, 1.0, 4));
        assertFalse(FeatherBudgetRules.canStartAction(1, 5, 0, 1.0, 1));
        assertTrue(FeatherBudgetRules.canStartAction(0, 0, 0, 0.0, 0));
        assertTrue(FeatherBudgetRules.canStartAction(2, 0, 2, 0.0, 0));
    }

    @Test
    void fractionalVisibleCostsAccumulateIntoHalfFeatherSpend() {
        FeatherBudgetRules.FractionalSpend first = FeatherBudgetRules.accumulateVisibleCost(0.0, 0.45);
        assertEquals(0, first.halfFeatherCost());
        assertEquals(0.45, first.remainingVisibleCost(), 0.000001);

        FeatherBudgetRules.FractionalSpend second = FeatherBudgetRules.accumulateVisibleCost(first.remainingVisibleCost(), 0.10);
        assertEquals(1, second.halfFeatherCost());
        assertEquals(0.05, second.remainingVisibleCost(), 0.000001);

        FeatherBudgetRules.FractionalSpend third = FeatherBudgetRules.accumulateVisibleCost(0.0, 1.10);
        assertEquals(2, third.halfFeatherCost());
        assertEquals(0.10, third.remainingVisibleCost(), 0.000001);
    }
}
