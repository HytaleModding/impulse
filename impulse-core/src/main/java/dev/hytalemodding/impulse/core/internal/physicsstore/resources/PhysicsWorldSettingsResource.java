package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import javax.annotation.Nonnull;

/**
 * World-level PhysicsStore step settings.
 */
public final class PhysicsWorldSettingsResource implements Resource<PhysicsStore> {

    @Nonnull
    private final PhysicsWorldSettings settings = new PhysicsWorldSettings();

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
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldSettingsResource> getResourceType() {
        return PhysicsStoreTypes.worldSettingsResourceType();
    }
}
