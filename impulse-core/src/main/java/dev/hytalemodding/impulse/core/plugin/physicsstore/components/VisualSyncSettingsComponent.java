package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored visual synchronization policy for one PhysicsStore space row.
 */
public final class VisualSyncSettingsComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<VisualSyncSettingsComponent> CODEC = BuilderCodec.builder(
            VisualSyncSettingsComponent.class,
            VisualSyncSettingsComponent::new)
        .append(new KeyedCodec<>("VisualFullSyncRadius", Codec.INTEGER, false),
            (component, value) -> component.visualFullSyncRadius = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS,
            VisualSyncSettingsComponent::getVisualFullSyncRadius)
        .add()
        .append(new KeyedCodec<>("VisualMaxSyncRadius", Codec.INTEGER, false),
            (component, value) -> component.visualMaxSyncRadius = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS,
            VisualSyncSettingsComponent::getVisualMaxSyncRadius)
        .add()
        .append(new KeyedCodec<>("VisualFarSyncCutoffEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.visualFarSyncCutoffEnabled = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED,
            VisualSyncSettingsComponent::isVisualFarSyncCutoffEnabled)
        .add()
        .append(new KeyedCodec<>("VisualMidSyncIntervalTicks", Codec.INTEGER, false),
            (component, value) -> component.visualMidSyncIntervalTicks = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS,
            VisualSyncSettingsComponent::getVisualMidSyncIntervalTicks)
        .add()
        .append(new KeyedCodec<>("VisualFarSyncIntervalTicks", Codec.INTEGER, false),
            (component, value) -> component.visualFarSyncIntervalTicks = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS,
            VisualSyncSettingsComponent::getVisualFarSyncIntervalTicks)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionMode",
                new EnumCodec<>(VisualOcclusionMode.class),
                false),
            (component, value) -> component.visualOcclusionMode = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_MODE,
            VisualSyncSettingsComponent::getVisualOcclusionMode)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionRaycastsPerTick", Codec.INTEGER, false),
            (component, value) -> component.visualOcclusionRaycastsPerTick = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_RAYCASTS_PER_TICK,
            VisualSyncSettingsComponent::getVisualOcclusionRaycastsPerTick)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionCacheTicks", Codec.INTEGER, false),
            (component, value) -> component.visualOcclusionCacheTicks = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_CACHE_TICKS,
            VisualSyncSettingsComponent::getVisualOcclusionCacheTicks)
        .add()
        .append(new KeyedCodec<>("VisualSnapshotPredictionEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.visualSnapshotPredictionEnabled = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED,
            VisualSyncSettingsComponent::isVisualSnapshotPredictionEnabled)
        .add()
        .append(new KeyedCodec<>("VisualSnapshotPredictionMaxSeconds", Codec.FLOAT, false),
            (component, value) -> component.visualSnapshotPredictionMaxSeconds = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS,
            VisualSyncSettingsComponent::getVisualSnapshotPredictionMaxSeconds)
        .add()
        .append(new KeyedCodec<>("VisualSnapshotSmoothingEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.visualSnapshotSmoothingEnabled = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED,
            VisualSyncSettingsComponent::isVisualSnapshotSmoothingEnabled)
        .add()
        .append(new KeyedCodec<>("VisualSnapshotSmoothingRate", Codec.FLOAT, false),
            (component, value) -> component.visualSnapshotSmoothingRate = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE,
            VisualSyncSettingsComponent::getVisualSnapshotSmoothingRate)
        .add()
        .append(new KeyedCodec<>("EntityVisualSyncCullingEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.entityVisualSyncCullingEnabled = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED,
            VisualSyncSettingsComponent::isEntityVisualSyncCullingEnabled)
        .add()
        .append(new KeyedCodec<>("VisualVisibilityCullingEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.visualVisibilityCullingEnabled = value != null
                ? value
                : PhysicsVisualSyncSettings.DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED,
            VisualSyncSettingsComponent::isVisualVisibilityCullingEnabled)
        .add()
        .build();

    private int visualFullSyncRadius =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS;
    private int visualMaxSyncRadius =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS;
    private boolean visualFarSyncCutoffEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED;
    private int visualMidSyncIntervalTicks =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS;
    private int visualFarSyncIntervalTicks =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS;
    @Nonnull
    private VisualOcclusionMode visualOcclusionMode =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_MODE;
    private int visualOcclusionRaycastsPerTick =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_RAYCASTS_PER_TICK;
    private int visualOcclusionCacheTicks =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_CACHE_TICKS;
    private boolean visualSnapshotPredictionEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED;
    private float visualSnapshotPredictionMaxSeconds =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS;
    private boolean visualSnapshotSmoothingEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED;
    private float visualSnapshotSmoothingRate =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE;
    private boolean entityVisualSyncCullingEnabled =
        PhysicsVisualSyncSettings.DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED;
    private boolean visualVisibilityCullingEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED;

    public VisualSyncSettingsComponent() {
    }

    public VisualSyncSettingsComponent(@Nonnull PhysicsVisualSyncSettings settings) {
        visualFullSyncRadius = settings.getVisualFullSyncRadius();
        visualMaxSyncRadius = settings.getVisualMaxSyncRadius();
        visualFarSyncCutoffEnabled = settings.isVisualFarSyncCutoffEnabled();
        visualMidSyncIntervalTicks = settings.getVisualMidSyncIntervalTicks();
        visualFarSyncIntervalTicks = settings.getVisualFarSyncIntervalTicks();
        visualOcclusionMode = settings.getVisualOcclusionMode();
        visualOcclusionRaycastsPerTick = settings.getVisualOcclusionRaycastsPerTick();
        visualOcclusionCacheTicks = settings.getVisualOcclusionCacheTicks();
        visualSnapshotPredictionEnabled = settings.isVisualSnapshotPredictionEnabled();
        visualSnapshotPredictionMaxSeconds = settings.getVisualSnapshotPredictionMaxSeconds();
        visualSnapshotSmoothingEnabled = settings.isVisualSnapshotSmoothingEnabled();
        visualSnapshotSmoothingRate = settings.getVisualSnapshotSmoothingRate();
        entityVisualSyncCullingEnabled = settings.isEntityVisualSyncCullingEnabled();
        visualVisibilityCullingEnabled = settings.isVisualVisibilityCullingEnabled();
    }

    private VisualSyncSettingsComponent(int visualFullSyncRadius,
        int visualMaxSyncRadius,
        boolean visualFarSyncCutoffEnabled,
        int visualMidSyncIntervalTicks,
        int visualFarSyncIntervalTicks,
        @Nonnull VisualOcclusionMode visualOcclusionMode,
        int visualOcclusionRaycastsPerTick,
        int visualOcclusionCacheTicks,
        boolean visualSnapshotPredictionEnabled,
        float visualSnapshotPredictionMaxSeconds,
        boolean visualSnapshotSmoothingEnabled,
        float visualSnapshotSmoothingRate,
        boolean entityVisualSyncCullingEnabled,
        boolean visualVisibilityCullingEnabled) {
        this.visualFullSyncRadius = visualFullSyncRadius;
        this.visualMaxSyncRadius = visualMaxSyncRadius;
        this.visualFarSyncCutoffEnabled = visualFarSyncCutoffEnabled;
        this.visualMidSyncIntervalTicks = visualMidSyncIntervalTicks;
        this.visualFarSyncIntervalTicks = visualFarSyncIntervalTicks;
        this.visualOcclusionMode = Objects.requireNonNull(visualOcclusionMode,
            "visualOcclusionMode");
        this.visualOcclusionRaycastsPerTick = visualOcclusionRaycastsPerTick;
        this.visualOcclusionCacheTicks = visualOcclusionCacheTicks;
        this.visualSnapshotPredictionEnabled = visualSnapshotPredictionEnabled;
        this.visualSnapshotPredictionMaxSeconds = visualSnapshotPredictionMaxSeconds;
        this.visualSnapshotSmoothingEnabled = visualSnapshotSmoothingEnabled;
        this.visualSnapshotSmoothingRate = visualSnapshotSmoothingRate;
        this.entityVisualSyncCullingEnabled = entityVisualSyncCullingEnabled;
        this.visualVisibilityCullingEnabled = visualVisibilityCullingEnabled;
    }

    public int getVisualFullSyncRadius() {
        return visualFullSyncRadius;
    }

    public int getVisualMaxSyncRadius() {
        return visualMaxSyncRadius;
    }

    public boolean isVisualFarSyncCutoffEnabled() {
        return visualFarSyncCutoffEnabled;
    }

    public int getVisualMidSyncIntervalTicks() {
        return visualMidSyncIntervalTicks;
    }

    public int getVisualFarSyncIntervalTicks() {
        return visualFarSyncIntervalTicks;
    }

    @Nonnull
    public VisualOcclusionMode getVisualOcclusionMode() {
        return visualOcclusionMode;
    }

    public int getVisualOcclusionRaycastsPerTick() {
        return visualOcclusionRaycastsPerTick;
    }

    public int getVisualOcclusionCacheTicks() {
        return visualOcclusionCacheTicks;
    }

    public boolean isVisualSnapshotPredictionEnabled() {
        return visualSnapshotPredictionEnabled;
    }

    public float getVisualSnapshotPredictionMaxSeconds() {
        return visualSnapshotPredictionMaxSeconds;
    }

    public boolean isVisualSnapshotSmoothingEnabled() {
        return visualSnapshotSmoothingEnabled;
    }

    public float getVisualSnapshotSmoothingRate() {
        return visualSnapshotSmoothingRate;
    }

    public boolean isEntityVisualSyncCullingEnabled() {
        return entityVisualSyncCullingEnabled;
    }

    public boolean isVisualVisibilityCullingEnabled() {
        return visualVisibilityCullingEnabled;
    }

    public void copyTo(@Nonnull PhysicsSpaceSettings settings) {
        PhysicsVisualSyncSettings target = settings.getVisualSyncSettings();
        target.setVisualSyncRadii(visualFullSyncRadius, visualMaxSyncRadius);
        target.setVisualFarSyncCutoffEnabled(visualFarSyncCutoffEnabled);
        target.setVisualMidSyncIntervalTicks(visualMidSyncIntervalTicks);
        target.setVisualFarSyncIntervalTicks(visualFarSyncIntervalTicks);
        target.setVisualOcclusionMode(visualOcclusionMode);
        target.setVisualOcclusionRaycastsPerTick(visualOcclusionRaycastsPerTick);
        target.setVisualOcclusionCacheTicks(visualOcclusionCacheTicks);
        target.setVisualSnapshotPredictionEnabled(visualSnapshotPredictionEnabled);
        target.setVisualSnapshotPredictionMaxSeconds(visualSnapshotPredictionMaxSeconds);
        target.setVisualSnapshotSmoothingEnabled(visualSnapshotSmoothingEnabled);
        target.setVisualSnapshotSmoothingRate(visualSnapshotSmoothingRate);
        target.setEntityVisualSyncCullingEnabled(entityVisualSyncCullingEnabled);
        target.setVisualVisibilityCullingEnabled(visualVisibilityCullingEnabled);
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualSyncSettingsComponent> getComponentType() {
        return PhysicsStoreTypes.visualSyncSettingsComponentType();
    }

    @Nonnull
    @Override
    public VisualSyncSettingsComponent clone() {
        return new VisualSyncSettingsComponent(visualFullSyncRadius,
            visualMaxSyncRadius,
            visualFarSyncCutoffEnabled,
            visualMidSyncIntervalTicks,
            visualFarSyncIntervalTicks,
            visualOcclusionMode,
            visualOcclusionRaycastsPerTick,
            visualOcclusionCacheTicks,
            visualSnapshotPredictionEnabled,
            visualSnapshotPredictionMaxSeconds,
            visualSnapshotSmoothingEnabled,
            visualSnapshotSmoothingRate,
            entityVisualSyncCullingEnabled,
            visualVisibilityCullingEnabled);
    }
}
