package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementCostRulesTest {
    @Test
    void gliderCostIsNotReducedByLearningCurve() {
        assertEquals(0.0375, MovementCostRules.adjustedCost(0.0375, 0.1, true), 0.000001);
    }

    @Test
    void nonGliderMovementCostUsesLearningCurve() {
        assertEquals(0.00375, MovementCostRules.adjustedCost(0.0375, 0.1, false), 0.000001);
    }

    @Test
    void movementRequiresMinimumFeathers() {
        assertTrue(MovementCostRules.canStartActivity(2, 0, 0, 0.5));
        assertFalse(MovementCostRules.canStartActivity(0, 0, 0, 0.5));
        assertFalse(MovementCostRules.canStartActivity(2, 0, 2, 0.5));
        assertTrue(MovementCostRules.canStartActivity(2, 0, 2, 0.0));
        assertTrue(MovementCostRules.canStartActivity(0, 0, 0, 0.0));
        assertTrue(MovementCostRules.canStartActivity(2, 1, 2, 0.5));
    }

    @Test
    void deniedFlyingGetsDownwardVelocityInsteadOfGliderFloat() {
        assertEquals(-0.35, MovementCostRules.restrictedVerticalVelocity("flying", -0.05), 0.000001);
        assertEquals(-0.6, MovementCostRules.restrictedVerticalVelocity("flying", -0.6), 0.000001);
    }

    @Test
    void activityCooldownGatingAppliesOnlyToRunningAndSwimming() {
        assertFalse(MovementCostRules.canStartActivity("running", 4, 0, 0, 0.0, 100L, 150L));
        assertFalse(MovementCostRules.canStartActivity("swimming", 4, 0, 0, 0.0, 100L, 150L));
        assertTrue(MovementCostRules.canStartActivity("walking", 4, 0, 0, 0.0, 100L, 150L));
        assertTrue(MovementCostRules.canStartActivity("flying", 4, 0, 0, 0.0, 100L, 150L));
        assertTrue(MovementCostRules.canStartActivity("running", 4, 0, 0, 0.0, 150L, 100L));
    }
}
