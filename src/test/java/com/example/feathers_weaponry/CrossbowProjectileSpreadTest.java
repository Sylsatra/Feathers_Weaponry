package com.example.feathers_weaponry;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CrossbowProjectileSpreadTest {
    @Test
    void zeroSpreadKeepsMotionUnchanged() {
        Vec3 motion = new Vec3(0.0, 0.0, 3.0);

        Vec3 spread = CrossbowProjectileSpread.rotate(motion, 0.0, 0.0);

        assertEquals(motion.x, spread.x, 0.000001);
        assertEquals(motion.y, spread.y, 0.000001);
        assertEquals(motion.z, spread.z, 0.000001);
    }

    @Test
    void spreadChangesDirectionButKeepsSpeed() {
        Vec3 motion = new Vec3(0.0, 0.0, 3.0);

        Vec3 spread = CrossbowProjectileSpread.rotate(motion, 10.0, -5.0);

        assertEquals(motion.length(), spread.length(), 0.000001);
        assertNotEquals(motion.normalize().x, spread.normalize().x, 0.000001);
        assertNotEquals(motion.normalize().y, spread.normalize().y, 0.000001);
    }
}
