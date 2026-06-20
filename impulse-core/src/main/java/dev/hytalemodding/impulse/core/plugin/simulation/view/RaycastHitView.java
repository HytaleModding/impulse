package dev.hytalemodding.impulse.core.plugin.simulation.view;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Copied raycast geometry plus the PhysicsStore body ref hit by the backend.
 */
public record RaycastHitView(@Nullable Ref<PhysicsStore> bodyRef,
                             float pointX,
                             float pointY,
                             float pointZ,
                             float normalX,
                             float normalY,
                             float normalZ,
                             float fraction,
                             float distance) {

    public RaycastHitView(@Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f point,
        @Nonnull Vector3f normal,
        float fraction,
        float distance) {
        this(bodyRef,
            Objects.requireNonNull(point, "point").x,
            point.y,
            point.z,
            Objects.requireNonNull(normal, "normal").x,
            normal.y,
            normal.z,
            fraction,
            distance);
    }

    public RaycastHitView {
    }

    @Nonnull
    public PhysicsBodyType bodyType() {
        DynamicsComponent dynamics = dynamicsComponent(bodyRef);
        return dynamics != null ? dynamics.getBodyType() : PhysicsBodyType.STATIC;
    }

    @Nonnull
    public ShapeType shapeType() {
        ShapeComponent shape = shapeComponent(bodyRef);
        return shape != null ? shape.getShapeType() : ShapeType.UNKNOWN;
    }

    @Nonnull
    public Vector3f point() {
        return new Vector3f(pointX, pointY, pointZ);
    }

    @Nonnull
    public Vector3f normal() {
        return new Vector3f(normalX, normalY, normalZ);
    }

    @Nonnull
    public Vector3f copyPointTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target").set(pointX, pointY, pointZ);
    }

    @Nonnull
    public Vector3f copyNormalTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target").set(normalX, normalY, normalZ);
    }

    @Nullable
    private static DynamicsComponent dynamicsComponent(@Nullable Ref<PhysicsStore> bodyRef) {
        if (bodyRef == null || !bodyRef.isValid()) {
            return null;
        }
        Store<PhysicsStore> store = bodyRef.getStore();
        return store != null
            ? store.getComponentConcurrent(bodyRef, DynamicsComponent.getComponentType())
            : null;
    }

    @Nullable
    private static ShapeComponent shapeComponent(@Nullable Ref<PhysicsStore> bodyRef) {
        if (bodyRef == null || !bodyRef.isValid()) {
            return null;
        }
        Store<PhysicsStore> store = bodyRef.getStore();
        return store != null
            ? store.getComponentConcurrent(bodyRef, ShapeComponent.getComponentType())
            : null;
    }
}
