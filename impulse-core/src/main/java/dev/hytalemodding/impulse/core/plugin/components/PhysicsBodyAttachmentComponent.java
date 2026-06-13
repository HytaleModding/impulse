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
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Runtime attachment from a Hytale entity to an Impulse body key.
 *
 * <p>The entity is a gameplay or visual representation. It does not own backend
 * body destruction; removing the entity only removes this attachment unless the
 * lifecycle marks it as a disposable Impulse visual.</p>
 */
public class PhysicsBodyAttachmentComponent implements Component<EntityStore> {

    private static final float USE_BODY_VISUAL_ORIGIN_OFFSET_Y = -1.0f;

    @Nonnull
    public static final BuilderCodec<PhysicsBodyAttachmentComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyAttachmentComponent.class,
            PhysicsBodyAttachmentComponent::new)
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyKey = value != null
                ? RigidBodyKey.of(value)
                : RigidBodyKey.random(),
            PhysicsBodyAttachmentComponent::getBodyKeyValue)
        .add()
        .append(new KeyedCodec<>("PhysicsBodyUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.physicsBodyUuid = value,
            PhysicsBodyAttachmentComponent::getPhysicsBodyUuid)
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
        .append(new KeyedCodec<>("LocalRotationOffset", ImpulseCodecs.QUATERNIONF, false),
            (component, value) -> component.localRotationOffset.set(value != null
                ? value
                : new Quaternionf()),
            component -> new Quaternionf(component.localRotationOffset))
        .add()
        .append(new KeyedCodec<>("VisualOriginOffsetY", Codec.FLOAT, false),
            (component, value) -> component.visualOriginOffsetY = normalizeVisualOriginOffsetY(value),
            PhysicsBodyAttachmentComponent::getVisualOriginOffsetY)
        .add()
        .build();

    private RigidBodyKey bodyKey = RigidBodyKey.random();

    @Nullable
    private UUID physicsBodyUuid;

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

    private float visualOriginOffsetY = USE_BODY_VISUAL_ORIGIN_OFFSET_Y;

    public PhysicsBodyAttachmentComponent() {
    }

    public PhysicsBodyAttachmentComponent(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId) {
        this(bodyKey,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            new Vector3f(),
            new Quaternionf());
    }

    public PhysicsBodyAttachmentComponent(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle) {
        this(bodyKey, spaceId, transformAuthority, lifecycle, new Vector3f(), new Quaternionf());
    }

    public PhysicsBodyAttachmentComponent(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset) {
        this(bodyKey,
            spaceId,
            transformAuthority,
            lifecycle,
            localPositionOffset,
            localRotationOffset,
            USE_BODY_VISUAL_ORIGIN_OFFSET_Y);
    }

    public PhysicsBodyAttachmentComponent(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        this.bodyKey = bodyKey;
        this.spaceId = spaceId;
        this.transformAuthority = transformAuthority;
        this.lifecycle = lifecycle;
        this.localPositionOffset.set(localPositionOffset);
        this.localRotationOffset.set(localRotationOffset);
        this.visualOriginOffsetY = normalizeVisualOriginOffsetY(visualOriginOffsetY);
    }

    @Nonnull
    public static PhysicsBodyAttachmentComponent physicsStoreEntity(@Nonnull UUID physicsBodyUuid) {
        PhysicsBodyAttachmentComponent component = externalEntity(RigidBodyKey.of(physicsBodyUuid), null);
        component.setPhysicsBodyUuid(physicsBodyUuid);
        return component;
    }

    /**
     * Creates the normal attachment for a plugin-owned gameplay or visual entity.
     *
     * <p>The entity follows the body but does not own backend body destruction.</p>
     */
    @Nonnull
    public static PhysicsBodyAttachmentComponent externalEntity(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId) {
        return new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY);
    }

    /**
     * Creates the normal external-entity attachment with a local transform offset.
     */
    @Nonnull
    public static PhysicsBodyAttachmentComponent externalEntity(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset) {
        return new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            localPositionOffset,
            localRotationOffset);
    }

    /**
     * Creates an external-entity attachment whose visual transform origin differs from the owning
     * body's support/base offset.
     */
    @Nonnull
    public static PhysicsBodyAttachmentComponent externalEntity(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        return new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
    }

    /**
     * Creates a disposable Impulse-owned visual attachment.
     *
     * <p>Unlike {@link #externalEntity(RigidBodyKey, SpaceId)}, this entity should be removed when
     * the attached body is no longer available.</p>
     */
    @Nonnull
    public static PhysicsBodyAttachmentComponent impulseOwnedVisual(@Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        return new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.IMPULSE_OWNED_VISUAL,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
    }

    @Nonnull
    public RigidBodyKey getBodyKey() {
        return bodyKey;
    }

    public void setBodyKey(@Nonnull RigidBodyKey bodyKey) {
        this.bodyKey = bodyKey;
    }

    @Nullable
    public UUID getPhysicsBodyUuid() {
        return physicsBodyUuid;
    }

    public void setPhysicsBodyUuid(@Nullable UUID physicsBodyUuid) {
        this.physicsBodyUuid = physicsBodyUuid;
    }

    @Nonnull
    public UUID getPhysicsBodyUuidOrLegacy() {
        return physicsBodyUuid != null ? physicsBodyUuid : bodyKey.value();
    }

    @Nonnull
    public TransformAuthority getTransformAuthority() {
        return transformAuthority;
    }

    @Nonnull
    public AttachmentLifecycle getLifecycle() {
        return lifecycle;
    }

    public float getVisualOriginOffsetY() {
        return visualOriginOffsetY;
    }

    public void setVisualOriginOffsetY(float visualOriginOffsetY) {
        this.visualOriginOffsetY = normalizeVisualOriginOffsetY(visualOriginOffsetY);
    }

    public float resolveVisualOriginOffsetY(float bodyVisualOriginOffsetY) {
        return visualOriginOffsetY >= 0.0f ? visualOriginOffsetY : bodyVisualOriginOffsetY;
    }

    public boolean shouldRemoveEntityWhenBodyMissing() {
        return lifecycle == AttachmentLifecycle.IMPULSE_OWNED_VISUAL
            || lifecycle == AttachmentLifecycle.GENERATED_PROXY;
    }

    public static ComponentType<EntityStore, PhysicsBodyAttachmentComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyAttachmentComponentType();
    }

    @Nonnull
    private UUID getBodyKeyValue() {
        return bodyKey.value();
    }

    @Nullable
    private Integer getSpaceIdValue() {
        return spaceId != null ? spaceId.value() : null;
    }

    @Nonnull
    @Override
    public PhysicsBodyAttachmentComponent clone() {
        PhysicsBodyAttachmentComponent copy = new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            transformAuthority,
            lifecycle,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
        copy.physicsBodyUuid = physicsBodyUuid;
        return copy;
    }

    private static float normalizeVisualOriginOffsetY(@Nullable Float value) {
        if (value == null || !Float.isFinite(value) || value < 0.0f) {
            return USE_BODY_VISUAL_ORIGIN_OFFSET_Y;
        }
        return value;
    }

    private static float normalizeVisualOriginOffsetY(float value) {
        return normalizeVisualOriginOffsetY(Float.valueOf(value));
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
        IMPULSE_OWNED_VISUAL,
        GENERATED_PROXY
    }
}
