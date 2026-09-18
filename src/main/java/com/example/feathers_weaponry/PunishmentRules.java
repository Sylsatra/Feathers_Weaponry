package com.example.feathers_weaponry;

import java.util.ArrayList;
import java.util.List;

final class PunishmentRules {
    private PunishmentRules() {
    }

    enum Severity {
        MINOR,
        MODERATE,
        SEVERE
    }

    record EffectRule(String effectId, int durationTicks, int amplifier) {
    }

    static boolean isBelowThreshold(int feathers, double threshold) {
        return !FeatherBudgetRules.meetsMinimum(feathers, threshold);
    }

    static Severity severityForDamage(double damage) {
        if (damage <= 2.0) {
            return Severity.MINOR;
        }
        if (damage <= 4.0) {
            return Severity.MODERATE;
        }
        return Severity.SEVERE;
    }

    static List<EffectRule> parseEffects(List<? extends String> entries) {
        List<EffectRule> rules = new ArrayList<>();
        if (entries == null) {
            return rules;
        }

        for (String entry : entries) {
            EffectRule rule = parseEffect(entry);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private static EffectRule parseEffect(String entry) {
        if (entry == null) {
            return null;
        }

        String[] parts = entry.trim().split(",");
        if (parts.length != 3) {
            return null;
        }

        String effectId = parts[0].trim();
        if (!effectId.contains(":")) {
            return null;
        }

        try {
            int durationTicks = Integer.parseInt(parts[1].trim());
            int amplifier = Integer.parseInt(parts[2].trim());
            if (durationTicks <= 0 || amplifier < -1) {
                return null;
            }
            return new EffectRule(effectId, durationTicks, amplifier);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
