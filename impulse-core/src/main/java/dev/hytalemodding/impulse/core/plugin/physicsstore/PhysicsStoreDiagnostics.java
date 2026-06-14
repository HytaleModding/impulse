package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStoreReadQueueResource;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilitySummary;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/**
 * Diagnostics for live PhysicsStore backend state.
 *
 * <p>The synchronous methods read mutable runtime/backend state and must only run from the
 * PhysicsStore tick lane or explicitly scheduled PhysicsStore owner work. Off-lane callers should
 * use the {@code *Async} methods, which enqueue copied reads for
 * {@link PhysicsStoreReadQueueResource}.</p>
 */
public final class PhysicsStoreDiagnostics {

    private PhysicsStoreDiagnostics() {
    }

    public static int bodyCount(@Nonnull Store<PhysicsStore> store, @Nonnull SpaceId spaceId) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceId, "spaceId"));
        return space != null ? space.backendRuntime().bodyCount(space.spaceHandle().value()) : 0;
    }

    public static int bodyCount(@Nonnull Store<PhysicsStore> store, @Nonnull UUID spaceUuid) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceUuid, "spaceUuid"));
        return space != null ? space.backendRuntime().bodyCount(space.spaceHandle().value()) : 0;
    }

    @Nonnull
    public static CompletionStage<Integer> bodyCountAsync(@Nonnull World world,
        @Nonnull SpaceId spaceId) {
        return bodyCountAsync(store(world), spaceId);
    }

    @Nonnull
    public static CompletionStage<Integer> bodyCountAsync(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        return queue(store).enqueue(physics -> bodyCount(physics, spaceId));
    }

    public static int runtimeJointCount(@Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        int[] count = {0};
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            count[0] += backendRuntime.jointCount(spaceHandle.value()));
        return count[0];
    }

    @Nonnull
    public static CompletionStage<Integer> runtimeJointCountAsync(@Nonnull World world) {
        return runtimeJointCountAsync(store(world));
    }

    @Nonnull
    public static CompletionStage<Integer> runtimeJointCountAsync(@Nonnull Store<PhysicsStore> store) {
        return queue(store).enqueue(PhysicsStoreDiagnostics::runtimeJointCount);
    }

    public static boolean ccdSupported(@Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        boolean[] supported = {false};
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) -> {
            if (backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                supported[0] = true;
            }
        });
        return supported[0];
    }

    @Nonnull
    public static CompletionStage<Boolean> ccdSupportedAsync(@Nonnull World world) {
        return ccdSupportedAsync(store(world));
    }

    @Nonnull
    public static CompletionStage<Boolean> ccdSupportedAsync(@Nonnull Store<PhysicsStore> store) {
        return queue(store).enqueue(PhysicsStoreDiagnostics::ccdSupported);
    }

    @Nonnull
    public static SolverCapabilitySummary solverCapability(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.requireSpace(store, Objects.requireNonNull(spaceId, "spaceId"));
        return solverCapability(spaceId, space);
    }

    @Nonnull
    public static CompletionStage<SolverCapabilitySummary> solverCapabilityAsync(
        @Nonnull World world,
        @Nonnull SpaceId spaceId) {
        return solverCapabilityAsync(store(world), spaceId);
    }

    @Nonnull
    public static CompletionStage<SolverCapabilitySummary> solverCapabilityAsync(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        return queue(store).enqueue(physics -> solverCapability(physics, spaceId));
    }

    @Nonnull
    public static SolverCapabilitySummary solverCapability(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        SpaceId spaceId = compatibility.getSpaceId(Objects.requireNonNull(spaceUuid, "spaceUuid"));
        if (spaceId == null) {
            throw new IllegalArgumentException("Physics space uuid=" + spaceUuid
                + " has no compatibility SpaceId");
        }
        return solverCapability(spaceId, PhysicsStoreBackendAccess.requireSpace(store, spaceUuid));
    }

    @Nonnull
    public static List<SpaceSummary> spaceSummaries(@Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        List<SpaceSummary> summaries = new ArrayList<>();
        runtime.forEachSpaceBinding((spaceUuid, backendId, spaceHandle, backendRuntime) -> {
            SpaceId spaceId = compatibility.getSpaceId(spaceUuid);
            if (spaceId != null) {
                summaries.add(new SpaceSummary(spaceId,
                    backendId,
                    backendRuntime.bodyCount(spaceHandle.value()),
                    backendRuntime.jointCount(spaceHandle.value())));
            }
        });
        return summaries.isEmpty() ? List.of() : List.copyOf(summaries);
    }

    @Nonnull
    public static CompletionStage<List<SpaceSummary>> spaceSummariesAsync(@Nonnull World world) {
        return spaceSummariesAsync(store(world));
    }

    @Nonnull
    public static CompletionStage<List<SpaceSummary>> spaceSummariesAsync(
        @Nonnull Store<PhysicsStore> store) {
        return queue(store).enqueue(PhysicsStoreDiagnostics::spaceSummaries);
    }

    @Nonnull
    public static List<SpaceSummary> spaceSummaries(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        UUID spaceUuid = compatibility.getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
        if (spaceUuid == null) {
            return List.of();
        }
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(runtime, spaceUuid);
        return space != null
            ? List.of(PhysicsStoreBackendAccess.summary(compatibility, space))
            : List.of();
    }

    @Nonnull
    public static List<SpaceSummary> unsupportedCcdSpaces(@Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        List<SpaceSummary> spaces = new ArrayList<>();
        runtime.forEachSpaceBinding((spaceUuid, _, spaceHandle, backendRuntime) -> {
            if (!backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                PhysicsStoreBackendAccess.SpaceContext context =
                    PhysicsStoreBackendAccess.space(runtime, spaceUuid);
                if (context != null) {
                    spaces.add(PhysicsStoreBackendAccess.summary(compatibility, context));
                }
            }
        });
        return spaces.isEmpty() ? List.of() : List.copyOf(spaces);
    }

    @Nonnull
    public static CompletionStage<List<SpaceSummary>> unsupportedCcdSpacesAsync(
        @Nonnull World world) {
        return unsupportedCcdSpacesAsync(store(world));
    }

    @Nonnull
    public static CompletionStage<List<SpaceSummary>> unsupportedCcdSpacesAsync(
        @Nonnull Store<PhysicsStore> store) {
        return queue(store).enqueue(PhysicsStoreDiagnostics::unsupportedCcdSpaces);
    }

    @Nonnull
    private static SolverCapabilitySummary solverCapability(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsStoreBackendAccess.SpaceContext space) {
        return new SolverCapabilitySummary(spaceId,
            space.backendId().value(),
            space.backendRuntime().supportsSolverTuning(space.spaceHandle().value()),
            space.backendRuntime().supportsActivationTuning(space.spaceHandle().value()));
    }

    @Nonnull
    private static Store<PhysicsStore> store(@Nonnull World world) {
        return ((PhysicsStoreWorld) Objects.requireNonNull(world, "world")).getPhysicsStore()
            .getStore();
    }

    @Nonnull
    private static PhysicsStoreReadQueueResource queue(@Nonnull Store<PhysicsStore> store) {
        return Objects.requireNonNull(store, "store")
            .getResource(PhysicsStoreReadQueueResource.getResourceType());
    }
}
