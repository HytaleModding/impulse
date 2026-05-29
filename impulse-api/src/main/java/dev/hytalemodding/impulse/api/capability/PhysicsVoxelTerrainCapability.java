package dev.hytalemodding.impulse.api.capability;

import dev.hytalemodding.impulse.api.PhysicsBody;
import javax.annotation.Nonnull;

/**
 * Optional backend capability for native voxel terrain collision.
 */
public interface PhysicsVoxelTerrainCapability extends PhysicsCapability {

    PhysicsCapabilityDescriptor DESCRIPTOR = new PhysicsCapabilityDescriptor(
        new PhysicsCapabilityId("impulse:voxel_terrain"),
        "Voxel terrain",
        "Creates native static voxel terrain bodies");

    /**
     * Creates a static terrain body made from occupied voxel cells.
     *
     * <p>The {@code voxelCoordinates} array stores triples of local integer grid coordinates:
     * {@code x0, y0, z0, x1, y1, z1, ...}. The returned body can then be positioned at the
     * section/world origin like any other static body.</p>
     */
    @Nonnull
    PhysicsBody createVoxelTerrain(float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        @Nonnull int[] voxelCoordinates);

    /**
     * Couples two adjacent voxel terrain bodies so the backend can treat their shared boundary
     * as continuous terrain instead of two unrelated voxel sets.
     *
     * <p>The shift is expressed in voxel units from {@code bodyA}'s local voxel origin to
     * {@code bodyB}'s local voxel origin. Backends that do not use native adjacency hints can keep
     * the default no-op behavior.</p>
     */
    default void combineVoxelTerrains(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        int shiftX,
        int shiftY,
        int shiftZ) {
    }
}
