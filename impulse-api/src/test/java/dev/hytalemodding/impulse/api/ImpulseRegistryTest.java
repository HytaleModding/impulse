package dev.hytalemodding.impulse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class ImpulseRegistryTest {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private static final int CONCURRENT_RUNTIME_CREATIONS = 4;

    @Test
    void throwsWhenRequestingUnknownRuntimeProvider() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> Impulse.getRuntimeProvider(new BackendId(uniqueId())));

        assertTrue(exception.getMessage().startsWith("No backend runtime provider registered with id:"));
    }

    @Test
    void registersRuntimeProvidersInitializesOnceAndCreatesRuntime() {
        CountingRuntimeProvider provider = new CountingRuntimeProvider(new BackendId(uniqueId()));
        Impulse.registerRuntimeProvider(provider);

        PhysicsBackendRuntime firstRuntime = Impulse.createRuntime(provider.getId());
        PhysicsBackendRuntime secondRuntime = Impulse.createRuntime(provider.getId());

        assertSame(provider, Impulse.getRuntimeProvider(provider.getId()));
        assertEquals(1, provider.initCount());
        assertEquals(2, provider.createRuntimeCount());
        assertTrue(Impulse.getRuntimeProviders().contains(provider));
        assertTrue(provider.createdRuntimes().contains(firstRuntime));
        assertTrue(provider.createdRuntimes().contains(secondRuntime));
    }

    @Test
    void replacingRuntimeProviderResetsItsInitializationState() {
        BackendId backendId = new BackendId(uniqueId());
        CountingRuntimeProvider first = new CountingRuntimeProvider(backendId);
        CountingRuntimeProvider second = new CountingRuntimeProvider(backendId);

        Impulse.registerRuntimeProvider(first);
        Impulse.createRuntime(backendId);
        Impulse.registerRuntimeProvider(second);
        Impulse.createRuntime(backendId);

        assertEquals(1, first.initCount());
        assertEquals(1, second.initCount());
        assertEquals(1, second.createRuntimeCount());
        assertSame(second, Impulse.getRuntimeProvider(backendId));
    }

    @Test
    void createsRuntimesConcurrentlyThroughRegistry() throws Exception {
        CountingRuntimeProvider provider = new CountingRuntimeProvider(new BackendId(uniqueId()));
        Impulse.registerRuntimeProvider(provider);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_RUNTIME_CREATIONS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_RUNTIME_CREATIONS);
        CountDownLatch start = new CountDownLatch(1);
        Set<PhysicsBackendRuntime> runtimes = new HashSet<>();
        try {
            List<Future<PhysicsBackendRuntime>> futures = IntStream
                .range(0, CONCURRENT_RUNTIME_CREATIONS)
                .mapToObj(ignored -> executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return Impulse.createRuntime(provider.getId());
                }))
                .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<PhysicsBackendRuntime> future : futures) {
                assertTrue(runtimes.add(future.get(5, TimeUnit.SECONDS)));
            }

            assertEquals(CONCURRENT_RUNTIME_CREATIONS, runtimes.size());
            assertEquals(1, provider.initCount());
            assertEquals(CONCURRENT_RUNTIME_CREATIONS, provider.createRuntimeCount());
        } finally {
            executor.shutdownNow();
        }
    }

    private static String uniqueId() {
        return "test:backend-" + ID_COUNTER.incrementAndGet();
    }

    private static final class CountingRuntimeProvider implements PhysicsBackendRuntimeProvider {

        @Nonnull
        private final FakePhysicsBackendRuntimeProvider delegate;
        private int initCount;
        private int createRuntimeCount;

        private CountingRuntimeProvider(@Nonnull BackendId id) {
            this.delegate = new FakePhysicsBackendRuntimeProvider(id, false, false);
        }

        @Nonnull
        @Override
        public BackendId getId() {
            return delegate.getId();
        }

        @Override
        public synchronized void init() {
            initCount++;
        }

        @Nonnull
        @Override
        public synchronized PhysicsBackendRuntime createRuntime() {
            createRuntimeCount++;
            return delegate.createRuntime();
        }

        private synchronized int initCount() {
            return initCount;
        }

        private synchronized int createRuntimeCount() {
            return createRuntimeCount;
        }

        @Nonnull
        private synchronized List<FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime> createdRuntimes() {
            return delegate.createdRuntimes();
        }
    }
}
