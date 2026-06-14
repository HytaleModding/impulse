package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreAsyncCompletions;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequestFenceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequestFenceResult;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied request queue drained by PhysicsStore.tick().
 */
public final class PhysicsRequestQueueResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Queue<QueuedRequest> requests = new ArrayDeque<>();
    @Nonnull
    private final Map<UUID, PendingFence> pendingFences = new Object2ObjectLinkedOpenHashMap<>();

    public PhysicsRequestQueueResource() {
    }

    public synchronized void enqueue(@Nonnull PhysicsStoreRequest request) {
        requests.add(QueuedRequest.unfenced(request));
    }

    public synchronized void enqueueAll(@Nonnull Iterable<? extends PhysicsStoreRequest> batch) {
        Objects.requireNonNull(batch, "batch");
        for (PhysicsStoreRequest request : batch) {
            requests.add(QueuedRequest.unfenced(request));
        }
    }

    @Nonnull
    public synchronized PhysicsStoreRequestFenceHandle enqueueAllFenced(
        @Nonnull Iterable<? extends PhysicsStoreRequest> batch,
        long submittedServerTick) {
        Objects.requireNonNull(batch, "batch");
        UUID fenceUuid = UUID.randomUUID();
        CompletableFuture<PhysicsStoreRequestFenceResult> completion = new CompletableFuture<>();
        int count = 0;
        for (PhysicsStoreRequest request : batch) {
            requests.add(QueuedRequest.fenced(request, fenceUuid, submittedServerTick));
            count++;
        }
        if (count == 0) {
            PhysicsStoreAsyncCompletions.complete(completion,
                new PhysicsStoreRequestFenceResult(fenceUuid,
                submittedServerTick,
                submittedServerTick,
                0,
                0,
                0,
                0,
                0));
        } else {
            pendingFences.put(fenceUuid,
                new PendingFence(fenceUuid, submittedServerTick, count, completion));
        }
        return new PhysicsStoreRequestFenceHandle(fenceUuid, completion.minimalCompletionStage());
    }

    @Nonnull
    public synchronized List<QueuedRequest> drain() {
        List<QueuedRequest> drained = new ArrayList<>(requests.size());
        QueuedRequest request;
        while ((request = requests.poll()) != null) {
            drained.add(request);
        }
        return drained;
    }

    public void completeFences(@Nonnull Collection<PhysicsStoreRequestFenceResult> results) {
        Objects.requireNonNull(results, "results");
        List<FenceCompletion> completions = new ArrayList<>();
        synchronized (this) {
            for (PhysicsStoreRequestFenceResult result : results) {
                PendingFence fence = pendingFences.remove(result.fenceUuid());
                if (fence != null) {
                    completions.add(new FenceCompletion(fence.completion(), result));
                }
            }
        }
        complete(completions);
    }

    public synchronized int size() {
        return requests.size();
    }

    public void clear() {
        List<FenceCompletion> completions = new ArrayList<>();
        synchronized (this) {
            requests.clear();
            for (PendingFence fence : pendingFences.values()) {
                completions.add(new FenceCompletion(fence.completion(),
                    new PhysicsStoreRequestFenceResult(fence.fenceUuid(),
                        fence.submittedServerTick(),
                        fence.submittedServerTick(),
                        fence.acceptedCount(),
                        0,
                        0,
                        0,
                        fence.acceptedCount())));
            }
            pendingFences.clear();
        }
        complete(completions);
    }

    @Nonnull
    @Override
    public synchronized PhysicsRequestQueueResource clone() {
        PhysicsRequestQueueResource copy = new PhysicsRequestQueueResource();
        copy.requests.addAll(requests);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRequestQueueResource> getResourceType() {
        return PhysicsStoreTypes.requestQueueResourceType();
    }

    private static void complete(@Nonnull Iterable<FenceCompletion> completions) {
        for (FenceCompletion completion : completions) {
            PhysicsStoreAsyncCompletions.complete(completion.completion(), completion.result());
        }
    }

    public record QueuedRequest(@Nonnull PhysicsStoreRequest request,
                                @Nullable UUID fenceUuid,
                                long submittedServerTick) {

        public QueuedRequest {
            Objects.requireNonNull(request, "request");
            submittedServerTick = Math.max(0L, submittedServerTick);
        }

        @Nonnull
        private static QueuedRequest unfenced(@Nonnull PhysicsStoreRequest request) {
            return new QueuedRequest(Objects.requireNonNull(request, "request"), null, 0L);
        }

        @Nonnull
        private static QueuedRequest fenced(@Nonnull PhysicsStoreRequest request,
            @Nonnull UUID fenceUuid,
            long submittedServerTick) {
            return new QueuedRequest(Objects.requireNonNull(request, "request"),
                Objects.requireNonNull(fenceUuid, "fenceUuid"),
                submittedServerTick);
        }
    }

    private record PendingFence(@Nonnull UUID fenceUuid,
                                long submittedServerTick,
                                int acceptedCount,
                                @Nonnull CompletableFuture<PhysicsStoreRequestFenceResult>
                                    completion) {

        private PendingFence {
            Objects.requireNonNull(fenceUuid, "fenceUuid");
            submittedServerTick = Math.max(0L, submittedServerTick);
            acceptedCount = Math.max(0, acceptedCount);
            Objects.requireNonNull(completion, "completion");
        }
    }

    private record FenceCompletion(
        @Nonnull CompletableFuture<PhysicsStoreRequestFenceResult> completion,
        @Nonnull PhysicsStoreRequestFenceResult result) {

        private FenceCompletion {
            Objects.requireNonNull(completion, "completion");
            Objects.requireNonNull(result, "result");
        }
    }
}
