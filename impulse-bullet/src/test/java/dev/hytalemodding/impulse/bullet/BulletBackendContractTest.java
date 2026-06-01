package dev.hytalemodding.impulse.bullet;

import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBackendContactEvent;
import dev.hytalemodding.impulse.api.PhysicsBackendEvent;
import dev.hytalemodding.impulse.api.PhysicsBackendEventBatch;
import dev.hytalemodding.impulse.api.PhysicsBackendEventBuffer;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.capability.PhysicsBackendEventsCapability;
import dev.hytalemodding.impulse.api.testsupport.PhysicsBackendContractTest;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulletBackendContractTest extends PhysicsBackendContractTest {

    @Nonnull
    @Override
    protected PhysicsBackend createBackend() {
        return new BulletBackend();
    }

    @Test
    void exposesBulletBackendEventCapability() {
        PhysicsSpace space = createHeadlessSpace();

        PhysicsBackendEventsCapability capability = space.getCapability(PhysicsBackendEventsCapability.class)
            .orElseThrow();

        assertTrue(capability.supportsContactPhase(PhysicsContactPhase.STARTED));
        assertTrue(capability.supportsContactPhase(PhysicsContactPhase.PERSISTED));
        assertTrue(capability.supportsContactPhase(PhysicsContactPhase.ENDED));
        assertFalse(capability.supportsContactPhase(PhysicsContactPhase.FORCE));
        assertFalse(capability.supportsContactPhase(PhysicsContactPhase.OBSERVED));
    }

    @Test
    void stepCopiesBulletContactLifecycleCallbacksIntoBackendEventBatch() {
        PhysicsSpace space = createHeadlessSpace();
        space.setGravity(0.0f, 0.0f, 0.0f);

        PhysicsBody staticBox = space.createBox(0.5f, 0.5f, 0.5f, 0.0f);
        PhysicsBody dynamicBox = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        staticBox.setPosition(0.0f, 0.0f, 0.0f);
        dynamicBox.setPosition(0.9f, 0.0f, 0.0f);

        space.addBody(staticBox);
        space.addBody(dynamicBox);

        PhysicsBackendEventBuffer buffer = new PhysicsBackendEventBuffer();
        PhysicsBackendContactEvent started = stepUntilContactEvent(space,
            buffer,
            PhysicsContactPhase.STARTED,
            staticBox,
            dynamicBox,
            60);
        assertTrue(started != null, "Expected Bullet contact-start callback to emit STARTED");

        PhysicsBackendContactEvent persisted = stepUntilContactEvent(space,
            buffer,
            PhysicsContactPhase.PERSISTED,
            staticBox,
            dynamicBox,
            60);
        assertTrue(persisted != null, "Expected Bullet contact-processed callback to emit PERSISTED");

        dynamicBox.setPosition(4.0f, 0.0f, 0.0f);
        dynamicBox.setLinearVelocity(0.0f, 0.0f, 0.0f);
        dynamicBox.activate();

        PhysicsBackendContactEvent ended = stepUntilContactEvent(space,
            buffer,
            PhysicsContactPhase.ENDED,
            staticBox,
            dynamicBox,
            60);
        assertTrue(ended != null, "Expected Bullet contact-ended callback to emit ENDED");
    }

    private PhysicsBackendContactEvent stepUntilContactEvent(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsBackendEventBuffer buffer,
        @Nonnull PhysicsContactPhase phase,
        @Nonnull PhysicsBody expectedA,
        @Nonnull PhysicsBody expectedB,
        int maxSteps) {
        for (int i = 0; i < maxSteps; i++) {
            space.step(STEP_DT, buffer);
            PhysicsBackendEventBatch batch = buffer.drain();
            for (PhysicsBackendEvent event : batch.events()) {
                if (event instanceof PhysicsBackendContactEvent contactEvent
                    && contactEvent.phase() == phase
                    && referencesBodies(contactEvent, expectedA, expectedB)) {
                    return contactEvent;
                }
            }
        }
        return null;
    }

    private static boolean referencesBodies(@Nonnull PhysicsBackendContactEvent event,
        @Nonnull PhysicsBody expectedA,
        @Nonnull PhysicsBody expectedB) {
        return (event.bodyA() == expectedA && event.bodyB() == expectedB)
            || (event.bodyA() == expectedB && event.bodyB() == expectedA);
    }
}
