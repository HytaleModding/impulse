package dev.hytalemodding.impulse.jolt;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.annotation.Nonnull;

final class PanamaJoltNativeLibrary implements JoltNativeLibrary {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final int GRAVITY_COMPONENTS = 3;

    private final MethodHandle createSpace;
    private final MethodHandle destroySpace;
    private final MethodHandle step;
    private final MethodHandle setGravity;
    private final MethodHandle getGravity;
    private final MethodHandle createBody;
    private final MethodHandle removeBody;
    private final MethodHandle containsBody;
    private final MethodHandle bodySnapshot;
    private final MethodHandle setBodyTransform;
    private final MethodHandle setBodyPosition;
    private final MethodHandle setBodyVelocity;
    private final MethodHandle setBodyType;
    private final MethodHandle setBodyDamping;
    private final MethodHandle setBodyFriction;
    private final MethodHandle setBodyRestitution;
    private final MethodHandle setBodyCollisionFilter;
    private final MethodHandle setBodySensor;
    private final MethodHandle setBodyContinuousCollision;
    private final MethodHandle isBodyContinuousCollisionEnabled;
    private final MethodHandle activateBody;
    private final MethodHandle sleepBody;
    private final MethodHandle applyBodyImpulse;
    private final MethodHandle applyBodyForce;
    private final MethodHandle createJoint;
    private final MethodHandle removeJoint;
    private final MethodHandle raycastClosest;
    private final MethodHandle raycastAll;
    private final MethodHandle contacts;
    private final MethodHandle contactCount;
    private final MethodHandle bodyCount;
    private final MethodHandle jointCount;

