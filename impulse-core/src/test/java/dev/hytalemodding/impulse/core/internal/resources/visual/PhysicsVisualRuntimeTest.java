package dev.hytalemodding.impulse.core.internal.resources.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PhysicsVisualRuntimeTest {

    private static final UUID FIRST_BODY =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_BODY =
        UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void hasAttachmentsPrunesStaleReferencesWithoutCopyingLiveAttachments() {
        AtomicInteger cleaned = new AtomicInteger();
        PhysicsVisualRuntime runtime = new PhysicsVisualRuntime(_ -> cleaned.incrementAndGet());
        TestRef liveRef = new TestRef(true);
        TestRef staleRef = new TestRef(false);

        assertFalse(runtime.hasAttachments(FIRST_BODY, null));

        runtime.registerAttachment(FIRST_BODY, null, staleRef);
        runtime.registerAttachment(FIRST_BODY, null, liveRef);

        assertTrue(runtime.hasAttachments(FIRST_BODY, null));
        assertEquals(1, cleaned.get());

        runtime.unregisterAttachment(FIRST_BODY, null, liveRef);

        assertFalse(runtime.hasAttachments(FIRST_BODY, null));
    }

    @Test
    void generatedVisualProxyCountPrunesStaleReferencesWithoutBodyIdCopy() {
        AtomicInteger cleaned = new AtomicInteger();
        PhysicsVisualRuntime runtime = new PhysicsVisualRuntime(_ -> cleaned.incrementAndGet());
        runtime.setGeneratedVisualProxy(FIRST_BODY, null, new TestRef(true));
        runtime.setGeneratedVisualProxy(SECOND_BODY, null, new TestRef(false));

        assertEquals(1, runtime.generatedVisualProxyCount());
        assertEquals(1, cleaned.get());
    }

    @Test
    void staleReferenceCleanerRunsOutsideVisualRuntimeLock() {
        AtomicInteger cleaned = new AtomicInteger();
        AtomicReference<PhysicsVisualRuntime> runtimeRef = new AtomicReference<>();
        PhysicsVisualRuntime runtime = new PhysicsVisualRuntime(_ -> {
            assertFalse(Thread.holdsLock(runtimeRef.get()));
            cleaned.incrementAndGet();
        });
        runtimeRef.set(runtime);

        runtime.registerAttachment(FIRST_BODY, null, new TestRef(false));
        runtime.setGeneratedVisualProxy(SECOND_BODY, null, new TestRef(false));

        assertFalse(runtime.hasAttachments(FIRST_BODY, null));
        assertEquals(0, runtime.generatedVisualProxyCount());
        assertEquals(2, cleaned.get());
    }

    private static final class TestRef extends Ref<EntityStore> {

        private final boolean valid;

        private TestRef(boolean valid) {
            super(null);
            this.valid = valid;
        }

        @Override
        public boolean isValid() {
            return valid;
        }
    }
}
