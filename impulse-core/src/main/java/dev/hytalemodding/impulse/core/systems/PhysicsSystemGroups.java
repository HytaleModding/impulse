package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared logical groupings for Impulse ECS systems.
 *
 * <p>Some Impulse features run as multi-system phases rather than isolated systems.
 * The persistence restore cycle is one such phase: space bootstrap, body hydration,
 * and joint hydration still order themselves internally, while other systems only
 * need a dependency on the restore phase as a whole.</p>
 */
public final class PhysicsSystemGroups {

    public static final SystemGroup<EntityStore> PERSISTENCE_RESTORE_GROUP =
        EntityStore.REGISTRY.registerSystemGroup();

    private PhysicsSystemGroups() {
    }
}
