# Feather Action System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a server-authoritative feather action engine that fixes every approved stamina, shield, ranged, mining, and compatibility defect while adding the v3 configuration features.

**Architecture:** Focused Forge event adapters submit typed actions to one `FeatherActionService`. Immutable item/effect registries and per-player `PlayerActionState` isolate parsing and state, while optional Better Combat and Glider integrations load only when their mod IDs are present.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1/47.4.0, Feathers 1.1.0 API, JUnit 5, Mockito, Forge GameTest, optional Better Combat and Gliders APIs.

---

## Preconditions and File Structure

The current working tree contains staged user changes in the three original Java classes. Treat those changes as input, not disposable scaffolding. Before implementation, use the worktree skill to create isolation and explicitly carry the staged diff into that worktree; do not reset or overwrite the original checkout.

Create these focused units:

```text
src/main/java/com/example/feathers_weaponry/
  FeathersWeaponry.java                         # bootstrap only
  config/FeathersWeaponryConfig.java            # v3 config declarations
  config/ConfigMigration.java                    # v2 -> v3 migration
  action/ActionType.java                         # stable action enum
  action/ActionResult.java                       # decision result
  action/ActionRequest.java                      # validated action input
  action/FeatherBank.java                        # Feathers capability boundary
  action/FeathersFeatherBank.java                # production Feathers adapter
  action/FeatherActionService.java               # limits, atomic cost, regen
  action/PlayerActionState.java                  # per-player transient state
  action/PlayerActionStateStore.java             # lifecycle-safe state map
  rules/ItemRuleRegistry.java                    # six immutable rule sets
  rules/EffectRule.java                          # parsed effect value
  rules/EffectRuleParser.java                    # safe config parser
  punishment/PunishmentService.java              # damage/effects/cooldowns
  event/PlayerLifecycleEvents.java               # logout/death/config reload
  event/MovementEvents.java                      # movement + jump
  event/RangedWeaponEvents.java                  # bow/crossbow/trident
  event/MeleeToolEvents.java                     # combat + mining
  event/ShieldEvents.java                        # hold/block/compat guard
  network/NetworkHandler.java                    # validated custom channel
  network/BetterCombatSwingPacket.java           # swing intent packet
  compat/CompatibilityBootstrap.java             # optional loader boundary
  compat/bettercombat/BetterCombatCompat.java    # ATTACK_START hook
  compat/glider/GliderBridge.java                # no-op/testable bridge
  compat/glider/GliderCompat.java                # activity/deactivation bridge
src/test/java/com/example/feathers_weaponry/
  rules/ItemRuleRegistryTest.java
  rules/EffectRuleParserTest.java
  action/PlayerActionStateTest.java
  action/FeatherActionServiceTest.java
  punishment/PunishmentServiceTest.java
  config/ConfigMigrationTest.java
  event/MovementEventsTest.java
  event/RangedWeaponEventsTest.java
  event/MeleeToolEventsTest.java
  event/ShieldEventsTest.java
  network/BetterCombatSwingPacketTest.java
```

Delete `PlayerDataTracker.java` only after all state has moved and all references are gone.

## Task 1: Protect the Existing Baseline and Add the Test Harness

**Files:**
- Modify: `build.gradle`
- Create: `src/test/java/com/example/feathers_weaponry/BuildSmokeTest.java`

- [ ] **Step 1: Capture and verify the input state**

Run:

```powershell
git status --short
git diff --cached --binary > ..\feathers-weaponry-preimplementation.patch
.\gradlew.bat compileJava --no-daemon
```

Expected: the five pre-existing staged files remain listed and `BUILD SUCCESSFUL` confirms the approved baseline compiles. The patch is a safety copy outside the repository.

- [ ] **Step 2: Add JUnit 5 and Mockito**

Add to `build.gradle`:

```groovy
dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
    implementation fg.deobf("curse.maven:feathers-699933:4814309")

    testImplementation platform("org.junit:junit-bom:5.10.2")
    testImplementation "org.junit.jupiter:junit-jupiter"
    testImplementation "org.mockito:mockito-core:5.12.0"
}

tasks.named('test', Test).configure {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Add a passing harness smoke test**

Create `BuildSmokeTest.java`:

```java
package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildSmokeTest {
    @Test
    void junitRuns() {
        assertEquals(4, 2 + 2);
    }
}
```

- [ ] **Step 4: Run the harness**

Run:

```powershell
.\gradlew.bat test --tests com.example.feathers_weaponry.BuildSmokeTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`, one test passed.

- [ ] **Step 5: Commit the harness**

```powershell
git add build.gradle src/test/java/com/example/feathers_weaponry/BuildSmokeTest.java
git commit -m "test: add unit test harness"
```

## Task 2: Define the Typed Action Contract

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/action/ActionType.java`
- Create: `src/main/java/com/example/feathers_weaponry/action/ActionResult.java`
- Create: `src/main/java/com/example/feathers_weaponry/action/ActionRequest.java`
- Test: `src/test/java/com/example/feathers_weaponry/action/ActionContractTest.java`

- [ ] **Step 1: Write the failing contract test**

```java
package com.example.feathers_weaponry.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionContractTest {
    @Test
    void actionRequestRejectsInvalidNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActionRequest(ActionType.SPRINT, -1, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> new ActionRequest(ActionType.SPRINT, 1, -1, true));
    }

    @Test
    void resultDistinguishesEveryDecision() {
        assertEquals(4, ActionResult.values().length);
        assertTrue(ActionResult.ALLOWED_PAID.allowed());
        assertTrue(ActionResult.ALLOWED_ZERO_LIMIT.allowed());
        assertFalse(ActionResult.DENIED_LIMIT.allowed());
        assertFalse(ActionResult.DENIED_PAYMENT.allowed());
    }
}
```

- [ ] **Step 2: Verify the test fails**

Run:

```powershell
.\gradlew.bat test --tests '*ActionContractTest' --no-daemon
```

Expected: compilation fails because the action types do not exist.

- [ ] **Step 3: Implement the contract**

