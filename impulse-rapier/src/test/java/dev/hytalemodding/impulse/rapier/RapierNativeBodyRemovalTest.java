package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RapierNativeBodyRemovalTest {

    @Test
    void nativeBodyRemovalDropsAttachedJointHandles() {
        RapierBackend backend = new RapierBackend();
        backend.init();
        RapierSpace space = (RapierSpace) backend.createSpace(new SpaceId(4));
        try {
            PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            space.addBody(first);
            space.addBody(second);
            space.createFixedJoint(first, second, new Vector3f(), new Vector3f());

            assertEquals(1, RapierNative.jointHandleCountNative(space.getNativeSpaceHandle()));

            RapierNative.removeBodyNative(space.getNativeSpaceHandle(),
                ((RapierBody) first).getBodyHandle());

            assertEquals(0, RapierNative.jointHandleCountNative(space.getNativeSpaceHandle()));
            assertEquals(0, space.getRuntimeStats().jointCount());
        } finally {
            space.close();
        }
    }
}
