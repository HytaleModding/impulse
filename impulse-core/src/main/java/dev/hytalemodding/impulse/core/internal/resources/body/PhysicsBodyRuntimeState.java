package dev.hytalemodding.impulse.core.internal.resources.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Transient per-body runtime state that is not part of body identity or persistence.
 */
public final class PhysicsBodyRuntimeState {

    private final Set<PhysicsBody> forcedContinuousCollisionBodies = new ReferenceOpenHashSet<>();
    private final Map<Ref<EntityStore>, PhysicsWorldResource.BodySyncState> bodySyncStates =
        new Reference2ObjectOpenHashMap<>();
    private final Set<PhysicsBodyId> controlledBodies = new ObjectOpenHashSet<>();
    private final Map<PhysicsBodyId, PhysicsWorldResource.ChunkBoundarySafeState> chunkBoundarySafeStates =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsBodyId, PhysicsWorldResource.ChunkBoundaryPauseState> chunkBoundaryPauseStates =
        new Object2ObjectLinkedOpenHashMap<>();

    @Nonnull
    public PhysicsWorldResource.BodySyncState getOrCreateBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.computeIfAbsent(entityRef, ignored -> new PhysicsWorldResource.BodySyncState());
    }

    @Nullable
    public PhysicsWorldResource.BodySyncState getBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.get(entityRef);
    }

    public void clearBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        bodySyncStates.remove(entityRef);
    }

    public void markBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.add(bodyId);
    }

    public void clearControlledBody(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.remove(bodyId);
    }

    public boolean isBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        return controlledBodies.contains(bodyId);
    }

    public void updateChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        PhysicsWorldResource.ChunkBoundarySafeState state = chunkBoundarySafeStates.computeIfAbsent(bodyId,
            ignored -> new PhysicsWorldResource.ChunkBoundarySafeState());
        state.set(position, rotation);
    }

    @Nullable
    public PhysicsWorldResource.ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId) {
        return chunkBoundarySafeStates.get(bodyId);
    }

    public void pauseChunkBoundaryBody(@Nonnull PhysicsBodyId bodyId,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        PhysicsWorldResource.ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(bodyId,
            ignored -> new PhysicsWorldResource.ChunkBoundaryPauseState());
        state.set(targetChunkIndex, originalBodyType, linearVelocity, angularVelocity);
    }

    @Nullable
    public PhysicsWorldResource.ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        return chunkBoundaryPauseStates.get(bodyId);
    }

    public void clearChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        chunkBoundaryPauseStates.remove(bodyId);
    }

    public void markContinuousCollisionForced(@Nonnull PhysicsBody body) {
        forcedContinuousCollisionBodies.add(body);
    }

    @Nonnull
    public Collection<PhysicsBody> getForcedContinuousCollisionBodies() {
        return new ArrayList<>(forcedContinuousCollisionBodies);
    }

    public void clearForcedContinuousCollisionBodies() {
        forcedContinuousCollisionBodies.clear();
    }

    public void remapBodies(@Nonnull Map<PhysicsBody, PhysicsBody> bodyRemaps) {
        for (Map.Entry<PhysicsBody, PhysicsBody> entry : bodyRemaps.entrySet()) {
            PhysicsBody sourceBody = entry.getKey();
            PhysicsBody targetBody = entry.getValue();
            if (sourceBody != targetBody && forcedContinuousCollisionBodies.remove(sourceBody)) {
                forcedContinuousCollisionBodies.add(targetBody);
            }
        }
    }

    public void clearBody(@Nonnull PhysicsBody body, @Nonnull PhysicsBodyId bodyId) {
        forcedContinuousCollisionBodies.remove(body);
        clearBody(bodyId);
    }

    public void clearBody(@Nonnull PhysicsBody body) {
        forcedContinuousCollisionBodies.remove(body);
    }

    public void clearBody(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.remove(bodyId);
        chunkBoundarySafeStates.remove(bodyId);
        chunkBoundaryPauseStates.remove(bodyId);
    }

    public void clear() {
        forcedContinuousCollisionBodies.clear();
        bodySyncStates.clear();
        controlledBodies.clear();
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
    }
}
