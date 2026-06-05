package dev.hytalemodding.impulse.examples.explosive;

import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public final class ExplosiveBlockPolicy {

    public static final String DEFAULT_BLOCK_TYPE = ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
    private static final int EMPTY_BLOCK_ID = 0;
    private static final int UNKNOWN_BLOCK_ID = 1;

    private ExplosiveBlockPolicy() {
    }

    @Nonnull
    public static Vector3f outwardImpulse(@Nonnull Vector3f center,
        @Nonnull Vector3f blockCenter,
        float strength,
        float verticalLift) {
        Vector3f direction = new Vector3f(blockCenter).sub(center);
        direction.y = 0.0f;
        float clampedStrength = Math.max(0.0f, strength);
        if (direction.lengthSquared() == 0.0f) {
            return new Vector3f(0.0f, clampedStrength, 0.0f);
        }
        direction.normalize().mul(clampedStrength);
        direction.y = clampedStrength * Math.max(0.0f, verticalLift);
        return direction;
    }

    public static boolean isFragmentCandidate(int blockId) {
        return blockId != EMPTY_BLOCK_ID && blockId != UNKNOWN_BLOCK_ID;
    }
}
