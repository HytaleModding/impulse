package dev.hytalemodding.impulse.core.internal.systems.debug;

import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class PhysicsContactDebugCapture {

    private PhysicsContactDebugCapture() {
    }

    @Nonnull
    static List<PhysicsDebugRenderer.ContactDebugPrimitive> collectVisibleContactPrimitives(
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d center,
        double radius,
        int maxContacts) {
        if (maxContacts <= 0) {
            return List.of();
        }
        List<PhysicsDebugRenderer.ContactDebugPrimitive> primitives = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (PhysicsContact contact : space.getContacts()) {
            Vector3f pointOnB = contact.pointOnB();
            Vector3d point = new Vector3d(pointOnB.x, pointOnB.y, pointOnB.z);
            if (point.distanceSquared(center) <= radiusSquared) {
                Vector3f normalOnB = contact.normalOnB();
                Vector3d normal = new Vector3d(normalOnB.x, normalOnB.y, normalOnB.z).normalize();
                primitives.add(new PhysicsDebugRenderer.ContactDebugPrimitive(point, normal));
                if (primitives.size() >= maxContacts) {
                    break;
                }
            }
        }
        return List.copyOf(primitives);
    }
}
