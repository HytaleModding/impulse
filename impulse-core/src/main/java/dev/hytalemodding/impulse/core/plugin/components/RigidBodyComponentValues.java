package dev.hytalemodding.impulse.core.plugin.components;

import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stateless conversions from ECS components to existing simulation values.
 */
public final class RigidBodyComponentValues {

    private RigidBodyComponentValues() {
    }

    public static boolean hasExplicitSpace(@Nullable RigidBodySpaceComponent space) {
        return space != null && space.getSpaceId() != null && space.getSpaceId().value() > 0;
    }

    @Nonnull
    public static PhysicsShapeSpec toShapeSpec(@Nonnull RigidBodyShapeComponent component) {
        Objects.requireNonNull(component, "component");
        return switch (component.getShapeType()) {
            case BOX -> PhysicsShapeSpec.box(component.getHalfExtentX(),
                component.getHalfExtentY(),
                component.getHalfExtentZ());
            case SPHERE -> PhysicsShapeSpec.sphere(component.getRadius());
            case CAPSULE -> PhysicsShapeSpec.capsule(component.getRadius(),
                component.getHalfHeight(),
                component.getAxis());
            case CYLINDER -> PhysicsShapeSpec.cylinder(component.getRadius(),
                component.getHalfHeight(),
                component.getAxis());
            case CONE -> PhysicsShapeSpec.cone(component.getRadius(),
                component.getHalfHeight(),
                component.getAxis());
            case PLANE -> PhysicsShapeSpec.plane(component.getGroundY());
            case VOXELS, UNKNOWN ->
                throw new IllegalArgumentException("Unsupported rigid body shape "
                    + component.getShapeType());
        };
    }

    @Nonnull
    public static RigidBodySpawnSettings toSpawnSettings(
        @Nullable RigidBodyMaterialComponent material,
        @Nullable RigidBodyCollisionComponent collision) {
        return RigidBodySpawnSettings.of(
            material != null ? material.getFriction() : null,
            material != null ? material.getRestitution() : null,
            material != null ? material.getLinearDamping() : null,
            material != null ? material.getAngularDamping() : null,
            collision != null ? collision.getCollisionGroup() : null,
            collision != null ? collision.getCollisionMask() : null,
            collision != null ? collision.isSensor() : null);
    }
}
