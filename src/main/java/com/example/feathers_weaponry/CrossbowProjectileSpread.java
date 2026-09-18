package com.example.feathers_weaponry;

import net.minecraft.world.phys.Vec3;

final class CrossbowProjectileSpread {
    private CrossbowProjectileSpread() {
    }

    static Vec3 rotate(Vec3 motion, double yawDegrees, double pitchDegrees) {
        if (motion == null) {
            return Vec3.ZERO;
        }
        double speed = motion.length();
        if (speed == 0.0 || (yawDegrees == 0.0 && pitchDegrees == 0.0)) {
            return motion;
        }

        Vec3 normalized = motion.normalize();
        double yaw = Math.atan2(-normalized.x, normalized.z) + Math.toRadians(yawDegrees);
        double pitch = Math.asin(-normalized.y) + Math.toRadians(pitchDegrees);
        double maxPitch = Math.toRadians(89.0);
        pitch = Math.max(-maxPitch, Math.min(maxPitch, pitch));

        double horizontal = Math.cos(pitch);
        double x = -Math.sin(yaw) * horizontal;
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * horizontal;
        return new Vec3(x, y, z).normalize().scale(speed);
    }
}
