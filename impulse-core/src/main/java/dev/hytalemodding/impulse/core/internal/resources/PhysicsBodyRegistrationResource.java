package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Latest copied body registrations published by the authoritative PhysicsStore.
 */
public final class PhysicsBodyRegistrationResource implements Resource<PhysicsStore> {

    @Nonnull
    private volatile PublishedRegistrations registrations = PublishedRegistrations.EMPTY;

    public PhysicsBodyRegistrationResource() {
    }

    @Nullable
    public SpaceId getBodySpaceId(@Nonnull UUID bodyUuid) {
        return registrations.spaceIdsByUuid().get(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nullable
    public SpaceId getBodySpaceId(@Nonnull Ref<PhysicsStore> bodyRef) {
        RegistrationByRef registration = registrations.registrationsByRowIndex()
            .get(Objects.requireNonNull(bodyRef, "bodyRef").getIndex());
        return registration != null && sameRef(registration.bodyRef(), bodyRef)
            ? registration.spaceId()
            : null;
    }

    @Nullable
    public UUID getBodyUuid(@Nonnull Ref<PhysicsStore> bodyRef) {
        RegistrationByRef registration = registrations.registrationsByRowIndex()
            .get(Objects.requireNonNull(bodyRef, "bodyRef").getIndex());
        return registration != null && sameRef(registration.bodyRef(), bodyRef)
            ? registration.bodyUuid()
            : null;
    }

    public boolean hasBody(@Nonnull UUID bodyUuid) {
        return registrations.spaceIdsByUuid()
            .containsKey(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    public boolean hasBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        return getBodyUuid(bodyRef) != null;
    }

    @Nonnull
    public Collection<UUID> getBodyUuids() {
        return registrations.bodyUuids();
    }

    public int getBodyRegistrationCount() {
        return registrations.bodyUuids().size();
    }

    public boolean isCurrent(long registrationTopologyGeneration) {
        return registrations.registrationTopologyGeneration() == registrationTopologyGeneration;
    }

    public int getBodyRegistrationCount(@Nonnull SpaceId spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        int count = 0;
        for (SpaceId registeredSpaceId : registrations.spaceIdsByUuid().values()) {
            if (registeredSpaceId.equals(spaceId)) {
                count++;
            }
        }
        return count;
    }

    public void publish(long registrationTopologyGeneration,
        @Nonnull Collection<BodyRegistrationPublication> publications) {
        Object2ObjectLinkedOpenHashMap<UUID, BodyRegistrationPublication> publicationsByUuid =
            new Object2ObjectLinkedOpenHashMap<>(publications.size());
        for (BodyRegistrationPublication publication : publications) {
            BodyRegistrationPublication checkedPublication =
                Objects.requireNonNull(publication, "publication");
            publicationsByUuid.put(checkedPublication.bodyUuid(), checkedPublication);
        }

        Object2ObjectLinkedOpenHashMap<UUID, SpaceId> spaceIdsByUuid =
            new Object2ObjectLinkedOpenHashMap<>(publicationsByUuid.size());
        Int2ObjectOpenHashMap<RegistrationByRef> registrationsByRowIndex =
            new Int2ObjectOpenHashMap<>(publicationsByUuid.size());
        for (BodyRegistrationPublication publication : publicationsByUuid.values()) {
            spaceIdsByUuid.put(publication.bodyUuid(), publication.spaceId());
            registrationsByRowIndex.put(publication.bodyRef().getIndex(),
                new RegistrationByRef(publication.bodyRef(),
                    publication.bodyUuid(),
                    publication.spaceId()));
        }
        registrations = new PublishedRegistrations(registrationTopologyGeneration,
            new ArrayList<>(spaceIdsByUuid.keySet()),
            spaceIdsByUuid,
            registrationsByRowIndex);
    }

    public void removeBody(@Nonnull UUID bodyUuid) {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        removeBodies(List.of(bodyUuid));
    }

    public void removeBodies(@Nonnull Collection<UUID> bodyUuids) {
        Objects.requireNonNull(bodyUuids, "bodyUuids");
        PublishedRegistrations current = registrations;
        if (bodyUuids.isEmpty()) {
            return;
        }
        ObjectOpenHashSet<UUID> removedBodyUuids = new ObjectOpenHashSet<>(bodyUuids.size());
        for (UUID bodyUuid : bodyUuids) {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            if (current.spaceIdsByUuid().containsKey(bodyUuid)) {
                removedBodyUuids.add(bodyUuid);
            }
        }
        if (removedBodyUuids.isEmpty()) {
            return;
        }
        Object2ObjectLinkedOpenHashMap<UUID, SpaceId> spaceIdsByUuid =
            new Object2ObjectLinkedOpenHashMap<>(current.spaceIdsByUuid());
        for (UUID bodyUuid : removedBodyUuids) {
            spaceIdsByUuid.remove(bodyUuid);
        }
        Int2ObjectOpenHashMap<RegistrationByRef> registrationsByRowIndex =
            new Int2ObjectOpenHashMap<>(current.registrationsByRowIndex());
        registrationsByRowIndex.int2ObjectEntrySet()
            .removeIf(entry -> removedBodyUuids.contains(entry.getValue().bodyUuid()));
        registrations = new PublishedRegistrations(current.registrationTopologyGeneration(),
            new ArrayList<>(spaceIdsByUuid.keySet()),
            spaceIdsByUuid,
            registrationsByRowIndex);
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
        @Nonnull UUID bodyUuid,
        @Nonnull SpaceId spaceId) {

        public BodyRegistrationPublication {
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Objects.requireNonNull(spaceId, "spaceId");
        }
    }

    private record RegistrationByRef(@Nonnull Ref<PhysicsStore> bodyRef,
                                     @Nonnull UUID bodyUuid,
                                     @Nonnull SpaceId spaceId) {

        private RegistrationByRef {
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Objects.requireNonNull(spaceId, "spaceId");
        }
    }

    private record PublishedRegistrations(
        long registrationTopologyGeneration,
        @Nonnull List<UUID> bodyUuids,
        @Nonnull Map<UUID, SpaceId> spaceIdsByUuid,
        @Nonnull Int2ObjectOpenHashMap<RegistrationByRef> registrationsByRowIndex) {

        private static final PublishedRegistrations EMPTY =
            new PublishedRegistrations(-1L,
                List.of(),
                Map.of(),
                new Int2ObjectOpenHashMap<>());
    }

    private static boolean sameRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.getIndex() == second.getIndex()
            && first.getStore() == second.getStore();
    }
}
