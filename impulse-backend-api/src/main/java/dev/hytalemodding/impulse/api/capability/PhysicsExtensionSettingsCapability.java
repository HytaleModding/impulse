package dev.hytalemodding.impulse.api.capability;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Optional backend capability for applying capability-keyed extension settings.
 *
 * <p>The setting values are strings so core can persist and forward unknown backend
 * settings without depending on backend-owned Java types.</p>
 */
@Deprecated(forRemoval = true)
public interface PhysicsExtensionSettingsCapability extends PhysicsCapability {

    PhysicsCapabilityDescriptor DESCRIPTOR = new PhysicsCapabilityDescriptor(
        new PhysicsCapabilityId("impulse:extension_settings"),
        "Extension settings",
        "Applies capability-keyed backend extension settings");

    void applyExtensionSettings(@Nonnull PhysicsCapabilityId capabilityId,
        @Nonnull Map<String, String> values);
}
