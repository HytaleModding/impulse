package dev.hytalemodding.impulse.core.internal.persistence;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Validates hard restore invariants before live runtime topology is stripped.
 */
public final class PersistentPhysicsRestorePreflight {

    private PersistentPhysicsRestorePreflight() {
    }

    @Nullable
    public static String validate(@Nonnull PersistentPhysicsWorldResource persistent) {
        PhysicsWorldSettings worldSettings = persistent.getWorldSettings();
        String worldSettingsFailure = validateWorldSettings(worldSettings);
        if (worldSettingsFailure != null) {
            return worldSettingsFailure;
        }

        String spaceFailure = validateSpaces(persistent.getSpaces(), worldSettings.getStepMode());
        if (spaceFailure != null) {
            return spaceFailure;
        }

        String bodyFailure = validateBodyKeys(persistent.getBodies());
        if (bodyFailure != null) {
            return bodyFailure;
        }

        return validateJointKeys(persistent.getJoints());
    }

    @Nullable
    private static String validateWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        try {
            PhysicsWorldSettings validated = PhysicsWorldSettings.defaults();
            validated.setSimulationSteps(settings.getSimulationSteps());
            validated.setStepMode(settings.getStepMode());
            validated.setStepSchedulingMode(settings.getStepSchedulingMode());
            validated.setEventCollectionMode(settings.getEventCollectionMode());
            validated.setMaxStepDt(settings.getMaxStepDt());
            return null;
        } catch (RuntimeException exception) {
            return "Invalid persisted physics runtime settings: " + exception.getMessage();
        }
    }

    @Nullable
    private static String validateSpaces(@Nonnull PersistentPhysicsSpaceState[] spaces,
        @Nonnull PhysicsStepMode stepMode) {
        IntOpenHashSet spaceIds = new IntOpenHashSet();
        for (PersistentPhysicsSpaceState state : spaces) {
            int spaceId = state.getSpaceId();
            if (spaceId <= 0) {
                return "Persisted space id must be positive, found " + spaceId;
            }
            if (!spaceIds.add(spaceId)) {
                return "Duplicate persisted space id " + spaceId;
            }
            if (!isFiniteVector(state.getGravity())) {
                return "Persisted space gravity must be finite for space id=" + spaceId;
            }

            String settingsFailure = validateSpaceBackendAndSettings(state, spaceId, stepMode);
            if (settingsFailure != null) {
                return settingsFailure;
            }
        }
        return null;
    }

    @Nullable
    private static String validateSpaceBackendAndSettings(@Nonnull PersistentPhysicsSpaceState state,
        int spaceId,
        @Nonnull PhysicsStepMode stepMode) {
        BackendId backendId;
        try {
            /*
             * BackendId construction validates the serialized id. Keep that check inside
             * preflight so bootstrap records a restore failure instead of throwing mid-tick.
             */
            backendId = state.toBackendId();
        } catch (RuntimeException exception) {
            return "Invalid persisted physics space backend id for space id=" + spaceId
                + ": " + failureMessage(exception);
        }

        try {
            Impulse.getBackend(backendId);
        } catch (IllegalStateException exception) {
            return "Saved backend " + backendId + " is not available for restore";
        }

        try {
            state.toSettings();
            validateBackendStepModeCompatibility(backendId, stepMode);
        } catch (RuntimeException exception) {
            return "Invalid persisted physics space settings for space id=" + spaceId
                + " backend=" + backendId + ": " + failureMessage(exception);
        }
        return null;
    }

    private static void validateBackendStepModeCompatibility(@Nonnull BackendId backendId,
        @Nonnull PhysicsStepMode stepMode) {
        if (stepMode != PhysicsStepMode.CCD) {
            return;
        }

        PhysicsBackendRuntime probe = Impulse.createRuntime(backendId);
        SpaceId probeSpaceId = SpaceId.next();
        int backendSpaceId = probe.createSpace(probeSpaceId);
        try {
            if (!probe.supportsContinuousCollision(backendSpaceId)) {
                throw new IllegalArgumentException("CCD mode is not available for backend "
                    + backendId);
            }
        } finally {
            probe.destroySpace(backendSpaceId);
        }
    }

    @Nullable
    private static String validateBodyKeys(@Nonnull PersistentPhysicsBodyState[] bodies) {
        ObjectOpenHashSet<RigidBodyKey> bodyKeys = new ObjectOpenHashSet<>();
        for (PersistentPhysicsBodyState state : bodies) {
            RigidBodyKey bodyKey = state.getBodyKey();
            if (bodyKey != null && !bodyKeys.add(bodyKey)) {
                return "Duplicate persisted body key " + bodyKey;
            }
        }
        return null;
    }

    @Nullable
    private static String validateJointKeys(@Nonnull PersistentPhysicsJointState[] joints) {
        ObjectOpenHashSet<String> jointKeys = new ObjectOpenHashSet<>();
        for (PersistentPhysicsJointState state : joints) {
            String jointKey = state.key();
            if (!jointKeys.add(jointKey)) {
                return "Duplicate persisted joint key " + jointKey;
            }
        }
        return null;
    }

    private static boolean isFiniteVector(@Nullable Vector3f value) {
        return value != null
            && Float.isFinite(value.x)
            && Float.isFinite(value.y)
            && Float.isFinite(value.z);
    }

    @Nonnull
    private static String failureMessage(@Nonnull RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && !message.isBlank()
            ? message
            : exception.getClass().getSimpleName();
    }
}
