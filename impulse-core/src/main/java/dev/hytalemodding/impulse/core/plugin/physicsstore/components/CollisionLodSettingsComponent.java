package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import javax.annotation.Nonnull;

/**
 * Authored collision LOD policy for one PhysicsStore space row.
 */
public final class CollisionLodSettingsComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<CollisionLodSettingsComponent> CODEC = BuilderCodec.builder(
            CollisionLodSettingsComponent.class,
            CollisionLodSettingsComponent::new)
        .append(new KeyedCodec<>("CollisionLodEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.collisionLodEnabled = value != null
                ? value
                : PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_ENABLED,
            CollisionLodSettingsComponent::isCollisionLodEnabled)
        .add()
        .append(new KeyedCodec<>("CollisionLodNearRadius", Codec.INTEGER, false),
            (component, value) -> component.collisionLodNearRadius = value != null
                ? value
                : PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_NEAR_RADIUS,
            CollisionLodSettingsComponent::getCollisionLodNearRadius)
        .add()
        .append(new KeyedCodec<>("CollisionLodMidRadius", Codec.INTEGER, false),
            (component, value) -> component.collisionLodMidRadius = value != null
                ? value
                : PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_MID_RADIUS,
            CollisionLodSettingsComponent::getCollisionLodMidRadius)
        .add()
        .append(new KeyedCodec<>("CollisionLodHysteresis", Codec.INTEGER, false),
            (component, value) -> component.collisionLodHysteresis = value != null
                ? value
                : PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_HYSTERESIS,
            CollisionLodSettingsComponent::getCollisionLodHysteresis)
        .add()
        .append(new KeyedCodec<>("CollisionLodRefreshIntervalTicks", Codec.INTEGER, false),
            (component, value) -> component.collisionLodRefreshIntervalTicks = value != null
                ? value
                : PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS,
            CollisionLodSettingsComponent::getCollisionLodRefreshIntervalTicks)
        .add()
        .append(new KeyedCodec<>("CollisionLodFarSleepEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.collisionLodFarSleepEnabled = value != null
                ? value
                : PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_FAR_SLEEP_ENABLED,
            CollisionLodSettingsComponent::isCollisionLodFarSleepEnabled)
        .add()
        .build();

    private boolean collisionLodEnabled =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_ENABLED;
    private int collisionLodNearRadius =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_NEAR_RADIUS;
    private int collisionLodMidRadius =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_MID_RADIUS;
    private int collisionLodHysteresis =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_HYSTERESIS;
    private int collisionLodRefreshIntervalTicks =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS;
    private boolean collisionLodFarSleepEnabled =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_FAR_SLEEP_ENABLED;

    public CollisionLodSettingsComponent() {
    }

    public CollisionLodSettingsComponent(@Nonnull PhysicsCollisionLodSettings settings) {
        collisionLodEnabled = settings.isCollisionLodEnabled();
        collisionLodNearRadius = settings.getCollisionLodNearRadius();
        collisionLodMidRadius = settings.getCollisionLodMidRadius();
        collisionLodHysteresis = settings.getCollisionLodHysteresis();
        collisionLodRefreshIntervalTicks = settings.getCollisionLodRefreshIntervalTicks();
        collisionLodFarSleepEnabled = settings.isCollisionLodFarSleepEnabled();
    }

    private CollisionLodSettingsComponent(boolean collisionLodEnabled,
        int collisionLodNearRadius,
        int collisionLodMidRadius,
        int collisionLodHysteresis,
        int collisionLodRefreshIntervalTicks,
        boolean collisionLodFarSleepEnabled) {
        this.collisionLodEnabled = collisionLodEnabled;
        this.collisionLodNearRadius = collisionLodNearRadius;
        this.collisionLodMidRadius = collisionLodMidRadius;
        this.collisionLodHysteresis = collisionLodHysteresis;
        this.collisionLodRefreshIntervalTicks = collisionLodRefreshIntervalTicks;
        this.collisionLodFarSleepEnabled = collisionLodFarSleepEnabled;
    }

    public boolean isCollisionLodEnabled() {
        return collisionLodEnabled;
    }

    public int getCollisionLodNearRadius() {
        return collisionLodNearRadius;
    }

    public int getCollisionLodMidRadius() {
        return collisionLodMidRadius;
    }

    public int getCollisionLodHysteresis() {
        return collisionLodHysteresis;
    }

    public int getCollisionLodRefreshIntervalTicks() {
        return collisionLodRefreshIntervalTicks;
    }

    public boolean isCollisionLodFarSleepEnabled() {
        return collisionLodFarSleepEnabled;
    }

    public void copyTo(@Nonnull PhysicsSpaceSettings settings) {
        PhysicsCollisionLodSettings target = settings.getCollisionLodSettings();
        target.setCollisionLodEnabled(collisionLodEnabled);
        target.setCollisionLodRadii(collisionLodNearRadius, collisionLodMidRadius);
        target.setCollisionLodHysteresis(collisionLodHysteresis);
        target.setCollisionLodRefreshIntervalTicks(collisionLodRefreshIntervalTicks);
        target.setCollisionLodFarSleepEnabled(collisionLodFarSleepEnabled);
    }

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionLodSettingsComponent> getComponentType() {
        return PhysicsStoreTypes.collisionLodSettingsComponentType();
    }

    @Nonnull
    @Override
    public CollisionLodSettingsComponent clone() {
        return new CollisionLodSettingsComponent(collisionLodEnabled,
            collisionLodNearRadius,
            collisionLodMidRadius,
            collisionLodHysteresis,
            collisionLodRefreshIntervalTicks,
            collisionLodFarSleepEnabled);
    }
}
