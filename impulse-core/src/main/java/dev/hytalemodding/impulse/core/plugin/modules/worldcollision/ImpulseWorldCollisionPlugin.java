package dev.hytalemodding.impulse.core.plugin.modules.worldcollision;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse world collision.
 */
public final class ImpulseWorldCollisionPlugin extends JavaPlugin {

    public ImpulseWorldCollisionPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        WorldCollisionLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        WorldCollisionLifecycle.disable();
    }
}
