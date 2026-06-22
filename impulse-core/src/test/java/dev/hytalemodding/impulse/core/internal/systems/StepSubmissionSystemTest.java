package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.systems.step.StepSubmissionSystem;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StepSubmissionSystemTest {

    @Test
    void tickWithNoRuntimeSpacesDoesNotSubmitOwnerLaneStep() throws Exception {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("step-submission-no-spaces")),
            EmptyResourceStorage.get());
        try {
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();

            new StepSubmissionSystem().tick(0.05f, 0, store);

            PhysicsStepSchedulerResource scheduler = store.getResource(
                PhysicsStepSchedulerResource.getResourceType());
            scheduler.whenIdle().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertNull(scheduler.pollCompletedStep(),
                "Zero-space physics ticks should not submit an owner-lane step");
        } finally {
            store.getResource(PhysicsStepSchedulerResource.getResourceType()).close();
            registry.removeStore(store);
            registry.shutdown();
        }
    }
}
