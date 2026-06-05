package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Chunk-boundary and forced-CCD runtime state for registered bodies.
 */
public final class PhysicsChunkBoundaryRuntime {

    private final Set<RigidBodyKey> forcedContinuousCollisionBodyKeys = new ObjectOpenHashSet<>();
    private final Map<RigidBodyKey, ChunkBoundarySafeState> chunkBoundarySafeStates =
        new Object2ObjectOpenHashMap<>();
    private final Map<RigidBodyKey, ChunkBoundaryPauseState> chunkBoundaryPauseStates =
        new Object2ObjectOpenHashMap<>();

    public void updateChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        ChunkBoundarySafeState state = chunkBoundarySafeStates.computeIfAbsent(bodyKey,
            ignored -> new ChunkBoundarySafeState());
        state.set(position, rotation);
    }

    public void updateChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkBoundarySafeState state = chunkBoundarySafeStates.computeIfAbsent(bodyKey,
            ignored -> new ChunkBoundarySafeState());
        state.set(snapshot);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(
        @Nonnull RigidBodyKey bodyKey) {
        return chunkBoundarySafeStates.get(bodyKey);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(bodyKey,
            ignored -> new ChunkBoundaryPauseState());
        state.set(targetChunkIndex, originalBodyType, linearVelocity, angularVelocity);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(bodyKey,
            ignored -> new ChunkBoundaryPauseState());
        state.set(targetChunkIndex, snapshot);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(
        @Nonnull RigidBodyKey bodyKey) {
        return chunkBoundaryPauseStates.get(bodyKey);
    }

    public void clearChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        chunkBoundaryPauseStates.remove(bodyKey);
    }

    @Nonnull
    public Collection<RigidBodyKey> getChunkBoundaryPausedBodyKeys() {
        return new ArrayList<>(chunkBoundaryPauseStates.keySet());
    }

    public void markContinuousCollisionForced(@Nonnull RigidBodyKey bodyKey) {
        forcedContinuousCollisionBodyKeys.add(bodyKey);
    }

    @Nonnull
    public Collection<RigidBodyKey> getForcedContinuousCollisionBodyKeys() {
        return new ArrayList<>(forcedContinuousCollisionBodyKeys);
    }

    public boolean hasForcedContinuousCollisionBodies() {
        return !forcedContinuousCollisionBodyKeys.isEmpty();
    }

    public void forEachForcedContinuousCollisionBody(@Nonnull Consumer<RigidBodyKey> consumer) {
        forcedContinuousCollisionBodyKeys.forEach(consumer);
    }

    public void clearForcedContinuousCollisionBodies() {
        forcedContinuousCollisionBodyKeys.clear();
    }

    public void clearBody(@Nonnull RigidBodyKey bodyKey) {
        forcedContinuousCollisionBodyKeys.remove(bodyKey);
        chunkBoundarySafeStates.remove(bodyKey);
        chunkBoundaryPauseStates.remove(bodyKey);
    }

    public void clear() {
        forcedContinuousCollisionBodyKeys.clear();
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
    }

    public void clearChunkBoundaryStates() {
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

        public void set(long targetChunkIndex, @Nonnull PhysicsBodySnapshot snapshot) {
            this.targetChunkIndex = targetChunkIndex;
            this.originalBodyType = snapshot.bodyType();
            snapshot.copyLinearVelocityTo(this.linearVelocity);
            snapshot.copyAngularVelocityTo(this.angularVelocity);
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

        public void set(@Nonnull PhysicsBodySnapshot snapshot) {
            snapshot.copyPositionTo(this.position);
            snapshot.copyRotationTo(this.rotation);
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
