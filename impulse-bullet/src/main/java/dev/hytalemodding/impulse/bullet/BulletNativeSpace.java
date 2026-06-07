package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.StepFlag;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PersistentManifolds;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

final class BulletNativeSpace extends PhysicsSpace {

    private static final int CONTACT_EVENT_FLAGS = StepFlag.contactConceived
        | StepFlag.contactStarted
        | StepFlag.contactProcessed
        | StepFlag.contactEnded;
    private static final int MAX_CONTACT_EVENTS = 16_384;
    private static final Vector3f ZERO_VECTOR = new Vector3f();

    private final ArrayDeque<BulletNativeContactEvent> contactEvents = new ArrayDeque<>();
    private final Map<Long, BulletNativeContactEvent> activeManifoldEvents = new HashMap<>();

    BulletNativeSpace(@Nonnull BroadphaseType broadphaseType) {
        super(broadphaseType);
    }

    void updateWithContactEvents(float timeInterval, int maxSteps) {
        contactEvents.clear();
        update(timeInterval, maxSteps, CONTACT_EVENT_FLAGS);
    }

    @Nonnull
    List<BulletNativeContactEvent> drainContactEvents() {
        if (contactEvents.isEmpty()) {
            return List.of();
        }
        List<BulletNativeContactEvent> drained = new ArrayList<>(contactEvents);
        contactEvents.clear();
        return drained;
    }

    void forgetBody(long bodyId) {
        Iterator<Map.Entry<Long, BulletNativeContactEvent>> active = activeManifoldEvents.entrySet().iterator();
        while (active.hasNext()) {
            BulletNativeContactEvent event = active.next().getValue();
            if (event.bodyAId() == bodyId || event.bodyBId() == bodyId) {
                active.remove();
            }
        }
        contactEvents.removeIf(event -> event.bodyAId() == bodyId || event.bodyBId() == bodyId);
    }

    @Override
    public boolean onContactConceived(long pointId,
        long manifoldId,
        PhysicsCollisionObject pcoA,
        PhysicsCollisionObject pcoB) {
        boolean accepted = super.onContactConceived(pointId, manifoldId, pcoA, pcoB);
        if (accepted) {
            BulletNativeContactEvent event = eventFromPoint(PhysicsContactPhase.STARTED,
                pcoA.nativeId(),
                pcoB.nativeId(),
                pointId);
            activeManifoldEvents.put(manifoldId, event);
        }
        return accepted;
    }

    @Override
    public void onContactStarted(long manifoldId) {
        super.onContactStarted(manifoldId);
        BulletNativeContactEvent event = activeManifoldEvents.get(manifoldId);
        if (event == null) {
            event = eventFromManifold(PhysicsContactPhase.STARTED, manifoldId);
            if (event != null) {
                activeManifoldEvents.put(manifoldId, event);
            }
        }
        if (event != null) {
            addContactEvent(event.withPhase(PhysicsContactPhase.STARTED));
        }
    }

    @Override
    public void onContactProcessed(PhysicsCollisionObject pcoA,
        PhysicsCollisionObject pcoB,
        long pointId) {
        super.onContactProcessed(pcoA, pcoB, pointId);
        addContactEvent(eventFromPoint(PhysicsContactPhase.PERSISTED,
            pcoA.nativeId(),
            pcoB.nativeId(),
            pointId));
    }

    @Override
    public void onContactEnded(long manifoldId) {
        super.onContactEnded(manifoldId);
        BulletNativeContactEvent event = activeManifoldEvents.remove(manifoldId);
        if (event != null) {
            addContactEvent(event.withPhase(PhysicsContactPhase.ENDED));
        }
    }

    private void addContactEvent(@Nonnull BulletNativeContactEvent event) {
        if (contactEvents.size() < MAX_CONTACT_EVENTS) {
            contactEvents.add(event);
        }
    }

    @Nullable
    private static BulletNativeContactEvent eventFromManifold(@Nonnull PhysicsContactPhase phase,
        long manifoldId) {
        long bodyAId = PersistentManifolds.getBodyAId(manifoldId);
        long bodyBId = PersistentManifolds.getBodyBId(manifoldId);
        int pointCount = PersistentManifolds.countPoints(manifoldId);
        if (pointCount <= 0) {
            return new BulletNativeContactEvent(phase,
                bodyAId,
                bodyBId,
                ZERO_VECTOR,
                ZERO_VECTOR,
                ZERO_VECTOR,
                0.0f,
                0.0f);
        }
        long pointId = PersistentManifolds.getPointId(manifoldId, 0);
        return eventFromPoint(phase, bodyAId, bodyBId, pointId);
    }

    @Nonnull
    private static BulletNativeContactEvent eventFromPoint(@Nonnull PhysicsContactPhase phase,
        long bodyAId,
        long bodyBId,
        long pointId) {
        return new BulletNativeContactEvent(phase,
            bodyAId,
            bodyBId,
            fromJmeVector(ManifoldPoints::getPositionWorldOnA, pointId),
            fromJmeVector(ManifoldPoints::getPositionWorldOnB, pointId),
            fromJmeVector(ManifoldPoints::getNormalWorldOnB, pointId),
            ManifoldPoints.getDistance1(pointId),
            ManifoldPoints.getAppliedImpulse(pointId));
    }

    @Nonnull
    private static Vector3f fromJmeVector(@Nonnull ManifoldVectorReader reader, long pointId) {
        com.jme3.math.Vector3f out = new com.jme3.math.Vector3f();
        reader.read(pointId, out);
        return new Vector3f(out.x, out.y, out.z);
    }

    @FunctionalInterface
    private interface ManifoldVectorReader {
        void read(long pointId, com.jme3.math.Vector3f out);
    }
}
