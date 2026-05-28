package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity index for backend physics bodies.
 */
public final class PhysicsBodyRegistry {

    private final Map<PhysicsBodyId, PhysicsBodyRegistration> registrationsById =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsBody, PhysicsBodyId> bodyIdsByBody = new Reference2ObjectOpenHashMap<>();

    @Nonnull
    public PhysicsBodyRegistration registerBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsBodyId existingId = bodyIdsByBody.get(body);
        if (existingId != null && !existingId.equals(bodyId)) {
            throw new IllegalArgumentException("Physics body is already registered as " + existingId);
        }
        PhysicsBodyRegistration existingRegistration = registrationsById.get(bodyId);
        if (existingRegistration != null && existingRegistration.body() != body) {
            throw new IllegalArgumentException("Physics body id=" + bodyId
                + " is already registered to another backend body");
        }
        PhysicsBodyRegistration registration =
            new PhysicsBodyRegistration(bodyId, body, spaceId, kind, persistenceMode);
        registrationsById.put(bodyId, registration);
        bodyIdsByBody.put(body, bodyId);
        return registration;
    }

    @Nullable
    public PhysicsBodyRegistration unregisterBody(@Nonnull PhysicsBodyId bodyId) {
        PhysicsBodyRegistration registration = registrationsById.remove(bodyId);
        if (registration == null) {
            return null;
        }

        bodyIdsByBody.remove(registration.body());
        return registration;
    }

    @Nullable
    public PhysicsBodyRegistration unregisterBody(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = bodyIdsByBody.get(body);
        return bodyId != null ? unregisterBody(bodyId) : null;
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull PhysicsBodyId bodyId) {
        return registrationsById.get(bodyId);
    }

    @Nullable
    public PhysicsBodyRegistrationView getRegistrationView(@Nonnull PhysicsBodyId bodyId) {
        return toView(getRegistration(bodyId));
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getRegistrationViews() {
        List<PhysicsBodyRegistrationView> views = new ArrayList<>();
        for (PhysicsBodyRegistration registration : registrationsById.values()) {
            views.add(toView(registration));
        }
        return views;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getRegistrationViews(@Nonnull PhysicsBodyKind kind) {
        List<PhysicsBodyRegistrationView> views = new ArrayList<>();
        for (PhysicsBodyRegistration registration : registrationsById.values()) {
            if (registration.kind() == kind) {
                views.add(toView(registration));
            }
        }
        return views;
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = bodyIdsByBody.get(body);
        return bodyId != null ? registrationsById.get(bodyId) : null;
    }

    @Nullable
    public PhysicsBodyId getBodyId(@Nonnull PhysicsBody body) {
        return bodyIdsByBody.get(body);
    }

    @Nonnull
    public Collection<PhysicsBodyId> getBodyIds() {
        return new ArrayList<>(registrationsById.keySet());
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getRegistrations() {
        return new ArrayList<>(registrationsById.values());
    }

    public int getRegistrationCount() {
        return registrationsById.size();
    }

    public void forEachRegistration(@Nonnull Consumer<PhysicsBodyRegistration> consumer) {
        registrationsById.values().forEach(consumer);
    }

    public int getRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        int count = 0;
        for (PhysicsBodyRegistration registration : registrationsById.values()) {
            if (registration.persistenceMode() == persistenceMode) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getRegistrations(@Nonnull PhysicsBodyKind kind) {
        List<PhysicsBodyRegistration> registrations = new ArrayList<>();
        for (PhysicsBodyRegistration registration : registrationsById.values()) {
            if (registration.kind() == kind) {
                registrations.add(registration);
            }
        }
        return registrations;
    }

    public void clear() {
        registrationsById.clear();
        bodyIdsByBody.clear();
    }

    @Nullable
    private static PhysicsBodyRegistrationView toView(@Nullable PhysicsBodyRegistration registration) {
        if (registration == null) {
            return null;
        }
        return new PhysicsBodyRegistrationView(registration.id(),
            registration.spaceId(),
            registration.kind(),
            registration.persistenceMode());
    }
}
