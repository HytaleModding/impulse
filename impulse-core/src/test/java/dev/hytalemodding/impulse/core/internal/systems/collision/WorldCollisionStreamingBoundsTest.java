package dev.hytalemodding.impulse.core.internal.systems.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.hytalemodding.impulse.core.internal.voxel.WorldCollisionStreamingBounds;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WorldCollisionStreamingBoundsTest {

    @Test
    void dedupesBodiesThatShareTheSameChunkAndSectionNeighborhood() {
        WorldCollisionStreamingBounds first = WorldCollisionStreamingBounds.from(
            new Vector3f(10.2f, 65.4f, -3.7f),
            4);
        WorldCollisionStreamingBounds second = WorldCollisionStreamingBounds.from(
            new Vector3f(10.8f, 65.1f, -3.2f),
            4);

        assertEquals(first, second,
            "Bodies inside the same effective streamed neighborhood should dedupe");
    }

    @Test
    void keepsBodiesDistinctWhenTheirChunkNeighborhoodChanges() {
        WorldCollisionStreamingBounds first = WorldCollisionStreamingBounds.from(
            new Vector3f(15.5f, 70.0f, 15.5f),
            4);
        WorldCollisionStreamingBounds second = WorldCollisionStreamingBounds.from(
            new Vector3f(31.5f, 70.0f, 15.5f),
            4);

        assertNotEquals(first, second,
            "Bodies that touch different chunk neighborhoods should not dedupe");
    }

    @Test
    void clampsVerticalBoundsToValidSectionRange() {
        WorldCollisionStreamingBounds bounds = WorldCollisionStreamingBounds.from(
            new Vector3f(0.0f, -20.0f, 0.0f),
            12);

        assertEquals(0, bounds.minSectionY());
        assertEquals(0, bounds.maxSectionY(),
            "Below-world targets should clamp to the lowest valid streamed section");
    }

    @Test
    void scalarFactoryMatchesVectorFactory() {
        WorldCollisionStreamingBounds vectorBounds = WorldCollisionStreamingBounds.from(
            new Vector3f(10.2f, 65.4f, -3.7f),
            4);
        WorldCollisionStreamingBounds scalarBounds = WorldCollisionStreamingBounds.from(
            10.2f,
            65.4f,
            -3.7f,
            4);

        assertEquals(vectorBounds, scalarBounds);
    }
}
