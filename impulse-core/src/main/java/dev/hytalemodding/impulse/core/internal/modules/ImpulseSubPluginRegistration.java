package dev.hytalemodding.impulse.core.internal.modules;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginClassLoader;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.pending.PendingLoadJavaPlugin;
import com.hypixel.hytale.server.core.plugin.pending.PendingLoadPlugin;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Registers Impulse same-jar subplugins for dynamic load after Hytale unloads them.
 */
public final class ImpulseSubPluginRegistration {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final String CORE_PLUGINS_FIELD = "corePlugins";

    private ImpulseSubPluginRegistration() {
    }

    public static void register(@Nonnull JavaPlugin parentPlugin) {
        try {
            PluginManager pluginManager = PluginManager.get();
            List<PendingLoadPlugin> corePlugins = getCorePlugins(pluginManager);
            for (PluginManifest subPluginManifest :
                prepareSubPluginManifests(parentPlugin.getManifest())) {
                PluginIdentifier subPluginId = new PluginIdentifier(subPluginManifest);
                if (hasPendingPlugin(corePlugins, subPluginId)) {
                    continue;
                }
                corePlugins.add(createPendingSubPlugin(pluginManager,
                    parentPlugin,
                    subPluginId,
                    subPluginManifest));
            }
        } catch (ReflectiveOperationException | MalformedURLException | RuntimeException exception) {
            LOGGER.at(Level.WARNING).log(
                "Failed to register Impulse subplugins for dynamic load: %s",
                exception.getMessage());
        }
    }

    @Nonnull
    static List<PluginManifest> prepareSubPluginManifests(@Nonnull PluginManifest parentManifest) {
        List<PluginManifest> prepared = new ArrayList<>();
        for (PluginManifest subPluginManifest : parentManifest.getSubPlugins()) {
            subPluginManifest.inherit(parentManifest);
            prepared.add(subPluginManifest);
        }
        return prepared;
    }

    private static boolean hasPendingPlugin(@Nonnull List<PendingLoadPlugin> pendingPlugins,
        @Nonnull PluginIdentifier pluginId) {
        for (PendingLoadPlugin pendingPlugin : pendingPlugins) {
            if (pluginId.equals(pendingPlugin.getIdentifier())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static PendingLoadJavaPlugin createPendingSubPlugin(@Nonnull PluginManager pluginManager,
        @Nonnull JavaPlugin parentPlugin,
        @Nonnull PluginIdentifier subPluginId,
        @Nonnull PluginManifest subPluginManifest)
        throws MalformedURLException {

        Path pluginFile = parentPlugin.getFile();
        URL[] urls = new URL[] {pluginFile.toUri().toURL()};
        PluginClassLoader classLoader = new PluginClassLoader(pluginManager,
            subPluginId,
            true,
            urls);
        return new PendingLoadJavaPlugin(pluginFile, subPluginManifest, classLoader);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static List<PendingLoadPlugin> getCorePlugins(@Nonnull PluginManager pluginManager)
        throws ReflectiveOperationException {

        Field corePluginsField = PluginManager.class.getDeclaredField(CORE_PLUGINS_FIELD);
        corePluginsField.setAccessible(true);
        return (List<PendingLoadPlugin>) corePluginsField.get(pluginManager);
    }
}
