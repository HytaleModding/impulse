package dev.hytalemodding.impulse.core.internal.resources.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PhysicsBodyRegistryTest {

    @Test
    void indexesRegistrationsBySpaceWithoutScanningUnrelatedSpaces() {
        SpaceId firstSpace = new SpaceId(1);
        SpaceId secondSpace = new SpaceId(2);
        UUID firstId = new UUID(0L, 1L);
        UUID secondId = new UUID(0L, 2L);
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

        List<UUID> firstSpaceIds = new ArrayList<>();
        registry.forEachRegistration(firstSpace,
            registration -> firstSpaceIds.add(registration.bodyUuid()));

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
        UUID bodyId = new UUID(0L, 3L);
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
        UUID bodyId = new UUID(0L, 4L);
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
        Assertions.assertNotNull(first);
        assertEquals(bodyId, first.bodyUuid());
    }

    @Nonnull
    private static BackendBodyHandle handle(long value) {
        return new BackendBodyHandle(value);
    }
}
