package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreAsyncCompletions;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
                R value = read.apply(store);
                CopiedReadBoundary.requireCopied(value);
                PhysicsStoreAsyncCompletions.complete(completion, value);
            } catch (RuntimeException | Error exception) {
                fail(exception);
            }
        }

        public void fail(@Nonnull Throwable failure) {
            PhysicsStoreAsyncCompletions.fail(completion, Objects.requireNonNull(failure, "failure"));
        }
    }

    private static final class CopiedReadBoundary {

        private static final String LIVE_BACKEND = "dev.hytalemodding.impulse.api.PhysicsBackend";
        private static final String LIVE_SPACE = "dev.hytalemodding.impulse.api.PhysicsSpace";
        private static final String LIVE_BODY = "dev.hytalemodding.impulse.api.PhysicsBody";
        private static final String LIVE_JOINT = "dev.hytalemodding.impulse.api.PhysicsJoint";

        private CopiedReadBoundary() {
        }

        private static void requireCopied(Object value) {
            requireCopied(value, new IdentityHashMap<>());
        }

        private static void requireCopied(Object value, IdentityHashMap<Object, Boolean> seen) {
            if (value == null
                || isClearlyCopiedScalar(value)
                || seen.put(value, Boolean.TRUE) != null) {
                return;
            }
            rejectLiveValue(value);
            if (value instanceof Optional<?> optional) {
                optional.ifPresent(contained -> requireCopied(contained, seen));
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                for (Object contained : iterable) {
                    requireCopied(contained, seen);
                }
                return;
            }
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    requireCopied(entry.getKey(), seen);
                    requireCopied(entry.getValue(), seen);
                }
                return;
            }
            if (value.getClass().isArray() && !value.getClass().componentType().isPrimitive()) {
                Object[] values = (Object[]) value;
                for (Object contained : values) {
                    requireCopied(contained, seen);
                }
            }
        }

        private static boolean isClearlyCopiedScalar(Object value) {
            return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof java.util.UUID;
        }

        private static void rejectLiveValue(Object value) {
            if (value instanceof Store<?>
                || value instanceof Ref<?>
                || value instanceof Resource<?>
                || value instanceof PhysicsBackendRuntime
                || value instanceof CompletionStage<?>
                || implementsNamedType(value.getClass(), LIVE_BACKEND)
                || implementsNamedType(value.getClass(), LIVE_SPACE)
                || implementsNamedType(value.getClass(), LIVE_BODY)
                || implementsNamedType(value.getClass(), LIVE_JOINT)
                || value instanceof BackendSpaceHandle
                || value instanceof BackendBodyHandle
                || value instanceof BackendJointHandle) {
                throw new IllegalStateException("PhysicsStore queued reads must complete with "
                    + "copied values, not live " + value.getClass().getName());
            }
        }

        private static boolean implementsNamedType(@Nonnull Class<?> type,
            @Nonnull String typeName) {
            if (typeName.equals(type.getName())) {
                return true;
            }
            for (Class<?> interfaceType : type.getInterfaces()) {
                if (implementsNamedType(interfaceType, typeName)) {
                    return true;
                }
            }
            Class<?> superType = type.getSuperclass();
            return superType != null && implementsNamedType(superType, typeName);
        }
    }
}
