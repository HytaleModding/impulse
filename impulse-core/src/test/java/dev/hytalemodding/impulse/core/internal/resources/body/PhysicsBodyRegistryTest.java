package dev.hytalemodding.impulse.core.internal.resources.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
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
            firstSpace);
        registry.registerBody(secondId,
            handle(12L),
            secondSpace);

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
            firstSpace);

        assertThrows(IllegalArgumentException.class, () -> registry.registerBody(bodyId,
            handle(21L),
            secondSpace));

        assertEquals(1, registry.getRegistrationCount(firstSpace));
        assertEquals(0, registry.getRegistrationCount(secondSpace));
    }

    @Test
    void registrationsExposeBodyIdentityAndSpace() {
        SpaceId space = new SpaceId(1);
        UUID bodyId = new UUID(0L, 4L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            handle(31L),
            space);

        PhysicsBodyRegistration first = registry.getRegistration(bodyId);
        PhysicsBodyRegistration second = registry.getRegistration(bodyId);
        PhysicsBodyRegistration fromCollection = registry.getRegistrations()
            .iterator()
            .next();

        assertSame(first, second);
        assertSame(first, fromCollection);
        assertEquals(bodyId, first.bodyUuid());
        assertEquals(space, first.spaceId());
    }

    @Nonnull
    private static BackendBodyHandle handle(long value) {
        return new BackendBodyHandle(value);
    }
}
