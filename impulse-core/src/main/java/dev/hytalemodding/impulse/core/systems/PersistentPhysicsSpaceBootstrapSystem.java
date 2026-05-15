package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * First stage of persistence restore: recreates physics spaces from persisted world state.
 *
 * <p>Runs when {@link PersistentPhysicsWorldResource#isRuntimeRestorePending()} is true
 * (set after Hytale deserializes the resource or after a manual snapshot load). For each
 * persisted space definition, either creates a new runtime space or updates an existing
 * one with the persisted gravity and settings.</p>
 *
 * <p>Runs before body and joint hydration so that target spaces exist when the
 * downstream systems try to add bodies to them. Downstream systems declare
 * {@code AFTER} this system in their dependency sets.</p>
 */
public class PersistentPhysicsSpaceBootstrapSystem extends TickingSystem<EntityStore> {

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (!persistent.isRuntimeRestorePending()) {
            return;
        }

        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        runtime.setSimulationSteps(persistent.getSimulationSteps());
        World world = store.getExternalData().getWorld();
        for (PersistentPhysicsSpaceState state : persistent.getSpaces()) {
            SpaceId spaceId = state.toSpaceId();
            PhysicsSpace space = runtime.getSpace(spaceId);
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
        }

        runtime.setDefaultSpaceId(persistent.getDefaultSpaceIdValue());
    }
}
