package dev.hytalemodding.impulse.core.internal.resources.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicsBodyRegistryTest {

    @Test
    void indexesRegistrationsBySpaceWithoutScanningUnrelatedSpaces() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:body-registry-space-index");
        var firstSpace = backend.createSpace(new SpaceId(1));
        var secondSpace = backend.createSpace(new SpaceId(2));
        PhysicsBody firstBody = firstSpace.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody secondBody = secondSpace.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey firstId = RigidBodyKey.of(0L, 1L);
        RigidBodyKey secondId = RigidBodyKey.of(0L, 2L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();

        registry.registerBody(firstId,
            firstBody,
            firstSpace.id(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        registry.registerBody(secondId,
            secondBody,
            secondSpace.id(),
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        List<RigidBodyKey> firstSpaceIds = new ArrayList<>();
        registry.forEachRegistration(firstSpace.id(),
            registration -> firstSpaceIds.add(registration.id()));

        assertEquals(List.of(firstId), firstSpaceIds);
        assertEquals(1, registry.getRegistrationCount(firstSpace.id()));
        assertEquals(1, registry.getRegistrationCount(secondSpace.id()));

        registry.unregisterBody(firstId);

        assertEquals(0, registry.getRegistrationCount(firstSpace.id()));
        assertEquals(1, registry.getRegistrationCount(secondSpace.id()));
    }

    @Test
    void reRegisteringSameBodyMovesSpaceIndexEntry() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:body-registry-space-move");
        var firstSpace = backend.createSpace(new SpaceId(1));
        var secondSpace = backend.createSpace(new SpaceId(2));
        PhysicsBody body = firstSpace.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 3L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            body,
            firstSpace.id(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        registry.registerBody(bodyId,
            body,
            secondSpace.id(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        assertEquals(0, registry.getRegistrationCount(firstSpace.id()));
        assertEquals(1, registry.getRegistrationCount(secondSpace.id()));
        List<RigidBodyKey> secondSpaceIds = new ArrayList<>();
        registry.forEachRegistration(secondSpace.id(),
            registration -> secondSpaceIds.add(registration.id()));
        assertEquals(List.of(bodyId), secondSpaceIds);
    }

    @Test
    void registrationViewsReuseCachedImmutableMetadata() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:body-registry-view-cache");
        var space = backend.createSpace(new SpaceId(1));
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 4L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            body,
            space.id(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        PhysicsBodyRegistrationView first = registry.getRegistrationView(bodyId);
        PhysicsBodyRegistrationView second = registry.getRegistrationView(bodyId);
        PhysicsBodyRegistrationView fromCollection = registry.getRegistrationViews()
            .iterator()
            .next();

        assertSame(first, second);
        assertSame(first, fromCollection);
    }
}
