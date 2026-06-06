package dev.hytalemodding.impulse.core.internal.systems.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.math.PhysicsVisualPoseMath;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSyncSystemTest {

    @Test
    void visualPredictionSecondsClampToConfiguredWindow() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(true);
        settings.getVisualSyncSettings().setVisualSnapshotPredictionMaxSeconds(0.05f);

        assertEquals(0.05f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings,
                1_100_000_000L,
                1_000_000_000L),
            0.0001f);
    }

    @Test
    void visualPredictionSecondsStayZeroWhenDisabledOrMissingFrame() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(true);

        assertEquals(0.0f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings, 1_100_000_000L, 0L),
            0.0001f);
        settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(false);
        assertEquals(0.0f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings,
                1_100_000_000L,
                1_000_000_000L),
            0.0001f);
    }

    @Test
    void bodyTransformSyncOnlyAppliesToBodyAuthoritativeAttachments() {
        RigidBodyKey bodyKey = RigidBodyKey.random();
        SpaceId spaceId = new SpaceId(1);

        assertTrue(PhysicsTransformAuthority.shouldApplyBodyTransform(new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY)));
        assertFalse(PhysicsTransformAuthority.shouldApplyBodyTransform(new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            TransformAuthority.CONTROLLER,
            AttachmentLifecycle.EXTERNAL_ENTITY)));
        assertFalse(PhysicsTransformAuthority.shouldApplyBodyTransform(new PhysicsBodyAttachmentComponent(bodyKey,
            spaceId,
            TransformAuthority.ENTITY_KINEMATIC,
            AttachmentLifecycle.EXTERNAL_ENTITY)));
    }

    @Test
    void visualPositionKeepsCenterOfMassOffsetWorldUp() {
        Vector3f visualPosition = PhysicsVisualPoseMath.visualPositionFromBodyPose(new Vector3f(10.0f,
                20.0f,
                30.0f),
            new Quaternionf().rotateZ((float) (Math.PI / 2.0)),
            0.5f,
            new Vector3f(),
            new Vector3f());

        assertEquals(10.0f, visualPosition.x, 0.0001f);
        assertEquals(19.5f, visualPosition.y, 0.0001f);
        assertEquals(30.0f, visualPosition.z, 0.0001f);
    }

    @Test
    void visualPositionRotatesLocalAttachmentOffset() {
        Vector3f visualPosition = PhysicsVisualPoseMath.visualPositionFromBodyPose(new Vector3f(10.0f,
                20.0f,
                30.0f),
            new Quaternionf().rotateZ((float) (Math.PI / 2.0)),
            0.5f,
            new Vector3f(1.0f, 0.0f, 0.0f),
            new Vector3f());

        assertEquals(10.0f, visualPosition.x, 0.0001f);
        assertEquals(20.5f, visualPosition.y, 0.0001f);
        assertEquals(30.0f, visualPosition.z, 0.0001f);
    }

    @Test
    void bodyCenterInvertsWorldUpCenterOfMassOffsetAndRotatedLocalOffset() {
        Vector3f bodyCenter = new Vector3f(10.0f, 20.0f, 30.0f);
        Quaternionf bodyRotation = new Quaternionf().rotateZ((float) (Math.PI / 2.0));
        float centerOfMassOffsetY = 0.5f;
        Vector3f visualPosition = PhysicsVisualPoseMath.visualPositionFromBodyPose(bodyCenter,
            bodyRotation,
            centerOfMassOffsetY,
            new Vector3f(1.0f, 0.0f, 0.0f),
            new Vector3f());

        org.joml.Vector3d invertedCenter = PhysicsVisualPoseMath.bodyCenterFromVisualPose(
            new org.joml.Vector3d(visualPosition.x, visualPosition.y, visualPosition.z),
            new org.joml.Quaterniond(bodyRotation),
            centerOfMassOffsetY,
            new Vector3f(1.0f, 0.0f, 0.0f),
            new org.joml.Vector3d());

        assertEquals(bodyCenter.x, invertedCenter.x, 0.0001f);
        assertEquals(bodyCenter.y, invertedCenter.y, 0.0001f);
        assertEquals(bodyCenter.z, invertedCenter.z, 0.0001f);
    }

    @Test
    void attachmentVisualOriginOffsetOverridesBodyShapeOffset() {
        PhysicsBodyAttachmentComponent attachment = PhysicsBodyAttachmentComponent.externalEntity(
            RigidBodyKey.random(),
            null,
            new Vector3f(0.0f, -0.5f, 0.0f),
            new Quaternionf(),
            0.5f);

        Vector3f visualPosition = PhysicsVisualPoseMath.visualPositionFromBodyPose(new Vector3f(10.0f,
                20.0f,
                30.0f),
            new Quaternionf(),
            attachment.resolveVisualOriginOffsetY(1.0f),
            attachment.getLocalPositionOffset(),
            new Vector3f());

        assertEquals(10.0f, visualPosition.x, 0.0001f);
        assertEquals(19.0f, visualPosition.y, 0.0001f);
        assertEquals(30.0f, visualPosition.z, 0.0001f);
        assertEquals(0.5f, attachment.clone().getVisualOriginOffsetY(), 0.0001f);
    }
}
