package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull UUID bodyUuid) {
        return registrations.viewsByUuid().get(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nullable
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull Ref<PhysicsStore> bodyRef) {
        RegistrationByRef registration = registrations.viewsByRowIndex()
            .get(Objects.requireNonNull(bodyRef, "bodyRef").getIndex());
        return registration != null && sameRef(registration.bodyRef(), bodyRef)
            ? registration.view()
            : null;
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
        Object2ObjectLinkedOpenHashMap<UUID, BodyRegistrationPublication> publicationsByUuid =
            new Object2ObjectLinkedOpenHashMap<>(publications.size());
        for (BodyRegistrationPublication publication : publications) {
            BodyRegistrationPublication checkedPublication =
                Objects.requireNonNull(publication, "publication");
            publicationsByUuid.put(checkedPublication.view().bodyUuid(), checkedPublication);
        }

        Object2ObjectLinkedOpenHashMap<UUID, PhysicsBodyRegistrationView> viewsByUuid =
            new Object2ObjectLinkedOpenHashMap<>(publicationsByUuid.size());
        Int2ObjectOpenHashMap<RegistrationByRef> viewsByRowIndex =
            new Int2ObjectOpenHashMap<>(publicationsByUuid.size());
        for (BodyRegistrationPublication publication : publicationsByUuid.values()) {
            PhysicsBodyRegistrationView registration = publication.view();
            viewsByUuid.put(registration.bodyUuid(), registration);
            viewsByRowIndex.put(publication.bodyRef().getIndex(),
                new RegistrationByRef(publication.bodyRef(), registration));
        }
        registrations = new PublishedRegistrations(List.copyOf(viewsByUuid.values()),
            viewsByUuid,
            viewsByRowIndex);
    }

    public void removeBody(@Nonnull UUID bodyUuid) {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        PublishedRegistrations current = registrations;
        if (!current.viewsByUuid().containsKey(bodyUuid)) {
            return;
        }
        Object2ObjectLinkedOpenHashMap<UUID, PhysicsBodyRegistrationView> viewsByUuid =
            new Object2ObjectLinkedOpenHashMap<>(current.viewsByUuid());
        viewsByUuid.remove(bodyUuid);
        Int2ObjectOpenHashMap<RegistrationByRef> viewsByRowIndex =
            new Int2ObjectOpenHashMap<>(current.viewsByRowIndex());
        viewsByRowIndex.int2ObjectEntrySet()
            .removeIf(entry -> entry.getValue().view().bodyUuid().equals(bodyUuid));
        registrations = new PublishedRegistrations(List.copyOf(viewsByUuid.values()),
            viewsByUuid,
            viewsByRowIndex);
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
        return PhysicsResourceTypes.bodyRegistrationResourceType();
    }

    public record BodyRegistrationPublication(
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyRegistrationView view) {

        public BodyRegistrationPublication {
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(view, "view");
        }
    }

    private record RegistrationByRef(@Nonnull Ref<PhysicsStore> bodyRef,
                                     @Nonnull PhysicsBodyRegistrationView view) {

        private RegistrationByRef {
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(view, "view");
        }
    }

    private record PublishedRegistrations(
        @Nonnull List<PhysicsBodyRegistrationView> views,
        @Nonnull Map<UUID, PhysicsBodyRegistrationView> viewsByUuid,
        @Nonnull Int2ObjectOpenHashMap<RegistrationByRef> viewsByRowIndex) {

        private static final PublishedRegistrations EMPTY =
            new PublishedRegistrations(List.of(),
                Map.of(),
                new Int2ObjectOpenHashMap<>());
    }

    private static boolean sameRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.getIndex() == second.getIndex()
            && first.getStore() == second.getStore();
    }
}