```java
package com.example.feathers_weaponry.action;

public enum ActionType {
    BOW_DRAW, CROSSBOW_LOAD, CROSSBOW_FIRE, TRIDENT_CHARGE,
    WEAPON_SWING, WEAPON_HIT,
    TOOL_MINE_START, TOOL_MINE_TICK, TOOL_BLOCK_BREAK,
    SHIELD_HOLD, SHIELD_BLOCK,
    JUMP, WALK, SPRINT, SWIM, ELYTRA_FLIGHT, GLIDER_FLIGHT
}
```

```java
package com.example.feathers_weaponry.action;

public enum ActionResult {
    ALLOWED_PAID(true), ALLOWED_ZERO_LIMIT(true),
    DENIED_LIMIT(false), DENIED_PAYMENT(false);

    private final boolean allowed;

    ActionResult(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean allowed() {
        return allowed;
    }
}
```

```java
package com.example.feathers_weaponry.action;

public record ActionRequest(ActionType type, double cost, int limit, boolean continuous) {
    public ActionRequest {
        if (type == null) throw new IllegalArgumentException("type is required");
        if (!Double.isFinite(cost) || cost < 0) throw new IllegalArgumentException("cost must be finite and non-negative");
        if (limit < 0 || limit > 20) throw new IllegalArgumentException("limit must be in 0..20");
    }
}
```

- [ ] **Step 4: Run the contract test**

Run the Step 2 command. Expected: all `ActionContractTest` tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/action src/test/java/com/example/feathers_weaponry/action/ActionContractTest.java
git commit -m "feat: define feather action contract"
```

## Task 3: Parse Independent Item and Effect Rules

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/rules/ItemRuleRegistry.java`
- Create: `src/main/java/com/example/feathers_weaponry/rules/EffectRule.java`
- Create: `src/main/java/com/example/feathers_weaponry/rules/EffectRuleParser.java`
- Test: `src/test/java/com/example/feathers_weaponry/rules/ItemRuleRegistryTest.java`
- Test: `src/test/java/com/example/feathers_weaponry/rules/EffectRuleParserTest.java`

- [ ] **Step 1: Write failing parser tests**

```java
package com.example.feathers_weaponry.rules;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemRuleRegistryTest {
    @Test
    void categoriesRemainIndependent() {
        ItemRuleRegistry registry = ItemRuleRegistry.parse(
                List.of("minecraft:bow=1.0"),
                List.of("minecraft:crossbow=1.1"),
                List.of("minecraft:trident=1.3"),
                List.of("minecraft:iron_sword=0.9"),
                List.of("#forge:tools/pickaxes=0.8"),
                List.of("#forge:shields=1.0"));

        assertEquals(1.0, registry.exactFactor(ItemRuleRegistry.Category.BOW, id("minecraft:bow")).orElseThrow());
        assertTrue(registry.exactFactor(ItemRuleRegistry.Category.BOW, id("minecraft:crossbow")).isEmpty());
        assertEquals(1.1, registry.exactFactor(ItemRuleRegistry.Category.CROSSBOW, id("minecraft:crossbow")).orElseThrow());
        assertEquals(1, registry.tagRules(ItemRuleRegistry.Category.TOOL).size());
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
```

```java
package com.example.feathers_weaponry.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EffectRuleParserTest {
    @Test
    void parsesValidEntriesAndSkipsInvalidEntries() {
        var result = EffectRuleParser.parse(List.of(
                "minecraft:slowness,200,1",
                "minecraft:levitation,60,-1",
                "bad",
                "minecraft:hunger,0,0"));

        assertEquals(2, result.rules().size());
        assertEquals(2, result.errors().size());
        assertEquals(-1, result.rules().get(1).amplifier());
    }
}
```

- [ ] **Step 2: Verify failure**

Run:

```powershell
.\gradlew.bat test --tests '*rules.*Test' --no-daemon
```

Expected: compilation fails because the parser classes do not exist.

- [ ] **Step 3: Implement immutable parsing**

Use these public contracts and complete their bodies as shown:

```java
public record EffectRule(ResourceLocation effectId, int durationTicks, int amplifier) {
    public EffectRule {
        Objects.requireNonNull(effectId);
        if (durationTicks < 1 || durationTicks > 12000) throw new IllegalArgumentException("durationTicks");
        if (amplifier < -1 || amplifier > 255) throw new IllegalArgumentException("amplifier");
    }
}
```

```java
public final class EffectRuleParser {
    public record ParseResult(List<EffectRule> rules, List<String> errors) {}

    public static ParseResult parse(List<? extends String> entries) {
        List<EffectRule> rules = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String raw : entries) {
            try {
                String[] parts = raw.split(",", -1);
                if (parts.length != 3) throw new IllegalArgumentException("expected id,duration,amplifier");
                ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
                if (id == null) throw new IllegalArgumentException("invalid effect id");
                rules.add(new EffectRule(id, Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())));
            } catch (RuntimeException ex) {
                errors.add(raw + ": " + ex.getMessage());
            }
        }
        return new ParseResult(List.copyOf(rules), List.copyOf(errors));
    }
}
```

`ItemRuleRegistry` must expose `Category`, `exactFactor`, `tagRules`, and `resolve(Category, ItemStack)`. Parse `#tag=value` separately from exact IDs; reject non-finite or negative multipliers; publish immutable maps/lists. `parse` receives the six lists in enum order used by the test.

- [ ] **Step 4: Run parser tests**

Run the Step 2 command. Expected: all parser tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/rules src/test/java/com/example/feathers_weaponry/rules
git commit -m "feat: split item and effect rule parsing"
```

## Task 4: Replace Scattered Player State

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/action/PlayerActionState.java`
- Create: `src/main/java/com/example/feathers_weaponry/action/PlayerActionStateStore.java`
- Test: `src/test/java/com/example/feathers_weaponry/action/PlayerActionStateTest.java`

- [ ] **Step 1: Write failing state tests**

