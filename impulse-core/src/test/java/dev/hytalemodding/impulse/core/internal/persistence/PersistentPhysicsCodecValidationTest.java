package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PersistentPhysicsCodecValidationTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void spaceStateCodecValidatorChecksCrossFieldSettingsAfterDecode() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualSyncRadii(64, 128);
        SpaceFixture fixture = spaceFixture("test:space-codec-validation-", settings);
        BsonDocument encoded = encodeSpace(PersistentPhysicsSpaceState.from(fixture.binding(), settings));
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
    void worldResourceFieldValidatorsRejectUnknownEventCollectionMode() {
        BsonDocument encoded = encodeWorld(new PersistentPhysicsWorldResource());
        encoded.put("EventCollectionMode", new BsonString("bogus"));

        assertValidationFails(
            () -> PersistentPhysicsWorldResource.CODEC.decode(encoded, new ExtraInfo()),
            "Persistent physics event collection mode is unknown: bogus");
    }

    @Test
    void worldResourceCodecPreservesStepSchedulingMode() {
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        PhysicsWorldSettings settings = resource.getWorldSettings();
        settings.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT);
        resource.setWorldSettings(settings);

        BsonDocument encoded = encodeWorld(resource);
        PersistentPhysicsWorldResource decoded = PersistentPhysicsWorldResource.CODEC
            .decode(encoded, new ExtraInfo());

        assertEquals("accumulate_pending_dt",
            encoded.getString("StepSchedulingMode").getValue());
        Assertions.assertNotNull(decoded);
        assertEquals(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
            decoded.getWorldSettings().getStepSchedulingMode());
    }

    @Test
    void worldResourceCodecPreservesEventCollectionMode() {
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        PhysicsWorldSettings settings = resource.getWorldSettings();
        settings.setEventCollectionMode(PhysicsEventCollectionMode.CONTACTS);
        resource.setWorldSettings(settings);

        BsonDocument encoded = encodeWorld(resource);
        PersistentPhysicsWorldResource decoded = PersistentPhysicsWorldResource.CODEC
            .decode(encoded, new ExtraInfo());

        assertEquals("contacts", encoded.getString("EventCollectionMode").getValue());
        Assertions.assertNotNull(decoded);
        assertEquals(PhysicsEventCollectionMode.CONTACTS,
            decoded.getWorldSettings().getEventCollectionMode());
    }

    @Test
    void worldResourceCodecWritesStateBlocksWithoutFlatBodiesAndJoints() {
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        PersistentPhysicsBodyState body = persistentBodyState();
        PersistentPhysicsJointState joint = persistentJointState(body.getSpaceId());
        resource.setBodies(new PersistentPhysicsBodyState[] { body });
        resource.setJoints(new PersistentPhysicsJointState[] { joint });

        BsonDocument encoded = encodeWorld(resource);

        assertEquals(PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION,
            encoded.getInt32("SchemaVersion").getValue());
        assertFalse(encoded.containsKey("Bodies"));
        assertFalse(encoded.containsKey("Joints"));
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

    @Test
    void bodyStateCodecRejectsInvalidSpaceIdWithoutRuntimeFallback() {
        BsonDocument encoded = encodeBody(persistentBodyState());
        encoded.put("SpaceId", new BsonInt32(0));

        assertValidationFails(
            () -> PersistentPhysicsBodyState.CODEC.decode(encoded, new ExtraInfo()),
            "Must be greater than or equal to 1");
    }

    @Test
    void bodyStateCaptureRejectsMissingSpaceId() {
        PersistentPhysicsBodyState state = new PersistentPhysicsBodyState();

        assertThrows(NullPointerException.class, () -> state.updateFromSnapshot(boxSnapshot(0.0f), null));
    }

    @Test
    void bodyStateCaptureRejectsInvalidSpaceId() {
        PersistentPhysicsBodyState state = new PersistentPhysicsBodyState();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> state.updateFromSnapshot(boxSnapshot(0.0f), new SpaceId(0)));

        assertEquals("Persistent body state requires a positive explicit space id",
            exception.getMessage());
    }

    @Test
    void bodyStateCodecRejectsInvalidMassInsteadOfDefaulting() {
        BsonDocument encoded = encodeBody(persistentBodyState());
        encoded.put("Mass", new BsonDouble(Double.NaN));

        assertValidationFails(
            () -> PersistentPhysicsBodyState.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted body mass must be finite and >= 0");
    }

    @Test
    void bodyStateCodecRejectsZeroQuaternionInsteadOfRestoringInvalidRotation() {
        BsonDocument encoded = encodeBody(persistentBodyState());
        BsonDocument rotation = encoded.getDocument("Rotation");
        rotation.put("X", new BsonDouble(0.0));
        rotation.put("Y", new BsonDouble(0.0));
        rotation.put("Z", new BsonDouble(0.0));
        rotation.put("W", new BsonDouble(0.0));

        assertValidationFails(
            () -> PersistentPhysicsBodyState.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted quaternion must be finite and non-zero");
    }

    @Test
    void bodyStateCodecRejectsUnsupportedPersistentShape() {
        BsonDocument encoded = encodeBody(persistentBodyState());
        encoded.put("ShapeType", new BsonString("Voxels"));

        assertValidationFails(
            () -> PersistentPhysicsBodyState.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted body shape is unsupported: VOXELS");
    }

    @Test
    void bodyStateCodecDoesNotWritePlaneGroundY() {
        BsonDocument encoded = encodeBody(persistentPlaneBodyState(12.0f));

        assertFalse(encoded.containsKey("PlaneGroundY"));
    }

    @Test
    void worldResourceCodecRestoresPlaneHeightFromPositionY() {
        PersistentPhysicsBodyState state = persistentPlaneBodyState(12.0f);
        BsonDocument encodedBody = encodeBody(state);
        assertEquals(12.0, encodedBody.getDocument("Position").getDouble("Y").doubleValue(), 0.0001);
        PersistentPhysicsWorldResource resource = new PersistentPhysicsWorldResource();
        resource.setBodies(new PersistentPhysicsBodyState[] { state });

        PersistentPhysicsWorldResource decoded = PersistentPhysicsWorldResource.CODEC
            .decode(encodeWorld(resource), new ExtraInfo());
        SpaceFixture restore = spaceFixture("test:plane-restore-", PhysicsSpaceSettings.defaults());

        Assertions.assertNotNull(decoded);
        assertEquals(1, decoded.getBodyCount());
        PersistentPhysicsBodyState decodedBody = decoded.getBodies()[0];
        assertEquals(12.0f, decodedBody.getPosition().y, 0.0001f);
        long restoredBodyId = restore.binding()
            .runtime()
            .createBody(restore.binding().backendSpaceId(), decodedBody.toBackendBodySpec());
        decodedBody.applyToBody(restore.binding(), restoredBodyId);
        PhysicsBodySnapshot restored = restore.binding()
            .runtime()
            .bodySnapshot(restore.binding().backendSpaceId(), restoredBodyId)
            .orElseThrow()
            .snapshot();

        assertEquals(ShapeType.PLANE, restored.shapeType());
        assertEquals(12.0f, restored.positionY(), 0.0001f);
    }

    @Test
    void jointStateCodecRejectsInvalidSpaceIdInsteadOfDefaulting() {
        BsonDocument encoded = encodeJoint(persistentJointState(1));
        encoded.put("SpaceId", new BsonInt32(0));

        assertValidationFails(
            () -> PersistentPhysicsJointState.CODEC.decode(encoded, new ExtraInfo()),
            "Must be greater than or equal to 1");
    }

    @Test
    void jointStateCodecRejectsMissingAxisForAxisJoint() {
        BsonDocument encoded = encodeJoint(persistentJointState(1));
        encoded.put("Type", new BsonString("Hinge"));
        encoded.remove("Axis");

        assertValidationFails(
            () -> PersistentPhysicsJointState.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted HINGE joint requires an axis");
    }

    @Test
    void jointStateCodecRejectsInvalidSpringValuesInsteadOfDefaulting() {
        BsonDocument encoded = encodeJoint(persistentJointState(1));
        encoded.put("SpringStiffness", new BsonDouble(Double.NaN));

        assertValidationFails(
            () -> PersistentPhysicsJointState.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted joint spring stiffness must be finite and >= 0");
    }

    @Test
    void stateBlockCodecRejectsOldSchemaVersionBeforeInflating() {
        BsonDocument encoded = encodeStateBlock(bodyBlock());
        encoded.put("SchemaVersion", new BsonInt32(4));

        assertValidationFails(
            () -> PersistentPhysicsStateBlock.CODEC.decode(encoded, new ExtraInfo()),
            "Must be greater than or equal to "
                + PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void stateBlockCodecRejectsUnsupportedEnvelopeFields() {
        BsonDocument encoded = encodeStateBlock(bodyBlock());
        encoded.put("Codec", new BsonString("legacy-json-array"));

        assertValidationFails(
            () -> PersistentPhysicsStateBlock.CODEC.decode(encoded, new ExtraInfo()),
            "Persistent physics state block codec is unsupported");
    }

    @Test
    void stateBlockCodecRejectsEmptyPayloadInsteadOfDefaulting() {
        BsonDocument encoded = encodeStateBlock(bodyBlock());
        encoded.put("Payload", new BsonBinary(new byte[0]));

        assertValidationFails(
            () -> PersistentPhysicsStateBlock.CODEC.decode(encoded, new ExtraInfo()),
            "Persistent physics state block payload cannot be empty");
    }

    private static void assertValidationFails(Executable executable, String expectedMessagePart) {
        RuntimeException exception = assertThrows(RuntimeException.class, executable);

        assertTrue(exceptionContainsMessage(exception, expectedMessagePart),
            () -> "Expected validation message to contain: " + expectedMessagePart
                + "\nActual: " + exceptionMessages(exception));
    }

    private static boolean exceptionContainsMessage(Throwable throwable, String expectedMessagePart) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedMessagePart)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String exceptionMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (!messages.isEmpty()) {
                messages.append(" -> ");
            }
            messages.append(current.getMessage());
            current = current.getCause();
        }
        return messages.toString();
    }

    private static BsonDocument encodedSpaceState() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        SpaceFixture fixture = spaceFixture("test:space-codec-validation-", settings);
        return encodeSpace(PersistentPhysicsSpaceState.from(fixture.binding(), settings));
    }

    private static BsonDocument encodeSpace(PersistentPhysicsSpaceState state) {
        return PersistentPhysicsSpaceState.CODEC.encode(state, new ExtraInfo()).asDocument();
    }

    private static BsonDocument encodeBody(PersistentPhysicsBodyState state) {
        return PersistentPhysicsBodyState.CODEC.encode(state, new ExtraInfo()).asDocument();
    }

    private static BsonDocument encodeJoint(PersistentPhysicsJointState state) {
        return PersistentPhysicsJointState.CODEC.encode(state, new ExtraInfo()).asDocument();
    }

    private static BsonDocument encodeStateBlock(PersistentPhysicsStateBlock block) {
        return PersistentPhysicsStateBlock.CODEC.encode(block, new ExtraInfo()).asDocument();
    }

    private static BsonDocument encodeWorld(PersistentPhysicsWorldResource resource) {
        return PersistentPhysicsWorldResource.CODEC.encode(resource, new ExtraInfo()).asDocument();
    }

    private static PersistentPhysicsStateBlock bodyBlock() {
        return PersistentPhysicsStateBlock.bodyBlocks(new PersistentPhysicsBodyState[] {
            persistentBodyState()
        })[0];
    }

    private static PersistentPhysicsBodyState persistentBodyState() {
        RigidBodyKey bodyId = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        PhysicsBodyRegistration registration = new PhysicsBodyRegistration(
            bodyId,
            1L,
            new SpaceId(1),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        return PersistentPhysicsBodyState.from(registration, boxSnapshot(0.0f));
    }

    private static PersistentPhysicsBodyState persistentPlaneBodyState(float groundY) {
        RigidBodyKey bodyId = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        PhysicsBodyRegistration registration = new PhysicsBodyRegistration(
            bodyId,
            1L,
            new SpaceId(1),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        return PersistentPhysicsBodyState.from(registration, planeSnapshot(groundY));
    }

    @Nonnull
    private static SpaceFixture spaceFixture(@Nonnull String backendPrefix,
        @Nonnull PhysicsSpaceSettings settings) {
        FakePhysicsBackend backend = new FakePhysicsBackend(backendPrefix + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(), "test-world", settings);
        return new SpaceFixture(resource, space);
    }

    @Nonnull
    private static PhysicsBodySnapshot boxSnapshot(float y) {
        return new PhysicsBodySnapshot(new Vector3f(0.0f, y, 0.0f),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.75f, 1.0f),
            0.0f,
            0.0f,
            PhysicsAxis.Y);
    }

    @Nonnull
    private static PhysicsBodySnapshot planeSnapshot(float groundY) {
        return new PhysicsBodySnapshot(new Vector3f(0.0f, groundY, 0.0f),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.STATIC,
            false,
            false,
            0.0f,
            ShapeType.PLANE,
            null,
            0.0f,
            0.0f,
            PhysicsAxis.Y);
    }

    private record SpaceFixture(@Nonnull LegacyLiveHandleTestResource resource,
                                @Nonnull PhysicsSpace space) {

        @Nonnull
        private PhysicsSpaceBinding binding() {
            return resource.requireSpaceBinding(space.id());
        }
    }

    private static PersistentPhysicsJointState persistentJointState(int spaceId) {
        PersistentPhysicsJointState joint = new PersistentPhysicsJointState();
        joint.setSpaceId(spaceId);
        joint.setBodyAId(RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000001")));
        joint.setBodyBId(RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000002")));
        joint.setType(PhysicsJointType.FIXED);
        return joint;
    }
}
