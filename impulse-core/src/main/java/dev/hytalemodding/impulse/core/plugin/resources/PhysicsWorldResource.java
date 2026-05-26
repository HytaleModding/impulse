package dev.hytalemodding.impulse.core.plugin.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerScopedCallable;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerScopedMutation;
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
 * Public alpha facade for a world's physics runtime resource.
 *
 * <p>The concrete Impulse runtime lives in the internal package. Plugin-facing code should depend on
 * this facade for explicit space lifecycle, world settings, owner-thread routing, body lifetime by
 * id, immutable snapshots, read-only registration views, public attachment/control hooks, and world
 * collision operations.</p>
 *
 * <p>No physics space is created implicitly. A default space only exists after the consumer creates
 * one with {@code makeDefault=true} or calls {@link #setDefaultSpaceId(SpaceId)}.</p>
 *
 * <p>This facade does not directly return live backend spaces or bodies. Code that genuinely needs
 * live backend access must use a scoped owner callback, such as
 * {@link #runOnPhysicsOwner(String, PhysicsOwnerScopedMutation)} or
 * {@link #callOnPhysicsOwner(String, PhysicsOwnerScopedCallable)}, and resolve live objects from
 * the callback parameter.</p>
 */
public abstract class PhysicsWorldResource implements Resource<EntityStore> {

    protected PhysicsWorldResource() {
    }

    /**
     * Runs a live-backend mutation on the current physics owner.
     *
     * <p>When a world worker is attached, this submits to the physics worker and blocks until the
     * mutation completes. If no worker is attached, or the caller is already on the worker thread,
     * this runs immediately. Use the scoped overload when the operation needs to resolve live
     * backend objects. The callback must not assume it owns unrelated Hytale ECS state.</p>
     */
    public abstract void runOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation);

    /**
     * Runs a live-backend mutation on the current physics owner with scoped live-object access.
     */
    public abstract void runOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerScopedMutation mutation);

    /**
     * Queues a live-backend mutation on the current physics owner without blocking the caller.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<Void> enqueuePhysicsMutation(
        @Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation);

    /**
     * Queues a live-backend mutation with scoped live-object access.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<Void> enqueuePhysicsMutation(
        @Nonnull String operation,
        @Nonnull PhysicsOwnerScopedMutation mutation);

    /**
     * Queues a live-backend mutation and returns a handle that is already associated with a value.
     *
     * <p>The {@code value} parameter lets callers reserve and expose a logical id before the queued
     * mutation runs.</p>
     */
    @Nonnull
    public abstract <T> PhysicsMutationHandle<T> enqueuePhysicsMutation(
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation);

    /**
     * Queues a live-backend mutation with scoped live-object access and a reserved value.
     */
    @Nonnull
    public abstract <T> PhysicsMutationHandle<T> enqueuePhysicsMutation(
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerScopedMutation mutation);

    /**
     * Runs a live-backend read or write operation on the current physics owner and returns its value.
     *
     * <p>Prefer published snapshots for ordinary gameplay reads. Use this for explicit live-backend
     * operations that cannot be expressed through snapshots or higher-level resource methods. Use
     * the scoped overload when the operation needs to resolve live backend objects.</p>
     */
    @Nonnull
    public abstract <T> T callOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable);

    /**
     * Runs a live-backend read or write operation with scoped live-object access.
     */
    @Nonnull
    public abstract <T> T callOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerScopedCallable<T> callable);

    /**
     * Returns the configured default space id, or {@code null} when this world has no default.
     */
    @Nullable
    public abstract SpaceId getDefaultSpaceId();

    /**
     * Returns the configured default space id.
     *
     * @throws IllegalStateException when this world has no default space
     */
    @Nonnull
    public abstract SpaceId requireDefaultSpaceId();

    /**
     * Sets or clears the default space id on the physics owner thread.
     */
    public abstract void setDefaultSpaceId(@Nullable SpaceId defaultSpaceId);

    /**
     * Queues a default-space update and returns the requested default id.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> setDefaultSpaceIdAsync(
        @Nullable SpaceId defaultSpaceId);

    /**
     * Returns a defensive copy of the world-level simulation settings.
     *
     * <p>Changing the returned copy has no effect until it is passed to
     * {@link #setWorldSettings(PhysicsWorldSettings)} or
     * {@link #setWorldSettingsAsync(PhysicsWorldSettings)}.</p>
     */
    @Nonnull
    public abstract PhysicsWorldSettings getWorldSettings();

    /**
     * Applies world-level simulation settings on the physics owner thread.
     */
    public abstract void setWorldSettings(@Nonnull PhysicsWorldSettings settings);

    /**
     * Queues a world-level simulation settings update.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<Void> setWorldSettingsAsync(
        @Nonnull PhysicsWorldSettings settings);

    /**
     * Creates a non-default physics space using default space settings and returns its id.
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId);

    /**
     * Creates a non-default physics space for logging under the supplied world name and returns its id.
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName);

    /**
     * Creates a physics space with generated logical id, supplied settings, and optional default flag.
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    /**
     * Creates a physics space with an explicit logical id, supplied settings, and optional default flag.
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    /**
     * Queues physics-space creation and returns the reserved generated space id.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> createSpaceAsync(
        @Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    /**
     * Queues physics-space creation and returns the requested explicit space id.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> createSpaceAsync(
        @Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault);

    /**
     * Returns whether a physics space id is currently registered.
     */
    public abstract boolean hasSpace(@Nonnull SpaceId spaceId);

    /**
     * Returns a snapshot collection of registered physics space ids.
     */
    @Nonnull
    public abstract Collection<SpaceId> getSpaceIds();

    /**
     * Returns the number of registered physics spaces.
     */
    public abstract int getSpaceCount();

    /**
     * Captures and publishes body snapshots from the live backend state.
     *
     * @return number of published body snapshots
     */
    public abstract int refreshBodySnapshots();

    /**
     * Returns the latest published snapshot for a body, capturing a fresh snapshot on the physics
     * owner if the body is registered but missing from the published frame.
     */
    @Nonnull
    public abstract PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBodyId bodyId);

    /**
     * Returns the number of body snapshots in the latest published frame.
     */
    public abstract int getBodySnapshotCount();

    /**
     * Returns the number of body snapshots in the latest published frame for one space.
     */
    public abstract int getBodySnapshotCount(@Nonnull SpaceId spaceId);

    /**
     * Returns the number of occupied snapshot broad-phase cells.
     */
    public abstract int getBodySnapshotCellCount();

    /**
     * Rebuilds world collision around a center for the requested space.
     */
    @Nonnull
    public abstract WorldCollisionBuildStats rebuildWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius);

    /**
     * Ensures world collision exists around one or more centers for the requested space.
     */
    @Nonnull
    public abstract WorldCollisionPrewarmStats ensureWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick);

    /**
     * Clears cached world collision for the requested space.
     */
    public abstract int clearWorldCollision(@Nonnull SpaceId spaceId);

    /**
     * Returns world-collision runtime statistics.
     */
    @Nonnull
    public abstract WorldCollisionStats getWorldCollisionStats();

    /**
     * Iterates published body snapshots for one space.
     */
    public abstract void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer);

    /**
     * Iterates published body snapshots near a point.
     *
     * @return number of matching snapshot entries
     */
    public abstract int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer);

    /**
     * Removes a physics space and destroys its registered bodies.
     */
    public abstract void removeSpace(@Nonnull SpaceId spaceId);

    /**
     * Removes a physics space and destroys its registered bodies, using the world name for logging.
     */
    public abstract void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName);

    /**
     * Queues physics-space removal and returns the removed space id.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> removeSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull String worldName);

    /**
     * Removes all physics spaces and destroys their registered bodies.
     */
    public abstract void clearAllSpaces(@Nonnull String worldName);

    /**
     * Queues removal of all physics spaces.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<Void> clearAllSpacesAsync(@Nonnull String worldName);

    /**
     * Returns the current settings for a registered physics space.
     */
    @Nonnull
    public abstract PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId);

    /**
     * Applies settings to a registered physics space on the physics owner thread.
     */
    public abstract void setSpaceSettings(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Queues settings replacement for a registered physics space.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> setSpaceSettingsAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Destroys a registered body by stable id and removes it from its physics space.
     */
    public abstract void destroyBody(@Nonnull PhysicsBodyId bodyId);

    /**
     * Queues destruction of a registered body by stable id.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<PhysicsBodyId> destroyBodyAsync(
        @Nonnull PhysicsBodyId bodyId);

    /**
     * Returns immutable registration metadata for a body id.
     */
    @Nullable
    public abstract PhysicsBodyRegistrationView getBodyRegistrationView(
        @Nonnull PhysicsBodyId bodyId);

    /**
     * Returns immutable registration metadata for every registered body.
     */
    @Nonnull
    public abstract Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews();

    /**
     * Returns the number of registered bodies.
     */
    public abstract int getBodyRegistrationCount();

    /**
     * Returns the number of registered bodies with a persistence mode.
     */
    public abstract int getBodyRegistrationCount(
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    /**
     * Returns immutable registration metadata for bodies of a kind.
     */
    @Nonnull
    public abstract Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews(
        @Nonnull PhysicsBodyKind kind);

    /**
     * Returns ECS attachments associated with a registered body id.
     */
    @Nonnull
    public abstract Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull PhysicsBodyId bodyId);

    /**
     * Marks a body as externally controlled so runtime systems avoid fighting the controller.
     */
    public abstract void markBodyControlled(@Nonnull PhysicsBodyId bodyId);

    /**
     * Clears external-control state for a body.
     */
    public abstract void clearControlledBody(@Nonnull PhysicsBodyId bodyId);

    /**
     * Destroys all registered bodies while preserving registered physics spaces.
     */
    public abstract void clearBodies();

    /**
     * Queues destruction of all registered bodies while preserving registered physics spaces.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<Void> clearBodiesAsync();

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsWorldResourceType();
    }

    @Nonnull
    @Override
    public abstract PhysicsWorldResource clone();
}