```java
class PlayerActionStateTest {
    @Test
    void accumulatorsArePerActionAndOnlyYieldWholeUnits() {
        PlayerActionState state = new PlayerActionState();
        assertEquals(0, state.addFraction(ActionType.SPRINT, 0.6));
        assertEquals(1, state.addFraction(ActionType.SPRINT, 0.6));
        assertEquals(0.2, state.fraction(ActionType.SPRINT), 0.00001);
        assertEquals(0, state.addFraction(ActionType.SWIM, 0.9));
    }

    @Test
    void consumingActivityExtendsDeadline() {
        PlayerActionState state = new PlayerActionState();
        state.markConsuming(ActionType.SPRINT, 100, 20);
        assertTrue(state.suppressRegen(119));
        assertFalse(state.suppressRegen(120));
    }

    @Test
    void storeRemovesDisconnectedPlayer() {
        PlayerActionStateStore store = new PlayerActionStateStore();
        UUID id = UUID.randomUUID();
        store.getOrCreate(id);
        store.remove(id);
        assertEquals(0, store.size());
    }
}
```

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*PlayerActionStateTest' --no-daemon`. Expected: missing-class compilation failure.

- [ ] **Step 3: Implement state and store**

Use `EnumMap<ActionType, Double>` for fractions, `EnumMap<ActionType, Long>` for dedupe ticks, and `EnumMap<ActionType, Long>` for punishment timestamps. `addFraction` must reject invalid input, retain the fractional remainder, and return the whole portion without committing it to Feathers. Add `restoreWhole(ActionType, int)` for failed positive-limit atomic payments, plus `lastPunishmentTick(ActionType)` and `setLastPunishmentTick(ActionType, long)`. Implement:

```java
public void markConsuming(ActionType type, long tick, int delay) {
    activeActions.add(type);
    lastConsumingTick = Math.max(lastConsumingTick, tick);
    regenerationDeadline = Math.max(regenerationDeadline, tick + delay);
}

public boolean suppressRegen(long tick) {
    return !activeActions.isEmpty() || tick < regenerationDeadline;
}

public void beginTick() {
    activeActions.clear();
}
```

Also add typed nested records for `MiningSession`, `ShieldSession`, `SwingCredit`, and `CrossbowProjectileContext` with expiry ticks. `PlayerActionStateStore` wraps a `ConcurrentHashMap<UUID, PlayerActionState>` and exposes only `getOrCreate`, `remove`, `clear`, and package-private `size` for tests.

- [ ] **Step 4: Run tests**

Run the Step 2 command. Expected: all state tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/action/PlayerActionState.java src/main/java/com/example/feathers_weaponry/action/PlayerActionStateStore.java src/test/java/com/example/feathers_weaponry/action/PlayerActionStateTest.java
git commit -m "feat: add per-player action state"
```

## Task 5: Implement Atomic Weight-Aware Spending and Regeneration Suppression

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/action/FeatherBank.java`
- Create: `src/main/java/com/example/feathers_weaponry/action/FeathersFeatherBank.java`
- Create: `src/main/java/com/example/feathers_weaponry/action/FeatherActionService.java`
- Test: `src/test/java/com/example/feathers_weaponry/action/FeatherActionServiceTest.java`

- [ ] **Step 1: Write failing service tests**

```java
@ExtendWith(MockitoExtension.class)
class FeatherActionServiceTest {
    @Mock FeatherBank bank;
    private PlayerActionState state;
    private FeatherActionService service;

    @BeforeEach
    void setUp() {
        state = new PlayerActionState();
        service = new FeatherActionService(bank);
    }

    @Test
    void deniesBeforeMutatingWhenBelowLimit() {
        when(bank.feathers()).thenReturn(0);
        assertEquals(ActionResult.DENIED_LIMIT,
                service.perform(new ActionRequest(ActionType.SPRINT, 1, 1, true), state, 10, 20));
        verify(bank, never()).spend(anyInt());
    }

    @Test
    void restoresAccumulatorWhenWeightAwareSpendFails() {
        when(bank.feathers()).thenReturn(5);
        when(bank.spend(1)).thenReturn(false);
        assertEquals(ActionResult.DENIED_PAYMENT,
                service.perform(new ActionRequest(ActionType.SPRINT, 1, 1, true), state, 10, 20));
        assertEquals(1.0, state.fraction(ActionType.SPRINT));
    }

    @Test
    void fractionalActivitySuppressesRegenBeforeWholeSpend() {
        when(bank.feathers()).thenReturn(5);
        assertEquals(ActionResult.ALLOWED_PAID,
                service.perform(new ActionRequest(ActionType.SPRINT, 0.25, 1, true), state, 10, 20));
        verify(bank).resetCooldown();
        verify(bank, never()).spend(anyInt());
    }

    @Test
    void zeroLimitAllowsFailedSpendAndStillSuppressesRegen() {
        when(bank.feathers()).thenReturn(0);
        when(bank.spend(1)).thenReturn(false);
        assertEquals(ActionResult.ALLOWED_ZERO_LIMIT,
                service.perform(new ActionRequest(ActionType.SPRINT, 1, 0, true), state, 10, 20));
        assertTrue(state.suppressRegen(10));
        assertEquals(0.0, state.fraction(ActionType.SPRINT));
    }
}
```

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*FeatherActionServiceTest' --no-daemon`. Expected: missing service/bank compilation failure.

- [ ] **Step 3: Implement the bank boundary**

```java
public interface FeatherBank {
    int feathers();
    boolean spend(int amount);
    void resetCooldown();
}
```

`FeathersFeatherBank` accepts a `ServerPlayer`. `feathers()` calls `FeathersHelper.getFeathers`. `spend` calls `FeathersHelper.spendFeathers`, thereby preserving endurance and the armor-weight floor. `resetCooldown` accesses `PlayerFeathersProvider.PLAYER_FEATHERS` and calls `PlayerFeathers#setCooldown(0)` without changing the feather count.

- [ ] **Step 4: Implement the decision algorithm**

