package dev.hytalemodding.impulse.rapier;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.impulse.api.Impulse;
import javax.annotation.Nonnull;

/**
 * Makes the Rapier backend jar a valid Hytale mod-side artifact.
 */
public final class RapierBackendPlugin extends JavaPlugin {

    public RapierBackendPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        Impulse.registerBackend(new RapierBackend());
    }
}
