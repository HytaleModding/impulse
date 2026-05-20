package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.VisualOcclusionMode;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Materializes disposable Hytale visual followers for detached physics bodies near players.
 *
 * <p>Detached bodies stay physics-authoritative and are not persisted through these visual
 * proxies. Proxies are ordinary Hytale block entities with a generated
 * {@link PhysicsBodyAttachmentComponent}, so removing a proxy never removes the backend body.</p>
 */
public class PhysicsDetachedVisualMaterializationSystem extends TickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, HeadRotation> HEAD_ROTATION_TYPE =
        HeadRotation.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE =
        DespawnComponent.getComponentType();
    private static final ComponentType<EntityStore, Velocity> VELOCITY_TYPE =
        Velocity.getComponentType();
    private static final ComponentType<ChunkStore, WorldChunk> WORLD_CHUNK_TYPE =
        WorldChunk.getComponentType();

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    private static final float VIEW_CONE_DOT = 0.35f;
    private static final float VIEW_CONE_NEAR_RADIUS_SQUARED = 8.0f * 8.0f;
    private static final int ORPHAN_VISUAL_CLEANUP_INTERVAL_TICKS = 40;

    private int orphanVisualCleanupCooldown;

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        if (orphanVisualCleanupCooldown <= 0) {
            removeOrphanVisualFollowers(store, resource);
            orphanVisualCleanupCooldown = ORPHAN_VISUAL_CLEANUP_INTERVAL_TICKS;
        } else {
            orphanVisualCleanupCooldown--;
        }
        List<PhysicsWorldResource.VisualInterest> interests = collectVisualInterests(store, resource);
        int spawned = 0;
        int materialized = processMaterializedProxies(store, resource, interests);
        RaycastBudget raycastBudget = new RaycastBudget();
        List<MaterializationCandidate> candidates = new ArrayList<>();
        collectMaterializationCandidates(store, resource, interests, raycastBudget, candidates);

        candidates.sort(Comparator.comparingDouble(MaterializationCandidate::distanceSquared));
        for (MaterializationCandidate candidate : candidates) {
            if (spawned >= candidate.settings().getDetachedVisualMaxSpawnsPerTick()
                || materialized >= candidate.settings().getDetachedVisualMaxMaterialized()) {
                continue;
            }
            resource.setGeneratedVisualProxy(candidate.bodyId(),
                spawnProxy(store,
                    candidate.bodyId(),
                    candidate.snapshot(),
                    candidate.registration(),
                    candidate.settings()));
            spawned++;
            materialized++;
        }
    }

    @Nonnull
    private static List<PhysicsWorldResource.VisualInterest> collectVisualInterests(
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource) {
        List<PhysicsWorldResource.VisualInterest> interests = new ArrayList<>();
        for (PlayerRef playerRef : store.getExternalData().getWorld().getPlayerRefs()) {
            Ref<EntityStore> playerEntity = playerRef.getReference();
            if (playerEntity == null || !playerEntity.isValid()) {
                continue;
            }

            TransformComponent transform = store.getComponent(playerEntity, TRANSFORM_TYPE);
            if (transform == null) {
                continue;
            }

            Vector3d position = transform.getPosition();
            interests.add(new PhysicsWorldResource.VisualInterest(
                new Vector3f((float) position.x, (float) position.y, (float) position.z),
                playerLookDirection(store, playerEntity, transform)));
        }
        interests.addAll(resource.getSyntheticVisualInterests());
        return interests;
    }

    @Nonnull
    private static Vector3f playerLookDirection(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = store.getComponent(playerEntity, HEAD_ROTATION_TYPE);
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : transform.getRotation();
        Vector3d direction = new Vector3d(Vector3dUtil.FORWARD);
        rotation.getQuaternion(new Quaterniond()).transform(direction);
        if (direction.lengthSquared() == 0.0) {
            direction.set(Vector3dUtil.FORWARD);
        } else {
            direction.normalize();
        }
        return new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
    }

    private static int processMaterializedProxies(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        int count = 0;
        for (PhysicsBodyId bodyId : resource.getGeneratedVisualProxyBodyIds()) {
            PhysicsWorldResource.BodyRegistration registration = resource.getRegistration(bodyId);
            if (registration == null || registration.kind() != PhysicsBodyKind.BODY) {
                removeProxy(store, resource, bodyId);
                continue;
            }
            Ref<EntityStore> proxy = resource.getGeneratedVisualProxy(bodyId);
            if (proxy == null || !isExpectedProxy(store, proxy, bodyId, registration.spaceId())) {
                removeProxy(store, resource, bodyId);
                continue;
            }
            if (hasGameplayAttachment(store, resource, bodyId, proxy)) {
                removeProxy(store, resource, bodyId);
                continue;
            }

            PhysicsSpaceSettings settings = resolveSettings(resource, registration);
            if (settings == null || !settings.isDetachedVisualMaterializationEnabled()) {
                removeProxy(store, resource, bodyId);
                continue;
            }

            PhysicsBodySnapshot snapshot = resource.getBodySnapshot(bodyId);
            if (!isBodyChunkLoaded(store, snapshot)
                || shouldDematerialize(snapshot, settings, interests)) {
                removeProxy(store, resource, bodyId);
                continue;
            }
            count++;
        }
        return count;
    }

    private static void collectMaterializationCandidates(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        @Nonnull RaycastBudget raycastBudget,
        @Nonnull List<MaterializationCandidate> candidates) {
        if (interests.isEmpty()) {
            return;
        }

        Set<PhysicsBodyId> seenBodies = new ObjectOpenHashSet<>();
        for (PhysicsSpace space : resource.iterateSpaces()) {
            PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
            if (!settings.isDetachedVisualMaterializationEnabled()) {
                continue;
            }

            for (PhysicsWorldResource.VisualInterest interest : interests) {
                resource.forEachBodySnapshotNear(space.getId(),
                    interest.position(),
                    settings.getDetachedVisualMaterializationRadius(),
                    entry -> {
                        PhysicsBodySnapshot snapshot = entry.snapshot();
                        PhysicsBodyId bodyId = entry.bodyId();
                        if (!seenBodies.add(bodyId) || resource.getGeneratedVisualProxy(bodyId) != null) {
                            return;
                        }
                        PhysicsWorldResource.BodyRegistration registration =
                            resolveBodyRegistration(resource, entry);
                        if (registration == null) {
                            return;
                        }
                        if (!isBodyChunkLoaded(store, snapshot)) {
                            return;
                        }
                        PhysicsSpaceSettings registrationSettings = resolveSettings(resource, registration);
                        if (registrationSettings == null
                            || !registrationSettings.isDetachedVisualMaterializationEnabled()) {
                            return;
                        }

                        PhysicsSpace resolvedSpace = resolveSpace(resource, registration);
                        InterestResult materializeInterest = resolveVisualInterest(resource,
                            bodyId,
                            resolvedSpace,
                            snapshot,
                            registrationSettings,
                            interests,
                            registrationSettings.getDetachedVisualMaterializationRadius(),
                            raycastBudget);
                        if (materializeInterest.shouldMaterialize()) {
                            candidates.add(new MaterializationCandidate(bodyId,
                                snapshot,
                                registration,
                                registrationSettings,
                                materializeInterest.priorityDistanceSquared()));
                        }
                    });
            }
        }
    }

    private static void removeOrphanVisualFollowers(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource) {
        store.forEachEntityParallel(ATTACHMENT_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                PhysicsBodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    ATTACHMENT_TYPE);
                if (attachment == null
                    || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY
                    || hasLiveVisualTarget(resource, attachment)) {
                    return;
                }

                var ref = archetypeChunk.getReferenceTo(index);
                resource.clearGeneratedVisualProxy(attachment.getBodyId());
                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            });
    }

    private static boolean hasLiveVisualTarget(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyAttachmentComponent attachment) {
        PhysicsWorldResource.BodyRegistration registration = resource.getRegistration(attachment.getBodyId());
        if (registration == null || !sameSpaceId(registration.spaceId(), attachment.getSpaceId())) {
            return false;
        }
        return registration.spaceId() == null || resource.getSpace(registration.spaceId()) != null;
    }

    @Nullable
    private static PhysicsWorldResource.BodyRegistration resolveBodyRegistration(
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsWorldResource.BodySnapshotEntry entry) {
        PhysicsWorldResource.BodyRegistration registration = entry.registration();
        if (registration == null
            || registration.body() != entry.snapshot().body()
            || !registration.id().equals(entry.bodyId())) {
            registration = resource.getRegistration(entry.bodyId());
        }
        if (registration == null
            || registration.kind() != PhysicsBodyKind.BODY
            || !resource.getBodyAttachments(registration.id()).isEmpty()) {
            return null;
        }
        SpaceId registrationSpaceId = registration.spaceId();
        if (registrationSpaceId != null && !registrationSpaceId.equals(entry.spaceId())) {
            return null;
        }
        return registration;
    }

    private static boolean hasGameplayAttachment(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> proxy) {
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyId)) {
            if (attachmentRef == proxy || attachmentRef.equals(proxy)) {
                continue;
            }
            PhysicsBodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                ATTACHMENT_TYPE);
            if (attachment != null && attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static PhysicsSpaceSettings resolveSettings(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsWorldResource.BodyRegistration registration) {
        if (registration.spaceId() != null && resource.getSpace(registration.spaceId()) != null) {
            return resource.getSpaceSettings(registration.spaceId());
        }
        if (resource.getDefaultSpaceId() != null) {
            return resource.getSpaceSettings(resource.getDefaultSpaceId());
        }
        return null;
    }

    @Nullable
    private static PhysicsSpace resolveSpace(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsWorldResource.BodyRegistration registration) {
        if (registration.spaceId() != null) {
            PhysicsSpace space = resource.getSpace(registration.spaceId());
            if (space != null) {
                return space;
            }
        }
        return resource.getDefaultSpace();
    }

    private static boolean shouldMaterialize(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        if (interests.isEmpty()) {
            return false;
        }
        return visibleDistanceSquared(snapshot,
            settings,
            interests,
            settings.getDetachedVisualMaterializationRadius()) != Float.POSITIVE_INFINITY;
    }

    private static boolean shouldDematerialize(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        if (interests.isEmpty()) {
            return true;
        }
        return visibleDistanceSquared(snapshot,
            settings,
            interests,
            settings.getDetachedVisualDematerializationRadius()) == Float.POSITIVE_INFINITY;
    }

    private static float visibleDistanceSquared(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        Vector3f bodyPosition = snapshot.position();
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        for (PhysicsWorldResource.VisualInterest interest : interests) {
            float dx = bodyPosition.x - interest.position().x;
            float dy = bodyPosition.y - interest.position().y;
            float dz = bodyPosition.z - interest.position().z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= radiusSquared
                && isInsideViewCone(settings, interest, dx, dy, dz, distanceSquared)) {
                nearestDistanceSquared = Math.min(nearestDistanceSquared, distanceSquared);
            }
        }
        return nearestDistanceSquared;
    }

    @Nonnull
    private static InterestResult resolveVisualInterest(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyId bodyId,
        @Nullable PhysicsSpace space,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius,
        @Nonnull RaycastBudget raycastBudget) {
        InterestProbe probe = probeNearestLikelyInterest(snapshot, settings, interests, radius);
        PhysicsWorldResource.BodyVisualInterestState state =
            resource.getOrCreateBodyVisualInterestState(bodyId);
        if (!probe.inRange()) {
            state.recordInterest(Float.POSITIVE_INFINITY, false, false, false);
            return InterestResult.notVisible();
        }

        VisualOcclusionMode occlusionMode = settings.getVisualOcclusionMode();
        if (occlusionMode == VisualOcclusionMode.OFF || space == null) {
            state.recordInterest(probe.distanceSquared(), true, true, false);
            return InterestResult.visible(probe.distanceSquared(), probe.distanceSquared());
        }

        boolean raycastKnown = state.hasFreshRaycast(settings.getVisualOcclusionCacheTicks());
        boolean raycastVisible = raycastKnown && state.isRaycastVisible();
        boolean raycastEvaluated = false;
        if (!raycastKnown && raycastBudget.tryUse(settings)) {
            raycastVisible = raycastVisible(space, probe.interest(), snapshot);
            raycastKnown = true;
            raycastEvaluated = true;
        }

        state.recordInterest(probe.distanceSquared(), true, raycastVisible, raycastEvaluated);
        if (occlusionMode == VisualOcclusionMode.CULL && raycastKnown && !raycastVisible) {
            return InterestResult.notVisible();
        }

        float priorityDistanceSquared = probe.distanceSquared();
        if (occlusionMode == VisualOcclusionMode.PRIORITY && raycastKnown) {
            priorityDistanceSquared = raycastVisible
                ? probe.distanceSquared() * 0.25f
                : probe.distanceSquared() + radius * radius;
        }
        return InterestResult.visible(probe.distanceSquared(), priorityDistanceSquared);
    }

    @Nonnull
    private static InterestProbe probeNearestLikelyInterest(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        Vector3f bodyPosition = snapshot.position();
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        PhysicsWorldResource.VisualInterest nearestInterest = null;
        for (PhysicsWorldResource.VisualInterest interest : interests) {
            float dx = bodyPosition.x - interest.position().x;
            float dy = bodyPosition.y - interest.position().y;
            float dz = bodyPosition.z - interest.position().z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= radiusSquared
                && distanceSquared < nearestDistanceSquared
                && isInsideViewCone(settings, interest, dx, dy, dz, distanceSquared)) {
                nearestDistanceSquared = distanceSquared;
                nearestInterest = interest;
            }
        }
        return nearestInterest == null
            ? InterestProbe.notVisible()
            : new InterestProbe(nearestInterest, nearestDistanceSquared);
    }

    private static boolean raycastVisible(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsWorldResource.VisualInterest interest,
        @Nonnull PhysicsBodySnapshot snapshot) {
        Optional<PhysicsRayHit> hit = space.raycastClosest(interest.position(), snapshot.position());
        return hit.isPresent() && hit.get().body() == snapshot.body();
    }

    private static boolean isInsideViewCone(@Nonnull PhysicsSpaceSettings settings,
        @Nonnull PhysicsWorldResource.VisualInterest interest,
        float dx,
        float dy,
        float dz,
        float distanceSquared) {
        if (!settings.isVisualVisibilityCullingEnabled()
            || interest.direction() == null
            || distanceSquared <= VIEW_CONE_NEAR_RADIUS_SQUARED) {
            return true;
        }

        float length = (float) Math.sqrt(distanceSquared);
        if (length <= 0.0f) {
            return true;
        }
        Vector3f direction = interest.direction();
        float dot = (dx * direction.x + dy * direction.y + dz * direction.z) / length;
        return dot >= VIEW_CONE_DOT;
    }

    private static boolean isBodyChunkLoaded(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBodySnapshot snapshot) {
        Vector3f position = snapshot.position();
        ChunkStore chunkStore = store.getExternalData().getWorld().getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunk(
            ChunkUtil.chunkCoordinate(position.x),
            ChunkUtil.chunkCoordinate(position.z)));
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WORLD_CHUNK_TYPE);
        return worldChunk != null;
    }

    private static boolean isExpectedProxy(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> proxy,
        @Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId) {
        if (!proxy.isValid()) {
            return false;
        }
        PhysicsBodyAttachmentComponent attachment = store.getComponent(proxy, ATTACHMENT_TYPE);
        return attachment != null
            && attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY
            && attachment.getBodyId().equals(bodyId)
            && sameSpaceId(attachment.getSpaceId(), spaceId);
    }

    private static boolean sameSpaceId(@Nullable SpaceId first, @Nullable SpaceId second) {
        return first == null ? second == null : first.equals(second);
    }

    @Nonnull
    private static Ref<EntityStore> spawnProxy(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsWorldResource.BodyRegistration registration,
        @Nonnull PhysicsSpaceSettings settings) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Vector3f position = snapshot.position();
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            settings.getDetachedVisualBlockType(),
            new Vector3d(position.x,
                position.y - snapshot.centerOfMassOffsetY(),
                position.z));
        holder.removeComponent(DESPAWN_TYPE);
        holder.removeComponent(VELOCITY_TYPE);
        holder.addComponent(store.getRegistry().getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(ATTACHMENT_TYPE,
            new PhysicsBodyAttachmentComponent(bodyId,
                registration.spaceId(),
                TransformAuthority.BODY,
                AttachmentLifecycle.GENERATED_PROXY));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    private static void removeProxy(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyId bodyId) {
        Ref<EntityStore> proxy = resource.getGeneratedVisualProxy(bodyId);
        resource.clearGeneratedVisualProxy(bodyId);
        if (proxy != null && proxy.isValid()) {
            store.removeEntity(proxy, RemoveReason.REMOVE);
        }
    }

    private record MaterializationCandidate(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsWorldResource.BodyRegistration registration,
        @Nonnull PhysicsSpaceSettings settings,
        float distanceSquared) {
    }

    private record InterestProbe(@Nullable PhysicsWorldResource.VisualInterest interest,
        float distanceSquared) {

        static InterestProbe notVisible() {
            return new InterestProbe(null, Float.POSITIVE_INFINITY);
        }

        boolean inRange() {
            return interest != null;
        }
    }

    private record InterestResult(boolean shouldMaterialize,
        float distanceSquared,
        float priorityDistanceSquared) {

        static InterestResult notVisible() {
            return new InterestResult(false, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        }

        static InterestResult visible(float distanceSquared, float priorityDistanceSquared) {
            return new InterestResult(true, distanceSquared, priorityDistanceSquared);
        }
    }

    private static final class RaycastBudget {

        private int used;

        boolean tryUse(@Nonnull PhysicsSpaceSettings settings) {
            if (used >= settings.getVisualOcclusionRaycastsPerTick()) {
                return false;
            }
            used++;
            return true;
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
