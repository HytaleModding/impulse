package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored detached visual materialization policy for one PhysicsStore space row.
 */
public final class VisualMaterializationSettingsComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<VisualMaterializationSettingsComponent> CODEC =
        BuilderCodec.builder(VisualMaterializationSettingsComponent.class,
                VisualMaterializationSettingsComponent::new)
            .append(new KeyedCodec<>("DetachedVisualMaterializationEnabled", Codec.BOOLEAN, false),
                (component, value) -> component.detachedVisualMaterializationEnabled =
                    value != null
                        ? value
                        : PhysicsVisualMaterializationSettings
                            .DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED,
                VisualMaterializationSettingsComponent::isDetachedVisualMaterializationEnabled)
            .add()
            .append(new KeyedCodec<>("DetachedVisualMaterializationRadius", Codec.INTEGER, false),
                (component, value) -> component.detachedVisualMaterializationRadius =
                    value != null
                        ? value
                        : PhysicsVisualMaterializationSettings
                            .DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS,
                VisualMaterializationSettingsComponent::getDetachedVisualMaterializationRadius)
            .add()
            .append(new KeyedCodec<>("DetachedVisualDematerializationRadius", Codec.INTEGER, false),
                (component, value) -> component.detachedVisualDematerializationRadius =
                    value != null
                        ? value
                        : PhysicsVisualMaterializationSettings
                            .DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS,
                VisualMaterializationSettingsComponent::getDetachedVisualDematerializationRadius)
            .add()
            .append(new KeyedCodec<>("DetachedVisualMaxSpawnsPerTick", Codec.INTEGER, false),
                (component, value) -> component.detachedVisualMaxSpawnsPerTick = value != null
                    ? value
                    : PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK,
                VisualMaterializationSettingsComponent::getDetachedVisualMaxSpawnsPerTick)
            .add()
            .append(new KeyedCodec<>("DetachedVisualMaxMaterialized", Codec.INTEGER, false),
                (component, value) -> component.detachedVisualMaxMaterialized = value != null
                    ? value
                    : PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED,
                VisualMaterializationSettingsComponent::getDetachedVisualMaxMaterialized)
            .add()
            .append(new KeyedCodec<>("DetachedVisualInterestRefreshIntervalTicks", Codec.INTEGER, false),
                (component, value) -> component.detachedVisualInterestRefreshIntervalTicks =
                    value != null
                        ? value
                        : PhysicsVisualMaterializationSettings
                            .DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS,
                VisualMaterializationSettingsComponent::getDetachedVisualInterestRefreshIntervalTicks)
            .add()
            .append(new KeyedCodec<>("DetachedVisualCandidateRefreshIntervalTicks", Codec.INTEGER, false),
                (component, value) -> component.detachedVisualCandidateRefreshIntervalTicks =
                    value != null
                        ? value
                        : PhysicsVisualMaterializationSettings
                            .DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS,
                VisualMaterializationSettingsComponent::getDetachedVisualCandidateRefreshIntervalTicks)
            .add()
            .append(new KeyedCodec<>("DetachedVisualVisibilityCheckIntervalTicks", Codec.INTEGER, false),
                (component, value) -> component.detachedVisualVisibilityCheckIntervalTicks =
                    value != null
                        ? value
                        : PhysicsVisualMaterializationSettings
                            .DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS,
                VisualMaterializationSettingsComponent::getDetachedVisualVisibilityCheckIntervalTicks)
            .add()
            .append(new KeyedCodec<>("DetachedVisualBlockType", Codec.STRING, false),
                (component, value) -> component.detachedVisualBlockType = value != null
                    ? value
                    : PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE,
                VisualMaterializationSettingsComponent::getDetachedVisualBlockType)
            .add()
            .build();

    private boolean detachedVisualMaterializationEnabled =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED;
    private int detachedVisualMaterializationRadius =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS;
    private int detachedVisualDematerializationRadius =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS;
    private int detachedVisualMaxSpawnsPerTick =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK;
    private int detachedVisualMaxMaterialized =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED;
    private int detachedVisualInterestRefreshIntervalTicks =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS;
    private int detachedVisualCandidateRefreshIntervalTicks =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS;
    private int detachedVisualVisibilityCheckIntervalTicks =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS;
    @Nonnull
    private String detachedVisualBlockType =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;

    public VisualMaterializationSettingsComponent() {
    }

    public VisualMaterializationSettingsComponent(
        @Nonnull PhysicsVisualMaterializationSettings settings) {
        detachedVisualMaterializationEnabled =
            settings.isDetachedVisualMaterializationEnabled();
        detachedVisualMaterializationRadius =
            settings.getDetachedVisualMaterializationRadius();
        detachedVisualDematerializationRadius =
            settings.getDetachedVisualDematerializationRadius();
        detachedVisualMaxSpawnsPerTick = settings.getDetachedVisualMaxSpawnsPerTick();
        detachedVisualMaxMaterialized = settings.getDetachedVisualMaxMaterialized();
        detachedVisualInterestRefreshIntervalTicks =
            settings.getDetachedVisualInterestRefreshIntervalTicks();
        detachedVisualCandidateRefreshIntervalTicks =
            settings.getDetachedVisualCandidateRefreshIntervalTicks();
        detachedVisualVisibilityCheckIntervalTicks =
            settings.getDetachedVisualVisibilityCheckIntervalTicks();
        detachedVisualBlockType = settings.getDetachedVisualBlockType();
    }

    private VisualMaterializationSettingsComponent(boolean detachedVisualMaterializationEnabled,
        int detachedVisualMaterializationRadius,
        int detachedVisualDematerializationRadius,
        int detachedVisualMaxSpawnsPerTick,
        int detachedVisualMaxMaterialized,
        int detachedVisualInterestRefreshIntervalTicks,
        int detachedVisualCandidateRefreshIntervalTicks,
        int detachedVisualVisibilityCheckIntervalTicks,
        @Nonnull String detachedVisualBlockType) {
        this.detachedVisualMaterializationEnabled = detachedVisualMaterializationEnabled;
        this.detachedVisualMaterializationRadius = detachedVisualMaterializationRadius;
        this.detachedVisualDematerializationRadius = detachedVisualDematerializationRadius;
        this.detachedVisualMaxSpawnsPerTick = detachedVisualMaxSpawnsPerTick;
        this.detachedVisualMaxMaterialized = detachedVisualMaxMaterialized;
        this.detachedVisualInterestRefreshIntervalTicks = detachedVisualInterestRefreshIntervalTicks;
        this.detachedVisualCandidateRefreshIntervalTicks =
            detachedVisualCandidateRefreshIntervalTicks;
        this.detachedVisualVisibilityCheckIntervalTicks =
            detachedVisualVisibilityCheckIntervalTicks;
        this.detachedVisualBlockType = Objects.requireNonNull(detachedVisualBlockType,
            "detachedVisualBlockType");
    }

    public boolean isDetachedVisualMaterializationEnabled() {
        return detachedVisualMaterializationEnabled;
    }

    public int getDetachedVisualMaterializationRadius() {
        return detachedVisualMaterializationRadius;
    }

    public int getDetachedVisualDematerializationRadius() {
        return detachedVisualDematerializationRadius;
    }

    public int getDetachedVisualMaxSpawnsPerTick() {
        return detachedVisualMaxSpawnsPerTick;
    }

    public int getDetachedVisualMaxMaterialized() {
        return detachedVisualMaxMaterialized;
    }

    public int getDetachedVisualInterestRefreshIntervalTicks() {
        return detachedVisualInterestRefreshIntervalTicks;
    }

    public int getDetachedVisualCandidateRefreshIntervalTicks() {
        return detachedVisualCandidateRefreshIntervalTicks;
    }

    public int getDetachedVisualVisibilityCheckIntervalTicks() {
        return detachedVisualVisibilityCheckIntervalTicks;
    }

    @Nonnull
    public String getDetachedVisualBlockType() {
        return detachedVisualBlockType;
    }

    public void copyTo(@Nonnull PhysicsSpaceSettings settings) {
        PhysicsVisualMaterializationSettings target =
            settings.getVisualMaterializationSettings();
        target.setDetachedVisualMaterializationEnabled(detachedVisualMaterializationEnabled);
        target.setDetachedVisualRadii(detachedVisualMaterializationRadius,
            detachedVisualDematerializationRadius);
        target.setDetachedVisualMaxSpawnsPerTick(detachedVisualMaxSpawnsPerTick);
        target.setDetachedVisualMaxMaterialized(detachedVisualMaxMaterialized);
        target.setDetachedVisualInterestRefreshIntervalTicks(
            detachedVisualInterestRefreshIntervalTicks);
        target.setDetachedVisualCandidateRefreshIntervalTicks(
            detachedVisualCandidateRefreshIntervalTicks);
        target.setDetachedVisualVisibilityCheckIntervalTicks(
            detachedVisualVisibilityCheckIntervalTicks);
        target.setDetachedVisualBlockType(detachedVisualBlockType);
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualMaterializationSettingsComponent>
    getComponentType() {
        return PhysicsStoreTypes.visualMaterializationSettingsComponentType();
    }

    @Nonnull
    @Override
    public VisualMaterializationSettingsComponent clone() {
        return new VisualMaterializationSettingsComponent(
            detachedVisualMaterializationEnabled,
            detachedVisualMaterializationRadius,
            detachedVisualDematerializationRadius,
            detachedVisualMaxSpawnsPerTick,
            detachedVisualMaxMaterialized,
            detachedVisualInterestRefreshIntervalTicks,
            detachedVisualCandidateRefreshIntervalTicks,
            detachedVisualVisibilityCheckIntervalTicks,
            detachedVisualBlockType);
    }
}
