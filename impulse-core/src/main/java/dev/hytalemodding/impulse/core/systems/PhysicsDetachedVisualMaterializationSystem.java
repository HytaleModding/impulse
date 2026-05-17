package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
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
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.components.PhysicsBodyVisualComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.VisualOcclusionMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Quaterniond;

/**
 * Materializes disposable Hytale visual followers for detached physics bodies near players.
 *
 * <p>Detached bodies stay physics-authoritative and are not persisted through these visual
 * proxies. Proxies are ordinary Hytale block entities with only a {@link PhysicsBodyVisualComponent}
 * pointing at the detached body, so removing a proxy never removes the backend body.</p>
 */
public class PhysicsDetachedVisualMaterializationSystem extends TickingSystem<EntityStore> {

    private static final float VIEW_CONE_DOT = 0.35f;
    private static final float VIEW_CONE_NEAR_RADIUS_SQUARED = 8.0f * 8.0f;

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        List<PhysicsWorldResource.VisualInterest> interests = collectVisualInterests(store, resource);
        int spawned = 0;
        int materialized = countMaterialized(resource);
        RaycastBudget raycastBudget = new RaycastBudget();
        List<MaterializationCandidate> candidates = new ArrayList<>();
        for (PhysicsBody body : resource.getDetachedBodies()) {
            PhysicsWorldResource.BodyRegistration registration = resource.getBodyRegistration(body);
            if (registration == null
                || registration.ownerKind() != PhysicsWorldResource.BodyOwnerKind.DETACHED) {
                removeProxy(store, resource, body);
                continue;
            }

            PhysicsSpaceSettings settings = resolveSettings(resource, registration);
            if (settings == null || !settings.isDetachedVisualMaterializationEnabled()) {
                removeProxy(store, resource, body);
                continue;
            }

            Ref<EntityStore> proxy = resource.getDetachedVisualProxy(body);
            if (!isBodyChunkTicking(store, body)) {
                if (proxy != null) {
                    removeProxy(store, resource, body);
                    materialized = Math.max(0, materialized - 1);
                }
                continue;
            }

            PhysicsSpace space = resolveSpace(resource, registration);
            InterestResult materializeInterest = resolveVisualInterest(resource,
                space,
                body,
                settings,
                interests,
                settings.getDetachedVisualMaterializationRadius(),
                raycastBudget);
            if (materializeInterest.shouldMaterialize()) {
                if (proxy == null) {
                    candidates.add(new MaterializationCandidate(body,
                        registration,
                        settings,
                        materializeInterest.priorityDistanceSquared()));
                }
                continue;
            }

            if (proxy != null && shouldDematerialize(body, settings, interests)) {
                removeProxy(store, resource, body);
                materialized = Math.max(0, materialized - 1);
            }
        }

        candidates.sort(Comparator.comparingDouble(MaterializationCandidate::distanceSquared));
        for (MaterializationCandidate candidate : candidates) {
            if (spawned >= candidate.settings().getDetachedVisualMaxSpawnsPerTick()
                || materialized >= candidate.settings().getDetachedVisualMaxMaterialized()) {
                break;
            }
            resource.setDetachedVisualProxy(candidate.body(),
                spawnProxy(store, candidate.body(), candidate.registration(), candidate.settings()));
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

            TransformComponent transform = store.getComponent(playerEntity,
                TransformComponent.getComponentType());
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
        HeadRotation headRotation = store.getComponent(playerEntity, HeadRotation.getComponentType());
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

    private static int countMaterialized(@Nonnull PhysicsWorldResource resource) {
        int count = 0;
        for (PhysicsBody body : resource.getDetachedBodies()) {
            if (resource.getDetachedVisualProxy(body) != null) {
                count++;
            }
        }
        return count;
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

    private static boolean shouldMaterialize(@Nonnull PhysicsBody body,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        if (interests.isEmpty()) {
            return false;
        }
        return visibleDistanceSquared(body,
            settings,
            interests,
            settings.getDetachedVisualMaterializationRadius()) != Float.POSITIVE_INFINITY;
    }

    private static boolean shouldDematerialize(@Nonnull PhysicsBody body,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        if (interests.isEmpty()) {
            return true;
        }
        return visibleDistanceSquared(body,
            settings,
            interests,
            settings.getDetachedVisualDematerializationRadius()) == Float.POSITIVE_INFINITY;
    }

    private static float visibleDistanceSquared(@Nonnull PhysicsBody body,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        Vector3f bodyPosition = body.getPosition();
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
        @Nullable PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius,
        @Nonnull RaycastBudget raycastBudget) {
        InterestProbe probe = probeNearestLikelyInterest(body, settings, interests, radius);
        PhysicsWorldResource.BodyVisualInterestState state =
            resource.getOrCreateBodyVisualInterestState(body);
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
            raycastVisible = raycastVisible(space, probe.interest(), body);
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
    private static InterestProbe probeNearestLikelyInterest(@Nonnull PhysicsBody body,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        Vector3f bodyPosition = body.getPosition();
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
        @Nonnull PhysicsBody body) {
        Optional<PhysicsRayHit> hit = space.raycastClosest(interest.position(), body.getPosition());
        return hit.isPresent() && hit.get().body() == body;
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

    private static boolean isBodyChunkTicking(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBody body) {
        Vector3f position = body.getPosition();
        ChunkStore chunkStore = store.getExternalData().getWorld().getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunk(
            ChunkUtil.chunkCoordinate(position.x),
            ChunkUtil.chunkCoordinate(position.z)));
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef,
            WorldChunk.getComponentType());
        return worldChunk != null && worldChunk.is(ChunkFlag.TICKING);
    }

    @Nonnull
    private static Ref<EntityStore> spawnProxy(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsWorldResource.BodyRegistration registration,
        @Nonnull PhysicsSpaceSettings settings) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Vector3f position = body.getPosition();
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            settings.getDetachedVisualBlockType(),
            new Vector3d(position.x,
                position.y - body.getCenterOfMassOffsetY(),
                position.z));
        holder.removeComponent(DespawnComponent.getComponentType());
        holder.addComponent(PhysicsBodyVisualComponent.getComponentType(),
            new PhysicsBodyVisualComponent(body, registration.spaceId()));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    private static void removeProxy(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBody body) {
        Ref<EntityStore> proxy = resource.getDetachedVisualProxy(body);
        resource.clearDetachedVisualProxy(body);
        if (proxy != null && proxy.isValid()) {
            store.removeEntity(proxy, RemoveReason.REMOVE);
        }
    }

    private record MaterializationCandidate(@Nonnull PhysicsBody body,
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
}
