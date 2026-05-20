package dev.hytalemodding.impulse.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Runtime attachment from a Hytale entity to an Impulse body id.
 *
 * <p>The entity is a gameplay or visual representation. It does not own backend
 * body destruction; removing the entity only removes this attachment unless the
 * lifecycle says the entity is an Impulse-generated visual proxy.</p>
 */
public class PhysicsBodyAttachmentComponent implements Component<EntityStore> {

    @Setter
    @Getter(onMethod_ = @__(@Nonnull))
    private PhysicsBodyId bodyId;

    @Setter
    @Getter
    @Nullable
    private SpaceId spaceId;

    @Setter
    @Getter(onMethod_ = @__(@Nonnull))
    private TransformAuthority transformAuthority = TransformAuthority.BODY;

    @Setter
    @Getter(onMethod_ = @__(@Nonnull))
    private AttachmentLifecycle lifecycle = AttachmentLifecycle.EXTERNAL_ENTITY;

    @Nonnull
    @Getter
    private final Vector3f localPositionOffset = new Vector3f();

    @Nonnull
    @Getter
    private final Quaternionf localRotationOffset = new Quaternionf();

    public PhysicsBodyAttachmentComponent() {
    }

    public PhysicsBodyAttachmentComponent(@Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId) {
        this(bodyId,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            new Vector3f(),
            new Quaternionf());
    }

    public PhysicsBodyAttachmentComponent(@Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle) {
        this(bodyId, spaceId, transformAuthority, lifecycle, new Vector3f(), new Quaternionf());
    }

    public PhysicsBodyAttachmentComponent(@Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset) {
        this.bodyId = bodyId;
        this.spaceId = spaceId;
        this.transformAuthority = transformAuthority;
        this.lifecycle = lifecycle;
        this.localPositionOffset.set(localPositionOffset);
        this.localRotationOffset.set(localRotationOffset);
    }

    public static ComponentType<EntityStore, PhysicsBodyAttachmentComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyAttachmentComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyAttachmentComponent clone() {
        return new PhysicsBodyAttachmentComponent(bodyId,
            spaceId,
            transformAuthority,
            lifecycle,
            localPositionOffset,
            localRotationOffset);
    }

    public enum TransformAuthority {
        /*
         * TODO: Revisit transform ownership when multi-body actor wrappers land.
         * A root actor may need to own gameplay transforms while individual
         * bodies still publish resolved physics poses.
         */
        BODY,
        CONTROLLER,
        ENTITY_KINEMATIC
    }

    public enum AttachmentLifecycle {
        EXTERNAL_ENTITY,
        GENERATED_PROXY
    }
}
