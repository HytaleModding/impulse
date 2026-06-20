package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsVoxelTerrainCapability;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RapierVoxelTerrainTest {

    @Test
    void dynamicBoxRestsOnNativeVoxelFloor() {
        RapierBackend backend = new RapierBackend();
        backend.init();
        PhysicsSpace space = backend.createSpace(new SpaceId(10));
        try {
            space.setGravity(0.0f, -9.81f, 0.0f);
            addVoxelFloor(space, 0.0f);
            PhysicsBody box = addDynamicBox(space, 8.0f, 3.0f, 8.0f);

            StepResult result = stepAndTrackMinimumY(space, box, 240);

            assertFalse(result.fellThrough());
        } finally {
            space.close();
        }
    }

    @Test
    void dynamicBoxRestsNearNativeVoxelSectionEdge() {
        RapierBackend backend = new RapierBackend();
        backend.init();
        PhysicsSpace space = backend.createSpace(new SpaceId(11));
        try {
            space.setGravity(0.0f, -9.81f, 0.0f);
            addVoxelFloor(space, 0.0f);
            PhysicsBody box = addDynamicBox(space, 15.75f, 3.0f, 8.0f);

            StepResult result = stepAndTrackMinimumY(space, box, 240);

            assertFalse(result.fellThrough());
        } finally {
            space.close();
        }
    }

    @Test
    void dynamicBoxRestsAcrossStitchedNativeVoxelSections() {
        RapierBackend backend = new RapierBackend();
        backend.init();
        PhysicsSpace space = backend.createSpace(new SpaceId(12));
        try {
            space.setGravity(0.0f, -9.81f, 0.0f);
            PhysicsVoxelTerrainCapability voxelTerrain = space
                .getCapability(PhysicsVoxelTerrainCapability.class)
                .orElseThrow();
            PhysicsBody first = addVoxelFloor(space, 0.0f);
            PhysicsBody second = addVoxelFloor(space, 16.0f);
            voxelTerrain.combineVoxelTerrains(first, second, 16, 0, 0);
            PhysicsBody box = addDynamicBox(space, 16.0f, 3.0f, 8.0f);

            StepResult result = stepAndTrackMinimumY(space, box, 240);

            assertFalse(result.fellThrough());
        } finally {
            space.close();
        }
    }

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

    private static PhysicsBody addVoxelFloor(PhysicsSpace space, float originX) {
        PhysicsVoxelTerrainCapability voxelTerrain = space
            .getCapability(PhysicsVoxelTerrainCapability.class)
            .orElseThrow();
        PhysicsBody floor = voxelTerrain.createVoxelTerrain(1.0f,
            1.0f,
            1.0f,
            voxelFloorCoordinates(16, 16));
        floor.setPosition(originX, 0.0f, 0.0f);
        space.addBody(floor);
        return floor;
    }

    private static int[] voxelFloorCoordinates(int width, int depth) {
        int[] coordinates = new int[width * depth * 3];
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                coordinates[index++] = x;
                coordinates[index++] = 0;
                coordinates[index++] = z;
            }
        }
        return coordinates;
    }

    private static PhysicsBody addDynamicBox(PhysicsSpace space, float x, float y, float z) {
        PhysicsBody box = space.createBox(0.45f, 0.45f, 0.45f, 1.0f);
        box.setPosition(x, y, z);
        space.addBody(box);
        return box;
    }

    private static StepResult stepAndTrackMinimumY(PhysicsSpace space,
        PhysicsBody body,
        int steps) {
        float minY = Float.POSITIVE_INFINITY;
        for (int i = 0; i < steps; i++) {
            space.step(1.0f / 30.0f);
            minY = Math.min(minY, body.getPosition().y);
        }
        return new StepResult(body.getPosition(), minY);
    }

    private record StepResult(Vector3f finalPosition, float minY) {

        private boolean fellThrough() {
            return finalPosition.y < 1.0f || minY < 0.75f;
        }
    }
}
