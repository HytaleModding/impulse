package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.exception.CodecValidationException;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PersistentPhysicsCodecValidationTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();
    private static final ArrayCodec<PersistentPhysicsBodyState> BODY_ARRAY_CODEC =
        new ArrayCodec<>(PersistentPhysicsBodyState.CODEC, PersistentPhysicsBodyState[]::new);

    @Test
    void spaceStateCodecValidatorChecksCrossFieldSettingsAfterDecode() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualSyncRadii(64, 128);
        PhysicsSpace space = new FakePhysicsBackend("test:space-codec-validation-"
            + BACKEND_COUNTER.incrementAndGet()).createSpace();
        BsonDocument encoded = encodeSpace(PersistentPhysicsSpaceState.from(space, settings));
        encoded.put("VisualFullSyncRadius", new BsonInt32(128));
        encoded.put("VisualMaxSyncRadius", new BsonInt32(64));

        assertValidationFails(
            () -> PersistentPhysicsSpaceState.CODEC.decode(encoded, new ExtraInfo()),
            "Visual full sync radius cannot exceed visual max sync radius");
    }

    @Test
    void worldResourceCodecValidatorChecksStepBudgetAfterDecode() {
        BsonDocument encoded = encodeWorld(new PersistentPhysicsWorldResource());
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
        BsonDocument encoded = encodeWorld(new PersistentPhysicsWorldResource());
        encoded.put("StepMode", new BsonString("bogus"));

        assertValidationFails(
            () -> PersistentPhysicsWorldResource.CODEC.decode(encoded, new ExtraInfo()),
            "Persistent physics step mode is unknown: bogus");
    }

    @Test
    void worldResourceFieldValidatorsRejectUnknownStepSchedulingMode() {
        BsonDocument encoded = encodeWorld(new PersistentPhysicsWorldResource());
        encoded.put("StepSchedulingMode", new BsonString("bogus"));

        assertValidationFails(
            () -> PersistentPhysicsWorldResource.CODEC.decode(encoded, new ExtraInfo()),
            "Persistent physics step scheduling mode is unknown: bogus");
    }

    @Test
    void worldResourceCodecPreservesStepSchedulingMode() {
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        resource.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT);

        BsonDocument encoded = encodeWorld(resource);
        PersistentPhysicsWorldResource decoded = PersistentPhysicsWorldResource.CODEC
            .decode(encoded, new ExtraInfo());

        assertEquals("accumulate_pending_dt",
            encoded.getString("StepSchedulingMode").getValue());
        assertEquals(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
            decoded.getStepSchedulingMode());
    }

    @Test
    void worldResourceCodecDefaultsMissingStepSchedulingMode() {
        BsonDocument encoded = encodeWorld(new PersistentPhysicsWorldResource());
        encoded.remove("StepSchedulingMode");

        PersistentPhysicsWorldResource decoded = PersistentPhysicsWorldResource.CODEC
            .decode(encoded, new ExtraInfo());

        assertEquals(PhysicsStepSchedulingMode.DROP_PENDING_DT,
            decoded.getStepSchedulingMode());
    }

    @Test
    void worldResourceCodecWritesSchemaV4StateBlocksInsteadOfFlatBodiesAndJoints() {
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        PersistentPhysicsBodyState body = persistentBodyState();
        PersistentPhysicsJointState joint = persistentJointState(body.getSpaceId());
        resource.setBodies(new PersistentPhysicsBodyState[] { body });
        resource.setJoints(new PersistentPhysicsJointState[] { joint });

        BsonDocument encoded = encodeWorld(resource);

        assertEquals(PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION,
            encoded.getInt32("SchemaVersion").getValue());
        assertTrue(encoded.getArray("Bodies").isEmpty());
        assertTrue(encoded.getArray("Joints").isEmpty());
        assertFalse(encoded.getArray("BodyBlocks").isEmpty());
        assertFalse(encoded.getArray("JointBlocks").isEmpty());

        PersistentPhysicsWorldResource decoded = PersistentPhysicsWorldResource.CODEC
            .decode(encoded, new ExtraInfo());

        Assertions.assertNotNull(decoded);
        assertEquals(1, decoded.getBodyCount());
        assertEquals(1, decoded.getJointCount());
        assertEquals(body.getBodyIdValue(), decoded.getBodies()[0].getBodyIdValue());
        assertEquals(joint.key(), decoded.getJoints()[0].key());
    }

    @Test
    void worldResourceCodecReadsSchemaV4StateBlocksFromJson() throws Exception {
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        PersistentPhysicsBodyState body = persistentBodyState();
        resource.setBodies(new PersistentPhysicsBodyState[] { body });

        String json = BsonUtil.toJson(encodeWorld(resource));

        PersistentPhysicsWorldResource decoded;
        try (RawJsonReader reader = RawJsonReader.fromJsonString(json)) {
            decoded = PersistentPhysicsWorldResource.CODEC.decodeJson(reader, new ExtraInfo());
        }

        assertEquals(1, decoded.getBodyCount());
        assertEquals(body.getBodyIdValue(), decoded.getBodies()[0].getBodyIdValue());
    }

    @Test
    void worldResourceCodecMigratesSchemaV3FlatBodiesToSchemaV4RuntimeState() {
        PersistentPhysicsBodyState body = persistentBodyState();
        BsonDocument encoded = encodeWorld(new PersistentPhysicsWorldResource());
        encoded.put("SchemaVersion", new BsonInt32(3));
        encoded.remove("BodyBlocks");
        encoded.remove("JointBlocks");
        encoded.put("Bodies", BODY_ARRAY_CODEC.encode(new PersistentPhysicsBodyState[] { body }, new ExtraInfo()));

        PersistentPhysicsWorldResource decoded = PersistentPhysicsWorldResource.CODEC
            .decode(encoded, new ExtraInfo());

        assertEquals(PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION, decoded.getSchemaVersion());
        assertEquals(1, decoded.getBodyCount());
        assertEquals(body.getBodyIdValue(), decoded.getBodies()[0].getBodyIdValue());
        assertTrue(decoded.isRuntimeRestorePending());
    }

    @Test
    void worldResourceCodecRejectsCorruptStateBlockPayload() {
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        resource.setBodies(new PersistentPhysicsBodyState[] { persistentBodyState() });
        BsonDocument encoded = encodeWorld(resource);
        BsonDocument block = encoded.getArray("BodyBlocks").getFirst().asDocument();
        byte[] payload = block.getBinary("Payload").getData();
        payload[payload.length / 2] ^= 0x01;
        block.put("Payload", new BsonBinary(payload));

        assertThrows(RuntimeException.class,
            () -> PersistentPhysicsWorldResource.CODEC.decode(encoded, new ExtraInfo()));
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
        return encodeSpace(PersistentPhysicsSpaceState.from(space, PhysicsSpaceSettings.defaults()));
    }

    private static BsonDocument encodeSpace(PersistentPhysicsSpaceState state) {
        return PersistentPhysicsSpaceState.CODEC.encode(state, new ExtraInfo()).asDocument();
    }

    private static BsonDocument encodeWorld(PersistentPhysicsWorldResource resource) {
        return PersistentPhysicsWorldResource.CODEC.encode(resource, new ExtraInfo()).asDocument();
    }

    private static PersistentPhysicsBodyState persistentBodyState() {
        PhysicsSpace space = new FakePhysicsBackend("test:body-state-block-"
            + BACKEND_COUNTER.incrementAndGet()).createSpace();
        PhysicsBody body = space.createBox(0.5f, 0.75f, 1.0f, 1.0f);
        PhysicsBodyId bodyId = PhysicsBodyId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        PhysicsWorldResource.BodyRegistration registration = new PhysicsWorldResource.BodyRegistration(
            bodyId,
            body,
            space.getId(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        return PersistentPhysicsBodyState.from(registration);
    }

    private static PersistentPhysicsJointState persistentJointState(int spaceId) {
        PersistentPhysicsJointState joint = new PersistentPhysicsJointState();
        joint.setSpaceId(spaceId);
        joint.setBodyAId(PhysicsBodyId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")));
        joint.setBodyBId(PhysicsBodyId.of(UUID.fromString("00000000-0000-0000-0000-000000000002")));
        joint.setType(PhysicsJointType.FIXED);
        return joint;
    }
}
