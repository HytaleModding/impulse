package dev.hytalemodding.impulse.core.internal.systems.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend.InMemoryPhysicsSpace;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugContactView;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugJointView;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsDebugSystemTest {

    @Test
    void debugQueryCachePollsCompletedResultsWithoutBlocking() {
        PhysicsDebugSystem.DebugQueryCache cache = new PhysicsDebugSystem.DebugQueryCache();
        PhysicsDebugSystem.DebugQueryKey contactsKey =
            PhysicsDebugSystem.DebugQueryKey.contacts(new SpaceId(11), UUID.randomUUID());
        PhysicsDebugSystem.DebugQueryKey jointsKey =
            PhysicsDebugSystem.DebugQueryKey.joints(new SpaceId(11), UUID.randomUUID());
        CompletableFuture<List<PhysicsDebugContactView>> pendingContacts = new CompletableFuture<>();
        CompletableFuture<List<PhysicsDebugJointView>> pendingJoints = new CompletableFuture<>();
        List<PhysicsDebugContactView> contacts = List.of(new PhysicsDebugContactView(1.0f,
            2.0f,
            3.0f,
            true,
            0.0f,
            1.0f,
            0.0f));
        List<PhysicsDebugJointView> joints = List.of(new PhysicsDebugJointView(1.0f,
            2.0f,
            3.0f,
            4.0f,
            5.0f,
            6.0f,
            true,
            0.0f,
            1.0f,
            0.0f));

        assertTrue(cache.requestContactsIfIdle(contactsKey, () -> pendingContacts));
        assertTrue(cache.requestJointsIfIdle(jointsKey, () -> pendingJoints));
        assertTrue(cache.contactsOrEmpty(contactsKey).isEmpty());
        assertTrue(cache.jointsOrEmpty(jointsKey).isEmpty());

        pendingContacts.complete(contacts);
        pendingJoints.complete(joints);

        assertEquals(contacts, cache.contactsOrEmpty(contactsKey));
        assertEquals(joints, cache.jointsOrEmpty(jointsKey));
        assertTrue(cache.requestContactsIfIdle(contactsKey,
            () -> CompletableFuture.completedFuture(List.of())));
        assertTrue(cache.requestJointsIfIdle(jointsKey,
            () -> CompletableFuture.completedFuture(List.of())));
    }

    @Test
    void debugQueryCacheDoesNotSubmitDuplicateContactsWhilePending() {
        PhysicsDebugSystem.DebugQueryCache cache = new PhysicsDebugSystem.DebugQueryCache();
        PhysicsDebugSystem.DebugQueryKey key =
            PhysicsDebugSystem.DebugQueryKey.contacts(new SpaceId(12), UUID.randomUUID());
        CompletableFuture<List<PhysicsDebugContactView>> pending = new CompletableFuture<>();
        AtomicInteger submissions = new AtomicInteger();
        List<PhysicsDebugContactView> contacts = List.of(new PhysicsDebugContactView(1.0f,
            2.0f,
            3.0f,
            true,
            0.0f,
            1.0f,
            0.0f));

        assertTrue(cache.requestContactsIfIdle(key, () -> {
            submissions.incrementAndGet();
            return pending;
        }));
        assertFalse(cache.requestContactsIfIdle(key, () -> {
            submissions.incrementAndGet();
            return CompletableFuture.completedFuture(List.of());
        }));

        assertEquals(1, submissions.get());
        assertTrue(cache.contactsOrEmpty(key).isEmpty());
        pending.complete(contacts);
        assertEquals(contacts, cache.contactsOrEmpty(key));
    }

    @Test
    void debugQueryCacheDoesNotSubmitDuplicateJointsWhilePending() {
        PhysicsDebugSystem.DebugQueryCache cache = new PhysicsDebugSystem.DebugQueryCache();
        PhysicsDebugSystem.DebugQueryKey key =
            PhysicsDebugSystem.DebugQueryKey.joints(new SpaceId(13), UUID.randomUUID());
        CompletableFuture<List<PhysicsDebugJointView>> pending = new CompletableFuture<>();
        AtomicInteger submissions = new AtomicInteger();
        List<PhysicsDebugJointView> joints = List.of(new PhysicsDebugJointView(1.0f,
            2.0f,
            3.0f,
            4.0f,
            5.0f,
            6.0f,
            true,
            0.0f,
            1.0f,
            0.0f));

        assertTrue(cache.requestJointsIfIdle(key, () -> {
            submissions.incrementAndGet();
            return pending;
        }));
        assertFalse(cache.requestJointsIfIdle(key, () -> {
            submissions.incrementAndGet();
            return CompletableFuture.completedFuture(List.of());
        }));

        assertEquals(1, submissions.get());
        assertTrue(cache.jointsOrEmpty(key).isEmpty());
        pending.complete(joints);
        assertEquals(joints, cache.jointsOrEmpty(key));
    }

    @Test
    void collectVisibleJointPrimitivesUsesAnchorsForDistanceAndRendering() {
        PhysicsSpace space = new FakePhysicsBackend(new BackendId("test:debug-joints"))
            .createSpace(new SpaceId(1));
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
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
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
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
