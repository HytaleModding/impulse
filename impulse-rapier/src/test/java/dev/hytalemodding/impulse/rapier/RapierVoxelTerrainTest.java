package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsVoxelTerrainCapability;
import org.junit.jupiter.api.Test;

class RapierVoxelTerrainTest {

    @Test
    void combineVoxelTerrainNativeReportsInvalidSpaceHandle() {
        RapierNative.load();

        assertFalse(RapierNative.combineVoxelTerrainNative(0L, 1L, 2L, 1, 0, 0));
    }

    @Test
    void combineVoxelTerrainsRejectsNonVoxelBodies() {
        RapierBackend backend = new RapierBackend();
        backend.init();
        PhysicsSpace space = backend.createSpace(new SpaceId(2));
        try {
            PhysicsVoxelTerrainCapability voxelTerrain = space
                .getCapability(PhysicsVoxelTerrainCapability.class)
                .orElseThrow();
            PhysicsBody voxelBody = voxelTerrain.createVoxelTerrain(1.0f,
                1.0f,
                1.0f,
                new int[] {0, 0, 0});
            PhysicsBody box = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            space.addBody(voxelBody);
            space.addBody(box);

            assertThrows(IllegalArgumentException.class,
                () -> voxelTerrain.combineVoxelTerrains(voxelBody, box, 1, 0, 0));
        } finally {
            space.close();
        }
    }

    @Test
    void combineVoxelTerrainsRejectsSameBody() {
        RapierBackend backend = new RapierBackend();
        backend.init();
        PhysicsSpace space = backend.createSpace(new SpaceId(3));
        try {
            PhysicsVoxelTerrainCapability voxelTerrain = space
                .getCapability(PhysicsVoxelTerrainCapability.class)
                .orElseThrow();
            PhysicsBody voxelBody = voxelTerrain.createVoxelTerrain(1.0f,
                1.0f,
                1.0f,
                new int[] {0, 0, 0});
            space.addBody(voxelBody);

            assertThrows(IllegalArgumentException.class,
                () -> voxelTerrain.combineVoxelTerrains(voxelBody, voxelBody, 1, 0, 0));
        } finally {
            space.close();
        }
    }
}
