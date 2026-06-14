package dev.hytalemodding.impulse.core.internal.systems.publication;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource.StepSample;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsDetachedVisualMaterializationSystem;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;

/**
 * Forwards copied PhysicsStore event frames into the existing EntityStore world-event boundary.
 */
public final class PhysicsStoreEventPublicationSystem extends TickingSystem<EntityStore> {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsDetachedVisualMaterializationSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    @Nonnull
    private final Map<Store<EntityStore>, Long> lastPublishedSequences =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!(world instanceof PhysicsStoreWorld physicsStoreWorld)) {
            return;
        }
        Store<PhysicsStore> physics = physicsStoreWorld.getPhysicsStore().getStore();
        if (physics.isShutdown()) {
            return;
        }
        PhysicsStoreThreading.requireWorldThread(physics, "publish PhysicsStore event frame");
        PhysicsEventFrame frame = physics.getResource(PhysicsEventResource.getResourceType())
            .getLatestFrame();
        if (frame.frameSequence() <= 0L || !markPublished(store, frame.frameSequence())) {
            return;
        }
        recordProfiling(store, physics);
        store.invoke(new PhysicsEventFramePublishedEvent(frame));
    }

    private boolean markPublished(@Nonnull Store<EntityStore> store, long frameSequence) {
        synchronized (lastPublishedSequences) {
            long previous = lastPublishedSequences.getOrDefault(store, 0L);
            if (frameSequence <= previous) {
                return false;
            }
            lastPublishedSequences.put(store, frameSequence);
            return true;
        }
    }

    private static void recordProfiling(@Nonnull Store<EntityStore> store,
        @Nonnull Store<PhysicsStore> physics) {
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        if (!runtimeProfiling.isEnabled()) {
            return;
        }
        StepSample sample = physics.getResource(PhysicsProfilingResource.getResourceType())
            .latestStepSample();
        runtimeProfiling.recordStep(sample.spaces(),
            sample.substeps(),
            sample.stepSubmitNanos(),
            sample.publishedBodies(),
            0,
            sample.snapshotNanos(),
            0L,
            0L,
            System.nanoTime(),
            sample.nativePhaseStats());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
