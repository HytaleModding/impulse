package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntityLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Runtime-only helpers for exercising the PhysicsEntity subplugin through Hytale.
 */
final class PhysicsEntitySubPluginCrucibleSupport {

    private static final PluginIdentifier PLUGIN_ID =
        new PluginIdentifier("HytaleModding", "ImpulsePhysicsEntity");
    private static final long SMOKE_TIMEOUT_SECONDS = 30L;

    private PhysicsEntitySubPluginCrucibleSupport() {
    }

    @Nonnull
    static CompletionStage<CrucibleTestCase.TestOutcome> loadUnloadReloadSmokeAsync() {
        return CompletableFuture.supplyAsync(PhysicsEntitySubPluginCrucibleSupport::loadUnloadReloadSmoke)
            .orTimeout(SMOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(failure -> CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin lifecycle smoke failed: " + failure.getMessage()));
    }

    private static CrucibleTestCase.TestOutcome loadUnloadReloadSmoke() {
        PluginManager pluginManager = PluginManager.get();
        if (!pluginManager.getAvailablePlugins().containsKey(PLUGIN_ID)
            && pluginManager.getPlugin(PLUGIN_ID) == null) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin is not available: " + PLUGIN_ID);
        }

        if (!ensureLoaded(pluginManager)) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin load did not enable the lifecycle");
        }
        if (!pluginManager.unload(PLUGIN_ID)) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin unload returned false");
        }
        if (pluginManager.getPlugin(PLUGIN_ID) != null) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin remained loaded after unload");
        }
        if (isAvailable()) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin unload did not disable availability: "
                    + availabilityState());
        }
        boolean loadResult = pluginManager.load(PLUGIN_ID);
        PluginBase loadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
        if (!loadResult || !isAvailable()) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin reload load did not enable availability: "
                    + "loadResult=" + loadResult
                    + ", pluginState=" + stateOf(loadedPlugin)
                    + ", " + availabilityState());
        }
        boolean reloadResult = pluginManager.reload(PLUGIN_ID);
        PluginBase reloadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
        if (!reloadResult || !isAvailable()) {
            return CrucibleTestCase.TestOutcome.fail(
                "PhysicsEntity subplugin reload did not leave availability enabled: "
                    + "reloadResult=" + reloadResult
                    + ", pluginState=" + stateOf(reloadedPlugin)
                    + ", " + availabilityState());
        }
        return CrucibleTestCase.TestOutcome.pass();
    }

    private static boolean ensureLoaded(@Nonnull PluginManager pluginManager) {
        if (pluginManager.getPlugin(PLUGIN_ID) != null && isAvailable()) {
            return true;
        }
        return pluginManager.load(PLUGIN_ID) && isAvailable();
    }

    private static boolean isAvailable() {
        return PhysicsEntityLifecycle.isEnabled()
            && PhysicsEntityTypes.areEntityStoreTypesRegistered()
            && BodyAttachmentComponent.isComponentTypeRegistered()
            && GeneratedVisualProxyComponent.isComponentTypeRegistered()
            && PhysicsEntityAttachments.isAvailable();
    }

    @Nonnull
    private static String availabilityState() {
        return "lifecycleEnabled=" + PhysicsEntityLifecycle.isEnabled()
            + ", typesRegistered=" + PhysicsEntityTypes.areEntityStoreTypesRegistered()
            + ", bodyAttachmentRegistered="
            + BodyAttachmentComponent.isComponentTypeRegistered()
            + ", generatedVisualProxyRegistered="
            + GeneratedVisualProxyComponent.isComponentTypeRegistered()
            + ", attachmentsAvailable=" + PhysicsEntityAttachments.isAvailable();
    }

    @Nonnull
    private static String stateOf(PluginBase plugin) {
        return plugin == null ? "missing" : plugin.getState().name();
    }
}
