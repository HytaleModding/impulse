package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Runtime-only helpers for exercising the control subplugin through Hytale.
 */
final class ControlSubPluginCrucibleSupport {

    private static final PluginIdentifier PLUGIN_ID =
        new PluginIdentifier("HytaleModding", "ImpulseControl");
    private static final String DEFAULT_BLOCK_TYPE = "Rock_Stone";
    private static final long SMOKE_TIMEOUT_SECONDS = 30L;
    private static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE =
        DespawnComponent.getComponentType();

    private ControlSubPluginCrucibleSupport() {
    }

    @Nonnull
    static CompletionStage<CrucibleTestCase.TestOutcome> loadUnloadReloadSmokeAsync(
        @Nonnull CrucibleContext context) {
        return CompletableFuture.supplyAsync(() -> loadUnloadReloadSmoke(context))
            .orTimeout(SMOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(failure -> CrucibleTestCase.TestOutcome.fail(
                "Control subplugin lifecycle smoke failed: " + failure.getMessage()));
    }

    private static CrucibleTestCase.TestOutcome loadUnloadReloadSmoke(
        @Nonnull CrucibleContext context) {
        try {
            PluginManager pluginManager = PluginManager.get();
            if (!pluginManager.getAvailablePlugins().containsKey(PLUGIN_ID)
                && pluginManager.getPlugin(PLUGIN_ID) == null) {
                return CrucibleTestCase.TestOutcome.fail(
                    "Control subplugin is not available: " + PLUGIN_ID);
            }

            if (!ensureLoaded(pluginManager)) {
                return CrucibleTestCase.TestOutcome.fail(
                    "Control subplugin load did not enable the lifecycle");
            }
            Ref<EntityStore> controllableRef = addControllableEntity(context);
            if (!controllableRef.isValid()) {
                return CrucibleTestCase.TestOutcome.fail("Failed to add controllable test entity");
            }
            if (!pluginManager.unload(PLUGIN_ID)) {
                return CrucibleTestCase.TestOutcome.fail(
                    "Control subplugin unload returned false");
            }
            if (pluginManager.getPlugin(PLUGIN_ID) != null) {
                return CrucibleTestCase.TestOutcome.fail(
                    "Control subplugin remained loaded after unload");
            }
            if (ControlLifecycle.isEnabled()) {
                return CrucibleTestCase.TestOutcome.fail(
                    "Control subplugin unload did not disable the lifecycle");
            }
            boolean loadResult = pluginManager.load(PLUGIN_ID);
            PluginBase loadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
            if (!loadResult || !ControlLifecycle.isEnabled()) {
                return CrucibleTestCase.TestOutcome.fail(
                    "Control subplugin reload load did not enable the lifecycle: "
                        + "loadResult=" + loadResult
                        + ", pluginState=" + stateOf(loadedPlugin)
                        + ", lifecycleEnabled=" + ControlLifecycle.isEnabled());
            }
            boolean reloadResult = pluginManager.reload(PLUGIN_ID);
            PluginBase reloadedPlugin = pluginManager.getPlugin(PLUGIN_ID);
            if (!reloadResult || !ControlLifecycle.isEnabled()) {
                return CrucibleTestCase.TestOutcome.fail(
                    "Control subplugin reload did not leave the lifecycle enabled: "
                        + "reloadResult=" + reloadResult
                        + ", pluginState=" + stateOf(reloadedPlugin)
                        + ", lifecycleEnabled=" + ControlLifecycle.isEnabled());
            }
            return CrucibleTestCase.TestOutcome.pass();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return CrucibleTestCase.TestOutcome.fail(exception.getMessage());
        }
    }

    private static boolean ensureLoaded(@Nonnull PluginManager pluginManager) {
        if (pluginManager.getPlugin(PLUGIN_ID) != null && ControlLifecycle.isEnabled()) {
            return true;
        }
        return pluginManager.load(PLUGIN_ID) && ControlLifecycle.isEnabled();
    }

    @Nonnull
    private static Ref<EntityStore> addControllableEntity(@Nonnull CrucibleContext context)
        throws ReflectiveOperationException {
        World world = context.world();
        if (world.isInThread()) {
            return addControllableEntityOnWorldThread(context, world);
        }

        CompletableFuture<Ref<EntityStore>> entity = new CompletableFuture<>();
        try {
            world.execute(() -> {
                try {
                    entity.complete(addControllableEntityOnWorldThread(context, world));
                } catch (Throwable throwable) {
                    entity.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            entity.completeExceptionally(exception);
        }

        try {
            return entity.orTimeout(SMOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            throw exception;
        }
    }

    @Nonnull
    private static Ref<EntityStore> addControllableEntityOnWorldThread(
        @Nonnull CrucibleContext context,
        @Nonnull World world) throws ReflectiveOperationException {
        Store<EntityStore> store = world.getEntityStore().getStore();
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(context.wx(0), context.wy(8), context.wz(0)));
        holder.removeComponent(DESPAWN_TYPE);
        holder.addComponent(ImpulseControllableComponent.getComponentType(),
            new ImpulseControllableComponent());
        holder.addComponent(PhysicsControlSessionComponent.getComponentType(),
            new PhysicsControlSessionComponent());
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    private static String stateOf(PluginBase plugin) {
        return plugin == null ? "missing" : plugin.getState().name();
    }
}
