package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * First stage of persistence restore: recreates physics spaces from persisted world state.
 *
 * <p>Runs when {@link PersistentPhysicsWorldResource#isRuntimeRestorePending()} is true
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

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    @Nonnull
    private final SystemGroup<EntityStore> group = ImpulsePlugin.get().getPersistenceRestoreGroup();

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return group;
    }

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
        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        stripRuntimePhysicsStateForRestore(store, runtime, world);
        try {
            runtime.setSimulationSteps(persistent.getSimulationSteps());
            runtime.setStepMode(persistent.getStepMode());
            runtime.setMaxStepDt(persistent.getMaxStepDt());
        } catch (RuntimeException exception) {
            runtime.clearAllSpaces(world.getName());
            persistent.failRuntimeRestore("Invalid persisted physics runtime settings: "
                + exception.getMessage());
            LOGGER.at(Level.SEVERE).log(persistent.runtimeRestoreFailureSummary());
            return;
        }
        PersistentPhysicsSpaceState[] spaces = persistent.getSpaces();
        String validationFailure = validateSpaces(spaces);
        if (validationFailure != null) {
            persistent.failRuntimeRestore(validationFailure);
            LOGGER.at(Level.SEVERE).log(persistent.runtimeRestoreFailureSummary());
            return;
        }

        int restoredSpaceCount = 0;
        for (PersistentPhysicsSpaceState state : spaces) {
            SpaceId spaceId = state.toSpaceId();
            PhysicsSpace space = runtime.getSpace(spaceId);
            try {
                if (space == null) {
                    space = runtime.createSpace(state.toBackendId(),
                        spaceId,
                        world.getName(),
                        state.toSettings(),
                        persistent.getDefaultSpaceIdValue() != null
                            && persistent.getDefaultSpaceIdValue().equals(spaceId));
                } else {
                    runtime.setSpaceSettings(spaceId, state.toSettings());
                }
                space.setGravity(state.getGravity().x, state.getGravity().y, state.getGravity().z);
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

        try {
            runtime.setDefaultSpaceId(persistent.getDefaultSpaceIdValue());
        } catch (RuntimeException exception) {
            runtime.clearAllSpaces(world.getName());
            persistent.failRuntimeRestore("Invalid persisted default physics space: "
                + exception.getMessage());
            LOGGER.at(Level.SEVERE).log(persistent.runtimeRestoreFailureSummary());
            return;
        }
        persistent.markRuntimeSpaceBootstrapComplete(restoredSpaceCount);
    }

    private static void stripRuntimePhysicsStateForRestore(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource runtime,
        @Nonnull World world) {
        runtime.clearAllSpaces(world.getName());
        runtime.clearBodies();

        store.forEachEntityParallel(PhysicsBodyAttachmentComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                PhysicsBodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    PhysicsBodyAttachmentComponent.getComponentType());
                if (attachment == null
                    || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                    return;
                }

                var ref = archetypeChunk.getReferenceTo(index);
                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            });

        /*
         * Older runtime visual proxies may already exist in saved worlds as plain
         * block entities after their transient visual component is stripped during
         * unload/load. Remove those visual-only leftovers during restore so detached
         * views can be rebuilt from the live physics registry.
         */
        store.forEachEntityParallel(BlockEntity.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, PhysicsBodyAttachmentComponent.getComponentType()) != null) {
                    return;
                }

                BlockEntity blockEntity = archetypeChunk.getComponent(index, BlockEntity.getComponentType());
                if (blockEntity == null
                    || !PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE.equals(blockEntity.getBlockTypeKey())) {
                    return;
                }

                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        store.forEachEntityParallel(PhysicsControlSessionComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> commandBuffer.removeComponent(
                archetypeChunk.getReferenceTo(index),
                PhysicsControlSessionComponent.getComponentType()));
    }

    @Nullable
    private static String validateSpaces(@Nonnull PersistentPhysicsSpaceState[] spaces) {
        for (PersistentPhysicsSpaceState state : spaces) {
            if (state.getSpaceId() <= 0) {
                return "Persisted space id must be positive, found " + state.getSpaceId();
            }

            BackendId backendId = state.toBackendId();
            try {
                Impulse.getBackend(backendId);
            } catch (IllegalStateException exception) {
                return "Saved backend " + backendId + " is not available for restore";
            }
        }
        return null;
    }
}
