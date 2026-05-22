package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity and attachment index for backend physics bodies.
 */
public final class PhysicsBodyRegistry {

    @Nonnull
    private final Consumer<Ref<EntityStore>> syncStateCleaner;
    private final Map<PhysicsBodyId, PhysicsWorldResource.BodyRegistration> registrationsById =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsBody, PhysicsBodyId> bodyIdsByBody = new Reference2ObjectOpenHashMap<>();
    private final Map<PhysicsBodyId, Set<Ref<EntityStore>>> bodyAttachments = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsBodyId, Ref<EntityStore>> generatedVisualProxies = new Object2ObjectLinkedOpenHashMap<>();
    private final List<PhysicsWorldResource.VisualInterest> syntheticVisualInterests = new ArrayList<>();
    private final Map<PhysicsBodyId, PhysicsWorldResource.BodyVisualInterestState> bodyVisualInterestStates =
        new Object2ObjectLinkedOpenHashMap<>();

    public PhysicsBodyRegistry(@Nonnull Consumer<Ref<EntityStore>> syncStateCleaner) {
        this.syncStateCleaner = syncStateCleaner;
    }

    @Nonnull
    public PhysicsWorldResource.BodyRegistration registerBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsBodyId existingId = bodyIdsByBody.get(body);
        if (existingId != null && !existingId.equals(bodyId)) {
            throw new IllegalArgumentException("Physics body is already registered as " + existingId);
        }
        PhysicsWorldResource.BodyRegistration existingRegistration = registrationsById.get(bodyId);
        if (existingRegistration != null && existingRegistration.body() != body) {
            throw new IllegalArgumentException("Physics body id=" + bodyId
                + " is already registered to another backend body");
        }
        PhysicsWorldResource.BodyRegistration registration =
            new PhysicsWorldResource.BodyRegistration(bodyId, body, spaceId, kind, persistenceMode);
        registrationsById.put(bodyId, registration);
        bodyIdsByBody.put(body, bodyId);
        return registration;
    }

    @Nullable
    public PhysicsWorldResource.BodyRegistration unregisterBody(@Nonnull PhysicsBodyId bodyId) {
        PhysicsWorldResource.BodyRegistration registration = registrationsById.remove(bodyId);
        if (registration == null) {
            return null;
        }

        bodyIdsByBody.remove(registration.body());
        clearBodyRuntimeState(bodyId);
        return registration;
    }

    @Nullable
    public PhysicsWorldResource.BodyRegistration unregisterBody(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = bodyIdsByBody.get(body);
        return bodyId != null ? unregisterBody(bodyId) : null;
    }

    @Nullable
    public PhysicsWorldResource.BodyRegistration getRegistration(@Nonnull PhysicsBodyId bodyId) {
        return registrationsById.get(bodyId);
    }

    @Nullable
    public PhysicsWorldResource.BodyRegistration getRegistration(@Nonnull PhysicsBody body) {
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
    public Collection<PhysicsWorldResource.BodyRegistration> getRegistrations() {
        return new ArrayList<>(registrationsById.values());
    }

    public int getRegistrationCount() {
        return registrationsById.size();
    }

    public void forEachRegistration(@Nonnull Consumer<PhysicsWorldResource.BodyRegistration> consumer) {
        registrationsById.values().forEach(consumer);
    }

    public int getRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        int count = 0;
        for (PhysicsWorldResource.BodyRegistration registration : registrationsById.values()) {
            if (registration.persistenceMode() == persistenceMode) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    public Collection<PhysicsWorldResource.BodyRegistration> getRegistrations(@Nonnull PhysicsBodyKind kind) {
        List<PhysicsWorldResource.BodyRegistration> registrations = new ArrayList<>();
        for (PhysicsWorldResource.BodyRegistration registration : registrationsById.values()) {
            if (registration.kind() == kind) {
                registrations.add(registration);
            }
        }
        return registrations;
    }

    public void registerAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        bodyAttachments.computeIfAbsent(bodyId, ignored -> new ObjectOpenHashSet<>())
            .add(attachment);
    }

    public void unregisterAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyId);
        if (attachments == null) {
            return;
        }

        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyId);
        }
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull PhysicsBodyId bodyId) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyId);
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        for (Ref<EntityStore> attachment : attachments) {
            if (attachment != null && attachment.isValid()) {
                liveAttachments.add(attachment);
            } else {
                staleAttachments.add(attachment);
            }
        }
        staleAttachments.forEach(attachments::remove);
        for (Ref<EntityStore> staleAttachment : staleAttachments) {
            if (staleAttachment != null) {
                syncStateCleaner.accept(staleAttachment);
            }
        }
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyId);
        }
        return liveAttachments;
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        Ref<EntityStore> proxy = generatedVisualProxies.get(bodyId);
        if (proxy != null && !proxy.isValid()) {
            generatedVisualProxies.remove(bodyId);
            syncStateCleaner.accept(proxy);
            return null;
        }
        return proxy;
    }

    @Nonnull
    public Collection<PhysicsBodyId> getGeneratedVisualProxyBodyIds() {
        List<PhysicsBodyId> bodyIds = new ArrayList<>();
        List<PhysicsBodyId> staleBodyIds = new ArrayList<>();
        for (Map.Entry<PhysicsBodyId, Ref<EntityStore>> entry : generatedVisualProxies.entrySet()) {
            Ref<EntityStore> proxy = entry.getValue();
            if (proxy != null && proxy.isValid()) {
                bodyIds.add(entry.getKey());
            } else {
                staleBodyIds.add(entry.getKey());
                if (proxy != null) {
                    syncStateCleaner.accept(proxy);
                }
            }
        }
        for (PhysicsBodyId bodyId : staleBodyIds) {
            generatedVisualProxies.remove(bodyId);
        }
        return bodyIds;
    }

    public void setGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> previousProxy = generatedVisualProxies.put(bodyId, proxy);
        if (previousProxy != null && previousProxy != proxy) {
            syncStateCleaner.accept(previousProxy);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        Ref<EntityStore> proxy = generatedVisualProxies.remove(bodyId);
        if (proxy != null) {
            syncStateCleaner.accept(proxy);
        }
    }

    public boolean clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> expectedProxy) {
        Ref<EntityStore> proxy = generatedVisualProxies.get(bodyId);
        if (proxy == null || !sameRef(proxy, expectedProxy)) {
            return false;
        }

        generatedVisualProxies.remove(bodyId);
        syncStateCleaner.accept(proxy);
        return true;
    }

    public boolean isGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> registeredProxy = generatedVisualProxies.get(bodyId);
        return registeredProxy != null && sameRef(registeredProxy, proxy);
    }

    public void setSyntheticVisualInterests(@Nonnull Collection<PhysicsWorldResource.VisualInterest> interests) {
        syntheticVisualInterests.clear();
        syntheticVisualInterests.addAll(interests);
    }

    @Nonnull
    public List<PhysicsWorldResource.VisualInterest> getSyntheticVisualInterests() {
        return new ArrayList<>(syntheticVisualInterests);
    }

    public void clearSyntheticVisualInterests() {
        syntheticVisualInterests.clear();
    }

    @Nonnull
    public PhysicsWorldResource.BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull PhysicsBodyId bodyId) {
        return bodyVisualInterestStates.computeIfAbsent(bodyId,
            ignored -> new PhysicsWorldResource.BodyVisualInterestState());
    }

    @Nullable
    public PhysicsWorldResource.BodyVisualInterestState getBodyVisualInterestState(@Nonnull PhysicsBodyId bodyId) {
        return bodyVisualInterestStates.get(bodyId);
    }

    public void remapBodies(@Nonnull Map<PhysicsBody, PhysicsBody> bodyRemaps) {
        for (Map.Entry<PhysicsBody, PhysicsBody> entry : bodyRemaps.entrySet()) {
            PhysicsBody sourceBody = entry.getKey();
            PhysicsBody targetBody = entry.getValue();
            if (sourceBody == targetBody) {
                continue;
            }

            PhysicsBodyId bodyId = bodyIdsByBody.remove(sourceBody);
            if (bodyId == null) {
                continue;
            }

            bodyIdsByBody.put(targetBody, bodyId);
            PhysicsWorldResource.BodyRegistration registration = registrationsById.get(bodyId);
            if (registration != null) {
                registrationsById.put(bodyId, new PhysicsWorldResource.BodyRegistration(bodyId,
                    targetBody,
                    registration.spaceId(),
                    registration.kind(),
                    registration.persistenceMode()));
            }
        }
    }

    public void clearBodyRuntimeState(@Nonnull PhysicsBodyId bodyId) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.remove(bodyId);
        if (attachments != null) {
            for (Ref<EntityStore> attachment : attachments) {
                if (attachment != null) {
                    syncStateCleaner.accept(attachment);
                }
            }
        }
        bodyVisualInterestStates.remove(bodyId);
        clearGeneratedVisualProxy(bodyId);
    }

    public void clear() {
        for (Ref<EntityStore> proxy : generatedVisualProxies.values()) {
            if (proxy != null) {
                syncStateCleaner.accept(proxy);
            }
        }
        for (Set<Ref<EntityStore>> attachments : bodyAttachments.values()) {
            for (Ref<EntityStore> attachment : attachments) {
                if (attachment != null) {
                    syncStateCleaner.accept(attachment);
                }
            }
        }
        registrationsById.clear();
        bodyIdsByBody.clear();
        bodyAttachments.clear();
        generatedVisualProxies.clear();
        syntheticVisualInterests.clear();
        bodyVisualInterestStates.clear();
    }

    private static boolean sameRef(@Nonnull Ref<EntityStore> first,
        @Nonnull Ref<EntityStore> second) {
        return first == second || first.equals(second);
    }
}
