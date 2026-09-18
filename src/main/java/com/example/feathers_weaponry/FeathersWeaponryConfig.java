package com.example.feathers_weaponry;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class FeathersWeaponryConfig {
    public static final ForgeConfigSpec CONFIG;


    public static final ForgeConfigSpec.IntValue configVersion;


    public static final ForgeConfigSpec.IntValue drawTimeThreshold;
    public static final ForgeConfigSpec.IntValue bubbleThreshold;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> validBowItems;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> validWeaponItems;


    public static final ForgeConfigSpec.BooleanValue rotatorCuffEnabled;
    public static final ForgeConfigSpec.DoubleValue rotatorCuffMinDamage;
    public static final ForgeConfigSpec.DoubleValue rotatorCuffMaxDamage;
    public static final ForgeConfigSpec.BooleanValue rotatorCuffHardModeOnly;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> lowFeatherPenaltyEffects;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> rotatorCuffMinorEffects;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> rotatorCuffModerateEffects;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> rotatorCuffSevereEffects;


    public static final ForgeConfigSpec.BooleanValue shieldOvertimeEnabled;
    public static final ForgeConfigSpec.DoubleValue shieldOvertimeCost;
    public static final ForgeConfigSpec.BooleanValue shieldDamageEnabled;
    public static final ForgeConfigSpec.DoubleValue shieldDamageFactor;


    public static final ForgeConfigSpec.IntValue rightClickMinFeathers;
    public static final ForgeConfigSpec.DoubleValue leftClickMinFeathers;
    public static final ForgeConfigSpec.BooleanValue cancelAnimations;


    public static final ForgeConfigSpec.BooleanValue movementCostsEnabled;
    public static final ForgeConfigSpec.DoubleValue swimmingCostPerSecond;
    public static final ForgeConfigSpec.DoubleValue runningCostPerSecond;
    public static final ForgeConfigSpec.DoubleValue flyingCostPerSecond;
    public static final ForgeConfigSpec.DoubleValue gliderCostPerSecond;
    public static final ForgeConfigSpec.DoubleValue walkingCostPerSecond;
    public static final ForgeConfigSpec.DoubleValue jumpingCost;
    public static final ForgeConfigSpec.DoubleValue swimmingMinFeathers;
    public static final ForgeConfigSpec.DoubleValue runningMinFeathers;
    public static final ForgeConfigSpec.DoubleValue flyingMinFeathers;
    public static final ForgeConfigSpec.DoubleValue gliderMinFeathers;
    public static final ForgeConfigSpec.DoubleValue walkingMinFeathers;
    public static final ForgeConfigSpec.DoubleValue jumpingMinFeathers;
    public static final ForgeConfigSpec.IntValue regenDelayAfterCostTicks;
    public static final ForgeConfigSpec.DoubleValue sprintExhaustionCooldownSeconds;


    public static final ForgeConfigSpec.BooleanValue activityPunishmentsEnabled;
    public static final ForgeConfigSpec.BooleanValue activityPunishmentsHardModeOnly;
    public static final ForgeConfigSpec.DoubleValue swimmerShoulderMinDamage;
    public static final ForgeConfigSpec.DoubleValue swimmerShoulderMaxDamage;
    public static final ForgeConfigSpec.DoubleValue runnerFatigueMinDamage;
    public static final ForgeConfigSpec.DoubleValue runnerFatigueMaxDamage;
    public static final ForgeConfigSpec.DoubleValue pilotStrainMinDamage;
    public static final ForgeConfigSpec.DoubleValue pilotStrainMaxDamage;
    public static final ForgeConfigSpec.DoubleValue walkerCrampDamage;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> swimmerShoulderEffects;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> runnerFatigueEffects;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> pilotStrainEffects;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> walkerCrampEffects;


    public static final ForgeConfigSpec.BooleanValue learningCurveEnabled;
    public static final ForgeConfigSpec.DoubleValue learningCurveMinMultiplier;
    public static final ForgeConfigSpec.IntValue learningCurveMaxStat;
    public static final ForgeConfigSpec.DoubleValue learningCurveRate;
    
    
    public static final ForgeConfigSpec.BooleanValue meleeSwingEnabled;
    public static final ForgeConfigSpec.DoubleValue meleeSwingMultiplier;
    public static final ForgeConfigSpec.BooleanValue meleeSwingTriggersPunishment;
    public static final ForgeConfigSpec.IntValue meleeSwingMissChargeDelayTicks;


    public static final ForgeConfigSpec.BooleanValue velocityReductionEnabled;
    public static final ForgeConfigSpec.DoubleValue lowFeatherVelocityMultiplier;
    public static final ForgeConfigSpec.BooleanValue crossbowResetOnFail;
    public static final ForgeConfigSpec.BooleanValue lowFeatherInaccuracyEnabled;
    public static final ForgeConfigSpec.DoubleValue lowFeatherInaccuracyDegrees;


    public static final ForgeConfigSpec.BooleanValue flyingManeuverabilityEnabled;
    public static final ForgeConfigSpec.DoubleValue flyingManeuverabilityMultiplier;
    public static final ForgeConfigSpec.DoubleValue flyingManeuverabilityThreshold;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Feathers Weaponry Configuration v1.0.2").push("general");

        builder.comment("Config version for migration purposes. Do not modify manually.");
        configVersion = builder.defineInRange("configVersion", 3, 1, 100);

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
            "Bow-like items as item_id=drawCost,cancelAtMinimum. Legacy item_id=drawCost entries allow projectile fallback."
        );
        validBowItems = builder.defineList("validBowItems", 
            () -> Arrays.asList(
                "minecraft:bow=1.0,false",
                "minecraft:crossbow=1.1,true",
                "minecraft:trident=1.3,false"
            ),
            object -> object instanceof String
        );

        builder.comment(
            "Weapons as item_id=hitCost,swingCost,minFeatherRequirement. Legacy item_id=hitCost entries use the global swing multiplier and minimum."
        );
        validWeaponItems = builder.defineList("validWeaponItems",
            () -> Arrays.asList(
                "minecraft:wooden_sword=0.8",
                "minecraft:stone_sword=0.9",
                "minecraft:iron_sword=1.0",
                "minecraft:golden_sword=1.1",
                "minecraft:diamond_sword=0.9",
                "minecraft:netherite_sword=1.2",
                "minecraft:wooden_axe=0.9",
                "minecraft:stone_axe=1.0",
                "minecraft:iron_axe=1.1",
                "minecraft:golden_axe=1.2",
                "minecraft:diamond_axe=0.9",
                "minecraft:netherite_axe=1.3",
                "minecraft:trident=1.3"
            ),
            object -> object instanceof String
        );

        builder.pop();

        builder.comment("Melee Swing Cost - Deduct feathers for swinging melee weapons, even on a miss").push("meleeSwing");
        
        builder.comment("Enable feather costs for swinging melee weapons");
        meleeSwingEnabled = builder.define("meleeSwingEnabled", true);
        
        builder.comment("Percentage of weapon hit cost factor to charge on a swing (0.2 = 20%)");
        meleeSwingMultiplier = builder.defineInRange("meleeSwingMultiplier", 0.2, 0.0, 1.0);
        
        builder.comment("Whether swinging and missing can trigger existing exhaustion punishments (e.g., Rotator Cuff Tears)");
        meleeSwingTriggersPunishment = builder.define("meleeSwingTriggersPunishment", true);

        builder.comment("Ticks after a melee swing starts before a miss consumes swing feathers");
        meleeSwingMissChargeDelayTicks = builder.defineInRange("meleeSwingMissChargeDelayTicks", 8, 0, 40);

        builder.pop();

        builder.comment("Rotator Cuff Tears - Random punishment when exhausted from item usage").push("punishment");
        
        builder.comment("Enable Rotator Cuff Tears punishment system");
        rotatorCuffEnabled = builder.define("rotatorCuffEnabled", true);
        
        builder.comment("Minimum damage dealt by Rotator Cuff Tears (in half-hearts, 1.0 = 0.5 hearts)");
        rotatorCuffMinDamage = builder.defineInRange("rotatorCuffMinDamage", 1.0, 0.0, 20.0);
        
        builder.comment("Maximum damage dealt by Rotator Cuff Tears (in half-hearts, 6.0 = 3 hearts)");
        rotatorCuffMaxDamage = builder.defineInRange("rotatorCuffMaxDamage", 6.0, 0.0, 20.0);
        
        builder.comment("Only apply Rotator Cuff Tears on Hard and Hardcore difficulty");
        rotatorCuffHardModeOnly = builder.define("rotatorCuffHardModeOnly", true);

        builder.comment("Status effects applied by the generic low-feather penalty. Format: effect_id,duration_ticks,amplifier. Empty list disables these effects.");
        lowFeatherPenaltyEffects = builder.defineList("lowFeatherPenaltyEffects",
            () -> Arrays.asList("minecraft:weakness,60,0", "minecraft:mining_fatigue,60,0"),
            object -> object instanceof String
        );

        builder.comment("Rotator Cuff effects for minor damage rolls. Format: effect_id,duration_ticks,amplifier. Empty list disables effects for this severity.");
        rotatorCuffMinorEffects = builder.defineList("rotatorCuffMinorEffects",
            () -> Arrays.asList("minecraft:mining_fatigue,30,0"),
            object -> object instanceof String
        );

        builder.comment("Rotator Cuff effects for moderate damage rolls. Format: effect_id,duration_ticks,amplifier. Empty list disables effects for this severity.");
        rotatorCuffModerateEffects = builder.defineList("rotatorCuffModerateEffects",
            () -> Arrays.asList("minecraft:mining_fatigue,60,1"),
            object -> object instanceof String
        );

        builder.comment("Rotator Cuff effects for severe damage rolls. Format: effect_id,duration_ticks,amplifier. Empty list disables effects for this severity.");
        rotatorCuffSevereEffects = builder.defineList("rotatorCuffSevereEffects",
            () -> Arrays.asList("minecraft:mining_fatigue,90,2"),
            object -> object instanceof String
        );
        
        builder.pop();


        builder.comment("Shield feather consumption settings").push("shield");
        
        builder.comment("Enable overtime shield cost (feathers consumed while shield is raised)");
        shieldOvertimeEnabled = builder.define("shieldOvertimeEnabled", true);
        
        builder.comment("Feathers consumed per second while shield is raised");
        shieldOvertimeCost = builder.defineInRange("shieldOvertimeCost", 0.25, 0.0, 10.0);
        
        builder.comment("Enable damage-based shield cost (feathers consumed based on damage blocked)");
        shieldDamageEnabled = builder.define("shieldDamageEnabled", true);
        
        builder.comment("Multiplier for damage-blocked feather cost (feathers = damage_blocked * factor)");
        shieldDamageFactor = builder.defineInRange("shieldDamageFactor", 0.05, 0.0, 5.0);
        
        builder.pop();


        builder.comment("Item usage restrictions based on feather levels").push("restrictions");
        
        builder.comment("Minimum feathers required to use right-click items (bow, crossbow, trident, shield, etc.)");
        rightClickMinFeathers = builder.defineInRange("rightClickMinFeathers", 1, 0, 20);
        
        builder.comment("Minimum feathers required to use left-click items (weapons, tools)");
        leftClickMinFeathers = builder.defineInRange("leftClickMinFeathers", 0.5, 0.0, 20.0);
        
        builder.comment("Cancel animations when player doesn't have enough feathers");
        cancelAnimations = builder.define("cancelAnimations", true);
        
        builder.pop();


        builder.comment("Feather costs for player movement").push("movement");
        
        builder.comment("Enable feather costs for movement");
        movementCostsEnabled = builder.define("movementCostsEnabled", true);
        
        builder.comment("Feathers consumed per second while swimming");
        swimmingCostPerSecond = builder.defineInRange("swimmingCostPerSecond", 0.5, 0.0, 10.0);
        
        builder.comment("Feathers consumed per second while running/sprinting");
        runningCostPerSecond = builder.defineInRange("runningCostPerSecond", 0.75, 0.0, 10.0);
        
        builder.comment("Feathers consumed per second while flying (elytra/creative) - base cost for straight flight");
        flyingCostPerSecond = builder.defineInRange("flyingCostPerSecond", 0.75, 0.0, 10.0);

        builder.comment("Feathers consumed per second while actively gliding with vc_gliders");
        gliderCostPerSecond = builder.defineInRange("gliderCostPerSecond", 0.75, 0.0, 10.0);
        
        builder.comment("Feathers consumed per second while walking");
        walkingCostPerSecond = builder.defineInRange("walkingCostPerSecond", 0.05, 0.0, 10.0);
        
        builder.comment("Feathers consumed per jump");
        jumpingCost = builder.defineInRange("jumpingCost", 0.5, 0.0, 10.0);

        builder.comment("Minimum feathers required to continue swimming");
        swimmingMinFeathers = builder.defineInRange("swimmingMinFeathers", 0.5, 0.0, 20.0);

        builder.comment("Minimum feathers required to continue sprinting");
        runningMinFeathers = builder.defineInRange("runningMinFeathers", 0.5, 0.0, 20.0);

        builder.comment("Minimum feathers required to continue elytra flight");
        flyingMinFeathers = builder.defineInRange("flyingMinFeathers", 0.5, 0.0, 20.0);

        builder.comment("Minimum feathers required to continue vc_gliders active gliding");
        gliderMinFeathers = builder.defineInRange("gliderMinFeathers", 0.5, 0.0, 20.0);

        builder.comment("Minimum feathers required to continue walking");
        walkingMinFeathers = builder.defineInRange("walkingMinFeathers", 0.5, 0.0, 20.0);

        builder.comment("Minimum feathers required to jump");
        jumpingMinFeathers = builder.defineInRange("jumpingMinFeathers", 0.5, 0.0, 20.0);

        builder.comment("Ticks before feathers can regenerate after this mod applies a cost");
        regenDelayAfterCostTicks = builder.defineInRange("regenDelayAfterCostTicks", 20, 0, 20 * 60);

        builder.comment("Cooldown in seconds after running out of feathers before the player can sprint or swim again");
        sprintExhaustionCooldownSeconds = builder.defineInRange("sprintExhaustionCooldownSeconds", 3.0, 0.0, 60.0);
        
        builder.pop();


        builder.comment("Activity-specific exhaustion punishments").push("activityPunishments");
        
        builder.comment("Enable activity-specific punishments (Swimmer's Shoulder, Runner's Fatigue, etc.)");
        activityPunishmentsEnabled = builder.define("activityPunishmentsEnabled", true);
        
        builder.comment("Only apply activity punishments on Hard and Hardcore difficulty");
        activityPunishmentsHardModeOnly = builder.define("activityPunishmentsHardModeOnly", true);
        
        builder.comment("Swimmer's Shoulder minimum damage (in half-hearts, 1.0 = 0.5 hearts)");
        swimmerShoulderMinDamage = builder.defineInRange("swimmerShoulderMinDamage", 1.0, 0.0, 20.0);
        
        builder.comment("Swimmer's Shoulder maximum damage (in half-hearts, 3.0 = 1.5 hearts)");
        swimmerShoulderMaxDamage = builder.defineInRange("swimmerShoulderMaxDamage", 3.0, 0.0, 20.0);
        
        builder.comment("Runner's Fatigue minimum damage (in half-hearts, 1.0 = 0.5 hearts)");
        runnerFatigueMinDamage = builder.defineInRange("runnerFatigueMinDamage", 1.0, 0.0, 20.0);
        
        builder.comment("Runner's Fatigue maximum damage (in half-hearts, 2.0 = 1 heart)");
        runnerFatigueMaxDamage = builder.defineInRange("runnerFatigueMaxDamage", 2.0, 0.0, 20.0);
        
        builder.comment("Pilot's Strain minimum damage (in half-hearts, 1.0 = 0.5 hearts)");
        pilotStrainMinDamage = builder.defineInRange("pilotStrainMinDamage", 1.0, 0.0, 20.0);
        
        builder.comment("Pilot's Strain maximum damage (in half-hearts, 2.0 = 1 heart)");
        pilotStrainMaxDamage = builder.defineInRange("pilotStrainMaxDamage", 2.0, 0.0, 20.0);
        
        builder.comment("Walker's Cramp damage (in half-hearts, 1.0 = 0.5 hearts)");
        walkerCrampDamage = builder.defineInRange("walkerCrampDamage", 1.0, 0.0, 20.0);

        builder.comment("Effects applied by Swimmer's Shoulder as effect_id,duration_ticks,amplifier");
        swimmerShoulderEffects = builder.defineList("swimmerShoulderEffects",
            () -> Arrays.asList("minecraft:mining_fatigue,300,1", "minecraft:slowness,200,0", "minecraft:hunger,300,1"),
            object -> object instanceof String
        );

        builder.comment("Effects applied by Runner's Fatigue as effect_id,duration_ticks,amplifier");
        runnerFatigueEffects = builder.defineList("runnerFatigueEffects",
            () -> Arrays.asList("minecraft:slowness,400,1", "minecraft:weakness,200,0", "minecraft:hunger,300,0"),
            object -> object instanceof String
        );

        builder.comment("Effects applied by Pilot's Strain as effect_id,duration_ticks,amplifier");
        pilotStrainEffects = builder.defineList("pilotStrainEffects",
            () -> Arrays.asList("minecraft:levitation,60,-1", "minecraft:nausea,140,0"),
            object -> object instanceof String
        );

        builder.comment("Effects applied by Walker's Cramp as effect_id,duration_ticks,amplifier");
        walkerCrampEffects = builder.defineList("walkerCrampEffects",
            () -> Arrays.asList("minecraft:slowness,300,0", "minecraft:hunger,150,0"),
            object -> object instanceof String
        );
        
        builder.pop();


        builder.comment("Learning Curve - Players become more efficient with practice").push("learningCurve");
        
        builder.comment("Enable learning curve system (reduces feather costs as players gain experience)");
        learningCurveEnabled = builder.define("learningCurveEnabled", true);
        
        builder.comment("Minimum cost multiplier after full training (0.1 = 10% of original cost)");
        learningCurveMinMultiplier = builder.defineInRange("learningCurveMinMultiplier", 0.1, 0.0, 1.0);
        
        builder.comment("Stat value required to reach minimum cost (e.g., 100000 blocks walked)");
        learningCurveMaxStat = builder.defineInRange("learningCurveMaxStat", 100000, 1000, 10000000);
        
        builder.comment("Learning rate - how quickly players improve (higher = faster learning)");
        learningCurveRate = builder.defineInRange("learningCurveRate", 1.0, 0.1, 10.0);
        
        builder.pop();


        builder.comment("Velocity Reduction - Reduce projectile speed instead of blocking when low feathers").push("velocityReduction");
        
        builder.comment("Enable velocity reduction system (projectiles fly slower when low feathers instead of being blocked)");
        velocityReductionEnabled = builder.define("velocityReductionEnabled", true);
        
        builder.comment("Velocity multiplier when feathers are below requirement (0.3 = 30% speed, deals less damage)");
        lowFeatherVelocityMultiplier = builder.defineInRange("lowFeatherVelocityMultiplier", 0.3, 0.1, 1.0);
        
        builder.comment("Reset crossbow charge state if player runs out of feathers before fully charged");
        crossbowResetOnFail = builder.define("crossbowResetOnFail", true);

        builder.comment("Apply directional spread to charged crossbow shots fired below the right-click feather requirement instead of reducing velocity");
        lowFeatherInaccuracyEnabled = builder.define("lowFeatherInaccuracyEnabled", true);

        builder.comment("Maximum random yaw/pitch offset in degrees for low-feather crossbow shots");
        lowFeatherInaccuracyDegrees = builder.defineInRange("lowFeatherInaccuracyDegrees", 10.0, 0.0, 90.0);
        
        builder.pop();


        builder.comment("Flying Maneuverability - Increase costs for complex aerial maneuvers").push("flyingManeuverability");
        
        builder.comment("Enable dynamic flying costs based on direction/angle changes");
        flyingManeuverabilityEnabled = builder.define("flyingManeuverabilityEnabled", true);
        
        builder.comment("Cost multiplier for maneuvering (2.0 = double cost when turning/changing angles)");
        flyingManeuverabilityMultiplier = builder.defineInRange("flyingManeuverabilityMultiplier", 2.0, 1.0, 10.0);
        
        builder.comment("Threshold for detecting significant direction change (radians, 0.3 = ~17 degrees)");
        flyingManeuverabilityThreshold = builder.defineInRange("flyingManeuverabilityThreshold", 0.3, 0.1, 3.14);
        
        builder.pop();

        CONFIG = builder.build();
    }
}
