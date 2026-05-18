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
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

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
 * flag is cleared once restore finishes.</p>
 *
 * <p>Restore semantics are intentionally asymmetric. Missing saved backends are a
 * hard failure because the world-level space definitions cannot be recreated at
 * all. Missing entities, missing runtime bodies, and unresolved joints are softer
 * failures: those entries are skipped, counted, and reported so restore can finish
 * instead of hanging forever.</p>
 */
public class PersistentPhysicsWorldResource implements Resource<EntityStore> {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    private static final PersistentPhysicsSpaceState[] EMPTY_SPACES = new PersistentPhysicsSpaceState[0];
    private static final PersistentPhysicsJointState[] EMPTY_JOINTS = new PersistentPhysicsJointState[0];

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsWorldResource> CODEC = BuilderCodec.builder(
            PersistentPhysicsWorldResource.class,
            PersistentPhysicsWorldResource::new)
        .append(new KeyedCodec<>("SchemaVersion", Codec.INTEGER, false),
            PersistentPhysicsWorldResource::setSchemaVersion,
            PersistentPhysicsWorldResource::getSchemaVersion)
        .add()
        .append(new KeyedCodec<>("DefaultSpaceId", Codec.INTEGER),
            (resource, value) -> resource.defaultSpaceId = value,
            PersistentPhysicsWorldResource::getDefaultSpaceId)
        .add()
        .append(new KeyedCodec<>("SimulationSteps", Codec.INTEGER),
            PersistentPhysicsWorldResource::setSimulationSteps,
            PersistentPhysicsWorldResource::getSimulationSteps)
        .add()
        .append(new KeyedCodec<>("StepMode", Codec.STRING, false),
            (resource, value) -> resource.setStepMode(parseStepModeOrDefault(value)),
            resource -> resource.getStepMode().getSerializedName())
        .add()
        .append(new KeyedCodec<>("MaxStepDt", Codec.FLOAT, false),
            PersistentPhysicsWorldResource::setMaxStepDt,
            PersistentPhysicsWorldResource::getMaxStepDt)
        .add()
        .append(new KeyedCodec<>("Spaces",
                new ArrayCodec<>(PersistentPhysicsSpaceState.CODEC, PersistentPhysicsSpaceState[]::new)),
            (resource, value) -> resource.spaces = copySpaces(value),
            PersistentPhysicsWorldResource::getSpaces)
        .add()
        .append(new KeyedCodec<>("Joints",
                new ArrayCodec<>(PersistentPhysicsJointState.CODEC, PersistentPhysicsJointState[]::new)),
            (resource, value) -> resource.joints = copyJoints(value),
            PersistentPhysicsWorldResource::getJoints)
        .add()
        .afterDecode(PersistentPhysicsWorldResource::markRuntimeRestorePending)
        .build();

    @Getter
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    @Getter
    @Setter
    private int defaultSpaceId;
    @Getter
    private int simulationSteps = PhysicsWorldResource.MIN_SIMULATION_STEPS;
    @Getter
    @Nonnull
    private PhysicsStepMode stepMode = PhysicsStepMode.PROGRESSIVE_REFINEMENT;
    @Getter
    private float maxStepDt = PhysicsWorldResource.DEFAULT_MAX_STEP_DT;
    @Nonnull
    private PersistentPhysicsSpaceState[] spaces = EMPTY_SPACES;
    @Nonnull
    private PersistentPhysicsJointState[] joints = EMPTY_JOINTS;
    @Getter
    private transient boolean runtimeRestorePending;
    @Getter
    private transient boolean runtimeSpaceBootstrapComplete;
    private transient boolean runtimeRestoreFailed;
    @Nonnull
    private transient String runtimeRestoreFailureMessage = "";
    private transient int runtimeRestoredSpaceCount;
    private transient int runtimeRestoredBodyCount;
    private transient int runtimeRestoredJointCount;
    @Nonnull
    private transient Map<String, Integer> runtimeSkippedBodiesByReason = new LinkedHashMap<>();
    @Nonnull
    private transient Map<String, Integer> runtimeSkippedJointsByReason = new LinkedHashMap<>();
    @Nonnull
    private transient Set<String> runtimeSkippedJointKeys = new HashSet<>();
    private transient boolean runtimeSnapshotSynced;
    private transient int runtimeSnapshotSyncSkipTicks;

