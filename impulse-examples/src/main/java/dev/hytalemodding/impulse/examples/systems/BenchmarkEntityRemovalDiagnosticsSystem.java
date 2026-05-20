package dev.hytalemodding.impulse.examples.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

public final class BenchmarkEntityRemovalDiagnosticsSystem extends RefSystem<EntityStore> {

    private static final AtomicInteger PHYSICS_ENTITY_REMOVALS = new AtomicInteger();
    private static final ConcurrentMap<String, AtomicInteger> REMOVALS_BY_REASON = new ConcurrentHashMap<>();

    public static void reset() {
        PHYSICS_ENTITY_REMOVALS.set(0);
        REMOVALS_BY_REASON.clear();
    }

    @Nonnull
    public static String snapshot() {
        List<String> reasons = new ArrayList<>();
        REMOVALS_BY_REASON.forEach((reason, count) -> reasons.add(reason + "=" + count.get()));
        Collections.sort(reasons);
        return "physicsEntityRemovals="
            + PHYSICS_ENTITY_REMOVALS.get()
            + " removalReasons="
            + (reasons.isEmpty() ? "none" : String.join(",", reasons));
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsBodyAttachmentComponent component = store.getComponent(ref,
            PhysicsBodyAttachmentComponent.getComponentType());
        if (component == null) {
            return;
        }

        PHYSICS_ENTITY_REMOVALS.incrementAndGet();
        REMOVALS_BY_REASON.computeIfAbsent(reason.name(), ignored -> new AtomicInteger())
            .incrementAndGet();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return PhysicsBodyAttachmentComponent.getComponentType();
    }
}
