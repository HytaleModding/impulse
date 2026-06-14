package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreAsyncCompletions;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Owner-lane live backend read queue drained by PhysicsStore systems.
 *
 * <p>Enqueued reads must capture copied inputs only. They execute during PhysicsStore ticking and
 * must return copied values rather than live {@code Ref<PhysicsStore>}, runtime resources, or
 * backend handles.</p>
 */
public final class PhysicsStoreReadQueueResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Queue<QueuedRead<?>> reads = new ArrayDeque<>();

    public PhysicsStoreReadQueueResource() {
    }

    @Nonnull
    public synchronized <R> CompletionStage<R> enqueue(
        @Nonnull Function<Store<PhysicsStore>, R> read) {
        CompletableFuture<R> completion = new CompletableFuture<>();
        reads.add(new QueuedRead<>(read, completion));
        return completion.minimalCompletionStage();
    }

    @Nonnull
    public synchronized List<QueuedRead<?>> drain() {
        List<QueuedRead<?>> drained = new ArrayList<>(reads.size());
        QueuedRead<?> read;
        while ((read = reads.poll()) != null) {
            drained.add(read);
        }
        return drained;
    }

    public void clear() {
        List<QueuedRead<?>> drained;
        synchronized (this) {
            drained = new ArrayList<>(reads);
            reads.clear();
        }
        CancellationException cancelled =
            new CancellationException("PhysicsStore read queue cleared");
        for (QueuedRead<?> read : drained) {
            read.fail(cancelled);
        }
    }

    public synchronized int size() {
        return reads.size();
    }

    @Nonnull
    @Override
    public PhysicsStoreReadQueueResource clone() {
        return new PhysicsStoreReadQueueResource();
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsStoreReadQueueResource> getResourceType() {
        return PhysicsStoreTypes.readQueueResourceType();
    }

    public static final class QueuedRead<R> {

        @Nonnull
        private final Function<Store<PhysicsStore>, R> read;
        @Nonnull
        private final CompletableFuture<R> completion;

        private QueuedRead(@Nonnull Function<Store<PhysicsStore>, R> read,
            @Nonnull CompletableFuture<R> completion) {
            this.read = Objects.requireNonNull(read, "read");
            this.completion = Objects.requireNonNull(completion, "completion");
        }

        public void complete(@Nonnull Store<PhysicsStore> store) {
            try {
                PhysicsStoreAsyncCompletions.complete(completion, read.apply(store));
            } catch (RuntimeException | Error exception) {
                fail(exception);
            }
        }

        public void fail(@Nonnull Throwable failure) {
            PhysicsStoreAsyncCompletions.fail(completion, Objects.requireNonNull(failure, "failure"));
        }
    }
}
