package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Runtime-only helpers for exercising the PhysicsChunk subplugin through Hytale.
 */
final class PhysicsChunkSubPluginCrucibleSupport {

    private static final PluginIdentifier PLUGIN_ID =
        new PluginIdentifier("HytaleModding", "ImpulsePhysicsChunk");

    private PhysicsChunkSubPluginCrucibleSupport() {
    }

    static boolean ensureLoaded() {
        PluginManager pluginManager = PluginManager.get();
        if (pluginManager.getPlugin(PLUGIN_ID) != null && PhysicsChunkLifecycle.isEnabled()) {
            return true;
        }
        return pluginManager.load(PLUGIN_ID) && PhysicsChunkLifecycle.isEnabled();
    }

    @Nonnull
    static CompletionStage<CrucibleTestCase.TestOutcome> loadUnloadReloadSmokeAsync() {
        return CompletableFuture.supplyAsync(PhysicsChunkSubPluginCrucibleSupport::loadUnloadReloadSmoke)
            .orTimeout(30L, TimeUnit.SECONDS)
            .exceptionally(failure -> CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin lifecycle smoke failed: " + failure.getMessage()));
    }

    private static CrucibleTestCase.TestOutcome loadUnloadReloadSmoke() {
        PluginManager pluginManager = PluginManager.get();
        if (!pluginManager.getAvailablePlugins().containsKey(PLUGIN_ID)
            && pluginManager.getPlugin(PLUGIN_ID) == null) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin is not available: " + PLUGIN_ID);
        }

        if (!ensureLoaded()) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin load did not enable the lifecycle");
        }
        if (!pluginManager.unload(PLUGIN_ID)) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin unload returned false");
        }
        if (pluginManager.getPlugin(PLUGIN_ID) != null) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin remained loaded after unload");
        }
        if (PhysicsChunkLifecycle.isEnabled()) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin unload did not disable the lifecycle");
        }
        boolean loadResult = pluginManager.load(PLUGIN_ID);
        PluginBase loadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
        if (!loadResult || !PhysicsChunkLifecycle.isEnabled()) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin reload load did not enable the lifecycle: "
                    + "loadResult=" + loadResult
                    + ", pluginState=" + stateOf(loadedPlugin)
                    + ", lifecycleEnabled=" + PhysicsChunkLifecycle.isEnabled());
        }
        boolean reloadResult = pluginManager.reload(PLUGIN_ID);
        PluginBase reloadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
        if (!reloadResult || !PhysicsChunkLifecycle.isEnabled()) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsChunk subplugin reload did not leave the lifecycle enabled: "
                    + "reloadResult=" + reloadResult
                    + ", pluginState=" + stateOf(reloadedPlugin)
                    + ", lifecycleEnabled=" + PhysicsChunkLifecycle.isEnabled());
        }
        return CrucibleTestCase.TestOutcome.pass();
    }

    @Nonnull
    private static String stateOf(PluginBase plugin) {
        return plugin == null ? "missing" : plugin.getState().name();
    }
}
