package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAttackGateTest {
    @Test
    void cancelsValidWeaponAttackBelowLeftClickThresholdWhenAnimationsAreCanceled() {
        assertTrue(ClientAttackGate.shouldCancelAttack(0, 0, 0, 0.5, 1, true, true));
    }

    @Test
    void allowsAttackAtOrAboveLeftClickThreshold() {
        assertFalse(ClientAttackGate.shouldCancelAttack(2, 0, 0, 0.5, 1, true, true));
    }

    @Test
    void allowsAttackForNonWeaponOrWhenAnimationCancelIsDisabled() {
        assertFalse(ClientAttackGate.shouldCancelAttack(0, 0, 0, 0.5, 1, false, true));
        assertFalse(ClientAttackGate.shouldCancelAttack(0, 0, 0, 0.5, 1, true, false));
    }

    @Test
    void cancelsRightClickUseBelowThresholdUnlessAllowedProjectileFallbackApplies() {
        assertTrue(ClientAttackGate.shouldCancelUse(0, 0, 0, 1.0, 2, true, true, false));
        assertFalse(ClientAttackGate.shouldCancelUse(0, 0, 0, 1.0, 2, true, true, true));
        assertTrue(ClientAttackGate.shouldCancelUse(1, 0, 0, 1.0, 2, true, true, false));
        assertFalse(ClientAttackGate.shouldCancelUse(4, 0, 0, 1.0, 2, true, true, false));
    }

    @Test
    void cancelsUseWhenOffhandItemIsRestrictedBelowThreshold() {
        assertTrue(ClientAttackGate.shouldCancelEitherHandUse(
                0,
                0,
                0,
                1.0,
                0,
                false,
                false,
                2,
                true,
                false,
                true));
    }

    @Test
    void movementStopsAtWeightReserveOrBelowConfiguredMinimum() {
        assertFalse(ClientAttackGate.canContinueMovement(2, 0, 2, 0.5));
        assertTrue(ClientAttackGate.canContinueMovement(2, 0, 2, 0.0));
        assertTrue(ClientAttackGate.canContinueMovement(0, 0, 0, 0.0));
        assertFalse(ClientAttackGate.canContinueMovement(1, 0, 0, 1.0));
        assertTrue(ClientAttackGate.canContinueMovement(2, 1, 2, 0.5));
    }

    @Test
    void calculatesCooldownTicksCorrectly() {
        assertEquals(60L, ClientAttackGate.calculateCooldownTicks(3.0));
        assertEquals(0L, ClientAttackGate.calculateCooldownTicks(0.0));
        assertEquals(0L, ClientAttackGate.calculateCooldownTicks(-1.0));
    }

    @Test
    void sprintOrSwimGatedByCooldownEvenWithFeathers() {
        assertTrue(ClientAttackGate.canSprintOrSwim(4, 0, 0, 0.0, 100L, 50L));
        assertFalse(ClientAttackGate.canSprintOrSwim(4, 0, 0, 0.0, 100L, 120L));
        assertFalse(ClientAttackGate.canSprintOrSwim(0, 0, 0, 0.5, 100L, 50L));
        assertTrue(ClientAttackGate.canSprintOrSwim(0, 0, 0, 0.0, 100L, 50L));
    }

    @Test
    void jumpGatedByThresholdAndCost() {
        assertFalse(ClientAttackGate.canJump(0, 0, 0, 0.5, 0.5));
        assertFalse(ClientAttackGate.canJump(0, 0, 0, 0.0, 0.5));
        assertTrue(ClientAttackGate.canJump(0, 0, 0, 0.0, 0.0));
        assertTrue(ClientAttackGate.canJump(2, 0, 0, 0.5, 0.5));
        assertFalse(ClientAttackGate.canJump(2, 0, 2, 0.5, 0.5));
    }
}
