package dev.hytalemodding.impulse.core.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime ownership index for backend physics bodies.
 *
 * <p>Centralizes body ownership, detached visual proxy, and visual interest
 * bookkeeping for the world physics resource.</p>
 */
final class PhysicsBodyRegistry {

    @Nonnull
    private final Consumer<Ref<EntityStore>> syncStateCleaner;
    private final Map<PhysicsBody, Ref<EntityStore>> bodyOwners = new IdentityHashMap<>();
    private final Map<PhysicsBody, PhysicsWorldResource.BodyRegistration> bodyRegistrations =
        new IdentityHashMap<>();
    private final Set<PhysicsBody> detachedBodies =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<PhysicsBody, Ref<EntityStore>> detachedVisualProxies = new IdentityHashMap<>();
    private final List<PhysicsWorldResource.VisualInterest> syntheticVisualInterests = new ArrayList<>();
    private final Map<PhysicsBody, Set<Ref<EntityStore>>> bodyVisualFollowers = new IdentityHashMap<>();
    private final Map<PhysicsBody, PhysicsWorldResource.BodyVisualInterestState> bodyVisualInterestStates =
        new IdentityHashMap<>();

    PhysicsBodyRegistry(@Nonnull Consumer<Ref<EntityStore>> syncStateCleaner) {
        this.syncStateCleaner = syncStateCleaner;
    }

    @Nonnull
    Collection<Ref<EntityStore>> getBodyOwners(@Nonnull Collection<PhysicsBody> staleBodies) {
        List<Ref<EntityStore>> owners = new ArrayList<>();
        List<PhysicsBody> stale = new ArrayList<>();
        for (Map.Entry<PhysicsBody, Ref<EntityStore>> entry : bodyOwners.entrySet()) {
            Ref<EntityStore> owner = entry.getValue();
            if (owner != null && owner.isValid()) {
                owners.add(owner);
            } else {
                stale.add(entry.getKey());
            }
        }
        for (PhysicsBody body : stale) {
            removeInvalidEntityOwner(body);
            staleBodies.add(body);
        }
        return owners;
    }

    @Nonnull
    Collection<Ref<EntityStore>> getBodyVisualFollowers(@Nonnull PhysicsBody body) {
        Set<Ref<EntityStore>> followers = bodyVisualFollowers.get(body);
        if (followers == null || followers.isEmpty()) {
            return List.of();
        }

        List<Ref<EntityStore>> liveFollowers = new ArrayList<>();
        List<Ref<EntityStore>> staleFollowers = new ArrayList<>();
        for (Ref<EntityStore> follower : followers) {
            if (follower != null && follower.isValid()) {
                liveFollowers.add(follower);
            } else {
                staleFollowers.add(follower);
            }
        }
        followers.removeAll(staleFollowers);
        for (Ref<EntityStore> staleFollower : staleFollowers) {
            if (staleFollower != null) {
                syncStateCleaner.accept(staleFollower);
            }
        }
        if (followers.isEmpty()) {
            bodyVisualFollowers.remove(body);
        }
        return liveFollowers;
    }

