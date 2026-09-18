package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredItemRuleTest {
    @Test
    void legacyWeaponValueUsesGlobalSwingAndMinimumSettings() {
        ConfiguredItemRule rule = ConfiguredItemRule.parseWeaponValue("1.2", 0.25, 0.5);

        assertEquals(1.2, rule.primaryCost(), 0.000001);
        assertEquals(0.3, rule.secondaryCost(), 0.000001);
        assertEquals(0.5, rule.minimumFeathers(), 0.000001);
    }

    @Test
    void weaponValueSupportsHitSwingAndMinimumCosts() {
        ConfiguredItemRule rule = ConfiguredItemRule.parseWeaponValue("1.5,0.4,2.0", 0.2, 0.5);

        assertEquals(1.5, rule.primaryCost(), 0.000001);
        assertEquals(0.4, rule.secondaryCost(), 0.000001);
        assertEquals(2.0, rule.minimumFeathers(), 0.000001);
    }

    @Test
    void bowValueSupportsPerItemCancellationMode() {
        ConfiguredItemRule fallback = ConfiguredItemRule.parseBowValue("1.0,false", 1.0);
        ConfiguredItemRule cancel = ConfiguredItemRule.parseBowValue("1.1,true", 1.0);

        assertFalse(fallback.cancelAtMinimum());
        assertTrue(cancel.cancelAtMinimum());
        assertEquals(1.1, cancel.primaryCost(), 0.000001);
    }

    @Test
    void legacyBowValueKeepsProjectileFallbackEnabled() {
        ConfiguredItemRule rule = ConfiguredItemRule.parseBowValue("1.3", 1.0);

        assertFalse(rule.cancelAtMinimum());
    }
}
