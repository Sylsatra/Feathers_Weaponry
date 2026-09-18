package com.example.feathers_weaponry;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

final class BetterCombatCompat {
    private static final String MOD_ID = "bettercombat";
    private static final AtomicLong ATTACK_SEQUENCE = new AtomicLong();
    private static boolean registered;

    private BetterCombatCompat() {
    }

    static void registerClient() {
        if (registered || ModList.get() == null || !ModList.get().isLoaded(MOD_ID)) {
            return;
        }

        try {
            Class<?> eventsClass = Class.forName("net.bettercombat.api.client.BetterCombatClientEvents");
            Class<?> listenerClass = Class.forName("net.bettercombat.api.client.BetterCombatClientEvents$PlayerAttackStart");
            Object publisher = eventsClass.getField("ATTACK_START").get(null);
            if (publisher == null) {
                return;
            }
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[] { listenerClass },
                    (proxy, method, args) -> {
                        if ("onPlayerAttackStart".equals(method.getName()) && args != null && args.length >= 2) {
                            Object attackHand = args[1];
                            if (attackHand != null) {
                                boolean offHand = Boolean.TRUE.equals(attackHand.getClass().getMethod("isOffHand").invoke(attackHand));
                                FeathersWeaponryNetwork.sendBetterCombatSwing(offHand, ATTACK_SEQUENCE.incrementAndGet());
                            }
                        }
                        return null;
                    });

            publisher.getClass().getMethod("register", Object.class).invoke(publisher, listener);
            registered = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
