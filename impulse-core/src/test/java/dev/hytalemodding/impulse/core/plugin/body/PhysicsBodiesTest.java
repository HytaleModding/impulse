package dev.hytalemodding.impulse.core.plugin.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodiesTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void spawnCreatesAndRegistersBodyOnPhysicsOwner() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:spawn-body-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource runtime = new PhysicsWorldRuntimeResource();
        PhysicsWorldResource resource = runtime;
        PhysicsSpace space = runtime.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);

        PhysicsBodySpawnResult result = PhysicsBodies.spawn(resource,
            PhysicsBodySpawnSpec.persistentBody(space.getId(),
                ownerSpace -> {
                    PhysicsBody body = ownerSpace.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    body.setPosition(1.0f, 2.0f, 3.0f);
                    return body;
                }));

        assertEquals(space.getId(), result.spaceId());
        assertEquals(PhysicsBodyKind.BODY, result.kind());
        assertEquals(PhysicsBodyPersistenceMode.PERSISTENT, result.persistenceMode());
        assertEquals(1, space.bodyCount());
        assertEquals(result.bodyId(), resource.getBodyRegistrationView(result.bodyId()).id());
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
            resource.getBodySnapshot(result.bodyId()).position());
    }

    @Test
    void spawnRejectsDuplicateExplicitIdBeforeCreatingBody() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:spawn-duplicate-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource runtime = new PhysicsWorldRuntimeResource();
        PhysicsWorldResource resource = runtime;
        PhysicsSpace space = runtime.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBodySpawnResult first = PhysicsBodies.spawn(resource,
            PhysicsBodySpawnSpec.persistentBody(space.getId(),
                ownerSpace -> ownerSpace.createBox(0.5f, 0.5f, 0.5f, 1.0f)));
        AtomicInteger factoryCalls = new AtomicInteger();

        assertThrows(IllegalArgumentException.class,
            () -> PhysicsBodies.spawn(resource,
                PhysicsBodySpawnSpec.persistentBody(first.bodyId(),
                    space.getId(),
                    ownerSpace -> {
                        factoryCalls.incrementAndGet();
                        return ownerSpace.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    })));
        assertEquals(0, factoryCalls.get());
        assertEquals(1, space.bodyCount());
    }
}
