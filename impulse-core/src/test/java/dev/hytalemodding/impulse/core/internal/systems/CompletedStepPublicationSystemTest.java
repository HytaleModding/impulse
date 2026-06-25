package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.systems.publication.CompletedStepPublicationSystem;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsStepEvent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class CompletedStepPublicationSystemTest {

    @Test
    void completedStepPublishesCompactSnapshotAndRecordsCounts() throws Exception {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("completed-step-publication-compact")),
            EmptyResourceStorage.get());
        try {
            PhysicsStepSchedulerResource scheduler = store.getResource(
                PhysicsStepSchedulerResource.getResourceType());
            PhysicsStepSchedulerResource.StepInput input = scheduler.acceptStepInput(0.05f,
                PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
                0.10f);
            UUID spaceUuid = uuid(1);
            UUID firstBodyUuid = uuid(101);
            UUID secondBodyUuid = uuid(102);
            PhysicsSnapshotResource.CompactSnapshotBuilder builder =
                PhysicsSnapshotResource.compactBuilder(2);
            addBody(builder, firstBodyUuid, spaceUuid, PhysicsBodyType.DYNAMIC, 1.0f);
            addBody(builder, secondBodyUuid, spaceUuid, PhysicsBodyType.STATIC, 2.0f);
            PhysicsSnapshotResource.CompactSnapshot compactSnapshot = builder.build();

            assertTrue(scheduler.submitStep(input,
                () -> new PhysicsStepSchedulerResource.CompletedStep(3,
                    9,
                    123L,
                    456L,
                    PhysicsStepPhaseStats.unavailable(),
                    compactSnapshot),
                10L));
            scheduler.whenIdle().toCompletableFuture().get(5, TimeUnit.SECONDS);

            new CompletedStepPublicationSystem().tick(0.25f, 0, store);

            PhysicsSnapshotResource snapshot = store.getResource(
                PhysicsSnapshotResource.getResourceType());
            assertEquals(1L, snapshot.latestSequence());
            assertEquals(2, snapshot.bodyCount());
            assertEquals(0.05f, snapshot.getLatestFrame().dt(), 0.0001f);
            PhysicsBodySnapshot firstBody = snapshot.getBody(firstBodyUuid);
            assertNotNull(firstBody);
            assertEquals(1.0f, firstBody.positionX(), 0.0001f);

            PhysicsProfilingResource.StepSample profiling = store.getResource(
                PhysicsProfilingResource.getResourceType()).latestStepSample();
            assertEquals(123L, profiling.stepSubmitNanos());
            assertEquals(456L, profiling.snapshotNanos());
            assertEquals(2, profiling.publishedBodies());
            assertEquals(1, profiling.schedulerSamples());
            assertEquals(0.05f, profiling.schedulerSubmittedDtSeconds(), 0.0001f);

            PhysicsEventFrame eventFrame = store.getResource(PhysicsEventResource.getResourceType())
                .getLatestFrame();
            PhysicsStepEvent stepEvent = eventFrame.latestStep();
            assertNotNull(stepEvent);
            assertEquals(1L, stepEvent.stepSequence());
            assertEquals(1L, stepEvent.snapshotFrameEpoch());
            assertEquals(2, stepEvent.bodyCount());
            assertEquals(123L, stepEvent.stepNanos());
            assertEquals(456L, stepEvent.snapshotNanos());
        } finally {
            store.getResource(PhysicsStepSchedulerResource.getResourceType()).close();
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static void addBody(@Nonnull PhysicsSnapshotResource.CompactSnapshotBuilder builder,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsBodyType bodyType,
        float positionX) {
        builder.addBody(null,
            bodyUuid,
            spaceUuid,
            bodyType,
            positionX,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            false);
    }

    private static UUID uuid(long lowBits) {
        return new UUID(0L, lowBits);
    }
}
