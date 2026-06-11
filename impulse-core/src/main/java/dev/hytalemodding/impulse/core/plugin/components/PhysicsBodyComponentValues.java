package dev.hytalemodding.impulse.core.plugin.components;

import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stateless conversions from ECS body authoring components to simulation values.
 */
public final class PhysicsBodyComponentValues {

    private PhysicsBodyComponentValues() {
    }

    public static boolean hasExplicitSpace(@Nullable PhysicsBodyIdentityComponent component) {
        return component != null
            && component.getSpaceId() != null
            && component.getSpaceId().value() > 0;
    }

    @Nonnull
    public static PhysicsShapeSpec toShapeSpec(@Nonnull PhysicsBodyShapeComponent component) {
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
                throw new IllegalArgumentException("Unsupported physics body shape "
                    + component.getShapeType());
        };
    }

    @Nonnull
    public static RigidBodySpawnSettings toSpawnSettings(
        @Nonnull PhysicsBodyDynamicsComponent dynamics,
        @Nonnull PhysicsBodyMaterialComponent material,
        @Nonnull PhysicsBodyCollisionComponent collision) {
        Objects.requireNonNull(dynamics, "dynamics");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(collision, "collision");
        return RigidBodySpawnSettings.fromOptionalValues(
            material.getFriction(),
            material.getRestitution(),
            dynamics.getLinearDamping(),
            dynamics.getAngularDamping(),
            collision.getCollisionGroup(),
            collision.getCollisionMask(),
            collision.isSensor());
    }
}
