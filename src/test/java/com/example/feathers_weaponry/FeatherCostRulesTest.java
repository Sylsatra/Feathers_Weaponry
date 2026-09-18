package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatherCostRulesTest {
    @Test
    void spendRequiresFeathersAndEnduranceToStayAtOrAboveWeightReserve() {
        assertTrue(FeatherCostRules.canSpend(6, 0, 4, 2));
        assertTrue(FeatherCostRules.canSpend(5, 1, 4, 2));
        assertFalse(FeatherCostRules.canSpend(5, 0, 4, 2));
    }

    @Test
    void weightReserveIsCappedLikeFeathersApi() {
        assertTrue(FeatherCostRules.canSpend(22, 0, 30, 2));
        assertFalse(FeatherCostRules.canSpend(21, 0, 30, 2));
    }

    @Test
    void fractionalCostsSpendOnlyWholeAccumulatedFeathers() {
        FeatherCostRules.FractionalSpend first = FeatherCostRules.accumulate(0.0, 0.45);
        assertEquals(0, first.wholeCost());
        assertEquals(0.45, first.remainingAccumulator(), 0.000001);

        FeatherCostRules.FractionalSpend second = FeatherCostRules.accumulate(first.remainingAccumulator(), 0.65);
        assertEquals(1, second.wholeCost());
        assertEquals(0.10, second.remainingAccumulator(), 0.000001);
    }

    @Test
    void minimumFeatherGateUsesConfiguredFractionalThresholds() {
        assertTrue(FeatherCostRules.meetsMinimum(1, 0.5));
        assertFalse(FeatherCostRules.meetsMinimum(0, 0.5));
        assertFalse(FeatherCostRules.meetsMinimum(1, 1.0));
        assertTrue(FeatherCostRules.meetsMinimum(2, 1.0));
        assertTrue(FeatherCostRules.meetsMinimum(0, 0.0));
    }
}
