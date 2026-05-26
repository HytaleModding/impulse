package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;
import lombok.Getter;

@Getter
public class PhysicsControlSessionComponent implements Component<EntityStore> {

    @Nullable
    private PhysicsBodyId bodyId;
    @Nullable
    private PhysicsBodyId anchorBodyId;
    @Nullable
    private Ref<EntityStore> targetRef;
    @Nullable
    private SpaceId spaceId;
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

    public PhysicsControlSessionComponent(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId,
        @Nullable Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        this.bodyId = bodyId;
        this.anchorBodyId = anchorBodyId;
        this.targetRef = targetRef;
        this.spaceId = spaceId;
        this.originalBodyType = originalBodyType;
        this.grabDistance = grabDistance;
        this.viewOffset.set(viewOffset);
        this.previousTarget.set(previousTarget);
        this.active = true;
    }

    public static ComponentType<EntityStore, PhysicsControlSessionComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsControlSessionComponentType();
    }

    public void deactivate() {
        active = false;
    }

    @Nonnull
    @Override
    public PhysicsControlSessionComponent clone() {
        PhysicsControlSessionComponent copy = new PhysicsControlSessionComponent();
        copy.bodyId = bodyId;
        copy.anchorBodyId = anchorBodyId;
        copy.targetRef = targetRef;
        copy.spaceId = spaceId;
        copy.originalBodyType = originalBodyType;
        copy.grabDistance = grabDistance;
        copy.viewOffset.set(viewOffset);
        copy.previousTarget.set(previousTarget);
        copy.releaseVelocity.set(releaseVelocity);
        copy.active = active;
        return copy;
    }
}
