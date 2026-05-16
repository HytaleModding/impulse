package dev.hytalemodding.impulse.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Runtime-only visual follower that makes an entity track a physics body.
 *
 * <p>This is distinct from {@link PhysicsBodyComponent}, which remains the
 * authoritative physics-owner relationship used for persistence, cleanup, and
 * control flows. One body can have many visual followers, which is the shape
 * needed for future multiblock visuals.</p>
 */
public class PhysicsBodyVisualComponent implements Component<EntityStore> {

    @Setter
    @Getter(onMethod_ = @__(@Nonnull))
    private PhysicsBody body;

    @Setter
    @Getter
    @Nullable
    private SpaceId spaceId;

    @Nonnull
    @Getter
    private final Vector3f localPositionOffset = new Vector3f();

    @Nonnull
    @Getter
    private final Quaternionf localRotationOffset = new Quaternionf();

    public PhysicsBodyVisualComponent() {
    }

    public PhysicsBodyVisualComponent(@Nonnull PhysicsBody body, @Nullable SpaceId spaceId) {
        this(body, spaceId, new Vector3f(), new Quaternionf());
    }

    public PhysicsBodyVisualComponent(@Nonnull PhysicsBody body,
        @Nullable SpaceId spaceId,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset) {
        this.body = body;
        this.spaceId = spaceId;
        this.localPositionOffset.set(localPositionOffset);
        this.localRotationOffset.set(localRotationOffset);
    }

    public static ComponentType<EntityStore, PhysicsBodyVisualComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyVisualComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyVisualComponent clone() {
        return new PhysicsBodyVisualComponent(body,
            spaceId,
            localPositionOffset,
            localRotationOffset);
    }
}
