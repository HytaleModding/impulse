package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PersistentPhysicsStoreResourceTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();
    private static final UUID SPACE_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BODY_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SHAPE_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MATERIAL_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID COLLIDER_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void storeResourceCodecPreservesDtoRows() {
        PersistentPhysicsStoreResource resource = validResource(registeredBackendId("codec"));

        BsonDocument encoded = PersistentPhysicsStoreResource.CODEC.encode(resource,
            new ExtraInfo()).asDocument();
        PersistentPhysicsStoreResource decoded = PersistentPhysicsStoreResource.CODEC.decode(encoded,
            new ExtraInfo());

        assertEquals(PersistentPhysicsStoreResource.CURRENT_SCHEMA_VERSION,
            encoded.getInt32("SchemaVersion").getValue());
        assertNotNull(decoded);
        assertEquals(1, decoded.getSpaces().length);
        assertEquals(1, decoded.getBodies().length);
        assertEquals(1, decoded.getColliders().length);
        assertEquals(1, decoded.getShapes().length);
        assertEquals(1, decoded.getMaterials().length);
        assertEquals(BODY_UUID, decoded.getBodies()[0].getBodyUuid());
        assertEquals(COLLIDER_UUID, decoded.getBodies()[0].getColliderUuids()[0]);
    }

    @Test
    void storeResourceCodecRejectsOutdatedSchemaVersion() {
        PersistentPhysicsStoreResource resource = validResource(registeredBackendId("old-schema"));
        BsonDocument encoded = PersistentPhysicsStoreResource.CODEC.encode(resource,
            new ExtraInfo()).asDocument();
        encoded.put("SchemaVersion", new BsonInt32(1));

        assertValidationFails(
            () -> PersistentPhysicsStoreResource.CODEC.decode(encoded, new ExtraInfo()),
            "Must be greater than or equal to "
                + PersistentPhysicsStoreResource.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void preflightAcceptsAvailableRuntimeProviderWithoutLegacyBackendRegistration() {
        PersistentPhysicsStoreResource resource = validResource(registeredBackendId("preflight"));

        PersistentPhysicsStorePreflight.Result result = resource.preflight();

        assertTrue(result.valid(), () -> result.errors().toString());
    }

    @Test
    void preflightRejectsDuplicateBodyUuidBeforeBackendHydration() {
        PersistentPhysicsStoreResource resource = validResource(registeredBackendId("duplicate-body"));
        PersistentBodyDto body = resource.getBodies()[0];
        resource.setBodies(new PersistentBodyDto[] { body, body.copy() });

        PersistentPhysicsStorePreflight.Result result = resource.preflight();

        assertTrue(result.errors().stream()
            .anyMatch(error -> error.contains("Duplicate PhysicsStore body UUID")));
    }

    @Test
    void bodyDtoCodecRejectsInvalidMassInsteadOfDefaulting() {
        BsonDocument encoded = PersistentBodyDto.CODEC.encode(bodyDto(), new ExtraInfo()).asDocument();
        encoded.put("Mass", new BsonDouble(Double.NaN));

        assertValidationFails(
            () -> PersistentBodyDto.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted body mass must be finite and >= 0");
    }

    @Test
    void shapeDtoCodecRejectsUnsupportedNonFinitePlaneGroundY() {
        BsonDocument encoded = PersistentShapeDto.CODEC.encode(shapeDto(), new ExtraInfo()).asDocument();
        encoded.put("GroundY", new BsonDouble(Double.NaN));

        assertValidationFails(
            () -> PersistentShapeDto.CODEC.decode(encoded, new ExtraInfo()),
            "Persisted shape ground Y must be finite");
    }

    private static PersistentPhysicsStoreResource validResource(String backendId) {
        PersistentPhysicsStoreResource resource = new PersistentPhysicsStoreResource();
        resource.setSpaces(new PersistentSpaceDto[] {
            new PersistentSpaceDto(SPACE_UUID, backendId, new Vector3f(0.0f, -9.81f, 0.0f))
        });
        resource.setBodies(new PersistentBodyDto[] { bodyDto() });
        resource.setShapes(new PersistentShapeDto[] { shapeDto() });
        resource.setMaterials(new PersistentMaterialDto[] {
            new PersistentMaterialDto(MATERIAL_UUID, 0.5f, 0.0f)
        });
        resource.setColliders(new PersistentColliderDto[] {
            new PersistentColliderDto(COLLIDER_UUID,
                BODY_UUID,
                SHAPE_UUID,
                MATERIAL_UUID,
                new Vector3f(),
                new Quaternionf(),
                false,
                0,
                -1)
        });
        return resource;
    }

    private static PersistentBodyDto bodyDto() {
        return new PersistentBodyDto(BODY_UUID,
            SPACE_UUID,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT,
            PhysicsBodyType.DYNAMIC,
            1.0f,
            0.0f,
            0.0f,
            false,
            new UUID[] { COLLIDER_UUID },
            new PersistentBodyRuntimeStateDto(new Vector3f(),
                new Quaternionf(),
                new Vector3f(),
                new Vector3f(),
                false));
    }

    private static PersistentShapeDto shapeDto() {
        return new PersistentShapeDto(SHAPE_UUID,
            ShapeType.BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            PhysicsAxis.Y,
            0.0f,
            "");
    }

    private static String registeredBackendId(String suffix) {
        String backendId = "test:persistent-store-" + suffix + "-"
            + BACKEND_COUNTER.incrementAndGet();
        Impulse.registerRuntimeProvider(new FakePhysicsBackendRuntimeProvider(backendId));
        return backendId;
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
}
