package dev.hytalemodding.impulse.core.internal.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.internal.simulation.recorder.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsFalloff;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsRecipes;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsRecipesTest {

    @Test
    void applyImpulseRecipeCopiesVectorValuesAtConstructionTime() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 701L);
        Vector3f impulse = new Vector3f(1.0f, 2.0f, 3.0f);

        var recipe = PhysicsRecipes.applyImpulse(bodyKey, impulse);
        impulse.set(9.0f, 9.0f, 9.0f);

        PhysicsCommandOperations operations = record(recipe).operations();

        assertEquals(1, operations.size());
        assertEquals(PhysicsCommandOperations.APPLY_RIGID_BODY_IMPULSE, operations.opcode(0));
        assertEquals(bodyKey,
            operations.requiredObjectAt(0,
                PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                RigidBodyKey.class));
        assertVector(operations, 0, 1.0f, 2.0f, 3.0f);
    }

    @Test
    void applyForceRecipeRecordsOneOperationPerBody() {
        RigidBodyKey first = RigidBodyKey.of(0L, 702L);
        RigidBodyKey second = RigidBodyKey.of(0L, 703L);

        PhysicsCommandOperations operations = record(
            PhysicsRecipes.applyForce(List.of(first, second), new Vector3f(4.0f, 5.0f, 6.0f)))
            .operations();

        assertEquals(2, operations.size());
        assertEquals(PhysicsCommandOperations.APPLY_RIGID_BODY_FORCE, operations.opcode(0));
        assertEquals(PhysicsCommandOperations.APPLY_RIGID_BODY_FORCE, operations.opcode(1));
        assertEquals(first,
            operations.requiredObjectAt(0,
                PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                RigidBodyKey.class));
        assertEquals(second,
            operations.requiredObjectAt(1,
                PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                RigidBodyKey.class));
        assertVector(operations, 0, 4.0f, 5.0f, 6.0f);
        assertVector(operations, 1, 4.0f, 5.0f, 6.0f);
    }

    @Test
    void radialImpulseUsesSnapshotsFalloffAndDynamicBodiesOnly() {
        RigidBodyKey near = RigidBodyKey.of(0L, 704L);
        RigidBodyKey far = RigidBodyKey.of(0L, 705L);
        RigidBodyKey outside = RigidBodyKey.of(0L, 706L);
        RigidBodyKey staticBody = RigidBodyKey.of(0L, 707L);
        List<PhysicsBodySnapshotEntry> entries = List.of(
            entry(near, 1.0f, PhysicsBodyType.DYNAMIC),
            entry(far, 3.0f, PhysicsBodyType.DYNAMIC),
            entry(outside, 6.0f, PhysicsBodyType.DYNAMIC),
            entry(staticBody, 1.0f, PhysicsBodyType.STATIC));

        PhysicsCommandOperations operations = record(PhysicsRecipes.radialImpulse(entries,
            new Vector3f(),
            10.0f,
            5.0f,
            PhysicsFalloff.linear())).operations();

        assertEquals(2, operations.size());
        assertEquals(near,
            operations.requiredObjectAt(0,
                PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                RigidBodyKey.class));
        assertEquals(far,
            operations.requiredObjectAt(1,
                PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                RigidBodyKey.class));
        assertVector(operations, 0, 8.0f, 0.0f, 0.0f);
        assertVector(operations, 1, 4.0f, 0.0f, 0.0f);
    }

    private static RecordedPhysicsCommandBatch record(
        dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecipe recipe) {
        MutablePhysicsCommandContext context = new MutablePhysicsCommandContext(1L, 1L);
        context.compose(recipe);
        return context.freezeInternal(1L);
    }

    private static PhysicsBodySnapshotEntry entry(RigidBodyKey bodyKey,
        float positionX,
        PhysicsBodyType bodyType) {
        return new PhysicsBodySnapshotEntry(bodyKey,
            PhysicsBodySnapshot.of(positionX,
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
                bodyType,
                false,
                false,
                1.0f,
                0.5f,
                0.0f,
                0.0f,
                0.0f,
                1,
                1,
                false,
                0.0f,
                ShapeType.BOX,
                true,
                0.5f,
                0.5f,
                0.5f,
                0.0f,
                0.0f,
                PhysicsAxis.Y),
            new SpaceId(1),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
    }

    private static void assertVector(PhysicsCommandOperations operations,
        int operationIndex,
        float x,
        float y,
        float z) {
        assertEquals(x,
            operations.floatAt(operationIndex,
                PhysicsCommandOperations.VECTOR_COMMAND_X_FLOAT_SLOT),
            0.0001f);
        assertEquals(y,
            operations.floatAt(operationIndex,
                PhysicsCommandOperations.VECTOR_COMMAND_Y_FLOAT_SLOT),
            0.0001f);
        assertEquals(z,
            operations.floatAt(operationIndex,
                PhysicsCommandOperations.VECTOR_COMMAND_Z_FLOAT_SLOT),
            0.0001f);
    }
}
