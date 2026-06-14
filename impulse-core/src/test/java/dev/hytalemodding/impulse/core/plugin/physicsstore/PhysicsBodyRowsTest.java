package dev.hytalemodding.impulse.core.plugin.physicsstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.UUID;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodyRowsTest {

    @Test
    void dynamicBodyRowBuildsSingleBodyGraph() {
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000007");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000042");

        BodyRowDescriptor row = PhysicsBodyRows.body(spaceUuid,
            bodyUuid,
            new Vector3f(1.0f, 2.0f, 3.0f),
            PhysicsShapeSpec.box(0.5f, 0.75f, 1.0f),
            PhysicsBodyType.DYNAMIC,
            2.0f,
            RigidBodySpawnSettings.fromOptionalValues(0.4f,
                0.2f,
                0.05f,
                0.1f,
                PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.TERRAIN,
                true),
            null,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        assertEquals(bodyUuid, row.bodyUuid());
        assertEquals(spaceUuid, row.body().getSpaceUuid());
        assertEquals(PhysicsBodyKind.BODY, row.body().getKind());
        assertEquals(PhysicsBodyPersistenceMode.PERSISTENT, row.body().getPersistenceMode());
        assertEquals(PhysicsBodyType.DYNAMIC, row.dynamics().getBodyType());
        assertEquals(2.0f, row.dynamics().getMass(), 0.0001f);
        assertEquals(0.05f, row.dynamics().getLinearDamping(), 0.0001f);
        assertEquals(bodyUuid, row.colliderUuid());
        assertEquals(bodyUuid, row.shapeUuid());
        assertEquals(bodyUuid, row.materialUuid());
        assertEquals(bodyUuid, row.filterUuid());
        assertEquals(ShapeType.BOX, row.shape().getShapeType());
        assertEquals(0.75f, row.shape().getHalfExtentY(), 0.0001f);
        assertEquals(0.4f, row.material().getFriction(), 0.0001f);
        assertEquals(PhysicsCollisionFilters.TERRAIN, row.filter().getCollisionMask());
        assertTrue(row.collider().isSensor());
        assertFalse(row.target().isActive());
        assertTrue(row.target().isTransformEnabled());
        assertFalse(row.target().isVelocityEnabled());
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), row.target().getPosition());
    }
}
