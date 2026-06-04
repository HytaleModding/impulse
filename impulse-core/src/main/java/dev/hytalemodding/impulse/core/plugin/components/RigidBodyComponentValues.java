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

    public static boolean hasExplicitSpace(@Nullable RigidBodyComponent component) {
        return component != null
            && component.getSpaceId() != null
            && component.getSpaceId().value() > 0;
    }

    @Nonnull
    public static PhysicsShapeSpec toShapeSpec(@Nonnull RigidBodyComponent component) {
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
    public static RigidBodySpawnSettings toSpawnSettings(@Nonnull RigidBodyComponent component) {
        Objects.requireNonNull(component, "component");
        return RigidBodySpawnSettings.of(
            component.getFriction(),
            component.getRestitution(),
            component.getLinearDamping(),
            component.getAngularDamping(),
            component.getCollisionGroup(),
            component.getCollisionMask(),
            component.isSensor());
    }
}
