package dev.hytalemodding.impulse.core.internal.resources.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal snapshot visitor for authoritative paths that can use live PhysicsStore row refs.
 */
@FunctionalInterface
public interface PhysicsBodySnapshotRefVisitor {

    void accept(@Nonnull RigidBodyKey bodyKey,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);
}
