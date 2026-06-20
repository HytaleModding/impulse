package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied store tick lane space diagnostics.
 */
public record SpaceSummary(@Nonnull SpaceId spaceId,
                           @Nonnull BackendId backendId,
                           int bodyCount,
                           int jointCount,
                           boolean runtimeStatsAvailable,
                           int runtimeBodyCount,
                           int runtimeColliderCount,
                           int runtimeActiveBodyCount,
                           int runtimeContactPairCount,
                           int runtimeContactManifoldCount,
                           int runtimeContactPointCount,
                           int runtimeDynamicDynamicContactPairCount,
                           int runtimeTerrainContactPairCount,
                           int runtimeActiveIslandCount,
                           int runtimeJointCount) {

    public SpaceSummary(@Nonnull SpaceId spaceId,
        @Nonnull BackendId backendId,
        int bodyCount,
        int jointCount) {
        this(spaceId,
            backendId,
            bodyCount,
            jointCount,
            false,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0);
    }

    public SpaceSummary {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(backendId, "backendId");
        bodyCount = Math.max(0, bodyCount);
        jointCount = Math.max(0, jointCount);
        if (!runtimeStatsAvailable) {
            runtimeBodyCount = 0;
            runtimeColliderCount = 0;
            runtimeActiveBodyCount = 0;
            runtimeContactPairCount = 0;
            runtimeContactManifoldCount = 0;
            runtimeContactPointCount = 0;
            runtimeDynamicDynamicContactPairCount = 0;
            runtimeTerrainContactPairCount = 0;
            runtimeActiveIslandCount = 0;
            runtimeJointCount = 0;
        } else {
            runtimeBodyCount = Math.max(0, runtimeBodyCount);
            runtimeColliderCount = Math.max(0, runtimeColliderCount);
            runtimeActiveBodyCount = Math.max(0, runtimeActiveBodyCount);
            runtimeContactPairCount = Math.max(0, runtimeContactPairCount);
            runtimeContactManifoldCount = Math.max(0, runtimeContactManifoldCount);
            runtimeContactPointCount = Math.max(0, runtimeContactPointCount);
            runtimeDynamicDynamicContactPairCount =
                Math.max(0, runtimeDynamicDynamicContactPairCount);
            runtimeTerrainContactPairCount = Math.max(0, runtimeTerrainContactPairCount);
            runtimeActiveIslandCount = Math.max(0, runtimeActiveIslandCount);
            runtimeJointCount = Math.max(0, runtimeJointCount);
        }
    }
}
