package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsBodySnapshotCursor;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity index for backend physics bodies.
 */
public final class PhysicsBodyRegistry {

    private final Map<RigidBodyKey, PhysicsBodyRegistration> registrationsByKey =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<RigidBodyKey, PhysicsBodyRegistrationView> registrationViewsByKey =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<RigidBodyKey, PhysicsBodyRegistrationView> publishedRegistrationViewsByKey =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Object2LongOpenHashMap<RigidBodyKey> publishedLivenessMarks =
        new Object2LongOpenHashMap<>();
    private final Map<PhysicsBody, RigidBodyKey> bodyKeysByBody = new Reference2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ObjectArrayList<PhysicsBodyRegistration>> registrationsBySpace =
        new Int2ObjectOpenHashMap<>();
    private long publishedLivenessGeneration;

    @Nonnull
    public PhysicsBodyRegistration registerBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        RigidBodyKey existingKey = bodyKeysByBody.get(body);
        if (existingKey != null && !existingKey.equals(bodyKey)) {
            throw new IllegalArgumentException("Physics body is already registered as " + existingKey);
        }
        PhysicsBodyRegistration existingRegistration = registrationsByKey.get(bodyKey);
        if (existingRegistration != null && existingRegistration.body() != body) {
            throw new IllegalArgumentException("Physics body key=" + bodyKey
                + " is already registered to another backend body");
        }
        if (existingRegistration != null) {
            removeFromSpace(existingRegistration);
        }
        PhysicsBodyRegistration registration =
            new PhysicsBodyRegistration(bodyKey, body, spaceId, kind, persistenceMode);
        registrationsByKey.put(bodyKey, registration);
        registrationViewsByKey.put(bodyKey,
            new PhysicsBodyRegistrationView(bodyKey, spaceId, kind, persistenceMode));
        bodyKeysByBody.put(body, bodyKey);
        addToSpace(registration);
        return registration;
    }

    @Nullable
    public PhysicsBodyRegistration unregisterBody(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = registrationsByKey.remove(bodyKey);
        if (registration == null) {
            return null;
        }

        bodyKeysByBody.remove(registration.body());
        registrationViewsByKey.remove(bodyKey);
        removeFromSpace(registration);
        return registration;
    }

    @Nullable
    public PhysicsBodyRegistration unregisterBody(@Nonnull PhysicsBody body) {
        RigidBodyKey bodyKey = bodyKeysByBody.get(body);
        return bodyKey != null ? unregisterBody(bodyKey) : null;
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull RigidBodyKey bodyKey) {
        return registrationsByKey.get(bodyKey);
    }

    @Nullable
    public PhysicsBodyRegistrationView getRegistrationView(@Nonnull RigidBodyKey bodyKey) {
        return registrationViewsByKey.get(bodyKey);
    }

    @Nullable
    public PhysicsBodyRegistrationView getPublishedRegistrationView(@Nonnull RigidBodyKey bodyKey) {
        return publishedRegistrationViewsByKey.get(bodyKey);
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getRegistrationViews() {
        List<PhysicsBodyRegistrationView> views = new ArrayList<>();
        for (PhysicsBodyRegistration registration : registrationsByKey.values()) {
            views.add(registrationViewsByKey.get(registration.id()));
        }
        return views;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getPublishedRegistrationViews() {
        return new ArrayList<>(publishedRegistrationViewsByKey.values());
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getPublishedRegistrationViews(@Nonnull PhysicsBodyKind kind) {
        List<PhysicsBodyRegistrationView> views = new ArrayList<>();
        for (PhysicsBodyRegistrationView view : publishedRegistrationViewsByKey.values()) {
            if (view.kind() == kind) {
                views.add(view);
            }
        }
        return views;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getRegistrationViews(@Nonnull PhysicsBodyKind kind) {
        List<PhysicsBodyRegistrationView> views = new ArrayList<>();
        for (PhysicsBodyRegistration registration : registrationsByKey.values()) {
            if (registration.kind() == kind) {
                views.add(registrationViewsByKey.get(registration.id()));
            }
        }
        return views;
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull PhysicsBody body) {
        RigidBodyKey bodyKey = bodyKeysByBody.get(body);
        return bodyKey != null ? registrationsByKey.get(bodyKey) : null;
    }

    @Nullable
    public RigidBodyKey getBodyKey(@Nonnull PhysicsBody body) {
        return bodyKeysByBody.get(body);
    }

    @Nonnull
    public Collection<RigidBodyKey> getBodyKeys() {
        return new ArrayList<>(registrationsByKey.keySet());
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getRegistrations() {
        return new ArrayList<>(registrationsByKey.values());
    }

    public int getRegistrationCount() {
        return registrationsByKey.size();
    }

    public int getPublishedRegistrationCount() {
        return publishedRegistrationViewsByKey.size();
    }

    public void forEachRegistration(@Nonnull Consumer<PhysicsBodyRegistration> consumer) {
        registrationsByKey.values().forEach(consumer);
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

    public int getRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        int count = 0;
        for (PhysicsBodyRegistration registration : registrationsByKey.values()) {
            if (registration.persistenceMode() == persistenceMode) {
                count++;
            }
        }
        return count;
    }

    public int getPublishedRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        int count = 0;
        for (PhysicsBodyRegistrationView view : publishedRegistrationViewsByKey.values()) {
            if (view.persistenceMode() == persistenceMode) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getRegistrations(@Nonnull PhysicsBodyKind kind) {
        List<PhysicsBodyRegistration> registrations = new ArrayList<>();
        for (PhysicsBodyRegistration registration : registrationsByKey.values()) {
            if (registration.kind() == kind) {
                registrations.add(registration);
            }
        }
        return registrations;
    }

    public void clear() {
        registrationsByKey.clear();
        registrationViewsByKey.clear();
        publishedRegistrationViewsByKey.clear();
        publishedLivenessMarks.clear();
        bodyKeysByBody.clear();
        registrationsBySpace.clear();
    }

    public void publishLiveRegistrationViews() {
        long generation = nextPublishedLivenessGeneration();
        for (PhysicsBodyRegistration registration : registrationsByKey.values()) {
            publishRegistrationView(registration.id(),
                registration.spaceId(),
                registration.kind(),
                registration.persistenceMode(),
                generation);
        }
        retainPublishedRegistrationViews(generation);
    }

    public void applyPublishedRegistrationFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        long generation = nextPublishedLivenessGeneration();
        frame.forEachBodyCursor(body -> publishRegistrationView(body, generation));
        retainPublishedRegistrationViews(generation);
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

    private void publishRegistrationView(@Nonnull PublishedPhysicsBodySnapshotCursor body,
        long generation) {
        RigidBodyKey bodyKey = body.bodyKey();
        publishRegistrationView(bodyKey,
            body.spaceId(),
            body.kind(),
            body.persistenceMode(),
            generation);
    }

    private void publishRegistrationView(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        long generation) {
        PhysicsBodyRegistrationView existing = publishedRegistrationViewsByKey.get(bodyKey);
        if (existing == null
            || !existing.spaceId().equals(spaceId)
            || existing.kind() != kind
            || existing.persistenceMode() != persistenceMode) {
            publishedRegistrationViewsByKey.put(bodyKey,
                new PhysicsBodyRegistrationView(bodyKey, spaceId, kind, persistenceMode));
        }
        publishedLivenessMarks.put(bodyKey, generation);
    }

    private long nextPublishedLivenessGeneration() {
        publishedLivenessGeneration++;
        if (publishedLivenessGeneration == 0L) {
            publishedLivenessGeneration = 1L;
            publishedLivenessMarks.clear();
        }
        return publishedLivenessGeneration;
    }

    private void retainPublishedRegistrationViews(long generation) {
        Iterator<RigidBodyKey> iterator = publishedRegistrationViewsByKey.keySet().iterator();
        while (iterator.hasNext()) {
            RigidBodyKey bodyKey = iterator.next();
            if (publishedLivenessMarks.getLong(bodyKey) != generation) {
                iterator.remove();
                publishedLivenessMarks.removeLong(bodyKey);
            }
        }
    }
}
