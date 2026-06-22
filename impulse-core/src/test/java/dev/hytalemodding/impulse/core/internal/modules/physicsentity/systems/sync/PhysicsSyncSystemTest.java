package dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.math.PhysicsVisualPoseMath;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSyncSystemTest {

    @Test
    void visualPredictionSecondsClampToConfiguredWindow() {
        PhysicsVisualSyncSettings settings = new PhysicsVisualSyncSettings();
        settings.setVisualSnapshotPredictionEnabled(true);
        settings.setVisualSnapshotPredictionMaxSeconds(0.05f);

        assertEquals(0.05f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings,
                1_100_000_000L,
                1_000_000_000L),
            0.0001f);
    }

    @Test
    void visualPredictionSecondsStayZeroWhenDisabledOrMissingFrame() {
        PhysicsVisualSyncSettings settings = new PhysicsVisualSyncSettings();
        settings.setVisualSnapshotPredictionEnabled(true);

        assertEquals(0.0f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings, 1_100_000_000L, 0L),
            0.0001f);
        settings.setVisualSnapshotPredictionEnabled(false);
        assertEquals(0.0f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings,
                1_100_000_000L,
                1_000_000_000L),
            0.0001f);
    }

    @Test
    void bodyTransformSyncOnlyAppliesToBodyAuthoritativeAttachments() {
        UUID bodyUuid = UUID.randomUUID();

        assertTrue(PhysicsTransformAuthority.shouldApplyBodyTransform(new BodyAttachmentComponent(bodyUuid,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY)));
        assertFalse(PhysicsTransformAuthority.shouldApplyBodyTransform(new BodyAttachmentComponent(bodyUuid,
            TransformAuthority.CONTROLLER,
            AttachmentLifecycle.EXTERNAL_ENTITY)));
        assertFalse(PhysicsTransformAuthority.shouldApplyBodyTransform(new BodyAttachmentComponent(bodyUuid,
            TransformAuthority.ENTITY_KINEMATIC,
            AttachmentLifecycle.EXTERNAL_ENTITY)));
    }

    @Test
    void bodyTransformSyncSkipsUnchangedHytaleTransformWrites() {
        UUID bodyUuid = UUID.randomUUID();
        TransformComponent transform = new TransformComponent();
        BodyAttachmentComponent attachment = new BodyAttachmentComponent(bodyUuid,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY);
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshot.of(bodyUuid,
            UUID.randomUUID(),
            PhysicsBodyType.DYNAMIC,
            1.0f,
            2.0f,
            3.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            false);
        PhysicsSyncSystem.Scratch scratch = new PhysicsSyncSystem.Scratch();

        assertTrue(PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot,
            scratch));
        assertFalse(PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot,
            scratch));
        assertEquals(1.0, transform.getPosition().x, 0.0001);
        assertEquals(2.0, transform.getPosition().y, 0.0001);
        assertEquals(3.0, transform.getPosition().z, 0.0001);
        assertEquals(0.0f, transform.getRotation().x(), 0.0001f);
        assertEquals(0.0f, transform.getRotation().y(), 0.0001f);
        assertEquals(0.0f, transform.getRotation().z(), 0.0001f);
    }

    @Test
    void policyBackedSyncSkipsGeneratedProxyFarFromPlayers() {
        UUID bodyUuid = UUID.randomUUID();
        UUID spaceUuid = UUID.randomUUID();
        TransformComponent transform = new TransformComponent();
        BodyAttachmentComponent attachment = new BodyAttachmentComponent(bodyUuid,
            TransformAuthority.BODY,
            AttachmentLifecycle.GENERATED_PROXY);
        PhysicsSyncSystem.Scratch scratch = new PhysicsSyncSystem.Scratch();
        PhysicsBodyRuntimeState.BodySyncState syncState =
            new PhysicsBodyRuntimeState.BodySyncState();

        PhysicsSyncSystem.SyncResult initial = PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot(bodyUuid, spaceUuid, 10.0f, false),
            scratch,
            syncState,
            new PhysicsVisualSyncSettings(),
            PhysicsSyncPolicy.SyncRangeTier.NEAR,
            0.05f);
        PhysicsSyncSystem.SyncResult skipped = PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot(bodyUuid, spaceUuid, 10.5f, false),
            scratch,
            syncState,
            new PhysicsVisualSyncSettings(),
            PhysicsSyncPolicy.SyncRangeTier.FAR,
            0.05f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.INITIAL, initial.decision());
        assertTrue(initial.transformChanged());
        assertEquals(PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_RANGE, skipped.decision());
        assertFalse(skipped.transformChanged());
        assertEquals(10.0, transform.getPosition().x, 0.0001);
        assertEquals(0.05f, syncState.getSecondsSinceSync(), 0.0001f);
    }

    @Test
    void policyBackedSyncTreatsRetargetedBodyAsInitial() {
        UUID firstBodyUuid = UUID.randomUUID();
        UUID secondBodyUuid = UUID.randomUUID();
        UUID spaceUuid = UUID.randomUUID();
        TransformComponent transform = new TransformComponent();
        BodyAttachmentComponent attachment = new BodyAttachmentComponent(firstBodyUuid,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY);
        PhysicsSyncSystem.Scratch scratch = new PhysicsSyncSystem.Scratch();
        PhysicsBodyRuntimeState.BodySyncState syncState =
            new PhysicsBodyRuntimeState.BodySyncState();

        PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot(firstBodyUuid, spaceUuid, 10.0f, false),
            scratch,
            syncState,
            new PhysicsVisualSyncSettings(),
            PhysicsSyncPolicy.SyncRangeTier.NEAR,
            0.05f);
        PhysicsSyncSystem.SyncResult retargeted = PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot(secondBodyUuid, spaceUuid, 10.01f, false),
            scratch,
            syncState,
            new PhysicsVisualSyncSettings(),
            PhysicsSyncPolicy.SyncRangeTier.NEAR,
            0.05f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.INITIAL, retargeted.decision());
        assertTrue(retargeted.transformChanged());
        assertEquals(10.01, transform.getPosition().x, 0.0001);
    }

    @Test
    void rotatingNearDynamicBodiesDoNotUseLowSpeedDeadzone() {
        UUID bodyUuid = UUID.randomUUID();
        UUID spaceUuid = UUID.randomUUID();
        TransformComponent transform = new TransformComponent();
        BodyAttachmentComponent attachment = new BodyAttachmentComponent(bodyUuid,
            TransformAuthority.BODY,
            AttachmentLifecycle.EXTERNAL_ENTITY);
        PhysicsSyncSystem.Scratch scratch = new PhysicsSyncSystem.Scratch();
        PhysicsBodyRuntimeState.BodySyncState syncState =
            new PhysicsBodyRuntimeState.BodySyncState();

        PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot(bodyUuid, spaceUuid, 10.0f, new Quaternionf(), false),
            scratch,
            syncState,
            new PhysicsVisualSyncSettings(),
            PhysicsSyncPolicy.SyncRangeTier.NEAR,
            0.05f);

        PhysicsSyncSystem.SyncResult rotated = PhysicsSyncSystem.applyPhysicsStoreSnapshot(transform,
            attachment,
            snapshot(bodyUuid,
                spaceUuid,
                10.0f,
                new Quaternionf().rotateY((float) Math.toRadians(4.0)),
                false),
            scratch,
            syncState,
            new PhysicsVisualSyncSettings(),
            PhysicsSyncPolicy.SyncRangeTier.NEAR,
            0.05f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.THRESHOLD, rotated.decision());
        assertTrue(rotated.transformChanged());
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

        Vector3d invertedCenter = PhysicsVisualPoseMath.bodyCenterFromVisualPose(
            new Vector3d(visualPosition.x, visualPosition.y, visualPosition.z),
            new Quaterniond(bodyRotation),
            centerOfMassOffsetY,
            new Vector3f(1.0f, 0.0f, 0.0f),
            new Vector3d());

        assertEquals(bodyCenter.x, invertedCenter.x, 0.0001f);
        assertEquals(bodyCenter.y, invertedCenter.y, 0.0001f);
        assertEquals(bodyCenter.z, invertedCenter.z, 0.0001f);
    }

    @Test
    void attachmentVisualOriginOffsetOverridesBodyShapeOffset() {
        BodyAttachmentComponent attachment = BodyAttachmentComponent.externalEntity(
            UUID.randomUUID(),
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

    private static PhysicsBodySnapshot snapshot(@Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        float positionX,
        boolean sleeping) {
        return snapshot(bodyUuid, spaceUuid, positionX, new Quaternionf(), sleeping);
    }

    private static PhysicsBodySnapshot snapshot(@Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        float positionX,
        @Nonnull Quaternionf rotation,
        boolean sleeping) {
        return PhysicsBodySnapshot.of(bodyUuid,
            spaceUuid,
            PhysicsBodyType.DYNAMIC,
            positionX,
            2.0f,
            3.0f,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            sleeping);
    }
}