```java
public ActionResult perform(ActionRequest request, PlayerActionState state, long tick, int regenDelay) {
    if (request.limit() > 0 && bank.feathers() < request.limit()) return ActionResult.DENIED_LIMIT;

    if (request.cost() > 0 || request.limit() == 0 && request.continuous()) {
        state.markConsuming(request.type(), tick, regenDelay);
        bank.resetCooldown();
    }

    int whole = state.addFraction(request.type(), request.cost());
    if (whole == 0) return request.limit() == 0
            ? ActionResult.ALLOWED_ZERO_LIMIT : ActionResult.ALLOWED_PAID;

    if (bank.spend(whole)) return request.limit() == 0
            ? ActionResult.ALLOWED_ZERO_LIMIT : ActionResult.ALLOWED_PAID;

    if (request.limit() == 0) return ActionResult.ALLOWED_ZERO_LIMIT;
    state.restoreWhole(request.type(), whole);
    return ActionResult.DENIED_PAYMENT;
}
```

Add `suppressRegeneration(PlayerActionState, long)` that invokes `bank.resetCooldown()` while `state.suppressRegen(tick)` is true.

- [ ] **Step 5: Run service tests and compile production code**

Run:

```powershell
.\gradlew.bat test --tests '*FeatherActionServiceTest' --no-daemon
.\gradlew.bat compileJava --no-daemon
```

Expected: both commands succeed.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/action src/test/java/com/example/feathers_weaponry/action/FeatherActionServiceTest.java
git commit -m "feat: enforce atomic feather spending"
```

## Task 6: Introduce the v3 Configuration and Effect Defaults

**Files:**
- Move/Modify: `src/main/java/com/example/feathers_weaponry/FeathersWeaponryConfig.java` to `src/main/java/com/example/feathers_weaponry/config/FeathersWeaponryConfig.java`
- Modify: all imports referencing the old package
- Test: `src/test/java/com/example/feathers_weaponry/config/FeathersWeaponryConfigTest.java`

- [ ] **Step 1: Write a failing schema test**

Build a test config with `FeathersWeaponryConfig.SPEC` and assert these exact paths exist and accept boundary values:

```java
assertEquals(3, FeathersWeaponryConfig.configVersion.get());
assertEquals(20, FeathersWeaponryConfig.regenerationDelayTicks.get());
assertEquals(List.of("minecraft:bow=1.0"), FeathersWeaponryConfig.validBowItems.get());
assertEquals(List.of("minecraft:crossbow=1.1"), FeathersWeaponryConfig.validCrossbowItems.get());
assertEquals(List.of("minecraft:trident=1.3"), FeathersWeaponryConfig.validTridentItems.get());
assertEquals(1, FeathersWeaponryConfig.shieldLimit.get());
assertEquals(100, FeathersWeaponryConfig.shieldExhaustionCooldownTicks.get());
assertTrue(FeathersWeaponryConfig.lowFeatherInaccuracyEnabled.get());
assertEquals(10.0, FeathersWeaponryConfig.lowFeatherInaccuracyDegrees.get());
assertFalse(FeathersWeaponryConfig.gliderIntegrationEnabled.get());
```

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*FeathersWeaponryConfigTest' --no-daemon`. Expected: missing v3 fields.

- [ ] **Step 3: Implement every approved config field**

Move the class into `config`. Keep `CONFIG` as a compatibility alias for `SPEC`. Define the six item lists, `regenerationDelayTicks`, all movement cost/limit/effect triples, jump cost/limit/effects, ranged base costs/limits, tool settings, shield limit/cooldown/effects, crossbow inaccuracy, Rotator Cuff severity effects, and Glider settings with the exact defaults and ranges from the spec.

Use this validator for effect lists:

```java
private static boolean effectEntry(Object value) {
    return value instanceof String string && !EffectRuleParser.parse(List.of(string)).rules().isEmpty();
}
```

Do not remove legacy right/left limit keys yet; mark them deprecated and retain them as migration inputs.

- [ ] **Step 4: Run schema tests and compile**

Run the Step 2 command followed by `./gradlew.bat compileJava --no-daemon`. Expected: both pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/config src/main/java/com/example/feathers_weaponry src/test/java/com/example/feathers_weaponry/config
git commit -m "feat: add version 3 action configuration"
```

## Task 7: Centralize Punishments

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/punishment/PunishmentService.java`
- Test: `src/test/java/com/example/feathers_weaponry/punishment/PunishmentServiceTest.java`

- [ ] **Step 1: Write failing punishment tests**

Inject a seeded `Random`, effect sink, damage sink, and fresh player state. Use the production `PunishmentContext` record defined in Step 3 and these exact cases:

```java
ServerPlayer player = mock(ServerPlayer.class);
EffectSink effectSink = mock(EffectSink.class);
DamageSink damageSink = mock(DamageSink.class);
PlayerActionState state = new PlayerActionState();
PunishmentService service = new PunishmentService(new Random(7), effectSink, damageSink, state);
List<EffectRule> slowness = List.of(new EffectRule(
        ResourceLocation.tryParse("minecraft:slowness"), 200, 1));

PunishmentContext disabled = new PunishmentContext(
        player, ActionType.WEAPON_SWING, 0, Difficulty.HARD, 100,
        false, false, 1, 6, 100, slowness, slowness, slowness);
assertFalse(service.applyRotatorCuff(disabled));
verifyNoInteractions(effectSink, damageSink);

PunishmentContext wrongDifficulty = new PunishmentContext(
        player, ActionType.WEAPON_SWING, 0, Difficulty.NORMAL, 100,
        true, true, 1, 6, 100, slowness, slowness, slowness);
assertFalse(service.applyRotatorCuff(wrongDifficulty));
verifyNoInteractions(effectSink, damageSink);

PunishmentContext enabled = new PunishmentContext(
        player, ActionType.WEAPON_SWING, 0, Difficulty.HARD, 100,
        true, true, 1, 6, 100, slowness, slowness, slowness);
assertTrue(service.applyRotatorCuff(enabled));
verify(effectSink).apply(eq(player), eq(slowness.get(0)));
assertFalse(service.applyRotatorCuff(enabled));
```

The last assertion proves an accepted invocation updates its cooldown timestamp, while the first two prove rejected invocations do not apply damage/effects.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*PunishmentServiceTest' --no-daemon`. Expected: missing service compilation failure.

