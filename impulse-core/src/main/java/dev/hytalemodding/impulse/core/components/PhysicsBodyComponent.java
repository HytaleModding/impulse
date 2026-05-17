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

/**
 * ECS component that links a Hytale entity to an Impulse PhysicsBody.
 */
public class PhysicsBodyComponent implements Component<EntityStore> {

    @Setter
    @Getter(onMethod_ = @__(@Nonnull))
    private PhysicsBody body;

    @Setter
    @Getter
    @Nullable
    private SpaceId spaceId;

    @Setter
    @Getter(onMethod_ = @__(@Nonnull))
    private OwnerVisualMode ownerVisualMode = OwnerVisualMode.OWNER_ENTITY;

    private transient boolean chunkBoundarySleeping;
    private transient long chunkBoundarySleepingChunkIndex = Long.MIN_VALUE;
    private transient int chunkBoundarySleepingSkipTicks;

    public PhysicsBodyComponent() {
    }

    public PhysicsBodyComponent(@Nonnull PhysicsBody body, @Nullable SpaceId spaceId) {
        this.body = body;
        this.spaceId = spaceId;
    }

    public PhysicsBodyComponent(@Nonnull PhysicsBody body,
        @Nullable SpaceId spaceId,
        @Nonnull OwnerVisualMode ownerVisualMode) {
        this.body = body;
        this.spaceId = spaceId;
        this.ownerVisualMode = ownerVisualMode;
    }

    public static ComponentType<EntityStore, PhysicsBodyComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyComponentType();
    }

    public boolean shouldDeferSleepingChunkBoundaryCheck(long currentChunkIndex, int intervalTicks) {
        if (intervalTicks <= 0) {
            return false;
        }
        if (!chunkBoundarySleeping || chunkBoundarySleepingChunkIndex != currentChunkIndex) {
            chunkBoundarySleeping = true;
            chunkBoundarySleepingChunkIndex = currentChunkIndex;
            chunkBoundarySleepingSkipTicks = 0;
            return false;
        }
        if (chunkBoundarySleepingSkipTicks < intervalTicks) {
            chunkBoundarySleepingSkipTicks++;
            return true;
        }
        chunkBoundarySleepingSkipTicks = 0;
        return false;
    }

    public void resetSleepingChunkBoundaryCheck() {
        chunkBoundarySleeping = false;
        chunkBoundarySleepingChunkIndex = Long.MIN_VALUE;
        chunkBoundarySleepingSkipTicks = 0;
    }

    @Nonnull
    @Override
    public PhysicsBodyComponent clone() {
        return new PhysicsBodyComponent(body, spaceId, ownerVisualMode);
    }

    public enum OwnerVisualMode {
        OWNER_ENTITY,
        NONE
    }
}
