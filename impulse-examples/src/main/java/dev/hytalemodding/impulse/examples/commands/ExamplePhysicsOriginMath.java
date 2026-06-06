package dev.hytalemodding.impulse.examples.commands;

import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

final class ExamplePhysicsOriginMath {

    private ExamplePhysicsOriginMath() {
    }

    @Nonnull
    static Vector3d visualPositionFromBodyCenter(@Nonnull Vector3d bodyCenter,
        @Nonnull PhysicsShapeSpec shape) {
        Objects.requireNonNull(bodyCenter, "bodyCenter");
        Objects.requireNonNull(shape, "shape");
        return new Vector3d(bodyCenter.x,
            bodyCenter.y - shape.centerOfMassOffsetY(),
            bodyCenter.z);
    }
}
