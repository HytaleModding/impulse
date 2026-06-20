package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import java.lang.reflect.Field;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class RapierNativeBodyRemovalTest {

    @Test
    void nativeBodyRemovalDropsAttachedJointHandles() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int spaceId = runtime.createSpace(new SpaceId(4));
        try {
            long firstBodyId = createBox(runtime, spaceId);
            long secondBodyId = createBox(runtime, spaceId);
            runtime.createJoint(spaceId,
                BackendRuntimeCodes.jointTypeCode(BackendJointType.FIXED),
                firstBodyId,
                secondBodyId,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
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
                false,
                0.0f,
                0.0f);
            long nativeSpaceHandle = nativeSpaceHandle(runtime, spaceId);
            long nativeBodyHandle = nativeBodyHandle(runtime, spaceId, firstBodyId);

            assertEquals(1, RapierNative.jointHandleCountNative(nativeSpaceHandle));

            RapierNative.removeBodyNative(nativeSpaceHandle, nativeBodyHandle);

            assertEquals(0, RapierNative.jointHandleCountNative(nativeSpaceHandle));
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static long createBox(@Nonnull PhysicsBackendRuntime runtime, int spaceId) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
            0.5f,
            0.5f,
            0.5f,
            -1.0f,
            -1.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static long nativeSpaceHandle(@Nonnull PhysicsBackendRuntime runtime, int spaceId) {
        Object state = spaceState(runtime, spaceId);
        try {
            Field handle = state.getClass().getDeclaredField("nativeSpaceHandle");
            handle.setAccessible(true);
            return handle.getLong(state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier native space handle", exception);
        }
    }

    private static long nativeBodyHandle(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        Object body = bodyState(runtime, spaceId, bodyId);
        try {
            Field handle = body.getClass().getDeclaredField("nativeBodyHandle");
            handle.setAccessible(true);
            return handle.getLong(body);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier native body handle", exception);
        }
    }

    private static Object bodyState(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        Object state = spaceState(runtime, spaceId);
        try {
            Field bodies = state.getClass().getDeclaredField("bodiesById");
            bodies.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, ?> bodiesById = (Map<Long, ?>) bodies.get(state);
            Object body = bodiesById.get(bodyId);
            if (body == null) {
                throw new AssertionError("No cached body " + bodyId);
            }
            return body;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier body cache", exception);
        }
    }

    private static Object spaceState(@Nonnull PhysicsBackendRuntime runtime, int spaceId) {
        try {
            Field spaces = runtime.getClass().getDeclaredField("spaces");
            spaces.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, ?> spacesById = (Map<Integer, ?>) spaces.get(runtime);
            Object state = spacesById.get(spaceId);
            if (state == null) {
                throw new AssertionError("No cached space " + spaceId);
            }
            return state;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier runtime cache", exception);
        }
    }
}
