package dev.hytalemodding.impulse.core.internal.resources.chunk;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Chunk-boundary and forced-CCD runtime state for registered bodies.
 */
public final class PhysicsChunkBoundaryRuntime {

    private final Set<PhysicsBodyId> forcedContinuousCollisionBodyIds = new ObjectOpenHashSet<>();
    private final Map<PhysicsBodyId, ChunkBoundarySafeState> chunkBoundarySafeStates =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsBodyId, ChunkBoundaryPauseState> chunkBoundaryPauseStates =
        new Object2ObjectLinkedOpenHashMap<>();

    public void updateChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        ChunkBoundarySafeState state = chunkBoundarySafeStates.computeIfAbsent(bodyId,
            ignored -> new ChunkBoundarySafeState());
        state.set(position, rotation);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(
        @Nonnull PhysicsBodyId bodyId) {
        return chunkBoundarySafeStates.get(bodyId);
    }

    public void pauseChunkBoundaryBody(@Nonnull PhysicsBodyId bodyId,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(bodyId,
            ignored -> new ChunkBoundaryPauseState());
        state.set(targetChunkIndex, originalBodyType, linearVelocity, angularVelocity);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(
        @Nonnull PhysicsBodyId bodyId) {
        return chunkBoundaryPauseStates.get(bodyId);
    }

    public void clearChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        chunkBoundaryPauseStates.remove(bodyId);
    }

    public void markContinuousCollisionForced(@Nonnull PhysicsBodyId bodyId) {
        forcedContinuousCollisionBodyIds.add(bodyId);
    }

    @Nonnull
    public Collection<PhysicsBodyId> getForcedContinuousCollisionBodyIds() {
        return new ArrayList<>(forcedContinuousCollisionBodyIds);
    }

    public void clearForcedContinuousCollisionBodies() {
        forcedContinuousCollisionBodyIds.clear();
    }

    public void clearBody(@Nonnull PhysicsBodyId bodyId) {
        forcedContinuousCollisionBodyIds.remove(bodyId);
        chunkBoundarySafeStates.remove(bodyId);
        chunkBoundaryPauseStates.remove(bodyId);
    }

    public void clear() {
        forcedContinuousCollisionBodyIds.clear();
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
    }

    public static final class ChunkBoundaryPauseState {

        @Getter
        private long targetChunkIndex;
        @Nonnull
        private PhysicsBodyType originalBodyType = PhysicsBodyType.DYNAMIC;
        @Nonnull
        private final Vector3f linearVelocity = new Vector3f();
        @Nonnull
        private final Vector3f angularVelocity = new Vector3f();

        public void set(long targetChunkIndex,
            @Nonnull PhysicsBodyType originalBodyType,
            @Nonnull Vector3f linearVelocity,
            @Nonnull Vector3f angularVelocity) {
            this.targetChunkIndex = targetChunkIndex;
            this.originalBodyType = originalBodyType;
            this.linearVelocity.set(linearVelocity);
            this.angularVelocity.set(angularVelocity);
        }

        @Nonnull
        public PhysicsBodyType getOriginalBodyType() {
            return originalBodyType;
        }

        @Nonnull
        public Vector3f getLinearVelocity() {
            return linearVelocity;
        }

        @Nonnull
        public Vector3f getAngularVelocity() {
            return angularVelocity;
        }
    }

    public static final class ChunkBoundarySafeState {

        @Nonnull
        private final Vector3f position = new Vector3f();
        @Nonnull
        private final Quaternionf rotation = new Quaternionf();

        public void set(@Nonnull Vector3f position, @Nonnull Quaternionf rotation) {
            this.position.set(position);
            this.rotation.set(rotation);
        }

        @Nonnull
        public Vector3f getPosition() {
            return position;
        }

        @Nonnull
        public Quaternionf getRotation() {
            return rotation;
        }
    }
}
