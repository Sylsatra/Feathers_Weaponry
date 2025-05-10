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
            "Default value: 15 ticks; Minimum: 1 tick; Maximum: 100 ticks."
        );
        drawTimeThreshold = builder.defineInRange("drawTimeThreshold", 15, 1, 100);

        builder.comment(
            "Air (bubble) threshold. If the player's air is below this value, feather deductions are multiplied by 2.\n" +
            "Default value: 4; Minimum: 0; Maximum: 20."
        );
        bubbleThreshold = builder.defineInRange("bubbleThreshold", 4, 0, 20);

        builder.comment(
            "List of valid item IDs for bow-like items. These items trigger the draw deduction when used."
        );
        validBowItems = builder.defineList("validBowItems", 
            () -> Arrays.asList("minecraft:bow", "minecraft:crossbow", "minecraft:trident"),
            object -> object instanceof String
        );

        builder.comment(
            "List of valid item IDs for weapon-based actions. These items trigger feather deductions when used to attack."
        );
        validWeaponItems = builder.defineList("validWeaponItems",
            () -> Arrays.asList(
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
            ),
            object -> object instanceof String
        );

        builder.pop();
        CONFIG = builder.build();
    }
}
