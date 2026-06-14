package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreRuntimeCleaner;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyRowDescriptor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodyRows;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Internal Crucible helpers for authoring and clearing live PhysicsStore rows.
 */
final class PhysicsStoreCrucibleSupport {

    private PhysicsStoreCrucibleSupport() {
    }

    @Nonnull
    static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        return ((PhysicsStoreWorld) world).getPhysicsStore().getStore();
    }

    static void clearAll(@Nonnull Store<PhysicsStore> store) {
        PhysicsStoreRuntimeCleaner.clearAll(store);
    }

    @Nonnull
    static Ref<PhysicsStore> addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsStoreThreading.requireWorldThread(store, "add Crucible PhysicsStore body row");
        BodyRowDescriptor row = PhysicsBodyRows.body(
            PhysicsStoreSpaceMutations.requireSpaceUuid(store, spaceId),
            bodyUuid,
            bodyCenter,
            shape,
            bodyType,
            mass,
            settings,
            linearVelocity,
            kind,
            persistenceMode);
        return store.addEntity(PhysicsStoreEntities.bodyHolder(store,
            row.bodyUuid(),
            row.body(),
            row.dynamics(),
            row.target(),
            row.collider(),
            row.shape(),
            row.material(),
            row.filter()), AddReason.SPAWN);
    }
}
