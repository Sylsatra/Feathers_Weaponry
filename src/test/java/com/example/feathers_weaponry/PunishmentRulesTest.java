package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentRulesTest {
    @Test
    void exhaustionUsesConfiguredThreshold() {
        assertTrue(PunishmentRules.isBelowThreshold(0, 0.5));
        assertFalse(PunishmentRules.isBelowThreshold(1, 0.5));
        assertTrue(PunishmentRules.isBelowThreshold(1, 2.0));
    }

    @Test
    void rotatorCuffSeverityFollowsDamageBands() {
        assertEquals(PunishmentRules.Severity.MINOR, PunishmentRules.severityForDamage(2.0));
        assertEquals(PunishmentRules.Severity.MODERATE, PunishmentRules.severityForDamage(3.5));
        assertEquals(PunishmentRules.Severity.SEVERE, PunishmentRules.severityForDamage(5.0));
    }

    @Test
    void effectRulesParseConfiguredEntriesAndSkipInvalidEntries() {
        List<PunishmentRules.EffectRule> rules = PunishmentRules.parseEffects(List.of(
                "minecraft:weakness,60,1",
                "minecraft:mining_fatigue,30,0",
                "bad",
                "minecraft:speed,abc,0"));

        assertEquals(2, rules.size());
        assertEquals("minecraft:weakness", rules.get(0).effectId());
        assertEquals(60, rules.get(0).durationTicks());
        assertEquals(1, rules.get(0).amplifier());
        assertEquals("minecraft:mining_fatigue", rules.get(1).effectId());
    }

    @Test
    void effectRulesAllowNegativeOneAmplifierForActivityCompatibility() {
        List<PunishmentRules.EffectRule> rules = PunishmentRules.parseEffects(List.of("minecraft:levitation,60,-1"));

        assertEquals(1, rules.size());
        assertEquals(-1, rules.get(0).amplifier());
    }
}
