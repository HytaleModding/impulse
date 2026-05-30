package dev.hytalemodding.impulse.core.internal.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandCompletion;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandMetadata;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class PhysicsCommandCompletionTest {

    @Test
    void commandHandleSummariesUseCompletionSummary() {
        PhysicsCommandMetadata metadata = new PhysicsCommandMetadata(10L, 7L);
        PhysicsCommandCompletion completion = PhysicsCommandCompletion.allApplied(metadata, 10_000);
        PhysicsCommandHandle handle = PhysicsCommandHandle.fromCompletionSummary(
            new PhysicsCommandBatch(metadata, 10_000),
            CompletableFuture.completedFuture(completion));

        assertSame(completion, handle.completionSummary().toCompletableFuture().join());
        assertTrue(handle.allApplied().toCompletableFuture().join());
        assertTrue(handle.firstRejected().toCompletableFuture().join().isEmpty());
    }

    @Test
    void compactAllAppliedCompletionExposesLazyResultList() {
        PhysicsCommandMetadata metadata = new PhysicsCommandMetadata(10L, 7L);

        PhysicsCommandCompletion completion = PhysicsCommandCompletion.allApplied(metadata, 10_000);

        assertTrue(completion.allApplied());
        assertTrue(completion.firstRejected().isEmpty());
        assertEquals(10_000, completion.results().size());
        PhysicsCommandResult first = completion.results().getFirst();
        PhysicsCommandResult last = completion.results().get(9_999);
        assertEquals(1L, first.commandSequence());
        assertEquals(10_000L, last.commandSequence());
        assertEquals(7L, first.commandBatchSequence());
        assertEquals(10L, last.submittedServerTick());
    }

    @Test
    void compactAllRejectedCompletionExposesLazyResultList() {
        PhysicsCommandMetadata metadata = new PhysicsCommandMetadata(11L, 8L);

        PhysicsCommandCompletion completion = PhysicsCommandCompletion.allRejected(metadata,
            10_000,
            "stale batch");

        assertFalse(completion.allApplied());
        PhysicsCommandResult firstRejected = completion.firstRejected().orElseThrow();
        assertEquals(1L, firstRejected.commandSequence());
        assertEquals("stale batch", firstRejected.message());
        assertEquals(10_000, completion.results().size());
        PhysicsCommandResult last = completion.results().get(9_999);
        assertEquals(10_000L, last.commandSequence());
        assertEquals(PhysicsCommandResult.Status.REJECTED, last.status());
        assertEquals(8L, last.commandBatchSequence());
        assertEquals(11L, last.submittedServerTick());
    }
}
