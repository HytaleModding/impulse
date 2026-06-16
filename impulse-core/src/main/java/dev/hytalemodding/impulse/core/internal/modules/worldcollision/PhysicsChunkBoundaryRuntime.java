package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Chunk-boundary and forced-CCD runtime state for registered bodies.
 */
public final class PhysicsChunkBoundaryRuntime {

    private final Map<RigidBodyKey, ChunkBoundarySafeState> chunkBoundarySafeStates =
        new Object2ObjectOpenHashMap<>();
    private final Map<RigidBodyKey, ChunkBoundaryPauseState> chunkBoundaryPauseStates =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<RowState<ChunkBoundarySafeState>> chunkBoundarySafeStatesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<RowState<ChunkBoundaryPauseState>> chunkBoundaryPauseStatesByRowIndex =
        new Int2ObjectOpenHashMap<>();

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

    public void updateChunkBoundarySafeState(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        ChunkBoundarySafeState state = rowState(chunkBoundarySafeStatesByRowIndex,
            bodyRef,
            ChunkBoundarySafeState::new);
        state.set(position, rotation);
    }

    public void updateChunkBoundarySafeState(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkBoundarySafeState state = rowState(chunkBoundarySafeStatesByRowIndex,
            bodyRef,
            ChunkBoundarySafeState::new);
        state.set(snapshot);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(
        @Nonnull RigidBodyKey bodyKey) {
        return chunkBoundarySafeStates.get(bodyKey);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return getRowState(chunkBoundarySafeStatesByRowIndex, bodyRef);
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
        @Nonnull long[] targetChunkIndices,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(bodyKey,
            ignored -> new ChunkBoundaryPauseState());
        state.set(targetChunkIndex, targetChunkIndices, snapshot);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(bodyKey,
            ignored -> new ChunkBoundaryPauseState());
        state.set(targetChunkIndex, snapshot);
    }

    public void pauseChunkBoundaryBody(@Nonnull Ref<PhysicsStore> bodyRef,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        ChunkBoundaryPauseState state = rowState(chunkBoundaryPauseStatesByRowIndex,
            bodyRef,
            ChunkBoundaryPauseState::new);
        state.set(targetChunkIndex, originalBodyType, linearVelocity, angularVelocity);
    }

    public void pauseChunkBoundaryBody(@Nonnull Ref<PhysicsStore> bodyRef,
        long targetChunkIndex,
        @Nonnull long[] targetChunkIndices,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkBoundaryPauseState state = rowState(chunkBoundaryPauseStatesByRowIndex,
            bodyRef,
            ChunkBoundaryPauseState::new);
        state.set(targetChunkIndex, targetChunkIndices, snapshot);
    }

    public void pauseChunkBoundaryBody(@Nonnull Ref<PhysicsStore> bodyRef,
        long targetChunkIndex,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkBoundaryPauseState state = rowState(chunkBoundaryPauseStatesByRowIndex,
            bodyRef,
            ChunkBoundaryPauseState::new);
        state.set(targetChunkIndex, snapshot);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(
        @Nonnull RigidBodyKey bodyKey) {
        return chunkBoundaryPauseStates.get(bodyKey);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return getRowState(chunkBoundaryPauseStatesByRowIndex, bodyRef);
    }

    public void clearChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        chunkBoundaryPauseStates.remove(bodyKey);
    }

    public void clearChunkBoundaryPauseState(@Nonnull Ref<PhysicsStore> bodyRef) {
        removeRowState(chunkBoundaryPauseStatesByRowIndex, bodyRef);
    }

    @Nonnull
    public Collection<RigidBodyKey> getChunkBoundaryPausedBodyKeys() {
        return new ArrayList<>(chunkBoundaryPauseStates.keySet());
    }

    @Nonnull
    public Collection<Ref<PhysicsStore>> getChunkBoundaryPausedBodyRefs() {
        return liveRefs(chunkBoundaryPauseStatesByRowIndex);
    }

    public void clearBody(@Nonnull RigidBodyKey bodyKey) {
        chunkBoundarySafeStates.remove(bodyKey);
        chunkBoundaryPauseStates.remove(bodyKey);
    }

    public void clearBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        removeRowState(chunkBoundarySafeStatesByRowIndex, bodyRef);
        removeRowState(chunkBoundaryPauseStatesByRowIndex, bodyRef);
    }

    public void clear() {
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
        chunkBoundarySafeStatesByRowIndex.clear();
        chunkBoundaryPauseStatesByRowIndex.clear();
    }

