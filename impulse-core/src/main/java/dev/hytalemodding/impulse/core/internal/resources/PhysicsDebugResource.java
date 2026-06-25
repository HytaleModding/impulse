package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import lombok.Getter;
import lombok.Setter;
import javax.annotation.Nonnull;

/**
 * Runtime-only physics debug toggles owned by PhysicsStore.
 */
@Setter
@Getter
public final class PhysicsDebugResource implements Resource<PhysicsStore> {

    private boolean debugShapesEnabled = true;
    private boolean debugMotionEnabled = true;
    private boolean debugContactsEnabled;
    private boolean debugJointsEnabled = true;
    private boolean debugPhysicsChunkCollisionEnabled;

    public PhysicsDebugResource() {
    }

    @Nonnull
    @Override
    public PhysicsDebugResource clone() {
        PhysicsDebugResource copy = new PhysicsDebugResource();
        copy.debugShapesEnabled = debugShapesEnabled;
        copy.debugMotionEnabled = debugMotionEnabled;
        copy.debugContactsEnabled = debugContactsEnabled;
        copy.debugJointsEnabled = debugJointsEnabled;
        copy.debugPhysicsChunkCollisionEnabled = debugPhysicsChunkCollisionEnabled;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsDebugResource> getResourceType() {
        return PhysicsResourceTypes.debugResourceType();
    }
}
