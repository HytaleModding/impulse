package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Authored backend and gravity definition for one physics space row.
 */
public final class SpaceComponent implements Component<PhysicsStore> {

    private static final Vector3f DEFAULT_GRAVITY = new Vector3f(0.0f, -9.81f, 0.0f);

    @Nonnull
    public static final BuilderCodec<SpaceComponent> CODEC = BuilderCodec.builder(
            SpaceComponent.class,
            SpaceComponent::new)
        .append(new KeyedCodec<>("BackendId", Codec.STRING, false),
            (component, value) -> component.backendId = value != null && !value.isBlank()
                ? value
                : "",
            SpaceComponent::getBackendIdValue)
        .add()
        .append(new KeyedCodec<>("Gravity", Vector3fUtil.CODEC, false),
            (component, value) -> component.gravity.set(value != null ? value : DEFAULT_GRAVITY),
            SpaceComponent::getGravity)
        .add()
        .build();

    @Nonnull
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(DEFAULT_GRAVITY);

    public SpaceComponent() {
    }

    public SpaceComponent(@Nonnull BackendId backendId, @Nonnull Vector3f gravity) {
        this.backendId = Objects.requireNonNull(backendId, "backendId").value();
        this.gravity.set(Objects.requireNonNull(gravity, "gravity"));
    }

    @Nonnull
    public BackendId getBackendId() {
        return new BackendId(backendId);
    }

    public void setBackendId(@Nonnull BackendId backendId) {
        this.backendId = Objects.requireNonNull(backendId, "backendId").value();
    }

    @Nonnull
    public String getBackendIdValue() {
        return backendId;
    }

    @Nonnull
    public Vector3f getGravity() {
        return new Vector3f(gravity);
    }

    public void setGravity(@Nonnull Vector3f gravity) {
        this.gravity.set(Objects.requireNonNull(gravity, "gravity"));
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SpaceComponent> getComponentType() {
        return PhysicsStoreTypes.spaceComponentType();
    }

    @Nonnull
    @Override
    public SpaceComponent clone() {
        return new SpaceComponent(getBackendId(), gravity);
    }
}
