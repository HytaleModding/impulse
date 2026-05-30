package dev.hytalemodding.impulse.core.internal.systems.visual;

import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

final class DetachedVisualGeometry {

    /*
     * Approximate visibility policy for optional visual culling. Close bodies
     * bypass the cone so players do not lose nearby proxies while turning.
     */
    private static final float VIEW_CONE_DOT = 0.35f;
    private static final float VIEW_CONE_NEAR_RADIUS_SQUARED = 8.0f * 8.0f;

    private DetachedVisualGeometry() {
    }

    static float visibleDistanceSquared(float positionX,
        float positionY,
        float positionZ,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        for (VisualInterest interest : interests) {
            float dx = positionX - interest.position().x;
            float dy = positionY - interest.position().y;
            float dz = positionZ - interest.position().z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= radiusSquared
                && isInsideViewCone(settings, interest, dx, dy, dz, distanceSquared)) {
                nearestDistanceSquared = Math.min(nearestDistanceSquared, distanceSquared);
            }
        }
        return nearestDistanceSquared;
    }

    static boolean isInsideViewCone(@Nonnull PhysicsSpaceSettings settings,
        @Nonnull VisualInterest interest,
        float dx,
        float dy,
        float dz,
        float distanceSquared) {
        if (!settings.getVisualSyncSettings().isVisualVisibilityCullingEnabled()
            || interest.direction() == null
            || distanceSquared <= VIEW_CONE_NEAR_RADIUS_SQUARED) {
            return true;
        }

        float length = (float) Math.sqrt(distanceSquared);
        if (length <= 0.0f) {
            return true;
        }
        Vector3f direction = interest.direction();
        float dot = (dx * direction.x + dy * direction.y + dz * direction.z) / length;
        return dot >= VIEW_CONE_DOT;
    }
}
