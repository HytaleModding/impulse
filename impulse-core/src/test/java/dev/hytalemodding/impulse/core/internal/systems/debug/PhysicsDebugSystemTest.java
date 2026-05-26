package dev.hytalemodding.impulse.core.internal.systems.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend.InMemoryPhysicsSpace;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsDebugSystemTest {

    @Test
    void collectVisibleJointPrimitivesUsesAnchorsForDistanceAndRendering() {
        PhysicsSpace space = new FakePhysicsBackend(new BackendId("test:debug-joints"))
            .createSpace(new SpaceId(1));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsBody bodyA = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody bodyB = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        bodyA.setPosition(1.0f, 0.0f, 0.0f);
        bodyB.setPosition(5.0f, 0.0f, 0.0f);
        space.createHingeJoint(bodyA,
            bodyB,
            new Vector3f(1.0f, 0.0f, 0.0f),
            new Vector3f(-1.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, 1.0f, 0.0f));

        List<PhysicsDebugRenderer.JointDebugPrimitive> visible =
            PhysicsJointDebugCapture.collectVisibleJointPrimitives(resource,
                space,
                new Vector3d(3.0, 0.0, 0.0),
                1.0,
                4);

        assertEquals(1, visible.size());
        PhysicsDebugRenderer.JointDebugPrimitive primitive = visible.getFirst();
        assertEquals(new Vector3d(2.0, 0.0, 0.0), primitive.anchorA());
        assertEquals(new Vector3d(4.0, 0.0, 0.0), primitive.anchorB());
        assertEquals(0.0, primitive.axis().x, 0.00001);
        assertEquals(0.9, primitive.axis().y, 0.00001);
        assertEquals(0.0, primitive.axis().z, 0.00001);

        assertTrue(PhysicsJointDebugCapture.collectVisibleJointPrimitives(resource,
            space,
            new Vector3d(8.0, 0.0, 0.0),
            1.0,
            4).isEmpty());
        assertTrue(PhysicsJointDebugCapture.collectVisibleJointPrimitives(resource,
            space,
            new Vector3d(3.0, 0.0, 0.0),
            1.0,
            0).isEmpty());
    }

    @Test
    void collectVisibleContactPrimitivesCapturesPointsAndNormals() {
        InMemoryPhysicsSpace space = (InMemoryPhysicsSpace) new FakePhysicsBackend(
            new BackendId("test:debug-contacts")).createSpace(new SpaceId(2));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsBody bodyA = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody bodyB = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        space.addContact(new PhysicsContact(bodyA,
            bodyB,
            new Vector3f(0.0f, 0.0f, 0.0f),
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Vector3f(0.0f, 2.0f, 0.0f),
            -0.1f,
            20.0f));

        List<PhysicsDebugRenderer.ContactDebugPrimitive> visible =
            PhysicsContactDebugCapture.collectVisibleContactPrimitives(resource,
                space,
                new Vector3d(1.0, 2.0, 3.0),
                0.5,
                4);

        assertEquals(1, visible.size());
        PhysicsDebugRenderer.ContactDebugPrimitive primitive = visible.getFirst();
        assertEquals(new Vector3d(1.0, 2.0, 3.0), primitive.point());
        assertEquals(0.0, primitive.normal().x, 0.00001);
        assertEquals(1.0, primitive.normal().y, 0.00001);
        assertEquals(0.0, primitive.normal().z, 0.00001);

        assertTrue(PhysicsContactDebugCapture.collectVisibleContactPrimitives(resource,
            space,
            new Vector3d(3.0, 2.0, 3.0),
            0.5,
            4).isEmpty());
        assertTrue(PhysicsContactDebugCapture.collectVisibleContactPrimitives(resource,
            space,
            new Vector3d(1.0, 2.0, 3.0),
            0.5,
            0).isEmpty());
    }
}
