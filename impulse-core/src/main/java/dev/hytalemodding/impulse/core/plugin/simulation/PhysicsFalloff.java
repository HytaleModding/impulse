package dev.hytalemodding.impulse.core.plugin.simulation;

import javax.annotation.Nonnull;

/**
 * Scalar attenuation for area physics command recipes.
 */
@FunctionalInterface
public interface PhysicsFalloff {

    PhysicsFalloff CONSTANT = (distance, radius) -> distance <= radius ? 1.0f : 0.0f;
    PhysicsFalloff LINEAR = (distance, radius) -> radius > 0.0f
        ? Math.max(0.0f, 1.0f - distance / radius)
        : 0.0f;
    PhysicsFalloff SMOOTH = (distance, radius) -> {
        if (radius <= 0.0f) {
            return 0.0f;
        }
        float t = Math.max(0.0f, Math.min(1.0f, distance / radius));
        return 1.0f - (t * t * (3.0f - 2.0f * t));
    };

    float scale(float distance, float radius);

    @Nonnull
    static PhysicsFalloff constant() {
        return CONSTANT;
    }

    @Nonnull
    static PhysicsFalloff linear() {
        return LINEAR;
    }

    @Nonnull
    static PhysicsFalloff smooth() {
        return SMOOTH;
    }
}
