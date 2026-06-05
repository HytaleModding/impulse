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
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecipe;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQueryHandle;
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
 * this facade for explicit space lifecycle, world settings, command submission, body lifetime by key,
 * immutable snapshots, read-only registration views, public attachment/control hooks, and world
 * collision operations.</p>
 *
 * <p>No physics space is created implicitly. Consumers choose which explicit {@link SpaceId} to
 * target for each operation.</p>
 *
 * <p>This facade does not directly return live backend spaces or bodies. Gameplay code should use
 * physics simulation commands and queries. The explicit live-owner escape hatch is available inside
 * the command recipe for advanced diagnostics that cannot yet be expressed as copied recorder
 * operations.</p>
 */
public abstract class PhysicsWorldResource implements Resource<EntityStore> {

    protected PhysicsWorldResource() {
    }

    /**
     * Records copied simulation intent through the fluent command DSL and submits it.
     *
     * <p>The returned handle completes when the physics owner executes the batch. Snapshot
     * publication and ECS visual synchronization are separate phases and can lag behind command
     * completion.</p>
     */
    @Nonnull
    public abstract PhysicsCommandHandle submitCommands(long submittedServerTick,
        @Nonnull PhysicsCommandRecipe recipe);

    /**
     * Records copied simulation intent through the fluent command DSL with a capacity hint.
     *
     * <p>{@code expectedOperations} sizes the internal recorder arrays. It is a performance hint,
     * not a correctness requirement.</p>
     */
    @Nonnull
    public abstract PhysicsCommandHandle submitCommands(long submittedServerTick,
        int expectedOperations,
        @Nonnull PhysicsCommandRecipe recipe);

    /**
     * Runs a copied owner-lane physics query without exposing live backend handles.
     *
     * <p>Queries should return immutable or defensively copied values. Use commands for mutations
     * so all writes stay ordered through the physics owner.</p>
     */
    @Nonnull
    public abstract <R> PhysicsQueryHandle<R> query(@Nonnull PhysicsQuery<R> query);

    /**
     * Returns the latest value-only physics owner event frame.
     *
     * <p>Event frames describe owner-lane outcomes. They do not expose live
     * backend handles and do not imply that command completion has been
     * included in a captured or reader-applied body snapshot.</p>
     */
    @Nonnull
    public abstract PhysicsEventFrame getLatestEventFrame();

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
     * Applies world-level simulation settings on the physics owner lane.
     */
    public abstract void setWorldSettings(@Nonnull PhysicsWorldSettings settings);

    /**
     * Queues a world-level simulation settings update.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<Void> setWorldSettingsAsync(
        @Nonnull PhysicsWorldSettings settings);

    /**
     * Creates a physics space using default settings and returns its id.
     *
     * <p>Creation is serialized through this world's logical physics owner lane. Callers must not
     * infer a stable Java thread identity from the synchronous return path.</p>
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId);

    /**
     * Creates a physics space for logging under the supplied world name and returns its id.
     *
     * <p>No default space is created implicitly; the returned id is the explicit space handle for
     * later world-resource operations.</p>
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName);

    /**
     * Creates a physics space with generated logical id and supplied settings.
     *
     * <p>The live backend space is created inside the serialized owner lane. Use the async variant
     * when the caller should not block on owner-lane execution.</p>
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Creates a physics space with an explicit logical id and supplied settings.
     *
     * <p>The explicit id is reserved by the caller, but live backend creation still runs inside the
     * serialized owner lane.</p>
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Queues physics-space creation and returns the reserved generated space id.
     *
     * <p>The returned mutation handle completes when the owner lane creates the live backend
     * space, not when a later snapshot or ECS reader has consumed any resulting state.</p>
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> createSpaceAsync(
        @Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Queues physics-space creation and returns the requested explicit space id.
     *
     * <p>Different worlds may queue work concurrently, but this world's spaces remain serialized by
     * its owner lane.</p>
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> createSpaceAsync(
        @Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings);

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
    public abstract PhysicsBodySnapshot getBodySnapshot(@Nonnull RigidBodyKey bodyKey);

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
     * Rebuilds cached world-collision sections around a center for the requested space without
     * clearing retained terrain outside that radius.
     */
    @Nonnull
    public abstract WorldCollisionBuildStats refreshWorldCollisionAround(@Nonnull World world,
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
     * Applies settings to a registered physics space on the physics owner lane.
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
     * Destroys a registered body by stable key and removes it from its physics space.
     */
    public abstract void destroyBody(@Nonnull RigidBodyKey bodyKey);

    /**
     * Queues destruction of a registered body by stable key.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<RigidBodyKey> destroyBodyAsync(
        @Nonnull RigidBodyKey bodyKey);

    /**
     * Returns immutable registration metadata for a body key.
     */
    @Nullable
    public abstract PhysicsBodyRegistrationView getBodyRegistrationView(
        @Nonnull RigidBodyKey bodyKey);

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
     * Returns ECS attachments associated with a registered body key.
     */
    @Nonnull
    public abstract Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull RigidBodyKey bodyKey);

    /**
     * Returns whether a registered body has one or more ECS attachments without materializing the
     * attachment collection.
     */
    public abstract boolean hasBodyAttachments(@Nonnull RigidBodyKey bodyKey);

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
