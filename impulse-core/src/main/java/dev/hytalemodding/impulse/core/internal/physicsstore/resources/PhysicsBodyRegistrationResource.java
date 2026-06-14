package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Latest copied body registration views published by the authoritative PhysicsStore.
 */
public final class PhysicsBodyRegistrationResource implements Resource<PhysicsStore> {

    @Nonnull
    private volatile PublishedRegistrations registrations = PublishedRegistrations.EMPTY;

    public PhysicsBodyRegistrationResource() {
    }

    @Nullable
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull RigidBodyKey bodyKey) {
        return registrations.viewsByKey().get(Objects.requireNonNull(bodyKey, "bodyKey"));
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews() {
        return registrations.views();
    }

    public int getBodyRegistrationCount() {
        return registrations.views().size();
    }

    public int getBodyRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        Objects.requireNonNull(persistenceMode, "persistenceMode");
        int count = 0;
        for (PhysicsBodyRegistrationView view : registrations.views()) {
            if (view.persistenceMode() == persistenceMode) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews(
        @Nonnull PhysicsBodyKind kind) {
        Objects.requireNonNull(kind, "kind");
        List<PhysicsBodyRegistrationView> views = new ArrayList<>();
        for (PhysicsBodyRegistrationView view : registrations.views()) {
            if (view.kind() == kind) {
                views.add(view);
            }
        }
        return views;
    }

    public void publish(@Nonnull Collection<PhysicsBodyRegistrationView> views) {
        Object2ObjectLinkedOpenHashMap<RigidBodyKey, PhysicsBodyRegistrationView> viewsByKey =
            new Object2ObjectLinkedOpenHashMap<>();
        for (PhysicsBodyRegistrationView view : views) {
            PhysicsBodyRegistrationView registration =
                Objects.requireNonNull(view, "view");
            viewsByKey.put(registration.bodyKey(), registration);
        }
        registrations = new PublishedRegistrations(List.copyOf(viewsByKey.values()),
            Map.copyOf(viewsByKey));
    }

    public void clear() {
        registrations = PublishedRegistrations.EMPTY;
    }

    @Nonnull
    @Override
    public PhysicsBodyRegistrationResource clone() {
        PhysicsBodyRegistrationResource copy = new PhysicsBodyRegistrationResource();
        copy.registrations = registrations;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsBodyRegistrationResource> getResourceType() {
        return PhysicsStoreTypes.bodyRegistrationResourceType();
    }

    private record PublishedRegistrations(
        @Nonnull List<PhysicsBodyRegistrationView> views,
        @Nonnull Map<RigidBodyKey, PhysicsBodyRegistrationView> viewsByKey) {

        private static final PublishedRegistrations EMPTY =
            new PublishedRegistrations(List.of(), Map.of());
    }
}