    public PersistentPhysicsWorldResource() {
    }

    public static ResourceType<EntityStore, PersistentPhysicsWorldResource> getResourceType() {
        return ImpulsePlugin.get().getPersistentPhysicsWorldResourceType();
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion > 0 ? schemaVersion : CURRENT_SCHEMA_VERSION;
    }

    public void setSimulationSteps(int simulationSteps) {
        if (simulationSteps < PhysicsWorldResource.MIN_SIMULATION_STEPS
            || simulationSteps > PhysicsWorldResource.MAX_SIMULATION_STEPS) {
            this.simulationSteps = PhysicsWorldResource.MIN_SIMULATION_STEPS;
            return;
        }
        this.simulationSteps = simulationSteps;
    }

    public void setStepMode(@Nonnull PhysicsStepMode stepMode) {
        this.stepMode = stepMode != null ? stepMode : PhysicsStepMode.PROGRESSIVE_REFINEMENT;
    }

    public void setMaxStepDt(float maxStepDt) {
        this.maxStepDt = Float.isFinite(maxStepDt) && maxStepDt > 0.0f
            ? maxStepDt
            : PhysicsWorldResource.DEFAULT_MAX_STEP_DT;
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

    public void markRuntimeRestorePending() {
        ensureRuntimeTracking();
        runtimeRestorePending = true;
        runtimeSpaceBootstrapComplete = false;
        runtimeRestoreFailed = false;
        runtimeRestoreFailureMessage = "";
        runtimeRestoredSpaceCount = 0;
        runtimeRestoredBodyCount = 0;
        runtimeRestoredJointCount = 0;
        runtimeSnapshotSynced = false;
        runtimeSnapshotSyncSkipTicks = 0;
        runtimeSkippedBodiesByReason.clear();
        runtimeSkippedJointsByReason.clear();
        runtimeSkippedJointKeys.clear();
    }

    public void clearRuntimeRestorePending() {
        runtimeRestorePending = false;
        runtimeSnapshotSynced = false;
        runtimeSnapshotSyncSkipTicks = 0;
    }

    public boolean shouldSyncRuntimeSnapshot(int intervalTicks) {
        if (!runtimeSnapshotSynced || intervalTicks <= 0) {
            return true;
        }
        if (runtimeSnapshotSyncSkipTicks < intervalTicks) {
            runtimeSnapshotSyncSkipTicks++;
            return false;
        }
        return true;
    }

    public void markRuntimeSnapshotSynced() {
        runtimeSnapshotSynced = true;
        runtimeSnapshotSyncSkipTicks = 0;
    }

    public void markRuntimeSpaceBootstrapComplete(int restoredSpaceCount) {
        runtimeSpaceBootstrapComplete = true;
        runtimeRestoredSpaceCount = restoredSpaceCount;
    }

    public boolean hasRuntimeRestoreFailed() {
        return runtimeRestoreFailed;
    }

    public void failRuntimeRestore(@Nonnull String message) {
        ensureRuntimeTracking();
        runtimeRestorePending = false;
        runtimeRestoreFailed = true;
        runtimeRestoreFailureMessage = message;
    }

    public void recordRuntimeBodyRestored() {
        runtimeRestoredBodyCount++;
    }

    public void recordRuntimeBodySkipped(@Nonnull String reason) {
        ensureRuntimeTracking();
        runtimeSkippedBodiesByReason.merge(reason, 1, Integer::sum);
    }

    public void recordRuntimeJointRestored() {
        runtimeRestoredJointCount++;
    }

    public void recordRuntimeJointSkipped(@Nonnull String key, @Nonnull String reason) {
        ensureRuntimeTracking();
        if (!runtimeSkippedJointKeys.add(key)) {
            return;
        }
        runtimeSkippedJointsByReason.merge(reason, 1, Integer::sum);
    }

    public boolean hasRuntimeRestoreSkips() {
        ensureRuntimeTracking();
        return !runtimeSkippedBodiesByReason.isEmpty() || !runtimeSkippedJointsByReason.isEmpty();
    }

    @Nonnull
    public String runtimeRestoreSummary() {
        ensureRuntimeTracking();
        return "Impulse persistence restore completed: "
            + runtimeRestoredSpaceCount + " spaces, "
            + runtimeRestoredBodyCount + " bodies restored, "
            + countReasons(runtimeSkippedBodiesByReason) + " bodies skipped ("
            + formatReasons(runtimeSkippedBodiesByReason) + "), "
            + runtimeRestoredJointCount + " joints restored, "
            + countReasons(runtimeSkippedJointsByReason) + " joints skipped ("
            + formatReasons(runtimeSkippedJointsByReason) + ").";
    }

    @Nonnull
    public String runtimeRestoreFailureSummary() {
        ensureRuntimeTracking();
        return "Impulse persistence restore failed: " + runtimeRestoreFailureMessage + " "
            + "Partial progress before failure: "
            + runtimeRestoredSpaceCount + " spaces, "
            + runtimeRestoredBodyCount + " bodies restored, "
            + countReasons(runtimeSkippedBodiesByReason) + " bodies skipped, "
            + runtimeRestoredJointCount + " joints restored, "
            + countReasons(runtimeSkippedJointsByReason) + " joints skipped.";
    }

    public void copyFrom(@Nonnull PersistentPhysicsWorldResource other) {
        if (this == other) {
            return;
        }
        schemaVersion = other.schemaVersion > 0 ? other.schemaVersion : CURRENT_SCHEMA_VERSION;
        defaultSpaceId = other.defaultSpaceId;
        setSimulationSteps(other.simulationSteps);
        setStepMode(other.stepMode != null
            ? other.stepMode
            : PhysicsStepMode.PROGRESSIVE_REFINEMENT);
        setMaxStepDt(other.maxStepDt);
        spaces = copySpaces(other.spaces);
        joints = copyJoints(other.joints);
        runtimeRestorePending = false;
        runtimeSpaceBootstrapComplete = false;
        runtimeRestoreFailed = false;
        runtimeRestoreFailureMessage = "";
        runtimeRestoredSpaceCount = 0;
        runtimeRestoredBodyCount = 0;
        runtimeRestoredJointCount = 0;
        runtimeSnapshotSynced = false;
        runtimeSnapshotSyncSkipTicks = 0;
        ensureRuntimeTracking();
        runtimeSkippedBodiesByReason.clear();
        runtimeSkippedJointsByReason.clear();
        runtimeSkippedJointKeys.clear();
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

    private void ensureRuntimeTracking() {
        if (runtimeSkippedBodiesByReason == null) {
            runtimeSkippedBodiesByReason = new LinkedHashMap<>();
        }
        if (runtimeSkippedJointsByReason == null) {
            runtimeSkippedJointsByReason = new LinkedHashMap<>();
        }
        if (runtimeSkippedJointKeys == null) {
            runtimeSkippedJointKeys = new HashSet<>();
        }
        if (runtimeRestoreFailureMessage == null) {
            runtimeRestoreFailureMessage = "";
        }
    }

    @Nonnull
    private static PhysicsStepMode parseStepModeOrDefault(@Nonnull String value) {
        if (value == null) {
            return PhysicsStepMode.PROGRESSIVE_REFINEMENT;
        }
        try {
            return PhysicsStepMode.parse(value);
        } catch (IllegalArgumentException exception) {
            return PhysicsStepMode.PROGRESSIVE_REFINEMENT;
        }
    }

    private static int countReasons(@Nonnull Map<String, Integer> reasons) {
        int total = 0;
        for (int count : reasons.values()) {
            total += count;
        }
        return total;
    }

    @Nonnull
    private static String formatReasons(@Nonnull Map<String, Integer> reasons) {
        if (reasons.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : reasons.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }
}
