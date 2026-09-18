package com.example.feathers_weaponry;

record ConfiguredItemRule(
        double primaryCost,
        double secondaryCost,
        double minimumFeathers,
        boolean cancelAtMinimum) {

    static ConfiguredItemRule parseWeaponValue(String value, double swingMultiplier, double defaultMinimum) {
        String[] parts = split(value);
        double hitCost = parseNonNegative(parts, 0, 1.0);
        double swingCost = parts.length >= 2
                ? parseNonNegative(parts, 1, hitCost * swingMultiplier)
                : hitCost * swingMultiplier;
        double minimum = parts.length >= 3
                ? parseNonNegative(parts, 2, defaultMinimum)
                : defaultMinimum;
        return new ConfiguredItemRule(hitCost, swingCost, minimum, true);
    }

    static ConfiguredItemRule parseBowValue(String value, double defaultMinimum) {
        String[] parts = split(value);
        double drawCost = parseNonNegative(parts, 0, 1.0);
        boolean cancel = parts.length >= 2 && Boolean.parseBoolean(parts[1].trim());
        return new ConfiguredItemRule(drawCost, 0.0, defaultMinimum, cancel);
    }

    private static String[] split(String value) {
        return value == null ? new String[0] : value.split(",", -1);
    }

    private static double parseNonNegative(String[] parts, int index, double fallback) {
        if (index >= parts.length) {
            return fallback;
        }
        try {
            return Math.max(0.0, Double.parseDouble(parts[index].trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
