package dev.hytalemodding.impulse.core.plugin.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RaycastClosestBatchQueryTest {

    @Test
    void reusesImmutableSegmentsWhileFreezingTheInputList() {
        RaycastSegment first = new RaycastSegment(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);
        RaycastSegment second = new RaycastSegment(7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f);
        List<RaycastSegment> source = new ArrayList<>(List.of(first, second));

        RaycastClosestBatchQuery query = new RaycastClosestBatchQuery(new SpaceId(3), source);
        source.clear();

        assertEquals(2, query.rays().size());
        assertSame(first, query.rays().get(0));
        assertSame(second, query.rays().get(1));
        assertThrows(UnsupportedOperationException.class,
            () -> query.rays().add(new RaycastSegment(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f)));
    }
}
