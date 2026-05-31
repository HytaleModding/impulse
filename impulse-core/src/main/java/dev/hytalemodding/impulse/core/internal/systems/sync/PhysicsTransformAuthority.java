package dev.hytalemodding.impulse.core.internal.systems.sync;

import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.TransformAuthority;
import javax.annotation.Nonnull;

final class PhysicsTransformAuthority {

    private PhysicsTransformAuthority() {
    }

    static boolean shouldApplyBodyTransform(@Nonnull PhysicsBodyAttachmentComponent attachment) {
        return attachment.getTransformAuthority() == TransformAuthority.BODY;
    }
}
