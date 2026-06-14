package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import lombok.Getter;
import lombok.Setter;
import javax.annotation.Nonnull;

/**
 * Runtime-only debug toggles owned by PhysicsStore.
 */
@Setter
@Getter
public final class PhysicsDebugResource implements Resource<PhysicsStore> {

    private boolean debugBodiesEnabled;
    private boolean debugContactsEnabled;
    private boolean debugJointsEnabled;

    public PhysicsDebugResource() {
    }

    @Nonnull
    @Override
    public PhysicsDebugResource clone() {
        PhysicsDebugResource copy = new PhysicsDebugResource();
        copy.debugBodiesEnabled = debugBodiesEnabled;
        copy.debugContactsEnabled = debugContactsEnabled;
        copy.debugJointsEnabled = debugJointsEnabled;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsDebugResource> getResourceType() {
        return PhysicsStoreTypes.debugResourceType();
    }
}
