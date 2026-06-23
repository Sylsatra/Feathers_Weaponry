# Feather Action System Reliability and Expansion Specification

**Status:** Approved design  
**Target:** Feathers Weaponry for Minecraft Forge 1.20.1  
**Config version:** 3  
**Required dependency:** Feathers 1.1.0  
**Optional compatibility:** Better Combat 1.20.1, Shield Expansion 1.20.1, Gliders/`vc_gliders`

## 1. Purpose

Replace the current collection of independent feather deductions with one server-authoritative action system. The new system must fix the reported configuration, exhaustion, regeneration, item-classification, shield, crossbow, mining, and compatibility defects while adding per-action limits, configurable effects, tool costs, shield cooldowns, and optional Glider consumption.

This specification intentionally preserves the existing learning-curve and punishment-damage concepts. It changes how actions are classified, authorized, charged, and tracked.

## 2. Research Findings

The current implementation concentrates configuration, state, and all gameplay events in three Java classes. The following behaviors explain the reported defects:

1. `TickEvent.PlayerTickEvent` handlers do not select a phase. Forge invokes them during both `START` and `END`, allowing draw, shield, movement, crossbow, and accumulator logic to run twice per game tick.
2. `subtractFeathersDouble` calls Feathers only after a fractional accumulator reaches one. Feathers resets its regeneration cooldown only when `FeathersHelper.subFeathers` is called, so fractional continuous actions allow regeneration between whole-feather deductions.
3. All costs call `FeathersHelper.subFeathers`. This clamps the feather value to zero but bypasses Feathers' weight/endurance eligibility rule. `FeathersHelper.spendFeathers` performs the weight-aware atomic check.
4. Shield damage is inferred by comparing remaining durability with one player-wide previous value. Switching to a shield with lower remaining durability is interpreted as damage. Forge already exposes the actual amount through `ShieldBlockEvent#getBlockedDamage()`.
5. Shield Expansion performs blocking in `LivingAttackEvent` and may cancel the attack without emitting the vanilla/Forge shield path on which the current restriction depends.
6. Bows, crossbows, and tridents share one item-rule list and projectile handler. Every low-feather `AbstractArrow` is therefore reduced without knowing which ranged action produced it.
7. `onLeftClickBlock` and `BlockEvent.BreakEvent` explicitly apply weapon rules during mining. A configured tool or weapon therefore receives combat behavior during block interaction.
8. Better Combat transmits an attack request that may contain zero targets. A zero-target swing does not produce `AttackEntityEvent`, and its public client `ATTACK_START` event is the reliable compatibility hook.
9. The bundled Glider source exposes active flight through `GliderUtil.isGlidingWithActiveGlider` and disables flight by clearing the equipped glider's `glide` state.
10. The current working tree compiles successfully with Forge 47.4.0, but it contains no automated tests for these behaviors.

## 3. Core Invariants

### 3.1 Server authority

The server decides whether an action is permitted and whether its cost was paid. Client hooks may report intent for animation-only or third-party actions, but the server validates player state, held/equipped item, action rate, and configuration before changing feathers.

### 3.2 Exactly-once processing

All player-tick processing runs only in `TickEvent.Phase.END`. One logical action may produce several Forge events, but an action key and tick-based deduplication token ensure it is charged once.

Examples:

- a melee swing may later hit an entity; the hit charges only the unpaid remainder;
- a crossbow loose event may spawn multiple Multishot projectiles; the firing action is charged once while each projectile may receive the same accuracy rule;
- a tool mining session has one start charge, at most one per-tick charge per game tick, and one break charge.

### 3.3 Atomic spending and the weight floor

For every action whose configured limit is greater than zero:

1. Determine the action's effective cost after item multiplier, learning multiplier, air modifier, block hardness, and other relevant modifiers.
2. Confirm that the player meets the action limit.
3. Confirm that the full effective cost can be paid without crossing Feathers' weight/endurance floor.
4. Commit the cost atomically or deny/stop the action without a partial deduction.

