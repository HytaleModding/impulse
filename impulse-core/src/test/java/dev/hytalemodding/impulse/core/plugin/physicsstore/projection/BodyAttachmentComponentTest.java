package dev.hytalemodding.impulse.core.plugin.physicsstore.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class BodyAttachmentComponentTest {

    @Test
    void attachmentClonesUuidAndProjectionState() {
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000042");
        BodyAttachmentComponent attachment = BodyAttachmentComponent.impulseOwnedVisual(
            bodyUuid,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf().rotateY(0.5f),
            0.25f);

        BodyAttachmentComponent copy = attachment.clone();

        assertNotSame(attachment, copy);
        assertEquals(bodyUuid, copy.getBodyUuid());
        assertEquals(BodyAttachmentComponent.TransformAuthority.BODY,
            copy.getTransformAuthority());
        assertEquals(BodyAttachmentComponent.AttachmentLifecycle.IMPULSE_OWNED_VISUAL,
            copy.getLifecycle());
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), copy.getLocalPositionOffset());
        assertEquals(0.25f, copy.getVisualOriginOffsetY(), 0.0001f);
        assertTrue(copy.shouldRemoveEntityWhenBodyMissing());
    }

    @Test
    void externalEntityDefaultsToBodyAuthorityAndKeepsEntityWhenMissing() {
        UUID bodyUuid = UUID.randomUUID();

        BodyAttachmentComponent attachment = BodyAttachmentComponent.externalEntity(bodyUuid);

        assertEquals(bodyUuid, attachment.getBodyUuid());
        assertEquals(BodyAttachmentComponent.TransformAuthority.BODY,
            attachment.getTransformAuthority());
        assertEquals(BodyAttachmentComponent.AttachmentLifecycle.EXTERNAL_ENTITY,
            attachment.getLifecycle());
        assertFalse(attachment.shouldRemoveEntityWhenBodyMissing());
    }

    @Test
    void normalizesInvalidVisualOriginOffsetToBodyValue() {
        BodyAttachmentComponent attachment = BodyAttachmentComponent.generatedProxy(
            UUID.randomUUID(),
            new Vector3f(),
            new Quaternionf(),
            Float.NaN);

        assertEquals(-1.0f, attachment.getVisualOriginOffsetY(), 0.0001f);
        assertEquals(0.75f, attachment.resolveVisualOriginOffsetY(0.75f), 0.0001f);
    }

    @Test
    void rejectsMissingBodyUuid() {
        assertThrows(NullPointerException.class,
            () -> BodyAttachmentComponent.externalEntity(null));
    }
}
