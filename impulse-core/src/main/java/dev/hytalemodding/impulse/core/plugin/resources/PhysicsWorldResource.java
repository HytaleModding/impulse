package dev.hytalemodding.impulse.core.plugin.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsWorlds;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Public alpha facade for a world's physics runtime resource.
 *
 * <p>The concrete Impulse runtime lives in the internal package. This facade remains for
 * compatibility body lifetime by durable UUID, immutable snapshots, read-only registration views,
 * and public attachment/control hooks. New code that already has the real PhysicsStore should use
 * {@link PhysicsWorlds} for world settings/event-frame reads and {@link PhysicsSpaces} for space
 * lifecycle and per-space settings.</p>
 *
 * <p>No physics space is created implicitly. Consumers choose which explicit {@link SpaceId} to
 * target for each operation.</p>
 *
 * <p>This facade does not directly return live backend spaces or bodies. Gameplay code should use
 * PhysicsStore entities for authoring, copied snapshots for body state, and explicit PhysicsStore
 * diagnostics/raycast helpers for store tick lane backend reads.</p>
 */
public abstract class PhysicsWorldResource implements Resource<EntityStore> {

    protected PhysicsWorldResource() {
    }

    /**
     * Returns the latest value-only physics store event frame.
     *
     * <p>Event frames describe store tick lane outcomes. They do not expose live
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
     * Applies world-level simulation settings on the store tick lane.
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
     * <p>Creation is serialized through this world's logical store tick lane. Callers must not
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
     * <p>The live backend space is created inside the serialized store tick lane. Use the async
     * variant when the caller should not block on store tick execution.</p>
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Creates a physics space with an explicit logical id and supplied settings.
     *
     * <p>The explicit id is reserved by the caller, but live backend creation still runs inside the
     * serialized store tick lane.</p>
     */
    @Nonnull
    public abstract SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Queues physics-space creation and returns the reserved generated space id.
     *
     * <p>The returned mutation handle completes when the store tick lane creates the live backend
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
     * its store tick lane.</p>
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
     * Captures and publishes body snapshots from the live backend state in the legacy runtime, or
     * returns the latest copied PhysicsStore snapshot count when authoritative PhysicsStore is
     * active.
     *
     * @return number of copied body snapshots
     */
    public abstract int refreshBodySnapshots();

    /**
     * Returns the latest published snapshot for a body.
     *
     * <p>The legacy runtime may capture a copied live snapshot from live backend state when the body
     * is registered but missing from the published frame. Authoritative PhysicsStore mode reads
     * only the copied {@code PhysicsSnapshotResource} frame and does not synchronously touch the
     * live backend.</p>
     */
    @Nonnull
    public abstract PhysicsBodySnapshot getBodySnapshot(@Nonnull UUID bodyUuid);

    /**
     * Returns the number of body snapshots in the latest published frame.
     */
    public abstract int getBodySnapshotCount();

    /**
     * Returns the number of body snapshots in the latest published frame for one space.
     */
    public abstract int getBodySnapshotCount(@Nonnull SpaceId spaceId);

    /**
     * Returns the number of occupied legacy snapshot broad-phase cells, or {@code 0} for the flat
     * authoritative PhysicsStore snapshot frame.
     */
    public abstract int getBodySnapshotCellCount();

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
     * Returns the current settings for a live PhysicsStore space entity.
     *
     * <p>Prefer this overload when command or gameplay code already resolved the target
     * space entity.</p>
     */
    @Nonnull
    public abstract PhysicsSpaceSettings getSpaceSettings(@Nonnull Ref<PhysicsStore> spaceRef);

    /**
     * Applies settings to a registered physics space on the store tick lane.
     */
    public abstract void setSpaceSettings(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Applies settings to a live PhysicsStore space entity.
     *
     * <p>Prefer this overload when command or gameplay code already resolved the target
     * space entity.</p>
     */
    public abstract void setSpaceSettings(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Queues settings replacement for a registered physics space.
     */
    @Nonnull
    public abstract PhysicsMutationHandle<SpaceId> setSpaceSettingsAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings);

    /**
     * Destroys a registered body by durable body UUID.
     *
     * <p>Prefer this overload when the caller is crossing a durable identity boundary.</p>
     */
    public abstract void destroyBody(@Nonnull UUID bodyUuid);

    @Nonnull
    public abstract PhysicsMutationHandle<UUID> destroyBodyAsync(@Nonnull UUID bodyUuid);

    /**
     * Returns immutable registration metadata for a body UUID.
     *
     * <p>Prefer this overload when the caller is crossing a durable identity boundary.</p>
     */
    @Nullable
    public abstract PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull UUID bodyUuid);

    /**
     * Returns immutable registration metadata for a live PhysicsStore body ref.
     */
    @Nullable
    public abstract PhysicsBodyRegistrationView getBodyRegistrationView(
        @Nonnull Ref<PhysicsStore> bodyRef);

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
     * Returns ECS attachments associated with a durable body UUID and optional live body ref.
     */
    @Nonnull
    public abstract Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef);

    /**
     * Returns ECS attachments associated with a live PhysicsStore body ref.
     *
     * <p>Prefer this overload when a caller already has a body entity ref, such as from a PhysicsStore
     * raycast or copied registration.</p>
     */
    @Nonnull
    public abstract Collection<Ref<EntityStore>> getBodyAttachments(
        @Nonnull Ref<PhysicsStore> bodyRef);

    /**
     * Returns whether a durable body UUID and optional live body ref have one or more ECS attachments.
     */
    public abstract boolean hasBodyAttachments(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef);

    /**
     * Returns whether a live PhysicsStore body ref has one or more ECS attachments without
     * materializing the attachment collection.
     */
    public abstract boolean hasBodyAttachments(@Nonnull Ref<PhysicsStore> bodyRef);

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType() {
        return PhysicsEntityTypes.physicsWorldResourceType();
    }

    @Nonnull
    @Override
    public abstract PhysicsWorldResource clone();
}
