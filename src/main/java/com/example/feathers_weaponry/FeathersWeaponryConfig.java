package com.example.feathers_weaponry;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class FeathersWeaponryConfig {
    public static final ForgeConfigSpec CONFIG;

    public static final ForgeConfigSpec.IntValue drawTimeThreshold;
    public static final ForgeConfigSpec.IntValue bubbleThreshold;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> validBowItems;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> validWeaponItems;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Feathers Weaponry Configuration").push("general");

        builder.comment(
            "Time in ticks required for drawing a bow, crossbow, or trident before deducting feathers.\n" +
            "Format: defineInRange(key, default, min, max)\n" +
            "Default value: 15 ticks; Minimum: 1 tick; Maximum: 100 ticks.\n" +
            "Increase this value if you want players to have to draw longer before feathers are deducted."
        );
        drawTimeThreshold = builder.defineInRange("drawTimeThreshold", 15, 1, 100);

        builder.comment(
            "Air (bubble) threshold. If the player's air is below this value, feather deductions are multiplied by 2.\n" +
            "Format: defineInRange(key, default, min, max)\n" +
            "Default value: 4; Minimum: 0; Maximum: 20.\n" +
            "Set this value to control when low-air penalties kick in."
        );
        bubbleThreshold = builder.defineInRange("bubbleThreshold", 4, 0, 20);

        builder.comment(
            "List of valid item IDs for bow-like items. These items trigger the draw deduction when used.\n" +
            "Examples: minecraft:bow, minecraft:crossbow, minecraft:trident.\n" +
            "You can add or remove items as needed."
        );
        validBowItems = builder.define("validBowItems", Arrays.asList(
            "minecraft:bow",
            "minecraft:crossbow",
            "minecraft:trident"
        ));

        builder.comment(
            "List of valid item IDs for weapon-based actions. These items trigger feather deductions when used to attack.\n" +
            "You can include items for both main-hand and off-hand usage if desired."
        );
        validWeaponItems = builder.define("validWeaponItems", Arrays.asList(
            "minecraft:iron_sword",
            "minecraft:stone_sword",
            "minecraft:wooden_sword",
            "minecraft:golden_sword",
            "minecraft:diamond_sword",
            "minecraft:netherite_sword",
            "minecraft:iron_axe",
            "minecraft:stone_axe",
            "minecraft:wooden_axe",
            "minecraft:golden_axe",
            "minecraft:diamond_axe",
            "minecraft:netherite_axe",
            "minecraft:trident"
        ));

        builder.pop();
        CONFIG = builder.build();
    }
}
