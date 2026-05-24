package dev.hytalemodding.impulse.core.internal.systems.debug;

import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugRenderer.ContactDebugPrimitive;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

final class PhysicsContactDebugCapture {

    private PhysicsContactDebugCapture() {
    }

    @Nonnull
    static List<ContactDebugPrimitive> collectVisibleContactPrimitives(
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxContacts) {
        resource.assertCanAccessLiveBackendDirectly("capture physics debug contacts");
        int limit = Math.max(0, maxContacts);
        if (limit == 0) {
            return List.of();
        }

        List<ContactDebugPrimitive> visible = new ArrayList<>();
        double maxDistanceSquared = viewRadius * viewRadius;
        for (PhysicsContact contact : space.getContacts()) {
            if (visible.size() >= limit) {
                break;
            }

            ContactDebugPrimitive primitive = PhysicsDebugRenderer.captureContact(contact);
            if (viewerPosition.distanceSquared(primitive.point()) > maxDistanceSquared) {
                continue;
            }

            visible.add(primitive);
        }
        return visible;
    }
}
