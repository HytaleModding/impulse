package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsWorldResourceAttachmentSystem;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsStoreEventPublicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsBodyAttachmentIndexSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsGeneratedProxyCleanupSystem;
import javax.annotation.Nonnull;

/**
 * Subplugin that integrates authoritative PhysicsStore bodies with EntityStore entities.
 */
public final class ImpulsePhysicsEntityPlugin extends JavaPlugin {

    public ImpulsePhysicsEntityPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        PhysicsEntityTypes.registerComponentTypes(entityRegistry);
        PhysicsEntityTypes.registerResourceTypes(entityRegistry);
        PhysicsEntityTypes.registerEventTypes(entityRegistry);
        PhysicsEntityTypes.registerSystemGroups(entityRegistry);
        entityRegistry.registerSystem(new PhysicsBodyAttachmentIndexSystem());
        entityRegistry.registerSystem(new PhysicsGeneratedProxyCleanupSystem());
        entityRegistry.registerSystem(new PhysicsSyncSystem());
        entityRegistry.registerSystem(new PhysicsDebugSystem());
        entityRegistry.registerSystem(new PhysicsStoreEventPublicationSystem());
        entityRegistry.registerSystem(new PhysicsWorldResourceAttachmentSystem());
    }
}
