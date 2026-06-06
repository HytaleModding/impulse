package dev.hytalemodding.impulse.core.plugin.settings;

import lombok.Getter;
import lombok.Setter;
import javax.annotation.Nonnull;

/**
 * Distance-based dynamic-body collision LOD settings for a physics space.
 */
@Getter
public class PhysicsCollisionLodSettings {

    /**
     * Whether distance-based dynamic-body collision LOD is active for this space.
     */
    public static final boolean DEFAULT_COLLISION_LOD_ENABLED = false;

    /**
     * Radius where managed dynamic bodies keep full terrain plus dynamic-body collision.
     */
    public static final int DEFAULT_COLLISION_LOD_NEAR_RADIUS = 64;

    /**
     * Radius where managed dynamic bodies keep terrain collision but drop dynamic-body collision.
     */
    public static final int DEFAULT_COLLISION_LOD_MID_RADIUS = 128;

    /**
     * Hard block-radius cap for collision LOD tiers.
     */
    public static final int MAX_COLLISION_LOD_RADIUS = 1_024;

    /**
     * Extra radius used before downgrading an already higher-priority collision tier.
     */
    public static final int DEFAULT_COLLISION_LOD_HYSTERESIS = 16;

    /**
     * Hard block-radius cap for collision LOD hysteresis.
     */
    public static final int MAX_COLLISION_LOD_HYSTERESIS = 256;

    /**
     * Ticks between refreshing distance-based collision LOD decisions.
     */
    public static final int DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS = 10;

    /**
     * Hard tick cap for collision LOD refreshes.
     */
    public static final int MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS = 1_200;

    /**
     * Whether far managed dynamic bodies should be put to sleep after collision is reduced.
     */
    public static final boolean DEFAULT_COLLISION_LOD_FAR_SLEEP_ENABLED = true;

    /**
     * If enabled, default dynamic bodies can reduce dynamic-body collision away from players.
     */
    @Setter
    private boolean collisionLodEnabled = DEFAULT_COLLISION_LOD_ENABLED;

    /**
     * Full-collision radius for collision LOD.
     */
    private int collisionLodNearRadius = DEFAULT_COLLISION_LOD_NEAR_RADIUS;

    /**
     * Terrain-only radius for collision LOD.
     */
    private int collisionLodMidRadius = DEFAULT_COLLISION_LOD_MID_RADIUS;

    /**
     * Downgrade hysteresis for collision LOD.
     */
    private int collisionLodHysteresis = DEFAULT_COLLISION_LOD_HYSTERESIS;

    /**
     * Refresh cadence for collision LOD scans.
     */
    private int collisionLodRefreshIntervalTicks =
        DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS;

    /**
     * If enabled, far collision LOD bodies are put to sleep after collision is reduced.
     */
    @Setter
    private boolean collisionLodFarSleepEnabled = DEFAULT_COLLISION_LOD_FAR_SLEEP_ENABLED;

    public PhysicsCollisionLodSettings() {
    }

    public PhysicsCollisionLodSettings(@Nonnull PhysicsCollisionLodSettings settings) {
        collisionLodEnabled = settings.collisionLodEnabled;
        collisionLodNearRadius = settings.collisionLodNearRadius;
        collisionLodMidRadius = settings.collisionLodMidRadius;
        collisionLodHysteresis = settings.collisionLodHysteresis;
        collisionLodRefreshIntervalTicks = settings.collisionLodRefreshIntervalTicks;
        collisionLodFarSleepEnabled = settings.collisionLodFarSleepEnabled;
    }

    public void setCollisionLodNearRadius(int collisionLodNearRadius) {
        int boundedNearRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Collision LOD near radius",
            collisionLodNearRadius,
            MAX_COLLISION_LOD_RADIUS);
        if (boundedNearRadius > collisionLodMidRadius) {
            throw new IllegalArgumentException(
                "Collision LOD near radius cannot exceed mid radius");
        }
        this.collisionLodNearRadius = boundedNearRadius;
    }

    public void setCollisionLodMidRadius(int collisionLodMidRadius) {
        int boundedMidRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Collision LOD mid radius",
            collisionLodMidRadius,
            MAX_COLLISION_LOD_RADIUS);
        if (boundedMidRadius < collisionLodNearRadius) {
            throw new IllegalArgumentException(
                "Collision LOD mid radius cannot be lower than near radius");
        }
        this.collisionLodMidRadius = boundedMidRadius;
    }

    public void setCollisionLodRadii(int collisionLodNearRadius,
        int collisionLodMidRadius) {
        int boundedNearRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Collision LOD near radius",
            collisionLodNearRadius,
            MAX_COLLISION_LOD_RADIUS);
        int boundedMidRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Collision LOD mid radius",
            collisionLodMidRadius,
            MAX_COLLISION_LOD_RADIUS);
        if (boundedNearRadius > boundedMidRadius) {
            throw new IllegalArgumentException(
                "Collision LOD near radius cannot exceed mid radius");
        }
        this.collisionLodNearRadius = boundedNearRadius;
        this.collisionLodMidRadius = boundedMidRadius;
    }

    public void setCollisionLodHysteresis(int collisionLodHysteresis) {
        if (collisionLodHysteresis < 0
            || collisionLodHysteresis > MAX_COLLISION_LOD_HYSTERESIS) {
            throw new IllegalArgumentException("Collision LOD hysteresis must be between 0 and "
                + MAX_COLLISION_LOD_HYSTERESIS);
        }
        this.collisionLodHysteresis = collisionLodHysteresis;
    }

    public void setCollisionLodRefreshIntervalTicks(int collisionLodRefreshIntervalTicks) {
        this.collisionLodRefreshIntervalTicks =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Collision LOD refresh interval",
                collisionLodRefreshIntervalTicks,
                MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS);
    }

}
