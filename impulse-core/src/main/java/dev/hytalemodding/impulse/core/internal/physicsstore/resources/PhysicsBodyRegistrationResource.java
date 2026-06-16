package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Ref;
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
import java.util.UUID;
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

    @Nullable
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull UUID bodyUuid) {
        return registrations.viewsByUuid().get(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nullable
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull Ref<PhysicsStore> bodyRef) {
        return registrations.viewsByRef().get(Objects.requireNonNull(bodyRef, "bodyRef"));
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

    public void publish(@Nonnull Collection<BodyRegistrationPublication> publications) {
        Object2ObjectLinkedOpenHashMap<RigidBodyKey, BodyRegistrationPublication> publicationsByKey =
            new Object2ObjectLinkedOpenHashMap<>();
        for (BodyRegistrationPublication publication : publications) {
            BodyRegistrationPublication checkedPublication =
                Objects.requireNonNull(publication, "publication");
            publicationsByKey.put(checkedPublication.view().bodyKey(), checkedPublication);
        }

        Object2ObjectLinkedOpenHashMap<RigidBodyKey, PhysicsBodyRegistrationView> viewsByKey =
            new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<UUID, PhysicsBodyRegistrationView> viewsByUuid =
            new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<Ref<PhysicsStore>, PhysicsBodyRegistrationView> viewsByRef =
            new Object2ObjectLinkedOpenHashMap<>();
        for (BodyRegistrationPublication publication : publicationsByKey.values()) {
            PhysicsBodyRegistrationView registration = publication.view();
            viewsByKey.put(registration.bodyKey(), registration);
            viewsByUuid.put(registration.bodyKey().value(), registration);
            viewsByRef.put(publication.bodyRef(), registration);
        }
        registrations = new PublishedRegistrations(List.copyOf(viewsByKey.values()),
            Map.copyOf(viewsByKey),
            Map.copyOf(viewsByUuid),
            Map.copyOf(viewsByRef));
    }

    public void removeBody(@Nonnull RigidBodyKey bodyKey) {
        PublishedRegistrations current = registrations;
        if (!current.viewsByKey().containsKey(bodyKey)) {
            return;
        }
        Object2ObjectLinkedOpenHashMap<RigidBodyKey, PhysicsBodyRegistrationView> viewsByKey =
            new Object2ObjectLinkedOpenHashMap<>(current.viewsByKey());
        viewsByKey.remove(bodyKey);
        Object2ObjectLinkedOpenHashMap<UUID, PhysicsBodyRegistrationView> viewsByUuid =
            new Object2ObjectLinkedOpenHashMap<>(current.viewsByUuid());
        viewsByUuid.remove(bodyKey.value());
        Object2ObjectLinkedOpenHashMap<Ref<PhysicsStore>, PhysicsBodyRegistrationView> viewsByRef =
            new Object2ObjectLinkedOpenHashMap<>(current.viewsByRef());
        viewsByRef.object2ObjectEntrySet()
            .removeIf(entry -> entry.getValue().bodyKey().equals(bodyKey));
        registrations = new PublishedRegistrations(List.copyOf(viewsByKey.values()),
            Map.copyOf(viewsByKey),
            Map.copyOf(viewsByUuid),
            Map.copyOf(viewsByRef));
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

    public record BodyRegistrationPublication(
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyRegistrationView view) {

        public BodyRegistrationPublication {
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(view, "view");
        }
    }

    private record PublishedRegistrations(
        @Nonnull List<PhysicsBodyRegistrationView> views,
        @Nonnull Map<RigidBodyKey, PhysicsBodyRegistrationView> viewsByKey,
        @Nonnull Map<UUID, PhysicsBodyRegistrationView> viewsByUuid,
        @Nonnull Map<Ref<PhysicsStore>, PhysicsBodyRegistrationView> viewsByRef) {

        private static final PublishedRegistrations EMPTY =
            new PublishedRegistrations(List.of(), Map.of(), Map.of(), Map.of());
    }
}