Fractional costs are accumulated per action. When a whole-feather payment is due, the entire whole amount must pass the atomic check. A failed payment leaves the spendable resource unchanged and stops or denies the action.

Creative and spectator players follow Feathers' native exemption behavior and are not restricted or charged.

### 3.4 Zero-limit behavior

A limit of `0` explicitly means:

- the action remains available at any feather count;
- an available cost may be charged without crossing the weight floor;
- inability to pay does not stop the action;
- feather regeneration remains suppressed for every tick in which the action is active.

This is the only mode in which an exhausted player may continue a configured consuming action.

### 3.5 Regeneration delay

`general.regenerationDelayTicks` defines the quiet period after the most recent consuming action. Its default is `20` ticks and its valid range is `0..12000`.

Any attempted action that is permitted and configured with a positive cost marks the player active immediately, including when its current fractional charge is below one feather. While an action remains active, the integration resets Feathers' internal cooldown every server tick. After activity stops, the internal cooldown remains reset until the configured deadline expires. Feathers' native regeneration then resumes normally.

A denied action does not extend the delay unless it represents a continuous action that must be actively stopped during the same tick. A zero-limit continuous action always extends the delay while active.

## 4. Architecture

### 4.1 `ActionType`

Define stable action identifiers for:

- `BOW_DRAW`
- `CROSSBOW_LOAD`
- `CROSSBOW_FIRE`
- `TRIDENT_CHARGE`
- `WEAPON_SWING`
- `WEAPON_HIT`
- `TOOL_MINE_START`
- `TOOL_MINE_TICK`
- `TOOL_BLOCK_BREAK`
- `SHIELD_HOLD`
- `SHIELD_BLOCK`
- `JUMP`
- `WALK`
- `SPRINT`
- `SWIM`
- `ELYTRA_FLIGHT`
- `GLIDER_FLIGHT`

Action identifiers are internal constants, not free-form configuration keys.

### 4.2 `FeatherActionService`

This is the sole authority for action eligibility and payment. It must:

- resolve the configured limit and effective cost;
- read current feathers, endurance, maximum feathers, and player weight;
- implement atomic weight-aware fractional payments;
- update per-action accumulators only after a permitted action;
- mark consuming activity and reset regeneration timing;
- return a structured result: `ALLOWED_PAID`, `ALLOWED_ZERO_LIMIT`, `DENIED_LIMIT`, or `DENIED_PAYMENT`;
- invoke punishment evaluation after an exhausted permitted action;
- expose a no-cost activity mark for zero-cost actions that must suppress regeneration.

No event handler may call `FeathersHelper.subFeathers`, `setFeathers`, or `spendFeathers` directly.

### 4.3 `ItemRuleRegistry`

Maintain independent parsed rule sets for bows, crossbows, tridents, weapons, tools, and shields. Each supports exact item IDs and item tags with the existing syntax:

```text
minecraft:iron_sword=1.0
#forge:tools/pickaxes=0.8
```

Malformed IDs and multipliers are logged once during config load and ignored. Config reload atomically replaces all parsed rule sets; gameplay threads never observe a half-built registry.

Membership comes only from the relevant rule set. Java inheritance does not implicitly place an item into another category. An administrator may intentionally list an item in more than one category, but event context determines which category applies.

### 4.4 `PunishmentService`

Centralize all punishment enable flags, difficulty gates, cooldowns, damage rolls, and effects. Rotator Cuff and action-specific punishments must never be invoked directly by an event adapter.

Effect entries use:

```text
namespace:effect_id,durationTicks,amplifier
```

Duration must be `1..12000`; amplifier must be `-1..255`. The `-1` value is supported to preserve the existing downward Levitation effect used by Pilot's Strain. Unknown effect IDs and malformed entries are logged during config load and skipped. Valid entries continue to apply even if another entry is invalid.

`rotatorCuffEnabled=false` prevents its damage, effects, and cooldown timestamp from being applied. Difficulty-only settings are checked before random rolls or timestamps are updated.

