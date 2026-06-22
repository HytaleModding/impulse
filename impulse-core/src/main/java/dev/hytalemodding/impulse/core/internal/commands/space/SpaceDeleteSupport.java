package dev.hytalemodding.impulse.core.internal.commands.space;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollision;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsDiagnostics;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.physics.SpaceSummary;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

final class SpaceDeleteSupport {

    private SpaceDeleteSupport() {
    }

    @Nonnull
    static DeleteResult deleteOnWorldThread(
        @Nonnull World world,
        @Nonnull Store<PhysicsStore> physicsStore,
        int rawSpaceId) {
        PhysicsThreading.requireWorldThread(physicsStore, "delete PhysicsStore space");
        if (rawSpaceId <= 0) {
            return DeleteResult.invalid(rawSpaceId);
        }

        PhysicsSpaceCompatibilityIndexResource compatibility = physicsStore.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        SpaceId spaceId = SpaceSelection.specifiedSpaceId(compatibility, rawSpaceId);
        if (spaceId == null) {
            return DeleteResult.missing(rawSpaceId);
        }

        UUID spaceUuid = compatibility.getSpaceUuid(spaceId);
        Ref<PhysicsStore> spaceRef = spaceUuid != null
            ? physicsStore.getExternalData().getRefFromUUID(spaceUuid)
            : null;
        if (spaceRef == null || spaceRef.getStore() != physicsStore || !spaceRef.isValid()) {
            return DeleteResult.unbound(rawSpaceId);
        }

        /*
         * Streaming PhysicsChunk collision is represented as normal PhysicsStore body rows with
         * copied snapshots. Those rows belong to the space/cache lifecycle and are removed with
         * the space/cache. Non-PhysicsChunk body rows are gameplay/runtime resources addressed by
         * durable body UUID or live PhysicsStore entity ref, so they still require explicit
         * clean/destroy before deleting the space.
         */
        int registeredBodies = registeredBodiesExcludingChunkCollision(physicsStore,
            compatibility,
            spaceId);
        List<SpaceSummary> summaries = PhysicsDiagnostics.spaceSummaries(physicsStore,
            spaceRef);
        SpaceCounts counts = countSpaceContents(summaries, spaceId);
        int backendBodies = counts.bodies();
        int joints = counts.joints();
        if (registeredBodies > 0 || joints > 0) {
            return DeleteResult.notEmpty(rawSpaceId,
                registeredBodies,
                backendBodies,
                joints);
        }

        PhysicsChunkCollision.clearSpace(world, physicsStore, spaceRef);
        PhysicsSpaces.removeWithContents(physicsStore, spaceId);
        return DeleteResult.deleted(rawSpaceId, backendBodies, joints);
    }

    private static int registeredBodiesExcludingChunkCollision(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull SpaceId spaceId) {
        int count = 0;
        for (PhysicsBodySnapshot snapshot : PhysicsBodies.snapshotFrame(store).bodies()) {
            if (!spaceId.equals(compatibility.getSpaceId(snapshot.spaceUuid()))
                || PhysicsChunkCollision.isChunkCollisionBody(store, snapshot.bodyUuid())) {
                continue;
            }
            count++;
        }
        return count;
    }

    @Nonnull
    private static SpaceCounts countSpaceContents(@Nonnull List<SpaceSummary> summaries,
        @Nonnull SpaceId spaceId) {
        return summaries.stream()
            .filter(summary -> summary.spaceId().equals(spaceId))
            .findFirst()
            .map(summary -> new SpaceCounts(summary.bodyCount(), summary.jointCount()))
            .orElseGet(() -> new SpaceCounts(0, 0));
    }

    enum DeleteOutcome {
        INVALID,
        MISSING,
        UNBOUND,
        NOT_EMPTY,
        DELETED
    }

    record DeleteResult(@Nonnull DeleteOutcome outcome,
                        int rawSpaceId,
                        int registeredBodies,
                        int backendBodies,
                        int joints) {

        @Nonnull
        private static DeleteResult invalid(int rawSpaceId) {
            return new DeleteResult(DeleteOutcome.INVALID, rawSpaceId, 0, 0, 0);
        }

        @Nonnull
        private static DeleteResult missing(int rawSpaceId) {
            return new DeleteResult(DeleteOutcome.MISSING, rawSpaceId, 0, 0, 0);
        }

        @Nonnull
        private static DeleteResult unbound(int rawSpaceId) {
            return new DeleteResult(DeleteOutcome.UNBOUND, rawSpaceId, 0, 0, 0);
        }

        @Nonnull
        private static DeleteResult notEmpty(int rawSpaceId,
            int registeredBodies,
            int backendBodies,
            int joints) {
            return new DeleteResult(DeleteOutcome.NOT_EMPTY,
                rawSpaceId,
                registeredBodies,
                backendBodies,
                joints);
        }

        @Nonnull
        private static DeleteResult deleted(int rawSpaceId, int backendBodies, int joints) {
            return new DeleteResult(DeleteOutcome.DELETED, rawSpaceId, 0, backendBodies, joints);
        }
    }

    private record SpaceCounts(int bodies, int joints) {
    }
}
