package dev.hytalemodding.impulse.core.plugin.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import java.util.Collection;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Public facade for a world's physics runtime resource.
 *
 * <p>The concrete Impulse runtime lives in the internal package. Plugin-facing code should depend
 * on this facade and route live backend access through the owner-thread methods exposed here.</p>
 */
public abstract class PhysicsWorldResource implements Resource<EntityStore> {

    protected PhysicsWorldResource() {
    }

    public abstract boolean canAccessLiveBackendDirectly();

    public abstract void assertCanAccessLiveBackendDirectly(@Nonnull String operation);

    public abstract void runOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation);

    @Nonnull
    public abstract PhysicsMutationHandle<Void> enqueuePhysicsMutation(
        @Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation);

    @Nonnull
    public abstract <T> PhysicsMutationHandle<T> enqueuePhysicsMutation(
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation);

    @Nonnull
    public abstract <T> T callOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable);

    @Nullable
    public abstract SpaceId getDefaultSpaceId();

    @Nonnull
    public abstract SpaceId requireDefaultSpaceId();

    @Nullable
    public abstract PhysicsSpace getDefaultSpace();

    @Nonnull
    public abstract PhysicsSpace requireDefaultSpace();

    public abstract void setDefaultSpaceId(@Nullable SpaceId defaultSpaceId);

    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> setDefaultSpaceIdAsync(
        @Nullable SpaceId defaultSpaceId);

    @Nonnull
    public abstract PhysicsWorldSettings getWorldSettings();

    public abstract void setWorldSettings(@Nonnull PhysicsWorldSettings settings);

    @Nonnull
    public abstract PhysicsMutationHandle<Void> setWorldSettingsAsync(
        @Nonnull PhysicsWorldSettings settings);

    @Nonnull
    public abstract PhysicsSpace createSpace(@Nonnull BackendId backendId);

    @Nonnull
    public abstract PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName);

    @Nonnull
    public abstract PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    @Nonnull
    public abstract PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> createSpaceAsync(
        @Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> createSpaceAsync(
        @Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    @Nullable
    public abstract PhysicsSpace getSpace(@Nonnull SpaceId spaceId);

    @Nonnull
    public abstract Collection<PhysicsSpace> getSpaces();

    public abstract int getSpaceCount();

    public abstract int refreshBodySnapshots();

    @Nonnull
    public abstract PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBodyId bodyId);

    @Nonnull
    public abstract PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBody body);

    public abstract int getBodySnapshotCount();

    public abstract int getBodySnapshotCount(@Nonnull SpaceId spaceId);

    public abstract int getBodySnapshotCellCount();

    @Nonnull
    public abstract WorldCollisionBuildStats rebuildWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius);

    @Nonnull
    public abstract WorldCollisionPrewarmStats ensureWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick);

    public abstract int clearWorldCollision(@Nonnull SpaceId spaceId);

    @Nonnull
    public abstract WorldCollisionStats getWorldCollisionStats();

    public abstract void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer);

    public abstract int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer);

    public abstract void removeSpace(@Nonnull SpaceId spaceId);

    public abstract void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName);

    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> removeSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull String worldName);

    public abstract void clearAllSpaces(@Nonnull String worldName);

    @Nonnull
    public abstract PhysicsMutationHandle<Void> clearAllSpacesAsync(@Nonnull String worldName);

    @Nonnull
    public abstract PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId);

    public abstract void setSpaceSettings(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings);

    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> setSpaceSettingsAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings);

    @Nonnull
    public abstract PhysicsBodyId addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nonnull
    public abstract PhysicsBodyId addBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nonnull
    public abstract PhysicsMutationHandle<PhysicsBodyId> addBodyAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nonnull
    public abstract PhysicsMutationHandle<PhysicsBodyId> addBodyAsync(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    public abstract void destroyBody(@Nonnull PhysicsBodyId bodyId);

    @Nonnull
    public abstract PhysicsMutationHandle<PhysicsBodyId> destroyBodyAsync(
        @Nonnull PhysicsBodyId bodyId);

    public abstract void destroyBody(@Nonnull PhysicsBodyId bodyId, boolean removeFromSpace);

    @Nonnull
    public abstract PhysicsMutationHandle<PhysicsBodyId> destroyBodyAsync(
        @Nonnull PhysicsBodyId bodyId,
        boolean removeFromSpace);

    public abstract void destroyBody(@Nonnull PhysicsBody body);

    @Nonnull
    public abstract PhysicsMutationHandle<Void> destroyBodyAsync(@Nonnull PhysicsBody body);

    @Nullable
    public abstract PhysicsBody getBody(@Nonnull PhysicsBodyId bodyId);

    @Nullable
    public abstract PhysicsBodyId getBodyId(@Nonnull PhysicsBody body);

    @Nullable
    public abstract PhysicsBodyRegistration getBodyRegistration(@Nonnull PhysicsBody body);

    @Nullable
    public abstract PhysicsBodyRegistration getRegistration(@Nonnull PhysicsBodyId bodyId);

    @Nullable
    public abstract PhysicsBodyRegistrationView getBodyRegistrationView(
        @Nonnull PhysicsBodyId bodyId);

    public abstract boolean isBodyCreationPending(@Nonnull PhysicsBodyId bodyId);

    @Nonnull
    public abstract PhysicsBodyRegistration requireBodyRegistration(@Nonnull PhysicsBodyId bodyId);

    @Nonnull
    public abstract Collection<PhysicsBodyRegistration> getBodyRegistrations();

    @Nonnull
    public abstract Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews();

    public abstract int getBodyRegistrationCount();

    public abstract int getBodyRegistrationCount(
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nonnull
    public abstract Collection<PhysicsBodyRegistration> getBodyRegistrations(
        @Nonnull PhysicsBodyKind kind);

    @Nonnull
    public abstract Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews(
        @Nonnull PhysicsBodyKind kind);

    @Nonnull
    public abstract Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull PhysicsBodyId bodyId);

    public abstract void markBodyControlled(@Nonnull PhysicsBodyId bodyId);

    public abstract void clearControlledBody(@Nonnull PhysicsBodyId bodyId);

    public abstract void clearBodies();

    @Nonnull
    public abstract PhysicsMutationHandle<Void> clearBodiesAsync();

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsWorldResourceType();
    }

    @Nonnull
    @Override
    public abstract PhysicsWorldResource clone();
}
