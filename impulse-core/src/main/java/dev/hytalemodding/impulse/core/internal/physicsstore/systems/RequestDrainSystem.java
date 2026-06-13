package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.PendingBodyOperation;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyActivationRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyForceRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyRemoveRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTargetRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTypeRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyUpsertRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.JointRemoveRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.JointUpsertRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderRequest;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies copied boundary requests before backend reconciliation.
 */
public final class RequestDrainSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistenceHydrationSystem.class),
        new SystemDependency<>(Order.BEFORE, IdentityIndexSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRequestQueueResource queue = store.getResource(
            PhysicsRequestQueueResource.getResourceType());
        List<PhysicsStoreRequest> requests = queue.drain();
        if (requests.isEmpty()) {
            return;
        }
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsTerrainPayloadResource terrainPayloads = store.getResource(
            PhysicsTerrainPayloadResource.getResourceType());
        Set<UUID> structuralConflicts = structuralConflicts(requests, restore);
        Map<UUID, Ref<PhysicsStore>> refsThisDrain = new Object2ObjectOpenHashMap<>();

        applyRemovals(store,
            systemIndex,
            identity,
            runtime,
            terrainPayloads,
            refsThisDrain,
            restore,
            structuralConflicts,
            requests);
        applyUpserts(store,
            systemIndex,
            identity,
            runtime,
            terrainPayloads,
            refsThisDrain,
            restore,
            structuralConflicts,
            requests);
        applyBodyTypeRequests(store, identity, runtime, refsThisDrain, restore, requests);
        applyTargetRequests(store, identity, refsThisDrain, restore, requests);
        enqueueRuntimeBodyRequests(store, identity, runtime, refsThisDrain, restore, requests);
        recordUnsupported(restore, requests);
    }

    private static void applyRemovals(@Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Set<UUID> structuralConflicts,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof BodyRemoveRequest bodyRequest) {
                if (!structuralConflicts.contains(bodyRequest.bodyUuid())) {
                    applyBodyRemove(store,
                        systemIndex,
                        identity,
                        runtime,
                        refsThisDrain,
                        bodyRequest);
                }
                continue;
            }
            if (request instanceof JointRemoveRequest jointRequest) {
                if (!structuralConflicts.contains(jointRequest.jointUuid())) {
                    applyJointRemove(store, identity, runtime, refsThisDrain, jointRequest);
                }
                continue;
            }
            if (request instanceof TerrainColliderRequest terrainRequest
                && terrainRequest.remove()
                && !structuralConflicts.contains(terrainRequest.terrainColliderUuid())) {
                applyTerrainRequest(store,
                    identity,
                    terrainPayloads,
                    refsThisDrain,
                    restore,
                    terrainRequest);
            }
        }
    }

    private static void applyUpserts(@Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Set<UUID> structuralConflicts,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof BodyUpsertRequest bodyRequest) {
                if (!structuralConflicts.contains(bodyRequest.bodyUuid())) {
                    applyBodyUpsert(store,
                        systemIndex,
                        identity,
                        runtime,
                        refsThisDrain,
                        restore,
                        bodyRequest);
                }
                continue;
            }
            if (request instanceof JointUpsertRequest jointRequest) {
                if (!structuralConflicts.contains(jointRequest.jointUuid())) {
                    applyJointUpsert(store,
                        identity,
                        runtime,
                        refsThisDrain,
                        restore,
                        jointRequest);
                }
                continue;
            }
            if (request instanceof TerrainColliderRequest terrainRequest
                && !terrainRequest.remove()
                && !structuralConflicts.contains(terrainRequest.terrainColliderUuid())) {
                applyTerrainRequest(store,
                    identity,
                    terrainPayloads,
                    refsThisDrain,
                    restore,
                    terrainRequest);
            }
        }
    }

    private static void applyTargetRequests(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof BodyTargetRequest targetRequest) {
                applyTargetRequest(store, identity, refsThisDrain, restore, targetRequest);
            }
        }
    }

    private static void recordUnsupported(@Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (!isSupportedRequest(request)) {
                restore.recordSoftSkip("Unsupported PhysicsStore request "
                    + request.getClass().getName());
            }
        }
    }

    private static void applyBodyRemove(@Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull BodyRemoveRequest request) {
        UUID bodyUuid = request.bodyUuid();
        List<JointRow> attachedJoints = collectAttachedJoints(store, systemIndex, bodyUuid);
        List<ColliderRow> colliders = collectColliders(store, systemIndex, bodyUuid);
        Set<UUID> removedRows = new ObjectOpenHashSet<>();

        for (JointRow joint : attachedJoints) {
            removeJointBackend(runtime, identity, joint.uuid(), joint.joint());
            removeRow(store, identity, refsThisDrain, removedRows, joint.uuid(), joint.ref());
        }
        removeBodyBackend(runtime, identity, bodyUuid);
        removeRow(store,
            identity,
            refsThisDrain,
            removedRows,
            bodyUuid,
            refForUuid(identity, refsThisDrain, bodyUuid));
        for (ColliderRow collider : colliders) {
            removeRow(store, identity, refsThisDrain, removedRows, collider.uuid(), collider.ref());
        }
        for (UUID ownedRowUuid : request.ownedRowUuids()) {
            removeRow(store,
                identity,
                refsThisDrain,
                removedRows,
                ownedRowUuid,
                refForUuid(identity, refsThisDrain, ownedRowUuid));
        }
    }

    private static void applyBodyUpsert(@Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull BodyUpsertRequest request) {
        if (!isValidBodyUpsert(request, restore)) {
            return;
        }
        if (runtime.getBodyHandle(request.bodyUuid()) != null) {
            for (JointRow joint : collectAttachedJoints(store, systemIndex, request.bodyUuid())) {
                removeJointBackend(runtime, identity, joint.uuid(), joint.joint());
            }
            removeBodyBackend(runtime, identity, request.bodyUuid());
        }
        upsertComponentRow(store,
            identity,
            refsThisDrain,
            request.shapeUuid(),
            ShapeComponent.getComponentType(),
            request.shape().clone());
        upsertComponentRow(store,
            identity,
            refsThisDrain,
            request.materialUuid(),
            MaterialComponent.getComponentType(),
            request.material().clone());
        upsertComponentRow(store,
            identity,
            refsThisDrain,
            request.filterUuid(),
            CollisionFilterComponent.getComponentType(),
            request.filter().clone());
        upsertComponentRow(store,
            identity,
            refsThisDrain,
            request.colliderUuid(),
            ColliderComponent.getComponentType(),
            request.collider().clone());

        Ref<PhysicsStore> bodyRef = ensureRow(store, identity, refsThisDrain, request.bodyUuid());
        store.putComponent(bodyRef, BodyComponent.getComponentType(), request.body().clone());
        store.putComponent(bodyRef, DynamicsComponent.getComponentType(), request.dynamics().clone());
        if (request.target() != null) {
            store.putComponent(bodyRef, TargetComponent.getComponentType(), request.target().clone());
        } else {
            store.removeComponent(bodyRef, TargetComponent.getComponentType());
        }
    }

    private static void applyJointRemove(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull JointRemoveRequest request) {
        Ref<PhysicsStore> ref = refForUuid(identity, refsThisDrain, request.jointUuid());
        JointComponent joint = PhysicsStoreSystemSupport.component(store,
            ref,
            JointComponent.getComponentType());
        removeJointBackend(runtime, identity, request.jointUuid(), joint);
        removeRow(store,
            identity,
            refsThisDrain,
            new ObjectOpenHashSet<>(),
            request.jointUuid(),
            ref);
    }

    private static void applyJointUpsert(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull JointUpsertRequest request) {
        if (!isValidJointUpsert(request, restore)) {
            return;
        }
        Ref<PhysicsStore> ref = refForUuid(identity, refsThisDrain, request.jointUuid());
        JointComponent existing = PhysicsStoreSystemSupport.component(store,
            ref,
            JointComponent.getComponentType());
        removeJointBackend(runtime, identity, request.jointUuid(), existing);
        upsertComponentRow(store,
            identity,
            refsThisDrain,
            request.jointUuid(),
            JointComponent.getComponentType(),
            request.joint().clone());
    }

    private static void applyTargetRequest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull BodyTargetRequest request) {
        Ref<PhysicsStore> bodyRef = refForUuid(identity, refsThisDrain, request.bodyUuid());
        if (bodyRef == null) {
            restore.recordSoftSkip("Target request body is missing: " + request.bodyUuid());
            return;
        }
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        target.setPosition(request.position());
        target.setRotation(request.rotation());
        target.setLinearVelocity(request.linearVelocity());
        target.setAngularVelocity(request.angularVelocity());
        target.setTransformEnabled(request.transformEnabled());
        target.setVelocityEnabled(request.velocityEnabled());
        target.setActivate(request.activate());
        store.putComponent(bodyRef, TargetComponent.getComponentType(), target);
    }

    private static void applyBodyTypeRequests(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof BodyTypeRequest typeRequest) {
                applyBodyTypeRequest(store, identity, runtime, refsThisDrain, restore, typeRequest);
            }
        }
    }

    private static void enqueueRuntimeBodyRequests(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<PhysicsStoreRequest> requests) {
        for (PhysicsStoreRequest request : requests) {
            if (request instanceof BodyActivationRequest activationRequest) {
                enqueueBodyActivationRequest(identity, runtime, refsThisDrain, restore, activationRequest);
                continue;
            }
            if (request instanceof BodyForceRequest forceRequest) {
                enqueueBodyForceRequest(store, identity, runtime, refsThisDrain, restore, forceRequest);
            }
        }
    }

    private static void applyBodyTypeRequest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull BodyTypeRequest request) {
        Ref<PhysicsStore> bodyRef = refForUuid(identity, refsThisDrain, request.bodyUuid());
        if (bodyRef == null) {
            restore.recordSoftSkip("Body type request body is missing: " + request.bodyUuid());
            return;
        }
        DynamicsComponent dynamics = PhysicsStoreSystemSupport.component(store,
            bodyRef,
            DynamicsComponent.getComponentType());
        DynamicsComponent updated = dynamics != null ? dynamics.clone() : new DynamicsComponent();
        updated.setBodyType(request.bodyType());
        store.putComponent(bodyRef, DynamicsComponent.getComponentType(), updated);

        RuntimeBodyBinding binding = runtimeBodyBinding(runtime,
            request.bodyUuid(),
            restore,
            "Body type request",
            false);
        if (binding == null) {
            return;
        }
        binding.backendRuntime().setBodyType(binding.spaceHandle().value(),
            binding.bodyHandle().value(),
            BackendRuntimeCodes.bodyTypeCode(request.bodyType()));
        updateBodyHitMetadata(runtime, binding.bodyHandle(), request.bodyType());
        if (request.activate()) {
            runtime.enqueuePendingBodyOperation(PendingBodyOperation.wake(binding.spaceHandle(),
                binding.bodyHandle()));
        }
    }

    private static void enqueueBodyActivationRequest(
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull BodyActivationRequest request) {
        if (refForUuid(identity, refsThisDrain, request.bodyUuid()) == null) {
            restore.recordSoftSkip("Activation request body is missing: " + request.bodyUuid());
            return;
        }
        RuntimeBodyBinding binding = runtimeBodyBinding(runtime,
            request.bodyUuid(),
            restore,
            "Activation request",
            true);
        if (binding == null) {
            return;
        }
        if (request.action() == BodyActivationRequest.Action.WAKE) {
            runtime.enqueuePendingBodyOperation(PendingBodyOperation.wake(binding.spaceHandle(),
                binding.bodyHandle()));
        } else {
            runtime.enqueuePendingBodyOperation(PendingBodyOperation.sleep(binding.spaceHandle(),
                binding.bodyHandle()));
        }
    }

    private static void enqueueBodyForceRequest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull BodyForceRequest request) {
        Ref<PhysicsStore> bodyRef = refForUuid(identity, refsThisDrain, request.bodyUuid());
        if (bodyRef == null) {
            restore.recordSoftSkip("Force request body is missing: " + request.bodyUuid());
            return;
        }
        DynamicsComponent dynamics = PhysicsStoreSystemSupport.component(store,
            bodyRef,
            DynamicsComponent.getComponentType());
        if (dynamics == null || dynamics.getBodyType() != PhysicsBodyType.DYNAMIC) {
            restore.recordSoftSkip("Force request target is not dynamic: " + request.bodyUuid());
            return;
        }
        if (!hasFiniteVector(request)) {
            restore.recordSoftSkip("Force request contains non-finite values: " + request.bodyUuid());
            return;
        }
        RuntimeBodyBinding binding = runtimeBodyBinding(runtime,
            request.bodyUuid(),
            restore,
            "Force request",
            true);
        if (binding == null) {
            return;
        }
        runtime.enqueuePendingBodyOperation(PendingBodyOperation.vector(pendingKind(request),
            binding.spaceHandle(),
            binding.bodyHandle(),
            request.x(),
            request.y(),
            request.z(),
            request.hasOffset(),
            request.offsetX(),
            request.offsetY(),
            request.offsetZ()));
    }

    private static void applyTerrainRequest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull TerrainColliderRequest request) {
        UUID terrainUuid = request.terrainColliderUuid();
        Ref<PhysicsStore> ref = refForUuid(identity, refsThisDrain, terrainUuid);
        if (request.remove()) {
            if (ref != null) {
                TerrainColliderComponent existing = store.getComponent(ref,
                    TerrainColliderComponent.getComponentType());
                if (existing != null) {
                    removePayload(terrainPayloads, existing.getPayloadResourceKey());
                }
                store.putComponent(ref,
                    TerrainColliderComponent.getComponentType(),
                    removedTerrainComponent(request));
            }
            removePayload(terrainPayloads, request.payloadResourceKey());
            return;
        }
        TerrainColliderPayload payload = request.payload();
        if (payload == null || payload.isEmpty()) {
            restore.recordSoftSkip("Terrain upsert payload is missing: " + request.sourceKey());
            return;
        }
        terrainPayloads.put(request.payloadResourceKey(), payload);
        TerrainColliderComponent component = activeTerrainComponent(request);
        if (ref != null) {
            TerrainColliderComponent existing = store.getComponent(ref,
                TerrainColliderComponent.getComponentType());
            if (existing != null
                && !existing.getPayloadResourceKey().equals(component.getPayloadResourceKey())) {
                removePayload(terrainPayloads, existing.getPayloadResourceKey());
            }
            store.putComponent(ref, TerrainColliderComponent.getComponentType(), component);
            refsThisDrain.put(terrainUuid, ref);
            return;
        }
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        holder.addComponent(UuidComponent.getComponentType(), new UuidComponent(terrainUuid));
        holder.addComponent(TerrainColliderComponent.getComponentType(), component);
        refsThisDrain.put(terrainUuid, store.addEntity(holder, AddReason.SPAWN));
    }

    @Nonnull
    private static <C extends Component<PhysicsStore>> Ref<PhysicsStore> upsertComponentRow(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull UUID uuid,
        @Nonnull ComponentType<PhysicsStore, C> type,
        @Nonnull C component) {
        Ref<PhysicsStore> ref = ensureRow(store, identity, refsThisDrain, uuid);
        store.putComponent(ref, type, component);
        return ref;
    }

    @Nonnull
    private static Ref<PhysicsStore> ensureRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull UUID uuid) {
        Ref<PhysicsStore> ref = refForUuid(identity, refsThisDrain, uuid);
        if (ref != null) {
            return ref;
        }
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        holder.addComponent(UuidComponent.getComponentType(), new UuidComponent(uuid));
        ref = store.addEntity(holder, AddReason.SPAWN);
        refsThisDrain.put(uuid, ref);
        return ref;
    }

    private static void removeRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull Set<UUID> removedRows,
        @Nonnull UUID uuid,
        @Nullable Ref<PhysicsStore> ref) {
        if (!removedRows.add(uuid)) {
            return;
        }
        refsThisDrain.remove(uuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        identity.removeUuid(uuid, ref);
        store.removeEntity(ref, store.getRegistry().newHolder(), RemoveReason.REMOVE);
    }

    private static void removeBodyBackend(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyUuid);
        if (bodyHandle == null) {
            return;
        }
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyUuid);
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeBody(spaceHandle.value(), bodyHandle.value());
        }
        identity.removeBodyHandle(bodyHandle);
        runtime.removeBodyHandle(bodyUuid);
    }

    private static void removeJointBackend(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID jointUuid,
        @Nullable JointComponent joint) {
        BackendJointHandle jointHandle = runtime.getJointHandle(jointUuid);
        if (jointHandle == null) {
            return;
        }
        BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(jointUuid);
        if (spaceHandle == null && joint != null) {
            spaceHandle = runtime.getSpaceHandle(joint.getSpaceUuid());
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeJoint(spaceHandle.value(), jointHandle.value());
        }
        identity.removeJointHandle(jointHandle);
        runtime.removeJointHandle(jointUuid);
    }

    @Nullable
    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nullable BackendSpaceHandle spaceHandle) {
        if (spaceHandle == null) {
            return null;
        }
        final PhysicsBackendRuntime[] resolved = new PhysicsBackendRuntime[1];
        runtime.forEachSpaceBinding((_, _, handle, backendRuntime) -> {
            if (handle.value() == spaceHandle.value()) {
                resolved[0] = backendRuntime;
            }
        });
        return resolved[0];
    }

    @Nonnull
    private static List<JointRow> collectAttachedJoints(@Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull UUID bodyUuid) {
        List<JointRow> rows = new ArrayList<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> {
                for (int index = 0; index < chunk.size(); index++) {
                    JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
                    if (joint == null
                        || (!bodyUuid.equals(joint.getBodyAUuid())
                            && !bodyUuid.equals(joint.getBodyBUuid()))) {
                        continue;
                    }
                    UUID jointUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
                    if (!PhysicsStoreSystemSupport.isNil(jointUuid)) {
                        rows.add(new JointRow(jointUuid,
                            chunk.getReferenceTo(index),
                            joint.clone()));
                    }
                }
            };
        store.forEachChunk(systemIndex, collector);
        return rows;
    }

    @Nonnull
    private static List<ColliderRow> collectColliders(@Nonnull Store<PhysicsStore> store,
        int systemIndex,
        @Nonnull UUID bodyUuid) {
        List<ColliderRow> rows = new ArrayList<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> {
                for (int index = 0; index < chunk.size(); index++) {
                    ColliderComponent collider = chunk.getComponent(index,
                        ColliderComponent.getComponentType());
                    if (collider == null || !bodyUuid.equals(collider.getBodyUuid())) {
                        continue;
                    }
                    UUID colliderUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
                    if (!PhysicsStoreSystemSupport.isNil(colliderUuid)) {
                        rows.add(new ColliderRow(colliderUuid,
                            chunk.getReferenceTo(index),
                            collider.clone()));
                    }
                }
            };
        store.forEachChunk(systemIndex, collector);
        return rows;
    }

    @Nonnull
    private static Set<UUID> structuralConflicts(
        @Nonnull List<PhysicsStoreRequest> requests,
        @Nonnull PhysicsRestoreStatusResource restore) {
        Map<UUID, String> operationsByUuid = new Object2ObjectOpenHashMap<>();
        Set<UUID> conflicts = new ObjectOpenHashSet<>();
        for (PhysicsStoreRequest request : requests) {
            UUID uuid = structuralUuid(request);
            if (uuid == null) {
                continue;
            }
            String operation = structuralOperation(request);
            String previous = operationsByUuid.putIfAbsent(uuid, operation);
            if (previous != null) {
                conflicts.add(uuid);
            }
        }
        for (UUID conflict : conflicts) {
            restore.recordSoftSkip("Conflicting structural PhysicsStore requests for uuid: "
                + conflict);
        }
        return conflicts;
    }

    @Nullable
    private static UUID structuralUuid(@Nonnull PhysicsStoreRequest request) {
        if (request instanceof BodyUpsertRequest bodyRequest) {
            return bodyRequest.bodyUuid();
        }
        if (request instanceof BodyRemoveRequest bodyRequest) {
            return bodyRequest.bodyUuid();
        }
        if (request instanceof JointUpsertRequest jointRequest) {
            return jointRequest.jointUuid();
        }
        if (request instanceof JointRemoveRequest jointRequest) {
            return jointRequest.jointUuid();
        }
        if (request instanceof TerrainColliderRequest terrainRequest) {
            return terrainRequest.terrainColliderUuid();
        }
        return null;
    }

    @Nonnull
    private static String structuralOperation(@Nonnull PhysicsStoreRequest request) {
        if (request instanceof TerrainColliderRequest terrainRequest) {
            return terrainRequest.remove() ? "terrain-remove" : "terrain-upsert";
        }
        return request.getClass().getName();
    }

    @Nullable
    private static RuntimeBodyBinding runtimeBodyBinding(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull String requestName,
        boolean requireBound) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyUuid);
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyUuid);
        if (bodyHandle == null || spaceHandle == null) {
            if (requireBound) {
                restore.recordSoftSkip(requestName + " body is unbound: " + bodyUuid);
            }
            return null;
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
        if (backendRuntime == null) {
            restore.recordSoftSkip(requestName + " backend runtime is missing: " + bodyUuid);
            return null;
        }
        return new RuntimeBodyBinding(spaceHandle, bodyHandle, backendRuntime);
    }

    private static void updateBodyHitMetadata(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull BackendBodyHandle bodyHandle,
        @Nonnull PhysicsBodyType bodyType) {
        PhysicsRuntimeResource.BodyHitMetadata metadata = runtime.getBodyHitMetadata(bodyHandle);
        if (metadata != null) {
            runtime.putBodyHitMetadata(bodyHandle,
                metadata.bodyKey(),
                bodyType,
                metadata.shapeType());
        }
    }

    private static boolean hasFiniteVector(@Nonnull BodyForceRequest request) {
        return Float.isFinite(request.x())
            && Float.isFinite(request.y())
            && Float.isFinite(request.z())
            && (!request.hasOffset()
                || (Float.isFinite(request.offsetX())
                    && Float.isFinite(request.offsetY())
                    && Float.isFinite(request.offsetZ())));
    }

    @Nonnull
    private static PendingBodyOperation.Kind pendingKind(@Nonnull BodyForceRequest request) {
        return switch (request.kind()) {
            case IMPULSE -> PendingBodyOperation.Kind.IMPULSE;
            case TORQUE_IMPULSE -> PendingBodyOperation.Kind.TORQUE_IMPULSE;
            case FORCE -> PendingBodyOperation.Kind.FORCE;
            case TORQUE -> PendingBodyOperation.Kind.TORQUE;
        };
    }

    private static boolean isValidBodyUpsert(@Nonnull BodyUpsertRequest request,
        @Nonnull PhysicsRestoreStatusResource restore) {
        if (isNil(request.bodyUuid())
            || isNil(request.body().getSpaceUuid())
            || isNil(request.colliderUuid())
            || isNil(request.shapeUuid())
            || isNil(request.materialUuid())
            || isNil(request.filterUuid())) {
            restore.recordSoftSkip("Body upsert contains nil UUIDs: " + request.bodyUuid());
            return false;
        }
        if (!request.bodyUuid().equals(request.collider().getBodyUuid())
            || !request.shapeUuid().equals(request.collider().getShapeUuid())
            || !request.materialUuid().equals(request.collider().getMaterialUuid())
            || !request.filterUuid().equals(request.collider().getFilterUuid())) {
            restore.recordSoftSkip("Body upsert collider refs do not match request UUIDs: "
                + request.bodyUuid());
            return false;
        }
        return true;
    }

    private static boolean isValidJointUpsert(@Nonnull JointUpsertRequest request,
        @Nonnull PhysicsRestoreStatusResource restore) {
        if (isNil(request.jointUuid())
            || isNil(request.joint().getSpaceUuid())
            || isNil(request.joint().getBodyAUuid())
            || isNil(request.joint().getBodyBUuid())) {
            restore.recordSoftSkip("Joint upsert contains nil UUIDs: " + request.jointUuid());
            return false;
        }
        return true;
    }

    private static boolean isSupportedRequest(@Nonnull PhysicsStoreRequest request) {
        return request instanceof BodyTargetRequest
            || request instanceof BodyActivationRequest
            || request instanceof BodyForceRequest
            || request instanceof BodyTypeRequest
            || request instanceof TerrainColliderRequest
            || request instanceof BodyUpsertRequest
            || request instanceof BodyRemoveRequest
            || request instanceof JointUpsertRequest
            || request instanceof JointRemoveRequest;
    }

    @Nullable
    private static Ref<PhysicsStore> refForUuid(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Map<UUID, Ref<PhysicsStore>> refsThisDrain,
        @Nonnull UUID uuid) {
        Ref<PhysicsStore> ref = refsThisDrain.get(uuid);
        if (ref != null && ref.isValid()) {
            return ref;
        }
        return PhysicsStoreSystemSupport.refForUuid(identity, uuid);
    }

    private static boolean isNil(@Nonnull UUID uuid) {
        return PhysicsStoreSystemSupport.isNil(uuid);
    }

    @Nonnull
    private static TerrainColliderComponent activeTerrainComponent(
        @Nonnull TerrainColliderRequest request) {
        return terrainComponent(request, request.payloadResourceKey(), true);
    }

    @Nonnull
    private static TerrainColliderComponent removedTerrainComponent(
        @Nonnull TerrainColliderRequest request) {
        return terrainComponent(request, request.payloadResourceKey(), false);
    }

    @Nonnull
    private static TerrainColliderComponent terrainComponent(@Nonnull TerrainColliderRequest request,
        @Nullable String payloadResourceKey,
        boolean retained) {
        return new TerrainColliderComponent(request.spaceUuid(),
            request.sourceKey(),
            request.chunkX(),
            request.sectionY(),
            request.chunkZ(),
            payloadResourceKey != null ? payloadResourceKey : "",
            retained);
    }

    private static void removePayload(@Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nullable String payloadResourceKey) {
        if (payloadResourceKey != null && !payloadResourceKey.isBlank()) {
            terrainPayloads.remove(payloadResourceKey);
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private record JointRow(@Nonnull UUID uuid,
                            @Nonnull Ref<PhysicsStore> ref,
                            @Nonnull JointComponent joint) {
    }

    private record ColliderRow(@Nonnull UUID uuid,
                               @Nonnull Ref<PhysicsStore> ref,
                               @Nonnull ColliderComponent collider) {
    }

    private record RuntimeBodyBinding(@Nonnull BackendSpaceHandle spaceHandle,
                                      @Nonnull BackendBodyHandle bodyHandle,
                                      @Nonnull PhysicsBackendRuntime backendRuntime) {
    }
}
