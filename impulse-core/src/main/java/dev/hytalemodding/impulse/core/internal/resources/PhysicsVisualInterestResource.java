package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.BodyVisualInterestState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.VisualInterest;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only EntityStore visual-interest state for generated physics visuals.
 */
public final class PhysicsVisualInterestResource implements Resource<EntityStore> {

    @Nullable
    private static ResourceType<EntityStore, PhysicsVisualInterestResource> resourceType;

    private final List<VisualInterest> syntheticVisualInterests = new ArrayList<>();
    private final Map<UUID, BodyVisualInterestState> bodyVisualInterestStates =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<BodyVisualInterestRefState> bodyVisualInterestStatesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    private final AtomicLong visualInterestTick = new AtomicLong();

    public synchronized void setSyntheticVisualInterests(
        @Nonnull Collection<VisualInterest> interests) {
        syntheticVisualInterests.clear();
        syntheticVisualInterests.addAll(interests);
    }

    @Nonnull
    public synchronized List<VisualInterest> getSyntheticVisualInterests() {
        return new ArrayList<>(syntheticVisualInterests);
    }

    public synchronized void clearSyntheticVisualInterests() {
        syntheticVisualInterests.clear();
    }

    @Nonnull
    public synchronized BodyVisualInterestState getOrCreateBodyVisualInterestState(
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        BodyVisualInterestState state;
        if (isValidRef(bodyRef)) {
            state = getOrCreateBodyVisualInterestState(bodyRef);
        } else {
            state = bodyVisualInterestStates.computeIfAbsent(bodyUuid,
                _ -> new BodyVisualInterestState());
        }
        state.advanceVisualInterestTick(visualInterestTick.get());
        return state;
    }

    @Nullable
    public synchronized BodyVisualInterestState getBodyVisualInterestState(
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        BodyVisualInterestState state;
        if (isValidRef(bodyRef)) {
            int rowIndex = bodyRef.getIndex();
            BodyVisualInterestRefState row = bodyVisualInterestStatesByRowIndex.get(rowIndex);
            if (row == null) {
                return null;
            }
            if (!isMatchingLiveRef(row, bodyRef)) {
                bodyVisualInterestStatesByRowIndex.remove(rowIndex);
                return null;
            }
            state = row.state();
        } else {
            state = bodyVisualInterestStates.get(bodyUuid);
        }
        if (state != null) {
            state.advanceVisualInterestTick(visualInterestTick.get());
        }
        return state;
    }

    public synchronized void clearBodyVisualInterestState(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        bodyVisualInterestStates.remove(bodyUuid);
        if (!isValidRef(bodyRef)) {
            return;
        }
        int rowIndex = bodyRef.getIndex();
        BodyVisualInterestRefState row = bodyVisualInterestStatesByRowIndex.get(rowIndex);
        if (row != null && (!row.bodyRef().isValid() || sameRef(row.bodyRef(), bodyRef))) {
            bodyVisualInterestStatesByRowIndex.remove(rowIndex);
        }
    }

    public long advanceVisualInterestTick() {
        return visualInterestTick.incrementAndGet();
    }

    @Nonnull
    @Override
    public PhysicsVisualInterestResource clone() {
        PhysicsVisualInterestResource copy = new PhysicsVisualInterestResource();
        synchronized (this) {
            copy.syntheticVisualInterests.addAll(syntheticVisualInterests);
            copy.bodyVisualInterestStates.putAll(bodyVisualInterestStates);
            for (var entry : bodyVisualInterestStatesByRowIndex.int2ObjectEntrySet()) {
                BodyVisualInterestRefState row = entry.getValue();
                copy.bodyVisualInterestStatesByRowIndex.put(entry.getIntKey(),
                    new BodyVisualInterestRefState(row.bodyRef(), row.state()));
            }
            copy.visualInterestTick.set(visualInterestTick.get());
        }
        return copy;
    }

    @Nullable
    public static ResourceType<EntityStore, PhysicsVisualInterestResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<EntityStore, PhysicsVisualInterestResource> type) {
        resourceType = type;
    }

    public static void clearResourceType() {
        resourceType = null;
    }

    @Nonnull
    private BodyVisualInterestState getOrCreateBodyVisualInterestState(
        @Nonnull Ref<PhysicsStore> bodyRef) {
        int rowIndex = bodyRef.getIndex();
        BodyVisualInterestRefState row = bodyVisualInterestStatesByRowIndex.get(rowIndex);
        if (row == null || !isMatchingLiveRef(row, bodyRef)) {
            row = new BodyVisualInterestRefState(bodyRef, new BodyVisualInterestState());
            bodyVisualInterestStatesByRowIndex.put(rowIndex, row);
        }
        return row.state();
    }

    private static boolean isMatchingLiveRef(@Nonnull BodyVisualInterestRefState row,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return row.bodyRef().isValid() && sameRef(row.bodyRef(), bodyRef);
    }

    private static boolean isValidRef(@Nullable Ref<?> ref) {
        return ref != null && ref.isValid();
    }

    private static boolean sameRef(@Nullable Ref<?> first,
        @Nullable Ref<?> second) {
        return first == second
            || (first != null
                && second != null
                && first.getStore() != null
                && first.getStore() == second.getStore()
                && first.getIndex() == second.getIndex());
    }

    private record BodyVisualInterestRefState(@Nonnull Ref<PhysicsStore> bodyRef,
                                              @Nonnull BodyVisualInterestState state) {
    }
}
