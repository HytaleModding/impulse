package dev.hytalemodding.impulse.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Vector3f;

public class PhysicsControlSessionComponent implements Component<EntityStore> {

    @Nullable
    private PhysicsBody body;
    @Nullable
    private PhysicsBody anchorBody;
    @Nullable
    private PhysicsJoint joint;
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

    public PhysicsControlSessionComponent(@Nonnull PhysicsBody body,
        @Nonnull PhysicsBody anchorBody,
        @Nonnull PhysicsJoint joint,
        @Nonnull Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        this.body = body;
        this.anchorBody = anchorBody;
        this.joint = joint;
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

    @Nullable
    public PhysicsBody getBody() {
        return body;
    }

    @Nullable
    public PhysicsBody getAnchorBody() {
        return anchorBody;
    }

    @Nullable
    public PhysicsJoint getJoint() {
        return joint;
    }

    @Nullable
    public Ref<EntityStore> getTargetRef() {
        return targetRef;
    }

    @Nullable
    public SpaceId getSpaceId() {
        return spaceId;
    }

    @Nonnull
    public PhysicsBodyType getOriginalBodyType() {
        return originalBodyType;
    }

    @Nonnull
    public Vector3f getViewOffset() {
        return viewOffset;
    }

    @Nonnull
    public Vector3f getPreviousTarget() {
        return previousTarget;
    }

    @Nonnull
    public Vector3f getReleaseVelocity() {
        return releaseVelocity;
    }

    public void deactivate() {
        active = false;
    }

    @Nonnull
    @Override
    public PhysicsControlSessionComponent clone() {
        PhysicsControlSessionComponent copy = new PhysicsControlSessionComponent();
        copy.body = body;
        copy.anchorBody = anchorBody;
        copy.joint = joint;
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
