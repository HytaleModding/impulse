package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentQuaternion;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import java.util.UUID;
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

    @Nonnull
    public static final BuilderCodec<PhysicsBodyAttachmentComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyAttachmentComponent.class,
            PhysicsBodyAttachmentComponent::new)
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyId = value != null
                ? PhysicsBodyId.of(value)
                : PhysicsBodyId.random(),
            PhysicsBodyAttachmentComponent::getBodyIdValue)
        .add()
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER, false),
            (component, value) -> component.spaceId = value != null && value > 0
                ? new SpaceId(value)
                : null,
            PhysicsBodyAttachmentComponent::getSpaceIdValue)
        .add()
        .append(new KeyedCodec<>("TransformAuthority", new EnumCodec<>(TransformAuthority.class), false),
            (component, value) -> component.transformAuthority = value != null
                ? value
                : TransformAuthority.BODY,
            PhysicsBodyAttachmentComponent::getTransformAuthority)
        .add()
        .append(new KeyedCodec<>("Lifecycle", new EnumCodec<>(AttachmentLifecycle.class), false),
            (component, value) -> component.lifecycle = value != null
                ? value
                : AttachmentLifecycle.EXTERNAL_ENTITY,
            PhysicsBodyAttachmentComponent::getLifecycle)
        .add()
        .append(new KeyedCodec<>("LocalPositionOffset", Vector3fUtil.CODEC, false),
            (component, value) -> component.localPositionOffset.set(value != null
                ? value
                : new Vector3f()),
            PhysicsBodyAttachmentComponent::getLocalPositionOffset)
        .add()
        .append(new KeyedCodec<>("LocalRotationOffset", PersistentQuaternion.CODEC, false),
            (component, value) -> component.localRotationOffset.set(value != null
                ? value.toQuaternionf()
                : new Quaternionf()),
            component -> PersistentQuaternion.of(component.localRotationOffset))
        .add()
        .build();

    @Setter
    private PhysicsBodyId bodyId = PhysicsBodyId.random();

    @Setter
    @Getter
    @Nullable
    private SpaceId spaceId;

    @Setter
    private TransformAuthority transformAuthority = TransformAuthority.BODY;

    @Setter
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

    /**
     * Creates the normal attachment for a plugin-owned gameplay or visual entity.
     *
     * <p>The entity follows the body but does not own backend body destruction.</p>
     */
    @Nonnull
    public static PhysicsBodyAttachmentComponent externalEntity(@Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId) {
        return new PhysicsBodyAttachmentComponent(bodyId,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY);
    }

    /**
     * Creates the normal external-entity attachment with a local transform offset.
     */
    @Nonnull
    public static PhysicsBodyAttachmentComponent externalEntity(@Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset) {
        return new PhysicsBodyAttachmentComponent(bodyId,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            localPositionOffset,
            localRotationOffset);
    }

    @Nonnull
    public PhysicsBodyId getBodyId() {
        return bodyId;
    }

    @Nonnull
    public TransformAuthority getTransformAuthority() {
        return transformAuthority;
    }

    @Nonnull
    public AttachmentLifecycle getLifecycle() {
        return lifecycle;
    }

    public static ComponentType<EntityStore, PhysicsBodyAttachmentComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyAttachmentComponentType();
    }

    @Nonnull
    private UUID getBodyIdValue() {
        return bodyId.value();
    }

    @Nullable
    private Integer getSpaceIdValue() {
        return spaceId != null ? spaceId.value() : null;
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