### 4.5 `PlayerActionState`

Track state by player UUID:

- fractional accumulator per action;
- last action tick and deduplication token per action;
- last consuming tick and regeneration deadline;
- active continuous actions;
- draw/load/charge session identity;
- melee swing payment and expiry;
- mining position, item fingerprint, start-payment state, and last tick charged;
- shield item fingerprint and hold counter;
- pending low-feather crossbow projectile context;
- punishment cooldown timestamps;
- flight maneuverability history.

Clear state on logout. Reset transient sessions on death, dimension change, held-item replacement, block target change, or action interruption. Persistent feather data remains owned by Feathers.

### 4.6 Event adapters

Split the current monolithic handler into focused adapters:

- ranged use and projectile spawning;
- melee and tool interaction;
- shield use and damage;
- movement and regeneration;
- player lifecycle and config reload;
- optional compatibility bootstrap.

Adapters translate Forge events into `ActionType` requests and apply the returned allow/deny result. They contain no payment arithmetic.

## 5. Configuration Schema

### 5.1 General

```toml
[general]
configVersion = 3
drawTimeThreshold = 15
bubbleThreshold = 4
regenerationDelayTicks = 20
```

### 5.2 Item rules

```toml
[items]
validBowItems = ["minecraft:bow=1.0"]
validCrossbowItems = ["minecraft:crossbow=1.1"]
validTridentItems = ["minecraft:trident=1.3"]
validWeaponItems = [
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
  "minecraft:netherite_axe=1.3"
]
validToolItems = [
  "#forge:tools/pickaxes=1.0",
  "#forge:tools/axes=1.0",
  "#forge:tools/shovels=1.0",
  "#forge:tools/hoes=1.0"
]
validShieldItems = ["minecraft:shield=1.0", "#forge:shields=1.0"]
```

### 5.3 Action limits

Each action has an integer `limit` in `0..20`. Every action limit defaults to `1`. For migrated configurations, the former global right-click limit seeds bow, crossbow, trident, and shield limits, while the former floating-point left-click limit is rounded upward to seed weapon and tool limits. The global `rightClickMinFeathers` and `leftClickMinFeathers` keys are migration inputs only and are not read during v3 gameplay.

Movement settings use `costPerSecond`, `limit`, and `punishmentEffects` for walk, sprint, swim, elytra flight, and Glider flight. Jump uses `cost`, `limit`, and `punishmentEffects`.

Ranged settings are named `bowDrawCost`, `crossbowLoadCost`, and `tridentChargeCost`, each defaulting to `1.0`, plus `bowLimit`, `crossbowLimit`, and `tridentLimit`, each defaulting to `1`. At every `drawTimeThreshold`, the interval cost is `configured base cost * item multiplier * consecutive interval number * learning multiplier`, with the existing low-air multiplier applied last. Releasing or interrupting use resets the consecutive interval number.

### 5.4 Tool costs

```toml
[tools]
enabled = true
toolLimit = 1
miningStartCost = 0.0
miningCostPerTick = 0.0
blockBreakHardnessMultiplier = 0.0
punishmentEffects = []
```

Defaults are zero to avoid silently adding mining costs to upgraded installations. Costs still become active when administrators configure positive values.

The break cost is:

```text
max(0, blockState.getDestroySpeed(level, position))
  * blockBreakHardnessMultiplier
  * validToolItemMultiplier
  * learningMultiplier
```

Unbreakable blocks, represented by negative destroy speed, never produce a cost. A canceled break produces no break charge.

### 5.5 Shield settings

```toml
[shield]
shieldOvertimeEnabled = true
shieldOvertimeCost = 0.25
shieldDamageEnabled = true
shieldDamageFactor = 0.05
shieldLimit = 1
shieldExhaustionCooldownTicks = 100
punishmentEffects = []
```

