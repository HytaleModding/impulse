package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Public value diagnostics for the EntityStore side of Impulse physics.
 */
public final class PhysicsEntityDiagnostics {

    private PhysicsEntityDiagnostics() {
    }

    @Nonnull
    public static Snapshot collect(@Nonnull Store<EntityStore> store) {
        PhysicsEntityDiagnostics.Snapshot snapshot = PhysicsEntityDiagnostics.collect(store);
        return new Snapshot(snapshot.physicsBodyEntities(),
            snapshot.persistentPhysicsBodyEntities(),
            snapshot.physicsVisualEntities(),
            snapshot.transformEntities(),
            snapshot.networkIdEntities(),
            snapshot.visibleEntities(),
            snapshot.entityViewers(),
            snapshot.physicsBodyWithTransform(),
            snapshot.physicsBodyWithNetworkId(),
            snapshot.physicsBodyWithVisible(),
            snapshot.physicsBodyMaterialized(),
            snapshot.physicsVisualMaterialized());
    }

    public record Snapshot(int physicsBodyEntities,
        int persistentPhysicsBodyEntities,
        int physicsVisualEntities,
        int transformEntities,
        int networkIdEntities,
        int visibleEntities,
        int entityViewers,
        int physicsBodyWithTransform,
        int physicsBodyWithNetworkId,
        int physicsBodyWithVisible,
        int physicsBodyMaterialized,
        int physicsVisualMaterialized) {

        @Nonnull
        public String hytaleSummary() {
            return "transformEntities=" + transformEntities
                + " networkIdEntities=" + networkIdEntities
                + " visibleEntities=" + visibleEntities
                + " entityViewers=" + entityViewers;
        }

        @Nonnull
        public String impulseSummary() {
            return "physicsBodies=" + physicsBodyEntities
                + " persistentBodies=" + persistentPhysicsBodyEntities
                + " visualFollowers=" + physicsVisualEntities
                + " bodyTransform=" + physicsBodyWithTransform
                + " bodyNetworkId=" + physicsBodyWithNetworkId
                + " bodyVisible=" + physicsBodyWithVisible
                + " bodyMaterialized=" + physicsBodyMaterialized
                + " visualMaterialized=" + physicsVisualMaterialized;
        }
    }
}
