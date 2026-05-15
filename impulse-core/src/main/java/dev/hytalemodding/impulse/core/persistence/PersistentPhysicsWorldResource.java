package dev.hytalemodding.impulse.core.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Codec-backed world-level physics resource for the persistence layer.
 *
 * <p>This resource is registered on the {@code EntityStore} and persisted by
 * Hytale's serialization. It stores the world-level state that does not belong
 * on individual entities: the space definitions (id, backend, gravity, world-collision
 * settings), the joint definitions (keyed by endpoint entity UUIDs), the default
 * space id, and the simulation step count.</p>
 *
 * <p>Body state lives on each entity through {@link PersistentPhysicsBodyComponent}
 * instead. The split means Hytale can persist entities and the world resource
 * independently, and the hydration systems read both to rebuild the full runtime
 * state.</p>
 *
 * <p>The {@code runtimeRestorePending} flag is set by {@code afterDecode} whenever
 * Hytale deserializes this resource. It signals the hydration systems that they
 * need to recreate live spaces, bodies, and joints from the persisted data. The
 * flag is cleared once joint hydration completes successfully.</p>
 */
public class PersistentPhysicsWorldResource implements Resource<EntityStore> {

    private static final PersistentPhysicsSpaceState[] EMPTY_SPACES = new PersistentPhysicsSpaceState[0];
    private static final PersistentPhysicsJointState[] EMPTY_JOINTS = new PersistentPhysicsJointState[0];

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsWorldResource> CODEC = BuilderCodec.builder(
            PersistentPhysicsWorldResource.class,
            PersistentPhysicsWorldResource::new)
        .append(new KeyedCodec<>("DefaultSpaceId", Codec.INTEGER), (resource, value) -> resource.defaultSpaceId = value,
            resource -> resource.defaultSpaceId)
        .add()
        .append(new KeyedCodec<>("SimulationSteps", Codec.INTEGER),
            (resource, value) -> resource.simulationSteps = value,
            resource -> resource.simulationSteps)
        .add()
        .append(new KeyedCodec<>("Spaces",
                new ArrayCodec<>(PersistentPhysicsSpaceState.CODEC, PersistentPhysicsSpaceState[]::new)),
            (resource, value) -> resource.spaces = copySpaces(value),
            resource -> resource.getSpaces())
        .add()
        .append(new KeyedCodec<>("Joints",
                new ArrayCodec<>(PersistentPhysicsJointState.CODEC, PersistentPhysicsJointState[]::new)),
            (resource, value) -> resource.joints = copyJoints(value),
            resource -> resource.getJoints())
        .add()
        .afterDecode(resource -> resource.runtimeRestorePending = true)
        .build();

    private int defaultSpaceId;
    private int simulationSteps = PhysicsWorldResource.MIN_SIMULATION_STEPS;
    @Nonnull
    private PersistentPhysicsSpaceState[] spaces = EMPTY_SPACES;
    @Nonnull
    private PersistentPhysicsJointState[] joints = EMPTY_JOINTS;
    private transient boolean runtimeRestorePending;

    public PersistentPhysicsWorldResource() {
    }

    public static ResourceType<EntityStore, PersistentPhysicsWorldResource> getResourceType() {
        return ImpulsePlugin.get().getPersistentPhysicsWorldResourceType();
    }

    public int getDefaultSpaceId() {
        return defaultSpaceId;
    }

    public void setDefaultSpaceId(int defaultSpaceId) {
        this.defaultSpaceId = defaultSpaceId;
    }

    public int getSimulationSteps() {
        return simulationSteps;
    }

    public void setSimulationSteps(int simulationSteps) {
        this.simulationSteps = simulationSteps;
    }

    @Nonnull
    public PersistentPhysicsSpaceState[] getSpaces() {
        return copySpaces(spaces);
    }

    public void setSpaces(@Nonnull PersistentPhysicsSpaceState[] spaces) {
        this.spaces = copySpaces(spaces);
    }

    @Nonnull
    public PersistentPhysicsJointState[] getJoints() {
        return copyJoints(joints);
    }

    public void setJoints(@Nonnull PersistentPhysicsJointState[] joints) {
        this.joints = copyJoints(joints);
    }

    @Nullable
    public SpaceId getDefaultSpaceIdValue() {
        return defaultSpaceId > 0 ? new SpaceId(defaultSpaceId) : null;
    }

    public boolean isRuntimeRestorePending() {
        return runtimeRestorePending;
    }

    public void markRuntimeRestorePending() {
        runtimeRestorePending = true;
    }

    public void clearRuntimeRestorePending() {
        runtimeRestorePending = false;
    }

    public void copyFrom(@Nonnull PersistentPhysicsWorldResource other) {
        defaultSpaceId = other.defaultSpaceId;
        simulationSteps = other.simulationSteps;
        spaces = copySpaces(other.spaces);
        joints = copyJoints(other.joints);
        runtimeRestorePending = other.runtimeRestorePending;
    }

    @Nonnull
    @Override
    public PersistentPhysicsWorldResource clone() {
        PersistentPhysicsWorldResource copy = new PersistentPhysicsWorldResource();
        copy.copyFrom(this);
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsSpaceState[] copySpaces(@Nonnull PersistentPhysicsSpaceState[] source) {
        PersistentPhysicsSpaceState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsJointState[] copyJoints(@Nonnull PersistentPhysicsJointState[] source) {
        PersistentPhysicsJointState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }
}
