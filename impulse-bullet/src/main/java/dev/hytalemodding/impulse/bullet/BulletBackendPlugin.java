package dev.hytalemodding.impulse.bullet;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.impulse.api.Impulse;
import javax.annotation.Nonnull;

/**
 * Makes the Bullet backend jar a valid Hytale mod-side artifact.
 */
public final class BulletBackendPlugin extends JavaPlugin {

    public BulletBackendPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        Impulse.registerBackend(new BulletBackend());
    }
}
