package dev.hytalemodding.impulse.api.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsVoxelTerrainCapability;
import org.junit.jupiter.api.Test;

class FakePhysicsBackendCapabilityTest {

    @Test
    void appliesSolverAndActivationSettingsThroughCapabilities() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:fake");
        PhysicsSpace space = backend.createSpace();

        PhysicsSolverTuningCapability solverTuning = space.getCapability(PhysicsSolverTuningCapability.class)
            .orElseThrow();
        PhysicsActivationTuningCapability activationTuning = space.getCapability(PhysicsActivationTuningCapability.class)
            .orElseThrow();

        solverTuning.setSolverTuning(new PhysicsSolverTuning(12, 3));
        activationTuning.setActivationTuning(new PhysicsActivationTuning(0.25f, 0.5f, 1.75f));

        FakePhysicsBackend.InMemoryPhysicsSpace inMemorySpace = backend.createdSpaces().get(0);
        assertEquals(12, inMemorySpace.getSolverIterations());
        assertEquals(3, inMemorySpace.getStabilizationIterations());
        assertEquals(0.25f, inMemorySpace.getSleepLinearThreshold(), 0.0001f);
        assertEquals(0.5f, inMemorySpace.getSleepAngularThreshold(), 0.0001f);
        assertEquals(1.75f, inMemorySpace.getSleepTimeUntilSleep(), 0.0001f);
    }

    @Test
    void doesNotExposeVoxelCapability() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:fake");
        PhysicsSpace space = backend.createSpace();

        assertTrue(space.getCapability(PhysicsVoxelTerrainCapability.class).isEmpty());
    }
}
