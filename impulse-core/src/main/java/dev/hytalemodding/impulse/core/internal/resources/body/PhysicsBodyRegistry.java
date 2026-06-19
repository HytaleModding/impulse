package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsBodySnapshotCursor;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity index for backend physics bodies.
 */
public final class PhysicsBodyRegistry {

    private final Map<UUID, PhysicsBodyRegistration> registrationsByUuid =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<UUID, SpaceId> publishedRegistrationSpaceIdsByUuid =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Object2LongOpenHashMap<UUID> publishedLivenessMarks =
        new Object2LongOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<UUID>> bodyUuidsByRawBackendId =
        new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ObjectArrayList<PhysicsBodyRegistration>> registrationsBySpace =
        new Int2ObjectOpenHashMap<>();
    private long publishedLivenessGeneration;

    @Nonnull
    public PhysicsBodyRegistration registerBody(@Nonnull UUID bodyUuid,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull SpaceId spaceId) {
        validateRegisterable(bodyUuid, backendBodyHandle, spaceId);
        PhysicsBodyRegistration existingRegistration = registrationsByUuid.get(bodyUuid);
        if (existingRegistration != null) {
            removeFromSpace(existingRegistration);
            removeBackendIndex(existingRegistration);
        }
        PhysicsBodyRegistration registration =
            new PhysicsBodyRegistration(bodyUuid, backendBodyHandle, spaceId);
        registrationsByUuid.put(bodyUuid, registration);
        bodyUuidsByRawBackendId
            .computeIfAbsent(spaceId.value(), ignored -> new Long2ObjectOpenHashMap<>())
            .put(backendBodyHandle.value(), bodyUuid);
        addToSpace(registration);
        return registration;
    }

    public void validateRegisterable(@Nonnull UUID bodyUuid,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull SpaceId spaceId) {
        Long2ObjectOpenHashMap<UUID> bodyUuids =
            bodyUuidsByRawBackendId.get(spaceId.value());
        UUID existingUuid = bodyUuids != null ? bodyUuids.get(backendBodyHandle.value()) : null;
        if (existingUuid != null && !existingUuid.equals(bodyUuid)) {
            throw new IllegalArgumentException("Physics body is already registered as " + existingUuid);
        }
        PhysicsBodyRegistration existingRegistration = registrationsByUuid.get(bodyUuid);
        if (existingRegistration != null
            && (!existingRegistration.backendBodyHandle().equals(backendBodyHandle)
                || !existingRegistration.spaceId().equals(spaceId))) {
            throw new IllegalArgumentException("Physics body uuid=" + bodyUuid
                + " is already registered to another backend body");
        }
    }

    @Nullable
    public PhysicsBodyRegistration unregisterBody(@Nonnull UUID bodyUuid) {
        PhysicsBodyRegistration registration = registrationsByUuid.remove(bodyUuid);
        if (registration == null) {
            return null;
        }

        removeBackendIndex(registration);
        removeFromSpace(registration);
        return registration;
    }

    @Nullable
    public PhysicsBodyRegistration unregisterBody(@Nonnull SpaceId spaceId, long backendBodyId) {
        UUID bodyUuid = getBodyUuid(spaceId, backendBodyId);
        return bodyUuid != null ? unregisterBody(bodyUuid) : null;
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull UUID bodyUuid) {
        return registrationsByUuid.get(bodyUuid);
    }

    @Nullable
    public SpaceId getPublishedRegistrationSpaceId(@Nonnull UUID bodyUuid) {
        return publishedRegistrationSpaceIdsByUuid.get(bodyUuid);
    }

    public boolean hasPublishedRegistration(@Nonnull UUID bodyUuid) {
        return publishedRegistrationSpaceIdsByUuid.containsKey(bodyUuid);
    }

    @Nonnull
    public Collection<UUID> getPublishedBodyUuids() {
        return new ArrayList<>(publishedRegistrationSpaceIdsByUuid.keySet());
    }