    public void clearChunkBoundaryStates() {
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
        chunkBoundarySafeStatesByRowIndex.clear();
        chunkBoundaryPauseStatesByRowIndex.clear();
    }

    @Nonnull
    private static <T> T rowState(@Nonnull Int2ObjectOpenHashMap<RowState<T>> states,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Supplier<T> factory) {
        int rowIndex = rowIndex(bodyRef);
        RowState<T> row = states.get(rowIndex);
        if (row == null || !sameRef(row.bodyRef(), bodyRef)) {
            T state = factory.get();
            states.put(rowIndex, new RowState<>(bodyRef, state));
            return state;
        }
        return row.state();
    }

    @Nullable
    private static <T> T getRowState(@Nonnull Int2ObjectOpenHashMap<RowState<T>> states,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        RowState<T> row = states.get(rowIndex(bodyRef));
        return row != null && sameRef(row.bodyRef(), bodyRef) ? row.state() : null;
    }

    private static <T> void removeRowState(@Nonnull Int2ObjectOpenHashMap<RowState<T>> states,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        RowState<T> row = states.get(rowIndex(bodyRef));
        if (row != null && sameRef(row.bodyRef(), bodyRef)) {
            states.remove(bodyRef.getIndex());
        }
    }

    @Nonnull
    private static <T> Collection<Ref<PhysicsStore>> liveRefs(
        @Nonnull Int2ObjectOpenHashMap<RowState<T>> states) {
        ArrayList<Ref<PhysicsStore>> refs = new ArrayList<>();
        ArrayList<Integer> staleRows = new ArrayList<>();
        for (Int2ObjectMap.Entry<RowState<T>> entry : states.int2ObjectEntrySet()) {
            Ref<PhysicsStore> ref = entry.getValue().bodyRef();
            if (ref != null && ref.isValid()) {
                refs.add(ref);
            } else {
                staleRows.add(entry.getIntKey());
            }
        }
        for (int row : staleRows) {
            states.remove(row);
        }
        return refs;
    }

    private static int rowIndex(@Nonnull Ref<PhysicsStore> bodyRef) {
        return Objects.requireNonNull(bodyRef, "bodyRef").getIndex();
    }

    private static boolean sameRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.getIndex() == second.getIndex()
            && first.getStore() == second.getStore();
    }

    private record RowState<T>(@Nonnull Ref<PhysicsStore> bodyRef,
                               @Nonnull T state) {

        private RowState {
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(state, "state");
        }
    }

    public static final class ChunkBoundaryPauseState {

        @Getter
        private long targetChunkIndex;
        @Nonnull
        private long[] targetChunkIndices = new long[0];
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
            this.targetChunkIndices = new long[] {targetChunkIndex};
            this.originalBodyType = originalBodyType;
            this.linearVelocity.set(linearVelocity);
            this.angularVelocity.set(angularVelocity);
        }

        public void set(long targetChunkIndex,
            @Nonnull long[] targetChunkIndices,
            @Nonnull PhysicsBodySnapshot snapshot) {
            this.targetChunkIndex = targetChunkIndex;
            this.targetChunkIndices = copyTargetChunkIndices(targetChunkIndex, targetChunkIndices);
            this.originalBodyType = snapshot.bodyType();
            snapshot.copyLinearVelocityTo(this.linearVelocity);
            snapshot.copyAngularVelocityTo(this.angularVelocity);
        }

        public void set(long targetChunkIndex, @Nonnull PhysicsBodySnapshot snapshot) {
            this.targetChunkIndex = targetChunkIndex;
            this.targetChunkIndices = new long[] {targetChunkIndex};
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

        @Nonnull
        public long[] getTargetChunkIndices() {
            return targetChunkIndices.clone();
        }

        @Nonnull
        private static long[] copyTargetChunkIndices(long targetChunkIndex,
            @Nonnull long[] targetChunkIndices) {
            if (targetChunkIndices.length == 0) {
                return new long[] {targetChunkIndex};
            }
            long[] copy = targetChunkIndices.clone();
            if (copy[0] != targetChunkIndex) {
                int targetIndex = -1;
                for (int index = 1; index < copy.length; index++) {
                    if (copy[index] == targetChunkIndex) {
                        targetIndex = index;
                        break;
                    }
                }
                if (targetIndex >= 0) {
                    copy[targetIndex] = copy[0];
                }
                copy[0] = targetChunkIndex;
            }
            return copy;
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