- [ ] **Step 3: Implement punishment dispatch**

Define these nested public contracts inside `PunishmentService` (the test imports `PunishmentService.*`):

```java
public record PunishmentContext(
        ServerPlayer player, ActionType action, int currentFeathers,
        Difficulty difficulty, long gameTick, boolean enabled, boolean hardOnly,
        double minDamage, double maxDamage, long cooldownTicks,
        List<EffectRule> minorEffects, List<EffectRule> moderateEffects,
        List<EffectRule> severeEffects) {}

@FunctionalInterface
public interface EffectSink {
    void apply(ServerPlayer player, EffectRule effect);
}

@FunctionalInterface
public interface DamageSink {
    void apply(ServerPlayer player, float amount);
}
```

`PunishmentService(Random, EffectSink, DamageSink, PlayerActionState)` returns `false` before updating state if disabled, wrong difficulty, above the exhaustion threshold, or cooling down. The production effect sink resolves each `EffectRule.effectId()` through `BuiltInRegistries.MOB_EFFECT`, skips absent IDs, and applies a `MobEffectInstance`. Use the injected random source to select Rotator Cuff severity and damage, then the matching minor/moderate/severe list.

- [ ] **Step 4: Run tests**

Run the Step 2 command. Expected: all punishment tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/punishment src/test/java/com/example/feathers_weaponry/punishment
git commit -m "feat: centralize configurable punishments"
```

## Task 8: Wire Lifecycle, One-Phase Ticking, and Movement

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/event/PlayerLifecycleEvents.java`
- Create: `src/main/java/com/example/feathers_weaponry/event/MovementEvents.java`
- Modify: `src/main/java/com/example/feathers_weaponry/FeathersWeaponry.java`
- Test: `src/test/java/com/example/feathers_weaponry/event/MovementEventsTest.java`

- [ ] **Step 1: Write failing movement tests**

Mock a `ServerPlayer` and service. Verify `START` does nothing, `END` submits only one highest-priority activity, horizontal displacement drives walking, teleport-sized displacement resets position, and denied results invoke the exact stop method:

```java
verify(player).setSprinting(false);       // denied SPRINT
verify(player).stopFallFlying();          // denied ELYTRA_FLIGHT
verify(service, times(1)).perform(any(), any(), eq(gameTick), eq(regenDelay));
```

Verify a fractional continuous request invokes regeneration suppression on each `END` tick.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*MovementEventsTest' --no-daemon`. Expected: missing adapter compilation failure.

- [ ] **Step 3: Implement movement selection**

In `onPlayerTick`, return unless `event.phase == TickEvent.Phase.END`, server side, and `event.player instanceof ServerPlayer`. Call `state.beginTick()`, select exactly one action in this order: Glider, swim, sprint, fall-flying, horizontal walk. Submit its configured per-tick cost (`costPerSecond / 20.0`) and limit. Apply stop behavior for denied results. Always call `service.suppressRegeneration(state, tick)` after action handling.

Handle `LivingJumpEvent` separately with `JUMP`, using per-tick deduplication.

- [ ] **Step 4: Implement lifecycle cleanup**

Subscribe to logout, death/clone, dimension change, server stopping, and config reload. Remove or reset transient state and rebuild immutable rule/effect snapshots. Bootstrap all event adapters from `FeathersWeaponry`; remove the original nested `EventHandlers` registration only after replacement handlers compile.

- [ ] **Step 5: Run tests and compile**

Run:

```powershell
.\gradlew.bat test --tests '*MovementEventsTest' --no-daemon
.\gradlew.bat compileJava --no-daemon
```

Expected: success and no duplicate event registration.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/event src/main/java/com/example/feathers_weaponry/FeathersWeaponry.java src/test/java/com/example/feathers_weaponry/event/MovementEventsTest.java
git commit -m "feat: route movement through action engine"
```

## Task 9: Separate Bow, Crossbow, and Trident Flows

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/event/RangedWeaponEvents.java`
- Test: `src/test/java/com/example/feathers_weaponry/event/RangedWeaponEventsTest.java`

- [ ] **Step 1: Write failing classification and cancellation tests**

Test that each matching rule list produces only its own `ActionType`. Test an uncharged crossbow at zero feathers with `cancelAnimations=true`: `LivingEntityUseItemEvent.Tick` is canceled and `stopUsingItem()` is called. Test a preloaded crossbow below its limit creates projectile context but is allowed to fire.

Test that `EntityJoinLevelEvent`:

- does not change an arrow without crossbow context;
- changes direction but not speed magnitude/base damage with valid context;
- changes every same-owner Multishot projectile during the context tick;
- never applies bow velocity reduction to crossbow context;
- expires context after the firing tick.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*RangedWeaponEventsTest' --no-daemon`. Expected: missing adapter compilation failure.

- [ ] **Step 3: Implement use-session handling**

Use `LivingEntityUseItemEvent.Start`, `.Tick`, and `.Stop` to classify by rule registry in this strict order based on the event stack: CROSSBOW, TRIDENT, BOW. Charged crossbows request `CROSSBOW_FIRE`; uncharged ones request `CROSSBOW_LOAD`. Denied load/draw/charge cancels the event and calls `stopUsingItem()` when `cancelAnimations` is true. Reset the session on stop, item fingerprint change, or logout.

- [ ] **Step 4: Implement firing and projectile context**

In `ArrowLooseEvent`, inspect `event.getBow()` and mark a `CrossbowProjectileContext(playerUUID, gameTick, configuredDegrees)` only for a fully charged valid crossbow fired below its positive limit. For crossbow denial that is not the preloaded-zero exception, set charge to `-1` and cancel. On arrow join, rotate the existing normalized velocity by independently sampled yaw/pitch in `[-degrees, degrees]`, multiply by the original speed, and preserve `arrow.getBaseDamage()`.

Keep trident join handling separate. Apply `lowFeatherVelocityMultiplier` only when the source session was `BOW_DRAW` and `velocityReductionEnabled` is true.

