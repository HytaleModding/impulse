package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Owner-scoped resolver for live backend physics objects.
 *
 * <p>Instances are passed into {@link PhysicsCommandContext#liveOwnerTransaction(String,
 * PhysicsOwnerTransaction)} callbacks and are callback-local. Runtime implementations reject use
 * after the callback returns, even if the caller retained the resolver object.</p>
 *
 * <p>This API intentionally exposes live backend objects only inside the owner-thread callback.
 * Plugin state that survives the callback should store stable {@link RigidBodyKey} and
 * {@link JointKey} values instead of backend handles.</p>
 */
public interface PhysicsOwnerAccess {

    @Nullable
    PhysicsSpace getSpace(@Nonnull SpaceId spaceId);

    @Nonnull
    PhysicsSpace requireSpace(@Nonnull SpaceId spaceId);

    @Nonnull
    Collection<PhysicsSpace> getSpaces();

    @Nullable
    PhysicsBody getBody(@Nonnull RigidBodyKey bodyKey);

    @Nonnull
    PhysicsBody requireBody(@Nonnull RigidBodyKey bodyKey);

    @Nullable
    RigidBodyKey getBodyKey(@Nonnull PhysicsBody body);

    @Nonnull
    RigidBodyKey addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nonnull
    RigidBodyKey addBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nullable
    PhysicsJoint getJoint(@Nonnull JointKey jointKey);

    @Nonnull
    PhysicsJoint requireJoint(@Nonnull JointKey jointKey);

    @Nullable
    JointKey getJointKey(@Nonnull PhysicsJoint joint);

    @Nonnull
    JointKey addJoint(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsJoint joint);

    @Nonnull
    JointKey addJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsJoint joint);
}
