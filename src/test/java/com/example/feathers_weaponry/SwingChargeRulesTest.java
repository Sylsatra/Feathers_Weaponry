package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingChargeRulesTest {
    @Test
    void hitConsumesPendingSwingAndHitTogether() {
        SwingChargeRules.HitCharge charge = SwingChargeRules.resolveHitCharge(1.25, 0.5);

        assertEquals(1.75, charge.totalCost(), 0.000001);
        assertEquals(4, charge.wholeCost());
        assertTrue(charge.consumedPendingSwing());
    }

    @Test
    void hitWithoutPendingSwingChargesOnlyHitCost() {
        SwingChargeRules.HitCharge charge = SwingChargeRules.resolveHitCharge(1.25, 0.0);

        assertEquals(1.25, charge.totalCost(), 0.000001);
        assertEquals(3, charge.wholeCost());
        assertFalse(charge.consumedPendingSwing());
    }

    @Test
    void missChargesPendingSwingAfterDelay() {
        assertFalse(SwingChargeRules.shouldChargeMiss(25, 20, 6));
        assertTrue(SwingChargeRules.shouldChargeMiss(27, 20, 6));
    }

    @Test
    void hitWithZeroCostsChargesZero() {
        SwingChargeRules.HitCharge charge = SwingChargeRules.resolveHitCharge(0.0, 0.0);

        assertEquals(0.0, charge.totalCost(), 0.000001);
        assertEquals(0, charge.wholeCost());
        assertFalse(charge.consumedPendingSwing());
    }
}