- [ ] **Step 5: Run tests and compile**

Run the Step 2 command and `./gradlew.bat compileJava --no-daemon`. Expected: success.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/event/RangedWeaponEvents.java src/test/java/com/example/feathers_weaponry/event/RangedWeaponEventsTest.java
git commit -m "fix: isolate ranged weapon action flows"
```

## Task 10: Separate Melee Combat from Tool Mining

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/event/MeleeToolEvents.java`
- Test: `src/test/java/com/example/feathers_weaponry/event/MeleeToolEventsTest.java`

- [ ] **Step 1: Write failing melee and mining tests**

Cover these exact cases:

```java
// Weapon configured only as WEAPON: LeftClickBlock submits no action.
verify(service, never()).perform(argThat(r -> r.type().name().startsWith("TOOL_")), any(), anyLong(), anyInt());

// Tool start, tick, and break use three independent action types.
verify(service).perform(argThat(r -> r.type() == ActionType.TOOL_MINE_START), any(), anyLong(), anyInt());
verify(service).perform(argThat(r -> r.type() == ActionType.TOOL_MINE_TICK), any(), anyLong(), anyInt());
verify(service).perform(argThat(r -> r.type() == ActionType.TOOL_BLOCK_BREAK), any(), anyLong(), anyInt());
```

Assert break hardness `-1` produces zero cost, hardness `3` with factor `0.5` and item multiplier `0.8` produces `1.2` before learning, a canceled break is not charged, swing credit reduces a hit only within its expiry, and each event is deduplicated.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*MeleeToolEventsTest' --no-daemon`. Expected: missing adapter compilation failure.

- [ ] **Step 3: Implement melee charging**

On `AttackEntityEvent`, request `WEAPON_SWING` if the active hand resolves in the weapon rules. Record a `SwingCredit(costPaid, expiryTick)`. On damage caused by that player, request `WEAPON_HIT` for `max(0, fullHitCost - validCredit)`, then clear credit. Vanilla server-observed air swing is used only when Better Combat is absent and must share the same dedupe token.

- [ ] **Step 4: Implement mining sessions**

On `LeftClickBlock`, start a session only for a TOOL rule. On `END` tick, confirm the player is still destroying the same block with the same item fingerprint before requesting `TOOL_MINE_TICK`. On `BreakEvent`, compute:

```java
double hardness = Math.max(0.0, state.getDestroySpeed(level, pos));
double cost = hardness * FeathersWeaponryConfig.blockBreakHardnessMultiplier.get()
        * toolFactor * learningMultiplier;
```

Cancel the interaction/break on a denied positive-limit result. Clear sessions on abort, target/item change, death, teleport, or disconnect. Never consult weapon rules in mining handlers.

- [ ] **Step 5: Run tests and compile**

Run the Step 2 command and `./gradlew.bat compileJava --no-daemon`. Expected: success.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/event/MeleeToolEvents.java src/test/java/com/example/feathers_weaponry/event/MeleeToolEventsTest.java
git commit -m "fix: separate melee and mining costs"
```

## Task 11: Make Shields Item-Configured, Damage-Accurate, and Expansion-Compatible

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/event/ShieldEvents.java`
- Test: `src/test/java/com/example/feathers_weaponry/event/ShieldEventsTest.java`

- [ ] **Step 1: Write failing shield tests**

Assert:

- only SHIELD rules are recognized, independent of `ShieldItem` inheritance;
- swapping to a lower-durability shield charges nothing;
- `getBlockedDamage() * shieldDamageFactor * itemMultiplier` is submitted exactly once;
- zero feathers/positive limit cancels start and stops use;
- failed hold/block payment stops use and applies cooldown to every resolved shield item;
- a `LivingAttackEvent` guard at highest priority calls `stopUsingItem()` but does not cancel the attack;
- limit zero allows blocking and suppresses regeneration without forced cooldown.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*ShieldEventsTest' --no-daemon`. Expected: missing adapter compilation failure.

- [ ] **Step 3: Implement shield use and damage**

Use `LivingEntityUseItemEvent.Start/Tick` for SHIELD rule stacks. Submit `SHIELD_HOLD` on the configured interval. Use `ShieldBlockEvent#getBlockedDamage()` for `SHIELD_BLOCK`; delete all durability-delta fields and logic.

On exhaustion or payment denial, call:

```java
player.stopUsingItem();
for (Item item : rules.resolveAllItems(ItemRuleRegistry.Category.SHIELD)) {
    player.getCooldowns().addCooldown(item, FeathersWeaponryConfig.shieldExhaustionCooldownTicks.get());
}
```

- [ ] **Step 4: Implement Shield Expansion guard**

Subscribe with `@SubscribeEvent(priority = EventPriority.HIGHEST)`. If a server player is using a configured SHIELD rule stack with block animation and is below a positive shield limit, stop item use. Do not cancel or alter damage. This must execute before Shield Expansion's default-priority `LivingAttackEvent` handler.

- [ ] **Step 5: Run tests and compile**

Run the Step 2 command and `./gradlew.bat compileJava --no-daemon`. Expected: success.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/event/ShieldEvents.java src/test/java/com/example/feathers_weaponry/event/ShieldEventsTest.java
git commit -m "fix: enforce configured shield exhaustion"
```

## Task 12: Add Validated Better Combat Air-Swing Integration

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/network/NetworkHandler.java`
- Create: `src/main/java/com/example/feathers_weaponry/network/BetterCombatSwingPacket.java`
- Create: `src/main/java/com/example/feathers_weaponry/compat/CompatibilityBootstrap.java`
- Create: `src/main/java/com/example/feathers_weaponry/compat/bettercombat/BetterCombatCompat.java`
- Modify: `build.gradle`
- Test: `src/test/java/com/example/feathers_weaponry/network/BetterCombatSwingPacketTest.java`

- [ ] **Step 1: Write failing packet validation tests**

Encode/decode `InteractionHand` plus a non-negative sequence. Test rejection for stale sequence, absent Better Combat, invalid held item, excessive request rate, dead/spectator sender, and same-tick duplicate. Test acceptance submits exactly one `WEAPON_SWING`; the packet cannot contain cost, item ID, feather value, or effects.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*BetterCombatSwingPacketTest' --no-daemon`. Expected: missing packet compilation failure.