    void registerBodyVisualFollower(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> follower) {
        bodyVisualFollowers.computeIfAbsent(body, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
            .add(follower);
    }

    void unregisterBodyVisualFollower(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> follower) {
        Set<Ref<EntityStore>> followers = bodyVisualFollowers.get(body);
        if (followers == null) {
            return;
        }

        followers.remove(follower);
        if (followers.isEmpty()) {
            bodyVisualFollowers.remove(body);
        }
    }

    void registerEntityBody(@Nonnull PhysicsBody body,
        @Nullable SpaceId spaceId,
        @Nonnull Ref<EntityStore> owner) {
        bodyOwners.put(body, owner);
        detachedBodies.remove(body);
        bodyRegistrations.put(body, new PhysicsWorldResource.BodyRegistration(body,
            spaceId,
            PhysicsWorldResource.BodyOwnerKind.ENTITY,
            owner,
            true));
    }

    void registerDetachedBody(@Nonnull PhysicsBody body, @Nullable SpaceId spaceId) {
        Ref<EntityStore> previousOwner = bodyOwners.remove(body);
        if (previousOwner != null) {
            syncStateCleaner.accept(previousOwner);
        }
        detachedBodies.add(body);
        detachedVisualProxies.remove(body);
        bodyRegistrations.put(body, new PhysicsWorldResource.BodyRegistration(body,
            spaceId,
            PhysicsWorldResource.BodyOwnerKind.DETACHED,
            null,
            false));
    }

    boolean unregisterBodyOwner(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> owner) {
        Ref<EntityStore> current = bodyOwners.get(body);
        if (current != owner && !owner.equals(current)) {
            return false;
        }

        bodyOwners.remove(body);
        PhysicsWorldResource.BodyRegistration registration = bodyRegistrations.get(body);
        if (registration != null
            && registration.ownerKind() == PhysicsWorldResource.BodyOwnerKind.ENTITY
            && sameRef(registration.ownerRef(), owner)) {
            bodyRegistrations.remove(body);
        }
        clearBodyRuntimeState(body);
        return true;
    }

    @Nullable
    PhysicsWorldResource.BodyRegistration unregisterBody(@Nonnull PhysicsBody body) {
        PhysicsWorldResource.BodyRegistration registration = bodyRegistrations.remove(body);
        detachedBodies.remove(body);
        Ref<EntityStore> owner = bodyOwners.remove(body);
        if (owner != null) {
            syncStateCleaner.accept(owner);
        }
        clearBodyRuntimeState(body);
        return registration;
    }

    @Nullable
    PhysicsWorldResource.BodyRegistration getBodyRegistration(@Nonnull PhysicsBody body) {
        return bodyRegistrations.get(body);
    }

    void removeInvalidEntityOwner(@Nonnull PhysicsBody body) {
        Ref<EntityStore> owner = bodyOwners.remove(body);
        if (owner != null) {
            syncStateCleaner.accept(owner);
        }
        PhysicsWorldResource.BodyRegistration registration = bodyRegistrations.get(body);
        if (registration != null && registration.ownerKind() == PhysicsWorldResource.BodyOwnerKind.ENTITY) {
            bodyRegistrations.remove(body);
        }
        clearBodyRuntimeState(body);
    }

    @Nonnull
    Collection<PhysicsBody> getDetachedBodies() {
        return new ArrayList<>(detachedBodies);
    }

    @Nullable
    Ref<EntityStore> getDetachedVisualProxy(@Nonnull PhysicsBody body) {
        Ref<EntityStore> proxy = detachedVisualProxies.get(body);
        if (proxy != null && !proxy.isValid()) {
            detachedVisualProxies.remove(body);
            syncStateCleaner.accept(proxy);
            return null;
        }
        return proxy;
    }

    void setDetachedVisualProxy(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> proxy) {
        detachedVisualProxies.put(body, proxy);
    }

    void clearDetachedVisualProxy(@Nonnull PhysicsBody body) {
        Ref<EntityStore> proxy = detachedVisualProxies.remove(body);
        if (proxy != null) {
            syncStateCleaner.accept(proxy);
        }
    }

    void setSyntheticVisualInterests(@Nonnull Collection<PhysicsWorldResource.VisualInterest> interests) {
        syntheticVisualInterests.clear();
        syntheticVisualInterests.addAll(interests);
    }

    @Nonnull
    List<PhysicsWorldResource.VisualInterest> getSyntheticVisualInterests() {
        return new ArrayList<>(syntheticVisualInterests);
    }

    void clearSyntheticVisualInterests() {
        syntheticVisualInterests.clear();
    }

    @Nonnull
    PhysicsWorldResource.BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull PhysicsBody body) {
        return bodyVisualInterestStates.computeIfAbsent(body,
            ignored -> new PhysicsWorldResource.BodyVisualInterestState());
    }

    @Nullable
    PhysicsWorldResource.BodyVisualInterestState getBodyVisualInterestState(@Nonnull PhysicsBody body) {
        return bodyVisualInterestStates.get(body);
    }

    @Nullable
    Ref<EntityStore> getBodyOwner(@Nonnull PhysicsBody body) {
        return bodyOwners.get(body);
    }

    void clearBodyRuntimeState(@Nonnull PhysicsBody body) {
        bodyVisualFollowers.remove(body);
        bodyVisualInterestStates.remove(body);
        detachedVisualProxies.remove(body);
    }

    void clear() {
        bodyOwners.clear();
        bodyRegistrations.clear();
        detachedBodies.clear();
        detachedVisualProxies.clear();
        syntheticVisualInterests.clear();
        bodyVisualFollowers.clear();
        bodyVisualInterestStates.clear();
    }

    private static boolean sameRef(@Nullable Ref<EntityStore> left, @Nonnull Ref<EntityStore> right) {
        return left == right || right.equals(left);
    }
}