The cooldown range is `0..12000`. When shield hold or blocked-damage payment reduces the usable resource to exhaustion, or the next required payment fails, stop using the shield and apply the cooldown to the item type of every resolved `validShieldItems` exact rule. Tag-based rules are expanded over the item registry when applying the cooldown. A value of zero disables the cooldown but still stops use when the limit is positive.

### 5.6 Crossbow settings

```toml
[crossbow]
lowFeatherInaccuracyEnabled = true
lowFeatherInaccuracyDegrees = 10.0
crossbowResetOnFail = true
```

Inaccuracy degrees range from `0.0..90.0`. Zero disables spread even when the boolean is enabled. This setting applies only when a valid crossbow was fully loaded before the player fell below its limit and is subsequently fired while below that limit.

### 5.7 Punishment effects

Every consuming action exposes a `punishmentEffects` list. Existing punishment damage ranges and hard-mode gates remain independent configuration values. Default lists are:

| Action/punishment | Default effects |
| --- | --- |
| Rotator Cuff minor | `minecraft:mining_fatigue,30,0` |
| Rotator Cuff moderate | `minecraft:mining_fatigue,60,1` |
| Rotator Cuff severe | `minecraft:mining_fatigue,90,2` |
| Swim | `minecraft:mining_fatigue,300,1`, `minecraft:slowness,200,0`, `minecraft:hunger,300,1` |
| Sprint | `minecraft:slowness,400,1`, `minecraft:weakness,200,0`, `minecraft:hunger,300,0` |
| Elytra flight | `minecraft:levitation,60,-1`, `minecraft:nausea,140,0` |
| Walk | `minecraft:slowness,300,0`, `minecraft:hunger,150,0` |
| Bow, crossbow, trident, weapon | the Rotator Cuff severity list selected by the existing damage tier |
| Jump, tool, shield, crossbow fire, Glider | empty |

The Rotator Cuff lists are configured as `minorEffects`, `moderateEffects`, and `severeEffects` inside its existing punishment section. Other actions use one `punishmentEffects` list in their own section.

An empty list disables status effects for that action without disabling configured punishment damage. A zero damage range disables damage without disabling effects.

### 5.8 Glider integration

```toml
[compat.glider]
gliderIntegrationEnabled = false
gliderCostPerSecond = 0.5
gliderLimit = 1
gliderPunishmentEffects = []
```

The integration is disabled by default and remains inert unless `vc_gliders` is loaded. When enabled, it treats active Glider flight as `GLIDER_FLIGHT`, with its own cost, limit, effects, and regeneration suppression. If a positive-limit payment fails, clear the equipped Glider's active `glide` state and synchronize its normal Glider state to the client.

## 6. Action Behavior

### 6.1 Movement

Evaluate one movement action per `END` tick in this priority order:

1. active Glider flight;
2. swimming;
3. sprinting;
4. elytra flight;
5. walking based on horizontal displacement;
6. none.

Vertical displacement alone does not count as walking. Teleports and dimension changes reset the previous position and do not incur movement cost. A denied sprint invokes `setSprinting(false)`; a denied swim invokes `setSprinting(false)` and clears the swimming pose on the server; denied elytra flight invokes `stopFallFlying()`; denied Glider flight deactivates the glider. The adapter must not repeatedly punish a player merely because another mod reasserts a denied state in the same tick.

Jump is charged from `LivingJumpEvent` and deduplicated against movement processing.

### 6.2 Bow, crossbow, and trident

Only the matching item list drives each ranged action:

- bow use creates a `BOW_DRAW` session;
- uncharged crossbow use creates a `CROSSBOW_LOAD` session;
- charged crossbow use produces `CROSSBOW_FIRE`;
- trident use creates a `TRIDENT_CHARGE` session.

`LivingEntityUseItemEvent.Start` rejects a positive-limit action that cannot begin. `LivingEntityUseItemEvent.Tick` charges threshold/continuous costs and stops use immediately when payment fails. This event path is also responsible for `cancelAnimations`; cancellation and `stopUsingItem()` must occur on the server, with the normal use-state synchronization allowed to update clients.