- [ ] **Step 3: Implement the channel and packet**

Register a `SimpleChannel` with protocol version `1`. Define:

```java
public record BetterCombatSwingPacket(InteractionHand hand, long sequence) {
    static void encode(BetterCombatSwingPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.hand);
        buf.writeVarLong(packet.sequence);
    }

    static BetterCombatSwingPacket decode(FriendlyByteBuf buf) {
        return new BetterCombatSwingPacket(buf.readEnum(InteractionHand.class), buf.readVarLong());
    }
}
```

The handler enqueues server work, validates every condition from spec Section 7.1, derives the held item/cost on the server, records the accepted sequence/tick in player state, and submits `WEAPON_SWING`.

- [ ] **Step 4: Implement optional client registration**

Add `maven { url "https://api.modrinth.com/maven" }` to repositories and this non-transitive compile-only dependency:

```groovy
compileOnly(fg.deobf("maven.modrinth:better-combat:1.9.0+1.20.1-forge")) {
    transitive = false
}
```

Do not add a mandatory `mods.toml` entry. `CompatibilityBootstrap` calls `BetterCombatCompat.registerClient()` only when `ModList.get().isLoaded("bettercombat")` and the physical side is client. The compat class registers `BetterCombatClientEvents.ATTACK_START` and sends an incrementing sequence with the API-provided attack hand.

- [ ] **Step 5: Run tests and both startup modes**

Run packet tests and `compileJava`. Then start a server without Better Combat and verify no linkage error. Add Better Combat to the development run and verify one air swing yields one accepted packet in debug logging.

- [ ] **Step 6: Commit**

```powershell
git add build.gradle src/main/java/com/example/feathers_weaponry/network src/main/java/com/example/feathers_weaponry/compat src/test/java/com/example/feathers_weaponry/network
git commit -m "feat: support Better Combat air swings"
```

## Task 13: Add Optional Glider Consumption

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/compat/glider/GliderBridge.java`
- Create: `src/main/java/com/example/feathers_weaponry/compat/glider/GliderCompat.java`
- Modify: `src/main/java/com/example/feathers_weaponry/compat/CompatibilityBootstrap.java`
- Modify: `src/main/java/com/example/feathers_weaponry/event/MovementEvents.java`
- Modify: `build.gradle`
- Test: `src/test/java/com/example/feathers_weaponry/compat/GliderCompatTest.java`

- [ ] **Step 1: Write failing bridge tests**

Abstract Glider calls behind:

```java
public interface GliderBridge {
    boolean isActive(ServerPlayer player);
    void deactivate(ServerPlayer player);
}
```

Test that disabled config or absent mod reports inactive, active flight submits `GLIDER_FLIGHT` before all other movement, positive-limit denial calls `deactivate`, and zero-limit exhaustion remains active while regeneration is suppressed.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*GliderCompatTest' --no-daemon`. Expected: missing compatibility class.

- [ ] **Step 3: Implement optional linkage**

Use `libs/gliders-forge-1.2.0.jar` as `compileOnly fg.deobf(files(...))` for development. Keep direct `net.venturecraft.gliders` references inside `GliderCompat`. Detect activity with `GliderUtil.isGlidingWithActiveGlider`, retrieve the equipped stack with Glider's Curios helper, and deactivate with `GliderItem.setGlide(stack, false)`. Trigger Glider's normal data synchronization after deactivation.

`CompatibilityBootstrap` returns a no-op bridge unless both `vc_gliders` is loaded and `gliderIntegrationEnabled` is true.

- [ ] **Step 4: Run tests and startup matrix**

Run Glider tests, compile, then start once without the Glider JAR and once with it. Expected: no linkage failure without it; configured per-second drain and deactivation with it.

- [ ] **Step 5: Commit**

```powershell
git add build.gradle src/main/java/com/example/feathers_weaponry/compat src/main/java/com/example/feathers_weaponry/event/MovementEvents.java src/test/java/com/example/feathers_weaponry/compat
git commit -m "feat: add optional Glider feather costs"
```

## Task 14: Implement Safe v2-to-v3 Migration

**Files:**
- Create: `src/main/java/com/example/feathers_weaponry/config/ConfigMigration.java`
- Modify: `src/main/java/com/example/feathers_weaponry/event/PlayerLifecycleEvents.java`
- Test: `src/test/java/com/example/feathers_weaponry/config/ConfigMigrationTest.java`

- [ ] **Step 1: Write failing migration tests**

Use temporary TOML files. Assert migration:

- creates a byte-for-byte `.v2.bak` backup;
- preserves multipliers;
- moves exact vanilla crossbow/trident rules;
- keeps unknown modded bow entries and records warnings;
- seeds right-click limits directly;
- rounds `leftClickMinFeathers=0.5` to weapon/tool limit `1`;
- adds shield and tool defaults without duplicates;
- writes version `3` last;
- is idempotent on a second run;
- leaves v2 unchanged and returns failure when output cannot be written.

- [ ] **Step 2: Verify failure**

Run `./gradlew.bat test --tests '*ConfigMigrationTest' --no-daemon`. Expected: missing migration class.

- [ ] **Step 3: Implement migration as a pure transform plus atomic writer**

Expose:

```java
public record MigrationResult(boolean migrated, boolean playable, List<String> warnings) {}

public static MigrationResult migrate(Path configPath) throws IOException
```

Read with NightConfig, copy the original to `<filename>.v2.bak`, transform a detached config tree, write to a sibling temporary file, force/close it, and atomically replace the original. Set `general.configVersion=3` only in the final transformed tree. On failure, delete only the temporary output, retain original and backup, return `playable=false`, and let bootstrap skip gameplay handler registration for that server start.

- [ ] **Step 4: Run migration tests**

