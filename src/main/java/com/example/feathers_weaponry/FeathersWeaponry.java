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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
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
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerPlayerGameMode;
import com.elenai.feathers.api.FeathersHelper;

@Mod("feathersweaponry")
public class FeathersWeaponry {

    private static final Map<UUID, DrawData> activeDraws = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Random random = new Random();

    private static class Rule {
        final boolean isTag;
        final ResourceLocation id;
        final ConfiguredItemRule costs;
        final TagKey<Item> tagKey;

        Rule(boolean isTag, ResourceLocation id, ConfiguredItemRule costs) {
            this.isTag = isTag;
            this.id = id;
            this.costs = costs;
            this.tagKey = isTag ? ItemTags.create(id) : null;
        }
    }

    private static class RulesSnapshot {
        final Map<ResourceLocation, ConfiguredItemRule> bowExactFactors;
        final java.util.List<Rule> bowTagRules;
        final Map<ResourceLocation, ConfiguredItemRule> weaponExactFactors;
        final java.util.List<Rule> weaponTagRules;

        RulesSnapshot(
                Map<ResourceLocation, ConfiguredItemRule> bowExactFactors,
                java.util.List<Rule> bowTagRules,
                Map<ResourceLocation, ConfiguredItemRule> weaponExactFactors,
                java.util.List<Rule> weaponTagRules) {
            this.bowExactFactors = bowExactFactors;
            this.bowTagRules = bowTagRules;
            this.weaponExactFactors = weaponExactFactors;
            this.weaponTagRules = weaponTagRules;
        }
    }

    private static volatile RulesSnapshot cachedRules = null;
    private static final Object RULES_LOCK = new Object();

