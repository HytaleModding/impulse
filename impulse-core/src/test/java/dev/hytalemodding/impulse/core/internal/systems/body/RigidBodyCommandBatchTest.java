package dev.hytalemodding.impulse.core.internal.systems.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.recorder.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKeyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodySpaceComponent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RigidBodyCommandBatchTest {

    @Test
    void recordsMultipleSpawnsAndTargetsIntoOneCommandContext() {
        RigidBodyKey firstKey = RigidBodyKey.of(0L, 101L);
        RigidBodyKey secondKey = RigidBodyKey.of(0L, 102L);
        RigidBodyCommandBatch batch = new RigidBodyCommandBatch();

        batch.addSpawn(spawnPlan(firstKey), new Vector3f(1.0f, 2.0f, 3.0f));
        batch.addSpawn(spawnPlan(secondKey), new Vector3f(4.0f, 5.0f, 6.0f));
        batch.addKinematicTarget(firstKey, target(7.0f, true, true));

        assertTrue(batch.hasPendingBody(firstKey));
        assertTrue(batch.hasPendingBody(secondKey));
        assertEquals(4, batch.expectedOperations());

        MutablePhysicsCommandContext context = new MutablePhysicsCommandContext(25L,
            9L,
            batch.expectedOperations());
        batch.record(context);

        assertEquals(4, context.freezeInternal(31L).publicBatch().commandCount());
    }

    @Test
    void kinematicTargetStateAcceptsOnlyChangedTargets() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 103L);
        RigidBodyKinematicTargetState state = new RigidBodyKinematicTargetState();
        RigidBodyKinematicTargetComponent target = target(1.0f, true, false);

        state.beginTick();
        assertTrue(state.shouldSubmit(bodyKey, target));
        state.finishTick();
        state.beginTick();
        assertFalse(state.shouldSubmit(bodyKey, target(1.0f, true, false)));
        assertTrue(state.shouldSubmit(bodyKey, target(2.0f, true, false)));
        state.finishTick();
    }

    @Test
    void kinematicTargetStatePrunesTargetsThatAreNoLongerObserved() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 104L);
        RigidBodyKinematicTargetState state = new RigidBodyKinematicTargetState();

        state.beginTick();
        assertTrue(state.shouldSubmit(bodyKey, target(1.0f, true, false)));
        state.finishTick();

        state.beginTick();
        state.finishTick();

        assertEquals(0, state.trackedTargetCount());
    }

    private static RigidBodySpawnPlan spawnPlan(RigidBodyKey bodyKey) {
        return RigidBodySpawnPlan.create(
            new RigidBodyKeyComponent(bodyKey),
            new RigidBodySpaceComponent(new SpaceId(7)),
            new RigidBodyShapeComponent(ShapeType.BOX,
                0.5f,
                0.5f,
                0.5f,
                0.0f,
                0.0f,
                PhysicsAxis.Y,
                0.0f),
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private static RigidBodyKinematicTargetComponent target(float positionX,
        boolean transformEnabled,
        boolean velocityEnabled) {
        return new RigidBodyKinematicTargetComponent(
            new Vector3f(positionX, 2.0f, 3.0f),
            new Quaternionf(),
            new Vector3f(0.1f, 0.2f, 0.3f),
            new Vector3f(0.4f, 0.5f, 0.6f),
            transformEnabled,
            velocityEnabled,
            true);
    }
}
