package dev.hytalemodding.impulse.api.runtime;

import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Enumerates backend extension settings without exposing a map on the runtime port.
 */
@FunctionalInterface
public interface BackendExtensionSettingsSource {

    void forEachSetting(@Nonnull BiConsumer<String, String> consumer);
}
