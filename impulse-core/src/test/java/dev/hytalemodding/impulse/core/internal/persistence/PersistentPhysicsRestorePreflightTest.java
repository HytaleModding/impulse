package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PersistentPhysicsRestorePreflightTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void acceptsValidPersistedRuntimeSnapshot() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.syncPersistentState();

        assertNull(PersistentPhysicsRestorePreflight.validate(fixture.persistent));
    }

    @Test
    void rejectsDuplicateSpaceIdsBeforeRuntimeStrip() {
        RuntimeFixture fixture = createRuntimeFixture();
        PersistentPhysicsSpaceState first =
            PersistentPhysicsSpaceState.from(fixture.space, fixture.runtime.getSpaceSettings(fixture.space.getId()));
        PersistentPhysicsSpaceState second = first.copy();
        fixture.persistent.setSpaces(new PersistentPhysicsSpaceState[] { first, second });

        String failure = PersistentPhysicsRestorePreflight.validate(fixture.persistent);

        assertNotNull(failure);
        assertTrue(failure.contains("Duplicate persisted space id"));
    }

    @Test
    void rejectsDuplicateBodyKeysBeforeHydrationCanCreateBackendBodies() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.syncPersistentState();
        PersistentPhysicsBodyState body = fixture.persistent.getBodies()[0];

        fixture.persistent.setBodies(new PersistentPhysicsBodyState[] { body, body.copy() });
        String failure = PersistentPhysicsRestorePreflight.validate(fixture.persistent);

        assertNotNull(failure);
        assertTrue(failure.contains("Duplicate persisted body key"));
    }

    @Test
    void rejectsInvalidWorldSettingsBeforeRuntimeStrip() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.syncPersistentState();
        PhysicsWorldSettings invalid = fixture.persistent.getWorldSettings();
        setFloatField(invalid, "maxStepDt", Float.NaN);
        fixture.persistent.setWorldSettings(invalid);

        String failure = PersistentPhysicsRestorePreflight.validate(fixture.persistent);

        assertNotNull(failure);
        assertTrue(failure.contains("Invalid persisted physics runtime settings"));
    }

    @Test
    void rejectsInvalidSpaceSettingsBeforeRuntimeStrip() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        PersistentPhysicsSpaceState state =
            PersistentPhysicsSpaceState.from(fixture.space, fixture.runtime.getSpaceSettings(fixture.space.getId()));
        setIntField(state, "worldCollisionRadius", 0);
        fixture.persistent.setSpaces(new PersistentPhysicsSpaceState[] { state });

        String failure = PersistentPhysicsRestorePreflight.validate(fixture.persistent);

        assertNotNull(failure);
        assertTrue(failure.contains("Invalid persisted physics space settings"));
    }

    @Test
    void rejectsBlankBackendIdBeforeRuntimeStrip() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        PersistentPhysicsSpaceState state =
            PersistentPhysicsSpaceState.from(fixture.space, fixture.runtime.getSpaceSettings(fixture.space.getId()));
        setStringField(state, "backendId", " ");
        fixture.persistent.setSpaces(new PersistentPhysicsSpaceState[] { state });

        String failure = assertDoesNotThrow(() -> PersistentPhysicsRestorePreflight.validate(fixture.persistent));

        assertNotNull(failure);
        assertTrue(failure.contains("Invalid persisted physics space backend id"));
    }

    @Test
    void rejectsCcdModeForBackendWithoutCcdBeforeRuntimeStrip() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.syncPersistentState();
        PhysicsWorldSettings settings = fixture.persistent.getWorldSettings();
        settings.setStepMode(PhysicsStepMode.CCD);
        fixture.persistent.setWorldSettings(settings);

        String failure = PersistentPhysicsRestorePreflight.validate(fixture.persistent);

        assertNotNull(failure);
        assertTrue(failure.contains("CCD mode"));
    }

    private static RuntimeFixture createRuntimeFixture() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:persistence-preflight-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource runtime = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = runtime.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        runtime.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        return new RuntimeFixture(runtime, new PersistentPhysicsWorldResource(), space);
    }

    private static void setFloatField(Object target, String fieldName, float value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setStringField(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record RuntimeFixture(PhysicsWorldRuntimeResource runtime,
        PersistentPhysicsWorldResource persistent,
        PhysicsSpace space) {

        private void syncPersistentState() {
            persistent.setWorldSettings(runtime.getWorldSettings());
            persistent.setSpaces(new PersistentPhysicsSpaceState[] {
                PersistentPhysicsSpaceState.from(space, runtime.getSpaceSettings(space.getId()))
            });
            persistent.setBodies(PersistentPhysicsRuntimeSnapshot.capture(runtime).getBodies());
        }
    }
}
