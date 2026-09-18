package com.example.feathers_weaponry;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlidersCompatTest {
    @Test
    void absentGlidersIsNotActiveGliding() {
        GlidersCompat compat = new GlidersCompat(
                modId -> false,
                () -> FakeGliderApi.class.getMethod("isGlidingWithActiveGlider", Object.class),
                entity -> Optional.of(new FakeGliderData(true)));

        assertFalse(compat.isActivelyGliding(new Object()));
    }

    @Test
    void loadedGlidersDelegatesToApi() {
        GlidersCompat compat = new GlidersCompat(
                modId -> true,
                () -> FakeGliderApi.class.getMethod("isGlidingWithActiveGlider", Object.class),
                entity -> Optional.empty());

        assertTrue(compat.isActivelyGliding(new Object()));
    }

    @Test
    void changedGlidersApiFailsClosed() {
        GlidersCompat compat = new GlidersCompat(
                modId -> true,
                MissingMethodApi::method,
                entity -> Optional.empty());

        assertFalse(compat.isActivelyGliding(new Object()));
    }

    @Test
    void loadedGlidersCanFallbackToSyncedData() {
        GlidersCompat compat = new GlidersCompat(
                modId -> true,
                () -> FakeGliderApiFalse.class.getMethod("isGlidingWithActiveGlider", Object.class),
                entity -> Optional.of(new FakeGliderData(true)));

        assertTrue(compat.isActivelyGliding(new Object()));
    }

    public static final class FakeGliderApi {
        public static boolean isGlidingWithActiveGlider(Object entity) {
            return true;
        }
    }

    public static final class FakeGliderApiFalse {
        public static boolean isGlidingWithActiveGlider(Object entity) {
            return false;
        }
    }

    public record FakeGliderData(boolean gliding) {
    }

    public static final class MissingMethodApi {
        public static Method method() throws NoSuchMethodException {
            throw new NoSuchMethodException("isGlidingWithActiveGlider");
        }
    }
}
