package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkMutationCache.TargetRefreshDecision;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkMutationCache.TargetRefreshReason;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicsChunkCollisionStreamingResourceTest {

    @Test
    void clonePreservesDynamicBodyStreamingTargetState() {
        UUID spaceUuid = uuid(1);
        UUID bodyUuid = uuid(2);
        PhysicsChunkStreamingBounds bounds = PhysicsChunkStreamingBounds.from(10.0f,
            65.0f,
            -4.0f,
            4);
        PhysicsChunkCollisionStreamingResource resource =
            new PhysicsChunkCollisionStreamingResource();

        TargetRefreshDecision initial = resource.shouldRefreshBodyTarget(spaceUuid,
            bodyUuid,
            bounds,
            false,
            1L,
            100,
            null);
        assertTrue(initial.refresh());
        resource.recordBodyTargetRefresh(spaceUuid, bodyUuid, bounds, false, 1L);

        PhysicsChunkCollisionStreamingResource copy = resource.clone();
        TargetRefreshDecision copied = copy.shouldRefreshBodyTarget(spaceUuid,
            bodyUuid,
            bounds,
            false,
            2L,
            100,
            null);

        assertFalse(copied.refresh());
        assertEquals(TargetRefreshReason.STABLE_SKIP, copied.reason());
    }

    @Test
    void clonePreservesRefBodyStreamingTargetState() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physicschunk-streaming-resource-ref-clone")),
            EmptyResourceStorage.get());
        try {
            UUID spaceUuid = uuid(3);
            Ref<PhysicsStore> bodyRef = new Ref<>(store, 11);
            PhysicsChunkStreamingBounds bounds = PhysicsChunkStreamingBounds.from(32.0f,
                65.0f,
                16.0f,
                4);
            PhysicsChunkCollisionStreamingResource resource =
                new PhysicsChunkCollisionStreamingResource();

            TargetRefreshDecision initial = resource.shouldRefreshBodyTarget(spaceUuid,
                bodyRef,
                bounds,
                false,
                1L,
                100,
                null);
            assertTrue(initial.refresh());
            resource.recordBodyTargetRefresh(spaceUuid, bodyRef, bounds, false, 1L);

            PhysicsChunkCollisionStreamingResource copy = resource.clone();
            TargetRefreshDecision copied = copy.shouldRefreshBodyTarget(spaceUuid,
                bodyRef,
                bounds,
                false,
                2L,
                100,
                null);

            assertFalse(copied.refresh());
            assertEquals(TargetRefreshReason.STABLE_SKIP, copied.reason());
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }
}
