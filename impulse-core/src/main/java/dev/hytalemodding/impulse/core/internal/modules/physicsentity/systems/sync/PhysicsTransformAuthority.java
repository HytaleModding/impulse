package dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.sync;

import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.TransformAuthority;
import javax.annotation.Nonnull;

final class PhysicsTransformAuthority {

    private PhysicsTransformAuthority() {
    }

    static boolean shouldApplyBodyTransform(@Nonnull BodyAttachmentComponent attachment) {
        return attachment.getTransformAuthority() == TransformAuthority.BODY;
    }
}