    public FeathersWeaponry() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FeathersWeaponryConfig.CONFIG);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(FeathersWeaponry::onConfigEvent);
        FeathersWeaponryNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> BetterCombatCompat::registerClient);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientMovementEvents::register);
        MinecraftForge.EVENT_BUS.register(new EventHandlers());
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event != null && event.getConfig() != null && event.getConfig().getSpec() == FeathersWeaponryConfig.CONFIG) {
            cachedRules = null;
        }
    }

    private static RulesSnapshot getRules() {
        RulesSnapshot snapshot = cachedRules;
        if (snapshot == null) {
            synchronized (RULES_LOCK) {
                snapshot = cachedRules;
                if (snapshot == null) {
                    var listsBow = FeathersWeaponryConfig.validBowItems.get();
                    var parsedBow = parseRules(listsBow, false);
                    var listsWeapon = FeathersWeaponryConfig.validWeaponItems.get();
                    var parsedWeapon = parseRules(listsWeapon, true);
                    snapshot = new RulesSnapshot(parsedBow.exact, parsedBow.tags, parsedWeapon.exact, parsedWeapon.tags);
                    cachedRules = snapshot;
                }
            }
        }
        return snapshot;
    }

    public static boolean subtractFeathers(Player player, int amount) {
        if (!(player instanceof ServerPlayer serverPlayer) || amount <= 0) {
            return true;
        }

        boolean spent = spendFeathersForAction(serverPlayer, amount);
        if (spent) {
            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
            blockRegenAfterCost(serverPlayer, data);
        }
        return spent;
    }

    public static boolean subtractFeathersDouble(Player player, double amount) {
        if (!(player instanceof ServerPlayer serverPlayer) || amount <= 0.0) {
            return true;
        }

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        if (Double.isNaN(data.featherAccumulator) || Double.isInfinite(data.featherAccumulator) || data.featherAccumulator < 0.0) {
            data.featherAccumulator = 0.0;
        }

        FeatherBudgetRules.FractionalSpend spend = FeatherBudgetRules.accumulateVisibleCost(data.featherAccumulator, amount);
        if (spend.halfFeatherCost() == 0) {
            data.featherAccumulator = spend.remainingVisibleCost();
            return true;
        }

        boolean spent = spendFeathersForAction(serverPlayer, spend.halfFeatherCost());
        if (spent) {
            data.featherAccumulator = spend.remainingVisibleCost();
            blockRegenAfterCost(serverPlayer, data);
        } else {
            data.featherAccumulator = 0.0;
        }
        return spent;
    }

    private static boolean canSpendFeathers(ServerPlayer player, int amount) {
        if (amount <= 0 || player.isCreative() || player.isSpectator()) {
            return true;
        }
        return FeatherBudgetRules.canAfford(
                FeathersHelper.getFeathers(player),
                FeathersHelper.getEndurance(player),
                getEffectiveWeight(player),
                amount);
    }

    private static boolean spendFeathersForAction(ServerPlayer player, int amount) {
        if (amount <= 0 || player.isCreative() || player.isSpectator()) {
            return true;
        }
        if (!canSpendFeathers(player, amount)) {
            return false;
        }
        return FeathersHelper.spendFeathers(player, amount);
    }

    private static boolean canStartAction(ServerPlayer player, double minimumFeathers, double cost) {
        int halfFeatherCost = FeatherBudgetRules.toHalfFeatherUnits(cost);
        return FeatherBudgetRules.canStartAction(
                FeathersHelper.getFeathers(player),
                FeathersHelper.getEndurance(player),
                getEffectiveWeight(player),
                minimumFeathers,
                halfFeatherCost);
    }

    private static boolean isExhausted(ServerPlayer player, double minimumFeathers) {
        return FeatherBudgetRules.isExhausted(
                FeathersHelper.getFeathers(player),
                FeathersHelper.getEndurance(player),
                getEffectiveWeight(player),
                minimumFeathers);
    }

    private static int getEffectiveWeight(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int weight = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                weight += FeathersHelper.getArmorWeightByStack(stack);
            }
        }
        return weight;
    }

    private static void blockRegenAfterCost(ServerPlayer player, PlayerDataTracker.PlayerData data) {
        if (player == null || player.level() == null || data == null) {
            return;
        }
        int delay = FeathersWeaponryConfig.regenDelayAfterCostTicks.get();
        if (delay <= 0) {
            data.regenHoldFeathers = -1;
            data.regenBlockedUntilTick = 0;
            return;
        }
        long gameTime = player.level().getGameTime();
        data.regenBlockedUntilTick = gameTime + delay;
        int currentFeathers = FeathersHelper.getFeathers(player);
        data.regenHoldFeathers = currentFeathers;
    }

    private static void suppressRegenIfBlocked(ServerPlayer player, PlayerDataTracker.PlayerData data) {
        if (player == null || player.level() == null || data == null || data.regenHoldFeathers < 0) {
            return;
        }

        long currentTick = player.level().getGameTime();
        int delay = FeathersWeaponryConfig.regenDelayAfterCostTicks.get();
        if (currentTick < 0 || currentTick > data.regenBlockedUntilTick || data.regenBlockedUntilTick - currentTick > delay + 200) {
            data.regenHoldFeathers = -1;
            data.regenBlockedUntilTick = 0;
            return;
        }

        int currentFeathers = FeathersHelper.getFeathers(player);
        if (currentFeathers > data.regenHoldFeathers) {
            FeathersHelper.setFeathers(player, data.regenHoldFeathers);
        } else if (currentFeathers < data.regenHoldFeathers) {
            data.regenHoldFeathers = currentFeathers;
        }
    }

    private static volatile Field destroyingBlockField = null;
    private static volatile boolean destroyingBlockFieldResolved = false;
    private static final Object DESTROYING_BLOCK_LOCK = new Object();

    private static boolean isDestroyingBlock(ServerPlayer player) {
        if (player == null || player.gameMode == null) {
            return false;
        }
        if (!destroyingBlockFieldResolved) {
            synchronized (DESTROYING_BLOCK_LOCK) {
                if (!destroyingBlockFieldResolved) {
                    try {
                        destroyingBlockField = ServerPlayerGameMode.class.getDeclaredField("isDestroyingBlock");
                        destroyingBlockField.setAccessible(true);
                    } catch (NoSuchFieldException e) {
                        try {
                            destroyingBlockField = ServerPlayerGameMode.class.getDeclaredField("f_9249_");
                            destroyingBlockField.setAccessible(true);
                        } catch (NoSuchFieldException ignored) {
                        }
                    }
                    destroyingBlockFieldResolved = true;
                }
            }
        }
        if (destroyingBlockField != null) {
            try {
                return destroyingBlockField.getBoolean(player.gameMode);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static void handleSwingDeduction(ServerPlayer player, PlayerDataTracker.PlayerData data, ItemStack stack) {
        if (!FeathersWeaponryConfig.meleeSwingEnabled.get()) return;
        if (player == null || player.level() == null || data == null || stack == null || stack.isEmpty()) return;
        if (isDestroyingBlock(player)) return;
        
        long currentTick = player.level().getGameTime();
        if (currentTick <= data.lastBlockInteractionTick + 2) return;
        chargePendingSwingMiss(player, data, currentTick);
        if (currentTick <= data.lastSwingTick + 5) return;
        data.lastSwingTick = currentTick;

        ConfiguredItemRule rule = resolveWeaponRule(stack);
        if (rule == null) return;
        double learningMultiplier = getMovementLearningMultiplier(player, "walking");
        double swingCost = rule.secondaryCost() * learningMultiplier;
        
        data.pendingSwingCost = swingCost;
        data.pendingSwingMinimum = rule.minimumFeathers();
        data.pendingSwingTick = currentTick;
    }

    static void handleBetterCombatSwing(ServerPlayer player, boolean offHand, long sequence) {
        if (player == null || player.level() == null) return;
        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTick = player.level().getGameTime();
        if (data.lastBetterCombatSwingSequence == sequence && currentTick <= data.lastBetterCombatSwingTick + 20) {
            return;
        }

        data.lastBetterCombatSwingSequence = sequence;
        data.lastBetterCombatSwingTick = currentTick;

        ItemStack stack = offHand ? player.getOffhandItem() : player.getMainHandItem();
        if (isValidWeapon(stack)) {
            handleSwingDeduction(player, data, stack);
            data.pendingSwingSequence = sequence;
        }
    }

    private static void chargePendingSwingMiss(ServerPlayer player, PlayerDataTracker.PlayerData data, long currentTick) {
        if (player == null || data == null || data.pendingSwingCost <= 0.0 || isDestroyingBlock(player)) {
            return;
        }
        int delayTicks = FeathersWeaponryConfig.meleeSwingMissChargeDelayTicks.get();
        if (!SwingChargeRules.shouldChargeMiss(currentTick, data.pendingSwingTick, delayTicks)) {
            return;
        }

        double swingCost = data.pendingSwingCost;
        double minimum = data.pendingSwingMinimum;
        data.pendingSwingCost = 0.0;
        data.pendingSwingMinimum = 0.0;
        data.pendingSwingTick = 0;
        data.pendingSwingSequence = Long.MIN_VALUE;

        boolean spent = subtractFeathersDouble(player, swingCost);
        if (!spent || isExhausted(player, minimum)) {
            applySwingPunishments(player, minimum);
        }
    }

    private static void applySwingPunishments(ServerPlayer player, double minimum) {
        if (!FeathersWeaponryConfig.meleeSwingTriggersPunishment.get()) {
            return;
        }
        applyRotatorCuffTears(player, minimum);
        applyPenaltyIfLowFeathers(player, minimum);
    }

    public static int getFeathers(Player player) {
        if (player instanceof ServerPlayer) {
            return FeathersHelper.getFeathers((ServerPlayer) player);
        }
        return 0;
    }

    public static boolean isHardOrHardcore(Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        return player.level().getDifficulty() == Difficulty.HARD || player.level().getLevelData().isHardcore();
    }

    public static double getLearningMultiplier(ServerPlayer player, Stat<?> stat) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        if (player == null || stat == null || player.getStats() == null) return 1.0;
        
        int statValue = player.getStats().getValue(stat);
        int maxStat = FeathersWeaponryConfig.learningCurveMaxStat.get();
        double minMultiplier = FeathersWeaponryConfig.learningCurveMinMultiplier.get();
        double learningRate = FeathersWeaponryConfig.learningCurveRate.get();
        
        double progress = Math.min(1.0, (double) statValue / maxStat * learningRate);
        double multiplier = 1.0 - (progress * (1.0 - minMultiplier));
        return Math.max(minMultiplier, multiplier);
    }

    public static double getItemUseLearningMultiplier(ServerPlayer player, ItemStack stack) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        if (player == null || stack == null || stack.isEmpty()) return 1.0;
        
        Item item = stack.getItem();
        if (item == null) return 1.0;
        Stat<Item> stat = Stats.ITEM_USED.get(item);
        if (stat == null) return 1.0;
        return getLearningMultiplier(player, stat);
    }

    public static double getWeaponLearningMultiplier(ServerPlayer player, ItemStack stack) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        if (player == null) return 1.0;
        
        Stat<?> stat = Stats.CUSTOM.get(Stats.MOB_KILLS);
        if (stat == null) return 1.0;
        return getLearningMultiplier(player, stat);
    }

    public static double calculateFlyingManeuverability(ServerPlayer player, PlayerDataTracker.PlayerData data) {
        if (player == null || data == null) {
            return 1.0;
        }
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
    
    private static double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    public static double getMovementLearningMultiplier(ServerPlayer player, String activity) {
        if (!FeathersWeaponryConfig.learningCurveEnabled.get()) return 1.0;
        if (player == null) return 1.0;
        
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
        if (stat == null) return 1.0;
        return getLearningMultiplier(player, stat);
    }

    public static void applyPenaltyIfLowFeathers(Player player) {
        applyPenaltyIfLowFeathers(player, 1.0);
    }

    public static void applyPenaltyIfLowFeathers(Player player, double threshold) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null) return;
        if (isExhausted(serverPlayer, threshold)) {
            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
            long currentTime = player.level().getGameTime();
            if (currentTime - data.lastLowFeatherPenaltyTime < 40) {
                return;
            }
            data.lastLowFeatherPenaltyTime = currentTime;
            applyConfiguredEffects(player, FeathersWeaponryConfig.lowFeatherPenaltyEffects.get());
        }
    }

    public static void applyRotatorCuffTears(Player player) {
        applyRotatorCuffTears(player, 2.0);
    }

    public static void applyRotatorCuffTears(Player player, double threshold) {
        if (!FeathersWeaponryConfig.rotatorCuffEnabled.get()) return;
        if (FeathersWeaponryConfig.rotatorCuffHardModeOnly.get() && !isHardOrHardcore(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null || !isExhausted(serverPlayer, threshold)) return;

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

        if (damage > 0.0) {
            DamageSource source = FeatherDamageSources.exhaustion(serverPlayer.serverLevel());
            if (source != null) {
                player.hurt(source, (float) damage);
            }
        }

        PunishmentRules.Severity severity = PunishmentRules.severityForDamage(damage);
        switch (severity) {
            case MINOR -> applyConfiguredEffects(player, FeathersWeaponryConfig.rotatorCuffMinorEffects.get());
            case MODERATE -> applyConfiguredEffects(player, FeathersWeaponryConfig.rotatorCuffModerateEffects.get());
            case SEVERE -> applyConfiguredEffects(player, FeathersWeaponryConfig.rotatorCuffSevereEffects.get());
        }
    }

    private static void applyConfiguredEffects(Player player, java.util.List<? extends String> entries) {
        if (player == null || entries == null) return;
        for (PunishmentRules.EffectRule rule : PunishmentRules.parseEffects(entries)) {
            ResourceLocation id = ResourceLocation.tryParse(rule.effectId());
            if (id == null) {
                continue;
            }

            var effect = BuiltInRegistries.MOB_EFFECT.get(id);
            if (effect != null) {
                player.addEffect(new MobEffectInstance(effect, rule.durationTicks(), rule.amplifier()));
            }
        }
    }

    public static void applySwimmerShoulder(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null) return;

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastSwimmerShoulderTime < 200) return;
        data.lastSwimmerShoulderTime = currentTime;
        
        double minDmg = FeathersWeaponryConfig.swimmerShoulderMinDamage.get();
        double maxDmg = FeathersWeaponryConfig.swimmerShoulderMaxDamage.get();
        double damage = minDmg + (random.nextDouble() * (maxDmg - minDmg));
        
        if (damage > 0.0) {
            DamageSource source = FeatherDamageSources.exhaustion(serverPlayer.serverLevel());
            if (source != null) {
                player.hurt(source, (float) damage);
            }
        }
        applyConfiguredEffects(player, FeathersWeaponryConfig.swimmerShoulderEffects.get());
    }

    public static void applyRunnerFatigue(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null) return;

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastRunnerFatigueTime < 200) return;
        data.lastRunnerFatigueTime = currentTime;
        
        double minDmg = FeathersWeaponryConfig.runnerFatigueMinDamage.get();
        double maxDmg = FeathersWeaponryConfig.runnerFatigueMaxDamage.get();
        double damage = minDmg + (random.nextDouble() * (maxDmg - minDmg));
        
        if (damage > 0.0) {
            DamageSource source = FeatherDamageSources.exhaustion(serverPlayer.serverLevel());
            if (source != null) {
                player.hurt(source, (float) damage);
            }
        }
        applyConfiguredEffects(player, FeathersWeaponryConfig.runnerFatigueEffects.get());
    }

    public static void applyPilotStrain(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null) return;

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastPilotStrainTime < 200) return;
        data.lastPilotStrainTime = currentTime;
        
        double minDmg = FeathersWeaponryConfig.pilotStrainMinDamage.get();
        double maxDmg = FeathersWeaponryConfig.pilotStrainMaxDamage.get();
        double damage = minDmg + (random.nextDouble() * (maxDmg - minDmg));
        
        if (damage > 0.0) {
            DamageSource source = FeatherDamageSources.exhaustion(serverPlayer.serverLevel());
            if (source != null) {
                player.hurt(source, (float) damage);
            }
        }
        applyConfiguredEffects(player, FeathersWeaponryConfig.pilotStrainEffects.get());
    }

    public static void applyWalkerCramp(Player player) {
        if (!FeathersWeaponryConfig.activityPunishmentsEnabled.get()) return;
        if (FeathersWeaponryConfig.activityPunishmentsHardModeOnly.get() && !isHardOrHardcore(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null) return;

        PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
        long currentTime = player.level().getGameTime();
        if (currentTime - data.lastWalkerCrampTime < 200) return;
        data.lastWalkerCrampTime = currentTime;
        
        double damage = FeathersWeaponryConfig.walkerCrampDamage.get();
        
        if (damage > 0.0) {
            DamageSource source = FeatherDamageSources.exhaustion(serverPlayer.serverLevel());
            if (source != null) {
                player.hurt(source, (float) damage);
            }
        }
        applyConfiguredEffects(player, FeathersWeaponryConfig.walkerCrampEffects.get());
    }

    private static class DrawData {
        int tickCount = 0;
        int secondsDrawn = 0;
    }

    private static class ParsedRules {
        final Map<ResourceLocation, ConfiguredItemRule> exact;
        final java.util.List<Rule> tags;

        ParsedRules(Map<ResourceLocation, ConfiguredItemRule> exact, java.util.List<Rule> tags) {
            this.exact = exact;
            this.tags = tags;
        }
    }

    private static ParsedRules parseRules(java.util.List<? extends String> entries, boolean weapon) {
        Map<ResourceLocation, ConfiguredItemRule> exact = new HashMap<>();
        java.util.List<Rule> tags = new java.util.ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                if (raw == null || raw.isEmpty()) continue;
                String s = raw.trim();
                boolean isTag = s.startsWith("#");
                if (isTag) s = s.substring(1);
                int eq = s.indexOf('=');
                String idStr = s;
                String value = "1.0";
                if (eq >= 0) {
                    idStr = s.substring(0, eq).trim();
                    value = s.substring(eq + 1).trim();
                }
                ResourceLocation id = ResourceLocation.tryParse(idStr);
                if (id == null) continue;
                ConfiguredItemRule costs = weapon
                        ? ConfiguredItemRule.parseWeaponValue(
                                value,
                                FeathersWeaponryConfig.meleeSwingMultiplier.get(),
                                FeathersWeaponryConfig.leftClickMinFeathers.get())
                        : ConfiguredItemRule.parseBowValue(value, FeathersWeaponryConfig.rightClickMinFeathers.get());
                if (isTag) {
                    tags.add(new Rule(true, id, costs));
                } else {
                    exact.put(id, costs);
                }
            }
        }
        return new ParsedRules(exact, tags);
    }

    private static ConfiguredItemRule resolveBowRule(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item == null) return null;
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
        if (registryName == null) return null;
        RulesSnapshot rules = getRules();
        ConfiguredItemRule exact = rules.bowExactFactors.get(registryName);
        if (exact != null) return exact;
        for (Rule r : rules.bowTagRules) {
            if (r.tagKey != null && stack.is(r.tagKey)) return r.costs;
        }
        return null;
    }

    private static ConfiguredItemRule resolveWeaponRule(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item == null) return null;
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
        if (registryName == null) return null;
        RulesSnapshot rules = getRules();
        ConfiguredItemRule exact = rules.weaponExactFactors.get(registryName);
        if (exact != null) return exact;
        for (Rule r : rules.weaponTagRules) {
            if (r.tagKey != null && stack.is(r.tagKey)) return r.costs;
        }
        return null;
    }

    public static boolean isClientRestrictedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return resolveWeaponRule(stack) != null;
    }

    public static boolean isClientRestrictedUse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return isValidBow(stack) || stack.getItem() instanceof ShieldItem;
    }

    public static boolean allowsClientProjectileFallback(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ConfiguredItemRule rule = resolveBowRule(stack);
        return rule != null && !rule.cancelAtMinimum() && FeathersWeaponryConfig.velocityReductionEnabled.get();
    }

    public static boolean isValidBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return resolveBowRule(stack) != null;
    }

    public static boolean isValidWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return resolveWeaponRule(stack) != null;
    }

    public static double clientWeaponMinimum(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return FeathersWeaponryConfig.leftClickMinFeathers.get();
        ConfiguredItemRule rule = resolveWeaponRule(stack);
        return rule == null ? FeathersWeaponryConfig.leftClickMinFeathers.get() : rule.minimumFeathers();
    }

    public static int clientWeaponStartCost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ConfiguredItemRule rule = resolveWeaponRule(stack);
        return rule == null ? 0 : FeatherBudgetRules.toHalfFeatherUnits(rule.primaryCost() + rule.secondaryCost());
    }

    public static int clientUseStartCost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ConfiguredItemRule rule = resolveBowRule(stack);
        if (rule != null) {
            return FeatherBudgetRules.toHalfFeatherUnits(rule.primaryCost());
        }
        return stack.getItem() instanceof ShieldItem
                ? FeatherBudgetRules.toHalfFeatherUnits(FeathersWeaponryConfig.rightClickMinFeathers.get())
                : 0;
    }

    public static double clientUseMinimum(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return FeathersWeaponryConfig.rightClickMinFeathers.get();
        ConfiguredItemRule rule = resolveBowRule(stack);
        return rule == null ? FeathersWeaponryConfig.rightClickMinFeathers.get() : rule.minimumFeathers();
    }

    public static class EventHandlers {
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Player player = event.player;
            if (player == null || player.level() == null) return;
            if (player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            
            UUID playerId = player.getUUID();
            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(playerId);

            suppressRegenIfBlocked(serverPlayer, data);

            handleDrawing(player, playerId);

            handleShieldCosts(serverPlayer, data);

            handleMovementCosts(serverPlayer, data);

            if (isDestroyingBlock(serverPlayer)) {
                data.lastBlockInteractionTick = serverPlayer.level().getGameTime();
                data.pendingSwingCost = 0.0;
                data.pendingSwingMinimum = 0.0;
                data.pendingSwingTick = 0;
                data.pendingSwingSequence = Long.MIN_VALUE;
            } else {
                chargePendingSwingMiss(serverPlayer, data, serverPlayer.level().getGameTime());

                if (serverPlayer.swinging && serverPlayer.swingTime == 1) { 
                    ItemStack mainHand = serverPlayer.getMainHandItem();
                    if (isValidWeapon(mainHand)) {
                        handleSwingDeduction(serverPlayer, data, mainHand);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onLivingJump(LivingEvent.LivingJumpEvent event) {
            if (event == null || !(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level() == null) return;
            
            if (!FeathersWeaponryConfig.movementCostsEnabled.get()) return;

            double jumpCost = FeathersWeaponryConfig.jumpingCost.get();
            double learningMultiplier = getMovementLearningMultiplier(player, "jumping");
            jumpCost *= learningMultiplier;

            if (!MovementCostRules.canStartActivity(
                        getFeathers(player),
                        FeathersHelper.getEndurance(player),
                        getEffectiveWeight(player),
                        FeathersWeaponryConfig.jumpingMinFeathers.get())
                    || !subtractFeathersDouble(player, jumpCost)) {
                Vec3 motion = player.getDeltaMovement();
                if (motion != null) {
                    player.setDeltaMovement(motion.x, 0.0, motion.z);
                }
                player.hurtMarked = true;
                applyPenaltyIfLowFeathers(player, FeathersWeaponryConfig.jumpingMinFeathers.get());
            }
        }

        private void handleDrawing(Player player, UUID playerId) {
            if (player == null || playerId == null) return;
            if (player.isUsingItem()) {
                ItemStack itemStack = player.getUseItem();
                if (itemStack != null && !itemStack.isEmpty() && isValidBow(itemStack)) {
                    DrawData data = activeDraws.getOrDefault(playerId, new DrawData());
                    data.tickCount++;
                    if (data.tickCount >= FeathersWeaponryConfig.drawTimeThreshold.get()) {
                        data.secondsDrawn++;
                        int base = data.secondsDrawn;
                        ConfiguredItemRule bowRule = resolveBowRule(itemStack);
                        double cost = base * (bowRule == null ? 1.0 : bowRule.primaryCost());
                        if (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) {
                            cost *= 2.0;
                        }
                        

                        if (player instanceof ServerPlayer) {
                            double learningMultiplier = getItemUseLearningMultiplier((ServerPlayer) player, itemStack);
                            cost *= learningMultiplier;
                        }
                        
                        int deduction = FeatherBudgetRules.toHalfFeatherUnits(cost);
                        if (!subtractFeathers(player, deduction)) {
                            if (bowRule == null || bowRule.cancelAtMinimum()) {
                                player.stopUsingItem();
                            }
                            double minimum = bowRule == null
                                    ? FeathersWeaponryConfig.rightClickMinFeathers.get()
                                    : bowRule.minimumFeathers();
                            applyPenaltyIfLowFeathers(player, minimum);
                            data.tickCount = 0;
                            activeDraws.put(playerId, data);
                            return;
                        }
                        

                        double minimum = bowRule == null
                                ? FeathersWeaponryConfig.rightClickMinFeathers.get()
                                : bowRule.minimumFeathers();
                        if (player instanceof ServerPlayer serverPlayer && isExhausted(serverPlayer, minimum)) {
                            applyRotatorCuffTears(player, minimum);
                        }
                        
                        applyPenaltyIfLowFeathers(player, minimum);
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
            if (player == null || player.level() == null || data == null) return;
            ItemStack useItem = player.getUseItem();
            
            if (useItem != null && !useItem.isEmpty() && useItem.getItem() instanceof ShieldItem && player.isBlocking()) {
                long currentTick = player.level().getGameTime();
                

                if (FeathersWeaponryConfig.shieldOvertimeEnabled.get()) {
                    if (data.lastShieldCheckTick == 0) {
                        data.lastShieldCheckTick = currentTick;
                    }
                    
                    data.shieldRaisedTicks++;
                    

                    if (data.shieldRaisedTicks >= 40) {
                        double cost = FeathersWeaponryConfig.shieldOvertimeCost.get();
                        if (!subtractFeathersDouble(player, cost)) {
                            player.stopUsingItem();
                            applyPenaltyIfLowFeathers(player, FeathersWeaponryConfig.rightClickMinFeathers.get());
                        }
                        data.shieldRaisedTicks = 0;
                    }
                }
                

                if (FeathersWeaponryConfig.shieldDamageEnabled.get()) {
                    int currentDurability = useItem.getMaxDamage() - useItem.getDamageValue();
                    
                    if (data.lastShieldDurability > 0 && currentDurability < data.lastShieldDurability) {
                        int durabilityLost = data.lastShieldDurability - currentDurability;
                        double cost = durabilityLost * FeathersWeaponryConfig.shieldDamageFactor.get();
                        if (!subtractFeathersDouble(player, cost)) {
                            player.stopUsingItem();
                            applyPenaltyIfLowFeathers(player, FeathersWeaponryConfig.rightClickMinFeathers.get());
                        }
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
            if (player == null || player.level() == null || data == null) return;
            if (!FeathersWeaponryConfig.movementCostsEnabled.get()) return;
            
            long currentTick = player.level().getGameTime();
            if (data.lastMovementTick == 0) {
                data.lastPosX = player.getX();
                data.lastPosY = player.getY();
                data.lastPosZ = player.getZ();
                data.lastMovementTick = currentTick;
                return;
            }
            if (data.sprintExhaustedCooldownUntil < 0 || data.sprintExhaustedCooldownUntil > currentTick + 1200) {
                data.sprintExhaustedCooldownUntil = 0;
            }
            double costPerTick = 0.0;
            String currentActivity = "";
            

            boolean isActivelyGliding = GlidersCompat.isActivelyGliding(player);
            boolean isFlying = player.isFallFlying() || isActivelyGliding;

            if (player.isSwimming()) {
                costPerTick = FeathersWeaponryConfig.swimmingCostPerSecond.get() / 20.0;
                currentActivity = "swimming";
                data.consecutiveSwimTicks++;
                data.consecutiveRunTicks = 0;
                data.consecutiveFlyTicks = 0;
                data.consecutiveWalkTicks = 0;
            } else if (isFlying) {
                costPerTick = (isActivelyGliding && !player.isFallFlying()
                        ? FeathersWeaponryConfig.gliderCostPerSecond.get()
                        : FeathersWeaponryConfig.flyingCostPerSecond.get()) / 20.0;
                

                if (FeathersWeaponryConfig.flyingManeuverabilityEnabled.get()) {
                    double maneuverMultiplier = calculateFlyingManeuverability(player, data);
                    costPerTick *= maneuverMultiplier;
                }
                
                currentActivity = "flying";
                data.consecutiveFlyTicks++;
                data.consecutiveSwimTicks = 0;
                data.consecutiveRunTicks = 0;
                data.consecutiveWalkTicks = 0;
            } else if (player.isSprinting()) {
                costPerTick = FeathersWeaponryConfig.runningCostPerSecond.get() / 20.0;
                currentActivity = "running";
                data.consecutiveRunTicks++;
                data.consecutiveSwimTicks = 0;
                data.consecutiveFlyTicks = 0;
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
            

            if (!currentActivity.isEmpty()) {

                double learningMultiplier = getMovementLearningMultiplier(player, currentActivity);
                double adjustedCost = MovementCostRules.adjustedCost(costPerTick, learningMultiplier, isActivelyGliding);
                double minimumFeathers = movementMinimumFor(currentActivity, isActivelyGliding);
                boolean allowed = MovementCostRules.canStartActivity(
                                currentActivity,
                                getFeathers(player),
                                FeathersHelper.getEndurance(player),
                                getEffectiveWeight(player),
                                minimumFeathers,
                                currentTick,
                                data.sprintExhaustedCooldownUntil)
                        && subtractFeathersDouble(player, adjustedCost);
                if (!allowed) {
                    restrictMovement(player, currentActivity);
                    applyPenaltyIfLowFeathers(player, minimumFeathers);
                    applyMovementPunishment(player, currentActivity);
                    resetMovementCounter(data, currentActivity);
                    if ("running".equals(currentActivity) || "swimming".equals(currentActivity)) {
                        long cooldownTicks = ClientAttackGate.calculateCooldownTicks(FeathersWeaponryConfig.sprintExhaustionCooldownSeconds.get());
                        data.sprintExhaustedCooldownUntil = Math.max(data.sprintExhaustedCooldownUntil, currentTick + cooldownTicks);
                    }
                } else if (isExhausted(player, minimumFeathers)) {
                    applyPenaltyIfLowFeathers(player, minimumFeathers);
                    applyMovementPunishment(player, currentActivity);
                    if ("running".equals(currentActivity) || "swimming".equals(currentActivity)) {
                        long cooldownTicks = ClientAttackGate.calculateCooldownTicks(FeathersWeaponryConfig.sprintExhaustionCooldownSeconds.get());
                        data.sprintExhaustedCooldownUntil = Math.max(data.sprintExhaustedCooldownUntil, currentTick + cooldownTicks);
                    }
                }
            }
            

            data.lastPosX = player.getX();
            data.lastPosY = player.getY();
            data.lastPosZ = player.getZ();
            data.lastMovementTick = currentTick;
        }

        private boolean isMoving(ServerPlayer player, PlayerDataTracker.PlayerData data) {
            if (player == null || data == null) return false;
            double dx = player.getX() - data.lastPosX;
            double dy = player.getY() - data.lastPosY;
            double dz = player.getZ() - data.lastPosZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            return distSq > 0.001;
        }

        private double movementMinimumFor(String activity, boolean activeGlider) {
            return switch (activity) {
                case "swimming" -> FeathersWeaponryConfig.swimmingMinFeathers.get();
                case "running" -> FeathersWeaponryConfig.runningMinFeathers.get();
                case "flying" -> activeGlider
                        ? FeathersWeaponryConfig.gliderMinFeathers.get()
                        : FeathersWeaponryConfig.flyingMinFeathers.get();
                case "walking" -> FeathersWeaponryConfig.walkingMinFeathers.get();
                default -> 0.0;
            };
        }

        private void restrictMovement(ServerPlayer player, String activity) {
            if (player == null) return;
            boolean wasSprinting = player.isSprinting();
            boolean wasSwimming = player.isSwimming();
            boolean wasFallFlying = player.isFallFlying();

            if ("running".equals(activity)) {
                player.setSprinting(false);
            }
            if ("swimming".equals(activity)) {
                player.setSwimming(false);
                player.setSprinting(false);
            }
            if ("flying".equals(activity) && wasFallFlying) {
                player.stopFallFlying();
            }
            if ("flying".equals(activity)) {
                GlidersCompat.forceStopGliding(player);
            }

            Vec3 motion = player.getDeltaMovement();
            if (motion != null) {
                double y = MovementCostRules.restrictedVerticalVelocity(activity, motion.y);
                player.setDeltaMovement(motion.x * 0.2, y, motion.z * 0.2);

                if (wasSprinting || wasSwimming || wasFallFlying || (motion.x * motion.x + motion.z * motion.z) > 0.01) {
                    player.hurtMarked = true;
                }
            }
        }

        private void resetMovementCounter(PlayerDataTracker.PlayerData data, String activity) {
            if (data == null) return;
            if ("swimming".equals(activity)) {
                data.consecutiveSwimTicks = 0;
            } else if ("running".equals(activity)) {
                data.consecutiveRunTicks = 0;
            } else if ("flying".equals(activity)) {
                data.consecutiveFlyTicks = 0;
            } else if ("walking".equals(activity)) {
                data.consecutiveWalkTicks = 0;
            }
        }

        private void applyMovementPunishment(ServerPlayer player, String activity) {
            switch (activity) {
                case "swimming" -> applySwimmerShoulder(player);
                case "running" -> applyRunnerFatigue(player);
                case "flying" -> applyPilotStrain(player);
                case "walking" -> applyWalkerCramp(player);
                default -> {
                }
            }
        }

        @SubscribeEvent
        public void onLivingHurt(LivingHurtEvent event) {
            if (event == null || event.getSource() == null || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (player.level() == null) return;
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand != null && !mainHand.isEmpty() && isValidWeapon(mainHand)) {
                ConfiguredItemRule weaponRule = resolveWeaponRule(mainHand);
                double cost = weaponRule == null ? 1.0 : weaponRule.primaryCost();
                if (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) {
                    cost *= 2.0;
                }
                

                double learningMultiplier = getWeaponLearningMultiplier(player, mainHand);
                cost *= learningMultiplier;
                
                PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
                SwingChargeRules.HitCharge charge = SwingChargeRules.resolveHitCharge(cost, data.pendingSwingCost);
                data.pendingSwingCost = 0.0;
                data.pendingSwingMinimum = 0.0;
                data.pendingSwingTick = 0;
                data.pendingSwingSequence = Long.MIN_VALUE;

                if (!subtractFeathers(player, charge.wholeCost())) {
                    event.setCanceled(true);
                    double minimum = weaponRule == null
                            ? FeathersWeaponryConfig.leftClickMinFeathers.get()
                            : weaponRule.minimumFeathers();
                    applySwingPunishments(player, minimum);
                    return;
                }

                double minimum = weaponRule == null
                        ? FeathersWeaponryConfig.leftClickMinFeathers.get()
                        : weaponRule.minimumFeathers();
                if (isExhausted(player, minimum)) {
                    applySwingPunishments(player, minimum);
                }
            }
        }

        @SubscribeEvent
        public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (event == null || event.getEntity() == null) return;
            Player player = event.getEntity();
            if (player.level() == null || player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) return;
            

            if (stack.isEdible()) return;
            
            double minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
            

            double itemCost = 0;
            boolean isRestrictedItem = false;
            boolean isRangedWeapon = false;
            
            if (isValidBow(stack)) {
                ConfiguredItemRule bowRule = resolveBowRule(stack);
                itemCost = bowRule != null ? bowRule.primaryCost() : 1.0;
                minRequired = bowRule != null ? bowRule.minimumFeathers() : minRequired;
                isRestrictedItem = true;
                isRangedWeapon = (stack.getItem() instanceof BowItem || 
                                 stack.getItem() instanceof CrossbowItem || 
                                 stack.getItem() instanceof TridentItem);
            } else if (stack.getItem() instanceof ShieldItem) {

                isRestrictedItem = true;
                itemCost = minRequired;
            }

            if (isRestrictedItem) {
                boolean allowed = canStartAction(serverPlayer, minRequired, itemCost);
                if (!allowed) {

                    ConfiguredItemRule bowRule = resolveBowRule(stack);
                    boolean projectileFallback = bowRule != null
                            && !bowRule.cancelAtMinimum()
                            && FeathersWeaponryConfig.velocityReductionEnabled.get();
                    if (projectileFallback && isRangedWeapon) {
                        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
                            markCrossbowInaccuracy(serverPlayer);
                        }

                        return;
                    }

                    if (FeathersWeaponryConfig.cancelAnimations.get()) {
                        event.setCanceled(true);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event == null || event.getEntity() == null) return;
            Player player = event.getEntity();
            if (player.level() == null || player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) return;
            

            if (stack.isEdible()) return;
            
            int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
            

            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                if (!canStartAction(serverPlayer, minRequired, 0.0)) {
                    if (FeathersWeaponryConfig.cancelAnimations.get()) {
                        event.setCanceled(true);
                    }
                }
            }
        }

        private void markCrossbowInaccuracy(ServerPlayer player) {
            if (player == null || player.level() == null) return;
            if (!FeathersWeaponryConfig.lowFeatherInaccuracyEnabled.get()) {
                return;
            }

            double degrees = FeathersWeaponryConfig.lowFeatherInaccuracyDegrees.get();
            if (degrees <= 0.0) {
                return;
            }

            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
            data.crossbowInaccuracyUntilTick = player.level().getGameTime() + 5;
            data.crossbowInaccuracyDegrees = degrees;
        }

        @SubscribeEvent
        public void onAttackEntity(AttackEntityEvent event) {
            if (event == null || !(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level() == null) return;
            
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand == null || mainHand.isEmpty()) return;
            ConfiguredItemRule weaponRule = resolveWeaponRule(mainHand);
            if (weaponRule == null) {
                return;
            }

            double minRequired = weaponRule.minimumFeathers();
            double learningMultiplier = getWeaponLearningMultiplier(player, mainHand);
            double hitCost = weaponRule.primaryCost() * learningMultiplier;
            if (player.getAirSupply() < FeathersWeaponryConfig.bubbleThreshold.get()) {
                hitCost *= 2.0;
            }
            double combinedCost = hitCost + weaponRule.secondaryCost() * learningMultiplier;
            int startCost = FeatherBudgetRules.toHalfFeatherUnits(combinedCost);

            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
            boolean canceled = AttackStartRules.shouldCancelAttackStart(
                    true,
                    FeathersHelper.getFeathers(player),
                    FeathersHelper.getEndurance(player),
                    getEffectiveWeight(player),
                    minRequired,
                    startCost,
                    FeathersWeaponryConfig.cancelAnimations.get());
            if (canceled) {
                event.setCanceled(true);
                data.pendingSwingCost = 0.0;
                data.pendingSwingMinimum = 0.0;
                data.pendingSwingTick = 0;
                data.pendingSwingSequence = Long.MIN_VALUE;
                applySwingPunishments(player, minRequired);
            } else {
                handleSwingDeduction(player, data, mainHand);
            }
        }

        @SubscribeEvent
        public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (event == null || !(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level() == null) return;
            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
            data.lastBlockInteractionTick = player.level().getGameTime();
            data.pendingSwingCost = 0.0;
            data.pendingSwingMinimum = 0.0;
            data.pendingSwingTick = 0;
            data.pendingSwingSequence = Long.MIN_VALUE;
        }

        @SubscribeEvent
        public void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
            if (event == null || !(event.getPlayer() instanceof ServerPlayer player)) return;
            if (player.level() == null) return;
            PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
            data.lastBlockInteractionTick = player.level().getGameTime();
            data.pendingSwingCost = 0.0;
            data.pendingSwingMinimum = 0.0;
            data.pendingSwingTick = 0;
            data.pendingSwingSequence = Long.MIN_VALUE;
        }

        @SubscribeEvent
        public void onShieldBlock(ShieldBlockEvent event) {
            if (event == null || !(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level() == null) return;
            
            int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
            

            if (!canStartAction(player, minRequired, minRequired)) {
                event.setCanceled(true);

                player.stopUsingItem();
                applyPenaltyIfLowFeathers(player, minRequired);
            }
        }

        @SubscribeEvent
        public void onEntityJoinLevel(EntityJoinLevelEvent event) {
            if (!FeathersWeaponryConfig.velocityReductionEnabled.get()) return;
            if (event == null || event.getLevel() == null || event.getEntity() == null) return;
            if (event.getLevel().isClientSide()) return;
            

            if (event.getEntity() instanceof AbstractArrow arrow) {
                if (arrow.getOwner() instanceof ServerPlayer player) {
                    if (player.level() == null) return;
                    int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
                    
                    PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(player.getUUID());
                    long currentTick = player.level().getGameTime();
                    if (currentTick <= data.crossbowInaccuracyUntilTick && data.crossbowInaccuracyDegrees > 0.0) {
                        double degrees = data.crossbowInaccuracyDegrees;
                        double yawOffset = (random.nextDouble() * 2.0 - 1.0) * degrees;
                        double pitchOffset = (random.nextDouble() * 2.0 - 1.0) * degrees;
                        arrow.setDeltaMovement(CrossbowProjectileSpread.rotate(arrow.getDeltaMovement(), yawOffset, pitchOffset));
                        return;
                    }

                    if (!canStartAction(player, minRequired, 0.0)) {

                        Vec3 motion = arrow.getDeltaMovement();
                        if (motion != null) {
                            double multiplier = FeathersWeaponryConfig.lowFeatherVelocityMultiplier.get();
                            arrow.setDeltaMovement(motion.scale(multiplier));
                            

                            arrow.setBaseDamage(arrow.getBaseDamage() * multiplier);
                        }
                    }
                }
            }
            

            if (event.getEntity() instanceof ThrownTrident trident) {
                if (trident.getOwner() instanceof ServerPlayer player) {
                    if (player.level() == null) return;
                    int minRequired = FeathersWeaponryConfig.rightClickMinFeathers.get();
                    

                    if (!canStartAction(player, minRequired, 0.0)) {

                        Vec3 motion = trident.getDeltaMovement();
                        if (motion != null) {
                            double multiplier = FeathersWeaponryConfig.lowFeatherVelocityMultiplier.get();
                            trident.setDeltaMovement(motion.scale(multiplier));
                            

                            trident.setBaseDamage(trident.getBaseDamage() * multiplier);
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public void onPlayerTick_CrossbowCheck(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (!FeathersWeaponryConfig.velocityReductionEnabled.get()) return;
            if (!FeathersWeaponryConfig.crossbowResetOnFail.get()) return;
            if (event.player == null || event.player.level() == null) return;
            if (event.player.level().isClientSide) return;
            if (!(event.player instanceof ServerPlayer player)) return;
            
            ItemStack useItem = player.getUseItem();
            if (useItem == null || useItem.isEmpty()) return;
            

            if (useItem.getItem() instanceof CrossbowItem && player.isUsingItem()) {
                ConfiguredItemRule bowRule = resolveBowRule(useItem);
                double minRequired = bowRule == null
                        ? FeathersWeaponryConfig.rightClickMinFeathers.get()
                        : bowRule.minimumFeathers();
                

                if (!CrossbowItem.isCharged(useItem)
                        && bowRule != null
                        && bowRule.cancelAtMinimum()
                        && !canStartAction(player, minRequired, bowRule.primaryCost())) {

                    player.stopUsingItem();
                    

                    CompoundTag tag = useItem.getOrCreateTag();
                    tag.putBoolean("Charged", false);
                    tag.remove("ChargedProjectiles");
                }
            }
        }

        @SubscribeEvent
        public void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
            if (event != null && event.getEntity() != null) {
                UUID uuid = event.getEntity().getUUID();
                if (uuid != null) {
                    activeDraws.remove(uuid);
                    PlayerDataTracker.remove(uuid);
                }
            }
        }

        @SubscribeEvent
        public void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
            if (event != null && event.getEntity() != null) {
                UUID uuid = event.getEntity().getUUID();
                if (uuid != null) {
                    activeDraws.remove(uuid);
                    PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(uuid);
                    data.reset();
                }
            }
        }

        @SubscribeEvent
        public void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
            if (event != null && event.getEntity() != null) {
                UUID uuid = event.getEntity().getUUID();
                if (uuid != null) {
                    activeDraws.remove(uuid);
                    PlayerDataTracker.PlayerData data = PlayerDataTracker.getOrCreate(uuid);
                    data.reset();
                }
            }
        }

        @SubscribeEvent
        public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            activeDraws.clear();
            PlayerDataTracker.clear();
        }
    }
}
