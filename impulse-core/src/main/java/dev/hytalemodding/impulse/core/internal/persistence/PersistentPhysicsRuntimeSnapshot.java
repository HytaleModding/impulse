package dev.hytalemodding.impulse.core.internal.persistence;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Owner-owned copy of the live runtime state needed by world persistence.
 *
 * <p>{@link #capture(PhysicsWorldRuntimeResource)} and
 * {@link #captureFootprint(PhysicsWorldRuntimeResource)}
 * read live backend spaces, bodies, and joints. Callers must invoke them from the physics
 * owner lane or through the physics owner bridge.</p>
 */
public final class PersistentPhysicsRuntimeSnapshot {

    @Nonnull
    private final Footprint footprint;
    @Nonnull
    private final PhysicsWorldSettings worldSettings;
    @Nonnull
    private final PersistentPhysicsSpaceState[] spaces;
    @Nonnull
    private final PersistentPhysicsBodyState[] bodies;
    @Nonnull
    private final PersistentPhysicsJointState[] joints;

    private PersistentPhysicsRuntimeSnapshot(@Nonnull PhysicsWorldSettings worldSettings,
        @Nonnull PersistentPhysicsSpaceState[] spaces,
        @Nonnull PersistentPhysicsBodyState[] bodies,
        @Nonnull PersistentPhysicsJointState[] joints) {
        this.worldSettings = new PhysicsWorldSettings(worldSettings);
        this.spaces = copySpaces(spaces);
        this.bodies = copyBodies(bodies);
        this.joints = copyJoints(joints);
        footprint = new Footprint(spaces.length, bodies.length, joints.length);
    }

    @Nonnull
    public static PersistentPhysicsRuntimeSnapshot capture(@Nonnull PhysicsWorldRuntimeResource runtime) {
        runtime.assertCanAccessLiveBackendDirectly("capture persistent physics runtime snapshot");

        List<PersistentPhysicsSpaceState> spaces = new ArrayList<>();
        List<PersistentPhysicsBodyState> bodies = new ArrayList<>();
        List<PersistentPhysicsJointState> joints = new ArrayList<>();
        for (PhysicsBodyRegistration registration : runtime.getBodyRegistrations()) {
            if (registration.persistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT) {
                bodies.add(PersistentPhysicsBodyState.from(registration,
                    runtime.getBodySnapshot(registration.bodyKey())));
            }
        }
        for (PhysicsSpaceBinding space : runtime.iterateSpaceBindings()) {
            PhysicsSpaceSettings settings = runtime.getLiveSpaceSettings(space.spaceId());
            spaces.add(PersistentPhysicsSpaceState.from(space, settings));
            capturePersistentJoints(runtime, space, joints);
        }

        return new PersistentPhysicsRuntimeSnapshot(runtime.getWorldSettings(),
            spaces.toArray(PersistentPhysicsSpaceState[]::new),
            bodies.toArray(PersistentPhysicsBodyState[]::new),
            joints.toArray(PersistentPhysicsJointState[]::new));
    }

    @Nonnull
    public static Footprint captureFootprint(@Nonnull PhysicsWorldRuntimeResource runtime) {
        runtime.assertCanAccessLiveBackendDirectly("capture persistent physics runtime footprint");
        return new Footprint(runtime.getSpaceCount(),
            runtime.getBodyRegistrationCount(PhysicsBodyPersistenceMode.PERSISTENT),
            countPersistentJoints(runtime));
    }

    @Nonnull
    public PhysicsWorldSettings getWorldSettings() {
        return new PhysicsWorldSettings(worldSettings);
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

    private static int countPersistentJoints(@Nonnull PhysicsWorldRuntimeResource runtime) {
        int count = 0;
        for (PhysicsJointRegistration joint : runtime.getJointRegistrations()) {
            PhysicsBodyRegistration bodyA = runtime.getRegistration(joint.bodyA());
            PhysicsBodyRegistration bodyB = runtime.getRegistration(joint.bodyB());
            if (bodyA == null
                || bodyB == null
                || bodyA.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT
                || bodyB.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static void capturePersistentJoints(@Nonnull PhysicsWorldRuntimeResource runtime,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull List<PersistentPhysicsJointState> joints) {
        for (PhysicsJointRegistration joint : runtime.getJointRegistrations()) {
            if (!joint.spaceId().equals(space.spaceId())) {
                continue;
            }
            RigidBodyKey bodyAKey = joint.bodyA();
            RigidBodyKey bodyBKey = joint.bodyB();
            PhysicsBodyRegistration bodyA = runtime.getRegistration(bodyAKey);
            PhysicsBodyRegistration bodyB = runtime.getRegistration(bodyBKey);
            if (bodyA == null
                || bodyB == null
                || bodyA.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT
                || bodyB.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT) {
                continue;
            }
            joints.add(PersistentPhysicsJointState.from(space.spaceId().value(),
                bodyAKey,
                bodyBKey,
                joint));
        }
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
