package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import lombok.Getter;
import lombok.Setter;
import javax.annotation.Nonnull;

/**
 * World-level PhysicsStore step settings.
 */
public final class PhysicsWorldSettingsResource implements Resource<PhysicsStore> {

    @Nonnull
    private final PhysicsWorldSettings settings = new PhysicsWorldSettings();
    @Setter
    @Getter
    private boolean ccdStepModeActive;

    public PhysicsWorldSettingsResource() {
    }

    @Nonnull
    public PhysicsWorldSettings getSettings() {
        return new PhysicsWorldSettings(settings);
    }

    public void setSettings(@Nonnull PhysicsWorldSettings settings) {
        this.settings.copyFrom(settings);
    }

    @Nonnull
    @Override
    public PhysicsWorldSettingsResource clone() {
        PhysicsWorldSettingsResource copy = new PhysicsWorldSettingsResource();
        copy.setSettings(settings);
        copy.ccdStepModeActive = ccdStepModeActive;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldSettingsResource> getResourceType() {
        return PhysicsResourceTypes.worldSettingsResourceType();
    }
}
