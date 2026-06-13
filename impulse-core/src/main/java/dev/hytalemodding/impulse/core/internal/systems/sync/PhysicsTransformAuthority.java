package dev.hytalemodding.impulse.core.internal.systems.sync;

import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.TransformAuthority;
import javax.annotation.Nonnull;

final class PhysicsTransformAuthority {

    private PhysicsTransformAuthority() {
    }

    static boolean shouldApplyBodyTransform(@Nonnull BodyAttachmentComponent attachment) {
        return attachment.getTransformAuthority() == TransformAuthority.BODY;
    }
}
