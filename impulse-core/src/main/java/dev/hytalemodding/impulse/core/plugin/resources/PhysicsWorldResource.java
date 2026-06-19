package dev.hytalemodding.impulse.core.plugin.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Public legacy read facade for copied physics snapshots, registrations, and attachments.
 *
 * <p>The concrete Impulse runtime lives in the internal package. New authoring code should mutate
 * PhysicsStore entities and resources through {@code core.plugin.physicsstore} helpers and direct
 * {@code Store<PhysicsStore>} ECS operations.</p>
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
