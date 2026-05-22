package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.exception.CodecValidationException;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PersistentPhysicsCodecValidationTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void spaceStateCodecValidatorChecksCrossFieldSettingsAfterDecode() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.setVisualSyncRadii(64, 128);
        PhysicsSpace space = new FakePhysicsBackend("test:space-codec-validation-"
            + BACKEND_COUNTER.incrementAndGet()).createSpace();
        BsonDocument encoded = PersistentPhysicsSpaceState.CODEC
            .encode(PersistentPhysicsSpaceState.from(space, settings))
            .asDocument();
        encoded.put("VisualFullSyncRadius", new BsonInt32(128));
        encoded.put("VisualMaxSyncRadius", new BsonInt32(64));

        assertValidationFails(
            () -> PersistentPhysicsSpaceState.CODEC.decode(encoded, new ExtraInfo()),
            "Visual full sync radius cannot exceed visual max sync radius");
    }

    @Test
    void worldResourceCodecValidatorChecksStepBudgetAfterDecode() {
        BsonDocument encoded = PersistentPhysicsWorldResource.CODEC
            .encode(new PersistentPhysicsWorldResource())
            .asDocument();
        encoded.put("SimulationSteps", new BsonInt32(0));

        assertValidationFails(
            () -> PersistentPhysicsWorldResource.CODEC.decode(encoded, new ExtraInfo()),
            "Must be greater than or equal to 1");
    }

    @Test
    void spaceStateFieldValidatorsRejectNullOptionalEnums() {
        BsonDocument encoded = encodedSpaceState();
        encoded.put("WorldCollisionMode", BsonNull.VALUE);

        assertValidationFails(
            () -> PersistentPhysicsSpaceState.CODEC.decode(encoded, new ExtraInfo()),
            "Can't be null");
    }

    @Test
    void spaceStateFieldValidatorsRejectBlankDetachedVisualBlockType() {
        BsonDocument encoded = encodedSpaceState();
        encoded.put("DetachedVisualBlockType", new BsonString(" "));

        assertValidationFails(
            () -> PersistentPhysicsSpaceState.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted detached visual block type cannot be blank");
    }

    @Test
    void worldResourceFieldValidatorsRejectUnknownStepMode() {
        BsonDocument encoded = PersistentPhysicsWorldResource.CODEC
            .encode(new PersistentPhysicsWorldResource())
            .asDocument();
        encoded.put("StepMode", new BsonString("bogus"));

        assertValidationFails(
            () -> PersistentPhysicsWorldResource.CODEC.decode(encoded, new ExtraInfo()),
            "Persistent physics step mode is unknown: bogus");
    }

    private static void assertValidationFails(Executable executable, String expectedMessagePart) {
        CodecValidationException exception = assertThrows(CodecValidationException.class,
            executable);

        assertTrue(exception.getMessage().contains(expectedMessagePart),
            () -> "Expected validation message to contain: " + expectedMessagePart
                + "\nActual: " + exception.getMessage());
    }

    private static BsonDocument encodedSpaceState() {
        PhysicsSpace space = new FakePhysicsBackend("test:space-codec-validation-"
            + BACKEND_COUNTER.incrementAndGet()).createSpace();
        return PersistentPhysicsSpaceState.CODEC
            .encode(PersistentPhysicsSpaceState.from(space, PhysicsSpaceSettings.defaults()))
            .asDocument();
    }
}
