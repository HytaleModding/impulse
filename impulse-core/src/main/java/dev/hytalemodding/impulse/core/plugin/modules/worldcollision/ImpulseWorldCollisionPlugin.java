package dev.hytalemodding.impulse.core.plugin.modules.worldcollision;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse world collision.
 */
public final class ImpulseWorldCollisionPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    public ImpulseWorldCollisionPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("Impulse world-collision legacy EntityStore systems are "
            + "disabled while authoritative PhysicsStore terrain binding is being migrated.");
    }

    @Override
    protected void shutdown() {
        WorldCollisionLifecycle.disable();
        WorldCollisionProfilingResource.clearResourceType();
    }
}
