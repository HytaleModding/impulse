package dev.hytalemodding.impulse.core.internal.persistence;

import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import lombok.Getter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Worker-owned copy of the live runtime state needed by world persistence.
 *
 * <p>{@link #capture(PhysicsWorldResource)} and {@link #captureFootprint(PhysicsWorldResource)}
 * read live backend spaces, bodies, and joints. Callers must invoke them from the physics
 * owner thread or through the physics worker bridge.</p>
 */
public final class PersistentPhysicsRuntimeSnapshot {

    @Nonnull
    private final Footprint footprint;
    @Getter
    private final int simulationSteps;
    @Nonnull
    private final PhysicsStepMode stepMode;
    @Nonnull
    private final PhysicsStepSchedulingMode stepSchedulingMode;
    @Getter
    private final float maxStepDt;
    @Getter
    private final int defaultSpaceId;
    @Nonnull
    private final PersistentPhysicsSpaceState[] spaces;
    @Nonnull
    private final PersistentPhysicsBodyState[] bodies;
    @Nonnull
    private final PersistentPhysicsJointState[] joints;

    private PersistentPhysicsRuntimeSnapshot(int simulationSteps,
        @Nonnull PhysicsStepMode stepMode,
        @Nonnull PhysicsStepSchedulingMode stepSchedulingMode,
        float maxStepDt,
        int defaultSpaceId,
        @Nonnull PersistentPhysicsSpaceState[] spaces,
        @Nonnull PersistentPhysicsBodyState[] bodies,
        @Nonnull PersistentPhysicsJointState[] joints) {
        this.simulationSteps = simulationSteps;
        this.stepMode = stepMode;
        this.stepSchedulingMode = stepSchedulingMode;
        this.maxStepDt = maxStepDt;
        this.defaultSpaceId = defaultSpaceId;
        this.spaces = copySpaces(spaces);
        this.bodies = copyBodies(bodies);
        this.joints = copyJoints(joints);
        footprint = new Footprint(spaces.length, bodies.length, joints.length);
    }

    @Nonnull
    public static PersistentPhysicsRuntimeSnapshot capture(@Nonnull PhysicsWorldResource runtime) {
        runtime.assertCanAccessLiveBackendDirectly("capture persistent physics runtime snapshot");
        SpaceId defaultSpaceId = runtime.getDefaultSpaceId();
        int resolvedDefaultSpaceId = defaultSpaceId != null
            ? defaultSpaceId.value()
            : PersistentPhysicsBodyState.DEFAULT_SPACE_ID;

        List<PersistentPhysicsSpaceState> spaces = new ArrayList<>();
        List<PersistentPhysicsBodyState> bodies = new ArrayList<>();
        List<PersistentPhysicsJointState> joints = new ArrayList<>();
        for (PhysicsWorldResource.BodyRegistration registration : runtime.getBodyRegistrations()) {
            if (registration.persistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT) {
                bodies.add(PersistentPhysicsBodyState.from(registration));
            }
        }
        for (PhysicsSpace space : runtime.iterateSpaces()) {
            PhysicsSpaceSettings settings = runtime.getSpaceSettings(space.getId());
            spaces.add(PersistentPhysicsSpaceState.from(space, settings));
            capturePersistentJoints(runtime, space, joints);
        }

        return new PersistentPhysicsRuntimeSnapshot(runtime.getSimulationSteps(),
            runtime.getStepMode(),
            runtime.getStepSchedulingMode(),
            runtime.getMaxStepDt(),
            resolvedDefaultSpaceId,
            spaces.toArray(PersistentPhysicsSpaceState[]::new),
            bodies.toArray(PersistentPhysicsBodyState[]::new),
            joints.toArray(PersistentPhysicsJointState[]::new));
    }

    @Nonnull
    public static Footprint captureFootprint(@Nonnull PhysicsWorldResource runtime) {
        runtime.assertCanAccessLiveBackendDirectly("capture persistent physics runtime footprint");
        return new Footprint(runtime.getSpaceCount(),
            runtime.getBodyRegistrationCount(PhysicsBodyPersistenceMode.PERSISTENT),
            countPersistentJoints(runtime));
    }

    @Nonnull
    public PhysicsStepMode getStepMode() {
        return stepMode;
    }

    @Nonnull
    public PhysicsStepSchedulingMode getStepSchedulingMode() {
        return stepSchedulingMode;
    }

    @Nonnull
    public Footprint getFootprint() {
        return footprint;
    }

    @Nonnull
    public PersistentPhysicsSpaceState[] getSpaces() {
        return copySpaces(spaces);
    }

    @Nonnull
    public PersistentPhysicsBodyState[] getBodies() {
        return copyBodies(bodies);
    }

    @Nonnull
    public PersistentPhysicsJointState[] getJoints() {
        return copyJoints(joints);
    }

    private static int countPersistentJoints(@Nonnull PhysicsWorldResource runtime) {
        int[] count = new int[1];
        for (PhysicsSpace space : runtime.iterateSpaces()) {
            space.forEachJoint(joint -> {
                PhysicsBodyId bodyAId = runtime.getBodyId(joint.getBodyA());
                PhysicsBodyId bodyBId = runtime.getBodyId(joint.getBodyB());
                if (bodyAId == null || bodyBId == null) {
                    return;
                }
                PhysicsWorldResource.BodyRegistration bodyA = runtime.getRegistration(bodyAId);
                PhysicsWorldResource.BodyRegistration bodyB = runtime.getRegistration(bodyBId);
                if (bodyA == null
                    || bodyB == null
                    || bodyA.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT
                    || bodyB.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT) {
                    return;
                }
                count[0]++;
            });
        }
        return count[0];
    }

    private static void capturePersistentJoints(@Nonnull PhysicsWorldResource runtime,
        @Nonnull PhysicsSpace space,
        @Nonnull List<PersistentPhysicsJointState> joints) {
        space.forEachJoint(joint -> {
            PhysicsBodyId bodyAId = runtime.getBodyId(joint.getBodyA());
            PhysicsBodyId bodyBId = runtime.getBodyId(joint.getBodyB());
            if (bodyAId == null || bodyBId == null) {
                return;
            }
            PhysicsWorldResource.BodyRegistration bodyA = runtime.getRegistration(bodyAId);
            PhysicsWorldResource.BodyRegistration bodyB = runtime.getRegistration(bodyBId);
            if (bodyA == null
                || bodyB == null
                || bodyA.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT
                || bodyB.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT) {
                return;
            }
            joints.add(PersistentPhysicsJointState.from(space.getId().value(),
                bodyAId,
                bodyBId,
                joint));
        });
    }

    @Nonnull
    private static PersistentPhysicsSpaceState[] copySpaces(
        @Nonnull PersistentPhysicsSpaceState[] source) {
        PersistentPhysicsSpaceState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsBodyState[] copyBodies(
        @Nonnull PersistentPhysicsBodyState[] source) {
        PersistentPhysicsBodyState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsJointState[] copyJoints(
        @Nonnull PersistentPhysicsJointState[] source) {
        PersistentPhysicsJointState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    public record Footprint(int spaces, int bodies, int joints) {

        public Footprint {
            spaces = Math.max(0, spaces);
            bodies = Math.max(0, bodies);
            joints = Math.max(0, joints);
        }
    }
}