`ArrowLooseEvent` identifies the actual firing stack. Bow-only low-feather velocity rules never apply to a crossbow or trident. For a preloaded crossbow fired below its limit, mark a short-lived projectile context before Forge spawns its projectiles. `EntityJoinLevelEvent` consumes that context for same-owner, same-tick `AbstractArrow` entities and applies a random yaw/pitch deviation bounded by `lowFeatherInaccuracyDegrees`. Multishot projectiles receive independent deviations. Inaccuracy changes direction, not speed or base damage.

Trident projectile behavior is handled independently and is never classified as bow behavior.

### 6.3 Melee

`WEAPON_SWING` charges the configured swing portion once. `WEAPON_HIT` charges only the unpaid remainder of the hit cost. Pending swing credit expires after the attack's valid window and cannot subsidize a later hit.

Vanilla attacks use `AttackEntityEvent` plus the existing hit event. Air swings use the server-observed vanilla swing only when Better Combat is absent. The Better Combat integration described below becomes the swing-intent source when it is present.

Mining events never call weapon actions, even when the held item appears in `validWeaponItems`.

### 6.4 Tools and mining

A mining session is identified by player UUID, dimension, block position, and held-item fingerprint.

- `LeftClickBlock` attempts `TOOL_MINE_START` once per session.
- The server tick applies `TOOL_MINE_TICK` once while the same valid tool continues actively destroying the same block.
- `BreakEvent` attempts `TOOL_BLOCK_BREAK` only after prior charges are valid and before allowing the break.
- Aborting, changing target, changing item, leaving range, death, or dimension change clears the session.

Weapons not present in `validToolItems` mine without Feather Weaponry costs. Tools not present in `validWeaponItems` do not receive combat costs.

### 6.5 Shields

Shield eligibility comes exclusively from `validShieldItems`, not `instanceof ShieldItem`.

`LivingEntityUseItemEvent.Start` prevents a configured shield from being raised below a positive limit. `Tick` applies hold cost and regeneration suppression. `ShieldBlockEvent` uses `getBlockedDamage() * shieldDamageFactor * itemMultiplier`; durability is not inspected.

At `EventPriority.HIGHEST`, a `LivingAttackEvent` compatibility guard checks an exhausted player's configured blocking item and calls `stopUsingItem()` before default-priority Shield Expansion processing. The guard does not cancel the attack; it removes blocking so the attack proceeds normally. The ordinary `ShieldBlockEvent` path remains responsible for vanilla and compatible shield damage accounting.

Exhaustion applies one shared cooldown across all configured shield items. Switching hands, slots, or shield tiers cannot avoid it.

## 7. Optional Compatibility

### 7.1 Better Combat

Load the integration only when mod ID `bettercombat` is present. Register with Better Combat's public client `BetterCombatClientEvents.ATTACK_START` publisher. Send a small custom packet containing the selected hand and a monotonic client attack sequence.

The server accepts the packet only when:

- Better Combat is loaded;
- the sender is alive and not a spectator;
- the reported hand still contains the server-resolved valid weapon;
- the sequence is newer than the last accepted sequence;
- the request respects a server-side minimum interval derived from attack speed;
- no equivalent swing was charged for that tick.

The packet requests `WEAPON_SWING`; it never supplies cost or feather values. This covers zero-target air swings. Later hit processing charges only the remainder and deduplicates any vanilla events Better Combat emits.

### 7.2 Shield Expansion

No classes from Shield Expansion are required at compile time. Compatibility uses Forge event priority and the configured item's block use animation, so it continues to work with the mod's data-driven shield registrations.

The test matrix must verify that Shield Expansion cannot cancel incoming damage after Feather Weaponry has stopped an exhausted block.

### 7.3 Gliders

Isolate direct Glider class references in a compatibility class selected only after `ModList` confirms `vc_gliders`. Use the bundled 1.20.1 source contract:

- detect activity with `GliderUtil.isGlidingWithActiveGlider(player)`;
- retrieve the equipped glider through Glider's Curios helper;
- deactivate with `GliderItem.setGlide(stack, false)`;
- use Glider's existing synchronization behavior rather than defining ownership of its capability.

The main mod must compile and start without Glider or its transitive dependencies. Use optional/compile-only development dependencies and no mandatory `mods.toml` dependency.

## 8. Configuration Migration

On config load, v2 is migrated to v3 exactly once:

1. Preserve a backup of the pre-migration config beside the original.
2. Copy `rightClickMinFeathers` into bow, crossbow, trident, and shield limits.
3. Round `leftClickMinFeathers` upward to the nearest integer for weapon and tool limits because v3 limits are integral Feather values.
4. Move exact `minecraft:crossbow` and `minecraft:trident` entries, including multipliers, from `validBowItems` into their new lists.
5. Remove vanilla tool entries from `validWeaponItems` only when they are not one of the existing intentional axe weapon defaults. Copy recognized tool entries to `validToolItems` without duplicates.
6. Initialize `validShieldItems` with vanilla shield and `#forge:shields` rules.
7. Initialize all new costs, limits, effects, cooldowns, and compatibility settings with the defaults in this specification.
8. Set `configVersion=3` only after the migrated file is successfully written.

Unknown modded entries in `validBowItems` remain there and generate one warning asking the administrator to move crossbows or tridents manually. Migration never guesses a modded item's semantic category from its class.

If migration fails, retain v2 unchanged, log the failure clearly, and disable Feather Weaponry gameplay handlers for that server start rather than running with a partially migrated configuration.

## 9. Error Handling and Lifecycle

- Invalid rule/effect entries never crash config load; they are omitted with actionable log messages.
- Missing optional mods never load their compatibility classes.
- Player state is cleared on logout and server shutdown.
- Death, clone, dimension transfer, and respawn clear transient action sessions.
- Config reload rebuilds immutable rule/effect snapshots and resets sessions whose rules changed.
- A missing Feathers capability denies positive-limit consuming actions and logs at a rate-limited level; it never treats missing data as infinite stamina.
- All network packets are direction-checked, length-bounded, sequence-checked, and validated against server state.
- No client packet can directly specify a cost, limit, item ID, effect, or resulting feather value.

## 10. Acceptance Criteria

### 10.1 Reported bugs

1. With `rotatorCuffEnabled=false`, no Rotator Cuff damage or effects occur from any weapon, tool, bow, crossbow, or trident action.
2. With a positive action limit, the action cannot start or continue at zero usable feathers.
3. No action reduces feathers or endurance below the Feathers weight-aware floor.
4. Continuous movement, shield use, drawing/loading/charging, mining, and Glider flight cannot regenerate feathers while active. Regeneration starts no earlier than the configured delay after activity stops.
5. A shield cannot remain visually or mechanically in use after positive-limit exhaustion; normal server synchronization ends the use animation.
6. Switching from a higher-durability shield to a lower-durability shield never creates a damage cost. Only actual blocked damage does.
7. Shield Expansion cannot block an attack after the player falls below the positive shield limit.
8. At zero feathers with `cancelAnimations=true`, an uncharged crossbow cannot continue its loading animation when its limit is positive.
9. A crossbow projectile never receives the bow `lowFeatherVelocityMultiplier` solely because the crossbow was previously in `validBowItems`.
10. Mining with a `validWeaponItems` entry does not charge a weapon swing unless that item is also explicitly configured as a valid tool and tool costs are enabled.
11. Better Combat air swings charge the configured swing cost once; hits charge only the remainder.

### 10.2 Suggestions

