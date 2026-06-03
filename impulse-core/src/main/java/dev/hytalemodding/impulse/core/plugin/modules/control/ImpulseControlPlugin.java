package dev.hytalemodding.impulse.core.plugin.modules.control;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse kinematic control sessions.
 */
public final class ImpulseControlPlugin extends JavaPlugin {

    public ImpulseControlPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ControlLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        ControlLifecycle.disable();
    }
}
