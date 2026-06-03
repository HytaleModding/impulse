package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Runtime-only helpers for exercising the world-collision subplugin through Hytale.
 */
final class WorldCollisionSubPluginCrucibleSupport {

    private static final PluginIdentifier PLUGIN_ID =
        new PluginIdentifier("HytaleModding", "ImpulseWorldCollision");

    private WorldCollisionSubPluginCrucibleSupport() {
    }

    static boolean ensureLoaded() {
        PluginManager pluginManager = PluginManager.get();
        if (pluginManager.getPlugin(PLUGIN_ID) != null && WorldCollisionLifecycle.isEnabled()) {
            return true;
        }
        return pluginManager.load(PLUGIN_ID) && WorldCollisionLifecycle.isEnabled();
    }

    @Nonnull
    static CompletionStage<CrucibleTestCase.TestOutcome> loadUnloadReloadSmokeAsync() {
        return CompletableFuture.supplyAsync(WorldCollisionSubPluginCrucibleSupport::loadUnloadReloadSmoke)
            .orTimeout(30L, TimeUnit.SECONDS)
            .exceptionally(failure -> CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin lifecycle smoke failed: " + failure.getMessage()));
    }

    private static CrucibleTestCase.TestOutcome loadUnloadReloadSmoke() {
        PluginManager pluginManager = PluginManager.get();
        if (!pluginManager.getAvailablePlugins().containsKey(PLUGIN_ID)
            && pluginManager.getPlugin(PLUGIN_ID) == null) {
            return CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin is not available: " + PLUGIN_ID);
        }

        if (!ensureLoaded()) {
            return CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin load did not enable the lifecycle");
        }
        if (!pluginManager.unload(PLUGIN_ID)) {
            return CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin unload returned false");
        }
        if (pluginManager.getPlugin(PLUGIN_ID) != null) {
            return CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin remained loaded after unload");
        }
        if (WorldCollisionLifecycle.isEnabled()) {
            return CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin unload did not disable the lifecycle");
        }
        boolean loadResult = pluginManager.load(PLUGIN_ID);
        PluginBase loadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
        if (!loadResult || !WorldCollisionLifecycle.isEnabled()) {
            return CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin reload load did not enable the lifecycle: "
                    + "loadResult=" + loadResult
                    + ", pluginState=" + stateOf(loadedPlugin)
                    + ", lifecycleEnabled=" + WorldCollisionLifecycle.isEnabled());
        }
        boolean reloadResult = pluginManager.reload(PLUGIN_ID);
        PluginBase reloadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
        if (!reloadResult || !WorldCollisionLifecycle.isEnabled()) {
            return CrucibleTestCase.TestOutcome.fail(
                "World collision subplugin reload did not leave the lifecycle enabled: "
                    + "reloadResult=" + reloadResult
                    + ", pluginState=" + stateOf(reloadedPlugin)
                    + ", lifecycleEnabled=" + WorldCollisionLifecycle.isEnabled());
        }
        return CrucibleTestCase.TestOutcome.pass();
    }

    @Nonnull
    private static String stateOf(PluginBase plugin) {
        return plugin == null ? "missing" : plugin.getState().name();
    }
}
