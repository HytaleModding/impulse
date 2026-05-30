package dev.hytalemodding.impulse.core.internal.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PhysicsCommandOperationsAllocationTest {

    private static final RigidBodyKey BODY_KEY =
        RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void freezeReusesFloatStorageAfterExactGrowth() throws Exception {
        PhysicsCommandOperations operations = new PhysicsCommandOperations(2);
        operations.addSetVelocity(BODY_KEY,
            1.0f,
            2.0f,
            3.0f,
            4.0f,
            5.0f,
            6.0f,
            true);
        operations.addSetVelocity(BODY_KEY,
            7.0f,
            8.0f,
            9.0f,
            10.0f,
            11.0f,
            12.0f,
            true);

        float[] grownStorage = floats(operations);
        PhysicsCommandOperations frozen = operations.freeze();

        assertSame(grownStorage, floats(frozen));
    }

    @Test
    void templatedSpawnBatchStoresBodyKeysAsPrimitiveBits() {
        RigidBodySpawnTemplateBatch batch = new RigidBodySpawnTemplateBatch(1,
            new SpaceId(1),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            PhysicsBodyType.DYNAMIC,
            RigidBodySpawnSettings.defaults(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        batch.add(BODY_KEY, 1.0f, 2.0f, 3.0f);
        RigidBodySpawnTemplateBatch frozen = batch.freeze();

        assertEquals(BODY_KEY, frozen.bodyKey(0));
        for (Field field : RigidBodySpawnTemplateBatch.class.getDeclaredFields()) {
            assertNotEquals(RigidBodyKey[].class,
                field.getType(),
                "Templated spawn batches should store generated keys as primitive UUID bits");
        }
    }

    @Test
    void genericSpawnBatchStoresBodyKeysAsPrimitiveBits() throws Exception {
        RigidBodySpawnBatch batch = new RigidBodySpawnBatch(1);

        batch.add(BODY_KEY,
            new SpaceId(1),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            PhysicsBodyType.DYNAMIC,
            1.0f,
            2.0f,
            3.0f,
            RigidBodySpawnSettings.defaults(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        RigidBodySpawnBatch frozen = batch.freeze();

        assertEquals(BODY_KEY, frozen.bodyKey(0));
        for (Object value : objects(frozen)) {
            assertNotEquals(RigidBodyKey.class,
                value != null ? value.getClass() : null,
                "Generic spawn batches should store body keys as primitive UUID bits");
        }
    }

    private static float[] floats(PhysicsCommandOperations operations) throws Exception {
        Field field = PhysicsCommandOperations.class.getDeclaredField("floats");
        field.setAccessible(true);
        return (float[]) field.get(operations);
    }

    private static Object[] objects(RigidBodySpawnBatch batch) throws Exception {
        Field field = RigidBodySpawnBatch.class.getDeclaredField("objects");
        field.setAccessible(true);
        return (Object[]) field.get(batch);
    }
}
