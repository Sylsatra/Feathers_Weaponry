package com.example.feathers_weaponry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

final class FeatherDamageSources {
    private static final ResourceKey<DamageType> EXHAUSTION = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation("feathersweaponry", "exhaustion"));

    private FeatherDamageSources() {
    }

    static DamageSource exhaustion(ServerLevel level) {
        if (level == null) {
            return null;
        }
        var registry = level.registryAccess().registry(Registries.DAMAGE_TYPE);
        if (registry.isPresent()) {
            var holder = registry.get().getHolder(EXHAUSTION);
            if (holder.isPresent()) {
                return new DamageSource(holder.get());
            }
        }
        return level.damageSources().generic();
    }
}
