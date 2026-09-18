package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackStartRulesTest {
    @Test
    void unconfiguredItemsAreNotCanceledByLeftClickFeatherGate() {
        assertFalse(AttackStartRules.shouldCancelAttackStart(false, 0, 0, 0, 0.5, 1, true));
    }

    @Test
    void configuredWeaponsCancelOnlyBelowMinimumWhenAnimationCancelingIsEnabled() {
        assertTrue(AttackStartRules.shouldCancelAttackStart(true, 0, 0, 0, 0.5, 1, true));
        assertFalse(AttackStartRules.shouldCancelAttackStart(true, 3, 0, 0, 0.5, 2, true));
        assertFalse(AttackStartRules.shouldCancelAttackStart(true, 0, 0, 0, 0.5, 1, false));
    }

    @Test
    void configuredWeaponCancelsWhenCombinedCostWouldCrossWeightReserve() {
        assertTrue(AttackStartRules.shouldCancelAttackStart(true, 5, 0, 2, 0.5, 4, true));
        assertFalse(AttackStartRules.shouldCancelAttackStart(true, 6, 0, 2, 0.5, 4, true));
    }
}