1. `regenerationDelayTicks` controls the post-action delay and supports zero.
2. Every movement action has a limit whose zero behavior matches Section 3.4.
3. Every consuming action accepts configurable effect ID, duration, and amplifier entries.
4. Shield exhaustion applies the configured cooldown to every valid shield item.
5. Bow, crossbow, and trident rule lists operate independently.
6. A preloaded valid crossbow fired below its limit receives configurable directional spread when enabled.
7. Weapon and tool rule lists operate independently.
8. Tool mining start, per-tick, and hardness-based break costs can be configured independently.
9. Only items in `validShieldItems` receive Feather Weaponry shield behavior.
10. When enabled and installed, active Glider use consumes its configured cost and deactivates on failed positive-limit payment.

## 11. Verification Strategy

### 11.1 Unit tests

Add deterministic tests for:

- exact/tag rule parsing and independent category membership;
- config migration and idempotence;
- effect parsing and partial invalid lists;
- weight/endurance atomic budget calculations;
- fractional accumulation without premature commitment;
- positive and zero-limit results;
- regeneration deadline extension and expiry;
- melee swing/hit credit and expiry;
- hardness cost, including zero and negative hardness;
- shield cooldown expansion across exact and tag rules;
- projectile context ownership, expiry, and Multishot behavior.

Random damage and projectile spread use injected seeded randomness in tests.

### 11.2 Forge integration tests

Exercise event flows for:

- each continuous movement type;
- drawing, loading, firing, charging, and interruption;
- mining start, continuation, abort, target switch, and break;
- shield hold, actual blocked damage, exhaustion, switching, and cooldown;
- player logout, death, respawn, teleport, and config reload;
- exactly-once behavior across Forge tick phases and overlapping events.

### 11.3 Compatibility matrix

Run dedicated-server startup and gameplay checks with:

1. no optional mods;
2. Better Combat only;
3. Shield Expansion only;
4. Glider only;
5. all three together.

The base JAR must load in case 1 without `ClassNotFoundException` or optional dependency errors.

### 11.4 Manual multiplayer checks

Use a real client and dedicated server to verify:

- canceled bow/crossbow/trident and shield animations;
- no feather-bar flicker during fractional continuous costs;
- shield cooldown overlays across shield swaps;
- Better Combat air-swing timing and dual-hand behavior;
- crossbow spread for normal and Multishot projectiles;
- Glider deactivation and visible state synchronization.

## 12. Out of Scope

- Replacing or forking Feathers' capability, HUD, weight algorithm, or regeneration algorithm.
- Changing Better Combat, Shield Expansion, or Glider source code.
- Making optional integrations mandatory dependencies.
- Adding a new client configuration screen.
- Persisting transient action sessions across logout or server restart.
- Automatically classifying unknown modded items during migration.

## 13. Source Contracts Consulted

- Local `libs/feathers-1.1.jar`: `FeathersHelper`, `PlayerFeathers`, and `CommonEvents` bytecode for weight-aware spending and regeneration cooldown behavior.
- Forge 47.4.0 source: `ShieldBlockEvent`, `LivingEntityUseItemEvent`, `ArrowLooseEvent`, and the crossbow Forge patch.
- Bundled `source/Gliders`: `GliderUtil`, `GliderData`, `GliderItem`, and Forge events.
- [Better Combat 1.20.1 client events](https://github.com/ZsoltMolnarrr/BetterCombat/blob/1.20.1/common/src/main/java/net/bettercombat/api/client/BetterCombatClientEvents.java)
- [Better Combat 1.20.1 server attack handling](https://github.com/ZsoltMolnarrr/BetterCombat/blob/1.20.1/common/src/main/java/net/bettercombat/network/ServerNetwork.java)
- [Shield Expansion 1.20.1 Forge events](https://github.com/infernalstudios/Shield-Expansion/blob/1.20.1/forge/src/main/java/org/infernalstudios/shieldexp/forge/events/ForgeShieldEvents.java)
- [Shield Expansion 1.20.1 blocking mixin](https://github.com/infernalstudios/Shield-Expansion/blob/1.20.1/common/src/main/java/org/infernalstudios/shieldexp/mixin/LivingEntityMixin.java)
