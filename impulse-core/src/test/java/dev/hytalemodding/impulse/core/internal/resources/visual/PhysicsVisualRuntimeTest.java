package dev.hytalemodding.impulse.core.internal.resources.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PhysicsVisualRuntimeTest {

    private static final RigidBodyKey FIRST_BODY =
        RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final RigidBodyKey SECOND_BODY =
        RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    @Test
    void hasAttachmentsPrunesStaleReferencesWithoutCopyingLiveAttachments() {
        AtomicInteger cleaned = new AtomicInteger();
        PhysicsVisualRuntime runtime = new PhysicsVisualRuntime(ref -> cleaned.incrementAndGet());
        TestRef liveRef = new TestRef(true);
        TestRef staleRef = new TestRef(false);

        assertFalse(runtime.hasAttachments(FIRST_BODY));

        runtime.registerAttachment(FIRST_BODY, staleRef);
        runtime.registerAttachment(FIRST_BODY, liveRef);

        assertTrue(runtime.hasAttachments(FIRST_BODY));
        assertEquals(1, cleaned.get());

        runtime.unregisterAttachment(FIRST_BODY, liveRef);

        assertFalse(runtime.hasAttachments(FIRST_BODY));
    }

    @Test
    void generatedVisualProxyCountPrunesStaleReferencesWithoutBodyIdCopy() {
        AtomicInteger cleaned = new AtomicInteger();
        PhysicsVisualRuntime runtime = new PhysicsVisualRuntime(ref -> cleaned.incrementAndGet());
        runtime.setGeneratedVisualProxy(FIRST_BODY, new TestRef(true));
        runtime.setGeneratedVisualProxy(SECOND_BODY, new TestRef(false));

        assertEquals(1, runtime.generatedVisualProxyCount());
        assertEquals(1, cleaned.get());
        assertEquals(1, runtime.getGeneratedVisualProxyBodyKeys().size());
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
