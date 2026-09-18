package com.example.feathers_weaponry;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

final class GlidersCompat {
    private static final String MOD_ID = "vc_gliders";
    private static final GlidersCompat INSTANCE = new GlidersCompat(
            GlidersCompat::isModLoaded,
            GlidersCompat::findIsGlidingWithActiveGlider,
            GlidersCompat::findSyncedGliderData);

    private final ModLoadedChecker modLoadedChecker;
    private final MethodResolver methodResolver;
    private final GliderDataResolver gliderDataResolver;
    private Method isGlidingWithActiveGlider;
    private boolean lookupAttempted;

    GlidersCompat(ModLoadedChecker modLoadedChecker, MethodResolver methodResolver) {
        this(modLoadedChecker, methodResolver, entity -> Optional.empty());
    }

    GlidersCompat(ModLoadedChecker modLoadedChecker, MethodResolver methodResolver, GliderDataResolver gliderDataResolver) {
        this.modLoadedChecker = modLoadedChecker;
        this.methodResolver = methodResolver;
        this.gliderDataResolver = gliderDataResolver;
    }

    static boolean isActivelyGliding(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return INSTANCE.isActivelyGliding((Object) entity);
    }

    static boolean forceStopGliding(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return INSTANCE.forceStopGliding((Object) entity);
    }

    boolean isActivelyGliding(Object entity) {
        if (entity == null || !modLoadedChecker.isLoaded(MOD_ID)) {
            return false;
        }

        Method method = getIsGlidingWithActiveGlider();
        if (method == null) {
            return isGlidingFromSyncedData(entity);
        }

        try {
            if (Boolean.TRUE.equals(method.invoke(null, entity))) {
                return true;
            }
            return isGlidingFromSyncedData(entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return isGlidingFromSyncedData(entity);
        }
    }

    boolean forceStopGliding(Object entity) {
        if (entity == null || !modLoadedChecker.isLoaded(MOD_ID)) {
            return false;
        }

        try {
            Class<?> curiosUtil = Class.forName("net.venturecraft.gliders.common.compat.trinket.CuriosTrinketsUtil");
            Object instance = curiosUtil.getMethod("getInstance").invoke(null);
            Object glider = curiosUtil.getMethod("getFirstFoundGlider", LivingEntity.class).invoke(instance, entity);
            if (glider == null) {
                return false;
            }

            Class<?> gliderItem = Class.forName("net.venturecraft.gliders.common.item.GliderItem");
            gliderItem.getMethod("setGlide", glider.getClass(), boolean.class).invoke(null, glider, false);
            try {
                gliderItem.getMethod("setStruck", glider.getClass(), boolean.class).invoke(null, glider, false);
            } catch (ReflectiveOperationException ignored) {
            }

            sendFirstPersonPov(entity);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static void sendFirstPersonPov(Object entity) {
        try {
            Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            if (!serverPlayer.isInstance(entity)) {
                return;
            }
            Class<?> messagePov = findClass(
                    "net.venturecraft.gliders.network.messages.MessagePOV",
                    "net.venturecraft.gliders.network.MessagePOV");
            Object message = messagePov.getConstructor(String.class).newInstance("");
            messagePov.getMethod("send", serverPlayer).invoke(message, entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Class<?> findClass(String... classNames) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        throw last == null ? new ClassNotFoundException() : last;
    }

    private boolean isGlidingFromSyncedData(Object entity) {
        if (entity == null) {
            return false;
        }
        try {
            return gliderDataResolver.resolve(entity)
                    .map(GlidersCompat::readIsGliding)
                    .orElse(false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean readIsGliding(Object gliderData) {
        if (gliderData == null) {
            return false;
        }
        try {
            Object tag = gliderData.getClass().getMethod("serializeNBT").invoke(gliderData);
            return Boolean.TRUE.equals(tag.getClass().getMethod("getBoolean", String.class).invoke(tag, "is_gliding"));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            try {
                return Boolean.TRUE.equals(gliderData.getClass().getMethod("gliding").invoke(gliderData));
            } catch (ReflectiveOperationException | RuntimeException ignoredAgain) {
                return false;
            }
        }
    }

    private Method getIsGlidingWithActiveGlider() {
        if (lookupAttempted) {
            return isGlidingWithActiveGlider;
        }

        lookupAttempted = true;
        try {
            isGlidingWithActiveGlider = methodResolver.resolve();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return isGlidingWithActiveGlider;
    }

    private static Method findIsGlidingWithActiveGlider() throws ReflectiveOperationException {
        Class<?> gliderUtil = Class.forName("net.venturecraft.gliders.util.GliderUtil");
        return gliderUtil.getMethod("isGlidingWithActiveGlider", LivingEntity.class);
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> findSyncedGliderData(Object entity) {
        try {
            Class<?> gliderData = Class.forName("net.venturecraft.gliders.data.GliderData");
            Method get = gliderData.getMethod("get", LivingEntity.class);
            return (Optional<Object>) get.invoke(null, entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    @FunctionalInterface
    interface ModLoadedChecker {
        boolean isLoaded(String modId);
    }

    @FunctionalInterface
    interface MethodResolver {
        Method resolve() throws ReflectiveOperationException;
    }

    @FunctionalInterface
    interface GliderDataResolver {
        Optional<Object> resolve(Object entity);
    }
}
