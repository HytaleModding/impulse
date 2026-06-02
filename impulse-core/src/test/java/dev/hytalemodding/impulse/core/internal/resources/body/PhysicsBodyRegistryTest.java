package dev.hytalemodding.impulse.core.internal.resources.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsBodyRegistryTest {

    @Test
    void indexesRegistrationsBySpaceWithoutScanningUnrelatedSpaces() {
        SpaceId firstSpace = new SpaceId(1);
        SpaceId secondSpace = new SpaceId(2);
        RigidBodyKey firstId = RigidBodyKey.of(0L, 1L);
        RigidBodyKey secondId = RigidBodyKey.of(0L, 2L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();

        registry.registerBody(firstId,
            handle(11L),
            firstSpace,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        registry.registerBody(secondId,
            handle(12L),
            secondSpace,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        List<RigidBodyKey> firstSpaceIds = new ArrayList<>();
        registry.forEachRegistration(firstSpace,
            registration -> firstSpaceIds.add(registration.bodyKey()));

        assertEquals(List.of(firstId), firstSpaceIds);
        assertEquals(1, registry.getRegistrationCount(firstSpace));
        assertEquals(1, registry.getRegistrationCount(secondSpace));

        registry.unregisterBody(firstId);

        assertEquals(0, registry.getRegistrationCount(firstSpace));
        assertEquals(1, registry.getRegistrationCount(secondSpace));
    }

    @Test
    void reRegisteringSameBodyWithDifferentSpaceIsRejectedWithoutMovingIndex() {
        SpaceId firstSpace = new SpaceId(1);
        SpaceId secondSpace = new SpaceId(2);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 3L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            handle(21L),
            firstSpace,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        assertThrows(IllegalArgumentException.class, () -> registry.registerBody(bodyId,
            handle(21L),
            secondSpace,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY));

        assertEquals(1, registry.getRegistrationCount(firstSpace));
        assertEquals(0, registry.getRegistrationCount(secondSpace));
    }

    @Test
    void registrationViewsReuseCachedImmutableMetadata() {
        SpaceId space = new SpaceId(1);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 4L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            handle(31L),
            space,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        PhysicsBodyRegistrationView first = registry.getRegistrationView(bodyId);
        PhysicsBodyRegistrationView second = registry.getRegistrationView(bodyId);
        PhysicsBodyRegistrationView fromCollection = registry.getRegistrationViews()
            .iterator()
            .next();

        assertSame(first, second);
        assertSame(first, fromCollection);
        assertEquals(bodyId, first.bodyKey());
    }

    @Nonnull
    private static BackendBodyHandle handle(long value) {
        return new BackendBodyHandle(value);
    }
}
