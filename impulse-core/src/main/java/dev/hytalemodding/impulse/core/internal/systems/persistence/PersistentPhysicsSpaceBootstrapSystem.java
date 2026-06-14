package dev.hytalemodding.impulse.core.internal.systems.persistence;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRestorePreflight;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreRuntimeCleaner;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerBridge;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * First stage of persistence restore: recreates physics spaces from persisted world state.
 *
 * <p>Runs when {@code runtimeRestorePending} is true
 * (set after Hytale deserializes the resource or after a manual snapshot load). For each
 * persisted space definition, either creates a new runtime space or updates an existing
 * one with the persisted gravity and settings.</p>
 *
 * <p>Persisted spaces bind to explicit backend ids. When a saved backend is not
 * registered at restore time, the restore is treated as a hard failure and the
 * persisted data is left untouched. That behavior keeps backend availability an
 * explicit part of the persistence contract instead of silently changing physics
 * behavior by falling back to a different backend.</p>
 *
 * <p>Runs before body and joint hydration so that target spaces exist when the
 * downstream systems try to add bodies to them. Downstream systems declare
 * {@code AFTER} this system in their dependency sets.</p>
 */
public class PersistentPhysicsSpaceBootstrapSystem extends TickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, GeneratedVisualProxyComponent> GENERATED_PROXY_TYPE =
        GeneratedVisualProxyComponent.getComponentType();

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    @Nonnull
    private final SystemGroup<EntityStore> group = ImpulsePlugin.get().getPersistenceRestoreGroup();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (!persistent.isRuntimeRestorePending()
            || persistent.isRuntimeSpaceBootstrapComplete()
            || persistent.hasRuntimeRestoreFailed()) {
            return;
        }

        World world = store.getExternalData().getWorld();
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(store);
        if (physicsStoreOrNull(store) != null) {
            stripRuntimePhysicsStateForRestore(store, runtime, world);
            persistent.clearRuntimeRestorePending();
            LOGGER.at(Level.INFO).log("Skipped legacy PersistentPhysicsWorldResource restore in "
                + "world %s because authoritative PhysicsStore persistence is active",
                world.getName());
            return;
        }
        PersistentPhysicsSpaceState[] spaces = persistent.getSpaces();
        String validationFailure = PersistentPhysicsRestorePreflight.validate(persistent);
        if (validationFailure != null) {
            persistent.failRuntimeRestore(validationFailure);
            LOGGER.at(Level.SEVERE).log(persistent.runtimeRestoreFailureSummary());
            return;
        }
        stripRuntimePhysicsStateForRestore(store, runtime, world);
        try {
            PhysicsOwnerBridge.run(store, "restore persisted physics runtime settings",
                () -> runtime.setWorldSettings(persistent.getWorldSettings()));
        } catch (RuntimeException exception) {
            runtime.clearAllSpaces(world.getName());
            persistent.failRuntimeRestore("Invalid persisted physics runtime settings: "
                + exception.getMessage());
            LOGGER.at(Level.SEVERE).log(persistent.runtimeRestoreFailureSummary());
            return;
        }

        int restoredSpaceCount = 0;
        for (PersistentPhysicsSpaceState state : spaces) {
            SpaceId spaceId = state.toSpaceId();
            try {
                PhysicsOwnerBridge.run(store, "bootstrap persisted physics space", () -> {
                    PhysicsSpaceBinding targetSpace = runtime.getSpaceBinding(spaceId);
                    if (targetSpace == null) {
                        runtime.createSpace(state.toBackendId(),
                            spaceId,
                            world.getName(),
                            state.toSettings());
                        targetSpace = runtime.getSpaceBinding(spaceId);
                    } else {
                        runtime.setSpaceSettings(spaceId, state.toSettings());
                    }
                    if (targetSpace == null) {
                        throw new IllegalStateException("Physics space id=" + spaceId
                            + " was not registered after creation");
                    }
                    targetSpace.runtime().setGravity(targetSpace.backendSpaceHandle().value(),
                        state.getGravity().x,
                        state.getGravity().y,
                        state.getGravity().z);
                });
            } catch (RuntimeException exception) {
                runtime.clearAllSpaces(world.getName());
                persistent.failRuntimeRestore("Failed to bootstrap space id=" + state.getSpaceId()
                    + " backend=" + state.getBackendId() + ": " + exception.getMessage());
                LOGGER.at(Level.SEVERE).log(
                    "%s Cause: %s",
                    persistent.runtimeRestoreFailureSummary(),
                    exception.getMessage());
                return;
            }
            restoredSpaceCount++;
        }

        persistent.markRuntimeSpaceBootstrapComplete(restoredSpaceCount);
    }

    private static void stripRuntimePhysicsStateForRestore(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource runtime,
        @Nonnull World world) {
        Store<PhysicsStore> physicsStore = physicsStoreOrNull(store);
        if (physicsStore != null) {
            PhysicsStoreRuntimeCleaner.clearAll(physicsStore);
        } else {
            PhysicsOwnerBridge.run(store, "strip runtime physics state for restore", () -> {
                runtime.clearAllSpaces(world.getName());
                runtime.clearBodies();
            });
        }

        store.forEachEntityParallel(ATTACHMENT_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                BodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    ATTACHMENT_TYPE);
                if (attachment == null
                    || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                    return;
                }

                var ref = archetypeChunk.getReferenceTo(index);
                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            });

        store.forEachEntityParallel(GENERATED_PROXY_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, ATTACHMENT_TYPE) != null) {
                    return;
                }

                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        if (PhysicsControlSessionComponent.isComponentTypeRegistered()) {
            ComponentType<EntityStore, PhysicsControlSessionComponent> controlSessionType =
                PhysicsControlSessionComponent.getComponentType();
            store.forEachEntityParallel(controlSessionType,
                (index, archetypeChunk, commandBuffer) -> commandBuffer.removeComponent(
                    archetypeChunk.getReferenceTo(index),
                    controlSessionType));
        }
    }

    private static Store<PhysicsStore> physicsStoreOrNull(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        return world instanceof PhysicsStoreWorld physicsWorld
            ? physicsWorld.getPhysicsStore().getStore()
            : null;
    }

    @Nonnull
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return group;
    }
}
