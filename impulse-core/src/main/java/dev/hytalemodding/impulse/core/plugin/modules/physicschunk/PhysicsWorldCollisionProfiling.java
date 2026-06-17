package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @deprecated Use {@link PhysicsChunkTerrainProfiling}.
 */
@Deprecated(forRemoval = false)
public final class PhysicsWorldCollisionProfiling {

    private PhysicsWorldCollisionProfiling() {
    }

    public static boolean isRuntimeProfilingEnabled(@Nonnull Store<EntityStore> store) {
        return PhysicsChunkTerrainProfiling.isRuntimeProfilingEnabled(store);
    }

    public static void setRuntimeProfilingEnabled(@Nonnull World world,
        @Nonnull Store<EntityStore> store,
        boolean enabled) {
        PhysicsChunkTerrainProfiling.setRuntimeProfilingEnabled(world, store, enabled);
    }

    public static void resetRuntimeProfiling(@Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsChunkTerrainProfiling.resetRuntimeProfiling(world, store);
    }

    @Nonnull
    public static Snapshots snapshots(@Nonnull Store<EntityStore> store) {
        return new Snapshots(PhysicsChunkTerrainProfiling.snapshots(store));
    }

    @Nonnull
    public static List<MissingSectionSampleView> missingSectionSamples(
        @Nonnull SnapshotView snapshot) {
        return PhysicsChunkTerrainProfiling.missingSectionSamples(snapshot)
            .stream()
            .map(MissingSectionSampleView::new)
            .toList();
    }

    public record Snapshots(@Nonnull SnapshotView cumulative,
                            @Nonnull SnapshotView latest,
                            @Nonnull SnapshotView worst,
                            boolean enabled) {

        private Snapshots(@Nonnull PhysicsChunkTerrainProfiling.Snapshots snapshots) {
            this(new SnapshotView(snapshots.cumulative()),
                new SnapshotView(snapshots.latest()),
                new SnapshotView(snapshots.worst()),
                snapshots.enabled());
        }
    }

    public static final class SnapshotView extends PhysicsChunkTerrainProfiling.SnapshotView {

        private SnapshotView(@Nonnull PhysicsChunkTerrainProfiling.SnapshotView view) {
            super(view.rawSnapshot());
        }
    }

    public record MissingSectionSampleView(int chunkX,
                                           int sectionY,
                                           int chunkZ,
                                           @Nonnull String reason,
                                           @Nonnull String retainedEnvelopeStatus,
                                           @Nonnull String targetType,
                                           @Nullable UUID bodyUuid,
                                           @Nullable String snapshotPosition,
                                           @Nullable String livePosition) {

        private MissingSectionSampleView(
            @Nonnull PhysicsChunkTerrainProfiling.MissingSectionSampleView sample) {
            this(sample.chunkX(),
                sample.sectionY(),
                sample.chunkZ(),
                sample.reason(),
                sample.retainedEnvelopeStatus(),
                sample.targetType(),
                sample.bodyUuid(),
                sample.snapshotPosition(),
                sample.livePosition());
        }
    }
}