    @Nullable
    public UUID getBodyUuid(@Nonnull SpaceId spaceId, long backendBodyId) {
        Long2ObjectOpenHashMap<UUID> bodyUuids =
            bodyUuidsByRawBackendId.get(spaceId.value());
        return bodyUuids != null ? bodyUuids.get(backendBodyId) : null;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getRegistrations() {
        return new ArrayList<>(registrationsByUuid.values());
    }

    public int getRegistrationCount() {
        return registrationsByUuid.size();
    }

    public int getPublishedRegistrationCount() {
        return publishedRegistrationSpaceIdsByUuid.size();
    }

    public void forEachRegistration(@Nonnull Consumer<PhysicsBodyRegistration> consumer) {
        registrationsByUuid.values().forEach(consumer);
    }

    public void forEachRegistration(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodyRegistration> consumer) {
        ObjectArrayList<PhysicsBodyRegistration> registrations =
            registrationsBySpace.get(spaceId.value());
        if (registrations == null) {
            return;
        }
        registrations.forEach(consumer);
    }

    @Nonnull
    Iterator<PhysicsBodyRegistration> registrationIterator(@Nonnull SpaceId spaceId) {
        ObjectArrayList<PhysicsBodyRegistration> registrations =
            registrationsBySpace.get(spaceId.value());
        if (registrations == null) {
            return Collections.emptyIterator();
        }
        return new Iterator<>() {

            private int index;

            @Override
            public boolean hasNext() {
                return index < registrations.size();
            }

            @Override
            public PhysicsBodyRegistration next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return registrations.get(index++);
            }
        };
    }

    public int getRegistrationCount(@Nonnull SpaceId spaceId) {
        ObjectArrayList<PhysicsBodyRegistration> registrations =
            registrationsBySpace.get(spaceId.value());
        return registrations != null ? registrations.size() : 0;
    }

    public void clear() {
        registrationsByUuid.clear();
        publishedRegistrationSpaceIdsByUuid.clear();
        publishedLivenessMarks.clear();
        bodyUuidsByRawBackendId.clear();
        registrationsBySpace.clear();
    }

    public void publishLiveRegistrations() {
        long generation = nextPublishedLivenessGeneration();
        for (PhysicsBodyRegistration registration : registrationsByUuid.values()) {
            publishRegistration(registration.bodyUuid(),
                registration.spaceId(),
                generation);
        }
        retainPublishedRegistrations(generation);
    }

    public void applyPublishedRegistrationFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        long generation = nextPublishedLivenessGeneration();
        frame.forEachBodyCursor(body -> publishRegistration(body, generation));
        retainPublishedRegistrations(generation);
    }

    private void addToSpace(@Nonnull PhysicsBodyRegistration registration) {
        registrationsBySpace
            .computeIfAbsent(registration.spaceId().value(), ignored -> new ObjectArrayList<>())
            .add(registration);
    }

    private void removeFromSpace(@Nonnull PhysicsBodyRegistration registration) {
        ObjectArrayList<PhysicsBodyRegistration> registrations =
            registrationsBySpace.get(registration.spaceId().value());
        if (registrations == null) {
            return;
        }
        registrations.remove(registration);
        if (registrations.isEmpty()) {
            registrationsBySpace.remove(registration.spaceId().value());
        }
    }

    private void removeBackendIndex(@Nonnull PhysicsBodyRegistration registration) {
        Long2ObjectOpenHashMap<UUID> bodyUuids =
            bodyUuidsByRawBackendId.get(registration.spaceId().value());
        if (bodyUuids == null) {
            return;
        }
        bodyUuids.remove(registration.backendBodyHandle().value());
        if (bodyUuids.isEmpty()) {
            bodyUuidsByRawBackendId.remove(registration.spaceId().value());
        }
    }

    private void publishRegistration(@Nonnull PublishedPhysicsBodySnapshotCursor body,
        long generation) {
        publishRegistration(body.bodyUuid(),
            body.spaceId(),
            generation);
    }

    private void publishRegistration(@Nonnull UUID bodyUuid,
        @Nonnull SpaceId spaceId,
        long generation) {
        SpaceId existing = publishedRegistrationSpaceIdsByUuid.get(bodyUuid);
        if (existing == null || !existing.equals(spaceId)) {
            publishedRegistrationSpaceIdsByUuid.put(bodyUuid, spaceId);
        }
        publishedLivenessMarks.put(bodyUuid, generation);
    }

    private long nextPublishedLivenessGeneration() {
        publishedLivenessGeneration++;
        if (publishedLivenessGeneration == 0L) {
            publishedLivenessGeneration = 1L;
            publishedLivenessMarks.clear();
        }
        return publishedLivenessGeneration;
    }

    private void retainPublishedRegistrations(long generation) {
        Iterator<UUID> iterator = publishedRegistrationSpaceIdsByUuid.keySet().iterator();
        while (iterator.hasNext()) {
            UUID bodyUuid = iterator.next();
            if (publishedLivenessMarks.getLong(bodyUuid) != generation) {
                iterator.remove();
                publishedLivenessMarks.removeLong(bodyUuid);
            }
        }
    }
}
