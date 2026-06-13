package dev.hytalemodding.impulse.core.plugin.physicsstore.projection;

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
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * EntityStore projection relationship to an authoritative PhysicsStore body.
 */
public class BodyAttachmentComponent implements Component<EntityStore> {

    private static final float USE_BODY_VISUAL_ORIGIN_OFFSET_Y = -1.0f;

    @Nonnull
    public static final BuilderCodec<BodyAttachmentComponent> CODEC = BuilderCodec.builder(
            BodyAttachmentComponent.class,
            BodyAttachmentComponent::new)
        .append(new KeyedCodec<>("BodyUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyUuid = value != null ? value : UUID.randomUUID(),
            BodyAttachmentComponent::getBodyUuid)
        .add()
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER, false),
            (component, value) -> component.spaceId = value != null && value > 0
                ? new SpaceId(value)
                : null,
            BodyAttachmentComponent::getSpaceIdValue)
        .add()
        .append(new KeyedCodec<>("TransformAuthority", new EnumCodec<>(TransformAuthority.class), false),
            (component, value) -> component.transformAuthority = value != null
                ? value
                : TransformAuthority.BODY,
            BodyAttachmentComponent::getTransformAuthority)
        .add()
        .append(new KeyedCodec<>("Lifecycle", new EnumCodec<>(AttachmentLifecycle.class), false),
            (component, value) -> component.lifecycle = value != null
                ? value
                : AttachmentLifecycle.EXTERNAL_ENTITY,
            BodyAttachmentComponent::getLifecycle)
        .add()
        .append(new KeyedCodec<>("LocalPositionOffset", Vector3fUtil.CODEC, false),
            (component, value) -> component.localPositionOffset.set(value != null
                ? value
                : new Vector3f()),
            BodyAttachmentComponent::getLocalPositionOffset)
        .add()
        .append(new KeyedCodec<>("LocalRotationOffset", ImpulseCodecs.QUATERNIONF, false),
            (component, value) -> component.localRotationOffset.set(value != null
                ? value
                : new Quaternionf()),
            component -> new Quaternionf(component.localRotationOffset))
        .add()
        .append(new KeyedCodec<>("VisualOriginOffsetY", Codec.FLOAT, false),
            (component, value) -> component.visualOriginOffsetY = normalizeVisualOriginOffsetY(value),
            BodyAttachmentComponent::getVisualOriginOffsetY)
        .add()
        .build();

    @Nonnull
    private UUID bodyUuid = UUID.randomUUID();

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

    public BodyAttachmentComponent() {
    }

    public BodyAttachmentComponent(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId) {
        this(bodyUuid,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            new Vector3f(),
            new Quaternionf());
    }

    public BodyAttachmentComponent(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle) {
        this(bodyUuid, spaceId, transformAuthority, lifecycle, new Vector3f(), new Quaternionf());
    }

    public BodyAttachmentComponent(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset) {
        this(bodyUuid,
            spaceId,
            transformAuthority,
            lifecycle,
            localPositionOffset,
            localRotationOffset,
            USE_BODY_VISUAL_ORIGIN_OFFSET_Y);
    }

    public BodyAttachmentComponent(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId,
        @Nonnull TransformAuthority transformAuthority,
        @Nonnull AttachmentLifecycle lifecycle,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        this.bodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        this.spaceId = spaceId;
        this.transformAuthority = Objects.requireNonNull(transformAuthority, "transformAuthority");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.localPositionOffset.set(Objects.requireNonNull(localPositionOffset,
            "localPositionOffset"));
        this.localRotationOffset.set(Objects.requireNonNull(localRotationOffset,
            "localRotationOffset"));
        this.visualOriginOffsetY = normalizeVisualOriginOffsetY(visualOriginOffsetY);
    }

    @Nonnull
    public static BodyAttachmentComponent externalEntity(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId) {
        return new BodyAttachmentComponent(bodyUuid,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY);
    }

    @Nonnull
    public static BodyAttachmentComponent externalEntity(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset) {
        return new BodyAttachmentComponent(bodyUuid,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            localPositionOffset,
            localRotationOffset);
    }

    @Nonnull
    public static BodyAttachmentComponent externalEntity(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        return new BodyAttachmentComponent(bodyUuid,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
    }

    @Nonnull
    public static BodyAttachmentComponent impulseOwnedVisual(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        return new BodyAttachmentComponent(bodyUuid,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.IMPULSE_OWNED_VISUAL,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
    }

    @Nonnull
    public static BodyAttachmentComponent generatedProxy(@Nonnull UUID bodyUuid,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        return new BodyAttachmentComponent(bodyUuid,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.GENERATED_PROXY,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
    }

    @Nonnull
    public UUID getBodyUuid() {
        return bodyUuid;
    }

    public void setBodyUuid(@Nonnull UUID bodyUuid) {
        this.bodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
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

    public static ComponentType<EntityStore, BodyAttachmentComponent> getComponentType() {
        return ImpulsePlugin.get().getBodyAttachmentComponentType();
    }

    @Nullable
    private Integer getSpaceIdValue() {
        return spaceId != null ? spaceId.value() : null;
    }

    @Nonnull
    @Override
    public BodyAttachmentComponent clone() {
        return new BodyAttachmentComponent(bodyUuid,
            spaceId,
            transformAuthority,
            lifecycle,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
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
