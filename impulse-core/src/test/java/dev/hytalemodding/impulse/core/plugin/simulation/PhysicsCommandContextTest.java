package dev.hytalemodding.impulse.core.plugin.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.recorder.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.JointCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodyCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnBatchRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnTemplateRecorder;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsCommandContextTest {

    @Test
    void freezeCapturesMetadataCountAndRejectsFurtherAppends() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Vector3f linearVelocity = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f angularVelocity = new Vector3f(4.0f, 5.0f, 6.0f);
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(42L, 7L, 4);

        buffer.body(bodyKey)
            .setVelocity(linearVelocity.x, linearVelocity.y, linearVelocity.z,
                angularVelocity.x, angularVelocity.y, angularVelocity.z, true);
        PhysicsCommandBatch batch = buffer.freezeInternal(11L).publicBatch();
        linearVelocity.set(9.0f, 9.0f, 9.0f);
        angularVelocity.set(8.0f, 8.0f, 8.0f);

        assertThrows(IllegalStateException.class,
            () -> buffer.body(bodyKey).destroy());
        assertEquals(42L, batch.metadata().submittedServerTick());
        assertEquals(11L, batch.metadata().commandBatchSequence());
        assertEquals(1, batch.commandCount());
    }

    @Test
    void fluentDslRecordsComposableRecorderOperations() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        Vector3f linearVelocity = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f angularVelocity = new Vector3f(0.0f, 1.0f, 0.0f);
        PhysicsCommandRecipe wakeAndMove = commands -> commands.body(bodyKey,
            body -> body.setVelocity(linearVelocity, angularVelocity)
                .activate());
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(43L, 8L);

        buffer.compose(wakeAndMove);
        linearVelocity.set(99.0f, 99.0f, 99.0f);
        angularVelocity.set(88.0f, 88.0f, 88.0f);

        PhysicsCommandBatch batch = buffer.freezeInternal(12L).publicBatch();

        assertEquals(2, batch.commandCount());
    }

    @Test
    void thinBodyOperationsRecordThroughRecipesWithoutBodyRecorder() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000012"));
        PhysicsCommandRecipe wakeAndMove = commands -> commands
            .setBodyVelocity(bodyKey, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, true)
            .applyBodyImpulse(bodyKey, 7.0f, 8.0f, 9.0f)
            .applyBodyForce(bodyKey, 1.5f, 2.5f, 3.5f, 0.5f, 0.6f, 0.7f)
            .setBodyType(bodyKey, PhysicsBodyType.KINEMATIC, true)
            .activateBody(bodyKey)
            .destroyBody(bodyKey);
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(43L, 8L, 6);

        buffer.compose(wakeAndMove);

        PhysicsCommandBatch batch = buffer.freezeInternal(12L).publicBatch();

        assertEquals(6, batch.commandCount());
    }

    @Test
    void bodyRecipeScopesRecorderToCallback() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000017"));
        AtomicReference<RigidBodyCommandRecorder> captured = new AtomicReference<>();
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(43L, 8L, 2);

        buffer.body(bodyKey, body -> {
            captured.set(body);
            body.setPosition(1.0f, 2.0f, 3.0f, true)
                .applyForce(4.0f, 5.0f, 6.0f);
        });

        assertThrows(IllegalStateException.class, () -> captured.get().activate());
        assertEquals(2, buffer.freezeInternal(12L).publicBatch().commandCount());
    }

    @Test
    void fluentDslRecordsSpawnCommandsWithoutManualCommandConstruction() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        SpaceId spaceId = new SpaceId(4);
        Vector3f position = new Vector3f(2.0f, 3.0f, 4.0f);
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(44L, 9L);

        buffer.spawnBody(bodyKey, spawn -> spawn
            .space(spaceId)
            .box(0.5f, 0.6f, 0.7f)
            .mass(2.0f)
            .type(PhysicsBodyType.DYNAMIC)
            .position(position)
            .settings(RigidBodySpawnSettings.material(0.4f, 0.1f))
            .kind(PhysicsBodyKind.BODY)
            .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY));
        position.set(9.0f, 9.0f, 9.0f);

        PhysicsCommandBatch batch = buffer.freezeInternal(13L).publicBatch();

        assertEquals(1, batch.commandCount());
    }

    @Test
    void bareSpawnRecorderRecordsWhenContextFreezes() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000023"));
        SpaceId spaceId = new SpaceId(4);
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(44L, 9L);

        buffer.spawnBody(bodyKey)
            .space(spaceId)
            .box(0.5f, 0.5f, 0.5f)
            .position(1.0f, 2.0f, 3.0f);

        PhysicsCommandBatch batch = buffer.freezeInternal(13L).publicBatch();

        assertEquals(1, batch.commandCount());
    }

    @Test
    void singleSpawnRecipeScopesRecorderToCallback() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000021"));
        SpaceId spaceId = new SpaceId(4);
        AtomicReference<RigidBodySpawnRecorder> captured = new AtomicReference<>();
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(44L, 9L);

        buffer.spawnBody(bodyKey, spawn -> {
            captured.set(spawn);
            spawn.space(spaceId).box(0.5f, 0.5f, 0.5f);
        });

        assertThrows(IllegalStateException.class, () -> captured.get().mass(2.0f));
        assertEquals(1, buffer.freezeInternal(13L).publicBatch().commandCount());
    }

    @Test
    void spawnRecipesSealCapturedRecordersWhenRecipeThrows() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000022"));
        SpaceId spaceId = new SpaceId(4);
        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f);
        RigidBodySpawnSettings settings = RigidBodySpawnSettings.defaults();
        AtomicReference<RigidBodySpawnRecorder> singleSpawn = new AtomicReference<>();
        AtomicReference<RigidBodySpawnBatchRecorder> bulkSpawns = new AtomicReference<>();
        AtomicReference<RigidBodySpawnTemplateRecorder> templatedSpawns = new AtomicReference<>();
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(44L, 9L);

        assertThrows(IllegalStateException.class,
            () -> buffer.spawnBody(bodyKey, spawn -> {
                singleSpawn.set(spawn);
                throw new IllegalStateException("single spawn failed");
            }));
        assertThrows(IllegalStateException.class,
            () -> buffer.spawnBodies(1, spawns -> {
                bulkSpawns.set(spawns);
                throw new IllegalStateException("bulk spawn failed");
            }));
        assertThrows(IllegalStateException.class,
            () -> buffer.spawnBodies(1,
                spaceId,
                box,
                1.0f,
                PhysicsBodyType.DYNAMIC,
                settings,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                spawns -> {
                    templatedSpawns.set(spawns);
                    throw new IllegalStateException("templated spawn failed");
                }));

        assertThrows(IllegalStateException.class, () -> singleSpawn.get().space(spaceId));
        assertThrows(IllegalStateException.class,
            () -> bulkSpawns.get().body(bodyKey, spawn -> spawn.space(spaceId).shape(box)));
        assertThrows(IllegalStateException.class,
            () -> templatedSpawns.get().body(bodyKey, 1.0f, 2.0f, 3.0f));
    }

    @Test
    void fluentDslRecordsBulkSpawnCommandForRepeatedBodies() {
        SpaceId spaceId = new SpaceId(5);
        RigidBodyKey first = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        RigidBodyKey second = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f);
        RigidBodySpawnSettings settings = RigidBodySpawnSettings.material(0.6f, 0.2f);
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(45L, 10L);

        buffer.spawnBodies(2, spawns -> spawns
            .body(first, spawn -> spawn
                .space(spaceId)
                .shape(box)
                .position(1.0f, 2.0f, 3.0f)
                .settings(settings))
            .body(second,
                spaceId,
                box,
                1.0f,
                PhysicsBodyType.DYNAMIC,
                4.0f,
                5.0f,
                6.0f,
                settings,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY));

        PhysicsCommandBatch batch = buffer.freezeInternal(14L).publicBatch();

        assertEquals(1, batch.commandCount());
    }

    @Test
    void fluentDslRecordsTemplatedBulkSpawnCommandForRepeatedBodies() {
        SpaceId spaceId = new SpaceId(5);
        RigidBodyKey first = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000014"));
        RigidBodyKey second = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000015"));
        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f);
        RigidBodySpawnSettings settings = RigidBodySpawnSettings.material(0.6f, 0.2f);
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(45L, 10L);

        buffer.spawnBodies(2,
            spaceId,
            box,
            1.0f,
            PhysicsBodyType.DYNAMIC,
            settings,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            spawns -> spawns
                .body(first, 1.0f, 2.0f, 3.0f)
                .body(second, 4.0f, 5.0f, 6.0f));

        PhysicsCommandBatch batch = buffer.freezeInternal(14L).publicBatch();

        assertEquals(1, batch.commandCount());
    }

    @Test
    void bulkSpawnRecorderRejectsMutationAfterRecipeReturns() {
        SpaceId spaceId = new SpaceId(5);
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f);
        RigidBodySpawnSettings settings = RigidBodySpawnSettings.defaults();
        AtomicReference<RigidBodySpawnBatchRecorder> captured = new AtomicReference<>();
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(45L, 10L);

        buffer.spawnBodies(1, spawns -> {
            captured.set(spawns);
            spawns.body(bodyKey,
                spaceId,
                box,
                1.0f,
                PhysicsBodyType.DYNAMIC,
                1.0f,
                2.0f,
                3.0f,
                settings,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        });

        assertThrows(IllegalStateException.class,
            () -> captured.get().body(bodyKey, spawn -> spawn.space(spaceId).shape(box)));
        assertEquals(1, buffer.freezeInternal(14L).publicBatch().commandCount());
    }

    @Test
    void templatedBulkSpawnRecorderRejectsMutationAfterRecipeReturns() {
        SpaceId spaceId = new SpaceId(5);
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000016"));
        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f);
        RigidBodySpawnSettings settings = RigidBodySpawnSettings.defaults();
        AtomicReference<RigidBodySpawnTemplateRecorder> captured = new AtomicReference<>();
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(45L, 10L);

        buffer.spawnBodies(1,
            spaceId,
            box,
            1.0f,
            PhysicsBodyType.DYNAMIC,
            settings,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            spawns -> {
                captured.set(spawns);
                spawns.body(bodyKey, 1.0f, 2.0f, 3.0f);
            });

        assertThrows(IllegalStateException.class,
            () -> captured.get().body(bodyKey, 4.0f, 5.0f, 6.0f));
        assertEquals(1, buffer.freezeInternal(14L).publicBatch().commandCount());
    }

    @Test
    void fluentDslRecordsJointCommandsWithoutManualCommandConstruction() {
        SpaceId spaceId = new SpaceId(6);
        JointKey jointKey = JointKey.of(UUID.fromString("00000000-0000-0000-0000-000000000006"));
        RigidBodyKey bodyA = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        RigidBodyKey bodyB = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000008"));
        Vector3f anchorA = new Vector3f(0.0f, -0.5f, 0.0f);
        Vector3f anchorB = new Vector3f(0.0f, 0.5f, 0.0f);
        Vector3f axis = new Vector3f(0.0f, 0.0f, 1.0f);
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(46L, 11L);

        buffer.joint(jointKey, joint -> joint
            .space(spaceId)
            .bodies(bodyA, bodyB)
            .hinge(anchorA, anchorB, axis)
            .limits(-0.75f, 0.75f)
            .motor(1.25f, 2.5f));
        anchorA.set(9.0f, 9.0f, 9.0f);
        anchorB.set(8.0f, 8.0f, 8.0f);
        axis.set(7.0f, 7.0f, 7.0f);

        PhysicsCommandBatch batch = buffer.freezeInternal(15L).publicBatch();

        assertEquals(1, batch.commandCount());
    }

    @Test
    void bareJointRecorderRecordsWhenContextFreezes() {
        SpaceId spaceId = new SpaceId(6);
        JointKey jointKey = JointKey.of(UUID.fromString("00000000-0000-0000-0000-000000000024"));
        RigidBodyKey bodyA = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000025"));
        RigidBodyKey bodyB = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000026"));
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(46L, 11L);

        buffer.joint(jointKey)
            .space(spaceId)
            .bodies(bodyA, bodyB)
            .fixed(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        PhysicsCommandBatch batch = buffer.freezeInternal(15L).publicBatch();

        assertEquals(1, batch.commandCount());
    }

    @Test
    void jointRecipeScopesRecorderToCallback() {
        SpaceId spaceId = new SpaceId(6);
        JointKey jointKey = JointKey.of(UUID.fromString("00000000-0000-0000-0000-000000000018"));
        RigidBodyKey bodyA = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000019"));
        RigidBodyKey bodyB = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        AtomicReference<JointCommandRecorder> captured = new AtomicReference<>();
        MutablePhysicsCommandContext buffer = new MutablePhysicsCommandContext(46L, 11L);

        buffer.joint(jointKey, joint -> {
            captured.set(joint);
            joint.space(spaceId)
                .bodies(bodyA, bodyB)
                .fixed(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        });

        assertThrows(IllegalStateException.class, () -> captured.get().motor(1.0f, 2.0f));
        assertEquals(1, buffer.freezeInternal(15L).publicBatch().commandCount());
    }

}