Run the Step 2 command. Expected: all migration tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/feathers_weaponry/config/ConfigMigration.java src/main/java/com/example/feathers_weaponry/event/PlayerLifecycleEvents.java src/test/java/com/example/feathers_weaponry/config/ConfigMigrationTest.java
git commit -m "feat: migrate weaponry config to version 3"
```

## Task 15: Remove Legacy Paths and Add Regression GameTests

**Files:**
- Delete: `src/main/java/com/example/feathers_weaponry/PlayerDataTracker.java`
- Modify: `src/main/java/com/example/feathers_weaponry/FeathersWeaponry.java`
- Create: `src/main/java/com/example/feathers_weaponry/gametest/FeatherActionGameTests.java`
- Modify: `src/main/resources/META-INF/mods.toml`
- Modify: `TESTING_GUIDE.md`

- [ ] **Step 1: Prove no legacy calls remain**

Run:

```powershell
rg -n "PlayerDataTracker|subtractFeathers|subtractFeathersDouble|lastShieldDurability|instanceof ShieldItem|validBowItems.*crossbow|validBowItems.*trident" src/main/java
```

Expected before cleanup: matches identify remaining legacy code. Expected after cleanup: no matches except migration/config declarations that intentionally mention old keys.

- [ ] **Step 2: Remove the monolith safely**

Reduce `FeathersWeaponry` to config registration, migration/bootstrap, network registration, compatibility bootstrap, and event adapter registration. Delete old rule caches, direct subtraction helpers, nested event handlers, durability shield tracking, shared bow classification, and `PlayerDataTracker`. Keep learning-curve calculations in a focused helper if adapters still use them.

- [ ] **Step 3: Add deterministic GameTests**

Register seven `@GameTest(template = "empty", timeoutTicks = 100)` methods named `disabledRotatorCuffDoesNothing`, `continuousCostSuppressesRegen`, `shieldSwitchDoesNotChargeDamage`, `zeroFeatherPositiveLimitDenies`, `weightFloorIsPreserved`, `crossbowRulesDoNotUseBowVelocity`, and `weaponRulesDoNotChargeMining`. Each method must create a mock server player through the GameTest helper, set its initial Feather capability, invoke the production event adapter, schedule its assertion after the relevant event/tick, report the observed and expected feather values in `helper.assertTrue`, and call `helper.succeed()` only after every assertion passes.

- [ ] **Step 4: Update metadata and manual guide**

Keep Feathers mandatory. Document Better Combat, Shield Expansion, and `vc_gliders` as optional integrations without mandatory dependency blocks. Replace stale tests in `TESTING_GUIDE.md` with the exact compatibility matrix and client-visible checks from spec Section 11.

- [ ] **Step 5: Run all automated verification**

Run:

```powershell
.\gradlew.bat clean test compileJava build --no-daemon
.\gradlew.bat runGameTestServer --no-daemon
```

Expected: unit tests pass, production compilation passes, JAR builds, and GameTest server exits successfully with all registered tests passing.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java src/main/resources/META-INF/mods.toml TESTING_GUIDE.md
git commit -m "test: cover feather action regressions"
```

## Task 16: Run the Optional-Mod Compatibility Matrix

**Files:**
- Read: production files introduced in Tasks 8-13
- Update: `TESTING_GUIDE.md` with observed versions/results

- [ ] **Step 1: Verify no optional mods**

Launch the dedicated server with only Forge, Feathers, and Feathers Weaponry. Confirm startup, join, movement drain, shield use, and ranged use. Expected: no optional-class linkage errors.

- [ ] **Step 2: Verify Better Combat only**

Install its 1.20.1 build and dependencies. Confirm main-hand, off-hand, hit, miss, and zero-target air swings each charge once; a later hit charges only the remainder.

- [ ] **Step 3: Verify Shield Expansion only**

Confirm every configured vanilla/modded shield stops blocking at exhaustion, the incoming hit lands, swapping shields cannot bypass the cooldown, and actual blocked damage—not durability/switching—drives cost.

- [ ] **Step 4: Verify Glider only**

With integration enabled, confirm cost, limit, regeneration suppression, zero-limit behavior, exhaustion deactivation, and visible client synchronization.

- [ ] **Step 5: Verify all optional mods together**

Repeat the critical melee, shield, ranged, movement, and Glider checks. Confirm one action never triggers two categories and no event-order conflict restores blocked behavior.

- [ ] **Step 6: Record evidence and run final build**

Add tested mod versions and pass/fail results to `TESTING_GUIDE.md`, then run:

```powershell
.\gradlew.bat clean test build --no-daemon
```

Expected: `BUILD SUCCESSFUL` with no failing tests.

- [ ] **Step 7: Commit verification notes**

```powershell
git add TESTING_GUIDE.md
git commit -m "docs: record compatibility verification"
```

## Task 17: Final Spec Coverage Audit

**Files:**
- Read: `docs/superpowers/specs/2026-06-23-feather-action-system-design.md`
- Read: all files changed by Tasks 1-16

- [ ] **Step 1: Map every bug to evidence**

Create a temporary checklist from spec Section 10.1 and attach one unit test, GameTest, or manual matrix result to each of its eleven entries. Do not close an entry based only on code inspection.

- [ ] **Step 2: Map every suggestion to evidence**

Repeat for all ten entries in spec Section 10.2. Confirm config keys are generated with documented defaults and ranges.

- [ ] **Step 3: Scan for forbidden legacy behavior**

Run:

```powershell
rg -n "subFeathers\(|setFeathers\(|lastShieldDurability|PlayerDataTracker|validBowItems.*(crossbow|trident)" src/main/java
```

Expected: no direct deduction, durability inference, obsolete state, or shared default list remains outside the production Feathers adapter and migration code.

- [ ] **Step 4: Run final verification from a clean state**

```powershell
.\gradlew.bat clean test compileJava build --no-daemon
git status --short
```

Expected: `BUILD SUCCESSFUL`; only intentionally untracked local runtime artifacts may remain, and no source/test changes are uncommitted.

- [ ] **Step 5: Request code review**

Invoke `superpowers:requesting-code-review` and provide the spec, this plan, the commit range, automated output, and compatibility matrix. Address any correctness findings before merging.
