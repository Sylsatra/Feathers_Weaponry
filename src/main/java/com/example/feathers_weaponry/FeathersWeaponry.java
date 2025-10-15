package com.example.feathers_weaponry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Random;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.stats.Stats;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import com.elenai.feathers.api.FeathersHelper;

@Mod("feathersweaponry")
public class FeathersWeaponry {

    private static final Map<UUID, DrawData> activeDraws = new HashMap<>();
    private static final Random random = new Random();

    private static class Rule {
        final boolean isTag;
        final ResourceLocation id;
        final double factor;
        final TagKey<Item> tagKey;

        Rule(boolean isTag, ResourceLocation id, double factor) {
            this.isTag = isTag;
            this.id = id;
            this.factor = factor;
            this.tagKey = isTag ? ItemTags.create(id) : null;
        }
    }

    private static Map<ResourceLocation, Double> bowExactFactors = null;
    private static java.util.List<Rule> bowTagRules = null;
    private static Map<ResourceLocation, Double> weaponExactFactors = null;
    private static java.util.List<Rule> weaponTagRules = null;

    public FeathersWeaponry() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FeathersWeaponryConfig.CONFIG);
        MinecraftForge.EVENT_BUS.register(new EventHandlers());
    }

    public static void subtractFeathers(Player player, int amount) {
        if (player instanceof ServerPlayer) {
            FeathersHelper.subFeathers((ServerPlayer) player, amount);
        }
    }

    public static void subtractFeathersDouble(Player player, double amount) {
        if (player instanceof ServerPlayer) {
            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
            data.featherAccumulator += amount;
            if (data.featherAccumulator >= 1.0) {
                int toSubtract = (int) data.featherAccumulator;
                FeathersHelper.subFeathers((ServerPlayer) player, toSubtract);
                data.featherAccumulator -= toSubtract;
            }
        }
    }

    public static int getFeathers(Player player) {
        if (player instanceof ServerPlayer) {
            return FeathersHelper.getFeathers((ServerPlayer) player);
        }
        return 0;
    }

    public static boolean isHardOrHardcore(Player player) {
        Difficulty difficulty = player.level().getDifficulty();
        return difficulty == Difficulty.HARD || difficulty == Difficulty.HARD;
    }

    /**
     * Calculate learning curve multiplier based on player statistics
     * Returns a value between minMultiplier and 1.0
     * Formula: multiplier = max(minMultiplier, 1.0 - (statValue / maxStat) * learningRate * (1.0 - minMultiplier))
     */
    public static double getLearningMultiplier(ServerPlayer player, Stat<?> stat) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        
        int statValue = player.getStats().getValue(stat);
        int maxStat = FeathersWeaponryConfig.learningCurveMaxStat.get();
        double minMultiplier = FeathersWeaponryConfig.learningCurveMinMultiplier.get();
        double learningRate = FeathersWeaponryConfig.learningCurveRate.get();
        

        double progress = Math.min(1.0, (double) statValue / maxStat * learningRate);
        

        double multiplier = 1.0 - (progress * (1.0 - minMultiplier));
        
        return Math.max(minMultiplier, multiplier);
    }

    /**
     * Get learning multiplier for item usage (right-click)
     */
    public static double getItemUseLearningMultiplier(ServerPlayer player, ItemStack stack) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        
        Item item = stack.getItem();
        Stat<Item> stat = Stats.ITEM_USED.get(item);
        return getLearningMultiplier(player, stat);
    }

    /**
     * Get learning multiplier for weapon usage (left-click/attack)
     */
    public static double getWeaponLearningMultiplier(ServerPlayer player, ItemStack stack) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        

        return getLearningMultiplier(player, Stats.CUSTOM.get(Stats.MOB_KILLS));
    }

    /**
     * Calculate flying maneuverability multiplier based on direction/angle changes
     * Returns 1.0 for straight flight, up to maneuverabilityMultiplier for complex maneuvers
     */
    public static double calculateFlyingManeuverability(ServerPlayer player, PlayerDataTracker.PlayerData data) {

        double currentYaw = player.getYRot();
        double currentPitch = player.getXRot();
        

        if (data.isFirstFlyTick) {
            data.lastFlyYaw = currentYaw;
            data.lastFlyPitch = currentPitch;
            data.isFirstFlyTick = false;
            return 1.0;
        }
        

        double yawChange = Math.abs(normalizeAngle(currentYaw - data.lastFlyYaw));
        double pitchChange = Math.abs(currentPitch - data.lastFlyPitch);
        

        double totalChange = Math.toRadians(yawChange + pitchChange);
        

        data.lastFlyYaw = currentYaw;
        data.lastFlyPitch = currentPitch;
        

        double threshold = FeathersWeaponryConfig.flyingManeuverabilityThreshold.get();
        if (totalChange > threshold) {

            double multiplier = FeathersWeaponryConfig.flyingManeuverabilityMultiplier.get();

            double scaleFactor = Math.min(1.0, (totalChange - threshold) / threshold);
            return 1.0 + (scaleFactor * (multiplier - 1.0));
        }
        
        return 1.0;
    }
    
    /**
     * Normalize angle to -180 to 180 range
     */
    private static double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    /**
     * Get learning multiplier for movement activities
     */
    public static double getMovementLearningMultiplier(ServerPlayer player, String activity) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        
        Stat<?> stat;
        switch (activity) {
            case "swimming":
                stat = Stats.CUSTOM.get(Stats.SWIM_ONE_CM);
                break;
            case "running":
                stat = Stats.CUSTOM.get(Stats.SPRINT_ONE_CM);
                break;
            case "flying":
                stat = Stats.CUSTOM.get(Stats.FLY_ONE_CM);
                break;
            case "walking":
                stat = Stats.CUSTOM.get(Stats.WALK_ONE_CM);
                break;
            case "jumping":
                stat = Stats.CUSTOM.get(Stats.JUMP);
                break;
            default:
                return 1.0;
        }
        
        return getLearningMultiplier(player, stat);
    }

    public static void applyPenaltyIfLowFeathers(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        int currentFeathers = FeathersHelper.getFeathers((ServerPlayer) player);
        if (currentFeathers <= 1) {
            int amplifier = (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) ? 1 : 0;
            int duration = 60; 
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, amplifier));
        }
    }


    public static void applyRotatorCuffTears(Player player) {
        if (!FeathersWeaponryConfig.rotatorCuffEnabled.get()) return;
        if (FeathersWeaponryConfig.rotatorCuffHardModeOnly.get() && !isHardOrHardcore(player)) return;
        
        int currentFeathers = getFeathers(player);
        if (currentFeathers >= 2) return;
        

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastRotatorCuffTime < 100) return;
        data.lastRotatorCuffTime = currentTime;
        

        double roll = random.nextDouble();
        double damage;
        double minDmg = FeathersWeaponryConfig.rotatorCuffMinDamage.get();
        double maxDmg = FeathersWeaponryConfig.rotatorCuffMaxDamage.get();
        
        if (roll < 0.7) {

            damage = minDmg + (random.nextDouble() * Math.min(2.0, (maxDmg - minDmg) / 3.0));
        } else if (roll < 0.9) {

            damage = Math.min(maxDmg, 2.0) + (random.nextDouble() * Math.min(2.0, (maxDmg - 2.0)));
        } else {

            damage = Math.min(maxDmg, 4.0) + (random.nextDouble() * (maxDmg - 4.0));
        }
        
        damage = Math.max(minDmg, Math.min(maxDmg, damage));
        

        player.hurt(player.damageSources().generic(), (float) damage);
        

        int fatigueLevel = 0;
        int fatigueDuration = 30;
        
        if (damage <= 2.0) {
            fatigueLevel = 0;
            fatigueDuration = 30;
        } else if (damage <= 4.0) {
            fatigueLevel = 1;
            fatigueDuration = 60;
        } else {
            fatigueLevel = 2;
            fatigueDuration = 90;
        }
        
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, fatigueDuration, fatigueLevel));
    }


    public static void applySwimmerShoulder(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastSwimmerShoulderTime < 200) return;
        data.lastSwimmerShoulderTime = currentTime;
        
        double minDmg = FeathersWeaponryConfig.swimmerShoulderMinDamage.get();
        double maxDmg = FeathersWeaponryConfig.swimmerShoulderMaxDamage.get();
        double damage = minDmg + (random.nextDouble() * (maxDmg - minDmg));
        
        player.hurt(player.damageSources().generic(), (float) damage);
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 300, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 1));
    }

    public static void applyRunnerFatigue(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastRunnerFatigueTime < 200) return;
        data.lastRunnerFatigueTime = currentTime;
        
        double minDmg = FeathersWeaponryConfig.runnerFatigueMinDamage.get();
        double maxDmg = FeathersWeaponryConfig.runnerFatigueMaxDamage.get();
        double damage = minDmg + (random.nextDouble() * (maxDmg - minDmg));
        
        player.hurt(player.damageSources().generic(), (float) damage);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 0));
    }

    public static void applyPilotStrain(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastPilotStrainTime < 200) return;
        data.lastPilotStrainTime = currentTime;
        
        double minDmg = FeathersWeaponryConfig.pilotStrainMinDamage.get();
        double maxDmg = FeathersWeaponryConfig.pilotStrainMaxDamage.get();
        double damage = minDmg + (random.nextDouble() * (maxDmg - minDmg));
        
        player.hurt(player.damageSources().generic(), (float) damage);
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, -1));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
    }

    public static void applyWalkerCramp(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastWalkerCrampTime < 200) return;
        data.lastWalkerCrampTime = currentTime;
        
        double damage = FeathersWeaponryConfig.walkerCrampDamage.get();
        
        player.hurt(player.damageSources().generic(), (float) damage);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 0));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 150, 0));
    }

    private static class DrawData {
        int tickCount = 0;
        int secondsDrawn = 0;
    }

    private static void ensureRulesBuilt() {
        if (bowExactFactors == null || bowTagRules == null) {
            var lists = FeathersWeaponryConfig.validBowItems.get();
            var parsed = parseRules(lists);
            bowExactFactors = parsed.exact;
            bowTagRules = parsed.tags;
        }
        if (weaponExactFactors == null || weaponTagRules == null) {
            var lists = FeathersWeaponryConfig.validWeaponItems.get();
            var parsed = parseRules(lists);
            weaponExactFactors = parsed.exact;
            weaponTagRules = parsed.tags;
        }
    }

    private static class ParsedRules {
        final Map<ResourceLocation, Double> exact;
        final java.util.List<Rule> tags;

        ParsedRules(Map<ResourceLocation, Double> exact, java.util.List<Rule> tags) {
            this.exact = exact;
            this.tags = tags;
        }
    }

    private static ParsedRules parseRules(java.util.List<? extends String> entries) {
        Map<ResourceLocation, Double> exact = new HashMap<>();
        java.util.List<Rule> tags = new java.util.ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                if (raw == null || raw.isEmpty()) continue;
                String s = raw.trim();
                boolean isTag = s.startsWith("#");
                if (isTag) s = s.substring(1);
                double factor = 1.0;
                int eq = s.indexOf('=');
                String idStr = s;
                if (eq >= 0) {
                    idStr = s.substring(0, eq).trim();
                    String fStr = s.substring(eq + 1).trim();
                    try {
                        factor = Double.parseDouble(fStr);
                    } catch (NumberFormatException ignored) {
                        factor = 1.0;
                    }
                }
                ResourceLocation id = ResourceLocation.tryParse(idStr);
                if (id == null) continue;
                if (isTag) {
                    tags.add(new Rule(true, id, factor));
                } else {
                    exact.put(id, factor);
                }
            }
        }
        return new ParsedRules(exact, tags);
    }

    private static double resolveBowFactor(ItemStack stack) {
        ensureRulesBuilt();
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (registryName == null) return Double.NaN;
        Double exact = bowExactFactors.get(registryName);
        if (exact != null) return exact;
        for (Rule r : bowTagRules) {
            if (stack.is(r.tagKey)) return r.factor;
        }
        return Double.NaN;
    }

    private static double resolveWeaponFactor(ItemStack stack) {
        ensureRulesBuilt();
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (registryName == null) return Double.NaN;
        Double exact = weaponExactFactors.get(registryName);
        if (exact != null) return exact;
        for (Rule r : weaponTagRules) {
            if (stack.is(r.tagKey)) return r.factor;
        }
        return Double.NaN;
    }

    public static boolean isValidBow(ItemStack stack) {
        return !Double.isNaN(resolveBowFactor(stack));
    }

    public static boolean isValidWeapon(ItemStack stack) {
        return !Double.isNaN(resolveWeaponFactor(stack));
    }

    public static class EventHandlers {

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            Player player = event.player;
            if (player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer)) return;
            
            ServerPlayer serverPlayer = (ServerPlayer) player;
            UUID playerId = player.getUUID();
            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(playerId);
            

            handleDrawing(player, playerId);
            

            handleShieldCosts(serverPlayer, data);
            

            handleMovementCosts(serverPlayer, data);
        }

        private void handleDrawing(Player player, UUID playerId) {
            if (player.isUsingItem()) {
                ItemStack itemStack = player.getUseItem();
                if (itemStack != null && isValidBow(itemStack)) {
                    DrawData data = activeDraws.getOrDefault(playerId, new DrawData());
                    data.tickCount++;
                    if (data.tickCount >= FeathersWeaponryConfig.drawTimeThreshold.get()) {
                        data.secondsDrawn++;
                        int base = data.secondsDrawn;
                        double factor = resolveBowFactor(itemStack);
                        if (Double.isNaN(factor)) factor = 1.0;
                        double cost = base * factor;
                        if (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) {
                            cost *= 2.0;
                        }
                        

                        if (player instanceof ServerPlayer) {
                            double learningMultiplier = getItemUseLearningMultiplier((ServerPlayer) player, itemStack);
                            cost *= learningMultiplier;
                        }
                        
                        int deduction = Math.max(1, (int) Math.ceil(cost));
                        subtractFeathers(player, deduction);
                        

                        int currentFeathers = getFeathers(player);
                        if (currentFeathers < 2) {
                            applyRotatorCuffTears(player);
                        }
                        
                        applyPenaltyIfLowFeathers(player);
                        data.tickCount = 0;
                    }
                    activeDraws.put(playerId, data);
                } else {
                    activeDraws.remove(playerId);
                }
            } else {
                activeDraws.remove(playerId);
            }
        }

        private void handleShieldCosts(ServerPlayer player, PlayerDataTracker.PlayerData data) {
            ItemStack useItem = player.getUseItem();
            
            if (useItem.getItem() instanceof ShieldItem && player.isBlocking()) {
                long currentTick = player.level().getGameTime();
                

                if (FeathersWeaponryConfig.shieldOvertimeEnabled.get()) {
                    if (data.lastShieldCheckTick == 0) {
                        data.lastShieldCheckTick = currentTick;
                    }
                    
                    data.shieldRaisedTicks++;
                    

                    if (data.shieldRaisedTicks >= 40) {
                        double cost = FeathersWeaponryConfig.shieldOvertimeCost.get();
                        subtractFeathersDouble(player, cost);
                        data.shieldRaisedTicks = 0;
                    }
                }
                

                if (FeathersWeaponryConfig.shieldDamageEnabled.get()) {
                    int currentDurability = useItem.getMaxDamage() - useItem.getDamageValue();
                    
                    if (data.lastShieldDurability > 0 && currentDurability < data.lastShieldDurability) {
                        int durabilityLost = data.lastShieldDurability - currentDurability;
                        double cost = Math.ceil(durabilityLost * FeathersWeaponryConfig.shieldDamageFactor.get());
                        subtractFeathersDouble(player, cost);
                    }
                    
                    data.lastShieldDurability = currentDurability;
                }
            } else {

                data.shieldRaisedTicks = 0;
                data.lastShieldCheckTick = 0;
                data.lastShieldDurability = -1;
            }
        }

        private void handleMovementCosts(ServerPlayer player, PlayerDataTracker.PlayerData data) {
            if (!FeathersWeaponryConfig.movementCostsEnabled.get()) return;
            
            long currentTick = player.level().getGameTime();
            double costPerTick = 0.0;
            String currentActivity = "";
            

            boolean isOnGround = player.onGround();
            if (!isOnGround && data.wasOnGround) {

                double jumpCost = FeathersWeaponryConfig.runningCostPerSecond.get() / 20.0;

                double learningMultiplier = getMovementLearningMultiplier(player, "jumping");
                jumpCost *= learningMultiplier;
                subtractFeathersDouble(player, jumpCost * 5);
            }
            data.wasOnGround = isOnGround;
            

            if (player.isSwimming()) {
                costPerTick = FeathersWeaponryConfig.swimmingCostPerSecond.get() / 20.0;
                currentActivity = "swimming";
                data.consecutiveSwimTicks++;
                data.consecutiveRunTicks = 0;
                data.consecutiveFlyTicks = 0;
                data.consecutiveWalkTicks = 0;
            } else if (player.isSprinting()) {
                costPerTick = FeathersWeaponryConfig.runningCostPerSecond.get() / 20.0;
                currentActivity = "running";
                data.consecutiveRunTicks++;
                data.consecutiveSwimTicks = 0;
                data.consecutiveFlyTicks = 0;
                data.consecutiveWalkTicks = 0;
            } else if (player.isFallFlying()) {
                costPerTick = FeathersWeaponryConfig.flyingCostPerSecond.get() / 20.0;
                

                if (FeathersWeaponryConfig.flyingManeuverabilityEnabled.get()) {
                    double maneuverMultiplier = calculateFlyingManeuverability(player, data);
                    costPerTick *= maneuverMultiplier;
                }
                
                currentActivity = "flying";
                data.consecutiveFlyTicks++;
                data.consecutiveSwimTicks = 0;
                data.consecutiveRunTicks = 0;
                data.consecutiveWalkTicks = 0;
            } else if (!player.isCrouching() && isMoving(player, data)) {
                costPerTick = FeathersWeaponryConfig.walkingCostPerSecond.get() / 20.0;
                currentActivity = "walking";
                data.consecutiveWalkTicks++;
                data.consecutiveSwimTicks = 0;
                data.consecutiveRunTicks = 0;
                data.consecutiveFlyTicks = 0;
            } else {

                data.consecutiveSwimTicks = 0;
                data.consecutiveRunTicks = 0;
                data.consecutiveFlyTicks = 0;
                data.consecutiveWalkTicks = 0;
                data.isFirstFlyTick = true;
            }
            

            if (costPerTick > 0) {

                double learningMultiplier = getMovementLearningMultiplier(player, currentActivity);
                double adjustedCost = costPerTick * learningMultiplier;
                subtractFeathersDouble(player, adjustedCost);
                

                int currentFeathers = getFeathers(player);
                if (currentFeathers < 2) {

                    if (currentActivity.equals("swimming") && data.consecutiveSwimTicks > 100) {
                        applySwimmerShoulder(player);
                        data.consecutiveSwimTicks = 0;
                    } else if (currentActivity.equals("running") && data.consecutiveRunTicks > 100) {
                        applyRunnerFatigue(player);
                        data.consecutiveRunTicks = 0;
                    } else if (currentActivity.equals("flying") && data.consecutiveFlyTicks > 100) {
                        applyPilotStrain(player);
                        data.consecutiveFlyTicks = 0;
                    } else if (currentActivity.equals("walking") && data.consecutiveWalkTicks > 200) {
                        applyWalkerCramp(player);
                        data.consecutiveWalkTicks = 0;
                    }
                }
            }
            

            data.lastPosX = player.getX();
            data.lastPosY = player.getY();
            data.lastPosZ = player.getZ();
            data.lastMovementTick = currentTick;
        }

        private boolean isMoving(ServerPlayer player, PlayerDataTracker.PlayerData data) {
            double dx = player.getX() - data.lastPosX;
            double dy = player.getY() - data.lastPosY;
            double dz = player.getZ() - data.lastPosZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            return distSq > 0.001;
        }

        @SubscribeEvent
        public void onLivingHurt(LivingHurtEvent event) {
            if (!(event.getSource().getEntity() instanceof ServerPlayer)) return;
            ServerPlayer player = (ServerPlayer) event.getSource().getEntity();
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand != null && isValidWeapon(mainHand)) {
                int base = 1;
                double factor = resolveWeaponFactor(mainHand);
                if (Double.isNaN(factor)) factor = 1.0;
                double cost = base * factor;
                if (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) {
                    cost *= 2.0;
                }
                

                double learningMultiplier = getWeaponLearningMultiplier(player, mainHand);
                cost *= learningMultiplier;
                
                int deduction = Math.max(1, (int) Math.ceil(cost));
                subtractFeathers(player, deduction);
                

                int currentFeathers = getFeathers(player);
                if (currentFeathers < 2) {
                    applyRotatorCuffTears(player);
                }
                
                applyPenaltyIfLowFeathers(player);
            }
        }

        @SubscribeEvent
        public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (!(event.getEntity() instanceof Player)) return;
            Player player = (Player) event.getEntity();
            if (player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer)) return;
            
            ServerPlayer serverPlayer = (ServerPlayer) player;
            ItemStack stack = event.getItemStack();
            

            if (stack.isEdible()) return;
            
            int feathers = FeathersHelper.getFeathers(serverPlayer);
            int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
            

            double itemCost = 0;
            boolean isRestrictedItem = false;
            boolean isRangedWeapon = false;
            
            if (isValidBow(stack)) {
                itemCost = resolveBowFactor(stack);
                if (Double.isNaN(itemCost)) itemCost = 1.0;
                isRestrictedItem = true;
                isRangedWeapon = (stack.getItem() instanceof BowItem || 
                                 stack.getItem() instanceof CrossbowItem || 
                                 stack.getItem() instanceof TridentItem);
            } else if (stack.getItem() instanceof ShieldItem) {

                isRestrictedItem = true;
                itemCost = minRequired;
            }
            

            if (isRestrictedItem && (feathers < minRequired || (itemCost > 0 && feathers < itemCost))) {

                if (FeathersWeaponryConfig.velocityReductionEnabled.get() && isRangedWeapon) {

                    return;
                }
                
                if (FeathersWeaponryConfig.cancelAnimations.get()) {
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (!(event.getEntity() instanceof Player)) return;
            Player player = (Player) event.getEntity();
            if (player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer)) return;
            
            ServerPlayer serverPlayer = (ServerPlayer) player;
            ItemStack stack = event.getItemStack();
            

            if (stack.isEdible()) return;
            
            int feathers = FeathersHelper.getFeathers(serverPlayer);
            int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
            

            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                if (feathers < minRequired) {
                    if (FeathersWeaponryConfig.cancelAnimations.get()) {
                        event.setCanceled(true);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {

        }

        @SubscribeEvent
        public void onAttackEntity(AttackEntityEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer)) return;
            ServerPlayer player = (ServerPlayer) event.getEntity();
            
            ItemStack mainHand = player.getMainHandItem();
            int feathers = FeathersHelper.getFeathers(player);
            double minRequired = FeathersWeaponryConfig.leftClickMinFeathers.get();
            

            double itemCost = 0;
            if (isValidWeapon(mainHand)) {
                itemCost = resolveWeaponFactor(mainHand);
                if (Double.isNaN(itemCost)) itemCost = 1.0;
            }
            

            if (feathers < minRequired || (itemCost > 0 && feathers < itemCost)) {
                if (FeathersWeaponryConfig.cancelAnimations.get()) {
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public void onBlockBreak(BlockEvent.BreakEvent event) {
            if (!(event.getPlayer() instanceof ServerPlayer)) return;
            ServerPlayer player = (ServerPlayer) event.getPlayer();
            
            ItemStack mainHand = player.getMainHandItem();
            int feathers = FeathersHelper.getFeathers(player);
            double minRequired = FeathersWeaponryConfig.leftClickMinFeathers.get();
            

            double itemCost = 0;
            if (isValidWeapon(mainHand)) {
                itemCost = resolveWeaponFactor(mainHand);
                if (Double.isNaN(itemCost)) itemCost = 0.5;
            }
            

            if (feathers < minRequired || (itemCost > 0 && feathers < itemCost)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onShieldBlock(ShieldBlockEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer)) return;
            ServerPlayer player = (ServerPlayer) event.getEntity();
            
            int feathers = FeathersHelper.getFeathers(player);
            int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
            

            if (feathers < minRequired) {
                event.setCanceled(true);

                player.stopUsingItem();
            }
        }

        @SubscribeEvent
        public void onEntityJoinLevel(EntityJoinLevelEvent event) {
            if (!FeathersWeaponryConfig.velocityReductionEnabled.get()) return;
            if (event.getLevel().isClientSide()) return;
            

            if (event.getEntity() instanceof AbstractArrow) {
                AbstractArrow arrow = (AbstractArrow) event.getEntity();
                
                if (arrow.getOwner() instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) arrow.getOwner();
                    int feathers = FeathersHelper.getFeathers(player);
                    int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
                    

                    if (feathers < minRequired) {

                        Vec3 motion = arrow.getDeltaMovement();
                        double multiplier = FeathersWeaponryConfig.lowFeatherVelocityMultiplier.get();
                        arrow.setDeltaMovement(motion.scale(multiplier));
                        

                        arrow.setBaseDamage(arrow.getBaseDamage() * multiplier);
                    }
                }
            }
            

            if (event.getEntity() instanceof ThrownTrident) {
                ThrownTrident trident = (ThrownTrident) event.getEntity();
                
                if (trident.getOwner() instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) trident.getOwner();
                    int feathers = FeathersHelper.getFeathers(player);
                    int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
                    

                    if (feathers < minRequired) {

                        Vec3 motion = trident.getDeltaMovement();
                        double multiplier = FeathersWeaponryConfig.lowFeatherVelocityMultiplier.get();
                        trident.setDeltaMovement(motion.scale(multiplier));
                        

                        trident.setBaseDamage(trident.getBaseDamage() * multiplier);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onPlayerTick_CrossbowCheck(TickEvent.PlayerTickEvent event) {
            if (!FeathersWeaponryConfig.velocityReductionEnabled.get()) return;
            if (!FeathersWeaponryConfig.crossbowResetOnFail.get()) return;
            if (event.player.level().isClientSide) return;
            if (!(event.player instanceof ServerPlayer)) return;
            
            ServerPlayer player = (ServerPlayer) event.player;
            ItemStack useItem = player.getUseItem();
            

            if (useItem.getItem() instanceof CrossbowItem && player.isUsingItem()) {
                int feathers = FeathersHelper.getFeathers(player);
                int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
                

                if (!CrossbowItem.isCharged(useItem) && feathers < minRequired) {

                    player.stopUsingItem();
                    

                    CompoundTag tag = useItem.getOrCreateTag();
                    tag.putBoolean("Charged", false);
                    tag.remove("ChargedProjectiles");
                }
            }
        }
    }
}