    private PanamaJoltNativeLibrary(@Nonnull SymbolLookup symbols) {
        Objects.requireNonNull(symbols, "symbols");
        createSpace = downcall(symbols,
            "impulse_jolt_create_space",
            FunctionDescriptor.of(JAVA_LONG));
        destroySpace = downcall(symbols,
            "impulse_jolt_destroy_space",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
        step = downcall(symbols,
            "impulse_jolt_step",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_FLOAT));
        setGravity = downcall(symbols,
            "impulse_jolt_set_gravity",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));
        getGravity = downcall(symbols,
            "impulse_jolt_get_gravity",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
        createBody = downcall(symbols,
            "impulse_jolt_create_body",
            FunctionDescriptor.of(JAVA_LONG,
                JAVA_LONG,
                JAVA_INT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT));
        removeBody = downcall(symbols,
            "impulse_jolt_remove_body",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
        containsBody = downcall(symbols,
            "impulse_jolt_contains_body",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
        bodySnapshot = downcall(symbols,
            "impulse_jolt_body_snapshot",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS));
        setBodyTransform = downcall(symbols,
            "impulse_jolt_set_body_transform",
            FunctionDescriptor.of(JAVA_INT,
                JAVA_LONG,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT));
        setBodyPosition = downcall(symbols,
            "impulse_jolt_set_body_position",
            FunctionDescriptor.of(JAVA_INT,
                JAVA_LONG,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT));
        setBodyVelocity = downcall(symbols,
            "impulse_jolt_set_body_velocity",
            FunctionDescriptor.of(JAVA_INT,
                JAVA_LONG,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT));
        setBodyType = downcall(symbols,
            "impulse_jolt_set_body_type",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT));
        setBodyDamping = downcall(symbols,
            "impulse_jolt_set_body_damping",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT));
        setBodyFriction = downcall(symbols,
            "impulse_jolt_set_body_friction",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_FLOAT));
        setBodyRestitution = downcall(symbols,
            "impulse_jolt_set_body_restitution",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_FLOAT));
        setBodyCollisionFilter = downcall(symbols,
            "impulse_jolt_set_body_collision_filter",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT));
        setBodySensor = downcall(symbols,
            "impulse_jolt_set_body_sensor",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT));
        setBodyContinuousCollision = downcall(symbols,
            "impulse_jolt_set_body_continuous_collision",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT));
        isBodyContinuousCollisionEnabled = downcall(symbols,
            "impulse_jolt_is_body_continuous_collision_enabled",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
        activateBody = downcall(symbols,
            "impulse_jolt_activate_body",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
        sleepBody = downcall(symbols,
            "impulse_jolt_sleep_body",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
        applyBodyImpulse = downcall(symbols,
            "impulse_jolt_apply_body_impulse",
            FunctionDescriptor.of(JAVA_INT,
                JAVA_LONG,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT));
        applyBodyForce = downcall(symbols,
            "impulse_jolt_apply_body_force",
            FunctionDescriptor.of(JAVA_INT,
                JAVA_LONG,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT));
        createJoint = downcall(symbols,
            "impulse_jolt_create_joint",
            FunctionDescriptor.of(JAVA_LONG,
                JAVA_LONG,
                JAVA_INT,
                JAVA_LONG,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT,
                JAVA_FLOAT,
                JAVA_FLOAT));
        removeJoint = downcall(symbols,
            "impulse_jolt_remove_joint",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
        raycastClosest = downcall(symbols,
            "impulse_jolt_raycast_closest",
            FunctionDescriptor.of(JAVA_INT,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                ADDRESS,
                ADDRESS));
        raycastAll = downcall(symbols,
            "impulse_jolt_raycast_all",
            FunctionDescriptor.of(JAVA_INT,
                JAVA_LONG,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_FLOAT,
                JAVA_INT,
                ADDRESS,
                ADDRESS));
        contacts = downcall(symbols,
            "impulse_jolt_contacts",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS));
        contactCount = downcall(symbols,
            "impulse_jolt_contact_count",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
        bodyCount = downcall(symbols,
            "impulse_jolt_body_count",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
        jointCount = downcall(symbols,
            "impulse_jolt_joint_count",
            FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    }

    @Nonnull
    static PanamaJoltNativeLibrary open(@Nonnull SymbolLookup symbols) {
        return new PanamaJoltNativeLibrary(symbols);
    }

    @Override
    public long createSpace() {
        try {
            return (long) createSpace.invokeExact();
        } catch (Throwable throwable) {
            throw nativeFailure("create space", throwable);
        }
    }

    @Override
    public void destroySpace(long spaceHandle) {
        requireSuccess("destroy space", invokeStatus(destroySpace, spaceHandle));
    }

    @Override
    public void step(long spaceHandle, float dt) {
        requireSuccess("step space", invokeStatus(step, spaceHandle, dt));
    }

    @Override
    public void setGravity(long spaceHandle, float x, float y, float z) {
        requireSuccess("set gravity", invokeStatus(setGravity, spaceHandle, x, y, z));
    }

    @Override
    public void getGravity(long spaceHandle, float[] out) {
        if (out.length < GRAVITY_COMPONENTS) {
            throw new IllegalArgumentException("Gravity output must have at least 3 entries");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(JAVA_FLOAT, GRAVITY_COMPONENTS);
            requireSuccess("get gravity", invokeStatus(getGravity, spaceHandle, output));
            out[0] = output.getAtIndex(JAVA_FLOAT, 0);
            out[1] = output.getAtIndex(JAVA_FLOAT, 1);
            out[2] = output.getAtIndex(JAVA_FLOAT, 2);
        }
    }

    @Override
    public long createBody(long spaceHandle,
        int shapeTypeCode,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        int axisCode,
        float groundY,
        float mass,
        int bodyTypeCode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW) {
        return invokeLong("create body",
            createBody,
            spaceHandle,
            shapeTypeCode,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axisCode,
            groundY,
            mass,
            bodyTypeCode,
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW);
    }

    @Override
    public void removeBody(long spaceHandle, long bodyHandle) {
        requireSuccess("remove body", invokeStatus(removeBody, spaceHandle, bodyHandle));
    }

    @Override
    public boolean containsBody(long spaceHandle, long bodyHandle) {
        return invokeInt("contains body", containsBody, spaceHandle, bodyHandle) != 0;
    }

    @Override
    public boolean bodySnapshot(long spaceHandle, long bodyHandle, JoltBodySnapshot out) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment floats = arena.allocate(JAVA_FLOAT, JoltBodySnapshot.FLOAT_FIELD_COUNT);
            MemorySegment ints = arena.allocate(JAVA_INT, JoltBodySnapshot.INT_FIELD_COUNT);
            int status = invokeStatus(bodySnapshot, spaceHandle, bodyHandle, floats, ints);
            if (status == 0) {
                return false;
            }
            readSnapshot(floats, ints, out);
            return true;
        }
    }

    @Override
    public void setBodyTransform(long spaceHandle,
        long bodyHandle,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW) {
        requireSuccess("set body transform",
            invokeStatus("set body transform",
                setBodyTransform,
                spaceHandle,
                bodyHandle,
                positionX,
                positionY,
                positionZ,
                rotationX,
                rotationY,
                rotationZ,
                rotationW));
    }

    @Override
    public void setBodyPosition(long spaceHandle, long bodyHandle, float x, float y, float z) {
        requireSuccess("set body position",
            invokeStatus("set body position", setBodyPosition, spaceHandle, bodyHandle, x, y, z));
    }

    @Override
    public void setBodyVelocity(long spaceHandle,
        long bodyHandle,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ) {
        requireSuccess("set body velocity",
            invokeStatus("set body velocity",
                setBodyVelocity,
                spaceHandle,
                bodyHandle,
                linearX,
                linearY,
                linearZ,
                angularX,
                angularY,
                angularZ));
    }

    @Override
    public void setBodyType(long spaceHandle, long bodyHandle, int bodyTypeCode) {
        requireSuccess("set body type",
            invokeStatus(setBodyType, spaceHandle, bodyHandle, bodyTypeCode));
    }

    @Override
    public void setBodyDamping(long spaceHandle,
        long bodyHandle,
        float linearDamping,
        float angularDamping) {
        requireSuccess("set body damping",
            invokeStatus("set body damping",
                setBodyDamping,
                spaceHandle,
                bodyHandle,
                linearDamping,
                angularDamping));
    }

    @Override
    public void setBodyFriction(long spaceHandle, long bodyHandle, float friction) {
        requireSuccess("set body friction",
            invokeStatus("set body friction", setBodyFriction, spaceHandle, bodyHandle, friction));
    }

    @Override
    public void setBodyRestitution(long spaceHandle, long bodyHandle, float restitution) {
        requireSuccess("set body restitution",
            invokeStatus("set body restitution",
                setBodyRestitution,
                spaceHandle,
                bodyHandle,
                restitution));
    }

    @Override
    public void setBodyCollisionFilter(long spaceHandle, long bodyHandle, int group, int mask) {
        requireSuccess("set body collision filter",
            invokeStatus("set body collision filter",
                setBodyCollisionFilter,
                spaceHandle,
                bodyHandle,
                group,
                mask));
    }

    @Override
    public void setBodySensor(long spaceHandle, long bodyHandle, boolean sensor) {
        requireSuccess("set body sensor",
            invokeStatus(setBodySensor, spaceHandle, bodyHandle, sensor ? 1 : 0));
    }

    @Override
    public void setBodyContinuousCollision(long spaceHandle, long bodyHandle, boolean enabled) {
        requireSuccess("set body continuous collision",
            invokeStatus(setBodyContinuousCollision, spaceHandle, bodyHandle, enabled ? 1 : 0));
    }

    @Override
    public boolean isBodyContinuousCollisionEnabled(long spaceHandle, long bodyHandle) {
        return invokeInt("is body continuous collision enabled",
            isBodyContinuousCollisionEnabled,
            spaceHandle,
            bodyHandle) != 0;
    }

    @Override
    public void activateBody(long spaceHandle, long bodyHandle) {
        requireSuccess("activate body", invokeStatus(activateBody, spaceHandle, bodyHandle));
    }

    @Override
    public void sleepBody(long spaceHandle, long bodyHandle) {
        requireSuccess("sleep body", invokeStatus(sleepBody, spaceHandle, bodyHandle));
    }

    @Override
    public void applyBodyImpulse(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        requireSuccess("apply body impulse",
            invokeStatus("apply body impulse",
                applyBodyImpulse,
                spaceHandle,
                bodyHandle,
                x,
                y,
                z,
                hasOffset ? 1 : 0,
                offsetX,
                offsetY,
                offsetZ,
                torque ? 1 : 0));
    }

    @Override
    public void applyBodyForce(long spaceHandle,
        long bodyHandle,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        requireSuccess("apply body force",
            invokeStatus("apply body force",
                applyBodyForce,
                spaceHandle,
                bodyHandle,
                x,
                y,
                z,
                hasOffset ? 1 : 0,
                offsetX,
                offsetY,
                offsetZ,
                torque ? 1 : 0));
    }

    @Override
    public long createJoint(long spaceHandle,
        int jointTypeCode,
        long bodyAHandle,
        long bodyBHandle,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce) {
        return invokeLong("create joint",
            createJoint,
            spaceHandle,
            jointTypeCode,
            bodyAHandle,
            bodyBHandle,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled ? 1 : 0,
            motorTargetVelocity,
            motorMaxForce);
    }

    @Override
    public void removeJoint(long spaceHandle, long jointHandle) {
        requireSuccess("remove joint", invokeStatus(removeJoint, spaceHandle, jointHandle));
    }

    @Override
    public int raycastClosest(long spaceHandle,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        long[] bodyHandleOut,
        float[] hitOut) {
        if (bodyHandleOut.length < 1) {
            throw new IllegalArgumentException("Closest ray body handle output must have at least 1 entry");
        }
        if (hitOut.length < JoltNativeLibrary.RAY_HIT_FLOAT_COUNT) {
            throw new IllegalArgumentException("Closest ray hit output must have at least 8 entries");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bodyHandles = arena.allocate(JAVA_LONG, 1);
            MemorySegment hits = arena.allocate(JAVA_FLOAT, JoltNativeLibrary.RAY_HIT_FLOAT_COUNT);
            int count = invokeInt("raycast closest",
                raycastClosest,
                spaceHandle,
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ,
                bodyHandles,
                hits);
            if (count <= 0) {
                return 0;
            }
            bodyHandleOut[0] = bodyHandles.getAtIndex(JAVA_LONG, 0);
            readFloats(hits, hitOut, JoltNativeLibrary.RAY_HIT_FLOAT_COUNT);
            return count;
        }
    }

    @Override
    public int raycastAll(long spaceHandle,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        int maxHits,
        long[] bodyHandles,
        float[] hits) {
        if (maxHits <= 0) {
            return 0;
        }
        if (bodyHandles.length < maxHits) {
            throw new IllegalArgumentException("Raycast body handle output is smaller than maxHits");
        }
        int floatCount = maxHits * JoltNativeLibrary.RAY_HIT_FLOAT_COUNT;
        if (hits.length < floatCount) {
            throw new IllegalArgumentException("Raycast hit output is smaller than maxHits");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeBodyHandles = arena.allocate(JAVA_LONG, maxHits);
            MemorySegment nativeHits = arena.allocate(JAVA_FLOAT, floatCount);
            int count = invokeInt("raycast all",
                raycastAll,
                spaceHandle,
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ,
                maxHits,
                nativeBodyHandles,
                nativeHits);
            int boundedCount = Math.min(Math.max(count, 0), maxHits);
            readLongs(nativeBodyHandles, bodyHandles, boundedCount);
            readFloats(nativeHits,
                hits,
                boundedCount * JoltNativeLibrary.RAY_HIT_FLOAT_COUNT);
            return count;
        }
    }

    @Override
    public int contacts(long spaceHandle, int maxContacts, long[] bodyHandles, float[] contacts) {
        if (maxContacts <= 0) {
            return 0;
        }
        int bodyHandleCount = maxContacts * JoltNativeLibrary.CONTACT_BODY_HANDLE_COUNT;
        if (bodyHandles.length < bodyHandleCount) {
            throw new IllegalArgumentException("Contact body handle output is smaller than maxContacts");
        }
        int floatCount = maxContacts * JoltNativeLibrary.CONTACT_FLOAT_COUNT;
        if (contacts.length < floatCount) {
            throw new IllegalArgumentException("Contact output is smaller than maxContacts");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeBodyHandles = arena.allocate(JAVA_LONG, bodyHandleCount);
            MemorySegment nativeContacts = arena.allocate(JAVA_FLOAT, floatCount);
            int count = invokeInt("contacts",
                this.contacts,
                spaceHandle,
                maxContacts,
                nativeBodyHandles,
                nativeContacts);
            int boundedCount = Math.min(Math.max(count, 0), maxContacts);
            readLongs(nativeBodyHandles,
                bodyHandles,
                boundedCount * JoltNativeLibrary.CONTACT_BODY_HANDLE_COUNT);
            readFloats(nativeContacts,
                contacts,
                boundedCount * JoltNativeLibrary.CONTACT_FLOAT_COUNT);
            return count;
        }
    }

    @Override
    public int contactCount(long spaceHandle) {
        return invokeInt(contactCount, spaceHandle);
    }

    @Override
    public int bodyCount(long spaceHandle) {
        return invokeInt(bodyCount, spaceHandle);
    }

    @Override
    public int jointCount(long spaceHandle) {
        return invokeInt(jointCount, spaceHandle);
    }

    @Nonnull
    private static MethodHandle downcall(@Nonnull SymbolLookup symbols,
        @Nonnull String symbol,
        @Nonnull FunctionDescriptor descriptor) {
        try {
            return LINKER.downcallHandle(symbols.findOrThrow(symbol), descriptor);
        } catch (NoSuchElementException exception) {
            throw new IllegalStateException("Jolt native symbol is missing: " + symbol, exception);
        }
    }

    private static int invokeStatus(@Nonnull MethodHandle handle, long spaceHandle) {
        try {
            return (int) handle.invokeExact(spaceHandle);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native status function", throwable);
        }
    }

    private static int invokeStatus(@Nonnull MethodHandle handle, long spaceHandle, float value) {
        try {
            return (int) handle.invokeExact(spaceHandle, value);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native status function", throwable);
        }
    }

    private static int invokeStatus(@Nonnull MethodHandle handle,
        long spaceHandle,
        float x,
        float y,
        float z) {
        try {
            return (int) handle.invokeExact(spaceHandle, x, y, z);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native status function", throwable);
        }
    }

    private static int invokeStatus(@Nonnull MethodHandle handle,
        long spaceHandle,
        @Nonnull MemorySegment output) {
        try {
            return (int) handle.invokeExact(spaceHandle, output);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native status function", throwable);
        }
    }

    private static int invokeStatus(@Nonnull MethodHandle handle,
        long spaceHandle,
        long bodyHandle,
        @Nonnull MemorySegment floats,
        @Nonnull MemorySegment ints) {
        try {
            return (int) handle.invokeExact(spaceHandle, bodyHandle, floats, ints);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native status function", throwable);
        }
    }

    private static int invokeStatus(@Nonnull MethodHandle handle, long spaceHandle, long bodyHandle) {
        try {
            return (int) handle.invokeExact(spaceHandle, bodyHandle);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native status function", throwable);
        }
    }

    private static int invokeStatus(@Nonnull MethodHandle handle,
        long spaceHandle,
        long bodyHandle,
        int value) {
        try {
            return (int) handle.invokeExact(spaceHandle, bodyHandle, value);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native status function", throwable);
        }
    }

    private static int invokeStatus(@Nonnull String operation,
        @Nonnull MethodHandle handle,
        Object... args) {
        try {
            return (int) handle.invokeWithArguments(args);
        } catch (Throwable throwable) {
            throw nativeFailure(operation, throwable);
        }
    }

    private static int invokeInt(@Nonnull MethodHandle handle, long spaceHandle) {
        try {
            return (int) handle.invokeExact(spaceHandle);
        } catch (Throwable throwable) {
            throw nativeFailure("invoke native int function", throwable);
        }
    }

    private static int invokeInt(@Nonnull String operation,
        @Nonnull MethodHandle handle,
        Object... args) {
        try {
            return (int) handle.invokeWithArguments(args);
        } catch (Throwable throwable) {
            throw nativeFailure(operation, throwable);
        }
    }

    private static long invokeLong(@Nonnull String operation,
        @Nonnull MethodHandle handle,
        Object... args) {
        try {
            return (long) handle.invokeWithArguments(args);
        } catch (Throwable throwable) {
            throw nativeFailure(operation, throwable);
        }
    }

    private static void readSnapshot(@Nonnull MemorySegment floats,
        @Nonnull MemorySegment ints,
        @Nonnull JoltBodySnapshot out) {
        out.positionX = floats.getAtIndex(JAVA_FLOAT, 0);
        out.positionY = floats.getAtIndex(JAVA_FLOAT, 1);
        out.positionZ = floats.getAtIndex(JAVA_FLOAT, 2);
        out.rotationX = floats.getAtIndex(JAVA_FLOAT, 3);
        out.rotationY = floats.getAtIndex(JAVA_FLOAT, 4);
        out.rotationZ = floats.getAtIndex(JAVA_FLOAT, 5);
        out.rotationW = floats.getAtIndex(JAVA_FLOAT, 6);
        out.linearVelocityX = floats.getAtIndex(JAVA_FLOAT, 7);
        out.linearVelocityY = floats.getAtIndex(JAVA_FLOAT, 8);
        out.linearVelocityZ = floats.getAtIndex(JAVA_FLOAT, 9);
        out.angularVelocityX = floats.getAtIndex(JAVA_FLOAT, 10);
        out.angularVelocityY = floats.getAtIndex(JAVA_FLOAT, 11);
        out.angularVelocityZ = floats.getAtIndex(JAVA_FLOAT, 12);
        out.mass = floats.getAtIndex(JAVA_FLOAT, 13);
        out.friction = floats.getAtIndex(JAVA_FLOAT, 14);
        out.restitution = floats.getAtIndex(JAVA_FLOAT, 15);
        out.linearDamping = floats.getAtIndex(JAVA_FLOAT, 16);
        out.angularDamping = floats.getAtIndex(JAVA_FLOAT, 17);
        out.centerOfMassOffsetY = floats.getAtIndex(JAVA_FLOAT, 18);
        out.halfExtentX = floats.getAtIndex(JAVA_FLOAT, 19);
        out.halfExtentY = floats.getAtIndex(JAVA_FLOAT, 20);
        out.halfExtentZ = floats.getAtIndex(JAVA_FLOAT, 21);
        out.radius = floats.getAtIndex(JAVA_FLOAT, 22);
        out.halfHeight = floats.getAtIndex(JAVA_FLOAT, 23);
        out.shapeTypeCode = ints.getAtIndex(JAVA_INT, 0);
        out.bodyTypeCode = ints.getAtIndex(JAVA_INT, 1);
        out.sleeping = ints.getAtIndex(JAVA_INT, 2) != 0;
        out.sensor = ints.getAtIndex(JAVA_INT, 3) != 0;
        out.collisionGroup = ints.getAtIndex(JAVA_INT, 4);
        out.collisionMask = ints.getAtIndex(JAVA_INT, 5);
        out.continuousCollisionEnabled = ints.getAtIndex(JAVA_INT, 6) != 0;
        out.hasBoxHalfExtents = ints.getAtIndex(JAVA_INT, 7) != 0;
        out.axisCode = ints.getAtIndex(JAVA_INT, 8);
    }

    private static void readLongs(@Nonnull MemorySegment source, long[] target, int count) {
        for (int index = 0; index < count; index++) {
            target[index] = source.getAtIndex(JAVA_LONG, index);
        }
    }

    private static void readFloats(@Nonnull MemorySegment source, float[] target, int count) {
        for (int index = 0; index < count; index++) {
            target[index] = source.getAtIndex(JAVA_FLOAT, index);
        }
    }

    private static void requireSuccess(@Nonnull String operation, int status) {
        if (status == 0) {
            throw new IllegalStateException("Jolt native operation failed: " + operation);
        }
    }

    @Nonnull
    private static IllegalStateException nativeFailure(@Nonnull String operation,
        @Nonnull Throwable throwable) {
        return new IllegalStateException("Failed to " + operation + " through Jolt native library",
            throwable);
    }
}
