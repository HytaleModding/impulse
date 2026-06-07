package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBackendEventKind;
import dev.hytalemodding.impulse.api.PhysicsBackendEventSink;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsRuntimeStats;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsBackendEventsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityDescriptor;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsExtensionSettingsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsVoxelTerrainCapability;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RapierSpace implements PhysicsSpace {

    private static final Cleaner CLEANER = Cleaner.create();
    private static final PhysicsCapabilityId RAPIER_SOLVER_EXTENSION_ID =
        new PhysicsCapabilityId("impulse:rapier_solver");
    private static final String INTERNAL_PGS_ITERATIONS = "internalPgsIterations";
    private static final String MIN_ISLAND_SIZE = "minIslandSize";
    private static final PhysicsCapabilityDescriptor RAPIER_SOLVER_EXTENSION_DESCRIPTOR =
        new PhysicsCapabilityDescriptor(RAPIER_SOLVER_EXTENSION_ID,
            "Rapier solver",
            "Configures Rapier-specific solver batching settings");
    private static final List<PhysicsCapabilityDescriptor> CAPABILITY_DESCRIPTORS = List.of(
        PhysicsSolverTuningCapability.DESCRIPTOR,
        PhysicsActivationTuningCapability.DESCRIPTOR,
        PhysicsContinuousCollisionCapability.DESCRIPTOR,
        PhysicsBackendEventsCapability.DESCRIPTOR,
        PhysicsVoxelTerrainCapability.DESCRIPTOR,
        PhysicsExtensionSettingsCapability.DESCRIPTOR,
        RAPIER_SOLVER_EXTENSION_DESCRIPTOR);
    private static final int DEFAULT_SOLVER_ITERATIONS = 4;
    private static final int DEFAULT_INTERNAL_PGS_ITERATIONS = 1;
    private static final int DEFAULT_STABILIZATION_ITERATIONS = 1;
    private static final int DEFAULT_MIN_ISLAND_SIZE = 128;
    private static final int RAY_HIT_FLOATS = 10;
    private static final int CONTACT_FLOATS = 15;
    private static final int CONTACT_EVENT_FLOATS = 16;
    private static final int BODY_SNAPSHOT_FLOATS = 16;
    private static final int RUNTIME_STATS_VALUES = 10;
    private static final int STEP_PHASE_STATS_VALUES = 6;

    private final SpaceId id;
    private final RapierBackend backend;
    private long nativeSpaceHandle;
    private final Cleaner.Cleanable cleanable;
    private final PhysicsSolverTuningCapability solverTuningCapability =
        new RapierSolverTuningCapability();
    private final PhysicsActivationTuningCapability activationTuningCapability =
        new RapierActivationTuningCapability();
    private final PhysicsContinuousCollisionCapability continuousCollisionCapability =
        new PhysicsContinuousCollisionCapability() {
        };
    private final PhysicsBackendEventsCapability backendEventsCapability =
        () -> Set.of(PhysicsBackendEventKind.CONTACT_STARTED,
            PhysicsBackendEventKind.CONTACT_ENDED,
            PhysicsBackendEventKind.CONTACT_FORCE);
    private final PhysicsVoxelTerrainCapability voxelTerrainCapability =
        new RapierVoxelTerrainCapability();
    private final PhysicsExtensionSettingsCapability extensionSettingsCapability =
        new RapierExtensionSettingsCapability();
    private final List<RapierBody> bodies = new ArrayList<>();
    private final Map<Long, RapierBody> bodiesByHandle = new HashMap<>();
    private final List<RapierJoint> joints = new ArrayList<>();
    private long[] snapshotBodyHandles = new long[0];
    private float[] snapshotBodyData = new float[0];
    private RapierBody[] selectedSnapshotBodies = new RapierBody[0];
    private int solverIterations = DEFAULT_SOLVER_ITERATIONS;
    private int internalPgsIterations = DEFAULT_INTERNAL_PGS_ITERATIONS;
    private int stabilizationIterations = DEFAULT_STABILIZATION_ITERATIONS;
    private int minIslandSize = DEFAULT_MIN_ISLAND_SIZE;
    private boolean closed;

    RapierSpace(@Nonnull SpaceId id, @Nonnull RapierBackend backend, long nativeSpaceHandle) {
        if (nativeSpaceHandle == 0L) {
            throw new IllegalStateException("Rapier returned a null native space handle");
        }
        this.id = id;
        this.backend = backend;
        this.nativeSpaceHandle = nativeSpaceHandle;
        this.cleanable = CLEANER.register(this, new NativeSpaceCleanup(nativeSpaceHandle));
    }

    @Nonnull
    @Override
    public SpaceId id() {
        return id;
    }

    @Nonnull
    @Override
    public BackendId backendId() {
        return backend.getId();
    }

    @Override
    public void step(float dt) {
        if (dt <= 0f) {
            return;
        }
        ensureOpen();
        if (!RapierNative.stepNative(nativeSpaceHandle, dt)) {
            throw new IllegalStateException("Rapier native step failed");
        }
    }

    @Override
    public void step(float dt, @Nonnull PhysicsBackendEventSink events) {
        if (dt <= 0f) {
            return;
        }
        ensureOpen();
        float[] raw = RapierNative.stepContactEventsNative(nativeSpaceHandle, dt);
        if (raw == null) {
            throw new IllegalStateException("Rapier native contact event step failed");
        }
        for (int i = 0; i + CONTACT_EVENT_FLOATS <= raw.length; i += CONTACT_EVENT_FLOATS) {
            PhysicsContactPhase phase = contactPhase(raw[i]);
            if (phase == null) {
                continue;
            }
            RapierBody bodyA = bodiesByHandle.get(rawBitFloatPairToLong(raw[i + 1], raw[i + 2]));
            RapierBody bodyB = bodiesByHandle.get(rawBitFloatPairToLong(raw[i + 3], raw[i + 4]));
            if (bodyA == null || bodyB == null) {
                continue;
            }
            events.contact(phase,
                bodyA,
                bodyB,
                new Vector3f(raw[i + 5], raw[i + 6], raw[i + 7]),
                new Vector3f(raw[i + 8], raw[i + 9], raw[i + 10]),
                new Vector3f(raw[i + 11], raw[i + 12], raw[i + 13]),
                raw[i + 14],
                raw[i + 15]);
        }
    }

    private static PhysicsContactPhase contactPhase(float rawPhase) {
        return switch ((int) rawPhase) {
            case 0 -> PhysicsContactPhase.STARTED;
            case 2 -> PhysicsContactPhase.ENDED;
            case 4 -> PhysicsContactPhase.FORCE;
            default -> null;
        };
    }

    private void setSolverTuning(int solverIterations,
        int internalPgsIterations,
        int stabilizationIterations,
        int minIslandSize) {
        ensureOpen();
        this.solverIterations = solverIterations;
        this.internalPgsIterations = internalPgsIterations;
        this.stabilizationIterations = stabilizationIterations;
        this.minIslandSize = minIslandSize;
        RapierNative.setSolverTuningNative(nativeSpaceHandle,
            solverIterations,
            internalPgsIterations,
            stabilizationIterations,
            minIslandSize);
    }

    private void setDynamicSleepTuning(float linearThreshold,
        float angularThreshold,
        float timeUntilSleep) {
        ensureOpen();
        RapierNative.setDynamicSleepTuningNative(nativeSpaceHandle,
            linearThreshold,
            angularThreshold,
            timeUntilSleep);
    }

    private void applyCurrentSolverTuning() {
        setSolverTuning(solverIterations,
            internalPgsIterations,
            stabilizationIterations,
            minIslandSize);
    }

    @Override
    public void setGravity(float x, float y, float z) {
        ensureOpen();
        RapierNative.setGravityNative(nativeSpaceHandle, x, y, z);
    }

    @Nonnull
    @Override
    public Vector3f getGravity() {
        ensureOpen();
        float[] out = new float[3];
        RapierNative.getGravityNative(nativeSpaceHandle, out);
        return new Vector3f(out[0], out[1], out[2]);
    }

    @Override
    public void addBody(@Nonnull PhysicsBody body) {
        ensureOpen();
        if (!(body instanceof RapierBody rapierBody)) {
            throw new IllegalArgumentException("Body does not belong to rapier backend");
        }
        if (rapierBody.isAttached()) {
            throw new IllegalStateException("Rapier body is already attached to a space");
        }

        long nativeBodyHandle = addNativeBody(rapierBody);
        if (nativeBodyHandle == 0L) {
            throw new IllegalStateException("Rapier returned a null native body handle");
        }

        rapierBody.attach(this, nativeBodyHandle);
        bodies.add(rapierBody);
        bodiesByHandle.put(nativeBodyHandle, rapierBody);
    }

    @Override
    public void removeBody(@Nonnull PhysicsBody body) {
        if (closed) {
            return;
        }
        if (!(body instanceof RapierBody rapierBody)) {
            return;
        }
        if (!rapierBody.isAttachedTo(this)) {
            if (rapierBody.isAttached()) {
                throw new IllegalArgumentException("Rapier body belongs to another space");
            }
            return;
        }

        removeAttachedJoints(rapierBody);
        long handle = rapierBody.getBodyHandle();
        RapierNative.removeBodyNative(nativeSpaceHandle, handle);
        rapierBody.detach(this);
        bodies.remove(rapierBody);
        bodiesByHandle.remove(handle);
    }

    @Nonnull
    @Override
    public List<PhysicsBody> getBodies() {
        return new ArrayList<>(bodies);
    }

    @Override
    public int bodyCount() {
        return bodies.size();
    }

    @Override
    public void forEachBody(@Nonnull Consumer<PhysicsBody> consumer) {
        for (RapierBody body : bodies) {
            consumer.accept(body);
        }
    }

    @Override
    public boolean containsBody(@Nonnull PhysicsBody body) {
        return body instanceof RapierBody rapierBody && rapierBody.isAttachedTo(this);
    }

    @Nonnull
    @Override
    public <T extends PhysicsCapability> Optional<T> getCapability(@Nonnull Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (type == PhysicsSolverTuningCapability.class) {
            return Optional.of(type.cast(solverTuningCapability));
        }
        if (type == PhysicsActivationTuningCapability.class) {
            return Optional.of(type.cast(activationTuningCapability));
        }
        if (type == PhysicsContinuousCollisionCapability.class) {
            return Optional.of(type.cast(continuousCollisionCapability));
        }
        if (type == PhysicsBackendEventsCapability.class) {
            return Optional.of(type.cast(backendEventsCapability));
        }
        if (type == PhysicsVoxelTerrainCapability.class) {
            return Optional.of(type.cast(voxelTerrainCapability));
        }
        if (type == PhysicsExtensionSettingsCapability.class) {
            return Optional.of(type.cast(extensionSettingsCapability));
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public List<PhysicsCapabilityDescriptor> getCapabilityDescriptors() {
        return CAPABILITY_DESCRIPTORS;
    }

    @Override
    public void snapshotBodies(@Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        snapshotBodies(_ -> null, consumer);
    }

    @Override
    public void snapshotBodies(
        @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        snapshotBodies(previousSnapshots, (_, snapshot) -> consumer.accept(snapshot));
    }

    @Override
    public void snapshotBodies(
        @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull BiConsumer<PhysicsBody, PhysicsBodySnapshot> consumer) {
        ensureOpen();
        int count = bodies.size();
        if (count == 0) {
            return;
        }

        ensureSnapshotCapacity(count);
        for (int i = 0; i < count; i++) {
            snapshotBodyHandles[i] = bodies.get(i).getBodyHandle();
        }

        int written = RapierNative.snapshotBodiesNative(nativeSpaceHandle,
            snapshotBodyHandles,
            count,
            snapshotBodyData);
        if (written < 0) {
            throw new IllegalStateException("Rapier native snapshot failed");
        }
        int limit = Math.clamp(written, 0, count);
        for (int i = 0; i < limit; i++) {
            RapierBody body = bodies.get(i);
            consumer.accept(body, body.snapshotFromNative(snapshotBodyData,
                i * BODY_SNAPSHOT_FLOATS,
                previousSnapshots.apply(body)));
        }
        for (int i = limit; i < count; i++) {
            RapierBody body = bodies.get(i);
            consumer.accept(body, PhysicsBodySnapshot.from(body, previousSnapshots.apply(body)));
        }
    }

    @Override
    public void snapshotBodies(@Nonnull Iterable<? extends PhysicsBody> selectedBodies,
        @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        snapshotBodies(selectedBodies, previousSnapshots,
            (_, snapshot) -> consumer.accept(snapshot));
    }

    @Override
    public void snapshotBodies(@Nonnull Iterable<? extends PhysicsBody> selectedBodies,
        @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull BiConsumer<PhysicsBody, PhysicsBodySnapshot> consumer) {
        ensureOpen();
        int count = collectSelectedSnapshotBodies(selectedBodies, previousSnapshots, consumer);
        if (count == 0) {
            return;
        }

        try {
            int written = RapierNative.snapshotBodiesNative(nativeSpaceHandle,
                snapshotBodyHandles,
                count,
                snapshotBodyData);
            if (written < 0) {
                throw new IllegalStateException("Rapier native snapshot failed");
            }
            int limit = Math.clamp(written, 0, count);
            for (int i = 0; i < limit; i++) {
                RapierBody body = selectedSnapshotBodies[i];
                consumer.accept(body, body.snapshotFromNative(snapshotBodyData,
                    i * BODY_SNAPSHOT_FLOATS,
                    previousSnapshots.apply(body)));
            }
            for (int i = limit; i < count; i++) {
                RapierBody body = selectedSnapshotBodies[i];
                consumer.accept(body,
                    PhysicsBodySnapshot.from(body, previousSnapshots.apply(body)));
            }
        } finally {
            for (int i = 0; i < count; i++) {
                selectedSnapshotBodies[i] = null;
            }
        }
    }

    private int collectSelectedSnapshotBodies(
        @Nonnull Iterable<? extends PhysicsBody> selectedBodies,
        @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull BiConsumer<PhysicsBody, PhysicsBodySnapshot> consumer) {
        int count = 0;
        for (PhysicsBody body : selectedBodies) {
            if (!(body instanceof RapierBody rapierBody) || !rapierBody.isAttachedTo(this)) {
                consumer.accept(body,
                    PhysicsBodySnapshot.from(body, previousSnapshots.apply(body)));
                continue;
            }

            ensureSnapshotCapacity(count + 1);
            selectedSnapshotBodies[count] = rapierBody;
            snapshotBodyHandles[count] = rapierBody.getBodyHandle();
            count++;
        }
        return count;
    }

    private void ensureSnapshotCapacity(int count) {
        if (snapshotBodyHandles.length < count) {
            int capacity = Math.max(count, snapshotBodyHandles.length * 2);
            snapshotBodyHandles = Arrays.copyOf(snapshotBodyHandles, capacity);
        }
        int floats = count * BODY_SNAPSHOT_FLOATS;
        if (snapshotBodyData.length < floats) {
            int capacity = Math.max(floats, snapshotBodyData.length * 2);
            snapshotBodyData = new float[capacity];
        }
        if (selectedSnapshotBodies.length < count) {
            int capacity = Math.max(count, selectedSnapshotBodies.length * 2);
            selectedSnapshotBodies = Arrays.copyOf(selectedSnapshotBodies, capacity);
        }
    }

    @Nonnull
    @Override
    public PhysicsRuntimeStats getRuntimeStats() {
        ensureOpen();
        int[] values = RapierNative.getRuntimeStatsNative(nativeSpaceHandle);
        if (values == null || values.length < RUNTIME_STATS_VALUES) {
            return PhysicsRuntimeStats.unavailable();
        }
        return PhysicsRuntimeStats.available(values[0],
            values[1],
            values[2],
            values[3],
            values[4],
            values[5],
            values[6],
            values[7],
            values[8],
            values[9]);
    }

    @Override
    public void resetStepPhaseStats() {
        ensureOpen();
        RapierNative.resetStepPhaseStatsNative(nativeSpaceHandle);
    }

    @Nonnull
    @Override
    public PhysicsStepPhaseStats getStepPhaseStats() {
        ensureOpen();
        long[] values = RapierNative.getStepPhaseStatsNative(nativeSpaceHandle);
        if (values == null || values.length < STEP_PHASE_STATS_VALUES) {
            return PhysicsStepPhaseStats.unavailable();
        }
        return PhysicsStepPhaseStats.available(values[0],
            values[1],
            values[2],
            values[3],
            values[4],
            values[5]);
    }

    @Nonnull
    @Override
    public PhysicsBody createStaticPlane(float groundY) {
        return RapierBody.staticPlane(groundY);
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
        return RapierBody.box(halfX, halfY, halfZ, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
        return createBox(halfExtents.x, halfExtents.y, halfExtents.z, mass);
    }

    @Nonnull
    private PhysicsBody createVoxelTerrain(float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        @Nonnull int[] voxelCoordinates) {
        return RapierBody.voxelTerrain(voxelSizeX, voxelSizeY, voxelSizeZ, voxelCoordinates);
    }

    private void combineVoxelTerrains(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        int shiftX,
        int shiftY,
        int shiftZ) {
        ensureOpen();
        RapierBody rapierBodyA = requireAttachedBody(bodyA);
        RapierBody rapierBodyB = requireAttachedBody(bodyB);
        if (rapierBodyA == rapierBodyB) {
            throw new IllegalArgumentException("Cannot combine a voxel terrain body with itself");
        }
        requireVoxelTerrain(rapierBodyA);
        requireVoxelTerrain(rapierBodyB);
        if (!RapierNative.combineVoxelTerrainNative(nativeSpaceHandle,
            rapierBodyA.getBodyHandle(),
            rapierBodyB.getBodyHandle(),
            shiftX,
            shiftY,
            shiftZ)) {
            throw new IllegalStateException("Rapier native voxel terrain combine failed");
        }
    }

    private static void requireVoxelTerrain(@Nonnull RapierBody body) {
        if (body.getShapeType() != ShapeType.VOXELS) {
            throw new IllegalArgumentException("Body must be a voxel terrain");
        }
    }

    @Nonnull
    @Override
    public PhysicsBody createSphere(float radius, float mass) {
        return RapierBody.sphere(radius, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createCapsule(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float mass) {
        return RapierBody.capsule(radius, halfHeight, axis, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createCylinder(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float mass) {
        return RapierBody.cylinder(radius, halfHeight, axis, mass);
    }

    @Nonnull
    @Override
    public PhysicsBody createCone(float radius, float halfHeight, @Nonnull PhysicsAxis axis,
        float mass) {
        return RapierBody.cone(radius, halfHeight, axis, mass);
    }

    @Nonnull
    @Override
    public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to) {
        ensureOpen();
        List<PhysicsRayHit> hits = raycastAll(from, to);
        PhysicsRayHit closest = null;
        for (PhysicsRayHit hit : hits) {
            if (closest == null || hit.fraction() < closest.fraction()) {
                closest = hit;
            }
        }
        return Optional.ofNullable(closest);
    }

    public long rawBitFloatPairToLong(float upper, float lower) {
        long upperBits = Float.floatToRawIntBits(upper);
        long lowerBits = Float.floatToRawIntBits(lower) & 0xFFFFFFFFL;

        return (upperBits << 32) | lowerBits;
    }

    @Nonnull
    @Override
    public List<PhysicsRayHit> raycastAll(@Nonnull Vector3f from, @Nonnull Vector3f to) {
        ensureOpen();
        float[] raw = RapierNative.raycastAllNative(nativeSpaceHandle,
            from.x, from.y, from.z, to.x, to.y, to.z);
        List<PhysicsRayHit> hits = new ArrayList<>(raw.length / RAY_HIT_FLOATS);
        for (int i = 0; i + RAY_HIT_FLOATS <= raw.length; i += RAY_HIT_FLOATS) {
            RapierBody body = bodiesByHandle.get(rawBitFloatPairToLong(raw[i], raw[i + 1]));
            if (body == null) {
                continue;
            }
            Vector3f point = new Vector3f(raw[i + 2], raw[i + 3], raw[i + 4]);
            Vector3f normal = new Vector3f(raw[i + 5], raw[i + 6], raw[i + 7]);
            hits.add(new PhysicsRayHit(body, point, normal, raw[i + 8], raw[i + 9]));
        }
        return hits;
    }

    @Nonnull
    @Override
    public List<PhysicsContact> getContacts() {
        ensureOpen();
        float[] raw = RapierNative.getContactsNative(nativeSpaceHandle);
        List<PhysicsContact> contacts = new ArrayList<>(raw.length / CONTACT_FLOATS);
        for (int i = 0; i + CONTACT_FLOATS <= raw.length; i += CONTACT_FLOATS) {
            RapierBody bodyA = bodiesByHandle.get(rawBitFloatPairToLong(raw[i], raw[i + 1]));
            RapierBody bodyB = bodiesByHandle.get(rawBitFloatPairToLong(raw[i + 2], raw[i + 3]));
            if (bodyA == null || bodyB == null) {
                continue;
            }
            Vector3f pointA = new Vector3f(raw[i + 4], raw[i + 5], raw[i + 6]);
            Vector3f pointB = new Vector3f(raw[i + 7], raw[i + 8], raw[i + 9]);
            Vector3f normal = new Vector3f(raw[i + 10], raw[i + 11], raw[i + 12]);
            contacts.add(new PhysicsContact(bodyA, bodyB, pointA, pointB, normal,
                raw[i + 13], raw[i + 14]));
        }
        return contacts;
    }

    @Nonnull
    @Override
    public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        return createFixedJoint(bodyA,
            bodyB,
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z);
    }

    @Nonnull
    @Override
    public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ) {
        return createJoint(PhysicsJointType.FIXED,
            bodyA,
            bodyB,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            0f,
            1f,
            0f,
            0f,
            0f,
            0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        return createPointJoint(bodyA,
            bodyB,
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z);
    }

    @Nonnull
    @Override
    public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ) {
        return createJoint(PhysicsJointType.POINT,
            bodyA,
            bodyB,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            0f,
            1f,
            0f,
            0f,
            0f,
            0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        return createHingeJoint(bodyA,
            bodyB,
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            axis.x,
            axis.y,
            axis.z);
    }

    @Nonnull
    @Override
    public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ) {
        return createJoint(PhysicsJointType.HINGE,
            bodyA,
            bodyB,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            0f,
            0f,
            0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        return createSliderJoint(bodyA,
            bodyB,
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            axis.x,
            axis.y,
            axis.z);
    }

    @Nonnull
    @Override
    public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ) {
        return createJoint(PhysicsJointType.SLIDER,
            bodyA,
            bodyB,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            0f,
            0f,
            0f);
    }

    @Nonnull
    @Override
    public PhysicsJoint createSpringJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        float restLength,
        float stiffness,
        float damping) {
        return createSpringJoint(bodyA,
            bodyB,
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            restLength,
            stiffness,
            damping);
    }

    @Nonnull
    @Override
    public PhysicsJoint createSpringJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float restLength,
        float stiffness,
        float damping) {
        return createJoint(PhysicsJointType.SPRING,
            bodyA,
            bodyB,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            0f,
            1f,
            0f,
            restLength,
            stiffness,
            damping);
    }

    @Override
    public void removeJoint(@Nonnull PhysicsJoint joint) {
        if (closed) {
            return;
        }
        if (!(joint instanceof RapierJoint rapierJoint)) {
            return;
        }
        if (!rapierJoint.belongsTo(this)) {
            throw new IllegalArgumentException("Rapier joint belongs to another space");
        }
        if (!rapierJoint.isValidIn(this)) {
            joints.remove(rapierJoint);
            return;
        }
        RapierNative.removeJointNative(nativeSpaceHandle, rapierJoint.getJointHandle());
        joints.remove(rapierJoint);
        rapierJoint.invalidate(this);
    }

    @Nonnull
    @Override
    public List<PhysicsJoint> getJoints() {
        return new ArrayList<>(joints);
    }

    @Override
    public int jointCount() {
        return joints.size();
    }

    @Override
    public void forEachJoint(@Nonnull Consumer<PhysicsJoint> consumer) {
        for (RapierJoint joint : joints) {
            consumer.accept(joint);
        }
    }

    long getNativeSpaceHandle() {
        ensureOpen();
        return nativeSpaceHandle;
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (RapierJoint joint : new ArrayList<>(joints)) {
            joint.invalidate(this);
        }
        joints.clear();
        for (RapierBody body : new ArrayList<>(bodies)) {
            body.detach(this);
        }
        bodies.clear();
        bodiesByHandle.clear();
        cleanable.clean();
        nativeSpaceHandle = 0L;
    }

    private void removeAttachedJoints(@Nonnull RapierBody body) {
        for (RapierJoint joint : new ArrayList<>(joints)) {
            if (joint.getBodyA() != body && joint.getBodyB() != body) {
                continue;
            }

            RapierNative.removeJointNative(nativeSpaceHandle, joint.getJointHandle());
            joints.remove(joint);
            joint.invalidate(this);
        }
    }

    private long addNativeBody(@Nonnull RapierBody body) {
        if (body.getShapeType() == ShapeType.VOXELS) {
            Vector3f position = body.getStoredPosition();
            Vector3f voxelSize = body.getVoxelSize();
            return RapierNative.addVoxelTerrainNative(nativeSpaceHandle,
                voxelSize.x,
                voxelSize.y,
                voxelSize.z,
                body.getVoxelCoordinates(),
                position.x,
                position.y,
                position.z,
                body.getStoredFriction(),
                body.getStoredRestitution(),
                body.getStoredCollisionGroup(),
                body.getStoredCollisionMask());
        }

        Vector3f halfExtents = body.getBoxHalfExtents();
        if (halfExtents == null) {
            halfExtents = new Vector3f();
        }
        Vector3f position = body.getStoredPosition();
        Quaternionf rotation = body.getStoredRotation();
        Vector3f linearVelocity = body.getStoredLinearVelocity();
        Vector3f angularVelocity = body.getStoredAngularVelocity();

        return RapierNative.addBodyNative(nativeSpaceHandle,
            body.getShapeType().ordinal(),
            halfExtents.x,
            halfExtents.y,
            halfExtents.z,
            body.getSphereRadius(),
            body.getHalfHeight(),
            body.getShapeAxis().index(),
            body.getStoredBodyType().ordinal(),
            body.getStoredMass(),
            position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            linearVelocity.x,
            linearVelocity.y,
            linearVelocity.z,
            angularVelocity.x,
            angularVelocity.y,
            angularVelocity.z,
            body.getStoredFriction(),
            body.getStoredRestitution(),
            body.getStoredLinearDamping(),
            body.getStoredAngularDamping(),
            body.getStoredSensor(),
            body.getStoredCollisionGroup(),
            body.getStoredCollisionMask(),
            body.getStoredContinuousCollisionEnabled());
    }

    private PhysicsJoint createJoint(@Nonnull PhysicsJointType type,
        @Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
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
        float damping) {
        ensureOpen();
        RapierBody rapierA = requireAttachedBody(bodyA);
        RapierBody rapierB = requireAttachedBody(bodyB);
        float normalizedAxisX = axisX;
        float normalizedAxisY = axisY;
        float normalizedAxisZ = axisZ;
        float axisLengthSquared = axisX * axisX + axisY * axisY + axisZ * axisZ;
        if (axisLengthSquared == 0f) {
            normalizedAxisX = 0f;
            normalizedAxisY = 1f;
            normalizedAxisZ = 0f;
        } else {
            float inverseLength = (float) (1.0 / Math.sqrt(axisLengthSquared));
            normalizedAxisX *= inverseLength;
            normalizedAxisY *= inverseLength;
            normalizedAxisZ *= inverseLength;
        }
        long handle = RapierNative.addJointNative(nativeSpaceHandle,
            type.ordinal(),
            rapierA.getBodyHandle(),
            rapierB.getBodyHandle(),
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            normalizedAxisX,
            normalizedAxisY,
            normalizedAxisZ,
            restLength,
            stiffness,
            damping);
        if (handle == 0L) {
            throw new IllegalStateException("Rapier returned a null native joint handle");
        }
        RapierJoint joint = new RapierJoint(this, type, rapierA, rapierB, handle,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            normalizedAxisX,
            normalizedAxisY,
            normalizedAxisZ,
            restLength,
            stiffness,
            damping);
        joints.add(joint);
        return joint;
    }

    private RapierBody requireAttachedBody(@Nonnull PhysicsBody body) {
        if (!(body instanceof RapierBody rapierBody)) {
            throw new IllegalArgumentException("Body does not belong to rapier backend");
        }
        if (!rapierBody.isAttachedTo(this)) {
            if (rapierBody.isAttached()) {
                throw new IllegalArgumentException("Rapier body belongs to another space");
            }
            throw new IllegalStateException("Rapier joint bodies must be added to a space first");
        }
        return rapierBody;
    }

    private void ensureOpen() {
        if (closed || nativeSpaceHandle == 0L) {
            throw new IllegalStateException("Rapier space is closed");
        }
    }

    private final class RapierSolverTuningCapability implements PhysicsSolverTuningCapability {

        @Override
        public void setSolverTuning(@Nonnull PhysicsSolverTuning tuning) {
            Objects.requireNonNull(tuning, "tuning");
            solverIterations = tuning.solverIterations();
            stabilizationIterations = tuning.stabilizationIterations();
            applyCurrentSolverTuning();
        }
    }

    private final class RapierActivationTuningCapability implements PhysicsActivationTuningCapability {

        @Override
        public void setActivationTuning(@Nonnull PhysicsActivationTuning tuning) {
            Objects.requireNonNull(tuning, "tuning");
            setDynamicSleepTuning(tuning.linearSleepThreshold(),
                tuning.angularSleepThreshold(),
                tuning.timeUntilSleep());
        }
    }

    private final class RapierVoxelTerrainCapability implements PhysicsVoxelTerrainCapability {

        @Nonnull
        @Override
        public PhysicsBody createVoxelTerrain(float voxelSizeX,
            float voxelSizeY,
            float voxelSizeZ,
            @Nonnull int[] voxelCoordinates) {
            return RapierSpace.this.createVoxelTerrain(voxelSizeX,
                voxelSizeY,
                voxelSizeZ,
                voxelCoordinates);
        }

        @Override
        public void combineVoxelTerrains(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            int shiftX,
            int shiftY,
            int shiftZ) {
            RapierSpace.this.combineVoxelTerrains(bodyA, bodyB, shiftX, shiftY, shiftZ);
        }
    }

    private final class RapierExtensionSettingsCapability implements PhysicsExtensionSettingsCapability {

        @Override
        public void applyExtensionSettings(@Nonnull PhysicsCapabilityId capabilityId,
            @Nonnull Map<String, String> values) {
            Objects.requireNonNull(capabilityId, "capabilityId");
            Objects.requireNonNull(values, "values");
            if (!RAPIER_SOLVER_EXTENSION_ID.equals(capabilityId)) {
                return;
            }
            internalPgsIterations = parsePositive(values,
                INTERNAL_PGS_ITERATIONS,
                internalPgsIterations);
            minIslandSize = parsePositive(values,
                MIN_ISLAND_SIZE,
                minIslandSize);
            applyCurrentSolverTuning();
        }

        private int parsePositive(@Nonnull Map<String, String> values,
            @Nonnull String key,
            int fallback) {
            String value = values.get(key);
            if (value == null) {
                return fallback;
            }
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Rapier extension setting " + key
                    + " must be an integer", exception);
            }
            if (parsed < 1) {
                throw new IllegalArgumentException("Rapier extension setting " + key
                    + " must be positive");
            }
            return parsed;
        }
    }

    private static final class NativeSpaceCleanup implements Runnable {

        private final long nativeSpaceHandle;

        private NativeSpaceCleanup(long nativeSpaceHandle) {
            this.nativeSpaceHandle = nativeSpaceHandle;
        }

        @Override
        public void run() {
            RapierNative.destroySpaceNative(nativeSpaceHandle);
        }
    }
}
