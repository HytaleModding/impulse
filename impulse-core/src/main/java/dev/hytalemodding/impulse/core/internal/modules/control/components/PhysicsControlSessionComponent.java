package dev.hytalemodding.impulse.core.internal.modules.control.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Vector3f;

@Getter
public class PhysicsControlSessionComponent implements Component<EntityStore> {

    @Nullable
    private static ComponentType<EntityStore, PhysicsControlSessionComponent> componentType;

    @Nullable
    private Ref<PhysicsStore> bodyRef;
    @Nullable
    private Ref<PhysicsStore> anchorBodyRef;
    @Nullable
    private Ref<PhysicsStore> controlJointRef;
    @Nullable
    private Ref<EntityStore> targetRef;
    @Nonnull
    private PhysicsBodyType originalBodyType = PhysicsBodyType.DYNAMIC;
    @Getter
    private float grabDistance;
    @Nonnull
    private final Vector3f viewOffset = new Vector3f();
    @Nonnull
    private final Vector3f previousTarget = new Vector3f();
    @Nonnull
    private final Vector3f releaseVelocity = new Vector3f();
    @Getter
    private boolean active;

    public PhysicsControlSessionComponent() {
    }

    public PhysicsControlSessionComponent(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<PhysicsStore> anchorBodyRef,
        @Nullable Ref<PhysicsStore> controlJointRef,
        @Nullable Ref<EntityStore> targetRef,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        this.bodyRef = Objects.requireNonNull(bodyRef, "bodyRef");
        this.anchorBodyRef = Objects.requireNonNull(anchorBodyRef, "anchorBodyRef");
        this.controlJointRef = controlJointRef;
        this.targetRef = targetRef;
        this.originalBodyType = Objects.requireNonNull(originalBodyType, "originalBodyType");
        this.grabDistance = grabDistance;
        this.viewOffset.set(Objects.requireNonNull(viewOffset, "viewOffset"));
        this.previousTarget.set(Objects.requireNonNull(previousTarget, "previousTarget"));
        this.active = true;
    }

    public static void setComponentType(
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> type) {
        componentType = Objects.requireNonNull(type, "type");
    }

    public static void clearComponentType() {
        componentType = null;
    }

    public static boolean isComponentTypeRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, PhysicsControlSessionComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("Physics control session component is not registered");
        }
        return componentType;
    }

    public void deactivate() {
        active = false;
    }

    @Nonnull
    @Override
    public PhysicsControlSessionComponent clone() {
        PhysicsControlSessionComponent copy = new PhysicsControlSessionComponent();
        copy.bodyRef = bodyRef;
        copy.anchorBodyRef = anchorBodyRef;
        copy.controlJointRef = controlJointRef;
        copy.targetRef = targetRef;
        copy.originalBodyType = originalBodyType;
        copy.grabDistance = grabDistance;
        copy.viewOffset.set(viewOffset);
        copy.previousTarget.set(previousTarget);
        copy.releaseVelocity.set(releaseVelocity);
        copy.active = active;
        return copy;
    }
}
